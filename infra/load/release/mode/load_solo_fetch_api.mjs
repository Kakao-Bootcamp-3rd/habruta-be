import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'

const BASE_URL = process.env.BASE_URL ?? 'https://release.imymemine.kr'
const E2E_LOGIN_PATH = '/server/e2e/login' // VU별: /server/e2e/login/{vuId}
const CONCURRENCY = Number(process.env.CONCURRENCY ?? '100')
const ITERATIONS = Number(process.env.ITERATIONS ?? '100')
const AUDIO_FILE =
  process.env.AUDIO_FILE ??
  '/Users/wonhyeonseob/Desktop/git/MINE/fe/4-team-IMYME-fe/load/release/assets/sample_speech.webm'
const AUDIO_CONTENT_TYPE = 'audio/webm'
const POLL_INTERVAL_MS = Number(process.env.POLL_INTERVAL_MS ?? '2000')
const FEEDBACK_TIMEOUT_MS = Number(process.env.FEEDBACK_TIMEOUT_MS ?? '600000')
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'EXPIRED'])

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const formatKst = (date) =>
  new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date) + ' KST'

const msToSec = (ms) => (ms / 1000).toFixed(2)
const formatDurationKo = (ms) => {
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes <= 0) return `${seconds}초`
  return `${minutes}분 ${seconds}초`
}
const STEP_ORDER = ['login', 'metadata', 'createCard', 'createAttempt', 'upload', 'uploadComplete', 'pollFeedback']
const STEP_LABELS = {
  login: '1) Login',
  metadata: '2) Category/Keyword',
  createCard: '3) Create Card',
  createAttempt: '4) Create Attempt',
  upload: '5) Presigned+Upload',
  uploadComplete: '6) Upload Complete',
  pollFeedback: '7) Poll Feedback',
}

function percentile(arr, p) {
  if (arr.length === 0) return 0
  const sorted = [...arr].sort((a, b) => a - b)
  const index = Math.ceil((p / 100) * sorted.length) - 1
  return sorted[Math.max(0, index)]
}

async function e2eLogin(deviceUuid, vuId) {
  const loginPath = `${E2E_LOGIN_PATH}/${vuId}`
  const response = await fetch(new URL(loginPath, BASE_URL).toString(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceUuid }),
  })
  if (!response.ok) {
    throw new Error(`Login failed: ${loginPath} -> ${response.status} ${await response.text()}`)
  }
  const body = await response.json()
  const accessToken = body?.data?.accessToken
  if (!accessToken) throw new Error('Missing accessToken')
  return { accessToken }
}

async function fetchCategories(accessToken) {
  const response = await fetch(new URL('/server/categories', BASE_URL).toString(), {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!response.ok) throw new Error(`Categories failed: ${response.status}`)
  const body = await response.json()
  return body?.data ?? []
}

async function fetchKeywords(accessToken, categoryId) {
  const response = await fetch(
    new URL(`/server/categories/${categoryId}/keywords`, BASE_URL).toString(),
    { headers: { Authorization: `Bearer ${accessToken}` } },
  )
  if (!response.ok) throw new Error(`Keywords failed: ${response.status}`)
  const body = await response.json()
  return body?.data?.keywords ?? []
}

async function createCard(accessToken, { categoryId, keywordId, title }) {
  const response = await fetch(new URL('/server/cards', BASE_URL).toString(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ categoryId, keywordId, title }),
  })
  if (!response.ok) throw new Error(`Create card failed: ${response.status} ${await response.text()}`)
  const body = await response.json()
  const cardId = body?.data?.id
  if (!cardId) throw new Error('Missing cardId')
  return { cardId }
}

async function createAttempt(accessToken, cardId) {
  const response = await fetch(new URL(`/server/cards/${cardId}/attempts`, BASE_URL).toString(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  })
  if (!response.ok) throw new Error(`Create attempt failed: ${response.status} ${await response.text()}`)
  const body = await response.json()
  const attemptId = body?.data?.attemptId
  if (!attemptId) throw new Error('Missing attemptId')
  return { attemptId }
}

async function getPresignedUrl(accessToken, attemptId) {
  const response = await fetch(new URL('/server/learning/presigned-url', BASE_URL).toString(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ attemptId, contentType: AUDIO_CONTENT_TYPE }),
  })
  if (!response.ok) throw new Error(`Presigned URL failed: ${response.status}`)
  const body = await response.json()
  return { uploadUrl: body?.data?.uploadUrl, objectKey: body?.data?.objectKey }
}

async function uploadAudio(uploadUrl, audioBytes) {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': AUDIO_CONTENT_TYPE },
    body: audioBytes,
  })
  if (!response.ok) throw new Error(`Upload failed: ${response.status}`)
}

async function completeUpload(accessToken, { cardId, attemptId, objectKey }) {
  const response = await fetch(
    new URL(`/server/cards/${cardId}/attempts/${attemptId}/upload-complete`, BASE_URL).toString(),
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ objectKey, durationSeconds: 3 }),
    },
  )
  if (!response.ok) throw new Error(`Complete upload failed: ${response.status}`)
}

async function waitForTerminalStatus(accessToken, { cardId, attemptId }) {
  const start = Date.now()
  while (Date.now() - start < FEEDBACK_TIMEOUT_MS) {
    const response = await fetch(
      new URL(`/server/cards/${cardId}/attempts/${attemptId}`, BASE_URL).toString(),
      { headers: { Authorization: `Bearer ${accessToken}` } },
    )
    if (response.ok) {
      const body = await response.json()
      const status = body?.data?.status
      if (TERMINAL_STATUSES.has(status)) return status
    }
    await sleep(POLL_INTERVAL_MS)
  }
  throw new Error('Timeout waiting for terminal status')
}

async function warmupServerless(accessToken) {
  const response = await fetch(new URL('/server/learning/warmup', BASE_URL).toString(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  })
  if (!response.ok) {
    console.warn(`Warmup failed: ${response.status} (non-fatal)`)
    return false
  }
  return true
}

async function runSingleFlow(index) {
  const startTime = Date.now()
  const deviceUuid = randomUUID()
  let status = 'UNKNOWN'
  const stepDurations = {}
  let currentStep = 'init'
  const measureStep = async (step, fn) => {
    currentStep = step
    const t0 = Date.now()
    const result = await fn()
    stepDurations[step] = Date.now() - t0
    return result
  }

  try {
    // 1. Login (VU별 다른 유저)
    const vuId = 10000 + index + 1 // 기존 유저와 충돌 방지 (10001부터 시작)
    const { accessToken } = await measureStep('login', async () => e2eLogin(deviceUuid, vuId))

    // 2. Get categories & keywords
    const { category, keyword } = await measureStep('metadata', async () => {
      const categories = await fetchCategories(accessToken)
      const pickedCategory = categories.find((c) => !/테스트/.test(c.name)) ?? categories[0]
      const keywords = await fetchKeywords(accessToken, pickedCategory.id)
      return { category: pickedCategory, keyword: keywords[0] }
    })

    // 3. Create card
    const cardTitle = `t${Date.now() % 100000}-${index}`
    const { cardId } = await measureStep('createCard', async () =>
      createCard(accessToken, {
        categoryId: category.id,
        keywordId: keyword.id,
        title: cardTitle,
      }),
    )

    // 4. Create attempt
    const { attemptId } = await measureStep('createAttempt', async () => createAttempt(accessToken, cardId))

    // 5. Get presigned URL & upload audio
    const { objectKey } = await measureStep('upload', async () => {
      const { uploadUrl, objectKey } = await getPresignedUrl(accessToken, attemptId)
      const audioBytes = await readFile(AUDIO_FILE)
      await uploadAudio(uploadUrl, audioBytes)
      return { objectKey }
    })

    // 6. Complete upload
    await measureStep('uploadComplete', async () =>
      completeUpload(accessToken, { cardId, attemptId, objectKey }),
    )

    // 7. Wait for feedback
    status = await measureStep('pollFeedback', async () => waitForTerminalStatus(accessToken, { cardId, attemptId }))

    const duration = Date.now() - startTime
    return { index, ok: true, status, duration, stepDurations }
  } catch (error) {
    const duration = Date.now() - startTime
    return { index, ok: false, status, error: error.message, duration, stepDurations, failedStep: currentStep }
  }
}

async function main() {
  const testStartTime = new Date()
  console.log(`[LOAD TEST] Starting...`)
  console.log(`- START TIME: ${formatKst(testStartTime)}`)
  console.log(`- BASE_URL: ${BASE_URL}`)
  console.log(`- CONCURRENCY: ${CONCURRENCY}`)
  console.log(`- ITERATIONS: ${ITERATIONS}`)
  console.log(`- AUDIO_FILE: ${AUDIO_FILE}`)
  console.log(`- E2E_LOGIN_PATH: ${E2E_LOGIN_PATH}`)
  console.log('')

  // Warmup: 먼저 로그인 후 RunPod Serverless 깨우기
  console.log('[WARMUP] Warming up RunPod Serverless...')
  const warmupDeviceUuid = randomUUID()
  const { accessToken: warmupToken } = await e2eLogin(warmupDeviceUuid, 9999)
  const warmupOk = await warmupServerless(warmupToken)
  if (warmupOk) {
    console.log('[WARMUP] RunPod Serverless warmup successful')
    // 워밍업 후 잠시 대기 (서버리스가 완전히 준비될 시간)
    await sleep(5000)
  } else {
    console.log('[WARMUP] RunPod Serverless warmup failed, continuing anyway...')
  }
  console.log('')

  const results = []
  const startTime = Date.now()

  for (let i = 0; i < ITERATIONS; i += CONCURRENCY) {
    const batchSize = Math.min(CONCURRENCY, ITERATIONS - i)
    const batchStart = Date.now()

    console.log(`[BATCH ${Math.floor(i / CONCURRENCY) + 1}] Running ${batchSize} concurrent requests...`)

    const batch = Array.from({ length: batchSize }, (_, idx) => runSingleFlow(i + idx))
    const batchResults = await Promise.all(batch)
    results.push(...batchResults)

    const batchDuration = Date.now() - batchStart
    const successCount = batchResults.filter((r) => r.ok).length
    console.log(
      `[BATCH ${Math.floor(i / CONCURRENCY) + 1}] Done in ${formatDurationKo(batchDuration)} (${batchDuration}ms) (${successCount}/${batchSize} success)`,
    )
  }

  const totalDuration = Date.now() - startTime
  const testEndTime = new Date()
  const successResults = results.filter((r) => r.ok)
  const failedResults = results.filter((r) => !r.ok)
  const completedResults = results.filter((r) => r.status === 'COMPLETED')
  const totalSeconds = totalDuration / 1000
  const throughput = totalSeconds > 0 ? results.length / totalSeconds : 0
  const successRate = results.length > 0 ? (successResults.length / results.length) * 100 : 0

  console.log('')
  console.log('=== LOAD TEST RESULTS ===')
  console.log(`Start Time: ${formatKst(testStartTime)}`)
  console.log(`End Time: ${formatKst(testEndTime)}`)
  console.log(`Total Duration: ${formatDurationKo(totalDuration)} (${totalDuration}ms)`)
  console.log('')
  console.log(`Total Requests: ${results.length}`)
  console.log(`Success: ${successResults.length}`)
  console.log(`Failed: ${failedResults.length}`)
  console.log(`COMPLETED: ${completedResults.length}`)
  console.log(`Success Rate: ${successRate.toFixed(2)}%`)
  console.log(`Throughput: ${throughput.toFixed(2)} req/s`)

  if (successResults.length > 0) {
    const durations = successResults.map((r) => r.duration)
    const avgDuration = durations.reduce((a, b) => a + b, 0) / durations.length
    const minDuration = Math.min(...durations)
    const maxDuration = Math.max(...durations)
    const p50 = percentile(durations, 50)
    const p95 = percentile(durations, 95)
    const p99 = percentile(durations, 99)

    console.log('')
    console.log('=== RESPONSE TIME ===')
    console.log(`Min:  ${msToSec(minDuration)}s (${Math.round(minDuration)}ms)`)
    console.log(`Avg:  ${msToSec(avgDuration)}s (${Math.round(avgDuration)}ms)`)
    console.log(`p50:  ${msToSec(p50)}s (${Math.round(p50)}ms)`)
    console.log(`p95:  ${msToSec(p95)}s (${Math.round(p95)}ms)`)
    console.log(`p99:  ${msToSec(p99)}s (${Math.round(p99)}ms)`)
    console.log(`Max:  ${msToSec(maxDuration)}s (${Math.round(maxDuration)}ms)`)
  }

  const stepStats = {}
  STEP_ORDER.forEach((step) => {
    const values = results.map((r) => r.stepDurations?.[step]).filter((v) => typeof v === 'number')
    if (values.length === 0) return
    const avg = values.reduce((a, b) => a + b, 0) / values.length
    stepStats[step] = {
      count: values.length,
      avg,
      p95: percentile(values, 95),
      max: Math.max(...values),
    }
  })

  if (Object.keys(stepStats).length > 0) {
    console.log('')
    console.log('=== BOTTLENECK BY STEP ===')
    STEP_ORDER.forEach((step) => {
      const s = stepStats[step]
      if (!s) return
      console.log(
        `${STEP_LABELS[step]} -> avg ${msToSec(s.avg)}s (${Math.round(s.avg)}ms), p95 ${msToSec(s.p95)}s (${Math.round(s.p95)}ms), max ${msToSec(s.max)}s (${Math.round(s.max)}ms), n=${s.count}`,
      )
    })
  }

  if (failedResults.length > 0) {
    const failedStepCount = {}
    const failedErrorCount = {}
    failedResults.forEach((r) => {
      const step = r.failedStep ?? 'unknown'
      failedStepCount[step] = (failedStepCount[step] ?? 0) + 1
      const errorKey = String(r.error).slice(0, 120)
      failedErrorCount[errorKey] = (failedErrorCount[errorKey] ?? 0) + 1
    })
    console.log('')
    console.log('=== FAILURE HOTSPOTS ===')
    Object.entries(failedStepCount)
      .sort((a, b) => b[1] - a[1])
      .forEach(([step, count]) => {
        const label = STEP_LABELS[step] ?? step
        console.log(`${label}: ${count}`)
      })
    console.log('')
    console.log('=== TOP ERRORS ===')
    Object.entries(failedErrorCount)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .forEach(([error, count]) => {
        console.log(`${count}x ${error}`)
      })
  }

  if (failedResults.length > 0) {
    console.log('')
    console.log('=== FAILURES ===')
    failedResults.forEach((r) => {
      console.log(`[${r.index}] ${r.error}`)
    })
  }
}

main()

// node /Users/wonhyeonseob/Desktop/git/MINE/fe/4-team-IMYME-fe/load/release/35mau/steady_and_spike_until_upload.mjs
// Flow: login -> categories -> keywords -> card -> attempt -> presigned-url -> S3 upload (NO upload-complete, NO polling)

import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

// === Configuration ===
const BASE_URL =
  process.env.BASE_URL ?? 'http://habruta-release-alb-176225423.ap-northeast-2.elb.amazonaws.com'
const API_PREFIX = process.env.API_PREFIX ?? ''
const E2E_LOGIN_PATH = '/e2e/login'
const AUDIO_FILE =
  process.env.AUDIO_FILE ??
  fileURLToPath(new URL('../../../../assets/sample_speech.webm', import.meta.url))
const AUDIO_CONTENT_TYPE = 'audio/webm'

// Scheduler config
const TEST_DURATION_MINUTES = Number(process.env.TEST_DURATION_MINUTES ?? '30')
const TICK_MS = Number(process.env.TICK_MS ?? '1000')
const MAX_VU_CAP = Number(process.env.MAX_VU_CAP ?? '4000')

// Deprecated (unused)
void (process.env.CO1NCURRENCY ?? '100')
void (process.env.ITERATIONS ?? '100')

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const msToSec = (ms) => (ms / 1000).toFixed(2)
const toKST = (date) => date.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })
const apiUrl = (path) => new URL(`${API_PREFIX}${path}`, BASE_URL).toString()

function percentile(arr, p) {
  if (arr.length === 0) return 0
  const sorted = [...arr].sort((a, b) => a - b)
  const index = Math.ceil((p / 100) * sorted.length) - 1
  return sorted[Math.max(0, index)]
}

// === Scenario Definition (30분 기준) ===
const SCENARIO_STAGES = [
  { name: 'Ramp-up', startMin: 0, endMin: 5, startVU: 0, endVU: 2800 },
  { name: 'Steady', startMin: 5, endMin: 20, startVU: 2800, endVU: 2800 },
  { name: 'Spike-ramp', startMin: 20, endMin: 22, startVU: 2800, endVU: 3600 },
  { name: 'Spike-steady', startMin: 22, endMin: 27, startVU: 3600, endVU: 3600 },
  { name: 'Ramp-down', startMin: 27, endMin: 30, startVU: 3600, endVU: 0 },
]

function getTargetVU(elapsedMs) {
  const elapsedMin = elapsedMs / 60000
  const scaleFactor = TEST_DURATION_MINUTES / 30

  for (const stage of SCENARIO_STAGES) {
    const scaledStart = stage.startMin * scaleFactor
    const scaledEnd = stage.endMin * scaleFactor
    if (elapsedMin >= scaledStart && elapsedMin < scaledEnd) {
      const progress = (elapsedMin - scaledStart) / (scaledEnd - scaledStart)
      const targetVU = Math.round(stage.startVU + (stage.endVU - stage.startVU) * progress)
      return { target: Math.min(targetVU, MAX_VU_CAP), stageName: stage.name }
    }
  }
  return { target: 0, stageName: 'End' }
}

function getStageName(elapsedMs) {
  return getTargetVU(elapsedMs).stageName
}

// === API Functions ===
async function e2eLogin(deviceUuid, vuId) {
  const loginPath = `${E2E_LOGIN_PATH}/${vuId}`
  const response = await fetch(apiUrl(loginPath), {
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
  const response = await fetch(apiUrl('/categories'), {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!response.ok) throw new Error(`Categories failed: ${response.status}`)
  const body = await response.json()
  return body?.data ?? []
}

async function fetchKeywords(accessToken, categoryId) {
  const response = await fetch(
    apiUrl(`/categories/${categoryId}/keywords`),
    { headers: { Authorization: `Bearer ${accessToken}` } },
  )
  if (!response.ok) throw new Error(`Keywords failed: ${response.status}`)
  const body = await response.json()
  return body?.data?.keywords ?? []
}

async function createCard(accessToken, { categoryId, keywordId, title }) {
  const response = await fetch(apiUrl('/cards'), {
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
  const response = await fetch(apiUrl(`/cards/${cardId}/attempts`), {
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
  const response = await fetch(apiUrl('/learning/presigned-url'), {
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

async function warmupServerless(accessToken) {
  const response = await fetch(apiUrl('/learning/warmup'), {
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

// === Single Flow (1~8단계: S3 업로드까지만) ===
async function runSingleFlow(vuId, audioBytes) {
  const startTime = Date.now()
  const deviceUuid = randomUUID()

  try {
    const { accessToken } = await e2eLogin(deviceUuid, vuId)
    const categories = await fetchCategories(accessToken)
    const category = categories.find((c) => !/테스트/.test(c.name)) ?? categories[0]
    const keywords = await fetchKeywords(accessToken, category.id)
    const keyword = keywords[0]
    const cardTitle = `t${Date.now() % 100000}-${vuId}`
    const { cardId } = await createCard(accessToken, {
      categoryId: category.id,
      keywordId: keyword.id,
      title: cardTitle,
    })
    const { attemptId } = await createAttempt(accessToken, cardId)
    const { uploadUrl } = await getPresignedUrl(accessToken, attemptId)
    await uploadAudio(uploadUrl, audioBytes)

    return { vuId, ok: true, duration: Date.now() - startTime, startTime }
  } catch (error) {
    return { vuId, ok: false, error: error.message, duration: Date.now() - startTime, startTime }
  }
}

// === VU Scheduler ===
async function main() {
  const testStartTime = Date.now()
  const testEndTime = testStartTime + TEST_DURATION_MINUTES * 60 * 1000

  console.log(`[LOAD TEST] 30분 Steady + Spike (Until Upload)`)
  console.log(`- Start Time (KST): ${toKST(new Date(testStartTime))}`)
  console.log(`- BASE_URL: ${BASE_URL}`)
  console.log(`- TEST_DURATION: ${TEST_DURATION_MINUTES}분`)
  console.log(`- TICK_MS: ${TICK_MS}`)
  console.log(`- MAX_VU_CAP: ${MAX_VU_CAP}`)
  console.log(`- AUDIO_FILE: ${AUDIO_FILE}`)
  console.log(`- Flow: login -> categories -> keywords -> card -> attempt -> presigned -> S3 upload`)
  console.log('')

  // Warmup
  console.log('[WARMUP] Warming up RunPod Serverless...')
  const warmupDeviceUuid = randomUUID()
  const { accessToken: warmupToken } = await e2eLogin(warmupDeviceUuid, 9999)
  const warmupOk = await warmupServerless(warmupToken)
  if (warmupOk) {
    console.log('[WARMUP] Success')
    await sleep(5000)
  } else {
    console.log('[WARMUP] Failed, continuing...')
  }
  console.log('')

  // Preload audio
  const audioBytes = await readFile(AUDIO_FILE)

  // State
  const results = []
  const stageStats = {}
  for (const s of SCENARIO_STAGES) {
    stageStats[s.name] = { requests: 0, success: 0, failed: 0, durations: [] }
  }

  let activeVUs = new Set()
  let vuIdCounter = 10000
  let testRunning = true
  let lastLogTime = 0

  const runVU = async (vuId) => {
    activeVUs.add(vuId)
    while (testRunning || Date.now() < testEndTime) {
      const now = Date.now()
      if (now >= testEndTime) break

      const elapsed = now - testStartTime
      const { target } = getTargetVU(elapsed)

      if (activeVUs.size > target) {
        activeVUs.delete(vuId)
        return
      }

      const result = await runSingleFlow(vuId, audioBytes)
      result.stage = getStageName(result.startTime - testStartTime)
      results.push(result)

      const stats = stageStats[result.stage]
      if (stats) {
        stats.requests++
        if (result.ok) {
          stats.success++
          stats.durations.push(result.duration)
        } else {
          stats.failed++
        }
      }

      if (Date.now() >= testEndTime) break
    }
    activeVUs.delete(vuId)
  }

  // Scheduler loop
  const schedulerInterval = setInterval(() => {
    const now = Date.now()
    if (now >= testEndTime) {
      testRunning = false
      clearInterval(schedulerInterval)
      return
    }

    const elapsed = now - testStartTime
    const { target, stageName } = getTargetVU(elapsed)
    const currentActive = activeVUs.size

    const toSpawn = Math.min(target - currentActive, 50)
    for (let i = 0; i < toSpawn && activeVUs.size < target; i++) {
      vuIdCounter++
      runVU(vuIdCounter)
    }

    if (now - lastLogTime >= 10000) {
      const elapsedMin = (elapsed / 60000).toFixed(1)
      console.log(
        `[${elapsedMin}m] Stage: ${stageName} | Target: ${target} | Active: ${activeVUs.size} | Completed: ${results.length}`,
      )
      lastLogTime = now
    }
  }, TICK_MS)

  await sleep(TEST_DURATION_MINUTES * 60 * 1000 + 1000)
  testRunning = false
  clearInterval(schedulerInterval)

  // Drain
  console.log(`\n[DRAIN] Waiting for ${activeVUs.size} active VUs to finish...`)
  const drainStart = Date.now()
  while (activeVUs.size > 0 && Date.now() - drainStart < 60000) {
    await sleep(2000)
    console.log(`[DRAIN] ${activeVUs.size} VUs still active...`)
  }

  // === Results ===
  const actualEndTime = Date.now()
  const totalDuration = actualEndTime - testStartTime
  const successResults = results.filter((r) => r.ok)
  const failedResults = results.filter((r) => !r.ok)

  console.log('')
  console.log('═'.repeat(60))
  console.log('              LOAD TEST RESULTS (Until Upload)')
  console.log('═'.repeat(60))
  console.log(`Start Time (KST): ${toKST(new Date(testStartTime))}`)
  console.log(`End Time (KST):   ${toKST(new Date(actualEndTime))}`)
  console.log(`Total Duration:   ${msToSec(totalDuration)}s (${(totalDuration / 60000).toFixed(1)}분)`)
  console.log('')
  console.log(`Total Requests:   ${results.length}`)
  console.log(`Success:          ${successResults.length}`)
  console.log(`Failed:           ${failedResults.length}`)
  console.log(`Success Rate:     ${results.length > 0 ? ((successResults.length / results.length) * 100).toFixed(2) : 0}%`)
  console.log(`Overall TPS:      ${(results.length / (totalDuration / 1000)).toFixed(2)} req/s`)

  if (successResults.length > 0) {
    const durations = successResults.map((r) => r.duration)
    console.log('')
    console.log('─'.repeat(60))
    console.log('                    RESPONSE TIME')
    console.log('─'.repeat(60))
    console.log(`Min:  ${msToSec(Math.min(...durations))}s`)
    console.log(`Avg:  ${msToSec(durations.reduce((a, b) => a + b, 0) / durations.length)}s`)
    console.log(`p50:  ${msToSec(percentile(durations, 50))}s`)
    console.log(`p95:  ${msToSec(percentile(durations, 95))}s`)
    console.log(`p99:  ${msToSec(percentile(durations, 99))}s`)
    console.log(`Max:  ${msToSec(Math.max(...durations))}s`)
  }

  console.log('')
  console.log('─'.repeat(60))
  console.log('                    STAGE BREAKDOWN')
  console.log('─'.repeat(60))
  for (const stage of SCENARIO_STAGES) {
    const stats = stageStats[stage.name]
    const stageDurationSec = (stage.endMin - stage.startMin) * 60 * (TEST_DURATION_MINUTES / 30)
    const tps = stageDurationSec > 0 ? (stats.requests / stageDurationSec).toFixed(2) : '0.00'
    console.log(
      `${stage.name.padEnd(12)} | Requests: ${String(stats.requests).padStart(6)} | ` +
        `Success: ${String(stats.success).padStart(6)} | Failed: ${String(stats.failed).padStart(4)} | TPS: ${tps}`,
    )
  }

  if (failedResults.length > 0) {
    console.log('')
    console.log('─'.repeat(60))
    console.log('                    TOP 10 ERRORS')
    console.log('─'.repeat(60))
    const errorCounts = {}
    failedResults.forEach((r) => {
      const key = r.error || 'Unknown'
      errorCounts[key] = (errorCounts[key] || 0) + 1
    })
    Object.entries(errorCounts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .forEach(([err, count]) => {
        console.log(`  [${count}] ${err}`)
      })
  }

  console.log('')
  console.log('═'.repeat(60))
}

main().catch(console.error)

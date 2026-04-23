// node /Users/wonhyeonseob/Desktop/git/MINE/fe/4-team-IMYME-fe/load/release/35mau/steady_and_spike.mjs

import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'

// === Configuration ===
const BASE_URL = process.env.BASE_URL ?? 'https://release.imymemine.kr'
const E2E_LOGIN_PATH = '/server/e2e/login'
const AUDIO_FILE =
  process.env.AUDIO_FILE ??
  '/Users/wonhyeonseob/Desktop/git/MINE/fe/4-team-IMYME-fe/load/release/assets/sample_speech.webm'
const AUDIO_CONTENT_TYPE = 'audio/webm'
const POLL_INTERVAL_MS = Number(process.env.POLL_INTERVAL_MS ?? '2000')
const FEEDBACK_TIMEOUT_MS = Number(process.env.FEEDBACK_TIMEOUT_MS ?? '600000')
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'EXPIRED'])

// New scheduler config
const TEST_DURATION_MINUTES = Number(process.env.TEST_DURATION_MINUTES ?? '20')
const TICK_MS = Number(process.env.TICK_MS ?? '1000')
const MAX_VU_CAP = Number(process.env.MAX_VU_CAP ?? '4000')

// Deprecated (kept for backwards compat, unused)
void (process.env.CONCURRENCY ?? '100')
void (process.env.ITERATIONS ?? '100')

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const msToSec = (ms) => (ms / 1000).toFixed(2)
const toKST = (date) => date.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })

function percentile(arr, p) {
  if (arr.length === 0) return 0
  const sorted = [...arr].sort((a, b) => a - b)
  const index = Math.ceil((p / 100) * sorted.length) - 1
  return sorted[Math.max(0, index)]
}

// === Scenario Definition (20분 기준, 비율로 계산) ===
const SCENARIO_STAGES = [
  { name: 'Ramp-up', startMin: 0, endMin: 5, startVU: 0, endVU: 2800 },
  { name: 'Steady', startMin: 5, endMin: 10, startVU: 2800, endVU: 2800 },
  { name: 'Spike-ramp', startMin: 10, endMin: 12, startVU: 2800, endVU: 3600 },
  { name: 'Spike-steady', startMin: 12, endMin: 17, startVU: 3600, endVU: 3600 },
  { name: 'Ramp-down', startMin: 17, endMin: 20, startVU: 3600, endVU: 0 },
]

function getTargetVU(elapsedMs) {
  const elapsedMin = elapsedMs / 60000
  const scaleFactor = TEST_DURATION_MINUTES / 20

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

// === API Functions (unchanged) ===
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

// === Single Flow (reused) ===
async function runSingleFlow(vuId, audioBytes) {
  const startTime = Date.now()
  const deviceUuid = randomUUID()
  let status = 'UNKNOWN'

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
    const { uploadUrl, objectKey } = await getPresignedUrl(accessToken, attemptId)
    await uploadAudio(uploadUrl, audioBytes)
    await completeUpload(accessToken, { cardId, attemptId, objectKey })
    status = await waitForTerminalStatus(accessToken, { cardId, attemptId })

    return { vuId, ok: true, status, duration: Date.now() - startTime, startTime }
  } catch (error) {
    return { vuId, ok: false, status, error: error.message, duration: Date.now() - startTime, startTime }
  }
}

// === VU Scheduler ===
async function main() {
  const testStartTime = Date.now()
  const testEndTime = testStartTime + TEST_DURATION_MINUTES * 60 * 1000

  console.log(`[LOAD TEST] 30분 Steady + Spike 시나리오`)
  console.log(`- Start Time (KST): ${toKST(new Date(testStartTime))}`)
  console.log(`- BASE_URL: ${BASE_URL}`)
  console.log(`- TEST_DURATION: ${TEST_DURATION_MINUTES}분`)
  console.log(`- TICK_MS: ${TICK_MS}`)
  console.log(`- MAX_VU_CAP: ${MAX_VU_CAP}`)
  console.log(`- AUDIO_FILE: ${AUDIO_FILE}`)
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

    // Spawn new VUs if needed
    const toSpawn = Math.min(target - currentActive, 50) // max 50 per tick
    for (let i = 0; i < toSpawn && activeVUs.size < target; i++) {
      vuIdCounter++
      runVU(vuIdCounter)
    }

    // Log every 10 seconds
    if (now - lastLogTime >= 10000) {
      const elapsedMin = (elapsed / 60000).toFixed(1)
      console.log(
        `[${elapsedMin}m] Stage: ${stageName} | Target: ${target} | Active: ${activeVUs.size} | Completed: ${results.length}`,
      )
      lastLogTime = now
    }
  }, TICK_MS)

  // Wait for test duration + drain time
  await sleep(TEST_DURATION_MINUTES * 60 * 1000 + 1000)
  testRunning = false
  clearInterval(schedulerInterval)

  // Wait for remaining VUs to finish (max 10min)
  console.log(`\n[DRAIN] Waiting for ${activeVUs.size} active VUs to finish...`)
  const drainStart = Date.now()
  while (activeVUs.size > 0 && Date.now() - drainStart < 600000) {
    await sleep(5000)
    console.log(`[DRAIN] ${activeVUs.size} VUs still active...`)
  }

  // === Results ===
  const actualEndTime = Date.now()
  const totalDuration = actualEndTime - testStartTime
  const successResults = results.filter((r) => r.ok)
  const failedResults = results.filter((r) => !r.ok)
  const completedResults = results.filter((r) => r.status === 'COMPLETED')

  console.log('')
  console.log('═'.repeat(60))
  console.log('                    LOAD TEST RESULTS')
  console.log('═'.repeat(60))
  console.log(`Start Time (KST): ${toKST(new Date(testStartTime))}`)
  console.log(`End Time (KST):   ${toKST(new Date(actualEndTime))}`)
  console.log(`Total Duration:   ${msToSec(totalDuration)}s (${(totalDuration / 60000).toFixed(1)}분)`)
  console.log('')
  console.log(`Total Requests:   ${results.length}`)
  console.log(`Success:          ${successResults.length}`)
  console.log(`Failed:           ${failedResults.length}`)
  console.log(`COMPLETED:        ${completedResults.length}`)
  console.log(`Success Rate:     ${((successResults.length / results.length) * 100).toFixed(2)}%`)
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
    const stageDurationSec = (stage.endMin - stage.startMin) * 60 * (TEST_DURATION_MINUTES / 20)
    const tps = stageDurationSec > 0 ? (stats.requests / stageDurationSec).toFixed(2) : '0.00'
    console.log(
      `${stage.name.padEnd(12)} | Requests: ${String(stats.requests).padStart(6)} | ` +
        `Success: ${String(stats.success).padStart(6)} | Failed: ${String(stats.failed).padStart(4)} | TPS: ${tps}`,
    )
  }

  if (failedResults.length > 0 && failedResults.length <= 50) {
    console.log('')
    console.log('─'.repeat(60))
    console.log('                    FAILURES (first 50)')
    console.log('─'.repeat(60))
    failedResults.slice(0, 50).forEach((r) => {
      console.log(`[VU ${r.vuId}] ${r.error}`)
    })
  } else if (failedResults.length > 50) {
    console.log('')
    console.log(`[FAILURES] ${failedResults.length} total failures (showing first error types)`)
    const errorCounts = {}
    failedResults.forEach((r) => {
      const key = r.error?.split(':')[0] || 'Unknown'
      errorCounts[key] = (errorCounts[key] || 0) + 1
    })
    Object.entries(errorCounts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .forEach(([err, count]) => {
        console.log(`  ${err}: ${count}`)
      })
  }

  console.log('')
  console.log('═'.repeat(60))
}

main().catch(console.error)

// node /Users/wonhyeonseob/dev/projects/App/MINE/service/be/4-team-IMYME-be/infra/load/release/35mau/modes/challenge/fetch/steady_and_spike_not_AI.mjs
// Flow: login -> today challenge -> create attempt -> S3 upload (NO upload-complete, NO AI)
// 100명의 가상 사용자가 동시에 챌린지 참여 (S3 업로드까지만)

import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'

// === Configuration ===
const BASE_URL = process.env.BASE_URL ?? 'https://release.imymemine.kr'
const E2E_LOGIN_PATH = '/server/e2e/login'
const AUDIO_FILE =
  process.env.AUDIO_FILE ??
  '/Users/wonhyeonseob/Desktop/git/MINE/fe/4-team-IMYME-fe/load/release/assets/sample_speech.webm'
const AUDIO_CONTENT_TYPE = 'audio/webm'

// VU config
const TOTAL_USERS = Number(process.env.TOTAL_USERS ?? '100')

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const msToSec = (ms) => (ms / 1000).toFixed(2)
const toKST = (date) => date.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })

function percentile(arr, p) {
  if (arr.length === 0) return 0
  const sorted = [...arr].sort((a, b) => a - b)
  const index = Math.ceil((p / 100) * sorted.length) - 1
  return sorted[Math.max(0, index)]
}

// === API Functions ===
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

async function fetchTodayChallenge(accessToken) {
  const response = await fetch(new URL('/server/challenges/today', BASE_URL).toString(), {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!response.ok) throw new Error(`Today challenge failed: ${response.status}`)
  const body = await response.json()
  const challengeId = body?.data?.challengeId
  if (!challengeId) throw new Error('Missing challengeId from today challenge')
  return { challengeId, challengeData: body?.data }
}

async function createChallengeAttempt(accessToken, challengeId) {
  const response = await fetch(
    new URL(`/server/challenges/${challengeId}/attempts`, BASE_URL).toString(),
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ contentType: AUDIO_CONTENT_TYPE }),
    },
  )
  if (!response.ok) {
    throw new Error(`Create challenge attempt failed: ${response.status} ${await response.text()}`)
  }
  const body = await response.json()
  const attemptId = body?.data?.attemptId
  const uploadUrl = body?.data?.uploadUrl
  const objectKey = body?.data?.objectKey
  if (!attemptId || !uploadUrl || !objectKey) {
    throw new Error('Missing attemptId, uploadUrl or objectKey from challenge attempt')
  }
  return { attemptId, uploadUrl, objectKey }
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

// === Single Flow (챌린지 모드: S3 업로드까지만) ===
async function runSingleFlow(vuId, audioBytes) {
  const startTime = Date.now()
  const deviceUuid = randomUUID()

  try {
    const { accessToken } = await e2eLogin(deviceUuid, vuId)
    const { challengeId } = await fetchTodayChallenge(accessToken)
    const { uploadUrl } = await createChallengeAttempt(accessToken, challengeId)
    await uploadAudio(uploadUrl, audioBytes)

    return { vuId, ok: true, duration: Date.now() - startTime, startTime }
  } catch (error) {
    return { vuId, ok: false, error: error.message, duration: Date.now() - startTime, startTime }
  }
}

// === Main ===
async function main() {
  const testStartTime = Date.now()

  console.log(`[LOAD TEST] Challenge Mode - ${TOTAL_USERS}명 동시 참여 테스트 (Until Upload)`)
  console.log(`- Start Time (KST): ${toKST(new Date(testStartTime))}`)
  console.log(`- BASE_URL: ${BASE_URL}`)
  console.log(`- TOTAL_USERS: ${TOTAL_USERS}`)
  console.log(`- AUDIO_FILE: ${AUDIO_FILE}`)
  console.log(`- Flow: login -> today challenge -> create attempt -> S3 upload`)
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

  // Create VU list (10001 ~ 10000 + TOTAL_USERS)
  const vuIds = Array.from({ length: TOTAL_USERS }, (_, i) => 10001 + i)

  console.log(`[START] ${TOTAL_USERS}명 동시 참여 시작...`)

  // 모든 사용자가 동시에 챌린지 참여 시작
  const results = await Promise.all(
    vuIds.map((vuId) => runSingleFlow(vuId, audioBytes))
  )

  // === Results ===
  const actualEndTime = Date.now()
  const totalDuration = actualEndTime - testStartTime
  const successResults = results.filter((r) => r.ok)
  const failedResults = results.filter((r) => !r.ok)

  console.log('')
  console.log('═'.repeat(60))
  console.log('        LOAD TEST RESULTS (Challenge Mode - Until Upload)')
  console.log('═'.repeat(60))
  console.log(`Start Time (KST): ${toKST(new Date(testStartTime))}`)
  console.log(`End Time (KST):   ${toKST(new Date(actualEndTime))}`)
  console.log(`Total Duration:   ${msToSec(totalDuration)}s (${(totalDuration / 60000).toFixed(1)}분)`)
  console.log('')
  console.log(`Total Users:      ${TOTAL_USERS}`)
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

  if (failedResults.length > 0) {
    console.log('')
    console.log('─'.repeat(60))
    console.log('                    ERRORS')
    console.log('─'.repeat(60))
    const errorCounts = {}
    failedResults.forEach((r) => {
      const key = r.error || 'Unknown'
      errorCounts[key] = (errorCounts[key] || 0) + 1
    })
    Object.entries(errorCounts)
      .sort((a, b) => b[1] - a[1])
      .forEach(([err, count]) => {
        console.log(`  [${count}] ${err}`)
      })
  }

  console.log('')
  console.log('═'.repeat(60))
}

main().catch(console.error)

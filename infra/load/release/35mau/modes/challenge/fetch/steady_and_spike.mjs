// node infra/load/release/35mau/modes/challenge/fetch/steady_and_spike.mjs
// 100명 동시 챌린지 참여 테스트 (AI 분석 완료까지)
// Flow: login -> GET /challenges/today -> POST /challenges/{id}/attempts -> S3 upload -> POST upload-complete -> poll my-result

import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'

// === Configuration ===
const BASE_URL = process.env.BASE_URL ?? 'https://release.imymemine.kr'
const E2E_LOGIN_PATH = '/server/e2e/login'
const AUDIO_FILE =
  process.env.AUDIO_FILE ??
  '/Users/wonhyeonseob/Desktop/git/MINE/fe/4-team-IMYME-fe/load/release/assets/sample_speech.webm'
const AUDIO_CONTENT_TYPE = 'audio/webm'

const TOTAL_USERS = Number(process.env.TOTAL_USERS ?? '100')
const POLL_INTERVAL_MS = Number(process.env.POLL_INTERVAL_MS ?? '2000')
const FEEDBACK_TIMEOUT_MS = Number(process.env.FEEDBACK_TIMEOUT_MS ?? '600000') // 10분

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

// 1. 로그인
async function e2eLogin(deviceUuid, vuId) {
  const response = await fetch(`${BASE_URL}${E2E_LOGIN_PATH}/${vuId}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceUuid }),
  })
  if (!response.ok) throw new Error(`Login failed: ${response.status}`)
  const body = await response.json()
  return body?.data?.accessToken
}

// 2. 오늘 챌린지 조회
async function getTodayChallenge(accessToken) {
  const response = await fetch(`${BASE_URL}/server/challenges/today`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!response.ok) throw new Error(`Today challenge failed: ${response.status}`)
  const body = await response.json()
  return body?.data?.challengeId
}

// 3. 챌린지 참여 (presigned URL 발급)
async function createAttempt(accessToken, challengeId) {
  const response = await fetch(`${BASE_URL}/server/challenges/${challengeId}/attempts`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ contentType: AUDIO_CONTENT_TYPE }),
  })
  if (!response.ok) throw new Error(`Create attempt failed: ${response.status}`)
  const body = await response.json()
  return {
    attemptId: body?.data?.attemptId,
    uploadUrl: body?.data?.uploadUrl,
    objectKey: body?.data?.objectKey,
  }
}

// 4. S3 업로드
async function uploadToS3(uploadUrl, audioBytes) {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': AUDIO_CONTENT_TYPE },
    body: audioBytes,
  })
  if (!response.ok) throw new Error(`S3 upload failed: ${response.status}`)
}

// 5. 업로드 완료 확정
async function completeUpload(accessToken, challengeId, attemptId, objectKey) {
  const response = await fetch(
    `${BASE_URL}/server/challenges/${challengeId}/attempts/${attemptId}/upload-complete`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ objectKey, durationSeconds: 3 }),
    },
  )
  if (!response.ok) throw new Error(`Upload complete failed: ${response.status}`)
}

// 6. AI 결과 대기 (폴링)
async function waitForResult(accessToken, challengeId) {
  const start = Date.now()
  while (Date.now() - start < FEEDBACK_TIMEOUT_MS) {
    const response = await fetch(`${BASE_URL}/server/challenges/${challengeId}/my-result`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    if (response.ok) {
      const body = await response.json()
      if (body?.data?.status === 'COMPLETED') {
        return body?.data
      }
    }
    await sleep(POLL_INTERVAL_MS)
  }
  throw new Error('AI result timeout')
}

// === Single User Flow ===
async function runUser(vuId, audioBytes) {
  const start = Date.now()
  const deviceUuid = randomUUID()

  try {
    // 1. 로그인
    const accessToken = await e2eLogin(deviceUuid, vuId)

    // 2. 오늘 챌린지 조회
    const challengeId = await getTodayChallenge(accessToken)

    // 3. 챌린지 참여 (presigned URL 발급)
    const { attemptId, uploadUrl, objectKey } = await createAttempt(accessToken, challengeId)

    // 4. S3 업로드
    await uploadToS3(uploadUrl, audioBytes)

    // 5. 업로드 완료 확정
    await completeUpload(accessToken, challengeId, attemptId, objectKey)

    // 6. AI 결과 대기
    const result = await waitForResult(accessToken, challengeId)

    return { vuId, ok: true, duration: Date.now() - start, score: result?.score }
  } catch (error) {
    return { vuId, ok: false, duration: Date.now() - start, error: error.message }
  }
}

// === Main ===
async function main() {
  console.log(`[Challenge Test] ${TOTAL_USERS}명 동시 참여`)
  console.log(`- BASE_URL: ${BASE_URL}`)
  console.log(`- Start: ${toKST(new Date())}`)
  console.log('')

  // 오디오 파일 로드
  const audioBytes = await readFile(AUDIO_FILE)
  console.log(`[Audio] ${AUDIO_FILE} loaded (${audioBytes.length} bytes)`)
  console.log('')

  // 100명 동시 실행
  const startTime = Date.now()
  const vuIds = Array.from({ length: TOTAL_USERS }, (_, i) => 10001 + i)

  console.log(`[Start] ${TOTAL_USERS}명 동시 시작...`)
  const results = await Promise.all(vuIds.map((vuId) => runUser(vuId, audioBytes)))

  // 결과
  const duration = Date.now() - startTime
  const success = results.filter((r) => r.ok)
  const failed = results.filter((r) => !r.ok)

  console.log('')
  console.log('═'.repeat(50))
  console.log('                 RESULTS')
  console.log('═'.repeat(50))
  console.log(`Total:    ${TOTAL_USERS}`)
  console.log(`Success:  ${success.length}`)
  console.log(`Failed:   ${failed.length}`)
  console.log(`Duration: ${msToSec(duration)}s`)
  console.log('')

  if (success.length > 0) {
    const durations = success.map((r) => r.duration)
    console.log('─'.repeat(50))
    console.log('            RESPONSE TIME')
    console.log('─'.repeat(50))
    console.log(`Min: ${msToSec(Math.min(...durations))}s`)
    console.log(`Avg: ${msToSec(durations.reduce((a, b) => a + b, 0) / durations.length)}s`)
    console.log(`p50: ${msToSec(percentile(durations, 50))}s`)
    console.log(`p95: ${msToSec(percentile(durations, 95))}s`)
    console.log(`Max: ${msToSec(Math.max(...durations))}s`)
  }

  if (failed.length > 0) {
    console.log('')
    console.log('─'.repeat(50))
    console.log('              ERRORS')
    console.log('─'.repeat(50))
    const errors = {}
    failed.forEach((r) => {
      errors[r.error] = (errors[r.error] || 0) + 1
    })
    Object.entries(errors).forEach(([err, count]) => {
      console.log(`[${count}] ${err}`)
    })
  }

  console.log('')
  console.log('═'.repeat(50))
}

main().catch(console.error)

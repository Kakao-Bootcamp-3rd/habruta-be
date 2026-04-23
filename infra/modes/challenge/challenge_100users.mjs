// node infra/modes/challenge/challenge_100users.mjs
// 100명 동시 챌린지 참여 테스트 (AI 분석 완료까지)
// Flow:
//   1. admin/setup → admin/open
//   2. 100명: login → today → attempts → S3 upload → upload-complete
//   3. admin/close → admin/analyze
//   4. 100명: poll my-result

import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'

// === Configuration ===
const BASE_URL = process.env.BASE_URL ?? 'https://release.imymemine.kr'
const E2E_LOGIN_PATH = '/server/e2e/login'
const AUDIO_FILE =
  process.env.AUDIO_FILE ??
  '/Users/wonhyeonseob/dev/projects/App/MINE/service/be/4-team-IMYME-be/infra/load/release/assets/sample_speech.webm'
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

// === Admin API Functions ===
async function adminSetup() {
  const response = await fetch(`${BASE_URL}/server/admin/challenge/setup`, { method: 'POST' })
  if (!response.ok) throw new Error(`admin/setup failed: ${response.status}`)
  console.log('[Admin] setup 완료')
}

async function adminOpen() {
  const response = await fetch(`${BASE_URL}/server/admin/challenge/open`, { method: 'POST' })
  if (!response.ok) throw new Error(`admin/open failed: ${response.status}`)
  console.log('[Admin] open 완료')
}

async function adminClose() {
  const response = await fetch(`${BASE_URL}/server/admin/challenge/close`, { method: 'POST' })
  if (!response.ok) throw new Error(`admin/close failed: ${response.status}`)
  console.log('[Admin] close 완료')
}

async function adminAnalyze() {
  const response = await fetch(`${BASE_URL}/server/admin/challenge/analyze`, { method: 'POST' })
  if (!response.ok) throw new Error(`admin/analyze failed: ${response.status}`)
  console.log('[Admin] analyze 완료')
}

// === User API Functions ===

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
  return body?.data?.id  // 응답 필드명: id
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
  if (!response.ok) {
    const errorBody = await response.text()
    throw new Error(`Create attempt failed: ${response.status} - ${errorBody}`)
  }
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

// === Single User Flow (업로드까지만) ===
async function runUserUpload(vuId, audioBytes) {
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

    return { vuId, ok: true, duration: Date.now() - start, accessToken, challengeId }
  } catch (error) {
    return { vuId, ok: false, duration: Date.now() - start, error: error.message }
  }
}

// === Single User Flow (AI 결과 대기) ===
async function runUserWaitResult(uploadResult) {
  if (!uploadResult.ok) return uploadResult

  const start = Date.now()
  try {
    const result = await waitForResult(uploadResult.accessToken, uploadResult.challengeId)
    return {
      vuId: uploadResult.vuId,
      ok: true,
      uploadDuration: uploadResult.duration,
      aiDuration: Date.now() - start,
      totalDuration: uploadResult.duration + (Date.now() - start),
      score: result?.score,
    }
  } catch (error) {
    return {
      vuId: uploadResult.vuId,
      ok: false,
      uploadDuration: uploadResult.duration,
      error: error.message,
    }
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
  console.log(`[Audio] loaded (${audioBytes.length} bytes)`)
  console.log('')

  // === 1단계: 챌린지 셋업 & 오픈 ===
  console.log('[Phase 1] 챌린지 셋업 & 오픈')
  await adminSetup()
  await sleep(1000)
  await adminOpen()
  console.log('')

  // === 2단계: 100명 업로드 ===
  console.log('[Phase 2] 100명 업로드 시작...')
  const uploadStartTime = Date.now()
  const vuIds = Array.from({ length: TOTAL_USERS }, (_, i) => 10001 + i)
  const uploadResults = await Promise.all(vuIds.map((vuId) => runUserUpload(vuId, audioBytes)))
  const uploadDuration = Date.now() - uploadStartTime

  const uploadSuccess = uploadResults.filter((r) => r.ok)
  const uploadFailed = uploadResults.filter((r) => !r.ok)
  console.log(`[Phase 2] 완료 - 성공: ${uploadSuccess.length}, 실패: ${uploadFailed.length}, 소요: ${msToSec(uploadDuration)}s`)
  console.log('')

  // === 3단계: 챌린지 종료 & 분석 시작 ===
  console.log('[Phase 3] 챌린지 종료 & 분석 시작')
  await adminClose()
  await adminAnalyze()
  console.log('')

  // === 4단계: AI 결과 대기 ===
  console.log('[Phase 4] AI 결과 대기 중...')
  const aiStartTime = Date.now()
  const finalResults = await Promise.all(uploadResults.map((r) => runUserWaitResult(r)))
  const aiDuration = Date.now() - aiStartTime

  // 결과
  const totalDuration = uploadDuration + aiDuration
  const success = finalResults.filter((r) => r.ok)
  const failed = finalResults.filter((r) => !r.ok)

  console.log('')
  console.log('═'.repeat(50))
  console.log('                 RESULTS')
  console.log('═'.repeat(50))
  console.log(`Total Users:     ${TOTAL_USERS}`)
  console.log(`Success:         ${success.length}`)
  console.log(`Failed:          ${failed.length}`)
  console.log('')
  console.log(`Upload Time:     ${msToSec(uploadDuration)}s`)
  console.log(`AI Analysis:     ${msToSec(aiDuration)}s`)
  console.log(`Total Duration:  ${msToSec(totalDuration)}s`)
  console.log('')

  if (success.length > 0) {
    const uploadDurations = success.map((r) => r.uploadDuration)
    const aiDurations = success.map((r) => r.aiDuration)
    const totalDurations = success.map((r) => r.totalDuration)

    console.log('─'.repeat(50))
    console.log('          UPLOAD RESPONSE TIME')
    console.log('─'.repeat(50))
    console.log(`Min: ${msToSec(Math.min(...uploadDurations))}s`)
    console.log(`Avg: ${msToSec(uploadDurations.reduce((a, b) => a + b, 0) / uploadDurations.length)}s`)
    console.log(`p95: ${msToSec(percentile(uploadDurations, 95))}s`)
    console.log(`Max: ${msToSec(Math.max(...uploadDurations))}s`)

    console.log('')
    console.log('─'.repeat(50))
    console.log('          AI ANALYSIS TIME')
    console.log('─'.repeat(50))
    console.log(`Min: ${msToSec(Math.min(...aiDurations))}s`)
    console.log(`Avg: ${msToSec(aiDurations.reduce((a, b) => a + b, 0) / aiDurations.length)}s`)
    console.log(`p95: ${msToSec(percentile(aiDurations, 95))}s`)
    console.log(`Max: ${msToSec(Math.max(...aiDurations))}s`)

    console.log('')
    console.log('─'.repeat(50))
    console.log('          TOTAL TIME (per user)')
    console.log('─'.repeat(50))
    console.log(`Min: ${msToSec(Math.min(...totalDurations))}s`)
    console.log(`Avg: ${msToSec(totalDurations.reduce((a, b) => a + b, 0) / totalDurations.length)}s`)
    console.log(`p95: ${msToSec(percentile(totalDurations, 95))}s`)
    console.log(`Max: ${msToSec(Math.max(...totalDurations))}s`)
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

import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'

import { chromium } from 'playwright'

const BASE_URL = process.env.BASE_URL ?? 'https://release.imymemine.kr'
const E2E_LOGIN_PATH = process.env.E2E_LOGIN_PATH ?? '/api/e2e/login'
const DEVICE_UUID = process.env.DEVICE_UUID ?? randomUUID()
const HEADLESS = process.env.HEADLESS !== 'false'
const SLOW_MO = Number(process.env.SLOW_MO ?? '0')
const DEFAULT_TIMEOUT_MS = Number(process.env.DEFAULT_TIMEOUT_MS ?? '15000')
const FEEDBACK_TIMEOUT_MS = Number(process.env.FEEDBACK_TIMEOUT_MS ?? '180000')
const POLL_INTERVAL_MS = Number(process.env.POLL_INTERVAL_MS ?? '2000')
const AUDIO_FILE = process.env.AUDIO_FILE
const DEFAULT_AUDIO_CONTENT_TYPE = 'audio/webm'
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'EXPIRED'])

function assertOk(condition, message) {
  if (!condition) throw new Error(message)
}

async function e2eLogin(page) {
  const url = new URL(E2E_LOGIN_PATH, BASE_URL).toString()
  const response = await page.request.post(url, {
    data: { deviceUuid: DEVICE_UUID },
    headers: { 'Content-Type': 'application/json' },
  })

  if (!response.ok()) {
    const body = await response.text()
    throw new Error(`E2E login failed: ${response.status()} ${response.statusText()} ${body}`)
  }
}

async function runMainChecks(page) {
  await page.goto(new URL('/main', BASE_URL).toString(), { waitUntil: 'domcontentloaded' })

  assertOk(page.url().includes('/main'), `Expected URL to include /main, got: ${page.url()}`)

  await page.getByAltText('profile image').waitFor({ state: 'visible' })
  await page.getByText('카드 수', { exact: true }).waitFor({ state: 'visible' })
  await page.getByText('승리', { exact: true }).waitFor({ state: 'visible' })
  await page.getByText('레벨', { exact: true }).waitFor({ state: 'visible' })
  await page.getByRole('button', { name: '레벨업 모드' }).waitFor({ state: 'visible' })
  await page.getByRole('button', { name: 'PVP 모드' }).waitFor({ state: 'visible' })
}

async function runPvpFlow(page) {
  await page.getByRole('button', { name: 'PVP 모드' }).click()
  await page.waitForURL(/\/pvp/)

  await page.getByText('매칭 입장하기', { exact: true }).waitFor({ state: 'visible' })
  await page.getByText('매칭 만들기', { exact: true }).waitFor({ state: 'visible' })
}

async function runLevelupFlow(page) {
  await page.goto(new URL('/main', BASE_URL).toString(), { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '레벨업 모드' }).click()

  await page.waitForURL(/\/levelup(\?|$)/)
  await page.getByText('카테고리 선택', { exact: true }).waitFor({ state: 'visible' })

  const selectPanel = page.locator('div.bg-secondary').first()
  const categoryButtons = selectPanel.getByRole('button')
  await categoryButtons.first().waitFor({ state: 'visible' })
  await categoryButtons.first().click()

  await page.getByText('키워드 선택', { exact: true }).waitFor({ state: 'visible' })

  const keywordButtons = selectPanel.getByRole('button')
  await keywordButtons.first().waitFor({ state: 'visible' })
  await keywordButtons.first().click()

  await page.getByRole('heading', { name: '카드 만들기' }).waitFor({ state: 'visible' })

  const cardName = `playwright-${Date.now()}`
  await page.locator('input#cardName').fill(cardName)
  await page.getByRole('button', { name: '확인' }).click()

  await page.waitForURL(/\/levelup\/record\?cardId=\d+&attemptId=\d+/)
  await page.getByText('음성으로 말해보세요.', { exact: true }).waitFor({ state: 'visible' })
  await page.getByRole('button', { name: '녹음 완료 및 피드백 받기' }).waitFor({ state: 'visible' })
}

function parseRecordParams(page) {
  const url = new URL(page.url())
  const cardId = Number(url.searchParams.get('cardId') ?? '')
  const attemptId = Number(url.searchParams.get('attemptId') ?? '')
  assertOk(Number.isFinite(cardId) && cardId > 0, `Invalid cardId from URL: ${page.url()}`)
  assertOk(
    Number.isFinite(attemptId) && attemptId > 0,
    `Invalid attemptId from URL: ${page.url()}`,
  )
  return { cardId, attemptId }
}

async function resolveAudioBytes() {
  if (AUDIO_FILE) {
    return readFile(AUDIO_FILE)
  }
  // fallback dummy bytes for upload flow verification
  return Buffer.from('RIFF....WEBM', 'utf8')
}

async function submitAudioViaApis(page, { cardId, attemptId }) {
  const presignedRes = await page.request.post(new URL('/proxy-api/learning/presigned-url', BASE_URL).toString(), {
    data: { attemptId, contentType: DEFAULT_AUDIO_CONTENT_TYPE },
    headers: { 'Content-Type': 'application/json' },
  })
  assertOk(
    presignedRes.ok(),
    `presigned-url failed: ${presignedRes.status()} ${presignedRes.statusText()} ${await presignedRes.text()}`,
  )
  const presignedBody = await presignedRes.json()
  const uploadUrl = presignedBody?.data?.uploadUrl
  const objectKey = presignedBody?.data?.objectKey
  const contentType = presignedBody?.data?.contentType ?? DEFAULT_AUDIO_CONTENT_TYPE
  assertOk(uploadUrl && objectKey, 'Missing uploadUrl/objectKey from presigned response')

  const audioBytes = await resolveAudioBytes()
  const uploadRes = await page.request.put(uploadUrl, {
    data: audioBytes,
    headers: { 'Content-Type': contentType },
  })
  assertOk(
    uploadRes.ok(),
    `audio upload failed: ${uploadRes.status()} ${uploadRes.statusText()} ${await uploadRes.text()}`,
  )

  const completeRes = await page.request.put(
    new URL(`/proxy-api/cards/${cardId}/attempts/${attemptId}/upload-complete`, BASE_URL).toString(),
    {
      data: { objectKey, durationSeconds: 3 },
      headers: { 'Content-Type': 'application/json' },
    },
  )
  assertOk(
    completeRes.ok(),
    `upload-complete failed: ${completeRes.status()} ${completeRes.statusText()} ${await completeRes.text()}`,
  )
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function waitForTerminalAttemptStatus(page, { cardId, attemptId }) {
  const start = Date.now()
  while (Date.now() - start < FEEDBACK_TIMEOUT_MS) {
    const detailsRes = await page.request.get(
      new URL(`/proxy-api/cards/${cardId}/attempts/${attemptId}`, BASE_URL).toString(),
    )
    if (!detailsRes.ok()) {
      await sleep(POLL_INTERVAL_MS)
      continue
    }
    const details = await detailsRes.json()
    const status = details?.data?.status
    if (TERMINAL_STATUSES.has(status)) return status
    await sleep(POLL_INTERVAL_MS)
  }
  throw new Error(`Timed out waiting terminal feedback status (attemptId=${attemptId})`)
}

async function main() {
  const browser = await chromium.launch({
    headless: HEADLESS,
    slowMo: SLOW_MO,
  })

  const context = await browser.newContext()
  const page = await context.newPage()
  page.setDefaultTimeout(DEFAULT_TIMEOUT_MS)

  try {
    await e2eLogin(page)
    await runMainChecks(page)
    await runPvpFlow(page)
    await runLevelupFlow(page)
    const recordParams = parseRecordParams(page)
    await submitAudioViaApis(page, recordParams)
    await page.goto(
      new URL(
        `/levelup/feedback?cardId=${recordParams.cardId}&attemptId=${recordParams.attemptId}`,
        BASE_URL,
      ).toString(),
      { waitUntil: 'domcontentloaded' },
    )
    await page.waitForURL(/\/levelup\/feedback\?cardId=\d+&attemptId=\d+/)
    await page.getByRole('button', { name: '학습 종료하기' }).waitFor({ state: 'visible' })
    const terminalStatus = await waitForTerminalAttemptStatus(page, recordParams)

    console.log('[PASS] Release UI flow completed successfully.')
    console.log(`- BASE_URL: ${BASE_URL}`)
    console.log(`- DEVICE_UUID: ${DEVICE_UUID}`)
    console.log(`- TERMINAL_STATUS: ${terminalStatus}`)
  } finally {
    await context.close()
    await browser.close()
  }
}

main().catch((error) => {
  console.error('[FAIL] Release UI flow failed.')
  console.error(error)
  process.exitCode = 1
})

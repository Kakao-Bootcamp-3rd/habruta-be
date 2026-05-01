/*
 * Habruta release solo-mode load test converted from the Node.js fetch flow.
 *
 * Purpose:
 * - Simulate the real user flow for speaking practice, not a single API ping.
 * - Validate peak traffic where 2,800 concurrent users are already active and
 *   800 additional users enter over 2 minutes.
 * - Keep backend API p95 separate from full-flow p95 because the full flow
 *   includes external S3 upload time. The backend API SLO is p95 <= 1s.
 *
 * Flow:
 * 1. POST /e2e/login/{vuId}
 * 2. GET /categories
 * 3. GET /categories/{categoryId}/keywords
 * 4. POST /cards
 * 5. POST /cards/{cardId}/attempts
 * 6. POST /learning/presigned-url
 * 7. PUT presigned S3 uploadUrl
 *
 * Intentionally omitted:
 * - upload-complete API
 * - polling / feedback result checks
 *
 * Run through the wrapper so logs are saved and VU lines are split by minute:
 * ./run_steady_and_spike_until_upload.sh
 *
 * Direct run:
 * BASE_URL=http://habruta-release-alb-176225423.ap-northeast-2.elb.amazonaws.com \
 * AUDIO_FILE=/Users/wonhyeonseob/dev/projects/App/habruta/be/infra/load/release/assets/sample_speech.webm \
 * k6 run steady_and_spike_until_upload.k6.js
 *
 * Quick smoke with log file:
 * VUS=10 DURATION=30s k6 run steady_and_spike_until_upload.k6.js 2>&1 | tee k6-run.log
 */

import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js'
import exec from 'k6/execution'

const BASE_URL =
  __ENV.BASE_URL || 'http://habruta-release-alb-176225423.ap-northeast-2.elb.amazonaws.com'
const API_PREFIX = __ENV.API_PREFIX || ''
const AUDIO_FILE =
  __ENV.AUDIO_FILE ||
  '/Users/wonhyeonseob/dev/projects/App/habruta/be/infra/load/release/assets/sample_speech.webm'
const AUDIO_CONTENT_TYPE = __ENV.AUDIO_CONTENT_TYPE || 'audio/webm'

const QUICK_VUS = Number(__ENV.VUS || '0')
const QUICK_DURATION = __ENV.DURATION || '0s'
const VU_LOG_INTERVAL_SECONDS = Number(__ENV.VU_LOG_INTERVAL_SECONDS || '15')

const audioBytes = open(AUDIO_FILE, 'b')
const testStartedAt = Date.now()
let lastVuLogBucket = -1
let lastLoggedVus = 0

export const fullFlowDuration = new Trend('full_flow_duration', true)
export const backendApiDuration = new Trend('backend_api_duration', true)
export const s3UploadDuration = new Trend('s3_upload_duration', true)
export const apiLoginDuration = new Trend('api_login_duration', true)
export const apiCategoriesDuration = new Trend('api_categories_duration', true)
export const apiKeywordsDuration = new Trend('api_keywords_duration', true)
export const apiCardsDuration = new Trend('api_cards_duration', true)
export const apiAttemptsDuration = new Trend('api_attempts_duration', true)
export const apiPresignedDuration = new Trend('api_presigned_duration', true)
export const apiS3UploadDuration = new Trend('api_s3_upload_duration', true)

export const flowFailures = new Counter('flow_failures')
export const loginFailures = new Counter('login_failures')
export const categoriesFailures = new Counter('categories_failures')
export const keywordsFailures = new Counter('keywords_failures')
export const cardsFailures = new Counter('cards_failures')
export const attemptsFailures = new Counter('attempts_failures')
export const presignedFailures = new Counter('presigned_failures')
export const s3UploadFailures = new Counter('s3_upload_failures')

export const loginFailureRate = new Rate('login_failure_rate')
export const categoriesFailureRate = new Rate('categories_failure_rate')
export const keywordsFailureRate = new Rate('keywords_failure_rate')
export const cardsFailureRate = new Rate('cards_failure_rate')
export const attemptsFailureRate = new Rate('attempts_failure_rate')
export const presignedFailureRate = new Rate('presigned_failure_rate')
export const s3UploadFailureRate = new Rate('s3_upload_failure_rate')

const apiDurationTrends = {
  login: apiLoginDuration,
  categories: apiCategoriesDuration,
  keywords: apiKeywordsDuration,
  cards: apiCardsDuration,
  attempts: apiAttemptsDuration,
  presigned: apiPresignedDuration,
  s3_upload: apiS3UploadDuration,
}

const apiSummaryRows = [
  ['login', 'POST /e2e/login/{vuId}', 'api_login_duration', 'login_failure_rate'],
  ['categories', 'GET /categories', 'api_categories_duration', 'categories_failure_rate'],
  ['keywords', 'GET /categories/{categoryId}/keywords', 'api_keywords_duration', 'keywords_failure_rate'],
  ['cards', 'POST /cards', 'api_cards_duration', 'cards_failure_rate'],
  ['attempts', 'POST /cards/{cardId}/attempts', 'api_attempts_duration', 'attempts_failure_rate'],
  ['presigned', 'POST /learning/presigned-url', 'api_presigned_duration', 'presigned_failure_rate'],
  ['s3_upload', 'PUT presigned S3 uploadUrl', 'api_s3_upload_duration', 's3_upload_failure_rate'],
]

const defaultStages = [
  { duration: '5m', target: 2800 },
  { duration: '15m', target: 2800 },
  { duration: '2m', target: 3600 },
  { duration: '5m', target: 3600 },
  { duration: '3m', target: 0 },
]

export const options = {
  summaryTrendStats: ['count', 'avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    solo_steady_and_spike_until_upload: QUICK_VUS > 0
      ? {
          executor: 'constant-vus',
          vus: QUICK_VUS,
          duration: QUICK_DURATION === '0s' ? '30s' : QUICK_DURATION,
          gracefulStop: '30s',
        }
      : {
          executor: 'ramping-vus',
          startVUs: 0,
          stages: defaultStages,
          gracefulRampDown: '30s',
          gracefulStop: '1m',
        },
    vu_logger: {
      executor: 'constant-vus',
      vus: 1,
      duration: QUICK_VUS > 0 ? (QUICK_DURATION === '0s' ? '30s' : QUICK_DURATION) : '30m',
      gracefulStop: '1s',
      exec: 'logVuIncrease',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{type:backend_api}': ['p(95)<1000'],
    backend_api_duration: ['p(95)<1000'],
    // Reference only. This includes external S3 upload and must not be used as the backend API SLO.
    full_flow_duration: ['p(95)<15000'],
  },
}

function apiUrl(path) {
  return `${BASE_URL}${API_PREFIX}${path}`
}

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const value = Math.floor(Math.random() * 16)
    const resolved = char === 'x' ? value : (value & 0x3) | 0x8
    return resolved.toString(16)
  })
}

function uniqueVuId() {
  const vu = exec.vu.idInTest
  const iter = exec.scenario.iterationInTest
  return vu * 1000000 + iter
}

function formatKst(timestampMs) {
  return new Date(timestampMs + 9 * 60 * 60 * 1000).toISOString().replace('T', ' ').slice(0, 19)
}

function logVuIncreaseEveryInterval() {
  if (VU_LOG_INTERVAL_SECONDS <= 0) return

  const elapsedSeconds = Math.floor((Date.now() - testStartedAt) / 1000)
  const currentBucket = Math.floor(elapsedSeconds / VU_LOG_INTERVAL_SECONDS)
  if (currentBucket === lastVuLogBucket) return

  const currentVus = Math.max(0, exec.instance.vusActive - 1)
  const increased = currentVus - lastLoggedVus
  const increasedText = increased >= 0 ? `+${increased}` : `${increased}`

  console.log(
    `[VU] ${formatKst(Date.now())} KST 현재 ${currentVus}명 (${lastLoggedVus} -> ${currentVus}, ${increasedText}명 증가)`,
  )

  lastVuLogBucket = currentBucket
  lastLoggedVus = currentVus
}

function addApiMetric(api, response) {
  const trend = apiDurationTrends[api]
  if (trend) {
    trend.add(response.timings.duration)
  }
}

function addBackendMetric(api, response) {
  backendApiDuration.add(response.timings.duration)
  addApiMetric(api, response)
}

function markFailure(api) {
  flowFailures.add(1)

  switch (api) {
    case 'login':
      loginFailures.add(1)
      loginFailureRate.add(true)
      break
    case 'categories':
      categoriesFailures.add(1)
      categoriesFailureRate.add(true)
      break
    case 'keywords':
      keywordsFailures.add(1)
      keywordsFailureRate.add(true)
      break
    case 'cards':
      cardsFailures.add(1)
      cardsFailureRate.add(true)
      break
    case 'attempts':
      attemptsFailures.add(1)
      attemptsFailureRate.add(true)
      break
    case 'presigned':
      presignedFailures.add(1)
      presignedFailureRate.add(true)
      break
    case 's3_upload':
      s3UploadFailures.add(1)
      s3UploadFailureRate.add(true)
      break
  }
}

function markSuccess(api) {
  switch (api) {
    case 'login':
      loginFailureRate.add(false)
      break
    case 'categories':
      categoriesFailureRate.add(false)
      break
    case 'keywords':
      keywordsFailureRate.add(false)
      break
    case 'cards':
      cardsFailureRate.add(false)
      break
    case 'attempts':
      attemptsFailureRate.add(false)
      break
    case 'presigned':
      presignedFailureRate.add(false)
      break
    case 's3_upload':
      s3UploadFailureRate.add(false)
      break
  }
}

function parseJson(response, api) {
  try {
    return response.json()
  } catch (error) {
    markFailure(api)
    return null
  }
}

function backendHeaders(accessToken) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken}`,
  }
}

function login(vuId) {
  const response = http.post(
    apiUrl(`/e2e/login/${vuId}`),
    JSON.stringify({ deviceUuid: uuidv4() }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { type: 'backend_api', api: 'login', name: 'POST /e2e/login/{vuId}' },
    },
  )
  addBackendMetric('login', response)

  const body = parseJson(response, 'login')
  const accessToken = body?.data?.accessToken
  const ok = check(response, {
    'login status is 200': (res) => res.status === 200,
    'login has accessToken': () => Boolean(accessToken),
  })
  if (!ok) {
    markFailure('login')
    return null
  }

  markSuccess('login')
  return accessToken
}

function getCategories(accessToken) {
  const response = http.get(apiUrl('/categories'), {
    headers: { Authorization: `Bearer ${accessToken}` },
    tags: { type: 'backend_api', api: 'categories', name: 'GET /categories' },
  })
  addBackendMetric('categories', response)

  const body = parseJson(response, 'categories')
  const categories = body?.data || []
  const ok = check(response, {
    'categories status is 200': (res) => res.status === 200,
    'categories has item': () => categories.length > 0,
  })
  if (!ok) {
    markFailure('categories')
    return null
  }

  markSuccess('categories')
  return categories.find((category) => !String(category.name || '').includes('테스트')) || categories[0]
}

function getKeyword(accessToken, categoryId) {
  const response = http.get(apiUrl(`/categories/${categoryId}/keywords`), {
    headers: { Authorization: `Bearer ${accessToken}` },
    tags: { type: 'backend_api', api: 'keywords', name: 'GET /categories/{categoryId}/keywords' },
  })
  addBackendMetric('keywords', response)

  const body = parseJson(response, 'keywords')
  const keywords = body?.data?.keywords || []
  const ok = check(response, {
    'keywords status is 200': (res) => res.status === 200,
    'keywords has item': () => keywords.length > 0,
  })
  if (!ok) {
    markFailure('keywords')
    return null
  }

  markSuccess('keywords')
  return keywords[0]
}

function createCard(accessToken, categoryId, keywordId, title) {
  const response = http.post(
    apiUrl('/cards'),
    JSON.stringify({ categoryId, keywordId, title }),
    {
      headers: backendHeaders(accessToken),
      tags: { type: 'backend_api', api: 'cards', name: 'POST /cards' },
    },
  )
  addBackendMetric('cards', response)

  const body = parseJson(response, 'cards')
  const cardId = body?.data?.id
  const ok = check(response, {
    'card creation status is 201': (res) => res.status === 201,
    'card creation has id': () => Boolean(cardId),
  })
  if (!ok) {
    markFailure('cards')
    return null
  }

  markSuccess('cards')
  return cardId
}

function createAttempt(accessToken, cardId) {
  const response = http.post(apiUrl(`/cards/${cardId}/attempts`), null, {
    headers: backendHeaders(accessToken),
    tags: { type: 'backend_api', api: 'attempts', name: 'POST /cards/{cardId}/attempts' },
  })
  addBackendMetric('attempts', response)

  const body = parseJson(response, 'attempts')
  const attemptId = body?.data?.attemptId
  const ok = check(response, {
    'attempt creation status is 201': (res) => res.status === 201,
    'attempt creation has attemptId': () => Boolean(attemptId),
  })
  if (!ok) {
    markFailure('attempts')
    return null
  }

  markSuccess('attempts')
  return attemptId
}

function getPresignedUrl(accessToken, attemptId) {
  const response = http.post(
    apiUrl('/learning/presigned-url'),
    JSON.stringify({ attemptId, contentType: AUDIO_CONTENT_TYPE }),
    {
      headers: backendHeaders(accessToken),
      tags: { type: 'backend_api', api: 'presigned', name: 'POST /learning/presigned-url' },
    },
  )
  addBackendMetric('presigned', response)

  const body = parseJson(response, 'presigned')
  const uploadUrl = body?.data?.uploadUrl
  const ok = check(response, {
    'presigned-url status is 201': (res) => res.status === 201,
    'presigned-url has uploadUrl': () => Boolean(uploadUrl),
  })
  if (!ok) {
    markFailure('presigned')
    return null
  }

  markSuccess('presigned')
  return uploadUrl
}

function uploadToS3(uploadUrl) {
  const response = http.put(uploadUrl, audioBytes, {
    headers: { 'Content-Type': AUDIO_CONTENT_TYPE },
    tags: { type: 'external_s3', api: 's3_upload', name: 'PUT presigned S3 uploadUrl' },
  })
  s3UploadDuration.add(response.timings.duration)
  addApiMetric('s3_upload', response)

  const ok = check(response, {
    's3 upload status is 200': (res) => res.status === 200,
  })
  if (!ok) {
    markFailure('s3_upload')
    return false
  }

  markSuccess('s3_upload')
  return true
}

export default function () {
  const flowStartedAt = Date.now()
  const vuId = uniqueVuId()

  let accessToken
  group('login', () => {
    accessToken = login(vuId)
  })
  if (!accessToken) return

  let category
  group('categories', () => {
    category = getCategories(accessToken)
  })
  if (!category?.id) return

  let keyword
  group('keywords', () => {
    keyword = getKeyword(accessToken, category.id)
  })
  if (!keyword?.id) return

  let cardId
  group('card creation', () => {
    const title = `k6-${Date.now() % 100000}-${vuId}`
    cardId = createCard(accessToken, category.id, keyword.id, title)
  })
  if (!cardId) return

  let attemptId
  group('attempt creation', () => {
    attemptId = createAttempt(accessToken, cardId)
  })
  if (!attemptId) return

  let uploadUrl
  group('presigned url', () => {
    uploadUrl = getPresignedUrl(accessToken, attemptId)
  })
  if (!uploadUrl) return

  let uploaded = false
  group('s3 upload', () => {
    uploaded = uploadToS3(uploadUrl)
  })
  if (!uploaded) return

  fullFlowDuration.add(Date.now() - flowStartedAt)
  sleep(Number(__ENV.SLEEP_SECONDS || '0'))
}

export function logVuIncrease() {
  logVuIncreaseEveryInterval()
  sleep(1)
}

export function handleSummary(data) {
  const summaryLogPath = __ENV.K6_SUMMARY_LOG
  const summary = textSummary(data, { indent: ' ', enableColors: false })
  const output = `${summary}\n\n${buildApiSummary(data)}`

  const result = { stdout: output }
  if (summaryLogPath) {
    result[summaryLogPath] = output
  }
  return result
}

function metricValue(data, metricName, valueName) {
  return data.metrics[metricName]?.values?.[valueName]
}

function formatNumber(value, digits = 2) {
  return Number.isFinite(value) ? value.toFixed(digits) : '-'
}

function formatPercent(value) {
  return Number.isFinite(value) ? `${(value * 100).toFixed(2)}%` : '-'
}

function buildApiSummary(data) {
  const lines = [
    'API latency summary',
    'api         endpoint                              count      avg(ms)      p95(ms)      p99(ms)      max(ms)    fail_rate',
    '----------  ------------------------------------  --------  ----------  ----------  ----------  ----------  ----------',
  ]

  for (const [api, endpoint, durationMetric, failureMetric] of apiSummaryRows) {
    const count = metricValue(data, durationMetric, 'count')
    const avg = metricValue(data, durationMetric, 'avg')
    const p95 = metricValue(data, durationMetric, 'p(95)')
    const p99 = metricValue(data, durationMetric, 'p(99)')
    const max = metricValue(data, durationMetric, 'max')
    const failRate = metricValue(data, failureMetric, 'rate')

    lines.push(
      `${api.padEnd(10)}  ${endpoint.padEnd(36)}  ${formatNumber(count, 0).padStart(8)}  ${formatNumber(avg).padStart(10)}  ${formatNumber(p95).padStart(10)}  ${formatNumber(p99).padStart(10)}  ${formatNumber(max).padStart(10)}  ${formatPercent(failRate).padStart(10)}`,
    )
  }

  return lines.join('\n')
}

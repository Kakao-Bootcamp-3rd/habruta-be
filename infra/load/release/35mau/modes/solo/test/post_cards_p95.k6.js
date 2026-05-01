/*
k6 run /Users/wonhyeonseob/dev/projects/App/habruta/be/infra/load/release/35mau/modes/solo/test/post_cards_p95.k6.js

*/

import http from 'k6/http'
import { check, fail } from 'k6'
import { Counter, Trend } from 'k6/metrics'

const BASE_URL =
  __ENV.BASE_URL || 'http://habruta-release-alb-176225423.ap-northeast-2.elb.amazonaws.com'
const API_PREFIX = __ENV.API_PREFIX || ''
const CATEGORY_ID = Number(__ENV.CATEGORY_ID || '1')
const KEYWORD_ID = Number(__ENV.KEYWORD_ID || '17')
const VUS = Number(__ENV.VUS || '2000')
const DURATION = __ENV.DURATION || '30s'
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || '1000')

export const postCardsDuration = new Trend('post_cards_duration', true)
export const postCardsCount = new Counter('post_cards_count')

export const options = {
  summaryTrendStats: ['count', 'avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    post_cards: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    post_cards_duration: [`p(95)<${P95_THRESHOLD_MS}`],
    'http_req_failed{name:POST /cards}': ['rate<0.01'],
  },
}

function apiUrl(path) {
  return `${BASE_URL}${API_PREFIX}${path}`
}

function formatKst(date) {
  return new Date(date.getTime() + 9 * 60 * 60 * 1000).toISOString().replace('T', ' ').slice(0, 19)
}

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const value = Math.floor(Math.random() * 16)
    const resolved = char === 'x' ? value : (value & 0x3) | 0x8
    return resolved.toString(16)
  })
}

function login() {
  const vuId = (__VU * 1000000) + __ITER
  const response = http.post(
    apiUrl(`/e2e/login/${vuId}`),
    JSON.stringify({ deviceUuid: uuidv4() }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /e2e/login' },
    },
  )

  if (response.status !== 200) {
    fail(`login failed: ${response.status} ${response.body}`)
  }

  const accessToken = response.json('data.accessToken')
  if (!accessToken) {
    fail('login failed: missing accessToken')
  }

  return accessToken
}

export default function () {
  const accessToken = login()
  const title = `k${Date.now() % 100000}-${__VU}`

  const response = http.post(
    apiUrl('/cards'),
    JSON.stringify({
      categoryId: CATEGORY_ID,
      keywordId: KEYWORD_ID,
      title,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      tags: { name: 'POST /cards' },
    },
  )

  postCardsDuration.add(response.timings.duration)
  postCardsCount.add(1)

  check(response, {
    'POST /cards status is 201': (res) => res.status === 201,
  })
}

export function handleSummary(data) {
  const metric = data.metrics.post_cards_duration
  const values = metric?.values || {}
  const testEndedAt = new Date()
  const elapsedMs = data.state?.testRunDurationMs ?? 0
  const testStartedAt = new Date(testEndedAt.getTime() - elapsedMs)

  console.log('')
  console.log('=== POST /cards p95 ===')
  console.log(`started_at_kst: ${formatKst(testStartedAt)} KST`)
  console.log(`ended_at_kst: ${formatKst(testEndedAt)} KST`)
  console.log(`started_at_utc: ${testStartedAt.toISOString()}`)
  console.log(`ended_at_utc: ${testEndedAt.toISOString()}`)
  console.log(`elapsed: ${(elapsedMs / 1000).toFixed(2)}s`)
  console.log(`duration_count: ${values.count ?? 0}`)
  console.log(`post_cards_count: ${data.metrics.post_cards_count?.values?.count ?? 0}`)
  console.log(`avg: ${values.avg?.toFixed(2) ?? 'n/a'}ms`)
  console.log(`p95: ${values['p(95)']?.toFixed(2) ?? 'n/a'}ms`)
  console.log(`p99: ${values['p(99)']?.toFixed(2) ?? 'n/a'}ms`)
  console.log(`max: ${values.max?.toFixed(2) ?? 'n/a'}ms`)

  return {}
}

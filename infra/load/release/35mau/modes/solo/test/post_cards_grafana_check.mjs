import { randomUUID } from 'node:crypto'

// One-shot Grafana check for POST /cards.
// Flow: login -> categories -> keywords -> POST /cards

const BASE_URL =
  process.env.BASE_URL ?? 'http://habruta-release-alb-176225423.ap-northeast-2.elb.amazonaws.com'
const API_PREFIX = process.env.API_PREFIX ?? ''
const E2E_LOGIN_PATH = '/e2e/login'

const apiUrl = (path) => new URL(`${API_PREFIX}${path}`, BASE_URL).toString()

async function requestJson(label, url, options = {}) {
  const start = performance.now()
  const response = await fetch(url, options)
  const headersAt = performance.now()
  const text = await response.text()
  const bodyAt = performance.now()

  if (!response.ok) {
    throw new Error(`${label} failed: ${response.status} ${text}`)
  }

  const body = JSON.parse(text)
  const parsedAt = performance.now()

  console.log(
    `${label} status=${response.status} total=${Math.round(parsedAt - start)}ms ` +
      `ttfb=${Math.round(headersAt - start)}ms ` +
      `body=${Math.round(bodyAt - headersAt)}ms ` +
      `json=${Math.round(parsedAt - bodyAt)}ms`,
  )

  return body
}

async function main() {
  const vuId = Date.now() % 100000
  const deviceUuid = randomUUID()

  console.log(`[Grafana Check] POST /cards one-shot`)
  console.log(`- BASE_URL: ${BASE_URL}`)
  console.log(`- API_PREFIX: ${API_PREFIX || '(empty)'}`)
  console.log(`- DEVICE_UUID: ${deviceUuid}`)
  console.log('')

  const loginBody = await requestJson(
    'POST /e2e/login',
    apiUrl(`${E2E_LOGIN_PATH}/${vuId}`),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceUuid }),
    },
  )

  const accessToken = loginBody?.data?.accessToken
  if (!accessToken) {
    throw new Error('Missing accessToken')
  }

  const categoriesBody = await requestJson('GET /categories', apiUrl('/categories'), {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  const categories = categoriesBody?.data ?? []

  const category = categories.find((item) => !/테스트/.test(item.name)) ?? categories[0]
  if (!category) {
    throw new Error('No category found')
  }

  const keywordsBody = await requestJson(
    'GET /categories/{id}/keywords',
    apiUrl(`/categories/${category.id}/keywords`),
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    },
  )
  const keywords = keywordsBody?.data?.keywords ?? []

  const keyword = keywords[0]
  if (!keyword) {
    throw new Error(`No keyword found for categoryId=${category.id}`)
  }

  const cardTitle = `g${Date.now() % 100000}`
  const cardBody = await requestJson('POST /cards', apiUrl('/cards'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({
      categoryId: category.id,
      keywordId: keyword.id,
      title: cardTitle,
    }),
  })

  console.log('')
  console.log('[Result]')
  console.log(JSON.stringify({
    categoryId: category.id,
    keywordId: keyword.id,
    title: cardTitle,
    card: cardBody?.data,
  }, null, 2))
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})

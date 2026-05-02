const { test, expect } = require('@playwright/test')
const {
  resolveRealBackendConfig,
  loginViaApi,
  loginViaLoginModal,
  assertAuthenticatedProfile
} = require('./support/authRegressionHelper')

const REAL_BACKEND_E2E = process.env.REAL_BACKEND_E2E === '1'

test.describe('Authenticated Regression (Real Backend)', () => {
  test.skip(!REAL_BACKEND_E2E, 'Set REAL_BACKEND_E2E=1 to run authenticated regression on a real backend')

  test('protected route should redirect to login when not authenticated', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await page.goto(`${config.baseUrl}/setting/profile`)
    await expect(page).toHaveURL(/\/login/)
    await expect(page.locator('.login-page')).toBeVisible()
  })

  test('login page submit should create authenticated session', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await page.goto(`${config.baseUrl}/login?redirect=/`)
    await loginViaLoginModal(page, config)
    await assertAuthenticatedProfile(page, config)
  })

  test('submissions api should support authenticated filter and pagination interactions', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await loginViaApi(page, config)

    const firstPageResp = await page.request.get(`${config.baseUrl}/api/submissions`, {
      params: {
        myself: '1',
        username: config.username,
        limit: '12',
        offset: '0'
      }
    })
    expect(firstPageResp.ok()).toBeTruthy()
    const firstPagePayload = await firstPageResp.json()
    expect(firstPagePayload.error).toBeNull()
    expect(firstPagePayload.data).toBeTruthy()
    expect(Array.isArray(firstPagePayload.data.results)).toBeTruthy()

    const nextPageResp = await page.request.get(`${config.baseUrl}/api/submissions`, {
      params: {
        myself: '1',
        username: config.username,
        limit: '12',
        offset: '12'
      }
    })
    expect(nextPageResp.ok()).toBeTruthy()
    const nextPagePayload = await nextPageResp.json()
    expect(nextPagePayload.error).toBeNull()

    const firstResult = firstPagePayload.data.results[0]
    if (firstResult && typeof firstResult.result !== 'undefined') {
      const resultFilterResp = await page.request.get(`${config.baseUrl}/api/submissions`, {
        params: {
          myself: '1',
          username: config.username,
          result: String(firstResult.result),
          limit: '12',
          offset: '0'
        }
      })
      expect(resultFilterResp.ok()).toBeTruthy()
      const resultFilterPayload = await resultFilterResp.json()
      expect(resultFilterPayload.error).toBeNull()
    }
  })
})

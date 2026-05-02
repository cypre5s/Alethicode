const { test, expect } = require('@playwright/test')
const { loginViaApi, resolveRealBackendConfig } = require('./support/authRegressionHelper')
const { createRouteCatalog } = require('./support/replacementConfig')
const { gotoStableRoute } = require('./support/replacementHelpers')

test.describe('Admin runtime regression', () => {
  test('admin route catalog should render without runtime errors', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await loginViaApi(page, config)
    const { adminRoutes } = createRouteCatalog(config, {})

    for (const route of adminRoutes) {
      const pageErrors = []
      const onPageError = error => {
        pageErrors.push(String(error))
      }
      const onConsole = message => {
        if (message.type() === 'error') {
          pageErrors.push(`console:${message.text()}`)
        }
      }

      page.on('pageerror', onPageError)
      page.on('console', onConsole)
      const visitResult = await gotoStableRoute(page, config.baseUrl, route)
      page.removeListener('pageerror', onPageError)
      page.removeListener('console', onConsole)

      expect(visitResult.readyMatched, `[${route.name}] should match ready selector`).toBeTruthy()
      expect(pageErrors, `[${route.name}] should not emit runtime errors`).toEqual([])
    }
  })
})

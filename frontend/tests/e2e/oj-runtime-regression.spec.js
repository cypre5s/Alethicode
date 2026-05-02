const { test, expect } = require('@playwright/test')
const { loginViaApi, resolveRealBackendConfig } = require('./support/authRegressionHelper')
const { createRouteCatalog } = require('./support/replacementConfig')
const { gotoStableRoute, discoverSeedData } = require('./support/replacementHelpers')

function createOjRuntimeRoutes(config, seedData) {
  const { userRoutes } = createRouteCatalog(config, seedData)
  const problemId = seedData.problemId || 'PPT7-12'

  return [
    ...userRoutes,
    {
      name: 'oj-problem-list-auth',
      area: 'oj',
      authMode: 'user',
      path: '/problem',
      readySelector: '.problem-list, .problem-list-page, .el-table'
    },
    {
      name: 'oj-problem-detail-auth',
      area: 'oj',
      authMode: 'user',
      path: `/problem/${problemId}`,
      readySelector: '.problem-container, #problem-content, .problem-page'
    },
    {
      name: 'oj-submission-list-auth',
      area: 'oj',
      authMode: 'user',
      path: '/status',
      readySelector: '.submission-list, .status-wrapper, .el-table'
    }
  ]
}

test.describe('OJ runtime regression', () => {
  test('oj route catalog should render without runtime errors', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await loginViaApi(page, config)
    const seedData = await discoverSeedData(page, config.baseUrl, config)
    const ojRoutes = createOjRuntimeRoutes(config, seedData)

    for (const route of ojRoutes) {
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

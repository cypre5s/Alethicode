const { test, expect } = require('@playwright/test')
const { loginViaApi, resolveRealBackendConfig } = require('./support/authRegressionHelper')

test.describe('Authenticated navbar navigation', () => {
  test('top nav and dropdown routes should navigate without runtime errors', async ({ page }) => {
    const config = resolveRealBackendConfig()
    const pageErrors = []

    page.on('pageerror', error => {
      pageErrors.push(String(error))
    })

    page.on('console', message => {
      if (message.type() === 'error') {
        pageErrors.push(`console:${message.text()}`)
      }
    })

    await loginViaApi(page, config)
    await page.goto(`${config.baseUrl}/user-home`, { waitUntil: 'networkidle' })

    const topMenuItems = page.locator('.oj-menu > .el-menu-item')
    await expect(topMenuItems.filter({ hasText: '问题' })).toBeVisible()
    await topMenuItems.filter({ hasText: '问题' }).click()
    await expect(page).toHaveURL(/\/problem$/)

    await page.goto(`${config.baseUrl}/user-home`, { waitUntil: 'networkidle' })
    await page.locator('.drop-menu .el-button--text').click()
    await page.locator('.el-dropdown-menu__item').filter({ hasText: '我的设置' }).click()
    await expect(page).toHaveURL(/\/setting\/profile$/)

    expect(pageErrors).toEqual([])
  })
})

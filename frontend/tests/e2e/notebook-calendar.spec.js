/**
 * 错题本日历视图 E2E。
 */
const { test, expect } = require('@playwright/test')
const { loginViaApi, resolveRealBackendConfig } = require('./support/authRegressionHelper')

const REAL = process.env.REAL_BACKEND_E2E === '1'

test.describe('Phase 4 · notebook-calendar', () => {
  test.skip(!REAL, 'requires real backend (REAL_BACKEND_E2E=1)')

  test('calendar view loads, exposes 6×7 grid and switches to archive', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await loginViaApi(page, config)

    await page.goto(`${config.baseUrl}/user/notebook?view=calendar`)
    await page.waitForLoadState('networkidle')

    // 至少 28 个 cell（最少 4 周可见）；新组件用 .notebook-cell class
    const cells = page.locator('.notebook-cell')
    await expect(cells.first()).toBeVisible({ timeout: 15000 })
    const count = await cells.count()
    expect(count).toBeGreaterThanOrEqual(28)

    // 切换到归档视图。
    await page.locator('.nb-tab', { hasText: '错题档案' }).click()
    await expect(page).toHaveURL(/view=archive/)
    await expect(page.locator('.nav-sidebar-title, .nav-empty')).toBeVisible({ timeout: 10000 })
  })
})

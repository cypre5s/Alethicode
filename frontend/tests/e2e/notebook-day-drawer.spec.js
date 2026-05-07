/**
 * 日历日格到 DayDrawer 抽屉的 E2E。
 */
const { test, expect } = require('@playwright/test')
const { loginViaApi, resolveRealBackendConfig } = require('./support/authRegressionHelper')

const REAL = process.env.REAL_BACKEND_E2E === '1'

test.describe('Phase 4 · notebook-day-drawer', () => {
  test.skip(!REAL, 'requires real backend (REAL_BACKEND_E2E=1)')

  test('clicking a populated cell opens DayDrawer with review CTA', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await loginViaApi(page, config)

    await page.goto(`${config.baseUrl}/user/notebook?view=calendar`)
    await page.waitForLoadState('networkidle')

    const populatedCell = page.locator('.notebook-cell.has-items').first()
    await expect(populatedCell).toBeVisible({ timeout: 15000 })
    await populatedCell.click()

    await expect(page.locator('.ndd-panel')).toBeVisible({ timeout: 5000 })
    const goReview = page.locator('.ndd-go-btn', { hasText: '去复习' }).first()
    if (await goReview.isVisible().catch(() => false)) {
      await goReview.click()
      await expect(page).toHaveURL(/error-review-package\?ctx=/)
      await expect(page.locator('.erp-container, .erp-loading, .erp-empty')).toBeVisible({ timeout: 15000 })
    }
  })
})

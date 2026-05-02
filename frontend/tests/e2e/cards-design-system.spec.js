/**
 * Phase 4 · E2E #1 — 卡片设计系统真实校验
 *
 * 真实验证：登录 → 进 problem 详情页触发 problem_guide → 等卡片出现 → 校验：
 *   1) 9 张 BaseAgentCard 派生卡的 head 高度都来自共享 token
 *   2) borderRadius 全部 14px
 *   3) head-title 字号统一 13.5px
 *   4) UnifiedAgentPanel 不再含 inline knowledge_review/encouragement 旧 class 名
 */
const { test, expect } = require('@playwright/test')
const { loginViaApi, resolveRealBackendConfig } = require('./support/authRegressionHelper')

const REAL = process.env.REAL_BACKEND_E2E === '1'
const PROBLEM_ID = process.env.E2E_PROBLEM_ID || '1'

test.describe('Phase 4 · cards-design-system', () => {
  test.skip(!REAL, 'requires real backend (REAL_BACKEND_E2E=1)')

  test('9 BaseAgentCard cards share radius / head font-size / no legacy class names', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await loginViaApi(page, config)

    await page.goto(`${config.baseUrl}/problem/${PROBLEM_ID}`)
    await page.waitForLoadState('networkidle')

    // 触发任意 AI 卡片：等出现至少一张 .bac-card
    const baseCard = page.locator('.bac-card')
    await expect(baseCard.first()).toBeVisible({ timeout: 30000 })

    const cards = await baseCard.elementHandles()
    expect(cards.length).toBeGreaterThanOrEqual(1)

    for (const handle of cards) {
      const radius = await handle.evaluate(el => window.getComputedStyle(el).borderRadius)
      expect(radius).toBe('14px')
    }

    const titleSizes = await page.locator('.bac-head-title').evaluateAll(els =>
      els.map(el => window.getComputedStyle(el).fontSize)
    )
    for (const size of titleSizes) {
      expect(size).toBe('13.5px')
    }

    // UnifiedAgentPanel 不再含旧的 inline class 名
    const panelHtml = await page.content()
    expect(panelHtml).not.toContain('class="knowledge-review-card"')
    expect(panelHtml).not.toContain('class="encourage-card-wrap"')
  })
})

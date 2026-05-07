/**
 * 单题级评分核心路径 E2E。
 *
 * 1) 创建一个 review package（API 直建以加速）
 * 2) 进 /error-review-package?ctx=...
 * 3) FSRS 头条可见（fsrs_state / due_at）
 * 4) 提交一题 AC（直接调 ratingService 模拟 submitted=true）
 * 5) 「再练一题」按钮 → 触发 again → 等新题 append
 * 6) 新题卡有 .is-just-added 高亮，scrollIntoView
 * 7) DB 校验：ai_error_review_problem.user_rating='again' 命中 1 条
 */
const { test, expect } = require('@playwright/test')
const { execFileSync } = require('child_process')
const { loginViaApi, resolveRealBackendConfig } = require('./support/authRegressionHelper')
const { encodeRouteCtx } = require('../../src/utils/urlCipher')

const REAL = process.env.REAL_BACKEND_E2E === '1'
const POSTGRES_CONTAINER = process.env.E2E_POSTGRES_CONTAINER || 'java-oj-postgres'

function execSql(sql) {
  return execFileSync('docker', [
    'exec', POSTGRES_CONTAINER, 'psql', '-U', 'onlinejudge', '-d', 'alethicode', '-tA', '-c', sql
  ], { stdio: ['pipe', 'pipe', 'pipe'] }).toString().trim()
}

test.describe('Phase 4 · review-package-rating', () => {
  test.skip(!REAL, 'requires real backend (REAL_BACKEND_E2E=1)')

  test('rate again triggers AI similar problem append + flash + db record', async ({ page }) => {
    const config = resolveRealBackendConfig()
    await loginViaApi(page, config)

    // 通过 API 创建 review package。
    const createResp = await page.request.post(`${config.baseUrl}/api/ai/review-packages`, {
      data: { error_taxonomy: 'logic_error', language_pack_id: 1, trigger: 'wrong_answer' }
    })
    expect(createResp.ok()).toBeTruthy()
    const packageId = (await createResp.json()).data.id

    // 进入页面。
    await page.goto(`${config.baseUrl}/error-review-package?ctx=${encodeURIComponent(encodeRouteCtx({ pkg: packageId }))}`)
    await page.waitForLoadState('networkidle')

    // 确认 FSRS 头部可见。
    await expect(page.locator('.rph-fsrs-state')).toBeVisible({ timeout: 15000 })

    // 直接通过 DB 模拟第一题已 AC 提交。
    execSql(`update ai_error_review_problem set submitted=true, is_correct=true where package_id='${packageId}' order by sequence asc limit 1`)
    await page.reload()
    await page.waitForLoadState('networkidle')

    // 点击“再练一题”。
    const againBtn = page.locator('button.el-button', { hasText: '再练一题' }).first()
    await expect(againBtn).toBeVisible({ timeout: 15000 })
    const beforeCount = await page.locator('.rpc-card').count()
    await againBtn.click()

    // 等待新题出现，AI 生成最长等待 30 秒。
    await expect.poll(async () => page.locator('.rpc-card').count(), { timeout: 30_000 }).toBeGreaterThan(beforeCount)
    await expect(page.locator('.rpc-card.is-just-added')).toBeVisible({ timeout: 5000 })

    // 2 秒后高亮应自动清除。
    await page.waitForTimeout(2200)
    await expect(page.locator('.rpc-card.is-just-added')).toHaveCount(0)

    // 通过 DB 校验评分记录和新 AI 题标记。
    const ratingCount = parseInt(execSql(`select count(*) from ai_error_review_problem where package_id='${packageId}' and user_rating='again'`), 10)
    expect(ratingCount).toBe(1)
    const aiCount = parseInt(execSql(`select count(*) from ai_error_review_problem where package_id='${packageId}' and is_ai_generated=true`), 10)
    expect(aiCount).toBeGreaterThanOrEqual(1)
  })
})

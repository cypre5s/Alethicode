/**
 * Playwright 跑「点击邮件链接 → 设新密码 → 用新密码登录」完整闭环。
 *
 * 前置：DB 里 user id=68 (reset_mail_demo_1777798096) 已写 token。
 * 用法：
 *   TOKEN=oNEIpLEfrJH3gHwTmUwFLTkyDamYNOlk \
 *   USERNAME=reset_mail_demo_1777798096 \
 *   node tests/e2e/_reset_flow.js
 */

const { chromium } = require('playwright')
const path = require('path')
const fs = require('fs')

const BASE = process.env.BASE_URL || 'http://47.98.184.170'
const TOKEN = process.env.TOKEN
const USERNAME = process.env.USERNAME || 'reset_mail_demo_1777798096'
const NEW_PASS = process.env.NEW_PASS || `Reset_${Math.floor(Math.random() * 999999)}!`
const OUT_DIR = process.env.OUT_DIR || '/home/cypress/Alethicode/.cursor/scratch/guide-screenshots'

if (!TOKEN) {
  console.error('TOKEN env required')
  process.exit(1)
}

if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true })

function log(msg) {
  const ts = new Date().toISOString().replace('T', ' ').slice(0, 19)
  console.log(`[${ts}] ${msg}`)
}

async function shot(page, file) {
  const out = path.join(OUT_DIR, file)
  await page.screenshot({ path: out, fullPage: false })
  log(`  ✓ ${file} (${(fs.statSync(out).size / 1024).toFixed(0)} KB)`)
}

async function main() {
  log(`BASE=${BASE}`)
  log(`TOKEN=${TOKEN}`)
  log(`USERNAME=${USERNAME}`)
  log(`NEW_PASS=${NEW_PASS}`)

  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
  const page = await ctx.newPage()

  // 拦截 /api/captcha 拿到明文 code（dev/test mode 直接返回 code）
  let captchaCode = null
  page.on('response', async (resp) => {
    if (resp.url().includes('/api/captcha') && resp.request().method() === 'GET') {
      try {
        const body = await resp.json()
        // 后端返回 {"data":{"captcha":"4859"}}，但前端包了一层
        const data = body && body.data
        if (data && typeof data === 'object' && data.captcha) {
          captchaCode = String(data.captcha)
          log(`  intercepted captcha code = ${captchaCode}`)
        } else if (typeof data === 'string') {
          // 也许是 base64 data:image/...
          log(`  captcha API returned string len=${data.length} (image base64?)`)
        }
      } catch (e) {
        log(`  captcha resp not JSON`)
      }
    }
  })

  log('Step 1 · 打开 /reset-password/<token>')
  await page.goto(`${BASE}/reset-password/${TOKEN}`, { waitUntil: 'networkidle', timeout: 30000 })
  await page.waitForTimeout(1500)
  await shot(page, '40_reset_page_loaded.png')

  log('Step 2 · 填新密码 + 确认密码')
  // 前端模板：v-model formReset.password / passwordAgain / captcha
  // 没有 name 属性，按 type=password 顺序定位
  const passInputs = page.locator('input[type="password"]')
  await passInputs.nth(0).fill(NEW_PASS)
  await passInputs.nth(1).fill(NEW_PASS)

  log('Step 3 · 等 captcha API response 拿 code 后填进去')
  // 等最多 3 秒看是否 captcha 拿到
  for (let i = 0; i < 12 && !captchaCode; i++) {
    await page.waitForTimeout(250)
  }
  if (!captchaCode) {
    log('  !! 还没拿到 captcha code，主动点 captcha 图触发刷新')
    await page.locator('.captcha-img').click().catch(() => { })
    await page.waitForTimeout(800)
  }
  if (!captchaCode) {
    log('  !! 仍没拿到 captcha；后端返回的可能不是明文')
    await shot(page, '41_no_captcha_state.png')
  }

  if (captchaCode) {
    // captcha input：v-model formReset.captcha
    const captchaInput = page.locator('input[placeholder*="证"], .captcha-row input.f-input').first()
    await captchaInput.fill(captchaCode)
    await shot(page, '41_reset_filled.png')

    log('Step 4 · 点重置按钮')
    await page.locator('.login-btn').first().click()
    await page.waitForTimeout(2500)
    await shot(page, '42_reset_after_submit.png')

    // 检查 success state（form-body.success-body 或 success-title）
    const successVisible = await page.locator('.success-title, .success-body').isVisible().catch(() => false)
    log(`  reset success state visible = ${successVisible}`)

    if (successVisible) {
      log('Step 5 · 点「登录」按钮跳转到 /login')
      await page.locator('.success-green, button:has-text("登录")').first().click().catch(() => { })
      await page.waitForTimeout(2000)
      await shot(page, '43_after_reset_to_login.png')

      // 真用新密码 + username 登录
      log('Step 6 · 用 USERNAME / NEW_PASS 真登录')
      const usernameInput = page.locator('input[type="text"]').first()
      const passwordInput = page.locator('input[type="password"]').first()
      await usernameInput.waitFor({ timeout: 5000 })
      await usernameInput.fill(USERNAME)
      await passwordInput.fill(NEW_PASS)

      // 隐私政策 checkbox（如有）
      const privacyChecks = ['.el-checkbox__input:not(.is-checked)', 'input[type="checkbox"]:not(:checked)']
      for (const sel of privacyChecks) {
        try {
          const cb = page.locator(sel).first()
          if (await cb.isVisible({ timeout: 600 }).catch(() => false)) {
            await cb.click({ timeout: 1500 })
            log(`  privacy ticked via ${sel}`)
            break
          }
        } catch { /* try next */ }
      }
      await shot(page, '44_login_with_new_pass_filled.png')

      const loginBtn = page.locator('button:has-text("登录"), button[type="submit"]').first()
      await loginBtn.click({ timeout: 5000 })
      await page.waitForTimeout(3000)

      // 同意隐私（如果弹）
      for (const sel of ['text=同意', 'text=同意并继续使用']) {
        try {
          const b = page.locator(sel).first()
          if (await b.isVisible({ timeout: 1500 }).catch(() => false)) {
            await b.click()
            log(`  privacy dismissed via ${sel}`)
            break
          }
        } catch { /* try next */ }
      }
      await page.waitForTimeout(2000)
      await shot(page, '45_after_login_with_new_pass.png')

      // 验证已登录：调 /api/profile 看 username
      const profile = await page.evaluate(async () => {
        const r = await fetch('/api/profile', { credentials: 'include' })
        const j = await r.json()
        return j.data ? j.data.username : null
      })
      log(`  profile.username after new-password login = ${profile}`)
      if (profile === USERNAME) {
        log('  PASS 完整闭环成功（邮件 token → 重置密码 → 新密码登录）')
      } else {
        log(`  FAIL profile.username = ${profile} (expected ${USERNAME})`)
      }
    } else {
      log('  !! reset 没成功，看截图 42 排查')
    }
  }

  await ctx.close()
  await browser.close()
  log('done')
}

main().catch(err => {
  console.error('fatal:', err)
  process.exit(1)
})

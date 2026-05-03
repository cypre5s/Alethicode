/**
 * 一次性 Playwright 截图脚本：
 * - 抓 ECS 上 /guide 各章节（默认 funMode=off + funMode=on 对比）
 * - 用 lbx / 231310215 登录验证 NavBar 双入口
 *
 * 不进 jest/playwright test 套件，单纯节点跑：
 *   node tests/e2e/_guide_screenshots.js
 *
 * 截图输出到 /home/cypress/Alethicode/.cursor/scratch/guide-screenshots/
 */

const { chromium } = require('playwright')
const path = require('path')
const fs = require('fs')

const BASE = process.env.BASE_URL || 'http://47.98.184.170'
const OUT_DIR = process.env.OUT_DIR || '/home/cypress/Alethicode/.cursor/scratch/guide-screenshots'
const VIEWPORT = { width: 1440, height: 900 }

const SECTIONS = ['welcome', 'ai', 'context', 'qa', 'flow', 'tips', 'faq', 'tour']

if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true })

function log(msg) {
  const ts = new Date().toISOString().replace('T', ' ').slice(0, 19)
  console.log(`[${ts}] ${msg}`)
}

async function dismissPrivacy(page) {
  // 隐私政策弹窗：常见做法是 Element Plus / 自研对话框
  // 等待最多 4s 看是否出现
  const candidates = [
    'text=同意',
    'text=接受',
    'text=同意并继续',
    'role=button[name=/同意|接受|确认|继续/]',
    '.privacy-dialog .el-button--primary',
    '.privacy-policy-modal button.primary'
  ]
  for (const sel of candidates) {
    try {
      const loc = page.locator(sel).first()
      if (await loc.isVisible({ timeout: 1000 }).catch(() => false)) {
        await loc.click({ timeout: 2000 })
        log(`  privacy dismissed via ${sel}`)
        return true
      }
    } catch {
      // try next
    }
  }
  return false
}

async function fullPageShot(page, file) {
  const out = path.join(OUT_DIR, file)
  await page.screenshot({ path: out, fullPage: true })
  const size = fs.statSync(out).size
  log(`  ✓ ${file}  (${(size / 1024).toFixed(0)} KB)`)
}

async function viewportShot(page, file) {
  const out = path.join(OUT_DIR, file)
  await page.screenshot({ path: out, fullPage: false })
  const size = fs.statSync(out).size
  log(`  ✓ ${file}  (${(size / 1024).toFixed(0)} KB)`)
}

async function scrollToSection(page, id) {
  await page.evaluate((sid) => {
    const el = document.getElementById(sid)
    if (el) el.scrollIntoView({ behavior: 'instant', block: 'start' })
  }, id)
  await page.waitForTimeout(500)
}

async function main() {
  log(`OUT_DIR=${OUT_DIR}`)
  log(`BASE=${BASE}`)
  const browser = await chromium.launch({ headless: true })
  try {
    // ============ Pass 1: funMode 默认 off（Notion 风） ============
    log('Pass 1 · funMode=off (Notion / Claude 风默认)')
    const ctx1 = await browser.newContext({ viewport: VIEWPORT, locale: 'zh-CN' })
    const page1 = await ctx1.newPage()
    await page1.goto(`${BASE}/guide`, { waitUntil: 'networkidle', timeout: 30000 })
    await page1.waitForTimeout(800)

    // 1) hero 顶部
    await page1.evaluate(() => window.scrollTo(0, 0))
    await page1.waitForTimeout(400)
    await viewportShot(page1, '01_hero_default.png')

    // 2) 每个 section 单独截一张可视区
    let idx = 2
    for (const sid of SECTIONS) {
      await scrollToSection(page1, sid)
      await viewportShot(page1, `${String(idx).padStart(2, '0')}_section_${sid}_default.png`)
      idx += 1
    }

    // 3) 全页长截图作为总览
    await page1.evaluate(() => window.scrollTo(0, 0))
    await page1.waitForTimeout(400)
    await fullPageShot(page1, `${String(idx).padStart(2, '0')}_FULL_default.png`)

    await ctx1.close()

    // ============ Pass 2: funMode = on（趣味模式） ============
    log('Pass 2 · funMode=on (趣味模式)')
    const ctx2 = await browser.newContext({ viewport: VIEWPORT, locale: 'zh-CN' })
    const page2 = await ctx2.newPage()
    await page2.goto(`${BASE}/guide`, { waitUntil: 'networkidle', timeout: 30000 })
    await page2.waitForTimeout(500)
    // 点击「打开趣味模式」
    await page2.locator('.manual-page__fun-toggle').click()
    await page2.waitForTimeout(800)

    await page2.evaluate(() => window.scrollTo(0, 0))
    await page2.waitForTimeout(400)
    await viewportShot(page2, '11_hero_funMode.png')

    // gallery 这次会出现
    const sectionsFun = ['welcome', 'ai', 'context', 'qa', 'flow', 'tips', 'faq', 'tour', 'gallery', 'feedback']
    let idx2 = 12
    for (const sid of sectionsFun) {
      await scrollToSection(page2, sid)
      await viewportShot(page2, `${String(idx2).padStart(2, '0')}_section_${sid}_funMode.png`)
      idx2 += 1
    }

    await page2.evaluate(() => window.scrollTo(0, 0))
    await page2.waitForTimeout(400)
    await fullPageShot(page2, `${String(idx2).padStart(2, '0')}_FULL_funMode.png`)

    await ctx2.close()

    // ============ Pass 3: lbx 登录后 NavBar 双入口 ============
    log('Pass 3 · 登录 lbx → 验证 NavBar 双入口')
    const ctx3 = await browser.newContext({ viewport: VIEWPORT, locale: 'zh-CN' })
    const page3 = await ctx3.newPage()
    await page3.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 30000 })
    await page3.waitForTimeout(800)

    // 隐私政策（如果出现）
    await dismissPrivacy(page3)

    // 截登录页（看是否真的有隐私政策需要勾）
    await viewportShot(page3, '30_login_page.png')

    // 找登录表单 - 用户名 / 密码 / 提交
    // ApplyResetPassword.vue 的入口在登录页，这里只测登录
    try {
      // 项目用 element-plus 的 input
      const usernameInput = page3.locator('input[placeholder*="用户名"], input[name*="username"], input[type="text"]').first()
      const passwordInput = page3.locator('input[type="password"]').first()
      await usernameInput.waitFor({ timeout: 5000 })
      await usernameInput.fill('lbx')
      await passwordInput.fill('231310215')

      // 隐私政策可能是 checkbox（element-plus 用 .el-checkbox__input）
      const privacyChecks = [
        '.el-checkbox__input:not(.is-checked)',
        'input[type="checkbox"]:not(:checked)',
        'role=checkbox[name=/隐私|协议|同意/]'
      ]
      for (const sel of privacyChecks) {
        try {
          const cb = page3.locator(sel).first()
          if (await cb.isVisible({ timeout: 800 }).catch(() => false)) {
            await cb.click({ timeout: 1500 })
            log(`  privacy checkbox ticked via ${sel}`)
            break
          }
        } catch { /* try next */ }
      }

      await viewportShot(page3, '31_login_filled.png')

      // 点登录按钮
      const loginBtn = page3.locator('button:has-text("登录"), button[type="submit"]').first()
      await loginBtn.click({ timeout: 5000 })
      // 登录后会跳到首页或最近一页
      await page3.waitForTimeout(3000)
      await dismissPrivacy(page3)
      await viewportShot(page3, '32_after_login_landing.png')

      // 进 /guide 看 NavBar 双入口
      await page3.goto(`${BASE}/guide`, { waitUntil: 'networkidle', timeout: 30000 })
      await page3.waitForTimeout(800)
      await page3.evaluate(() => window.scrollTo(0, 0))
      await viewportShot(page3, '33_guide_after_login.png')
    } catch (err) {
      log(`  login flow encountered: ${err.message}`)
      await viewportShot(page3, '99_login_error_state.png')
    }

    await ctx3.close()
  } finally {
    await browser.close()
  }
  log('all done')
}

main().catch(err => {
  console.error('fatal:', err)
  process.exit(1)
})

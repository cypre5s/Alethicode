/**
 * `/guide` 用户使用手册的 Playwright 全链路测试。
 *
 * 重构后默认走 Notion / Cursor / Claude 文档风：funMode 默认 false（极简）。
 * 趣味模式必须由用户主动点 toggle 才出现奶蛙、图鉴、挂件、随机弹出。
 *
 * 覆盖项：
 *   - 未登录直接可访问；NavBar 双入口
 *   - 章节侧栏滚动联动 + 阅读进度 + 回到顶部
 *   - cmd+K 命令面板
 *   - 趣味模式 toggle → 挂件 / 图鉴 / 鼠标跟随入场和退场
 *   - FAQ 折叠 / 流程图入场
 *   - @ 上下文章节、课件问答章节、AI 三段式都可见
 *   - 笑声需点击触发，未配置时不抛错
 *   - 移动断点 (≤640) 命令面板隐藏
 */

const { test, expect } = require('@playwright/test')

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const GUIDE = `${BASE}/guide`

const SECTIONS_ORDER = [
  'welcome', 'ai', 'context', 'qa', 'flow', 'tips', 'faq', 'tour', 'feedback'
]

test.describe('/guide · 公开访问与基础渲染', () => {
  test('未登录直接打开 /guide 不被重定向到 /login', async ({ page }) => {
    await page.goto(GUIDE)
    await expect(page).toHaveURL(new RegExp('/guide(\\?|#|$)'))
    await expect(page).toHaveTitle(/指南|guide/i)
  })

  test('Hero 区显示新标题、双 CTA、4 张能力卡', async ({ page }) => {
    await page.goto(GUIDE)
    await expect(page.getByText('Alethicode 使用指南')).toBeVisible()
    await expect(page.getByRole('button', { name: '立刻开始' })).toBeVisible()
    await expect(page.getByRole('button', { name: '了解 AI 导学助手' })).toBeVisible()
    await expect(page.locator('.manual-hero-cap')).toHaveCount(4)
  })

  test('默认 funMode 关闭：不渲染吉祥物 mascot 图', async ({ page }) => {
    await page.goto(GUIDE)
    await expect(page.locator('.manual-page__hero-mascot')).toHaveCount(0)
  })

  test('9 个非趣味章节锚点全部出现在文档中（gallery 默认隐藏）', async ({ page }) => {
    await page.goto(GUIDE)
    for (const id of SECTIONS_ORDER) {
      await expect(page.locator(`section#${id}`)).toBeVisible()
    }
    await expect(page.locator('section#gallery')).toHaveCount(0)
  })

  test('左侧目录在桌面端 (≥1024) 显示并列出所有非趣味章节', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(GUIDE)
    const sidebar = page.getByRole('navigation', { name: '章节目录' })
    await expect(sidebar).toBeVisible()
    for (const title of ['快速开始', 'AI 导学助手', '@ 上下文引用', '课件问答', '完整学习闭环', '学习建议', '常见问题', '反馈与帮助']) {
      await expect(sidebar.getByRole('link', { name: new RegExp(title) })).toBeVisible()
    }
  })

  test('左侧目录在移动端 (≤1023) 隐藏', async ({ page }) => {
    await page.setViewportSize({ width: 800, height: 1200 })
    await page.goto(GUIDE)
    const sidebar = page.getByRole('navigation', { name: '章节目录' })
    await expect(sidebar).toBeHidden()
  })
})

test.describe('/guide · 章节联动 + 阅读进度 + 回到顶部', () => {
  test('点击目录项滚动到对应章节', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(GUIDE)
    const faqLink = page.getByRole('link', { name: /常见问题/ })
    await faqLink.click()
    await page.waitForTimeout(700)
    const faq = page.locator('section#faq')
    const box = await faq.boundingBox()
    expect(box).not.toBeNull()
    expect(box.y).toBeLessThanOrEqual(200)
  })

  test('滚动后顶部 progressbar 的 aria-valuenow > 0', async ({ page }) => {
    await page.goto(GUIDE)
    await page.evaluate(() => window.scrollTo(0, 1500))
    await page.waitForTimeout(300)
    const value = await page
      .getByRole('progressbar')
      .first()
      .getAttribute('aria-valuenow')
    expect(Number(value)).toBeGreaterThan(0)
  })

  test('滚动 > 240px 后「回到顶部」按钮浮现并可点击', async ({ page }) => {
    await page.goto(GUIDE)
    await page.evaluate(() => window.scrollTo(0, 600))
    await page.waitForTimeout(300)
    const btn = page.getByRole('button', { name: '回到顶部' }).first()
    await expect(btn).toBeVisible()
    await btn.click()
    await page.waitForTimeout(700)
    const y = await page.evaluate(() => window.scrollY)
    expect(y).toBeLessThan(50)
  })
})

test.describe('/guide · 命令面板（cmd+K）', () => {
  test('Ctrl+K 在桌面端打开命令面板', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(GUIDE)
    await page.keyboard.press('Control+K')
    await expect(page.getByRole('dialog', { name: '命令面板' })).toBeVisible()
  })

  test('搜索"上下文"匹配 SectionContext 的跳转条目', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(GUIDE)
    await page.keyboard.press('Control+K')
    await page.getByLabel('命令面板搜索').fill('上下文')
    await expect(page.getByRole('option', { name: /@ 上下文引用/ })).toBeVisible()
  })

  test('Esc 关闭命令面板', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(GUIDE)
    await page.keyboard.press('Control+K')
    await expect(page.getByRole('dialog', { name: '命令面板' })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog', { name: '命令面板' })).toBeHidden()
  })

  test('Enter 触发跳转命令并定位章节', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(GUIDE)
    await page.keyboard.press('Control+K')
    await page.getByLabel('命令面板搜索').fill('学习建议')
    await page.keyboard.press('Enter')
    await page.waitForTimeout(700)
    const tips = page.locator('section#tips')
    const box = await tips.boundingBox()
    expect(box).not.toBeNull()
    expect(box.y).toBeLessThanOrEqual(200)
  })
})

test.describe('/guide · 趣味模式开关 (默认 off)', () => {
  test('打开趣味模式后挂件 / 图鉴 / 鼠标跟随入场，再次关闭后退场，状态持久化', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(GUIDE)

    // 默认 funMode off：挂件、图鉴、跟随、随机弹出全部不存在
    await expect(page.locator('section#gallery')).toHaveCount(0)
    await expect(page.locator('.manual-naiwa-widget')).toHaveCount(0)

    // 打开趣味模式
    await page.locator('.manual-page__fun-toggle').click()
    await expect(page.locator('section#gallery')).toBeVisible()
    await expect(page.locator('.manual-naiwa-widget')).toBeVisible()
    let stored = await page.evaluate(() => window.localStorage.getItem('manual.fun_mode'))
    expect(stored).toBe('on')

    // 再次点击关闭
    await page.locator('.manual-page__fun-toggle').click()
    await expect(page.locator('section#gallery')).toHaveCount(0)
    await expect(page.locator('.manual-naiwa-widget')).toHaveCount(0)
    await expect(page.locator('.manual-naiwa-follower')).toHaveCount(0)
    await expect(page.locator('.manual-naiwa-popper')).toHaveCount(0)
    stored = await page.evaluate(() => window.localStorage.getItem('manual.fun_mode'))
    expect(stored).toBe('off')
  })

  test('刷新后趣味模式状态保持', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('.manual-page__fun-toggle').click()
    await page.reload()
    await expect(page.locator('.manual-naiwa-widget')).toBeVisible()
  })

  test('关闭趣味模式不影响正文阅读 (FAQ + Tips 仍可见)', async ({ page }) => {
    await page.goto(GUIDE)
    await expect(page.locator('section#faq')).toBeVisible()
    await expect(page.locator('section#tips')).toBeVisible()
  })
})

test.describe('/guide · FAQ 折叠展开', () => {
  test('点击 FAQ 标题展开答案', async ({ page }) => {
    await page.goto(GUIDE)
    const firstFaq = page.locator('.faq-item').first()
    const head = firstFaq.locator('.faq-item__head')
    await head.scrollIntoViewIfNeeded()
    await head.click()
    await expect(firstFaq).toHaveClass(/is-open/)
    await expect(firstFaq.locator('.faq-item__a')).toBeVisible()
  })

  test('再次点击同一项关闭答案 (accordion-like single open)', async ({ page }) => {
    await page.goto(GUIDE)
    const firstFaq = page.locator('.faq-item').first()
    const head = firstFaq.locator('.faq-item__head')
    await head.scrollIntoViewIfNeeded()
    await head.click()
    await head.click()
    await expect(firstFaq).not.toHaveClass(/is-open/)
  })
})

test.describe('/guide · 浮动挂件按钮 (打开趣味后)', () => {
  test('打开趣味模式后挂件首次访问自动展开', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('.manual-page__fun-toggle').click()
    await expect(page.locator('.manual-naiwa-widget')).toHaveClass(/is-expanded/, { timeout: 2000 })
  })

  test('点击挂件「关闭趣味模式」立即生效', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('.manual-page__fun-toggle').click()
    await page.locator('.manual-naiwa-widget__avatar').click()
    await page.getByRole('button', { name: '关闭趣味模式' }).click()
    await expect(page.locator('.manual-naiwa-widget')).toHaveCount(0)
  })

  test('点击「让他笑一下」不抛错（即使浏览器拦截 audio）', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('.manual-page__fun-toggle').click()
    await page.locator('.manual-naiwa-widget__avatar').click()
    const errors = []
    page.on('pageerror', err => errors.push(err))
    await page.getByRole('button', { name: '让他笑一下' }).click()
    await page.waitForTimeout(500)
    expect(errors).toHaveLength(0)
  })
})

test.describe('/guide · 奶蛙图鉴交互 (打开趣味后)', () => {
  test('图鉴展示 ≥8 张奶蛙变体', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto(GUIDE)
    await page.locator('.manual-page__fun-toggle').click()
    await page.locator('section#gallery').scrollIntoViewIfNeeded()
    const cards = page.locator('.manual-naiwa-gallery__card')
    const count = await cards.count()
    expect(count).toBeGreaterThanOrEqual(8)
  })
})

test.describe('/guide · 流程图与新 section 内容', () => {
  test('流程图渲染 8 个节点按钮', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('section#flow').scrollIntoViewIfNeeded()
    const nodes = page.locator('.manual-flow__node-btn')
    await expect(nodes).toHaveCount(8)
  })

  test('点击流程节点跳到对应说明章节', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('section#flow').scrollIntoViewIfNeeded()
    await page.locator('.manual-flow__node-btn').nth(0).click()
    await page.waitForTimeout(700)
    const target = page.locator('section#ai')
    const box = await target.boundingBox()
    expect(box).not.toBeNull()
  })

  test('SectionContext 渲染 @ token 表格 (含 @last_error 行)', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('section#context').scrollIntoViewIfNeeded()
    await expect(page.locator('section#context')).toContainText('@last_error')
    await expect(page.locator('section#context [role="table"]')).toBeVisible()
  })

  test('SectionCoursewareQa 显示「打开课件问答 →」按钮', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('section#qa').scrollIntoViewIfNeeded()
    await expect(page.getByRole('button', { name: /打开课件问答/ })).toBeVisible()
  })

  test('SectionAI 渲染推荐提问 PromptCard 且复制按钮可点击', async ({ page }) => {
    await page.goto(GUIDE)
    await page.locator('section#ai').scrollIntoViewIfNeeded()
    const cards = page.locator('section#ai .manual-prompt-card')
    expect(await cards.count()).toBeGreaterThanOrEqual(4)
    const firstCopy = cards.first().locator('.manual-prompt-card__copy')
    await expect(firstCopy).toBeVisible()
  })
})

test.describe('/guide · 控制台无错误', () => {
  test('页面加载 5 秒内无 console.error / pageerror', async ({ page }) => {
    const errors = []
    const pageErrors = []
    page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()) })
    page.on('pageerror', err => pageErrors.push(err.message))
    await page.goto(GUIDE)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    // 允许 audio autoplay 拦截一类的非致命警告，但不允许真实错误
    const fatal = errors.filter(e => !/play\(\) failed|user gesture/i.test(e))
    expect(fatal).toHaveLength(0)
    expect(pageErrors).toHaveLength(0)
  })
})

test.describe('/guide · 命令面板移动端隐藏', () => {
  test('窄屏 (≤640) 不渲染命令面板覆盖层', async ({ page }) => {
    await page.setViewportSize({ width: 480, height: 900 })
    await page.goto(GUIDE)
    await page.keyboard.press('Control+K')
    await page.waitForTimeout(400)
    const overlayVisible = await page.evaluate(() => {
      const el = document.querySelector('.manual-command-palette')
      if (!el) return false
      const style = window.getComputedStyle(el)
      return style.display !== 'none' && style.visibility !== 'hidden'
    })
    expect(overlayVisible).toBe(false)
  })
})

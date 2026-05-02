/* eslint-env jest */

/**
 * `/guide` 路由 + NavBar 双入口 + PUBLIC_PAGES 白名单的源契约。
 * 这些是手册页能"未登录直接访问 + 双入口可见"的硬性前置条件，
 * 一旦回归会导致整个手册不可达。
 */

const fs = require('fs')
const path = require('path')

function read (rel) {
  return fs.readFileSync(path.resolve(__dirname, rel), 'utf8')
}

describe('OJ views index.js · ManualPage 导出', () => {
  const source = read('../../src/pages/oj/views/index.js')

  test('lazy imports ManualPage from ./manual/ManualPage.vue', () => {
    expect(source).toMatch(/ManualPage\s*=\s*\(\)\s*=>\s*import\(['"]\.\/manual\/ManualPage\.vue['"]\)/)
  })

  test('exports ManualPage in the named export list', () => {
    expect(source).toMatch(/export\s*{[\s\S]*?ManualPage[\s\S]*?}/)
  })
})

describe('OJ router routes.js · /guide 路由定义', () => {
  const source = read('../../src/pages/oj/router/routes.js')

  test('imports ManualPage from views index', () => {
    expect(source).toContain('ManualPage')
    expect(source).toMatch(/from\s+['"]\.\.\/views['"]/)
  })

  test('registers route name=manual / path=/guide', () => {
    expect(source).toMatch(/name:\s*['"]manual['"]/)
    expect(source).toMatch(/path:\s*['"]\/guide['"]/)
  })

  test('does NOT mark /guide as requiresAuth (public access requirement)', () => {
    const block = source.match(/name:\s*['"]manual['"][\s\S]{0,200}/)
    expect(block).toBeTruthy()
    expect(block[0]).not.toContain('requiresAuth')
  })

  test('binds /guide to ManualPage component', () => {
    const block = source.match(/name:\s*['"]manual['"][\s\S]*?component:\s*ManualPage/)
    expect(block).toBeTruthy()
  })
})

describe('OJ router index.js · PUBLIC_PAGES 白名单', () => {
  const source = read('../../src/pages/oj/router/index.js')

  test("'manual' is in PUBLIC_PAGES so /guide bypasses login redirect", () => {
    expect(source).toContain("'manual'")
    expect(source).toMatch(/PUBLIC_PAGES\s*=\s*new Set\(\[[\s\S]*?'manual'/)
  })

  test('still keeps the original public pages (login / register / reset / logout)', () => {
    for (const name of ['login', 'register', 'apply-reset-password', 'reset-password', 'logout']) {
      const re = new RegExp(`'${name}'`)
      expect(source).toMatch(re)
    }
  })
})

describe('NavBar.vue · 双入口接入', () => {
  const source = read('../../src/pages/oj/components/NavBar.vue')

  test('imports Reading + QuestionFilled icons from element-plus icons', () => {
    expect(source).toMatch(/Reading[\s\S]*?from\s+['"]@element-plus\/icons-vue['"]/)
    expect(source).toMatch(/QuestionFilled/)
  })

  test('registers Reading + QuestionFilled in the components map', () => {
    const componentsBlock = source.match(/components:\s*{[\s\S]*?BetaFeedbackButton[\s\S]*?}/)
    expect(componentsBlock).toBeTruthy()
    expect(componentsBlock[0]).toContain('Reading')
    expect(componentsBlock[0]).toContain('QuestionFilled')
  })

  test('inserts the main-menu entry «新手指南» pointing to /guide', () => {
    expect(source).toMatch(/<ElMenuItem\s+index="\/guide"/)
    expect(source).toContain('新手指南')
  })

  test('right-side question icon button is present with tooltip 「使用手册」', () => {
    expect(source).toContain('guide-icon-wrap')
    expect(source).toContain('使用手册')
    expect(source).toMatch(/<ElTooltip[\s\S]*?content="使用手册"/)
  })

  test('right-side icon is keyboard-accessible (role=button + tabindex + Enter handler)', () => {
    const block = source.match(/guide-icon[\s\S]*?<\/span>/)
    expect(block).toBeTruthy()
    expect(block[0]).toMatch(/role="button"/)
    expect(block[0]).toMatch(/tabindex="0"/)
    expect(block[0]).toMatch(/@keyup\.enter=/)
  })

  test('icon click navigates to /guide', () => {
    expect(source).toMatch(/\$router\.push\(['"]\/guide['"]\)/)
  })

  test('keeps existing entry "班级" intact (no regression)', () => {
    expect(source).toContain('班级')
    expect(source).toMatch(/<ElMenuItem\s+index="\/classroom"/)
  })

  test('declares scoped CSS for guide-icon hover/focus states', () => {
    expect(source).toMatch(/\.guide-icon[\s\S]*?&:(?:hover|focus-visible)/)
  })
})

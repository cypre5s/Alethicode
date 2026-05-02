/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin design system contract', () => {
  const legacyAdminFooter = ['Online', 'Judge Admin'].join('')
  const legacyImportBrand = ['QD', 'UOJ'].join('')

  test('shared admin tokens should define layout and table primitives', () => {
    const commonSource = readSource('../../src/styles/common.less')

    expect(commonSource).toContain('--admin-sidebar-width:')
    expect(commonSource).toContain('--admin-panel-radius:')
    expect(commonSource).toContain('--admin-table-header-bg:')
    expect(commonSource).toContain('--admin-toolbar-gap:')
  })

  test('admin panel should expose a shared card skeleton', () => {
    const panelSource = readSource('../../src/pages/admin/components/Panel.vue')

    expect(panelSource).toContain('admin-panel')
    expect(panelSource).toContain('admin-panel__header')
    expect(panelSource).toContain('admin-panel__title')
    expect(panelSource).toContain('admin-panel__toolbar')
    expect(panelSource).toContain('admin-panel__body')
  })

  test('admin shell should use shared layout classes and Chinese brand copy', () => {
    const homeSource = readSource('../../src/pages/admin/views/Home.vue')

    expect(homeSource).toContain('admin-shell')
    expect(homeSource).toContain('admin-shell__main')
    expect(homeSource).toContain('admin-shell__header')
    expect(homeSource).toContain('admin-shell__content')
    expect(homeSource).toContain('Alethicode 管理台')
    expect(homeSource).not.toContain(legacyAdminFooter)
  })

  test('admin login and import pages should remove legacy English branding', () => {
    const loginSource = readSource('../../src/pages/admin/views/general/Login.vue')
    const importSource = readSource('../../src/pages/admin/views/problem/ImportAndExport.vue')

    expect(loginSource).toContain('Alethicode 管理台')
    expect(loginSource).toContain('教学管理入口')
    expect(loginSource).not.toContain('Focus. Judge. Deliver.')
    expect(importSource).not.toContain(legacyImportBrand)
    expect(importSource).not.toContain('Export Problems (beta)')
  })
})

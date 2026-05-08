/**
 * 输入框基础设施契约测试。
 *
 * jest 23 + babel-jest 23 不会自动转换项目源文件中的 ESM `import / export` 语法
 * （现有 spec 也都用 babel.transformSync 手动加载源文件，例如
 * frontend/tests/unit/workflow-server-state.spec.js）。
 *
 * 本 spec 同样采取手动加载策略：
 *  - composerStorage.js / useChatComposer.js 通过 loadEsmModule() 转换后注入依赖
 *  - .vue 组件用 readSource + 静态字符串断言保证 props/emits 与关键 UI 字符串存在
 */

const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

function loadEsmModule(relativePath, requireOverrides) {
  const filePath = path.resolve(__dirname, relativePath)
  const source = fs.readFileSync(filePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const moduleObj = { exports: {} }
  const localRequire = (request) => {
    if (requireOverrides && Object.prototype.hasOwnProperty.call(requireOverrides, request)) {
      return requireOverrides[request]
    }
    return require(request)
  }
  const fn = new Function(
    'module', 'exports', 'require', '__dirname', '__filename',
    transformed.code
  )
  fn(moduleObj, moduleObj.exports, localRequire, path.dirname(filePath), filePath)
  return moduleObj.exports
}

function createMockVueRuntime() {
  function ref(initial) {
    let internal = initial
    return {
      get value() { return internal },
      set value(v) { internal = v },
      __v_isRef: true
    }
  }

  function computed(fn) {
    return {
      get value() { return fn() },
      __v_isRef: true
    }
  }

  function watch() { return undefined }
  function onUnmounted() { return undefined }
  function isRef(v) { return Boolean(v && v.__v_isRef) }

  return { ref, computed, watch, onUnmounted, isRef }
}

const composerStorageSource = readSource('../../src/pages/oj/components/chat/composerStorage.js')
const useChatComposerSource = readSource('../../src/pages/oj/components/chat/useChatComposer.js')
const atMenuSource = readSource('../../src/pages/oj/components/chat/AtMentionMenu.vue')
const slashMenuSource = readSource('../../src/pages/oj/components/chat/SlashCommandMenu.vue')
const hintBarSource = readSource('../../src/pages/oj/components/chat/ComposerHintBar.vue')
const usageBarSource = readSource('../../src/pages/oj/components/chat/ContextUsageBar.vue')
const qaPageSource = readSource('../../src/pages/oj/views/languagepack/LanguagePackQaPage.vue')

describe('chat composer · composerStorage 静态契约', () => {
  test('导出 readDraft / writeDraft / readHistory / pushHistory / clearScope', () => {
    expect(composerStorageSource).toMatch(/export function readDraft\s*\(/)
    expect(composerStorageSource).toMatch(/export function writeDraft\s*\(/)
    expect(composerStorageSource).toMatch(/export function readHistory\s*\(/)
    expect(composerStorageSource).toMatch(/export function pushHistory\s*\(/)
    expect(composerStorageSource).toMatch(/export function clearScope\s*\(/)
  })

  test('使用统一前缀 alethicode.composer 与历史上限 50', () => {
    expect(composerStorageSource).toContain("'alethicode.composer'")
    expect(composerStorageSource).toContain('HISTORY_LIMIT = 50')
  })
})

describe('chat composer · composerStorage 行为', () => {
  let storage
  beforeEach(() => {
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.clear()
    }
    storage = loadEsmModule('../../src/pages/oj/components/chat/composerStorage.js')
  })

  test('草稿读写：写入后 readDraft 返回相同字符串', () => {
    storage.writeDraft('tutor:1', 'hello world')
    expect(storage.readDraft('tutor:1')).toBe('hello world')
  })

  test('草稿清空：写入空字符串等价于 removeItem', () => {
    storage.writeDraft('qa:42', 'draft')
    storage.writeDraft('qa:42', '')
    expect(storage.readDraft('qa:42')).toBe('')
  })

  test('历史去重：相同字符串不会重复写入', () => {
    storage.pushHistory('tutor:1', 'hello')
    storage.pushHistory('tutor:1', 'world')
    storage.pushHistory('tutor:1', 'hello')
    const list = storage.readHistory('tutor:1')
    expect(list).toEqual(['world', 'hello'])
  })

  test('历史上限 50：超出后保留最近的 50 条', () => {
    for (let i = 0; i < 55; i++) storage.pushHistory('tutor:1', 'item-' + i)
    const list = storage.readHistory('tutor:1')
    expect(list.length).toBe(50)
    expect(list[0]).toBe('item-5')
    expect(list[49]).toBe('item-54')
  })

  test('scope 隔离：tutor:1 与 qa:42 互不污染', () => {
    storage.writeDraft('tutor:1', 'A')
    storage.writeDraft('qa:42', 'B')
    expect(storage.readDraft('tutor:1')).toBe('A')
    expect(storage.readDraft('qa:42')).toBe('B')
  })

  test('clearScope 清掉草稿和历史', () => {
    storage.writeDraft('tutor:1', 'draft')
    storage.pushHistory('tutor:1', 'msg')
    storage.clearScope('tutor:1')
    expect(storage.readDraft('tutor:1')).toBe('')
    expect(storage.readHistory('tutor:1')).toEqual([])
  })

  test('readHistory 在数据损坏时返回空数组而不是抛错', () => {
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.setItem('alethicode.composer.history.broken', '{not json')
    }
    expect(storage.readHistory('broken')).toEqual([])
  })
})

describe('chat composer · useChatComposer 静态契约', () => {
  test('导出 useChatComposer + 触发正则常量', () => {
    expect(useChatComposerSource).toMatch(/export function useChatComposer\s*\(/)
    expect(useChatComposerSource).toMatch(/AT_TRIGGER_RE\s*=\s*\/\(\?:\^\|\\s\)@\(\[/)
    expect(useChatComposerSource).toMatch(/SLASH_TRIGGER_RE\s*=\s*\/\^\\\//)
  })

  test('参数 schema 包含 atProviders / slashCommands / scopeKey / onSubmit / isInputBlocked', () => {
    expect(useChatComposerSource).toContain('atProviders')
    expect(useChatComposerSource).toContain('slashCommands')
    expect(useChatComposerSource).toContain('scopeKey')
    expect(useChatComposerSource).toContain('onSubmit')
    expect(useChatComposerSource).toContain('isInputBlocked')
  })

  test('返回值 schema 含 rawText / atGroups / slashGroups / handlers', () => {
    expect(useChatComposerSource).toContain('rawText: rawText')
    expect(useChatComposerSource).toContain('atGroups: atGroups')
    expect(useChatComposerSource).toContain('slashGroups: slashGroups')
    expect(useChatComposerSource).toMatch(/handlers:\s*\{/)
    expect(useChatComposerSource).toContain('selectAtItem: selectAtItem')
    expect(useChatComposerSource).toContain('selectSlashItem: selectSlashItem')
    expect(useChatComposerSource).toContain('submit: submit')
  })

  test('参数缺失时 failfast 抛 TypeError', () => {
    expect(useChatComposerSource).toContain("throw new TypeError('useChatComposer: options object is required')")
    expect(useChatComposerSource).toContain('must be an Array, got')
  })
})

describe('chat composer · useChatComposer 行为', () => {
  let useChatComposer
  let storage

  beforeEach(() => {
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.clear()
    }
    storage = loadEsmModule('../../src/pages/oj/components/chat/composerStorage.js')
    const mod = loadEsmModule('../../src/pages/oj/components/chat/useChatComposer.js', {
      vue: createMockVueRuntime(),
      './composerStorage': storage
    })
    useChatComposer = mod.useChatComposer
  })

  function makeComposer(overrides) {
    overrides = overrides || {}
    const atProviders = overrides.atProviders || [
      {
        key: 'cards',
        group: '会话卡片',
        items: () => [
          { key: 'c1', token: '@card:C-V-001', label: '导读卡片' },
          { key: 'c2', token: '@last_error', label: '最近错误' }
        ]
      }
    ]
    const slashCommands = overrides.slashCommands || [
      { key: 's1', group: 'Agent 动作', command: '/ideate', label: '思路分析', run: jest.fn() },
      { key: 's2', group: '会话进阶', command: '/compact', label: '压缩上下文', status: 'placeholder' }
    ]
    return useChatComposer({
      scopeKey: overrides.scopeKey || 'tutor:1',
      atProviders: atProviders,
      slashCommands: slashCommands,
      onSubmit: overrides.onSubmit || jest.fn(),
      isInputBlocked: overrides.isInputBlocked
    })
  }

  test('options 不传时抛错', () => {
    expect(() => useChatComposer()).toThrow(TypeError)
  })

  test('atProviders 不是数组时抛错', () => {
    expect(() => useChatComposer({ atProviders: null, slashCommands: [] })).toThrow(/atProviders/)
  })

  test('slashCommands 不是数组时抛错', () => {
    expect(() => useChatComposer({ atProviders: [], slashCommands: null })).toThrow(/slashCommands/)
  })

  test('输入 @ 触发 atMenuVisible，且 atGroups 含 cards', () => {
    const c = makeComposer()
    c.handlers.onInput('hello @')
    expect(c.atMenuVisible.value).toBe(true)
    expect(c.atGroups.value.length).toBeGreaterThan(0)
    expect(c.atGroups.value[0].group).toBe('会话卡片')
  })

  test('输入 @car 过滤到 @card:C-V-001', () => {
    const c = makeComposer()
    c.handlers.onInput('hello @car')
    expect(c.atMenuVisible.value).toBe(true)
    const items = c.atGroups.value[0].items
    expect(items.find(it => it.token === '@card:C-V-001')).toBeTruthy()
    expect(items.find(it => it.token === '@last_error')).toBeFalsy()
  })

  test('输入 / 触发 slashMenuVisible 且与 atMenu 互斥', () => {
    const c = makeComposer()
    c.handlers.onInput('/idea')
    expect(c.slashMenuVisible.value).toBe(true)
    expect(c.atMenuVisible.value).toBe(false)
  })

  test('selectAtItem 把 token 拼到输入末尾', () => {
    const c = makeComposer()
    c.handlers.onInput('看看 @car')
    c.handlers.selectAtItem({ token: '@card:C-V-001' })
    expect(c.rawText.value).toBe('看看 @card:C-V-001 ')
    expect(c.atMenuVisible.value).toBe(false)
  })

  test('selectSlashItem 触发 run() 并清空输入', () => {
    const run = jest.fn()
    const c = makeComposer({
      slashCommands: [{ key: 's1', group: 'g', command: '/ideate', label: 'x', run: run }]
    })
    c.handlers.onInput('/ideate 跑一下')
    c.handlers.selectSlashItem({ key: 's1', command: '/ideate', run: run })
    expect(run).toHaveBeenCalledTimes(1)
    expect(c.rawText.value).toBe('')
    expect(c.slashMenuVisible.value).toBe(false)
  })

  test('带参数的 slash 命令按 Enter 时把参数传给 run()', () => {
    const run = jest.fn()
    const onSubmit = jest.fn()
    const c = makeComposer({
      slashCommands: [{ key: 'page', group: 'g', command: '/page', label: '跳页', run: run }],
      onSubmit: onSubmit
    })
    c.handlers.onInput('/page 7')
    const event = { key: 'Enter', shiftKey: false, preventDefault: jest.fn() }
    c.handlers.onKeydown(event)

    expect(event.preventDefault).toHaveBeenCalled()
    expect(run).toHaveBeenCalledWith(expect.objectContaining({ args: '7' }))
    expect(onSubmit).not.toHaveBeenCalled()
    expect(c.rawText.value).toBe('')
  })

  test('placeholder 命令不触发 run', () => {
    const run = jest.fn()
    const c = makeComposer()
    c.handlers.onInput('/comp')
    c.handlers.selectSlashItem({ command: '/compact', status: 'placeholder', run: run })
    expect(run).not.toHaveBeenCalled()
    expect(c.slashMenuVisible.value).toBe(false)
  })

  test('/compact available 时触发 run', () => {
    const compactRun = jest.fn()
    const c = makeComposer({
      slashCommands: [
        { key: 'cmd-compact', group: '会话进阶', command: '/compact', label: '压缩上下文', status: 'available', run: compactRun }
      ]
    })
    c.handlers.onInput('/compact')
    c.handlers.selectSlashItem({ key: 'cmd-compact', command: '/compact', status: 'available', run: compactRun })
    expect(compactRun).toHaveBeenCalledTimes(1)
    expect(c.rawText.value).toBe('')
  })

  test('/fork available 时触发 run', () => {
    const forkRun = jest.fn()
    const c = makeComposer({
      slashCommands: [
        { key: 'cmd-fork', group: '会话进阶', command: '/fork', label: '分叉会话', status: 'available', run: forkRun }
      ]
    })
    c.handlers.onInput('/fork')
    c.handlers.selectSlashItem({ key: 'cmd-fork', command: '/fork', status: 'available', run: forkRun })
    expect(forkRun).toHaveBeenCalledTimes(1)
    expect(c.rawText.value).toBe('')
  })

  test('submit 触发 onSubmit 且把消息写入历史', () => {
    const onSubmit = jest.fn()
    const c = makeComposer({ onSubmit: onSubmit })
    c.handlers.onInput('hello world')
    c.handlers.submit()
    expect(onSubmit).toHaveBeenCalledWith('hello world')
    expect(storage.readHistory('tutor:1')).toEqual(['hello world'])
    expect(c.rawText.value).toBe('')
  })

  test('上方向键在空输入时召回历史最末条', () => {
    storage.pushHistory('tutor:1', 'first')
    storage.pushHistory('tutor:1', 'second')
    const c = makeComposer()
    const event = { key: 'ArrowUp', shiftKey: false, preventDefault: jest.fn() }
    c.handlers.onKeydown(event)
    expect(event.preventDefault).toHaveBeenCalled()
    expect(c.rawText.value).toBe('second')
  })

  test('在打开 @ 菜单时按 Enter 选中当前条目而不是 submit', () => {
    const onSubmit = jest.fn()
    const c = makeComposer({ onSubmit: onSubmit })
    c.handlers.onInput('@')
    const event = { key: 'Enter', shiftKey: false, preventDefault: jest.fn() }
    c.handlers.onKeydown(event)
    expect(onSubmit).not.toHaveBeenCalled()
    expect(c.atMenuVisible.value).toBe(false)
    expect(c.rawText.value).toMatch(/^@card:C-V-001 $/)
  })

  test('Esc 关闭打开的菜单', () => {
    const c = makeComposer()
    c.handlers.onInput('@')
    expect(c.atMenuVisible.value).toBe(true)
    const event = { key: 'Escape', shiftKey: false, preventDefault: jest.fn() }
    c.handlers.onKeydown(event)
    expect(c.atMenuVisible.value).toBe(false)
  })

  test('isInputBlocked=true 时 submit 不触发 onSubmit', () => {
    const onSubmit = jest.fn()
    const c = makeComposer({ onSubmit: onSubmit, isInputBlocked: true })
    c.handlers.onInput('text')
    c.handlers.submit()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  test('atGroups 按 item.subgroup 拆出二级目录小节', () => {
    const c = makeComposer({
      atProviders: [
        {
          key: 'courseware-pages',
          group: '课件页',
          items: () => [
            { key: 'p11', token: '@page:1.1', label: '第 1 页', subgroup: '第 1 章 · 入门.pptx' },
            { key: 'p12', token: '@page:1.2', label: '第 2 页', subgroup: '第 1 章 · 入门.pptx' },
            { key: 'p21', token: '@page:2.1', label: '第 1 页', subgroup: '第 2 章 · 进阶.pptx' }
          ]
        }
      ]
    })
    c.handlers.onInput('@')
    const groups = c.atGroups.value
    expect(groups.length).toBe(2)
    expect(groups[0].group).toBe('第 1 章 · 入门.pptx')
    expect(groups[0].items.map(it => it.token)).toEqual(['@page:1.1', '@page:1.2'])
    expect(groups[1].group).toBe('第 2 章 · 进阶.pptx')
    expect(groups[1].items.map(it => it.token)).toEqual(['@page:2.1'])
  })

  test('subgroup 与无 subgroup 的 provider 共存时各自独立分组', () => {
    const c = makeComposer({
      atProviders: [
        {
          key: 'cards',
          group: '会话卡片',
          items: () => [{ key: 'c1', token: '@card:C-V-001', label: '导读卡片' }]
        },
        {
          key: 'courseware-pages',
          group: '课件页',
          items: () => [
            { key: 'p11', token: '@page:1.1', label: '第 1 页', subgroup: '第 1 章 · 入门.pptx' },
            { key: 'p21', token: '@page:2.1', label: '第 1 页', subgroup: '第 2 章 · 进阶.pptx' }
          ]
        }
      ]
    })
    c.handlers.onInput('@')
    const groups = c.atGroups.value
    expect(groups.map(g => g.group)).toEqual(['会话卡片', '第 1 章 · 入门.pptx', '第 2 章 · 进阶.pptx'])
    expect(groups[0].items.map(it => it.token)).toEqual(['@card:C-V-001'])
  })

  test('refreshProvider 后忽略旧 lazy provider Promise 的回写', async () => {
    const resolvers = []
    const c = makeComposer({
      atProviders: [
        {
          key: 'knowledge-components',
          group: '知识点',
          lazyLoad: true,
          items: () => new Promise(resolve => { resolvers.push(resolve) })
        }
      ]
    })

    c.handlers.onInput('@')
    expect(c.atGroups.value).toEqual([])
    c.handlers.refreshProvider('knowledge-components')
    resolvers[0]([{ key: 'old', token: '@kc:old', label: '旧知识点' }])
    await Promise.resolve()
    await Promise.resolve()

    expect(c.atGroups.value).toEqual([])
    expect(resolvers.length).toBe(2)
    resolvers[1]([{ key: 'new', token: '@kc:new', label: '新知识点' }])
    await Promise.resolve()
    await Promise.resolve()

    expect(c.atGroups.value[0].items.map(item => item.token)).toEqual(['@kc:new'])
  })
})

describe('chat composer · AtMentionMenu 静态契约', () => {
  test('暴露 visible / groups / activeIndex / showHoverPreview props 与 select/hover/close emits', () => {
    expect(atMenuSource).toContain("name: 'AtMentionMenu'")
    expect(atMenuSource).toContain("emits: ['select', 'hover', 'close']")
    expect(atMenuSource).toContain('visible: { type: Boolean')
    expect(atMenuSource).toContain('groups: { type: Array')
    expect(atMenuSource).toContain('activeIndex: { type: Number')
    expect(atMenuSource).toContain('showHoverPreview: { type: Boolean')
  })

  test('渲染分组小标题与 placeholder 角标', () => {
    expect(atMenuSource).toContain('class="at-mention-group-title"')
    expect(atMenuSource).toContain('class="at-mention-placeholder"')
  })

  test('hover preview 浮窗只在 showHoverPreview 且有 hoverPreview 时渲染', () => {
    expect(atMenuSource).toContain('v-if="showHoverPreview && hoveredItem && hoveredItem.hoverPreview"')
  })

  test('菜单以浮层向上展开，不参与输入框文档流', () => {
    expect(atMenuSource).toContain('position: absolute')
    expect(atMenuSource).toContain('bottom: calc(100% + 8px)')
    expect(atMenuSource).toContain('left: 0')
    expect(atMenuSource).toContain('right: 0')
  })
})

describe('chat composer · SlashCommandMenu 静态契约', () => {
  test('暴露 visible / groups / activeIndex props 与 select/close emits', () => {
    expect(slashMenuSource).toContain("name: 'SlashCommandMenu'")
    expect(slashMenuSource).toContain("emits: ['select', 'close']")
    expect(slashMenuSource).toContain('visible: { type: Boolean')
    expect(slashMenuSource).toContain('groups: { type: Array')
  })

  test('placeholder 命令视觉降级', () => {
    expect(slashMenuSource).toContain("'is-placeholder': item.status === 'placeholder'")
    expect(slashMenuSource).toContain('class="slash-command-tag"')
  })

  test('命令菜单以浮层向上展开，不参与输入框文档流', () => {
    expect(slashMenuSource).toContain('position: absolute')
    expect(slashMenuSource).toContain('bottom: calc(100% + 8px)')
    expect(slashMenuSource).toContain('left: 0')
    expect(slashMenuSource).toContain('right: 0')
  })
})

describe('chat composer · ComposerHintBar 静态契约', () => {
  test('展示三段提示并支持 atActive / slashActive 高亮', () => {
    expect(hintBarSource).toContain("name: 'ComposerHintBar'")
    expect(hintBarSource).toContain('atActive: { type: Boolean')
    expect(hintBarSource).toContain('slashActive: { type: Boolean')
    expect(hintBarSource).toContain('/ 命令')
    expect(hintBarSource).toContain('@ 引用')
    expect(hintBarSource).toContain('↑ 历史')
  })
})

describe('chat composer · ContextUsageBar 静态契约', () => {
  test('暴露 tokensUsed / tokensLimit / modelName / loading props 与 compact-click emit', () => {
    expect(usageBarSource).toContain("name: 'ContextUsageBar'")
    expect(usageBarSource).toContain("emits: ['compact-click']")
    expect(usageBarSource).toContain('tokensUsed: { type: Number')
    expect(usageBarSource).toContain('tokensLimit: { type: Number')
    expect(usageBarSource).toContain('modelName: { type: String')
    expect(usageBarSource).toContain('loading: { type: Boolean')
  })

  test('彩条按 50% / 80% 阈值切档', () => {
    expect(usageBarSource).toContain("if (r >= 0.8) return 'is-danger'")
    expect(usageBarSource).toContain("if (r >= 0.5) return 'is-warning'")
    expect(usageBarSource).toContain("return 'is-safe'")
  })

  test('达到 80% 时显示 /compact 提示按钮', () => {
    expect(usageBarSource).toContain('return this.ratio >= 0.8')
    expect(usageBarSource).toContain('/compact 整理上下文')
  })
})

describe('language pack QA · citation display contract', () => {
  test('grounded 状态必须同时有 citations，避免只显示已定位但没有引用按钮', () => {
    expect(qaPageSource).toContain('isGroundedMessage (message)')
    expect(qaPageSource).toContain('message.answer_json.grounded && this.resolveCitations(message).length')
  })
})

/* eslint-env jest */

/**
 * `manualContent.js` 数据完整性的源契约测试。
 *
 * 项目使用 jest 23.6（不会自动加载 babel.config.js 来转换 ES module
 * 源文件），因此沿用其他 *.spec.js 的模式：读取源文件文本，
 * 用正则与字符串匹配验证导出与结构。
 */

const fs = require('fs')
const path = require('path')

const SOURCE_PATH = path.resolve(
  __dirname,
  '../../src/pages/oj/views/manual/manualContent.js'
)
const SOURCE = fs.readFileSync(SOURCE_PATH, 'utf8')

function extractObjectArray (key) {
  // 抓取形如 `export const KEY = [ ... ]` 的源块（按层匹配大括号 + 中括号）。
  const m = SOURCE.match(new RegExp(`export const ${key}\\s*=\\s*\\[`))
  if (!m) return null
  const start = m.index + m[0].length - 1
  let depth = 0
  for (let i = start; i < SOURCE.length; i += 1) {
    const c = SOURCE[i]
    if (c === '[') depth += 1
    else if (c === ']') {
      depth -= 1
      if (depth === 0) return SOURCE.slice(start, i + 1)
    }
  }
  return null
}

function countObjectsInArray (key) {
  const block = extractObjectArray(key)
  if (!block) return 0
  // 顶层对象计数：通过追踪大括号深度，遇到 depth=1→0 表示一个对象结束。
  let depth = 0
  let count = 0
  let inString = false
  let stringChar = null
  for (let i = 0; i < block.length; i += 1) {
    const c = block[i]
    const prev = i > 0 ? block[i - 1] : ''
    if (inString) {
      if (c === stringChar && prev !== '\\') inString = false
      continue
    }
    if (c === '"' || c === "'" || c === '`') {
      inString = true
      stringChar = c
      continue
    }
    if (c === '{') depth += 1
    else if (c === '}') {
      depth -= 1
      if (depth === 0) count += 1
    }
  }
  return count
}

const VALID_SECTION_TARGETS = new Set([
  'welcome', 'ai', 'context', 'qa', 'flow', 'tips', 'faq', 'tour', 'gallery', 'feedback'
])

describe('manualContent.js · localStorage keys', () => {
  test('exposes the two required localStorage keys (fun_mode + completed_at)', () => {
    expect(SOURCE).toMatch(/export const FUN_MODE_KEY\s*=\s*['"]manual\.fun_mode['"]/)
    expect(SOURCE).toMatch(/export const COMPLETED_KEY\s*=\s*['"]manual\.completed_at['"]/)
  })

  test('does NOT export THEME_KEY (dark mode removed)', () => {
    expect(SOURCE).not.toMatch(/export const THEME_KEY/)
  })
})

describe('manualContent.js · SECTIONS', () => {
  test('declares exactly 10 sections in plan-defined order', () => {
    expect(countObjectsInArray('SECTIONS')).toBe(10)
    const block = extractObjectArray('SECTIONS')
    const idOrder = [...block.matchAll(/id:\s*['"]([a-z-]+)['"]/g)].map(m => m[1])
    expect(idOrder).toEqual([
      'welcome', 'ai', 'context', 'qa', 'flow', 'tips', 'faq', 'tour', 'gallery', 'feedback'
    ])
  })

  test('only the gallery section is funOnly', () => {
    const block = extractObjectArray('SECTIONS')
    const matches = block.match(/funOnly:\s*true/g) || []
    expect(matches).toHaveLength(1)
    const galleryBlock = block.match(/id:\s*['"]gallery['"][\s\S]*?\}/)
    expect(galleryBlock[0]).toMatch(/funOnly:\s*true/)
  })

  test('every section carries id / title / subtitle / sticker', () => {
    const block = extractObjectArray('SECTIONS')
    expect((block.match(/title:\s*['"]/g) || []).length).toBeGreaterThanOrEqual(10)
    expect((block.match(/subtitle:\s*['"]/g) || []).length).toBeGreaterThanOrEqual(10)
    expect((block.match(/sticker:\s*\d+/g) || []).length).toBeGreaterThanOrEqual(10)
  })

  test('legacy "core" section id is removed (replaced by ai/context/qa/flow split)', () => {
    const block = extractObjectArray('SECTIONS')
    expect(block).not.toMatch(/id:\s*['"]core['"]/)
  })
})

describe('manualContent.js · naiwa asset paths', () => {
  test('hero / audio / motion paths use /assets/manual/naiwa/ root', () => {
    expect(SOURCE).toContain("export const NAIWA_HERO = `${NAIWA_BASE}/hero/naiwa-hero.png`")
    expect(SOURCE).toContain("export const NAIWA_LAUGH_AUDIO = `${NAIWA_BASE}/audio/nailong-laugh.m4a`")
    expect(SOURCE).toMatch(/NAIWA_BASE\s*=\s*['"]\/assets\/manual\/naiwa['"]/)
  })

  test('NAIWA_MOTION exposes 4 keys all pointing to .gif files', () => {
    const start = SOURCE.indexOf('export const NAIWA_MOTION')
    expect(start).toBeGreaterThan(-1)
    const objStart = SOURCE.indexOf('{', start)
    let depth = 0
    let objEnd = -1
    for (let i = objStart; i < SOURCE.length; i += 1) {
      const c = SOURCE[i]
      if (c === '{') depth += 1
      else if (c === '}') {
        depth -= 1
        if (depth === 0) { objEnd = i; break }
      }
    }
    expect(objEnd).toBeGreaterThan(objStart)
    const block = SOURCE.slice(objStart, objEnd + 1)
    for (const key of ['laughLoop', 'bounce', 'spin', 'celebrate']) {
      const re = new RegExp(`${key}:[\\s\\S]*?\\.gif`)
      expect(block).toMatch(re)
    }
  })

  test('NAIWA_STICKERS has exactly 8 entries indexed 01-08', () => {
    expect(countObjectsInArray('NAIWA_STICKERS')).toBe(8)
    const block = extractObjectArray('NAIWA_STICKERS')
    for (let i = 1; i <= 8; i += 1) {
      const num = String(i).padStart(2, '0')
      expect(block).toMatch(new RegExp(`sticker-${num}-`))
    }
  })

  test('NAIWA_FAQ_ICONS has exactly 4 emotion icons', () => {
    expect(countObjectsInArray('NAIWA_FAQ_ICONS')).toBe(4)
    const block = extractObjectArray('NAIWA_FAQ_ICONS')
    for (const name of ['icon-question', 'icon-confused', 'icon-aha', 'icon-shrug']) {
      expect(block).toContain(name)
    }
  })

  test('NAIWA_GALLERY has at least 12 entries (per plan ≥8 plus expansion)', () => {
    expect(countObjectsInArray('NAIWA_GALLERY')).toBeGreaterThanOrEqual(12)
  })

  test('total distinct naiwa asset paths satisfy the plan-required ≥20', () => {
    const stickersCount = countObjectsInArray('NAIWA_STICKERS')
    const faqCount = countObjectsInArray('NAIWA_FAQ_ICONS')
    const galleryCount = countObjectsInArray('NAIWA_GALLERY')
    const total = 1 + 1 + 4 + stickersCount + faqCount + galleryCount
    expect(total).toBeGreaterThanOrEqual(20)
  })
})

describe('manualContent.js · QUICK_START_STEPS', () => {
  test('exports exactly 4 quick-start steps (重构后从 3 步扩到 4 步)', () => {
    expect(countObjectsInArray('QUICK_START_STEPS')).toBe(4)
  })

  test('steps numbered 1-4', () => {
    const block = extractObjectArray('QUICK_START_STEPS')
    for (let i = 1; i <= 4; i += 1) {
      expect(block).toMatch(new RegExp(`step:\\s*${i}`))
    }
  })

  test('每步都附带 where / look / why 三个上下文字段', () => {
    const block = extractObjectArray('QUICK_START_STEPS')
    expect((block.match(/where:/g) || []).length).toBe(4)
    expect((block.match(/look:/g) || []).length).toBe(4)
    expect((block.match(/why:/g) || []).length).toBe(4)
  })
})

describe('manualContent.js · FLOW_NODES', () => {
  test('flow has exactly 8 nodes (8 步学习闭环)', () => {
    expect(countObjectsInArray('FLOW_NODES')).toBe(8)
  })

  test('contains the canonical 8-step waypoints', () => {
    const block = extractObjectArray('FLOW_NODES')
    for (const id of ['read', 'io', 'idea', 'code', 'submit', 'feedback', 'fix', 'review']) {
      expect(block).toMatch(new RegExp(`id:\\s*['"]${id}['"]`))
    }
  })

  test('every flow node points to a real section id as target', () => {
    const block = extractObjectArray('FLOW_NODES')
    const targets = [...block.matchAll(/target:\s*['"]([a-z-]+)['"]/g)].map(m => m[1])
    expect(targets.length).toBe(8)
    for (const t of targets) {
      expect(VALID_SECTION_TARGETS.has(t)).toBe(true)
    }
  })
})

describe('manualContent.js · LEARNING_LOOP_STEPS', () => {
  test('exports exactly 8 loop step descriptions matching FLOW_NODES count', () => {
    expect(countObjectsInArray('LEARNING_LOOP_STEPS')).toBe(8)
  })

  test('每条 loop step 都有 step 序号、title、desc', () => {
    const block = extractObjectArray('LEARNING_LOOP_STEPS')
    expect((block.match(/step:\s*\d+/g) || []).length).toBe(8)
    expect((block.match(/title:/g) || []).length).toBe(8)
    expect((block.match(/desc:/g) || []).length).toBe(8)
  })
})

describe('manualContent.js · TOUR_PAGES', () => {
  test('exports 14 tour pages after removing the global submission list', () => {
    expect(countObjectsInArray('TOUR_PAGES')).toBe(14)
  })

  test('contains original plan-required pages: home / problem-list / problem-detail / notebook / qa / classroom', () => {
    const block = extractObjectArray('TOUR_PAGES')
    for (const id of ['home', 'problem-list', 'problem-detail', 'notebook', 'qa', 'classroom']) {
      expect(block).toMatch(new RegExp(`id:\\s*['"]${id}['"]`))
    }
  })

  test('contains newly added sub-views and missing pages', () => {
    const block = extractObjectArray('TOUR_PAGES')
    for (const id of ['login-register', 'problem-ai', 'problem-submission', 'user-home', 'settings']) {
      expect(block).toMatch(new RegExp(`id:\\s*['"]${id}['"]`))
    }
  })

  test('does not include the hidden global submission list guide card', () => {
    const block = extractObjectArray('TOUR_PAGES')
    expect(block).not.toMatch(/id:\s*['"]submission-list['"]/)
    expect(block).not.toMatch(/全局提交记录/)
    expect(block).not.toMatch(/target:\s*['"]\/status['"]/)
  })

  test('every page declares a screenshot under /assets/manual/screenshots/', () => {
    const block = extractObjectArray('TOUR_PAGES')
    const matches = block.match(/SCREENSHOT_BASE/g) || []
    expect(matches.length).toBe(14)
  })
})

describe('manualContent.js · CORE_OPERATIONS / QA_GUIDE 已删除', () => {
  test('CORE_OPERATIONS 不再被导出（内容拆到 ai / context / qa / flow / tour）', () => {
    expect(SOURCE).not.toMatch(/export const CORE_OPERATIONS/)
  })

  test('QA_GUIDE 不再被导出（内容拆到 SectionCoursewareQa）', () => {
    expect(SOURCE).not.toMatch(/export const QA_GUIDE/)
  })

  test('文案中不再出现已下线的「协作编程」相关概念', () => {
    expect(SOURCE).not.toMatch(/协作编程|协同编程|共享代码|collab|collaboration/i)
  })
})

describe('manualContent.js · AI_CHARACTERS', () => {
  test('exports exactly 5 character profiles aligned with AGENTS.md', () => {
    expect(countObjectsInArray('AI_CHARACTERS')).toBe(5)
    const block = extractObjectArray('AI_CHARACTERS')
    for (const id of ['nene', 'yoshino', 'kanna', 'murasame', 'ayase']) {
      expect(block).toMatch(new RegExp(`id:\\s*['"]${id}['"]`))
    }
  })

  test('every character has avatar / duty / when / howTo / example fields', () => {
    const block = extractObjectArray('AI_CHARACTERS')
    expect((block.match(/avatar:/g) || []).length).toBe(5)
    expect((block.match(/duty:/g) || []).length).toBe(5)
    expect((block.match(/when:/g) || []).length).toBe(5)
    expect((block.match(/howTo:/g) || []).length).toBe(5)
    expect((block.match(/example:/g) || []).length).toBe(5)
  })

  test('avatar paths under /assets/characters/', () => {
    const block = extractObjectArray('AI_CHARACTERS')
    expect(block).toMatch(/\/assets\/characters\//)
  })
})

describe('manualContent.js · 新增 AI 教学数据 (ai 三段式)', () => {
  test('AI_CAPABILITIES 覆盖 10 项能力，含骨架代码 / 拼装挑战 fallback', () => {
    expect(countObjectsInArray('AI_CAPABILITIES')).toBe(10)
    const block = extractObjectArray('AI_CAPABILITIES')
    for (const id of ['explain-problem', 'split-io', 'split-thought',
      'skeleton-fill', 'parsons-fallback',
      'locate-bug', 'translate-error', 'review-code', 'summarize-kc',
      'recommend-similar']) {
      expect(block).toMatch(new RegExp(`id:\\s*['"]${id}['"]`))
    }
  })

  test('RECOMMENDED_PROMPTS 4 条推荐提问，每条带 when/prompt/why', () => {
    expect(countObjectsInArray('RECOMMENDED_PROMPTS')).toBe(4)
    const block = extractObjectArray('RECOMMENDED_PROMPTS')
    expect((block.match(/when:/g) || []).length).toBe(4)
    expect((block.match(/prompt:/g) || []).length).toBe(4)
    expect((block.match(/why:/g) || []).length).toBe(4)
  })

  test('DISCOURAGED_PROMPTS 至少包含 2 条反例', () => {
    expect(countObjectsInArray('DISCOURAGED_PROMPTS')).toBeGreaterThanOrEqual(2)
    const block = extractObjectArray('DISCOURAGED_PROMPTS')
    expect(block).toMatch(/直接给我答案|帮我把整段代码写完/)
  })
})

describe('manualContent.js · 新增 @ 上下文引用数据', () => {
  test('CONTEXT_TOKENS 列出 10 个 @ token (1 个 @card + 7 个 @last_* + @page + @courseware)', () => {
    expect(countObjectsInArray('CONTEXT_TOKENS')).toBe(10)
    const block = extractObjectArray('CONTEXT_TOKENS')
    for (const tok of ['@card:', '@last_guide', '@last_ideate', '@last_error',
      '@last_post_ac', '@last_transfer', '@last_review', '@last_visualize',
      '@page:', '@courseware:']) {
      expect(block).toContain(tok)
    }
  })

  test('CONTEXT_EXAMPLES 6 条示例提问，包含 @page 章.页 与 @courseware 示例', () => {
    expect(countObjectsInArray('CONTEXT_EXAMPLES')).toBe(6)
    const block = extractObjectArray('CONTEXT_EXAMPLES')
    expect((block.match(/prompt:/g) || []).length).toBe(6)
    expect((block.match(/label:/g) || []).length).toBe(6)
    expect(block).toContain('@courseware:')
    expect(block).toContain('@page:1.7')
  })

  test('CONTEXT_TIPS 至少 5 条使用建议（含 @page 二级目录与课件包隔离提示）', () => {
    const m = SOURCE.match(/export const CONTEXT_TIPS\s*=\s*\[([\s\S]*?)\]/)
    expect(m).toBeTruthy()
    const block = m[1]
    const lines = block.split(',').map(s => s.trim()).filter(s => s.startsWith("'") || s.startsWith('"') || s.startsWith('`'))
    expect(lines.length).toBeGreaterThanOrEqual(5)
    expect(block).toContain('@page')
    expect(block).toContain('当前题目所属的课程包')
  })
})

describe('manualContent.js · 新增 课件问答数据', () => {
  test('COURSEWARE_QA_SCOPE 7 条用法（字符串数组）', () => {
    const m = SOURCE.match(/export const COURSEWARE_QA_SCOPE\s*=\s*\[([\s\S]*?)\]/)
    expect(m).toBeTruthy()
    const lines = m[1].split(',').map(s => s.trim()).filter(s => s.startsWith("'") || s.startsWith('"') || s.startsWith('`'))
    expect(lines.length).toBe(7)
  })

  test('COURSEWARE_QA_PROMPTS 6 个提问模板（含 @page:章.页 直引模板）', () => {
    expect(countObjectsInArray('COURSEWARE_QA_PROMPTS')).toBe(6)
    const block = extractObjectArray('COURSEWARE_QA_PROMPTS')
    expect((block.match(/label:/g) || []).length).toBe(6)
    expect((block.match(/prompt:/g) || []).length).toBe(6)
    expect(block).toContain('@page:1.7')
  })

  test('COURSEWARE_QA_NOTES 至少 6 条注意事项（含二级目录 / page 双语法）', () => {
    const m = SOURCE.match(/export const COURSEWARE_QA_NOTES\s*=\s*\[([\s\S]*?)\]/)
    expect(m).toBeTruthy()
    const block = m[1]
    const lines = block.split(',').map(s => s.trim()).filter(s => s.startsWith("'") || s.startsWith('"') || s.startsWith('`'))
    expect(lines.length).toBeGreaterThanOrEqual(6)
    expect(block).toContain('@page:1.7')
    expect(block).toContain('/page')
  })
})

describe('manualContent.js · HERO_CAPABILITIES (Hero 4 张能力卡)', () => {
  test('exports 4 hero capability cards', () => {
    expect(countObjectsInArray('HERO_CAPABILITIES')).toBe(4)
  })

  test('每张能力卡的 target 必须是合法的 SECTION id', () => {
    const block = extractObjectArray('HERO_CAPABILITIES')
    const targets = [...block.matchAll(/target:\s*['"]([a-z-]+)['"]/g)].map(m => m[1])
    expect(targets.length).toBe(4)
    for (const t of targets) {
      expect(VALID_SECTION_TARGETS.has(t)).toBe(true)
    }
  })

  test('包含 ai / context / qa / flow 4 个核心入口', () => {
    const block = extractObjectArray('HERO_CAPABILITIES')
    const targets = [...block.matchAll(/target:\s*['"]([a-z-]+)['"]/g)].map(m => m[1]).sort()
    expect(targets).toEqual(['ai', 'context', 'flow', 'qa'])
  })
})

describe('manualContent.js · FAQ_ITEMS', () => {
  test('keeps at least 8 essential FAQ entries (随功能扩展会增加)', () => {
    expect(countObjectsInArray('FAQ_ITEMS')).toBeGreaterThanOrEqual(8)
  })

  test('covers core FAQ topics (Pending / 报错 / AI / 课件问答 / 反思 / @ 引用 / 拼装挑战 / @page)', () => {
    const block = extractObjectArray('FAQ_ITEMS')
    expect(block).toMatch(/Pending/)
    expect(block).toMatch(/报错|错误信息/)
    expect(block).toMatch(/AI/)
    expect(block).toMatch(/课件问答/)
    expect(block).toMatch(/反思|错题本/)
    expect(block).toMatch(/@ 引用|@ 引/)
    expect(block).toMatch(/拼装挑战/)
    expect(block).toMatch(/@page:1\.7/)
  })

  test('does NOT include the 4 deprecated questions (forgot pwd / mute / animation / dark)', () => {
    const block = extractObjectArray('FAQ_ITEMS')
    expect(block).not.toMatch(/忘记密码怎么找回/)
    expect(block).not.toMatch(/怎么关掉奶蛙的笑声/)
    expect(block).not.toMatch(/怎么关掉所有花里胡哨/)
    expect(block).not.toMatch(/怎么切换暗黑模式/)
  })
})

describe('manualContent.js · TIPS / FEEDBACK_ITEMS', () => {
  test('TIPS has at least 4 advice cards', () => {
    expect(countObjectsInArray('TIPS')).toBeGreaterThanOrEqual(4)
  })

  test('FEEDBACK_ITEMS has 3 cards (bug / fun / a11y)', () => {
    expect(countObjectsInArray('FEEDBACK_ITEMS')).toBe(3)
    const block = extractObjectArray('FEEDBACK_ITEMS')
    expect(block).toMatch(/bug/i)
    expect(block).toMatch(/趣味模式/)
    expect(block).toMatch(/键盘/)
  })
})

describe('manualContent.js · COMMAND_PALETTE_ITEMS', () => {
  test('contains exactly one entry per side action kind: laugh / fun / widget / top', () => {
    for (const id of ['naiwa-laugh', 'fun-toggle', 'widget-toggle', 'back-to-top']) {
      const matches = SOURCE.match(new RegExp(`id:\\s*['"]${id}['"]`, 'g')) || []
      expect(matches.length).toBe(1)
    }
  })

  test('does NOT include the theme-toggle entry (dark mode removed)', () => {
    expect(SOURCE).not.toMatch(/id:\s*['"]theme-toggle['"]/)
    expect(SOURCE).not.toMatch(/kind:\s*['"]theme['"]/)
  })

  test('uses every section as a goto target via SECTIONS.map', () => {
    expect(SOURCE).toMatch(/COMMAND_PALETTE_ITEMS\s*=\s*\[\s*\.\.\.SECTIONS\.map/)
    expect(SOURCE).toMatch(/kind:\s*['"]goto['"]/)
    expect(SOURCE).toMatch(/payload:\s*\{\s*section:/)
  })

  test('declares the 5 active kinds (goto/laugh/fun/widget/top)', () => {
    for (const k of ['goto', 'laugh', 'fun', 'widget', 'top']) {
      expect(SOURCE).toMatch(new RegExp(`kind:\\s*['"]${k}['"]`))
    }
  })
})

describe('manualContent.js · NAIWA_BUBBLE_LINES', () => {
  test('contains at least 3 rotating bubble lines', () => {
    const m = SOURCE.match(/export const NAIWA_BUBBLE_LINES\s*=\s*\[([\s\S]*?)\]/)
    expect(m).toBeTruthy()
    const lines = m[1].split(',').map(s => s.trim()).filter(s => s.startsWith("'") || s.startsWith('"'))
    expect(lines.length).toBeGreaterThanOrEqual(3)
  })

  test('does not put naiwa in instructional voice', () => {
    const m = SOURCE.match(/export const NAIWA_BUBBLE_LINES\s*=\s*\[([\s\S]*?)\]/)
    const block = m[1]
    expect(block).not.toMatch(/^\s*['"]奶蛙说/m)
    expect(block).not.toMatch(/我教你/)
    expect(block).not.toMatch(/我来给你讲/)
  })
})

/* eslint-env jest */

/**
 * `manualContent.js` 数据完整性的源契约测试。
 *
 * 项目使用 jest 23.6（不会自动加载 babel.config.js 来转换 ES module
 * source files），因此沿用其他 *.spec.js 的模式：读取源文件文本，
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
  test('declares exactly 9 sections in plan-defined order', () => {
    expect(countObjectsInArray('SECTIONS')).toBe(9)
    const block = extractObjectArray('SECTIONS')
    const idOrder = [...block.matchAll(/id:\s*['"]([a-z-]+)['"]/g)].map(m => m[1])
    expect(idOrder).toEqual([
      'welcome', 'flow', 'tour', 'core', 'ai', 'faq', 'tips', 'gallery', 'feedback'
    ])
  })

  test('only the gallery section is funOnly', () => {
    const block = extractObjectArray('SECTIONS')
    const matches = block.match(/funOnly:\s*true/g) || []
    expect(matches).toHaveLength(1)
    // 同步确认 funOnly 出现在 gallery 对象内
    const galleryBlock = block.match(/id:\s*['"]gallery['"][\s\S]*?\}/)
    expect(galleryBlock[0]).toMatch(/funOnly:\s*true/)
  })

  test('every section carries id / title / subtitle / sticker', () => {
    const block = extractObjectArray('SECTIONS')
    expect((block.match(/title:\s*['"]/g) || []).length).toBeGreaterThanOrEqual(9)
    expect((block.match(/subtitle:\s*['"]/g) || []).length).toBeGreaterThanOrEqual(9)
    expect((block.match(/sticker:\s*\d+/g) || []).length).toBeGreaterThanOrEqual(9)
  })
})

describe('manualContent.js · naiwa asset paths', () => {
  test('hero / audio / motion paths use /assets/manual/naiwa/ root', () => {
    expect(SOURCE).toContain("export const NAIWA_HERO = `${NAIWA_BASE}/hero/naiwa-hero.png`")
    expect(SOURCE).toContain("export const NAIWA_LAUGH_AUDIO = `${NAIWA_BASE}/audio/nailong-laugh.m4a`")
    expect(SOURCE).toMatch(/NAIWA_BASE\s*=\s*['"]\/assets\/manual\/naiwa['"]/)
  })

  test('NAIWA_MOTION exposes 4 keys all pointing to .gif files', () => {
    // 模板字符串里的 ${NAIWA_BASE} 会被非贪婪正则误判为对象闭合，故按
    // brace-depth 手动切片定位 NAIWA_MOTION 的对象字面量。
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
      // key 后面到下一行末必有一个 .gif 引用
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
    // hero(1) + audio(1) + motion(4) + stickers(8) + faq(4) + gallery(≥12) = ≥30
    const stickersCount = countObjectsInArray('NAIWA_STICKERS')
    const faqCount = countObjectsInArray('NAIWA_FAQ_ICONS')
    const galleryCount = countObjectsInArray('NAIWA_GALLERY')
    const total = 1 + 1 + 4 + stickersCount + faqCount + galleryCount
    expect(total).toBeGreaterThanOrEqual(20)
  })
})

describe('manualContent.js · QUICK_START_STEPS', () => {
  test('exports exactly 3 quick-start steps', () => {
    expect(countObjectsInArray('QUICK_START_STEPS')).toBe(3)
  })

  test('steps are numbered 1, 2, 3', () => {
    const block = extractObjectArray('QUICK_START_STEPS')
    expect(block).toMatch(/step:\s*1/)
    expect(block).toMatch(/step:\s*2/)
    expect(block).toMatch(/step:\s*3/)
  })
})

describe('manualContent.js · FLOW_NODES', () => {
  test('flow has exactly 8 nodes', () => {
    expect(countObjectsInArray('FLOW_NODES')).toBe(8)
  })

  test('contains the canonical novice waypoints', () => {
    const block = extractObjectArray('FLOW_NODES')
    for (const id of ['register', 'home', 'pack', 'problem', 'code', 'ai', 'submit', 'review']) {
      expect(block).toMatch(new RegExp(`id:\\s*['"]${id}['"]`))
    }
  })

  test('every flow node points to a real section id as target', () => {
    const block = extractObjectArray('FLOW_NODES')
    const targets = [...block.matchAll(/target:\s*['"]([a-z-]+)['"]/g)].map(m => m[1])
    expect(targets.length).toBe(8)
    const validTargets = new Set(['welcome', 'flow', 'tour', 'core', 'ai', 'faq', 'tips', 'gallery', 'feedback'])
    for (const t of targets) {
      expect(validTargets.has(t)).toBe(true)
    }
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

describe('manualContent.js · CORE_OPERATIONS', () => {
  test('exports 5 core operations matching plan §4.3', () => {
    expect(countObjectsInArray('CORE_OPERATIONS')).toBe(5)
    const block = extractObjectArray('CORE_OPERATIONS')
    for (const id of ['write-problem', 'use-ai-card', 'use-notebook', 'use-qa', 'join-classroom']) {
      expect(block).toMatch(new RegExp(`id:\\s*['"]${id}['"]`))
    }
  })

  test('does not mention hidden collaborative coding features', () => {
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
    const avatarCount = (block.match(/avatar:/g) || []).length
    const dutyCount = (block.match(/duty:/g) || []).length
    const whenCount = (block.match(/when:/g) || []).length
    const howToCount = (block.match(/howTo:/g) || []).length
    const exampleCount = (block.match(/example:/g) || []).length
    expect(avatarCount).toBe(5)
    expect(dutyCount).toBe(5)
    expect(whenCount).toBe(5)
    expect(howToCount).toBe(5)
    expect(exampleCount).toBe(5)
  })

  test('avatar paths under /assets/characters/', () => {
    const block = extractObjectArray('AI_CHARACTERS')
    expect(block).toMatch(/\/assets\/characters\//)
  })
})

describe('manualContent.js · FAQ_ITEMS', () => {
  test('contains 8 essential FAQ entries (after removing redundant ones)', () => {
    expect(countObjectsInArray('FAQ_ITEMS')).toBe(8)
  })

  test('covers core FAQ topics (Pending / 报错 / AI 卡 / 课件问答 / 反思)', () => {
    const block = extractObjectArray('FAQ_ITEMS')
    expect(block).toMatch(/Pending/)
    expect(block).toMatch(/报错|错误信息/)
    expect(block).toMatch(/AI 卡/)
    expect(block).toMatch(/课件问答/)
    expect(block).toMatch(/反思|错题本/)
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

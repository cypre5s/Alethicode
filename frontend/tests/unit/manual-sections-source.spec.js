/* eslint-env jest */

/**
 * 9 个 section 子组件的源契约测试。
 * 每个 section 必须满足：
 *   - 拥有正确的 id（与 SECTIONS 对齐）；
 *   - 渲染章节标题贴片 ManualNaiwaSticker；
 *   - 引用 manualContent.js 的对应数据；
 *   - 不出现"奶蛙说……"等让奶蛙担任讲解员角色的台词。
 */

const fs = require('fs')
const path = require('path')

function read (rel) {
  return fs.readFileSync(
    path.resolve(__dirname, '../../src/pages/oj/views/manual/sections', rel),
    'utf8'
  )
}

const SECTIONS = [
  { file: 'SectionWelcome.vue', id: 'welcome' },
  { file: 'SectionFlow.vue', id: 'flow' },
  { file: 'SectionTour.vue', id: 'tour' },
  { file: 'SectionCore.vue', id: 'core' },
  { file: 'SectionAI.vue', id: 'ai' },
  { file: 'SectionFAQ.vue', id: 'faq' },
  { file: 'SectionTips.vue', id: 'tips' },
  { file: 'SectionGallery.vue', id: 'gallery' },
  { file: 'SectionFeedback.vue', id: 'feedback' }
]

describe('All 9 sections · 公共契约', () => {
  for (const s of SECTIONS) {
    describe(s.file, () => {
      const src = read(s.file)

      test(`section root <section id="${s.id}"> matches the planned anchor`, () => {
        expect(src).toMatch(new RegExp(`<section[^>]*id="${s.id}"`))
      })

      test('imports ManualNaiwaSticker for the title sticker', () => {
        // SectionGallery 例外：不需要 sticker，但其余必须有
        if (s.id !== 'gallery') {
          expect(src).toMatch(/ManualNaiwaSticker/)
        } else {
          expect(src).toMatch(/ManualNaiwaSticker/) // gallery 自己仍然挂一个标题贴片
        }
      })

      test('declares scoped less and imports shared.less', () => {
        expect(src).toMatch(/<style[^>]*lang="less"[^>]*scoped[^>]*>/)
        expect(src).toMatch(/@import\s+['"]\.\/shared\.less['"]/)
      })

      test('does NOT use 奶蛙第一人称讲解口吻', () => {
        // plan §1 严禁让奶蛙开口"教学"，这里做硬约束
        expect(src).not.toMatch(/奶蛙说[:：]/)
        expect(src).not.toMatch(/我教你/)
        expect(src).not.toMatch(/我来给你讲/)
      })
    })
  }
})

describe('SectionWelcome · 欢迎与快速开始', () => {
  const src = read('SectionWelcome.vue')

  test('imports QUICK_START_STEPS from manualContent', () => {
    expect(src).toMatch(/import\s*{\s*QUICK_START_STEPS\s*}\s*from/)
  })

  test('emits jump event for both CTAs', () => {
    const matches = src.match(/\$emit\(['"]jump['"][^)]*\)/g) || []
    expect(matches.length).toBeGreaterThanOrEqual(2)
  })

  test('uses warm-grad-primary on primary CTA + shadow-warm box-shadow', () => {
    expect(src).toContain('--warm-grad-primary')
    expect(src).toContain('--shadow-warm')
  })
})

describe('SectionFlow · 新手路径', () => {
  const src = read('SectionFlow.vue')

  test('embeds ManualFlowDiagram with @jump → emit jump', () => {
    expect(src).toMatch(/<ManualFlowDiagram[\s\S]*?@jump=/)
    expect(src).toMatch(/\$emit\(['"]jump['"],\s*target\)/)
  })

  test('mentions 8 步 in the section header (matches plan §4.3)', () => {
    expect(src).toMatch(/8\s*步/)
  })
})

describe('SectionTour · 页面导览', () => {
  const src = read('SectionTour.vue')

  test('imports TOUR_PAGES from manualContent', () => {
    expect(src).toMatch(/import\s*{\s*TOUR_PAGES\s*}\s*from/)
  })

  test('image onError swaps to 「截图待补」placeholder', () => {
    expect(src).toMatch(/@error="onImgError/)
    expect(src).toContain('截图待补')
  })

  test('「前往该页 →」button uses $router.push without throwing on missing route', () => {
    expect(src).toMatch(/this\.\$router\.push\(target\)\.catch\(\(\)\s*=>/)
    expect(src).toContain('前往该页 →')
  })

  test('tour screenshots can be clicked to open a larger preview dialog', () => {
    expect(src).toContain('@click="openPreview(page)"')
    expect(src).toMatch(/<ElDialog[\s\S]*previewVisible/)
    expect(src).toContain('previewImage')
  })

  test('every screenshot path lives under /assets/manual/screenshots/', () => {
    // 通过 manualContent.js 间接验证
    const content = fs.readFileSync(
      path.resolve(__dirname, '../../src/pages/oj/views/manual/manualContent.js'),
      'utf8'
    )
    expect(content).toMatch(/SCREENSHOT_BASE\s*=\s*['"]\/assets\/manual\/screenshots['"]/)
  })
})

describe('SectionCore · 核心操作', () => {
  const src = read('SectionCore.vue')

  test('uses ElCollapse + ElCollapseItem (accordion = single expand)', () => {
    expect(src).toMatch(/import\s*{\s*ElCollapse,\s*ElCollapseItem\s*}/)
    expect(src).toMatch(/<ElCollapse[^>]*accordion/)
  })

  test('imports CORE_OPERATIONS array', () => {
    expect(src).toMatch(/CORE_OPERATIONS/)
  })

  test('default open key is "write-problem" (most common entry point)', () => {
    expect(src).toMatch(/activeKeys:\s*['"]write-problem['"]/)
  })
})

describe('SectionAI · 5 角色介绍', () => {
  const src = read('SectionAI.vue')

  test('imports AI_CHARACTERS and QA_GUIDE from manualContent', () => {
    expect(src).toMatch(/AI_CHARACTERS/)
    expect(src).toMatch(/QA_GUIDE/)
  })

  test('avatar onError gracefully shows fallback initial', () => {
    expect(src).toMatch(/onAvatarError/)
    expect(src).toMatch(/_avatarFailed/)
  })

  test('renders the 「AI 是辅助，不是答案」warning banner per plan §1', () => {
    expect(src).toMatch(/AI 给的引导和分析有时会错/)
  })
})

describe('SectionFAQ · 常见问题', () => {
  const src = read('SectionFAQ.vue')

  test('toggle behaves like accordion (open one, close others)', () => {
    expect(src).toMatch(/this\.openIdx\s*===\s*idx\s*\?\s*-1\s*:\s*idx/)
  })

  test('uses NAIWA_FAQ_ICONS rotation indexed by FAQ position', () => {
    expect(src).toContain('NAIWA_FAQ_ICONS')
    expect(src).toMatch(/list\[idx\s*%\s*list\.length\]/)
  })

  test('chevron flips between + and − to indicate state', () => {
    expect(src).toMatch(/openIdx === idx \? '−' : '\+'/)
  })

  test('aria-expanded reflects open state for screen readers', () => {
    expect(src).toMatch(/:aria-expanded="openIdx === idx"/)
  })
})

describe('SectionTips · 使用建议', () => {
  const src = read('SectionTips.vue')

  test('imports TIPS data array', () => {
    expect(src).toMatch(/TIPS/)
  })

  test('uses warm subtle surface tokens (warm-bg-subtle + border-warm)', () => {
    expect(src).toContain('--warm-bg-subtle')
    expect(src).toContain('--border-warm')
  })
})

describe('SectionGallery · 奶蛙图鉴（趣味专栏）', () => {
  const src = read('SectionGallery.vue')

  test('declares funMode prop with default true', () => {
    expect(src).toMatch(/funMode:\s*{\s*type:\s*Boolean,\s*default:\s*true\s*}/)
  })

  test('forwards burst to parent for confetti integration', () => {
    expect(src).toMatch(/\$emit\(['"]burst['"]/)
  })

  test('uses content-visibility: auto for off-screen perf', () => {
    expect(src).toContain('content-visibility: auto')
    expect(src).toContain('contain-intrinsic-size')
  })

  test('renders ManualNaiwaGallery with funMode binding', () => {
    expect(src).toMatch(/<ManualNaiwaGallery[\s\S]*?:fun-mode="funMode"/)
  })
})

describe('SectionFeedback · 反馈与帮助', () => {
  const src = read('SectionFeedback.vue')

  test('imports FEEDBACK_ITEMS data', () => {
    expect(src).toMatch(/FEEDBACK_ITEMS/)
  })

  test('"直接去做题 →" navigates to /problem (escape hatch from manual)', () => {
    expect(src).toMatch(/\$router\.push\(['"]\/problem['"]/)
  })

  test('"回到顶部，再走一遍" emits jump to welcome', () => {
    expect(src).toMatch(/jump['"],\s*['"]welcome['"]/)
  })
})

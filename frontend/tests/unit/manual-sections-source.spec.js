/* eslint-env jest */

/**
 * 10 个 section 子组件的源契约测试。
 * 每个 section 必须满足：
 *   - 拥有正确的 id（与 SECTIONS 对齐）；
 *   - 引用 manualContent.js 的对应数据；
 *   - 不出现"奶蛙说……"等让奶蛙担任讲解员角色的台词；
 *   - 默认风格走 Notion / Cursor 文档风（sticker 通过父级 .is-fun + :deep 控制显隐，
 *     section 自身仍可挂 ManualNaiwaSticker 组件）。
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
  { file: 'SectionAI.vue', id: 'ai' },
  { file: 'SectionContext.vue', id: 'context' },
  { file: 'SectionCoursewareQa.vue', id: 'qa' },
  { file: 'SectionFlow.vue', id: 'flow' },
  { file: 'SectionTips.vue', id: 'tips' },
  { file: 'SectionFAQ.vue', id: 'faq' },
  { file: 'SectionTour.vue', id: 'tour' },
  { file: 'SectionGallery.vue', id: 'gallery' },
  { file: 'SectionFeedback.vue', id: 'feedback' }
]

describe('All 10 sections · 公共契约', () => {
  for (const s of SECTIONS) {
    describe(s.file, () => {
      const src = read(s.file)

      test(`section root <section id="${s.id}"> matches the planned anchor`, () => {
        expect(src).toMatch(new RegExp(`<section[^>]*id="${s.id}"`))
      })

      test('imports ManualNaiwaSticker for the title sticker', () => {
        expect(src).toMatch(/ManualNaiwaSticker/)
      })

      test('declares scoped less and imports shared.less', () => {
        expect(src).toMatch(/<style[^>]*lang="less"[^>]*scoped[^>]*>/)
        expect(src).toMatch(/@import\s+['"]\.\/shared\.less['"]/)
      })

      test('does NOT use 奶蛙第一人称讲解口吻', () => {
        expect(src).not.toMatch(/奶蛙说[:：]/)
        expect(src).not.toMatch(/我教你/)
        expect(src).not.toMatch(/我来给你讲/)
      })
    })
  }
})

describe('SectionWelcome · 快速开始', () => {
  const src = read('SectionWelcome.vue')

  test('imports QUICK_START_STEPS from manualContent', () => {
    expect(src).toMatch(/import\s*{\s*QUICK_START_STEPS\s*}\s*from/)
  })

  test('emits jump event for both CTAs (走完整流程 / 读 AI 说明)', () => {
    const matches = src.match(/\$emit\(['"]jump['"][^)]*\)/g) || []
    expect(matches.length).toBeGreaterThanOrEqual(2)
    expect(src).toMatch(/jump['"],\s*['"]flow['"]/)
    expect(src).toMatch(/jump['"],\s*['"]ai['"]/)
  })

  test('每张步骤卡渲染 where / look / why 三段上下文', () => {
    expect(src).toContain('在哪做')
    expect(src).toContain('看什么')
    expect(src).toContain('为什么')
  })
})

describe('SectionAI · AI 导学助手三段式', () => {
  const src = read('SectionAI.vue')

  test('imports the new AI block constants (AI_CHARACTERS / AI_CAPABILITIES / RECOMMENDED_PROMPTS / DISCOURAGED_PROMPTS)', () => {
    expect(src).toMatch(/AI_CHARACTERS/)
    expect(src).toMatch(/AI_CAPABILITIES/)
    expect(src).toMatch(/RECOMMENDED_PROMPTS/)
    expect(src).toMatch(/DISCOURAGED_PROMPTS/)
  })

  test('does NOT import the deprecated QA_GUIDE (内容拆到 SectionCoursewareQa)', () => {
    expect(src).not.toMatch(/QA_GUIDE/)
  })

  test('引用 ManualPromptCard 渲染推荐提问', () => {
    expect(src).toMatch(/import\s+ManualPromptCard\s+from/)
    expect(src).toMatch(/<ManualPromptCard/)
  })

  test('avatar onError gracefully shows fallback initial', () => {
    expect(src).toMatch(/onAvatarError/)
    expect(src).toMatch(/_avatarFailed/)
  })

  test('renders the 「AI 是辅助，不是答案」warning banner per plan §1', () => {
    expect(src).toMatch(/AI 给的引导和分析有时会错/)
  })
})

describe('SectionContext · @ 上下文引用', () => {
  const src = read('SectionContext.vue')

  test('imports CONTEXT_TOKENS / CONTEXT_EXAMPLES / CONTEXT_TIPS', () => {
    expect(src).toMatch(/CONTEXT_TOKENS/)
    expect(src).toMatch(/CONTEXT_EXAMPLES/)
    expect(src).toMatch(/CONTEXT_TIPS/)
  })

  test('renders an inline SVG explainer (no external screenshot dependency)', () => {
    expect(src).toMatch(/<svg[\s\S]*?viewBox=/)
  })

  test('renders the @ token table with role="table" for a11y', () => {
    expect(src).toMatch(/role="table"/)
    expect(src).toMatch(/role="columnheader"/)
    expect(src).toMatch(/role="row"/)
  })

  test('uses ManualPromptCard to render @ examples', () => {
    expect(src).toMatch(/import\s+ManualPromptCard\s+from/)
    expect(src).toMatch(/<ManualPromptCard/)
  })
})

describe('SectionCoursewareQa · 课件问答', () => {
  const src = read('SectionCoursewareQa.vue')

  test('imports COURSEWARE_QA_SCOPE / COURSEWARE_QA_PROMPTS / COURSEWARE_QA_NOTES', () => {
    expect(src).toMatch(/COURSEWARE_QA_SCOPE/)
    expect(src).toMatch(/COURSEWARE_QA_PROMPTS/)
    expect(src).toMatch(/COURSEWARE_QA_NOTES/)
  })

  test('contains the 「打开课件问答 →」button + router.push to /language-pack-qa', () => {
    expect(src).toContain('打开课件问答 →')
    expect(src).toMatch(/\$router\.push\(['"]\/language-pack-qa['"]/)
  })

  test('uses ManualPromptCard to render QA examples', () => {
    expect(src).toMatch(/<ManualPromptCard/)
  })

  test('explicitly contrasts itself with AI 导学助手', () => {
    expect(src).toContain('AI 导学助手')
    expect(src).toContain('课件问答')
  })
})

describe('SectionFlow · 8 步学习闭环', () => {
  const src = read('SectionFlow.vue')

  test('imports LEARNING_LOOP_STEPS for the explainer list', () => {
    expect(src).toMatch(/import[\s\S]*LEARNING_LOOP_STEPS/)
  })

  test('embeds ManualFlowDiagram with @jump → emit jump', () => {
    expect(src).toMatch(/<ManualFlowDiagram[\s\S]*?@jump=/)
    expect(src).toMatch(/\$emit\(['"]jump['"],\s*target\)/)
  })

  test('mentions 8 步 in the section header', () => {
    expect(src).toMatch(/8\s*步/)
  })
})

describe('SectionTour · 页面导览（附录）', () => {
  const src = read('SectionTour.vue')

  test('imports TOUR_PAGES from manualContent', () => {
    expect(src).toMatch(/import\s*{\s*TOUR_PAGES\s*}\s*from/)
  })

  test('header kicker 标注为附录', () => {
    expect(src).toMatch(/Tour|附录/)
  })

  test('image onError swaps to 「截图待补」placeholder', () => {
    expect(src).toMatch(/@error="onImgError/)
    expect(src).toContain('截图待补')
  })

  test('「前往该页 →」button uses $router.push without throwing on missing route', () => {
    expect(src).toMatch(/this\.\$router\.push\(target\)\.catch\(\(\)\s*=>/)
    expect(src).toContain('前往该页 →')
  })

  test('every screenshot path lives under /assets/manual/screenshots/', () => {
    const content = fs.readFileSync(
      path.resolve(__dirname, '../../src/pages/oj/views/manual/manualContent.js'),
      'utf8'
    )
    expect(content).toMatch(/SCREENSHOT_BASE\s*=\s*['"]\/assets\/manual\/screenshots['"]/)
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

describe('SectionTips · 学习建议', () => {
  const src = read('SectionTips.vue')

  test('imports TIPS data array', () => {
    expect(src).toMatch(/TIPS/)
  })

  test('section header refers to 「学习建议」 (改名后)', () => {
    expect(src).toMatch(/学习建议|使用建议/)
  })
})

describe('SectionGallery · 奶蛙图鉴（趣味专栏）', () => {
  const src = read('SectionGallery.vue')

  test('declares funMode prop', () => {
    expect(src).toMatch(/funMode:\s*{\s*type:\s*Boolean/)
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

describe('Deprecated · SectionCore.vue 已删除', () => {
  test('SectionCore.vue 不再存在于 sections 目录', () => {
    const filePath = path.resolve(
      __dirname,
      '../../src/pages/oj/views/manual/sections/SectionCore.vue'
    )
    expect(fs.existsSync(filePath)).toBe(false)
  })
})

describe('ManualPromptCard · 公用提示卡 (新增)', () => {
  const src = fs.readFileSync(
    path.resolve(__dirname, '../../src/pages/oj/views/manual/ManualPromptCard.vue'),
    'utf8'
  )

  test('exposes label / prompt / note props', () => {
    expect(src).toMatch(/label:\s*{\s*type:\s*String,\s*required:\s*true/)
    expect(src).toMatch(/prompt:\s*{\s*type:\s*String,\s*required:\s*true/)
    expect(src).toMatch(/note:\s*{\s*type:\s*String,\s*default:\s*''/)
  })

  test('uses navigator.clipboard with execCommand fallback for offline copy', () => {
    expect(src).toMatch(/navigator\.clipboard\.writeText/)
    expect(src).toMatch(/document\.execCommand\(['"]copy['"]\)/)
  })

  test('shows a "已复制 / 复制" affordance after copy', () => {
    expect(src).toMatch(/已复制/)
    expect(src).toMatch(/copied/)
  })

  test('does NOT add new dependencies (pure Vue + scoped less)', () => {
    expect(src).not.toMatch(/^import\s+[A-Za-z_]+\s+from\s+['"][^.]/m)
  })
})

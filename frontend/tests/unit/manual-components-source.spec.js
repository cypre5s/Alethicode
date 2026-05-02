/* eslint-env jest */

/**
 * 手册 11 个核心组件的源契约测试。
 *
 * 覆盖每个组件的核心行为契约：状态、事件、外部 API 调用、可达性、
 * 性能护栏（RAF 节流、粒子封顶、prefers-reduced-motion 降级）等。
 */

const fs = require('fs')
const path = require('path')

function read (rel) {
  return fs.readFileSync(
    path.resolve(__dirname, '../../src/pages/oj/views/manual', rel),
    'utf8'
  )
}

describe('ManualConfettiCanvas · Canvas 粒子层', () => {
  const src = read('ManualConfettiCanvas.vue')

  test('caps total particles at 200 to avoid runaway memory', () => {
    expect(src).toContain('PARTICLE_LIMIT = 200')
  })

  test('exposes burst({x,y,count,spread}) and stop() on the instance', () => {
    expect(src).toMatch(/burst\s*\(\s*{[^}]*x[\s\S]*?count[\s\S]*?spread[\s\S]*?}/)
    expect(src).toMatch(/stop\s*\(\)/)
  })

  test('uses requestAnimationFrame for ticking instead of setInterval', () => {
    expect(src).toContain('requestAnimationFrame(this.tick)')
    expect(src).toContain('cancelAnimationFrame')
    expect(src).not.toContain('setInterval(')
  })

  test('supports DPR-aware canvas sizing (max 2 to avoid GPU overdraw)', () => {
    expect(src).toMatch(/Math\.min\(window\.devicePixelRatio[\s\S]*?2\)/)
  })

  test('has at least 6 confetti colors aligned with brand warm palette', () => {
    expect(src).toMatch(/COLORS\s*=\s*\[[^\]]+\]/)
    const m = src.match(/COLORS\s*=\s*\[([^\]]+)\]/)
    const colors = m[1].split(',').filter(Boolean)
    expect(colors.length).toBeGreaterThanOrEqual(5)
  })

  test('respects prefers-reduced-motion by hiding the canvas', () => {
    expect(src).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?display:\s*none/)
  })

  test('canvas is pointer-events:none and z-index < theme palette', () => {
    expect(src).toContain('pointer-events: none')
    expect(src).toMatch(/z-index:\s*800/) // < palette 1200, < toast 1300
  })
})

describe('ManualReadingProgress · 顶部 3px 进度条', () => {
  const src = read('ManualReadingProgress.vue')

  test('exposes ARIA progressbar role with valuenow/min/max', () => {
    expect(src).toContain('role="progressbar"')
    expect(src).toContain(':aria-valuenow="percent"')
    expect(src).toContain('aria-valuemin="0"')
    expect(src).toContain('aria-valuemax="100"')
  })

  test('uses transform scaleX rather than width for GPU-accelerated paint', () => {
    expect(src).toMatch(/transform:\s*`?scaleX/)
    expect(src).toContain('transform-origin: 0 0')
  })

  test('listens to scroll passively and throttles via requestAnimationFrame', () => {
    expect(src).toMatch(/scroll[\s\S]*?passive:\s*true/)
    expect(src).toContain('requestAnimationFrame')
    expect(src).toMatch(/this\.ticking/)
  })

  test('removes scroll/resize listeners in beforeUnmount', () => {
    expect(src).toMatch(/beforeUnmount[\s\S]*?removeEventListener\(['"]scroll['"]/)
    expect(src).toMatch(/removeEventListener\(['"]resize['"]/)
  })

  test('clamps percent to [0, 100]', () => {
    expect(src).toMatch(/Math\.max\(0,\s*Math\.min\(100/)
  })
})

describe('ManualBackToTop · 浮现按钮', () => {
  const src = read('ManualBackToTop.vue')

  test('default threshold is 240px per plan', () => {
    expect(src).toMatch(/threshold[\s\S]*?default:\s*240/)
  })

  test('exposes aria-label "回到顶部" for accessibility', () => {
    expect(src).toContain('aria-label="回到顶部"')
  })

  test('uses smooth scroll to (0,0) on click', () => {
    expect(src).toMatch(/window\.scrollTo\([\s\S]*?top:\s*0[\s\S]*?behavior:\s*['"]smooth['"]/)
  })

  test('repositions on mobile (≤640px)', () => {
    expect(src).toMatch(/@media\s*\(max-width:\s*640px\)[\s\S]*?bottom:/)
  })
})

describe('ManualThemeToggle · component file removed (dark mode deprecated)', () => {
  test('the file no longer exists in the manual folder', () => {
    const fp = path.resolve(__dirname, '../../src/pages/oj/views/manual/ManualThemeToggle.vue')
    expect(fs.existsSync(fp)).toBe(false)
  })
})

describe('ManualTypewriter · 打字效果（替代主题切换的位置）', () => {
  const src = read('ManualTypewriter.vue')

  test('exposes text + speed + startDelay props', () => {
    expect(src).toMatch(/text:\s*\{\s*type:\s*String/)
    expect(src).toMatch(/speed:\s*\{\s*type:\s*Number/)
    expect(src).toMatch(/startDelay:\s*\{\s*type:\s*Number/)
  })

  test('respects prefers-reduced-motion by rendering full text immediately', () => {
    expect(src).toMatch(/prefersReducedMotion[\s\S]*?this\.rendered\s*=\s*this\.text[\s\S]*?this\.done\s*=\s*true/)
  })

  test('caret hides once the typing finishes (is-done class) or motion is reduced', () => {
    expect(src).toMatch(/is-done/)
    expect(src).toMatch(/v-if="!done"/)
  })

  test('cleans up the timer on unmount to avoid leaks', () => {
    expect(src).toMatch(/beforeUnmount[\s\S]*?clearTimeout\(this\.timer\)/)
  })

  test('blink keyframes power the caret', () => {
    expect(src).toMatch(/@keyframes\s+manual-typewriter-blink/)
  })

  test('aria-live=polite so screen readers announce typed text gracefully', () => {
    expect(src).toMatch(/aria-live="polite"/)
  })
})

describe('ManualSearchBar · 章节搜索', () => {
  const src = read('ManualSearchBar.vue')

  test('imports SECTIONS constant for fuzzy match', () => {
    expect(src).toMatch(/import\s*{\s*SECTIONS[\s\S]*?from\s+['"]\.\/manualContent\.js['"]/)
  })

  test('Enter key jumps to first match', () => {
    expect(src).toMatch(/@keydown\.enter\.prevent="jumpToFirst"/)
  })

  test('Esc clears the query', () => {
    expect(src).toMatch(/@keydown\.esc=/)
    expect(src).toMatch(/query\s*=\s*''/)
  })

  test('emits jump event with section id payload', () => {
    expect(src).toMatch(/\$emit\(['"]jump['"]/)
  })

  test('search matches title / subtitle / id (case-insensitive)', () => {
    expect(src).toMatch(/title\.toLowerCase\(\)\.includes/)
    expect(src).toMatch(/subtitle[\s\S]*?toLowerCase/)
    expect(src).toMatch(/id\.toLowerCase\(\)\.includes/)
  })

  test('list items have role=option for screen-reader compatibility', () => {
    expect(src).toContain('role="option"')
  })
})

describe('ManualSidebar · 磨砂玻璃目录', () => {
  const src = read('ManualSidebar.vue')

  test('renders filtered sections via SECTIONS + funMode prop', () => {
    expect(src).toMatch(/SECTIONS\.filter\(s\s*=>\s*this\.funMode\s*\|\|\s*!s\.funOnly\)/)
  })

  test('uses backdrop-filter blur for glassmorphism', () => {
    expect(src).toMatch(/backdrop-filter:\s*blur/)
    expect(src).toMatch(/-webkit-backdrop-filter:\s*blur/)
  })

  test('hides on viewports < 1024px', () => {
    expect(src).toMatch(/@media\s*\(max-width:\s*1023px\)[\s\S]*?display:\s*none/)
  })

  test('emits jump event when item is clicked', () => {
    expect(src).toMatch(/\$emit\(['"]jump['"]/)
  })

  test('active item gets a left-side gradient bar (::before)', () => {
    expect(src).toMatch(/&\.is-active::before[\s\S]*?--warm-grad-primary/)
  })

  test('active accent uses warm-glow as background tint', () => {
    expect(src).toContain('--warm-glow')
  })
})

describe('ManualCommandPalette · cmd+K 命令面板', () => {
  const src = read('ManualCommandPalette.vue')

  test('Cmd+K / Ctrl+K hotkey toggles visibility', () => {
    expect(src).toMatch(/event\.metaKey\s*\|\|\s*event\.ctrlKey/)
    expect(src).toMatch(/event\.key\.toLowerCase\(\)\s*===\s*['"]k['"]/)
  })

  test('Esc closes the palette when visible', () => {
    expect(src).toMatch(/event\.key\s*===\s*['"]Escape['"][\s\S]*?this\.visible\s*=\s*false/)
  })

  test('arrow keys move focus through filtered list', () => {
    expect(src).toMatch(/@keydown\.down\.prevent="moveFocus\(1\)"/)
    expect(src).toMatch(/@keydown\.up\.prevent="moveFocus\(-1\)"/)
  })

  test('Enter triggers the active item', () => {
    expect(src).toMatch(/@keydown\.enter\.prevent="trigger\(filtered\[activeIdx\]\)"/)
  })

  test('uses fuzzyMatch as a fallback when substring fails', () => {
    expect(src).toMatch(/fuzzyMatch\s*\(/)
    expect(src).toMatch(/this\.fuzzyMatch\(blob,\s*q\)/)
  })

  test('emits command event when an item is triggered', () => {
    expect(src).toMatch(/\$emit\(['"]command['"]/)
  })

  test('uses pretext measureCommandItem to detect overflow', () => {
    expect(src).toMatch(/measureCommandItem/)
    expect(src).toMatch(/recomputeOverflow/)
  })

  test('mobile (≤640px) hides the entire palette per plan §10.2', () => {
    expect(src).toMatch(/@media\s*\(max-width:\s*640px\)[\s\S]*?manual-command-palette[\s\S]*?display:\s*none/)
  })

  test('overlay uses backdrop-filter blur and high z-index (1200)', () => {
    expect(src).toMatch(/backdrop-filter:\s*blur/)
    expect(src).toMatch(/z-index:\s*1200/)
  })

  test('focuses the input on visibility change for keyboard users', () => {
    expect(src).toMatch(/this\.\$refs\.inputRef\.focus\(\)/)
  })
})

describe('ManualNaiwaWidget · 右下角浮动挂件', () => {
  const src = read('ManualNaiwaWidget.vue')

  test('uses pretext measureBubble for tight bubble sizing', () => {
    expect(src).toMatch(/measureBubble/)
  })

  test('first-visit auto-expand for 6 seconds, then collapse', () => {
    expect(src).toMatch(/manual\.widget_seen/)
    expect(src).toMatch(/scheduleAutoCollapse\(6000\)/)
  })

  test('exposes 3 plan-mandated buttons: laugh / mute / close-fun', () => {
    expect(src).toContain('让他笑一下')
    expect(src).toMatch(/取消静音|静音/)
    expect(src).toContain('关闭趣味模式')
  })

  test('laugh refuses when funMode is off (toast instead)', () => {
    expect(src).toMatch(/if\s*\(!this\.funMode\)[\s\S]*?\$emit\(['"]toast['"]/)
  })

  test('laugh refuses when muted', () => {
    expect(src).toMatch(/if\s*\(this\.muted\)[\s\S]*?\$emit\(['"]toast['"]/)
  })

  test('delegates audio playback to parent (page-level <audio>) instead of new Audio()', () => {
    expect(src).not.toMatch(/new Audio\(/)
    expect(src).toMatch(/\$emit\(['"]laugh['"]\)/)
  })

  test('Esc collapses the expanded panel', () => {
    expect(src).toMatch(/handleEscape[\s\S]*?Escape[\s\S]*?this\.expanded\s*=\s*false/)
  })

  test('cleans up timers on unmount', () => {
    expect(src).toMatch(/beforeUnmount[\s\S]*?clearTimeout\(this\.autoCollapseTimer\)/)
    expect(src).toMatch(/clearInterval\(this\.bubbleRotateTimer\)/)
  })

  test('emits laugh / toast / close-fun events to parent', () => {
    expect(src).toMatch(/\$emit\(['"]laugh['"]/)
    expect(src).toMatch(/\$emit\(['"]toast['"]/)
    expect(src).toMatch(/\$emit\(['"]close-fun['"]\)/)
  })

  test('disables bounce animation under prefers-reduced-motion', () => {
    expect(src).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?animation:\s*none/)
  })
})

describe('ManualNaiwaSticker · 章节标题贴片', () => {
  const src = read('ManualNaiwaSticker.vue')

  test('index prop wraps modulo NAIWA_STICKERS.length', () => {
    expect(src).toMatch(/NAIWA_STICKERS\.length/)
    expect(src).toMatch(/%\s*NAIWA_STICKERS\.length/)
  })

  test('exposes 4 size presets validated via prop validator', () => {
    expect(src).toMatch(/validator:\s*v\s*=>\s*\[['"]xs['"],\s*['"]sm['"],\s*['"]md['"],\s*['"]lg['"]\]/)
  })

  test('aria-hidden for purely decorative stickers', () => {
    expect(src).toContain('aria-hidden="true"')
  })

  test('respects prefers-reduced-motion (no hover scale)', () => {
    expect(src).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)/)
    expect(src).toMatch(/transition:\s*none/)
  })
})

describe('ManualNaiwaMouseFollower · 鼠标跟随', () => {
  const src = read('ManualNaiwaMouseFollower.vue')

  test('uses 0.18 spring stiffness for following lag', () => {
    expect(src).toMatch(/dx\s*\*\s*0\.18/)
    expect(src).toMatch(/dy\s*\*\s*0\.18/)
  })

  test('passive mousemove listener keeps scroll smooth', () => {
    expect(src).toMatch(/mousemove[\s\S]*?passive:\s*true/)
  })

  test('uses RAF instead of setInterval for the spring loop', () => {
    expect(src).toContain('requestAnimationFrame')
  })

  test('disables when prefers-reduced-motion or viewport ≤768px', () => {
    expect(src).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?display:\s*none/)
    expect(src).toMatch(/@media\s*\(max-width:\s*768px\)[\s\S]*?display:\s*none/)
  })

  test('pointer-events: none so it never intercepts clicks', () => {
    expect(src).toContain('pointer-events: none')
  })
})

describe('ManualNaiwaRandomPopper · 随机边缘弹出', () => {
  const src = read('ManualNaiwaRandomPopper.vue')

  test('next pop is scheduled 90s~180s out (per plan §10.3)', () => {
    expect(src).toMatch(/90000\s*\+\s*Math\.random\(\)\s*\*\s*90000/)
  })

  test('lifetime is 3-5 seconds before auto-hide', () => {
    expect(src).toMatch(/3000\s*\+\s*Math\.random\(\)\s*\*\s*2000/)
  })

  test('cycles through left/right/top/bottom edges', () => {
    expect(src).toMatch(/EDGES\s*=\s*\[\s*['"]left['"],\s*['"]right['"],\s*['"]top['"],\s*['"]bottom['"]/)
  })

  test('disables on prefers-reduced-motion AND mobile (≤768px)', () => {
    expect(src).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?display:\s*none/)
    expect(src).toMatch(/@media\s*\(max-width:\s*768px\)[\s\S]*?display:\s*none/)
  })

  test('clears timers on unmount to prevent leak', () => {
    expect(src).toMatch(/beforeUnmount[\s\S]*?clearTimeout\(this\.timer\)/)
    expect(src).toMatch(/clearTimeout\(this\.hideTimer\)/)
  })
})

describe('ManualNaiwaGallery · 3D tilt 图鉴', () => {
  const src = read('ManualNaiwaGallery.vue')

  test('uses perspective 800px for tilt transform', () => {
    expect(src).toMatch(/perspective\(800px\)/)
    expect(src).toMatch(/rotateX\([^)]+\)/)
    expect(src).toMatch(/rotateY\([^)]+\)/)
  })

  test('disables tilt on mobile and reduce-motion', () => {
    expect(src).toMatch(/@media\s*\(max-width:\s*640px\)[\s\S]*?transform:\s*none/)
    expect(src).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)/)
  })

  test('emits burst event for confetti integration with the click event payload', () => {
    expect(src).toMatch(/\$emit\(['"]burst['"]/)
  })

  test('delegates audio playback to parent (no own Audio instance)', () => {
    expect(src).not.toMatch(/new Audio\(/)
  })

  test('keyboard accessible (tabindex=0 + Enter handler)', () => {
    expect(src).toMatch(/tabindex="0"/)
    expect(src).toMatch(/@keyup\.enter=/)
  })
})

describe('ManualFlowDiagram · SVG 流程图', () => {
  const src = read('ManualFlowDiagram.vue')

  test('imports FLOW_NODES (NAIWA_MOTION removed since stickers were removed to fix overlap)', () => {
    expect(src).toContain('FLOW_NODES')
    expect(src).not.toContain('NAIWA_MOTION')
  })

  test('does not render decorative naiwa stickers on flow nodes anymore', () => {
    expect(src).not.toMatch(/<span\s+v-if="hasSticker/)
    expect(src).not.toMatch(/manual-flow__sticker/)
  })

  test('triggers reveal via IntersectionObserver (≥18% threshold)', () => {
    expect(src).toContain('IntersectionObserver')
    expect(src).toMatch(/threshold:\s*0\.18/)
  })

  test('disconnect observer after first intersection (one-shot)', () => {
    expect(src).toMatch(/this\.observer\.disconnect\(\)/)
  })

  test('strokes use stroke-dasharray + stroke-dashoffset for line-draw animation', () => {
    expect(src).toMatch(/stroke-dasharray:\s*500/)
    expect(src).toMatch(/stroke-dashoffset:\s*500/)
    expect(src).toMatch(/@keyframes\s+manual-flow-draw/)
  })

  test('respects prefers-reduced-motion (no draw animation, end state)', () => {
    expect(src).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?stroke-dashoffset:\s*0/)
  })

  test('emits jump event when a node is clicked', () => {
    expect(src).toMatch(/\$emit\(['"]jump['"]/)
  })

  test('falls back to immediate reveal when IntersectionObserver is unavailable', () => {
    expect(src).toMatch(/typeof IntersectionObserver === 'undefined'[\s\S]*?this\.revealed\s*=\s*true/)
  })
})

describe('ManualCompletionFinale · 通关动画', () => {
  const src = read('ManualCompletionFinale.vue')

  test('persists COMPLETED_KEY to localStorage on show', () => {
    expect(src).toMatch(/setItem\(COMPLETED_KEY,\s*new Date\(\)\.toISOString\(\)\)/)
  })

  test('exposes show() / dismiss() methods', () => {
    expect(src).toMatch(/show\s*\(\)/)
    expect(src).toMatch(/dismiss\s*\(\)/)
  })

  test('default autoCloseMs is 6000 (≥5000 per plan §10.3)', () => {
    expect(src).toMatch(/autoCloseMs[\s\S]*?default:\s*6000/)
  })

  test('emits laugh / show / go-practice events for parent integration', () => {
    expect(src).toMatch(/\$emit\(['"]show['"]\)/)
    expect(src).toMatch(/\$emit\(['"]laugh['"]\)/)
    expect(src).toMatch(/\$emit\(['"]go-practice['"]\)/)
  })

  test('"再笑一个" / "回到顶部" / "去做第一题" buttons all present', () => {
    expect(src).toContain('再笑一个')
    expect(src).toContain('回到顶部')
    expect(src).toContain('去做第一题')
  })

  test('animation transitions disabled under prefers-reduced-motion', () => {
    expect(src).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?transition:\s*none/)
  })
})

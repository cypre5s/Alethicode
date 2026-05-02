/* eslint-env jest */

/**
 * `ManualPage.vue` 主容器源契约。
 *
 * 验证集成点：左侧目录、9 个 section、各种炫技层 (confetti / palette / widget /
 * follower / popper / finale)、双门控降级 (funMode + reduce-motion)、
 * theme 切换 (ViewTransitions API + localStorage 持久化)、IntersectionObserver
 * 联动、阅读完成监听 (≥30s + COMPLETED_KEY)。
 */

const fs = require('fs')
const path = require('path')

const SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../src/pages/oj/views/manual/ManualPage.vue'),
  'utf8'
)

describe('ManualPage · component imports', () => {
  test('imports the 11 utility components (after dark-mode removal swap to typewriter)', () => {
    const required = [
      'ManualReadingProgress', 'ManualBackToTop', 'ManualTypewriter',
      'ManualSearchBar', 'ManualSidebar', 'ManualConfettiCanvas',
      'ManualCommandPalette', 'ManualNaiwaWidget', 'ManualNaiwaMouseFollower',
      'ManualNaiwaRandomPopper', 'ManualCompletionFinale'
    ]
    for (const name of required) {
      expect(SOURCE).toMatch(new RegExp(`import\\s+${name}\\s+from`))
    }
  })

  test('does NOT import ManualThemeToggle (dark mode removed)', () => {
    expect(SOURCE).not.toMatch(/ManualThemeToggle/)
  })

  test('imports all 9 section subcomponents', () => {
    const sections = [
      'SectionWelcome', 'SectionFlow', 'SectionTour', 'SectionCore',
      'SectionAI', 'SectionFAQ', 'SectionTips', 'SectionGallery', 'SectionFeedback'
    ]
    for (const name of sections) {
      expect(SOURCE).toMatch(new RegExp(`import\\s+${name}\\s+from\\s+['"]\\.\\/sections\\/${name}\\.vue['"]`))
    }
  })

  test('imports the 4 plan-required constants from manualContent', () => {
    expect(SOURCE).toMatch(/FUN_MODE_KEY[\s,]/)
    expect(SOURCE).toMatch(/NAIWA_HERO[\s,]/)
    expect(SOURCE).toMatch(/NAIWA_LAUGH_AUDIO[\s,]/)
    expect(SOURCE).toMatch(/COMPLETED_KEY[\s,}]/)
  })

  test('imports ManualTypewriter for the hero subtitle character-by-character effect', () => {
    expect(SOURCE).toMatch(/import\s+ManualTypewriter\s+from\s+['"]\.\/ManualTypewriter\.vue['"]/)
    expect(SOURCE).toMatch(/<ManualTypewriter\s/)
  })
})

describe('ManualPage · template structure', () => {
  test('renders the reading progress bar at the top', () => {
    expect(SOURCE).toMatch(/<ManualReadingProgress[^/]*\/?>/)
  })

  test('renders hero with kicker / title / subtitle / dual CTAs', () => {
    expect(SOURCE).toContain('Alethicode 新手指南')
    expect(SOURCE).toContain('hero-title-grad')
    expect(SOURCE).toMatch(/立即开始/)
    expect(SOURCE).toMatch(/先看页面导览/)
  })

  test('hero exposes the two mirror switches: search / fun-toggle (dark mode removed)', () => {
    expect(SOURCE).toMatch(/<ManualSearchBar[^>]*@jump=/)
    expect(SOURCE).not.toMatch(/<ManualThemeToggle/)
    expect(SOURCE).toContain('manual-page__fun-toggle')
    expect(SOURCE).toMatch(/关闭趣味模式|打开趣味模式/)
  })

  test('layout uses sticky sidebar + main content + back-to-top + confetti layer', () => {
    expect(SOURCE).toMatch(/<ManualSidebar[\s\S]*?:active-id=/)
    expect(SOURCE).toMatch(/<ManualBackToTop[^/]*\/?>/)
    expect(SOURCE).toMatch(/<ManualConfettiCanvas[\s\S]*?ref="confettiRef"/)
  })

  test('command palette is mounted with @command handler', () => {
    expect(SOURCE).toMatch(/<ManualCommandPalette[\s\S]*?ref="paletteRef"[\s\S]*?@command="onCommand"/)
  })

  test('toast transition is wired up', () => {
    expect(SOURCE).toMatch(/<transition[^>]*name="manual-toast"/)
    expect(SOURCE).toMatch(/manual-toast"/)
  })

  test('all guide images are delegated to a click-to-preview lightbox', () => {
    expect(SOURCE).toMatch(/@click\.capture="openImagePreviewFromEvent"/)
    expect(SOURCE).toMatch(/@keydown\.capture="openImagePreviewFromKeyboard"/)
    expect(SOURCE).toMatch(/manual-image-preview/)
    expect(SOURCE).toMatch(/imagePreview/)
    expect(SOURCE).toMatch(/querySelectorAll\(['"]img['"]\)/)
    expect(SOURCE).toMatch(/data-manual-previewable/)
    expect(SOURCE).toMatch(/MutationObserver/)
  })
})

describe('ManualPage · 双门控降级', () => {
  test('widget / mouse follower / random popper guarded by funMode AND !widgetHidden / !reduceMotion', () => {
    expect(SOURCE).toMatch(/<ManualNaiwaWidget[\s\S]*?v-if="funMode && !widgetHidden"/)
    expect(SOURCE).toMatch(/<ManualNaiwaMouseFollower[\s\S]*?v-if="funMode && !reduceMotion"/)
    expect(SOURCE).toMatch(/<ManualNaiwaRandomPopper[\s\S]*?v-if="funMode && !reduceMotion"/)
  })

  test('SectionGallery is gated by v-if="funMode" so closing fun-mode hides the whole section', () => {
    expect(SOURCE).toMatch(/<SectionGallery[\s\S]*?v-if="funMode"/)
  })

  test('reduceMotion is detected from prefers-reduced-motion media query', () => {
    expect(SOURCE).toContain("'(prefers-reduced-motion: reduce)'")
  })

  test('funMode default is true when localStorage is unset', () => {
    // localStorage saved === 'off' is the only path that flips it false
    expect(SOURCE).toMatch(/saved\s*===\s*'off'/)
    expect(SOURCE).toMatch(/this\.funMode\s*=\s*true/)
  })

  test('funMode change persists to localStorage with FUN_MODE_KEY', () => {
    expect(SOURCE).toMatch(/setItem\(FUN_MODE_KEY,\s*val\s*\?\s*'on'\s*:\s*'off'\)/)
  })
})

describe('ManualPage · IntersectionObserver 集成', () => {
  test('installObserver targets section[id] elements within mainRef', () => {
    expect(SOURCE).toMatch(/installObserver\s*\(/)
    expect(SOURCE).toMatch(/section\[id\]/)
    expect(SOURCE).toMatch(/IntersectionObserver/)
  })

  test('observer rootMargin tunes for "active" detection mid-viewport', () => {
    expect(SOURCE).toContain("rootMargin: '-30% 0px -55% 0px'")
  })

  test('finale observer waits ≥30s before triggering when COMPLETED_KEY exists', () => {
    expect(SOURCE).toMatch(/elapsed\s*<\s*30000\s*&&\s*completedAt/)
    expect(SOURCE).toMatch(/COMPLETED_KEY/)
  })

  test('finale observer fires confetti burst from bottom center on show', () => {
    expect(SOURCE).toMatch(/confettiRef[\s\S]*?\.burst/)
    expect(SOURCE).toMatch(/window\.innerHeight\s*-\s*40/)
  })
})

describe('ManualPage · 命令面板 onCommand 派发', () => {
  test('handles 5 command kinds (theme removed)', () => {
    for (const kind of ['goto', 'laugh', 'fun', 'widget', 'top']) {
      expect(SOURCE).toMatch(new RegExp(`item\\.kind\\s*===\\s*['"]${kind}['"]`))
    }
  })

  test('does NOT handle theme kind anymore', () => {
    expect(SOURCE).not.toMatch(/item\.kind\s*===\s*['"]theme['"]/)
    expect(SOURCE).not.toMatch(/data-manual-theme/)
  })

  test('top command triggers smooth scroll to (0,0)', () => {
    expect(SOURCE).toMatch(/window\.scrollTo\([\s\S]*?top:\s*0[\s\S]*?behavior:\s*['"]smooth['"]/)
  })
})

describe('ManualPage · 笑声播放策略（页面级 <audio>）', () => {
  test('uses page-level <audio> element instead of new Audio() per click', () => {
    expect(SOURCE).toMatch(/<audio[\s\S]*?ref="laughAudioRef"[\s\S]*?preload="auto"/)
    expect(SOURCE).toMatch(/this\.\$refs\.laughAudioRef/)
    expect(SOURCE).not.toMatch(/new Audio\(NAIWA_LAUGH_AUDIO\)/)
  })

  test('playLaugh refuses when funMode is off', () => {
    expect(SOURCE).toMatch(/playLaugh\s*\([\s\S]*?if\s*\(!this\.funMode\)\s*return/)
  })

  test('boosts volume to 0.95 (above 0.5) so users can actually hear the laugh', () => {
    expect(SOURCE).toMatch(/audio\.volume\s*=\s*0\.95/)
  })

  test('toast falls back to a friendly message when audio playback is rejected', () => {
    expect(SOURCE).toMatch(/浏览器拦截了音频/)
  })

  test('explicitly unmutes before play so a previous mute state cannot block sound', () => {
    expect(SOURCE).toMatch(/audio\.muted\s*=\s*false/)
  })
})

describe('ManualPage · 不再渲染暗黑模式相关 CSS', () => {
  test('no :global data-manual-theme dark scope in styles', () => {
    expect(SOURCE).not.toMatch(/data-manual-theme='dark'/)
    expect(SOURCE).not.toMatch(/color-scheme:\s*dark/)
  })

  test('no ViewTransitions animation block (removed with theme toggle)', () => {
    expect(SOURCE).not.toMatch(/::view-transition-old/)
    expect(SOURCE).not.toMatch(/document\.startViewTransition/)
  })
})

describe('ManualPage · 响应式断点', () => {
  test('hides sidebar on screens narrower than 1024px', () => {
    expect(SOURCE).toMatch(/@media\s*\(max-width:\s*1023px\)[\s\S]*?display:\s*none/)
  })

  test('compresses hero into single column at ≤768px', () => {
    expect(SOURCE).toMatch(/@media\s*\(max-width:\s*768px\)[\s\S]*?grid-template-columns:\s*1fr/)
  })

  test('disables hero mascot float animation under prefers-reduced-motion', () => {
    expect(SOURCE).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?manual-page__hero-mascot[\s\S]*?animation:\s*none/)
  })
})

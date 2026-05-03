<template>
  <div
    class="manual-page"
    :class="{ 'is-fun': funMode }"
    @click.capture="openImagePreviewFromEvent"
    @keydown.capture="openImagePreviewFromKeyboard"
  >
    <ManualReadingProgress />

    <header class="manual-page__hero">
      <div class="manual-page__hero-left">
        <span class="manual-page__hero-kicker">Alethicode 使用指南</span>
        <h1 class="manual-page__hero-title">
          <template v-if="funMode">
            你好，<ManualRoughAnnotation type="underline" color="#ec4899" :stroke-width="3"><span class="hero-title-grad">欢迎来到 Alethicode</span></ManualRoughAnnotation>
          </template>
          <template v-else>
            和 AI 一起，把每道题学透
          </template>
        </h1>
        <p class="manual-page__hero-sub">
          <ManualTypewriter v-if="funMode" :text="heroSubtitle" :speed="42" :start-delay="320" />
          <span v-else>{{ heroSubtitle }}</span>
        </p>

        <div class="manual-page__hero-cta">
          <button type="button" class="btn primary" @click="jumpTo('welcome')">立刻开始</button>
          <button type="button" class="btn ghost" @click="jumpTo('ai')">了解 AI 导学助手</button>
        </div>

        <ul class="manual-page__hero-caps">
          <li v-for="cap in capabilities" :key="cap.id">
            <button type="button" class="manual-hero-cap" @click="jumpTo(cap.target)">
              <span class="manual-hero-cap__label">{{ cap.label }}</span>
              <span class="manual-hero-cap__sep" aria-hidden="true">·</span>
              <span class="manual-hero-cap__desc">{{ cap.desc }}</span>
            </button>
          </li>
        </ul>
      </div>

      <div v-if="funMode" class="manual-page__hero-right">
        <img :src="heroSrc" alt="Alethicode 吉祥物" class="manual-page__hero-mascot">
      </div>

      <div class="manual-page__hero-tools">
        <ManualSearchBar @jump="jumpTo" />
        <button
          type="button"
          class="manual-page__fun-toggle"
          :class="{ 'is-off': !funMode }"
          @click="setFun(!funMode)"
        >
          <span aria-hidden="true">{{ funMode ? '🐸' : '·' }}</span>
          {{ funMode ? '关闭趣味模式' : '打开趣味模式' }}
        </button>
      </div>
    </header>

    <div class="manual-page__layout">
      <aside class="manual-page__sidebar">
        <ManualSidebar :active-id="activeId" :fun-mode="funMode" @jump="jumpTo" />
      </aside>

      <main class="manual-page__main" ref="mainRef">
        <div v-if="funMode" class="manual-page__hero-stats">
          <ManualFlowingText />
          <ManualStatsCounter />
        </div>
        <SectionWelcome ref="sec_welcome" @jump="jumpTo" />
        <SectionAI ref="sec_ai" @jump="jumpTo" />
        <SectionContext ref="sec_context" @jump="jumpTo" />
        <SectionCoursewareQa ref="sec_qa" @jump="jumpTo" />
        <SectionFlow ref="sec_flow" @jump="jumpTo" />
        <SectionTips ref="sec_tips" @jump="jumpTo" />
        <SectionFAQ ref="sec_faq" @jump="jumpTo" />
        <SectionTour ref="sec_tour" @jump="jumpTo" />
        <SectionGallery v-if="funMode" ref="sec_gallery" :fun-mode="funMode" @burst="onGalleryBurst" />
        <SectionFeedback ref="sec_feedback" @jump="jumpTo" />
      </main>
    </div>

    <ManualBackToTop />

    <ManualConfettiCanvas ref="confettiRef" />

    <ManualCommandPalette ref="paletteRef" @command="onCommand" />

    <ManualNaiwaWidget
      v-if="funMode && !widgetHidden"
      :fun-mode="funMode"
      @laugh="onWidgetLaugh"
      @toast="showToast"
      @close-fun="setFun(false)"
    />

    <ManualNaiwaMouseFollower v-if="funMode && !reduceMotion" />
    <ManualNaiwaRandomPopper v-if="funMode && !reduceMotion" />

    <ManualCompletionFinale
      ref="finaleRef"
      @show="onFinaleShown"
      @laugh="onFinaleLaugh"
      @go-practice="goPractice"
    />

    <audio
      ref="laughAudioRef"
      :src="audioSrc"
      preload="auto"
      class="manual-page__audio"
      aria-hidden="true"
    ></audio>

    <transition name="manual-toast">
      <div v-if="toast" class="manual-toast">{{ toast }}</div>
    </transition>

    <transition name="manual-image-preview">
      <div
        v-if="imagePreview.visible"
        class="manual-image-preview"
        role="dialog"
        aria-modal="true"
        :aria-label="imagePreview.alt || '图片预览'"
        @click.self="closeImagePreview"
      >
        <button
          type="button"
          class="manual-image-preview__close"
          aria-label="关闭图片预览"
          @click="closeImagePreview"
        >
          ×
        </button>
        <img
          class="manual-image-preview__img"
          :src="imagePreview.src"
          :alt="imagePreview.alt"
        >
      </div>
    </transition>
  </div>
</template>

<script>
import ManualReadingProgress from './ManualReadingProgress.vue'
import ManualBackToTop from './ManualBackToTop.vue'
import ManualSearchBar from './ManualSearchBar.vue'
import ManualSidebar from './ManualSidebar.vue'
import ManualConfettiCanvas from './ManualConfettiCanvas.vue'
import ManualCommandPalette from './ManualCommandPalette.vue'
import ManualNaiwaWidget from './ManualNaiwaWidget.vue'
import ManualNaiwaMouseFollower from './ManualNaiwaMouseFollower.vue'
import ManualNaiwaRandomPopper from './ManualNaiwaRandomPopper.vue'
import ManualCompletionFinale from './ManualCompletionFinale.vue'
import ManualTypewriter from './ManualTypewriter.vue'
import ManualFlowingText from './ManualFlowingText.vue'
import ManualStatsCounter from './ManualStatsCounter.vue'
import ManualRoughAnnotation from './ManualRoughAnnotation.vue'

import SectionWelcome from './sections/SectionWelcome.vue'
import SectionAI from './sections/SectionAI.vue'
import SectionContext from './sections/SectionContext.vue'
import SectionCoursewareQa from './sections/SectionCoursewareQa.vue'
import SectionFlow from './sections/SectionFlow.vue'
import SectionTips from './sections/SectionTips.vue'
import SectionFAQ from './sections/SectionFAQ.vue'
import SectionTour from './sections/SectionTour.vue'
import SectionGallery from './sections/SectionGallery.vue'
import SectionFeedback from './sections/SectionFeedback.vue'

import {
  FUN_MODE_KEY,
  NAIWA_HERO,
  NAIWA_LAUGH_AUDIO,
  COMPLETED_KEY,
  HERO_CAPABILITIES
} from './manualContent.js'

export default {
  name: 'ManualPage',
  components: {
    ManualReadingProgress,
    ManualBackToTop,
    ManualSearchBar,
    ManualSidebar,
    ManualConfettiCanvas,
    ManualCommandPalette,
    ManualNaiwaWidget,
    ManualNaiwaMouseFollower,
    ManualNaiwaRandomPopper,
    ManualCompletionFinale,
    ManualTypewriter,
    ManualFlowingText,
    ManualStatsCounter,
    ManualRoughAnnotation,
    SectionWelcome,
    SectionAI,
    SectionContext,
    SectionCoursewareQa,
    SectionFlow,
    SectionTips,
    SectionFAQ,
    SectionTour,
    SectionGallery,
    SectionFeedback
  },
  data () {
    return {
      funMode: false,
      widgetHidden: false,
      reduceMotion: false,
      activeId: 'welcome',
      observer: null,
      toast: '',
      toastTimer: null,
      imagePreview: { visible: false, src: '', alt: '' },
      previewObserver: null,
      visitStart: Date.now(),
      finaleShown: false,
      capabilities: HERO_CAPABILITIES,
      heroSubtitle: '从读题、思考、编码到复盘，完成一次完整学习闭环——AI 不替你写代码，是和你一起想清楚下一步。'
    }
  },
  computed: {
    heroSrc () { return NAIWA_HERO },
    audioSrc () { return NAIWA_LAUGH_AUDIO }
  },
  mounted () {
    const saved = window.localStorage.getItem(FUN_MODE_KEY)
    if (saved === 'on') this.funMode = true
    else this.funMode = false
    this.reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches

    this.$nextTick(this.installObserver)
    this.installFinaleObserver()
    this.$nextTick(this.installImagePreviewTargets)
    window.addEventListener('keydown', this.handlePreviewEscape)
  },
  beforeUnmount () {
    if (this.observer) this.observer.disconnect()
    if (this.finaleObserver) this.finaleObserver.disconnect()
    if (this.previewObserver) this.previewObserver.disconnect()
    if (this.toastTimer) clearTimeout(this.toastTimer)
    window.removeEventListener('keydown', this.handlePreviewEscape)
  },
  watch: {
    funMode (val) {
      try { window.localStorage.setItem(FUN_MODE_KEY, val ? 'on' : 'off') } catch (err) { console.warn('[ManualPage] persist fun_mode failed', err) }
      this.$nextTick(() => {
        this.installObserver()
        this.installFinaleObserver()
        this.installImagePreviewTargets()
      })
    }
  },
  methods: {
    installObserver () {
      if (this.observer) this.observer.disconnect()
      if (typeof IntersectionObserver === 'undefined') return
      const root = this.$refs.mainRef
      if (!root) return
      const sectionEls = root.querySelectorAll('section[id]')
      this.observer = new IntersectionObserver(entries => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            const id = entry.target.id
            if (id) this.activeId = id
          }
        })
      }, { rootMargin: '-30% 0px -55% 0px', threshold: 0 })
      sectionEls.forEach(el => this.observer.observe(el))
    },
    installFinaleObserver () {
      if (this.finaleObserver) this.finaleObserver.disconnect()
      if (typeof IntersectionObserver === 'undefined') return
      const tail = document.getElementById('feedback')
      if (!tail) return
      const completedAt = window.localStorage.getItem(COMPLETED_KEY)
      this.finaleObserver = new IntersectionObserver(entries => {
        entries.forEach(entry => {
          if (!entry.isIntersecting) return
          const elapsed = Date.now() - this.visitStart
          if (elapsed < 30000 && completedAt) {
            return
          }
          if (this.finaleShown) return
          if (this.$refs.finaleRef) {
            this.finaleShown = true
            this.$refs.finaleRef.show()
            this.$nextTick(() => {
              if (this.$refs.confettiRef) {
                const x = window.innerWidth / 2
                const y = window.innerHeight - 40
                this.$refs.confettiRef.burst({ x, y, count: 120, spread: 220 })
              }
            })
          }
        })
      }, { threshold: 0.3 })
      this.finaleObserver.observe(tail)
    },
    installImagePreviewTargets () {
      if (this.previewObserver) this.previewObserver.disconnect()
      const root = this.$el
      if (!root) return
      const markImages = () => {
        root.querySelectorAll('img').forEach(img => {
          img.setAttribute('data-manual-previewable', 'true')
          img.setAttribute('tabindex', img.getAttribute('tabindex') || '0')
          img.setAttribute('role', img.getAttribute('role') || 'button')
        })
      }
      markImages()
      if (typeof MutationObserver === 'undefined') return
      this.previewObserver = new MutationObserver(markImages)
      this.previewObserver.observe(root, { childList: true, subtree: true })
    },
    openImagePreviewFromEvent (event) {
      const target = event.target
      if (!target || target.tagName !== 'IMG' || target.dataset.manualPreviewable !== 'true') return
      this.openImagePreview(target)
    },
    openImagePreviewFromKeyboard (event) {
      if (event.key !== 'Enter' && event.key !== ' ') return
      const target = event.target
      if (!target || target.tagName !== 'IMG' || target.dataset.manualPreviewable !== 'true') return
      event.preventDefault()
      this.openImagePreview(target)
    },
    openImagePreview (img) {
      const src = img.currentSrc || img.src
      if (!src) return
      this.imagePreview = {
        visible: true,
        src,
        alt: img.alt || '图片预览'
      }
    },
    closeImagePreview () {
      this.imagePreview = { visible: false, src: '', alt: '' }
    },
    jumpTo (id) {
      const target = document.getElementById(id)
      if (!target) return
      target.scrollIntoView({ behavior: this.reduceMotion ? 'auto' : 'smooth', block: 'start' })
      this.activeId = id
    },
    setFun (val) {
      this.funMode = val
      this.showToast(val ? '已打开趣味模式' : '已关闭趣味模式')
    },
    onCommand (item) {
      if (item.kind === 'goto') return this.jumpTo(item.payload.section)
      if (item.kind === 'laugh') return this.playLaugh()
      if (item.kind === 'fun') return this.setFun(!this.funMode)
      if (item.kind === 'widget') {
        this.widgetHidden = !this.widgetHidden
        this.showToast(this.widgetHidden ? '已隐藏挂件' : '已显示挂件')
        return
      }
      if (item.kind === 'top') return window.scrollTo({ top: 0, behavior: 'smooth' })
    },
    handlePreviewEscape (event) {
      if (event.key === 'Escape' && this.imagePreview.visible) {
        this.closeImagePreview()
      }
    },
    onWidgetLaugh (event) {
      this.playLaugh()
      const x = event && event.clientX != null ? event.clientX : window.innerWidth - 60
      const y = event && event.clientY != null ? event.clientY : window.innerHeight - 60
      if (this.$refs.confettiRef) this.$refs.confettiRef.burst({ x, y, count: 40, spread: 80 })
    },
    onGalleryBurst (payload) {
      const ev = payload && payload.event
      const x = ev && ev.clientX != null ? ev.clientX : window.innerWidth / 2
      const y = ev && ev.clientY != null ? ev.clientY : window.innerHeight / 2
      if (this.$refs.confettiRef) this.$refs.confettiRef.burst({ x, y, count: 30, spread: 100 })
      this.playLaugh()
    },
    onFinaleShown () {
      this.showToast('看完啦～')
    },
    onFinaleLaugh () {
      this.playLaugh()
      if (this.$refs.confettiRef) {
        const x = window.innerWidth / 2
        const y = window.innerHeight / 2
        this.$refs.confettiRef.burst({ x, y, count: 80, spread: 200 })
      }
    },
    goPractice () {
      this.$router.push('/problem').catch(() => {})
    },
    /**
     * 播放奶蛙笑声。统一使用页面级 <audio> 元素而不是临时 new Audio()，
     * 避免每次点击重新发起请求；preload="auto" 让首次响应秒开。
     */
    playLaugh () {
      if (!this.funMode) return
      const audio = this.$refs.laughAudioRef
      if (!audio) {
        this.showToast('音效未配置')
        return
      }
      try {
        audio.volume = 0.95
        audio.muted = false
        audio.currentTime = 0
        const p = audio.play()
        if (p && typeof p.catch === 'function') {
          p.catch(err => {
            console.warn('[ManualPage] audio play rejected:', err)
            this.showToast('浏览器拦截了音频，再点一下试试')
          })
        }
      } catch (err) {
        console.warn('[ManualPage] playLaugh failed', err)
        this.showToast('音效未配置')
      }
    },
    showToast (msg) {
      this.toast = msg
      if (this.toastTimer) clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => { this.toast = '' }, 2400)
    }
  }
}
</script>

<style lang="less" scoped>
.manual-page {
  --manual-content-max: 920px;
  background: var(--bg-base);
  min-height: 100vh;
  padding: 88px 0 120px;
  font-family: var(--font-sans);
  color: var(--text-primary);
}

/* 趣味模式下恢复温暖渐变背景。 */
.manual-page.is-fun {
  background: linear-gradient(180deg, #f5f3ff 0%, var(--bg-base) 320px);
}

.manual-page__hero {
  max-width: 1200px;
  margin: 0 auto 40px;
  padding: 28px 36px 22px;
  display: grid;
  grid-template-columns: 1fr;
  grid-template-areas:
    'left'
    'tools';
  gap: 16px;
  align-items: stretch;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  position: relative;
  overflow: hidden;
}

.manual-page.is-fun .manual-page__hero {
  grid-template-columns: 1fr auto;
  grid-template-areas:
    'left right'
    'tools tools';
  align-items: center;
  background: var(--warm-bg-hero);
  border-color: var(--border-warm);
  padding: 24px 24px 20px;
}

.manual-page__hero-left { grid-area: left; }
.manual-page__hero-right { grid-area: right; display: flex; align-items: center; justify-content: center; }
.manual-page__hero-tools {
  grid-area: tools;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.manual-page.is-fun .manual-page__hero-tools {
  border-top: 1px dashed var(--border-warm);
}

.manual-page__hero-kicker {
  display: inline-block;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 1.6px;
  color: var(--text-disabled);
  text-transform: uppercase;
  font-weight: 600;
}

.manual-page.is-fun .manual-page__hero-kicker {
  color: var(--warm-primary-strong);
  font-weight: 700;
}

.manual-page__hero-title {
  margin: 6px 0 8px;
  font-size: 32px;
  font-weight: 800;
  line-height: 1.2;
  text-wrap: balance;
  color: var(--text-strong, var(--text-primary));
  letter-spacing: -0.6px;
  max-width: 22ch;
}

.manual-page.is-fun .manual-page__hero-title {
  font-size: 36px;
  letter-spacing: -1px;
  max-width: 18ch;
}

.hero-title-grad {
  background: linear-gradient(135deg, #6366f1, #ec4899);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.manual-page__hero-sub {
  margin: 0 0 16px;
  font-size: 15px;
  color: var(--text-secondary);
  line-height: 1.65;
  text-wrap: pretty;
  max-width: 60ch;
  min-height: 1.65em;
}

.manual-page__hero-caps {
  list-style: none;
  margin: 14px 0 0;
  padding: 12px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  border-top: 1px dashed var(--border-color);

  li { margin: 0; padding: 0; }
}

.manual-hero-cap {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 4px 10px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-pill);
  cursor: pointer;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--text-secondary);
  transition: border-color 0.18s ease, color 0.18s ease, background 0.18s ease;

  &:hover, &:focus-visible {
    border-color: var(--text-primary);
    color: var(--text-primary);
    background: var(--bg-panel);
    outline: none;
  }
}

.manual-hero-cap__label {
  font-weight: 600;
  color: var(--text-primary);
}

.manual-hero-cap__sep {
  color: var(--text-disabled);
  font-family: var(--font-mono);
}

.manual-hero-cap__desc {
  color: var(--text-secondary);
}

.manual-page__hero-cta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;

  .btn {
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    color: var(--text-primary);
    border-radius: var(--radius-md);
    padding: 8px 18px;
    font-size: 13.5px;
    cursor: pointer;
    font-weight: 600;
    transition: all 0.18s ease;

    &:hover {
      border-color: var(--text-disabled);
    }

    &.primary {
      background: var(--text-primary);
      color: var(--bg-card);
      border-color: var(--text-primary);
      &:hover { opacity: 0.85; }
    }
  }
}

.manual-page.is-fun .manual-page__hero-cta .btn {
  border-radius: var(--radius-pill);

  &.primary {
    background: var(--warm-grad-primary);
    color: #fff;
    border-color: transparent;
    box-shadow: var(--shadow-warm);
    &:hover { transform: translateY(-1px); color: #fff; border-color: transparent; opacity: 1; }
  }

  &:not(.primary):hover {
    color: var(--primary-color);
    border-color: var(--primary-color);
  }
}

.manual-page__hero-mascot {
  width: 140px;
  height: auto;
  filter: drop-shadow(0 10px 24px rgba(99, 102, 241, 0.18));
  animation: hero-float 4.5s ease-in-out infinite;
}

@keyframes hero-float {
  0%, 100% { transform: translateY(0) rotate(-1deg); }
  50% { transform: translateY(-6px) rotate(1deg); }
}

.manual-page__fun-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover, &:focus-visible {
    color: var(--text-primary);
    border-color: var(--text-disabled);
    outline: none;
  }

  &.is-off {
    background: var(--bg-panel);
    color: var(--text-disabled);
  }
}

.manual-page.is-fun .manual-page__fun-toggle {
  border-radius: var(--radius-pill);
  &:hover, &:focus-visible {
    color: var(--primary-color);
    border-color: var(--primary-color);
  }
}

.manual-page__layout {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 32px;
  align-items: flex-start;
}

.manual-page__sidebar {
  position: sticky;
  top: 96px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  align-self: flex-start;
}

.manual-page__main {
  max-width: var(--manual-content-max);
  margin: 0 auto;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 56px;
}

.manual-page__hero-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.manual-page__audio {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}

:deep(img[data-manual-previewable='true']) {
  cursor: zoom-in;
}

/* 默认模式下隐藏所有 sticker；funMode 下恢复。 */
:deep(.manual-section__head .manual-naiwa-sticker) {
  display: none;
}

.manual-page.is-fun :deep(.manual-section__head .manual-naiwa-sticker) {
  display: inline-flex;
}

.manual-toast {
  position: fixed;
  left: 50%;
  bottom: 32px;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.92);
  color: #fff;
  font-size: 13px;
  padding: 9px 18px;
  border-radius: var(--radius-pill);
  z-index: 1300;
  box-shadow: var(--shadow-md);
}

.manual-image-preview {
  position: fixed;
  inset: 0;
  z-index: 1400;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: rgba(15, 23, 42, 0.82);
  backdrop-filter: blur(10px);
}

.manual-image-preview__close {
  position: fixed;
  top: 18px;
  right: 22px;
  width: 44px;
  height: 44px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
}

.manual-image-preview__img {
  max-width: min(1120px, 94vw);
  max-height: 88vh;
  object-fit: contain;
  border-radius: var(--radius-md);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.36);
  cursor: zoom-out;
}

.manual-image-preview-enter-active,
.manual-image-preview-leave-active {
  transition: opacity 180ms ease;
}

.manual-image-preview-enter-from,
.manual-image-preview-leave-to {
  opacity: 0;
}

.manual-toast-enter-active,
.manual-toast-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}
.manual-toast-enter-from,
.manual-toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px);
}

@media (prefers-reduced-motion: reduce) {
  .manual-page__hero-mascot { animation: none; }
}

@media (max-width: 1023px) {
  .manual-page__layout {
    grid-template-columns: 1fr;
    padding: 0 20px;
  }
  .manual-page__sidebar { display: none; }
}

@media (max-width: 768px) {
  .manual-page.is-fun .manual-page__hero {
    grid-template-columns: 1fr;
    grid-template-areas:
      'left'
      'right'
      'tools';
    padding: 22px 18px 18px;
  }
  .manual-page__hero { padding: 22px 18px 18px; }
  .manual-page__hero-mascot { width: 120px; }
  .manual-page__hero-title { font-size: 26px; }
  .manual-page.is-fun .manual-page__hero-title { font-size: 28px; }
  .manual-page__hero-sub { font-size: 14px; }
  .manual-page__hero-caps { gap: 6px; }
}
</style>

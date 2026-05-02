<template>
  <transition name="manual-finale-fade">
    <div v-if="visible" class="manual-finale" role="dialog" aria-modal="true" aria-label="阅读完成">
      <div class="manual-finale__panel">
        <img class="manual-finale__hero" :src="celebrate" alt="撒花的奶蛙">
        <h3>你看完啦！</h3>
        <p>预计完成时长 <strong>{{ minutes }}</strong> 分钟，恭喜过关。</p>
        <div class="manual-finale__actions">
          <button type="button" class="btn primary" @click="$emit('laugh')">再笑一个</button>
          <button type="button" class="btn" @click="goTop">回到顶部</button>
          <button type="button" class="btn" @click="goPractice">去做第一题</button>
        </div>
        <button class="manual-finale__close" type="button" aria-label="关闭" @click="dismiss">×</button>
      </div>
    </div>
  </transition>
</template>

<script>
import { COMPLETED_KEY, NAIWA_MOTION } from './manualContent.js'

export default {
  name: 'ManualCompletionFinale',
  props: {
    autoCloseMs: { type: Number, default: 6000 }
  },
  data () {
    return {
      visible: false,
      mountedAt: Date.now(),
      timer: null
    }
  },
  computed: {
    celebrate () { return NAIWA_MOTION.celebrate },
    minutes () {
      const ms = Date.now() - this.mountedAt
      return Math.max(1, Math.round(ms / 60000))
    }
  },
  methods: {
    show () {
      if (this.visible) return
      this.visible = true
      this.$emit('show')
      try { window.localStorage.setItem(COMPLETED_KEY, new Date().toISOString()) } catch (err) { console.warn('[ManualCompletionFinale] persist failed', err) }
      if (this.autoCloseMs > 0) {
        this.timer = setTimeout(this.dismiss, this.autoCloseMs)
      }
    },
    dismiss () {
      this.visible = false
      if (this.timer) clearTimeout(this.timer)
      this.timer = null
    },
    goTop () {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      this.dismiss()
    },
    goPractice () {
      this.$emit('go-practice')
      this.dismiss()
    }
  },
  beforeUnmount () {
    if (this.timer) clearTimeout(this.timer)
  }
}
</script>

<style lang="less" scoped>
.manual-finale {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.manual-finale__panel {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 28px 32px 24px;
  text-align: center;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-default);
  position: relative;
  width: min(420px, 92vw);

  h3 {
    margin: 14px 0 6px;
    font-size: 22px;
    color: var(--text-primary);
  }
  p {
    color: var(--text-secondary);
    margin: 0 0 20px;
    font-size: 14px;
    strong { color: var(--primary-color); font-weight: 700; }
  }
}

.manual-finale__hero {
  width: 132px;
  height: 132px;
  object-fit: contain;
  margin: 0 auto;
  display: block;
  filter: drop-shadow(0 12px 28px rgba(99, 102, 241, 0.25));
}

.manual-finale__actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;

  .btn {
    border: 1px solid var(--border-default);
    background: var(--bg-card);
    color: var(--text-secondary);
    border-radius: var(--radius-pill);
    padding: 8px 16px;
    font-size: 13px;
    cursor: pointer;
    font-weight: 500;
    transition: all 0.18s ease;

    &:hover {
      color: var(--primary-color);
      border-color: var(--primary-color);
    }

    &.primary {
      background: var(--warm-grad-primary);
      color: #fff;
      border-color: transparent;
    }
  }
}

.manual-finale__close {
  position: absolute;
  top: 8px;
  right: 12px;
  width: 28px;
  height: 28px;
  border: 0;
  background: transparent;
  font-size: 22px;
  color: var(--text-secondary);
  cursor: pointer;
  line-height: 1;

  &:hover { color: var(--text-primary); }
}

.manual-finale-fade-enter-active,
.manual-finale-fade-leave-active {
  transition: opacity 320ms ease;
}
.manual-finale-fade-enter-from,
.manual-finale-fade-leave-to { opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .manual-finale-fade-enter-active,
  .manual-finale-fade-leave-active { transition: none; }
}
</style>

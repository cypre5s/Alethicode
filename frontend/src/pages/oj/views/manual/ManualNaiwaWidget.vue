<template>
  <div
    v-show="!hidden"
    class="manual-naiwa-widget"
    :class="{ 'is-expanded': expanded, 'is-mute': muted }"
  >
    <transition name="manual-bubble-fade">
      <div
        v-if="expanded && bubbleText"
        class="manual-naiwa-widget__bubble"
        :style="bubbleStyle"
      >
        <span>{{ bubbleText }}</span>
      </div>
    </transition>
    <button
      type="button"
      class="manual-naiwa-widget__avatar"
      :aria-label="expanded ? '收起奶蛙挂件' : '展开奶蛙挂件'"
      @click="toggleExpanded"
    >
      <img
        :src="expanded ? motionLaughLoop : heroSrc"
        alt="奶蛙挂件"
      >
    </button>
    <transition name="manual-actions">
      <div v-if="expanded" class="manual-naiwa-widget__actions">
        <button type="button" class="manual-naiwa-widget__btn primary" @click="laugh">
          <span aria-hidden="true">🎵</span> 让他笑一下
        </button>
        <button type="button" class="manual-naiwa-widget__btn" @click="toggleMute">
          {{ muted ? '取消静音' : '静音' }}
        </button>
        <button type="button" class="manual-naiwa-widget__btn danger" @click="closeFun">
          关闭趣味模式
        </button>
      </div>
    </transition>
  </div>
</template>

<script>
import { measureBubble } from './manualPretextLayout.js'
import {
  NAIWA_HERO,
  NAIWA_MOTION,
  NAIWA_BUBBLE_LINES
} from './manualContent.js'

export default {
  name: 'ManualNaiwaWidget',
  props: {
    funMode: { type: Boolean, default: true },
    hidden: { type: Boolean, default: false }
  },
  data () {
    return {
      expanded: false,
      muted: false,
      bubbleText: '',
      bubbleWidth: 240,
      autoCollapseTimer: null,
      bubbleRotateTimer: null,
      bubbleIdx: 0
    }
  },
  computed: {
    heroSrc () { return NAIWA_HERO },
    motionLaughLoop () { return NAIWA_MOTION.laughLoop },
    bubbleStyle () {
      return {
        '--manual-bubble-width': `${Math.min(280, Math.max(120, this.bubbleWidth + 28))}px`
      }
    }
  },
  watch: {
    funMode (val) {
      if (!val) this.expanded = false
    }
  },
  mounted () {
    const firstVisitKey = 'manual.widget_seen'
    if (!window.localStorage.getItem(firstVisitKey) && this.funMode) {
      this.expanded = true
      this.scheduleAutoCollapse(6000)
      try { window.localStorage.setItem(firstVisitKey, '1') } catch (err) { console.warn('[ManualNaiwaWidget] persist seen failed', err) }
    }
    this.cycleBubble()
    this.bubbleRotateTimer = setInterval(this.cycleBubble, 8000)
  },
  beforeUnmount () {
    if (this.autoCollapseTimer) clearTimeout(this.autoCollapseTimer)
    if (this.bubbleRotateTimer) clearInterval(this.bubbleRotateTimer)
  },
  methods: {
    cycleBubble () {
      this.bubbleIdx = (this.bubbleIdx + 1) % NAIWA_BUBBLE_LINES.length
      this.bubbleText = NAIWA_BUBBLE_LINES[this.bubbleIdx]
      this.$nextTick(() => {
        const font = '13px/1.5 var(--font-sans)'
        const stat = measureBubble(this.bubbleText, font)
        this.bubbleWidth = stat.width
      })
    },
    toggleExpanded () {
      if (this.expanded) {
        this.expanded = false
        if (this.autoCollapseTimer) clearTimeout(this.autoCollapseTimer)
      } else {
        this.expanded = true
        this.scheduleAutoCollapse(8000)
      }
    },
    scheduleAutoCollapse (ms) {
      if (this.autoCollapseTimer) clearTimeout(this.autoCollapseTimer)
      this.autoCollapseTimer = setTimeout(() => {
        this.expanded = false
      }, ms)
    },
    /**
     * 交给父组件 ManualPage 用页面级 <audio> 元素统一播放，避免和挂件
     * 自带 new Audio 双轨同放导致音量减半或互相打断。
     */
    laugh () {
      if (!this.funMode) {
        this.$emit('toast', '趣味模式已关闭，先打开再点～')
        return
      }
      if (this.muted) {
        this.$emit('toast', '当前静音中，先取消静音')
        return
      }
      this.$emit('laugh')
    },
    toggleMute () {
      this.muted = !this.muted
      this.$emit('toast', this.muted ? '已静音' : '已取消静音')
      this.$emit('mute-changed', this.muted)
    },
    closeFun () {
      this.expanded = false
      this.$emit('close-fun')
    },
    handleEscape (event) {
      if (event.key === 'Escape' && this.expanded) {
        this.expanded = false
      }
    }
  },
  created () {
    if (typeof window !== 'undefined') {
      window.addEventListener('keydown', this.handleEscape)
    }
  },
  unmounted () {
    if (typeof window !== 'undefined') {
      window.removeEventListener('keydown', this.handleEscape)
    }
  }
}
</script>

<style lang="less" scoped>
.manual-naiwa-widget {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 720;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  pointer-events: none;
  --manual-bubble-width: 220px;

  > * { pointer-events: auto; }
}

.manual-naiwa-widget__avatar {
  width: 56px;
  height: 56px;
  border: 0;
  border-radius: 50%;
  padding: 0;
  background: var(--bg-card);
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1);
  overflow: hidden;
  position: relative;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  &:hover, &:focus-visible {
    transform: translateY(-2px) scale(1.06);
    outline: none;
    box-shadow: var(--shadow-lg);
  }
}

.is-expanded .manual-naiwa-widget__avatar {
  width: 96px;
  height: 96px;
  animation: manual-naiwa-bounce 1.6s ease-in-out infinite;
}

@keyframes manual-naiwa-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

.manual-naiwa-widget__bubble {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  color: var(--text-primary);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.5;
  width: var(--manual-bubble-width);
  max-width: 280px;
  position: relative;

  span {
    text-wrap: balance;
    display: block;
  }

  &::after {
    content: '';
    position: absolute;
    right: 22px;
    bottom: -8px;
    width: 14px;
    height: 14px;
    background: inherit;
    border: inherit;
    border-top: 0;
    border-left: 0;
    transform: rotate(45deg);
  }
}

.manual-naiwa-widget__actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 8px;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-default);
  min-width: 180px;
}

.manual-naiwa-widget__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.18s ease;

  &:hover, &:focus-visible {
    color: var(--primary-color);
    border-color: var(--primary-color);
    outline: none;
  }

  &.primary {
    background: var(--warm-grad-primary);
    color: #fff;
    border-color: transparent;

    &:hover { transform: translateY(-1px); }
  }

  &.danger {
    color: var(--color-danger);

    &:hover { color: #fff; background: var(--color-danger); border-color: var(--color-danger); }
  }
}

.manual-bubble-fade-enter-active,
.manual-bubble-fade-leave-active {
  transition: all 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}
.manual-bubble-fade-enter-from,
.manual-bubble-fade-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.96);
}

.manual-actions-enter-active,
.manual-actions-leave-active {
  transition: all 0.24s ease;
}
.manual-actions-enter-from,
.manual-actions-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 640px) {
  .manual-naiwa-widget {
    right: 12px;
    bottom: 12px;
    --manual-bubble-width: 180px;
  }
  .is-expanded .manual-naiwa-widget__avatar {
    width: 72px;
    height: 72px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .is-expanded .manual-naiwa-widget__avatar { animation: none; }
}
</style>

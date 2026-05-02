<template>
  <div class="manual-naiwa-gallery">
    <div
      v-for="(item, idx) in items"
      :key="item.src"
      class="manual-naiwa-gallery__card"
      tabindex="0"
      :aria-label="item.label"
      @mousemove="(event) => onMove(event, idx)"
      @mouseleave="onLeave(idx)"
      @click="onClick(item, $event)"
      @keyup.enter="onClick(item, $event)"
      :ref="el => setCardRef(el, idx)"
    >
      <img :src="item.src" :alt="item.alt" loading="lazy" decoding="async">
      <span class="manual-naiwa-gallery__label">{{ item.label }}</span>
    </div>
  </div>
</template>

<script>
import { NAIWA_GALLERY } from './manualContent.js'

export default {
  name: 'ManualNaiwaGallery',
  props: {
    funMode: { type: Boolean, default: true }
  },
  data () {
    return {
      items: NAIWA_GALLERY,
      cardRefs: []
    }
  },
  methods: {
    setCardRef (el, idx) {
      if (el) this.cardRefs[idx] = el
    },
    onMove (event, idx) {
      if (this.prefersReducedMotion()) return
      const card = this.cardRefs[idx]
      if (!card) return
      const rect = card.getBoundingClientRect()
      const x = (event.clientX - rect.left) / rect.width - 0.5
      const y = (event.clientY - rect.top) / rect.height - 0.5
      card.style.transform = `perspective(800px) rotateX(${(-y * 10).toFixed(2)}deg) rotateY(${(x * 12).toFixed(2)}deg)`
    },
    onLeave (idx) {
      const card = this.cardRefs[idx]
      if (card) card.style.transform = ''
    },
    onClick (item, event) {
      // 把笑声播放交给父组件统一处理（页面级 <audio>），避免重复实例。
      this.$emit('burst', { event, item })
    },
    prefersReducedMotion () {
      return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    }
  }
}
</script>

<style lang="less" scoped>
.manual-naiwa-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 16px;
  padding: 8px 0;
}

.manual-naiwa-gallery__card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: transform 220ms cubic-bezier(0.4, 0, 0.2, 1), box-shadow 220ms ease, border-color 220ms ease;
  will-change: transform;
  outline: none;
  text-align: center;

  &:hover, &:focus-visible {
    border-color: var(--primary-color);
    box-shadow: var(--shadow-md);
  }

  img {
    width: 96px;
    height: 96px;
    object-fit: contain;
    filter: drop-shadow(0 4px 12px rgba(99, 102, 241, 0.18));
  }
}

.manual-naiwa-gallery__label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
}

@media (max-width: 640px) {
  .manual-naiwa-gallery__card { transform: none !important; }
}

@media (prefers-reduced-motion: reduce) {
  .manual-naiwa-gallery__card { transition: none; transform: none !important; }
}
</style>

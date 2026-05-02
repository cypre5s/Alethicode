<template>
  <span class="manual-naiwa-sticker" :class="sizeClass" :style="rotationStyle" aria-hidden="true">
    <img :src="src" :alt="alt" loading="lazy" decoding="async">
  </span>
</template>

<script>
import { NAIWA_STICKERS } from './manualContent.js'

export default {
  name: 'ManualNaiwaSticker',
  props: {
    index: {
      type: Number,
      default: 0
    },
    size: {
      type: String,
      default: 'md',
      validator: v => ['xs', 'sm', 'md', 'lg'].includes(v)
    },
    rotate: {
      type: Number,
      default: 0
    }
  },
  computed: {
    sticker () {
      const i = ((this.index % NAIWA_STICKERS.length) + NAIWA_STICKERS.length) % NAIWA_STICKERS.length
      return NAIWA_STICKERS[i]
    },
    src () {
      return this.sticker.src
    },
    alt () {
      return this.sticker.alt
    },
    sizeClass () {
      return `size-${this.size}`
    },
    rotationStyle () {
      return this.rotate ? { '--manual-sticker-rotation': `${this.rotate}deg` } : null
    }
  }
}
</script>

<style lang="less" scoped>
.manual-naiwa-sticker {
  --manual-sticker-rotation: 0deg;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transform: rotate(var(--manual-sticker-rotation));
  transition: transform 220ms cubic-bezier(0.34, 1.56, 0.64, 1);

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: block;
    pointer-events: none;
  }

  &:hover {
    transform: rotate(calc(var(--manual-sticker-rotation) + 8deg)) scale(1.06);
  }

  &.size-xs { width: 24px; height: 24px; }
  &.size-sm { width: 36px; height: 36px; }
  &.size-md { width: 56px; height: 56px; }
  &.size-lg { width: 80px; height: 80px; }
}

@media (prefers-reduced-motion: reduce) {
  .manual-naiwa-sticker {
    transition: none;
    &:hover { transform: rotate(var(--manual-sticker-rotation)); }
  }
}
</style>

<template>
  <div class="manual-naiwa-popper" aria-hidden="true">
    <transition name="manual-popper-slide">
      <img
        v-if="visible"
        :key="popKey"
        :src="src"
        :class="['popper-img', `from-${edge}`]"
        alt=""
      >
    </transition>
  </div>
</template>

<script>
import { NAIWA_GALLERY, NAIWA_STICKERS } from './manualContent.js'

const EDGES = ['left', 'right', 'top', 'bottom']

export default {
  name: 'ManualNaiwaRandomPopper',
  data () {
    return {
      visible: false,
      src: '',
      edge: 'left',
      popKey: 0,
      timer: null,
      hideTimer: null
    }
  },
  mounted () {
    this.scheduleNext()
  },
  beforeUnmount () {
    if (this.timer) clearTimeout(this.timer)
    if (this.hideTimer) clearTimeout(this.hideTimer)
  },
  methods: {
    pickAsset () {
      const pool = [...NAIWA_GALLERY, ...NAIWA_STICKERS]
      const idx = Math.floor(Math.random() * pool.length)
      return pool[idx].src
    },
    scheduleNext () {
      const wait = 90000 + Math.random() * 90000
      this.timer = setTimeout(() => {
        this.popOnce()
      }, wait)
    },
    popOnce () {
      this.src = this.pickAsset()
      this.edge = EDGES[Math.floor(Math.random() * EDGES.length)]
      this.popKey += 1
      this.visible = true
      const lifetime = 3000 + Math.random() * 2000
      this.hideTimer = setTimeout(() => {
        this.visible = false
        this.scheduleNext()
      }, lifetime)
    }
  }
}
</script>

<style lang="less" scoped>
.manual-naiwa-popper {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 580;
  overflow: hidden;
}

.popper-img {
  position: absolute;
  width: 96px;
  height: 96px;
  object-fit: contain;
  filter: drop-shadow(0 8px 18px rgba(99, 102, 241, 0.25));
}

.popper-img.from-left { left: 0; bottom: 80px; }
.popper-img.from-right { right: 0; bottom: 80px; }
.popper-img.from-top { right: 24px; top: 0; }
.popper-img.from-bottom { left: 24px; bottom: 0; }

.manual-popper-slide-enter-active,
.manual-popper-slide-leave-active {
  transition: transform 600ms cubic-bezier(0.34, 1.56, 0.64, 1), opacity 320ms ease;
}

.manual-popper-slide-enter-from.from-left,
.manual-popper-slide-leave-to.from-left { transform: translateX(-110%); opacity: 0; }
.manual-popper-slide-enter-from.from-right,
.manual-popper-slide-leave-to.from-right { transform: translateX(110%); opacity: 0; }
.manual-popper-slide-enter-from.from-top,
.manual-popper-slide-leave-to.from-top { transform: translateY(-110%); opacity: 0; }
.manual-popper-slide-enter-from.from-bottom,
.manual-popper-slide-leave-to.from-bottom { transform: translateY(110%); opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .manual-naiwa-popper { display: none; }
}

@media (max-width: 768px) {
  .manual-naiwa-popper { display: none; }
}
</style>

<template>
  <span class="rough-annotation" ref="wrapRef" :class="[`rough-annotation--${type}`]">
    <slot></slot>
    <svg
      v-if="ready"
      class="rough-annotation__svg"
      :width="svgW"
      :height="svgH"
      :viewBox="`0 0 ${svgW} ${svgH}`"
      aria-hidden="true"
      ref="svgRef"
    ></svg>
  </span>
</template>

<script>
import rough from 'roughjs'

export default {
  name: 'ManualRoughAnnotation',
  props: {
    type: {
      type: String,
      default: 'underline',
      validator: v => ['underline', 'circle', 'box', 'highlight', 'strike-through', 'bracket'].includes(v)
    },
    color: { type: String, default: '#6366f1' },
    strokeWidth: { type: Number, default: 2 },
    padding: { type: Number, default: 4 },
    animate: { type: Boolean, default: true },
    roughness: { type: Number, default: 1.5 }
  },
  data () {
    return {
      ready: false,
      svgW: 0,
      svgH: 0,
      observer: null,
      drawn: false
    }
  },
  mounted () {
    this.measure()
    if (this.animate) {
      this.setupObserver()
    } else {
      this.$nextTick(() => this.draw())
    }
    window.addEventListener('resize', this.onResize)
  },
  beforeUnmount () {
    if (this.observer) this.observer.disconnect()
    window.removeEventListener('resize', this.onResize)
  },
  methods: {
    measure () {
      const el = this.$refs.wrapRef
      if (!el) return
      const rect = el.getBoundingClientRect()
      this.svgW = Math.ceil(rect.width + this.padding * 2)
      this.svgH = Math.ceil(rect.height + this.padding * 2)
      this.ready = true
    },
    setupObserver () {
      if (typeof IntersectionObserver === 'undefined') {
        this.$nextTick(() => this.draw())
        return
      }
      this.observer = new IntersectionObserver(entries => {
        for (const entry of entries) {
          if (entry.isIntersecting && !this.drawn) {
            this.drawn = true
            this.$nextTick(() => this.draw())
            this.observer.disconnect()
          }
        }
      }, { threshold: 0.5 })
      if (this.$refs.wrapRef) this.observer.observe(this.$refs.wrapRef)
    },
    draw () {
      const svgEl = this.$refs.svgRef
      if (!svgEl) return

      while (svgEl.firstChild) svgEl.removeChild(svgEl.firstChild)

      const rc = rough.svg(svgEl)
      const p = this.padding
      const w = this.svgW - p * 2
      const h = this.svgH - p * 2
      const opts = {
        stroke: this.color,
        strokeWidth: this.strokeWidth,
        roughness: this.roughness,
        bowing: 1
      }

      let node
      switch (this.type) {
        case 'underline':
          node = rc.line(p, h + p - 2, w + p, h + p - 2, opts)
          break
        case 'circle':
          node = rc.ellipse(p + w / 2, p + h / 2, w + 8, h + 8, opts)
          break
        case 'box':
          node = rc.rectangle(p - 2, p - 2, w + 4, h + 4, opts)
          break
        case 'highlight':
          node = rc.rectangle(p - 2, p - 2, w + 4, h + 4, {
            ...opts,
            fill: this.color,
            fillStyle: 'solid',
            fillWeight: 0.5,
            stroke: 'none',
            roughness: 0.8
          })
          break
        case 'strike-through':
          node = rc.line(p, p + h / 2, w + p, p + h / 2, opts)
          break
        case 'bracket': {
          const bracketW = 8
          const path = `M ${p + bracketW} ${p} Q ${p} ${p}, ${p} ${p + h * 0.15} L ${p} ${p + h * 0.85} Q ${p} ${p + h}, ${p + bracketW} ${p + h}`
          node = rc.path(path, opts)
          break
        }
      }

      if (node) {
        svgEl.appendChild(node)
        if (this.animate) {
          const paths = node.querySelectorAll('path')
          paths.forEach(pathEl => {
            const length = pathEl.getTotalLength ? pathEl.getTotalLength() : 200
            pathEl.style.strokeDasharray = `${length}`
            pathEl.style.strokeDashoffset = `${length}`
            pathEl.style.animation = `rough-draw 800ms ease forwards`
          })
        }
      }
    },
    onResize () {
      this.drawn = false
      this.measure()
      this.$nextTick(() => {
        this.draw()
        this.drawn = true
      })
    }
  }
}
</script>

<style lang="less" scoped>
.rough-annotation {
  position: relative;
  display: inline;
}

.rough-annotation__svg {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: -1;
  overflow: visible;
}

.rough-annotation--highlight .rough-annotation__svg {
  opacity: 0.15;
}

@keyframes rough-draw {
  to {
    stroke-dashoffset: 0;
  }
}
</style>

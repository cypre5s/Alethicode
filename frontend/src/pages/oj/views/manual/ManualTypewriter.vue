<template>
  <span class="manual-typewriter" :class="{ 'is-done': done }" aria-live="polite">
    <span ref="visibleRef" class="manual-typewriter__visible">{{ rendered }}</span>
    <span v-if="!done" class="manual-typewriter__caret" aria-hidden="true">|</span>
  </span>
</template>

<script>
/**
 * 一字一字逐次出现的打字效果。支持 prefers-reduced-motion 自动降级为整段直出。
 */
export default {
  name: 'ManualTypewriter',
  props: {
    text: { type: String, required: true },
    speed: { type: Number, default: 55 },
    startDelay: { type: Number, default: 250 }
  },
  data () {
    return {
      rendered: '',
      done: false,
      timer: null,
      idx: 0
    }
  },
  watch: {
    text () {
      this.reset()
      this.start()
    }
  },
  mounted () {
    if (this.prefersReducedMotion()) {
      this.rendered = this.text
      this.done = true
      return
    }
    this.start()
  },
  beforeUnmount () {
    if (this.timer) clearTimeout(this.timer)
  },
  methods: {
    prefersReducedMotion () {
      return typeof window !== 'undefined'
        && window.matchMedia
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    reset () {
      if (this.timer) clearTimeout(this.timer)
      this.timer = null
      this.idx = 0
      this.rendered = ''
      this.done = false
    },
    start () {
      this.timer = setTimeout(this.tick, this.startDelay)
    },
    tick () {
      if (this.idx >= this.text.length) {
        this.done = true
        this.timer = null
        return
      }
      this.rendered += this.text.charAt(this.idx)
      this.idx += 1
      this.timer = setTimeout(this.tick, this.speed)
    }
  }
}
</script>

<style lang="less" scoped>
.manual-typewriter {
  display: inline;
  word-break: break-word;
}

.manual-typewriter__caret {
  display: inline-block;
  width: 2px;
  margin-left: 2px;
  color: var(--primary-color);
  animation: manual-typewriter-blink 1s steps(1, end) infinite;
  font-weight: 400;
}

@keyframes manual-typewriter-blink {
  0%, 50% { opacity: 1; }
  50.01%, 100% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .manual-typewriter__caret { animation: none; opacity: 0; }
}
</style>

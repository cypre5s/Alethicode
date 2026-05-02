<template>
  <div class="stats-counter" ref="rootRef">
    <div v-for="item in stats" :key="item.label" class="stats-counter__item">
      <div class="stats-counter__row">
        <span class="stats-counter__number" :ref="el => setRef(item.label, el)">0</span>
        <span class="stats-counter__unit" v-if="item.unit">{{ item.unit }}</span>
      </div>
      <span class="stats-counter__label">{{ item.label }}</span>
    </div>
  </div>
</template>

<script>
import { CountUp } from 'countup.js'

export default {
  name: 'ManualStatsCounter',
  data () {
    return {
      stats: [
        { label: '核心页面', value: 15, unit: '个' },
        { label: 'AI 导学角色', value: 5, unit: '位' },
        { label: '新手步骤', value: 8, unit: '步' },
        { label: '常见问题', value: 8, unit: '条' }
      ],
      countUps: [],
      observed: false,
      observer: null,
      numRefs: {}
    }
  },
  mounted () {
    this.setupObserver()
  },
  beforeUnmount () {
    if (this.observer) this.observer.disconnect()
  },
  methods: {
    setRef (label, el) {
      if (el) this.numRefs[label] = el
    },
    setupObserver () {
      if (typeof IntersectionObserver === 'undefined') {
        this.startCounting()
        return
      }
      this.observer = new IntersectionObserver(entries => {
        for (const entry of entries) {
          if (entry.isIntersecting && !this.observed) {
            this.observed = true
            this.$nextTick(() => this.startCounting())
            this.observer.disconnect()
          }
        }
      }, { threshold: 0.3 })
      if (this.$refs.rootRef) this.observer.observe(this.$refs.rootRef)
    },
    startCounting () {
      for (const item of this.stats) {
        const el = this.numRefs[item.label]
        if (!el) continue
        const cu = new CountUp(el, item.value, {
          duration: 2,
          useEasing: true,
          useGrouping: false,
          startVal: 0
        })
        if (!cu.error) {
          cu.start()
          this.countUps.push(cu)
        }
      }
    }
  }
}
</script>

<style lang="less" scoped>
.stats-counter {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 8px 0;
}

.stats-counter__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 10px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(99, 102, 241, 0.08);
  border-radius: var(--radius-md);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: -1px;
    border-radius: inherit;
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(236, 72, 153, 0.15));
    opacity: 0;
    transition: opacity 0.3s ease;
    z-index: -1;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
    border-color: rgba(99, 102, 241, 0.2);

    &::before { opacity: 1; }
  }
}

.stats-counter__row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stats-counter__number {
  font-size: 32px;
  font-weight: 800;
  font-family: var(--font-mono);
  background: linear-gradient(135deg, #6366f1, #ec4899);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1;
}

.stats-counter__unit {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
}

.stats-counter__label {
  font-size: 13px;
  color: var(--text-disabled);
  font-weight: 500;
}

@media (max-width: 640px) {
  .stats-counter {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

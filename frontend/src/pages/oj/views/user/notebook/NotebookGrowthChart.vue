<template>
  <section class="ngc-card">
    <button type="button" class="ngc-toggle" :aria-expanded="expanded" @click="expanded = !expanded">
      <span class="ngc-toggle-icon" aria-hidden="true">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
      </span>
      <span class="ngc-toggle-text">
        <span class="ngc-toggle-title">我的学习轨迹</span>
        <span class="ngc-toggle-sub">{{ summaryText }}</span>
      </span>
      <span class="ngc-toggle-chevron" :class="{ 'is-open': expanded }" aria-hidden="true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
      </span>
    </button>

    <transition name="ngc-collapse">
      <div v-if="expanded" class="ngc-body">
        <div class="ngc-toolbar">
          <div class="ngc-tab-group" role="tablist" aria-label="数据分组方式">
            <button
              type="button"
              role="tab"
              :aria-selected="groupBy === 'aggregate'"
              :class="['ngc-tab', { 'is-active': groupBy === 'aggregate' }]"
              @click="setGroup('aggregate')"
            >
              聚合
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="groupBy === 'language'"
              :class="['ngc-tab', { 'is-active': groupBy === 'language' }]"
              @click="setGroup('language')"
            >
              按语言
            </button>
          </div>
          <div v-if="weeks.length" class="ngc-summary">
            <span class="ngc-summary-pill ngc-pill-error">
              <span class="ngc-pill-dot"></span>
              新错题 <b>{{ totalErrors }}</b>
            </span>
            <span class="ngc-summary-pill ngc-pill-fix">
              <span class="ngc-pill-dot"></span>
              平均修复率 <b>{{ avgFixRatePct }}%</b>
            </span>
            <span class="ngc-summary-pill ngc-pill-bt">
              <span class="ngc-pill-dot"></span>
              顿悟 <b>{{ totalBreakthroughs }}</b>
            </span>
          </div>
        </div>

        <div v-if="loading" class="ngc-loading" role="status">
          <span class="ngc-bar-skeleton" v-for="i in 6" :key="i" :style="{ height: 32 + (i * 7) % 50 + 'px' }"></span>
        </div>
        <div v-else-if="!weeks.length" class="ngc-empty">
          <span class="ngc-empty-icon" aria-hidden="true">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
          </span>
          <div class="ngc-empty-title">暂无成长数据</div>
          <div class="ngc-empty-sub">坚持记录错题，几周后就能看到自己的成长曲线</div>
        </div>
        <div v-else class="ngc-chart">
          <div class="ngc-grid-lines" aria-hidden="true">
            <span v-for="i in 4" :key="i" class="ngc-grid-line"></span>
          </div>
          <div class="ngc-bars">
            <div
              v-for="(w, i) in weeks"
              :key="i"
              class="ngc-bar-col"
              :title="tooltipFor(w)"
            >
              <div class="ngc-bar-stack">
                <div
                  class="ngc-bar ngc-bar-error"
                  :style="{ height: barHeight(w.new_errors) + '%' }"
                >
                  <span class="ngc-bar-label">{{ w.new_errors }}</span>
                </div>
                <div
                  v-if="w.new_breakthroughs"
                  class="ngc-bar ngc-bar-bt"
                  :style="{ height: barHeight(w.new_breakthroughs) + '%' }"
                >
                  <span class="ngc-bar-label">{{ w.new_breakthroughs }}</span>
                </div>
              </div>
              <div class="ngc-bar-foot">
                <div class="ngc-bar-week">{{ formatWeek(w.week) }}</div>
                <div v-if="w.fix_rate != null" class="ngc-bar-fix">
                  修复 {{ Math.round(w.fix_rate * 100) }}%
                </div>
              </div>
            </div>
          </div>
          <div class="ngc-legend">
            <span class="ngc-legend-item"><span class="ngc-legend-dot ngc-dot-error"></span>新错题</span>
            <span class="ngc-legend-item"><span class="ngc-legend-dot ngc-dot-bt"></span>顿悟</span>
            <span class="ngc-legend-item ngc-legend-hint">数字越高，那一周的活跃度越高</span>
          </div>
        </div>
      </div>
    </transition>
  </section>
</template>

<script>
import { ajax } from '@oj/api/shared'

export default {
  name: 'NotebookGrowthChart',
  data () {
    return {
      expanded: false,
      loading: false,
      groupBy: 'aggregate',
      weeks: []
    }
  },
  computed: {
    maxBarValue () {
      let max = 0
      for (const w of this.weeks) {
        const e = parseInt(w.new_errors, 10) || 0
        const b = parseInt(w.new_breakthroughs, 10) || 0
        if (e > max) max = e
        if (b > max) max = b
      }
      return max || 1
    },
    totalErrors () {
      return this.weeks.reduce((acc, w) => acc + (parseInt(w.new_errors, 10) || 0), 0)
    },
    totalBreakthroughs () {
      return this.weeks.reduce((acc, w) => acc + (parseInt(w.new_breakthroughs, 10) || 0), 0)
    },
    avgFixRatePct () {
      const valid = this.weeks.filter(w => w.fix_rate != null)
      if (!valid.length) return 0
      const sum = valid.reduce((acc, w) => acc + (parseFloat(w.fix_rate) || 0), 0)
      return Math.round((sum / valid.length) * 100)
    },
    summaryText () {
      if (this.weeks.length) {
        return `近 ${this.weeks.length} 周 · 共 ${this.totalErrors} 错 · ${this.totalBreakthroughs} 悟`
      }
      return '展开查看你的每周错题与修复趋势'
    }
  },
  watch: {
    expanded (val) { if (val && !this.weeks.length) this.load() }
  },
  methods: {
    setGroup (g) {
      this.groupBy = g
      this.load()
    },
    async load () {
      this.loading = true
      try {
        const res = await ajax('ai/tutor/notebook/growth-curve', 'get', { params: { group_by: this.groupBy } })
        const data = res.data && res.data.data
        this.weeks = (data && data.weeks) || []
      } finally { this.loading = false }
    },
    formatWeek (val) {
      if (!val) return ''
      const d = new Date(val)
      return Number.isNaN(d.getTime()) ? val : (d.getMonth() + 1) + '/' + d.getDate()
    },
    barHeight (value) {
      const v = parseInt(value, 10) || 0
      if (v === 0) return 0
      return Math.max(6, (v / this.maxBarValue) * 100)
    },
    tooltipFor (w) {
      const parts = []
      parts.push(this.formatWeek(w.week) + ' 那一周')
      parts.push('新错题 ' + (w.new_errors || 0))
      if (w.fix_rate != null) parts.push('修复率 ' + Math.round(w.fix_rate * 100) + '%')
      if (w.new_breakthroughs) parts.push('顿悟 ' + w.new_breakthroughs)
      return parts.join(' · ')
    }
  }
}
</script>

<style lang="less" scoped>
.ngc-card {
  background: var(--nb-bg-surface);
  border: 1px solid var(--nb-border-soft);
  border-radius: var(--nb-radius-lg);
  box-shadow: var(--nb-shadow-soft);
  overflow: hidden;
}

.ngc-toggle {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: none;
  background: transparent;
  padding: 14px 18px;
  font-family: inherit;
  cursor: pointer;
  text-align: left;
  transition: background var(--nb-transition);

  &:hover {
    background: var(--nb-bg-subtle);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--nb-shadow-glow);
  }
}

.ngc-toggle-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--nb-grad-cool);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(99, 102, 241, 0.22);
}

.ngc-toggle-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ngc-toggle-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--nb-color-text);
  line-height: 1.3;
}

.ngc-toggle-sub {
  font-size: 12px;
  color: var(--nb-color-text-dim);
  margin-top: 2px;
}

.ngc-toggle-chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--nb-color-text-dim);
  transition: transform var(--nb-transition);

  &.is-open {
    transform: rotate(180deg);
    color: var(--nb-color-primary-strong);
  }
}

.ngc-body {
  padding: 0 18px 18px;
}

.ngc-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
}

.ngc-tab-group {
  display: inline-flex;
  background: var(--nb-bg-subtle);
  border: 1px solid var(--nb-border-soft);
  border-radius: 999px;
  padding: 3px;
  gap: 2px;
}

.ngc-tab {
  border: none;
  background: transparent;
  color: var(--nb-color-text-mid);
  padding: 5px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all var(--nb-transition);

  &:hover {
    color: var(--nb-color-primary-strong);
  }

  &.is-active {
    background: var(--nb-grad-primary);
    color: #fff;
    box-shadow: 0 2px 8px rgba(124, 58, 237, 0.28);
  }
}

.ngc-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ngc-summary-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--nb-bg-subtle);
  border: 1px solid var(--nb-border-soft);
  color: var(--nb-color-text-mid);

  b {
    font-feature-settings: 'tnum';
    font-weight: 700;
    color: var(--nb-color-text);
  }
}

.ngc-pill-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.ngc-pill-error .ngc-pill-dot { background: var(--nb-color-danger); }
.ngc-pill-fix .ngc-pill-dot { background: var(--nb-color-success); }
.ngc-pill-bt .ngc-pill-dot { background: var(--nb-color-primary); }

.ngc-loading {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 180px;
  padding: 0 8px;
}

.ngc-bar-skeleton {
  flex: 1;
  border-radius: 8px 8px 4px 4px;
  background: linear-gradient(180deg, rgba(196, 181, 253, 0.32) 0%, rgba(196, 181, 253, 0.12) 100%);
  animation: ngc-pulse 1.4s ease-in-out infinite;
}

.ngc-empty {
  text-align: center;
  padding: 36px 16px;
  color: var(--nb-color-text-mid);
}

.ngc-empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--nb-bg-subtle);
  color: var(--nb-color-primary);
  margin-bottom: 10px;
}

.ngc-empty-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--nb-color-text);
}

.ngc-empty-sub {
  font-size: 12px;
  color: var(--nb-color-text-dim);
  margin-top: 4px;
}

.ngc-chart {
  position: relative;
}

.ngc-grid-lines {
  position: absolute;
  inset: 12px 8px 60px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
  z-index: 0;
}

.ngc-grid-line {
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, rgba(196, 181, 253, 0.3) 50%, transparent 100%);
}

.ngc-bars {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 200px;
  padding: 0 8px;
}

.ngc-bar-col {
  flex: 1;
  min-width: 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  cursor: default;

  &:hover .ngc-bar {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(124, 58, 237, 0.18);
  }

  &:hover .ngc-bar-label {
    opacity: 1;
  }
}

.ngc-bar-stack {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column-reverse;
  align-items: stretch;
  gap: 2px;
  justify-content: flex-start;
  padding-bottom: 8px;
}

.ngc-bar {
  position: relative;
  width: 100%;
  border-radius: 8px 8px 4px 4px;
  min-height: 6px;
  transition: transform var(--nb-transition), box-shadow var(--nb-transition), height 360ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

.ngc-bar-error {
  background: linear-gradient(180deg, #f87171 0%, #ef4444 100%);
}

.ngc-bar-bt {
  background: linear-gradient(180deg, #a78bfa 0%, #7c3aed 100%);
  border-radius: 6px;
}

.ngc-bar-label {
  position: absolute;
  top: -18px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  font-weight: 700;
  color: var(--nb-color-text);
  font-feature-settings: 'tnum';
  opacity: 0;
  transition: opacity var(--nb-transition);
  white-space: nowrap;
}

.ngc-bar-foot {
  width: 100%;
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.ngc-bar-week {
  font-size: 11px;
  color: var(--nb-color-text-mid);
  font-weight: 600;
}

.ngc-bar-fix {
  font-size: 10px;
  color: var(--nb-color-success);
  font-weight: 600;
  font-feature-settings: 'tnum';
}

.ngc-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
  font-size: 11px;
  color: var(--nb-color-text-mid);
  flex-wrap: wrap;
}

.ngc-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.ngc-legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.ngc-dot-error { background: linear-gradient(180deg, #f87171 0%, #ef4444 100%); }
.ngc-dot-bt { background: linear-gradient(180deg, #a78bfa 0%, #7c3aed 100%); }

.ngc-legend-hint {
  margin-left: auto;
  color: var(--nb-color-text-dim);
}

.ngc-collapse-enter-active,
.ngc-collapse-leave-active {
  transition: max-height 280ms ease, opacity 220ms ease;
  overflow: hidden;
  max-height: 600px;
}

.ngc-collapse-enter-from,
.ngc-collapse-leave-to {
  max-height: 0;
  opacity: 0;
}

@keyframes ngc-pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 0.9; }
}

@media (prefers-reduced-motion: reduce) {
  .ngc-toggle,
  .ngc-tab,
  .ngc-toggle-chevron,
  .ngc-bar,
  .ngc-bar-skeleton,
  .ngc-collapse-enter-active,
  .ngc-collapse-leave-active {
    transition: none !important;
    animation: none !important;
  }
}
</style>

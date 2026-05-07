<template>
  <transition name="smd-slide">
    <div v-if="visible" class="smd-overlay" @click.self="$emit('close')">
      <div class="smd-panel" :class="{ 'smd-panel-bottom': isMobile }">
        <div class="smd-header">
          <div class="smd-header-top">
            <div class="smd-title-row">
              <h3 class="smd-title">{{ kcDetail.kc ? kcDetail.kc.name : '加载中...' }}</h3>
              <span class="smd-chapter-tag">{{ chapterLabel }}</span>
            </div>
            <button class="smd-close" @click="$emit('close')">&times;</button>
          </div>
          <div class="smd-mastery-ring-wrap">
            <svg class="smd-mastery-ring" width="72" height="72" viewBox="0 0 72 72">
              <circle cx="36" cy="36" r="30" stroke="#2c3e50" stroke-width="6" fill="none" />
              <circle
                cx="36" cy="36" r="30"
                :stroke="masteryColor"
                stroke-width="6"
                fill="none"
                stroke-linecap="round"
                :stroke-dasharray="masteryDash"
                transform="rotate(-90 36 36)"
              />
              <text x="36" y="40" text-anchor="middle" fill="#ecf0f1" font-size="16" font-weight="600">
                {{ masteryPct }}%
              </text>
            </svg>
            <div class="smd-mastery-label">掌握度</div>
          </div>
          <div v-if="kcDetail.kc && kcDetail.kc.description" class="smd-desc">{{ kcDetail.kc.description }}</div>
        </div>

        <div class="smd-body">
          <section class="smd-section">
            <h4 class="smd-section-title">关联题目 ({{ kcDetail.problems.length }})</h4>
            <div v-if="!kcDetail.problems.length" class="smd-empty-hint">暂无关联题目</div>
            <div
              v-for="p in kcDetail.problems"
              :key="p.problem_id"
              class="smd-problem-row"
              @click="goToProblem(p)"
            >
              <span class="smd-problem-id">#{{ p.display_id }}</span>
              <span class="smd-problem-title">{{ p.title }}</span>
              <span :class="['smd-problem-status', statusClass(p.user_result)]">
                {{ statusText(p.user_result) }}
              </span>
            </div>
          </section>
          <section class="smd-section" v-if="kcDetail.prerequisites.length">
            <h4 class="smd-section-title">前置依赖</h4>
            <div
              v-for="pre in kcDetail.prerequisites"
              :key="pre.kc_id"
              class="smd-prereq-row"
              @click="$emit('prereq-click', pre.kc_id)"
            >
              <span class="smd-prereq-name">{{ pre.kc_name }}</span>
              <span
                class="smd-prereq-mastery"
                :style="{ color: getMasteryColor(pre.mastery) }"
              >{{ (pre.mastery * 100).toFixed(0) }}%</span>
              <div v-if="pre.mastery < 0.5" class="smd-prereq-warn">
                建议先掌握「{{ pre.kc_name }}」再练习本知识点
              </div>
            </div>
          </section>
          <section class="smd-section" v-if="kcDetail.active_misconceptions.length">
            <h4 class="smd-section-title">
              易错点 ({{ kcDetail.active_misconceptions.length }})
            </h4>
            <div
              v-for="m in kcDetail.active_misconceptions"
              :key="m.id"
              class="smd-misc-card"
            >
              <div class="smd-misc-header">
                <span class="smd-misc-name">{{ m.name }}</span>
                <span class="smd-misc-trigger">触发 {{ m.trigger_count }} 次</span>
              </div>
              <div v-if="m.description" class="smd-misc-desc">{{ m.description }}</div>
              <div v-if="m.correction_hint" class="smd-misc-hint">
                <span class="smd-misc-hint-icon">💡</span> {{ m.correction_hint }}
              </div>
            </div>
          </section>
          <section class="smd-section" v-if="kcDetail.mastery_history && kcDetail.mastery_history.length > 1">
            <h4 class="smd-section-title">掌握度趋势</h4>
            <div ref="trendChart" class="smd-trend-chart"></div>
          </section>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import echarts from '@/utils/echarts'

export default {
  name: 'StarMapDetailPanel',
  props: {
    visible: { type: Boolean, default: false },
    kcDetail: {
      type: Object,
      default: () => ({
        kc: { id: 0, name: '', chapter: '', description: '' },
        mastery: { p_mastery: 0, update_count: 0 },
        problems: [],
        prerequisites: [],
        active_misconceptions: [],
        mastery_history: []
      })
    }
  },
  data () {
    return {
      chart: null,
      isMobile: false
    }
  },
  computed: {
    masteryPct () {
      return Math.round((this.kcDetail.mastery.p_mastery || 0) * 100)
    },
    masteryColor () {
      return this.getMasteryColor(this.kcDetail.mastery.p_mastery || 0)
    },
    masteryDash () {
      const circumference = 2 * Math.PI * 30
      const filled = circumference * (this.kcDetail.mastery.p_mastery || 0)
      return `${filled} ${circumference - filled}`
    },
    chapterLabel () {
      const sourceChapter = this.kcDetail && this.kcDetail.kc && this.kcDetail.kc.chapter
        ? String(this.kcDetail.kc.chapter).trim()
        : ''
      const rawChapter = sourceChapter.replace(/^第第+/, '第')
      if (!rawChapter) return ''
      if (/^PPT\d+$/i.test(rawChapter)) return rawChapter.toUpperCase()
      if (rawChapter.startsWith('第')) {
        return rawChapter.includes('章') ? rawChapter : `${rawChapter}章`
      }
      if (rawChapter.includes('章')) return rawChapter
      if (/^\d+$/.test(rawChapter) || /^[一二三四五六七八九十百千万零两]+$/.test(rawChapter)) {
        return `第${rawChapter}章`
      }
      return rawChapter
    }
  },
  watch: {
    visible (val) {
      if (val) {
        this.$nextTick(() => this.renderTrendChart())
      } else {
        this.destroyChart()
      }
    },
    kcDetail: {
      handler () {
        if (this.visible) {
          this.$nextTick(() => this.renderTrendChart())
        }
      },
      deep: false
    }
  },
  mounted () {
    this.checkMobile()
    window.addEventListener('resize', this.checkMobile)
  },
  beforeUnmount () {
    this.destroyChart()
    window.removeEventListener('resize', this.checkMobile)
  },
  methods: {
    checkMobile () {
      this.isMobile = window.innerWidth <= 1200
    },

    getMasteryColor (m) {
      if (m >= 0.7) return '#3498db'
      if (m >= 0.5) return '#f39c12'
      if (m >= 0.3) return '#e74c3c'
      return '#7f8c8d'
    },

    statusClass (result) {
      const hasResult = result !== null && typeof result !== 'undefined' && result !== ''
      const numericResult = hasResult ? Number(result) : Number.NaN
      if (result === 'AC' || numericResult === 0) return 'smd-status-ac'
      if (result === 'WA' || result === 'RE' || result === 'TLE' || result === 'MLE' || numericResult === -1) return 'smd-status-wa'
      return 'smd-status-none'
    },
    statusText (result) {
      const hasResult = result !== null && typeof result !== 'undefined' && result !== ''
      const numericResult = hasResult ? Number(result) : Number.NaN
      if (result === 'AC' || numericResult === 0) return '已通过'
      if (result === 'WA' || numericResult === -1) return '未通过'
      if (result === 'RE' || result === 'TLE' || result === 'MLE') return result
      if (!result) return '未做'
      return result
    },

    goToProblem (p) {
      this.$router.push({ name: 'problem-details', params: { problemID: p.display_id || p.problem_id } })
    },

    renderTrendChart () {
      const history = this.kcDetail.mastery_history
      if (!history || history.length < 2 || !this.$refs.trendChart) return

      this.destroyChart()

      this.chart = echarts.init(this.$refs.trendChart)
      this.chart.setOption({
        grid: { left: 36, right: 12, top: 12, bottom: 24 },
        xAxis: {
          type: 'category',
          data: history.map((_, i) => '第' + (i + 1) + '次'),
          axisLine: { lineStyle: { color: '#555' } },
          axisLabel: { color: '#8899aa', fontSize: 10 }
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 1,
          splitNumber: 4,
          axisLine: { show: false },
          axisLabel: { color: '#8899aa', fontSize: 10, formatter: v => (v * 100).toFixed(0) + '%' },
          splitLine: { lineStyle: { color: '#2c3e50' } }
        },
        series: [{
          type: 'line',
          data: history.map(h => h.p_mastery),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { color: '#3498db', width: 2 },
          itemStyle: { color: '#3498db' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(52,152,219,0.3)' },
              { offset: 1, color: 'rgba(52,152,219,0)' }
            ])
          }
        }]
      })
    },

    destroyChart () {
      if (this.chart) {
        this.chart.dispose()
        this.chart = null
      }
    }
  }
}
</script>

<style lang="less" scoped>
.smd-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}
.smd-panel {
  width: 360px;
  max-width: 100vw;
  height: 100vh;
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  color: #ecf0f1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.4);
}
.smd-panel-bottom {
  width: 100vw;
  height: 65vh;
  position: fixed;
  bottom: 0;
  left: 0;
  border-radius: 16px 16px 0 0;
}

.smd-header {
  padding: 20px 20px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.smd-header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.smd-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.smd-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.smd-chapter-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(52, 152, 219, 0.2);
  color: #3498db;
  white-space: nowrap;
}
.smd-close {
  background: none;
  border: none;
  color: #8899aa;
  font-size: 24px;
  cursor: pointer;
  line-height: 1;
  padding: 0 4px;
  &:hover { color: #ecf0f1; }
}
.smd-mastery-ring-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 12px 0 8px;
}
.smd-mastery-label {
  font-size: 11px;
  color: #8899aa;
  margin-top: 4px;
}
.smd-desc {
  font-size: 12px;
  color: #8899aa;
  line-height: 1.5;
  margin-top: 8px;
}

.smd-body {
  flex: 1;
  padding: 0 20px 20px;
  overflow-y: auto;
}
.smd-section {
  margin-top: 20px;
}
.smd-section-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #bdc3c7;
}
.smd-empty-hint {
  font-size: 12px;
  color: #555;
}

/* 题目列表 */
.smd-problem-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
  &:hover { background: rgba(255, 255, 255, 0.04); }
  & + & { border-top: 1px solid rgba(255, 255, 255, 0.04); }
}
.smd-problem-id {
  font-size: 12px;
  color: #7f8c8d;
  min-width: 36px;
}
.smd-problem-title {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.smd-problem-status {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}
.smd-status-ac { background: rgba(39, 174, 96, 0.2); color: #27ae60; }
.smd-status-wa { background: rgba(231, 76, 60, 0.2); color: #e74c3c; }
.smd-status-none { background: rgba(127, 140, 141, 0.15); color: #7f8c8d; }

/* 前置依赖 */
.smd-prereq-row {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  &:hover { background: rgba(255, 255, 255, 0.04); }
  & + & { border-top: 1px solid rgba(255, 255, 255, 0.04); }
}
.smd-prereq-name {
  flex: 1;
  font-size: 13px;
}
.smd-prereq-mastery {
  font-size: 13px;
  font-weight: 600;
}
.smd-prereq-warn {
  width: 100%;
  font-size: 11px;
  color: #f39c12;
  margin-top: 4px;
}

/* 易错概念 */
.smd-misc-card {
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(231, 76, 60, 0.08);
  border: 1px solid rgba(231, 76, 60, 0.15);
  & + & { margin-top: 8px; }
}
.smd-misc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.smd-misc-name {
  font-size: 13px;
  font-weight: 500;
  color: #e74c3c;
}
.smd-misc-trigger {
  font-size: 11px;
  color: #8899aa;
}
.smd-misc-desc {
  font-size: 12px;
  color: #bdc3c7;
  margin-top: 6px;
  line-height: 1.5;
}
.smd-misc-hint {
  font-size: 12px;
  color: #f39c12;
  margin-top: 6px;
  line-height: 1.5;
}
.smd-misc-hint-icon {
  margin-right: 2px;
}

/* 趋势图 */
.smd-trend-chart {
  width: 100%;
  height: 140px;
}

/* 过渡动画 */
.smd-slide-enter-active,
.smd-slide-leave-active {
  transition: opacity 0.3s ease;
  .smd-panel {
    transition: transform 0.3s ease;
  }
}
.smd-slide-enter,
.smd-slide-leave-to {
  opacity: 0;
  .smd-panel {
    transform: translateX(100%);
  }
  .smd-panel-bottom {
    transform: translateY(100%);
  }
}
</style>

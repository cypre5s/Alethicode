<template>
  <div class="sr-container" ref="container">
    <div v-if="loading" class="sr-loading">
      <div class="sr-spinner"></div>
      <span>加载解题过程...</span>
    </div>

    <template v-else-if="riverData && !riverData.insufficient_data && riverData.submissions && riverData.submissions.length >= 2">
      <div class="sr-header">
        <span class="sr-title">📊 解题过程</span>
        <span class="sr-meta">{{ riverData.stats.total_submissions }} 次提交</span>
        <span class="sr-meta" v-if="riverData.stats.total_duration_seconds > 0">
          · {{ formatDuration(riverData.stats.total_duration_seconds) }}
        </span>
        <span class="sr-meta" v-if="riverData.stats.agent_interactions > 0">
          · AI 交互 {{ riverData.stats.agent_interactions }} 次
        </span>
      </div>

      <div class="sr-river-wrap" ref="riverWrap">
        <svg ref="svg" class="sr-svg"></svg>
      </div>

      <div class="sr-misc-track" v-if="riverData.misconception_events && riverData.misconception_events.length">
        <div
          v-for="(ev, idx) in riverData.misconception_events"
          :key="idx"
          class="sr-misc-item"
          :class="{ 'sr-misc-triggered': ev.type === 'triggered', 'sr-misc-resolved': ev.type === 'resolved' }"
        >
          <span class="sr-misc-icon">{{ ev.type === 'triggered' ? '🔴' : '✅' }}</span>
          <span class="sr-misc-name">{{ ev.misconception_name || '未知' }}</span>
          <span class="sr-misc-kc" v-if="ev.kc_name">{{ ev.kc_name }}</span>
          <span class="sr-misc-at">#{{ ev.submission_index + 1 }}</span>
        </div>
      </div>

      <div class="sr-narrative" v-if="riverData.narrative">
        <div class="sr-narrative-badge">AI 总结</div>
        <p class="sr-narrative-text">{{ riverData.narrative }}</p>
      </div>

      <SemanticDiffPanel
        v-if="selectedDiffIndex !== null"
        :diff="riverData.semantic_diffs[selectedDiffIndex]"
        :prevSubmission="riverData.submissions[selectedDiffIndex]"
        :currSubmission="riverData.submissions[selectedDiffIndex + 1]"
        @close="selectedDiffIndex = null"
      />

      <div
        v-show="tooltip.visible"
        class="sr-tooltip"
        :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
      >
        <div class="sr-tooltip-title">#{{ tooltip.attempt }} · {{ tooltip.label }}</div>
        <div class="sr-tooltip-row">{{ tooltip.time }}</div>
        <div class="sr-tooltip-row">代码行数：{{ tooltip.lines }}</div>
      </div>
    </template>

    <div v-else-if="riverData && riverData.insufficient_data" class="sr-empty">
      提交次数不足，至少需要 2 次提交才能展示解题过程。
    </div>
    <div v-else class="sr-empty">
      暂无可展示的解题过程数据。
    </div>
  </div>
</template>

<script>
import { select } from 'd3'
import SemanticDiffPanel from './SemanticDiffPanel'

const NODE_COLORS = {
  AC: '#27ae60',
  WA: '#e74c3c',
  RE: '#e67e22',
  TLE: '#9b59b6',
  MLE: '#9b59b6',
  CE: '#95a5a6',
  SE: '#95a5a6',
  Pending: '#3498db',
  Judging: '#3498db',
  PAC: '#f39c12'
}

export default {
  name: 'SubmissionRiver',
  components: { SemanticDiffPanel },
  props: {
    riverData: { type: Object, default: null },
    loading: { type: Boolean, default: false }
  },
  data () {
    return {
      selectedDiffIndex: null,
      tooltip: { visible: false, x: 0, y: 0, attempt: 0, label: '', time: '', lines: 0 }
    }
  },
  watch: {
    riverData: {
      handler () { this.$nextTick(() => this.renderRiver()) },
      deep: false
    }
  },
  mounted () {
    if (this.riverData && !this.riverData.insufficient_data) {
      this.$nextTick(() => this.renderRiver())
    }
  },
  methods: {
    formatDuration (seconds) {
      if (seconds >= 3600) return Math.floor(seconds / 3600) + '小时' + Math.floor((seconds % 3600) / 60) + '分钟'
      if (seconds >= 60) return Math.floor(seconds / 60) + '分钟'
      return seconds + '秒'
    },

    nodeColor (label) {
      return NODE_COLORS[label] || '#95a5a6'
    },

    renderRiver () {
      if (!this.$refs.svg || !this.riverData || this.riverData.insufficient_data) return
      const data = this.riverData
      const subs = data.submissions
      const phases = data.strategy_phases || []
      const btIdx = data.breakthrough_index

      const svgEl = this.$refs.svg
      const wrapEl = this.$refs.riverWrap
      const n = subs.length
      const nodeSpacing = Math.max(100, Math.min(160, (wrapEl.clientWidth - 80) / n))
      const totalW = Math.max(wrapEl.clientWidth, nodeSpacing * n + 80)
      const h = 200

      select(svgEl).selectAll('*').remove()
      svgEl.setAttribute('width', totalW)
      svgEl.setAttribute('height', h)

      const svg = select(svgEl)
      const defs = svg.append('defs')

      defs.append('filter')
        .attr('id', 'sr-glow')
        .append('feGaussianBlur')
        .attr('stdDeviation', '4')
        .attr('result', 'coloredBlur')
      defs.select('#sr-glow').append('feMerge')
        .selectAll('feMergeNode')
        .data(['coloredBlur', 'SourceGraphic'])
        .enter().append('feMergeNode')
        .attr('in', d => d)

      const y = h * 0.55
      const xStart = 40
      const xs = subs.map((_, i) => xStart + i * nodeSpacing)

      const phaseY = 28
      const phaseColors = {
        '探索期': 'rgba(52,152,219,0.12)',
        '结构搭建期': 'rgba(46,204,113,0.12)',
        '边界修复期': 'rgba(231,76,60,0.12)',
        '微调期': 'rgba(241,196,15,0.12)',
        '突破期': 'rgba(39,174,96,0.15)',
        '优化期': 'rgba(155,89,182,0.12)',
        '调试期': 'rgba(243,156,18,0.12)'
      }

      phases.forEach(p => {
        const x1 = xs[p.start_index] - 20
        const x2 = xs[p.end_index] + 20
        svg.append('rect')
          .attr('x', x1)
          .attr('y', phaseY)
          .attr('width', x2 - x1)
          .attr('height', y - phaseY + 20)
          .attr('rx', 10)
          .attr('fill', phaseColors[p.label] || 'rgba(200,200,200,0.08)')

        svg.append('text')
          .attr('x', (x1 + x2) / 2)
          .attr('y', phaseY + 14)
          .attr('text-anchor', 'middle')
          .attr('fill', '#8899aa')
          .attr('font-size', '11px')
          .text(p.label)
      })

      svg.append('line')
        .attr('x1', xs[0])
        .attr('y1', y)
        .attr('x2', xs[n - 1])
        .attr('y2', y)
        .attr('stroke', '#3a3a4a')
        .attr('stroke-width', 2)

      for (let i = 0; i < n - 1; i++) {
        svg.append('line')
          .attr('x1', xs[i])
          .attr('y1', y)
          .attr('x2', xs[i + 1])
          .attr('y2', y)
          .attr('stroke', '#555')
          .attr('stroke-width', 2)
      }

      const self = this

      subs.forEach((sub, i) => {
        const g = svg.append('g')
          .attr('transform', `translate(${xs[i]},${y})`)
          .style('cursor', 'pointer')
          .on('mouseover', function () {
            const rect = wrapEl.getBoundingClientRect()
            const cr = this.getBoundingClientRect()
            self.tooltip = {
              visible: true,
              x: cr.left - rect.left + cr.width / 2,
              y: cr.top - rect.top - 10,
              attempt: sub.attempt_number,
              label: sub.result_label,
              time: self.formatTime(sub.created_at),
              lines: sub.line_count
            }
          })
          .on('mouseout', () => { self.tooltip.visible = false })
          .on('click', () => {
            if (i > 0) self.selectedDiffIndex = i - 1
          })

        const isBt = (btIdx !== null && btIdx === i)
        const r = isBt ? 14 : 10

        if (isBt) {
          g.append('circle')
            .attr('r', r + 6)
            .attr('fill', 'none')
            .attr('stroke', '#f1c40f')
            .attr('stroke-width', 2)
            .style('filter', 'url(#sr-glow)')
            .attr('class', 'sr-bt-pulse')
        }

        g.append('circle')
          .attr('r', r)
          .attr('fill', self.nodeColor(sub.result_label))

        if (isBt) {
          g.append('text')
            .attr('y', 1)
            .attr('text-anchor', 'middle')
            .attr('dominant-baseline', 'central')
            .attr('fill', '#fff')
            .attr('font-size', '12px')
            .text('★')
        }

        g.append('text')
          .attr('y', 24)
          .attr('text-anchor', 'middle')
          .attr('fill', self.nodeColor(sub.result_label))
          .attr('font-size', '11px')
          .attr('font-weight', '600')
          .text(sub.result_label)

        g.append('text')
          .attr('y', 38)
          .attr('text-anchor', 'middle')
          .attr('fill', '#8899aa')
          .attr('font-size', '10px')
          .text('#' + sub.attempt_number)

        if (i > 0 && data.semantic_diffs[i - 1] && data.semantic_diffs[i - 1].agent_seen_between) {
          g.append('text')
            .attr('y', -22)
            .attr('text-anchor', 'middle')
            .attr('font-size', '12px')
            .text('💡')
        }
      })
    },

    formatTime (isoStr) {
      if (!isoStr) return ''
      const d = new Date(isoStr)
      return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
    }
  }
}
</script>

<style lang="less" scoped>
.sr-container {
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 12px;
  overflow: hidden;
  color: #ecf0f1;
}
.sr-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #8899aa;
  gap: 12px;
}
.sr-spinner {
  width: 24px;
  height: 24px;
  border: 3px solid #334;
  border-top-color: #3498db;
  border-radius: 50%;
  animation: sr-spin 0.8s linear infinite;
}
@keyframes sr-spin { to { transform: rotate(360deg); } }

.sr-header {
  padding: 14px 18px 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.sr-title {
  font-size: 15px;
  font-weight: 600;
}
.sr-meta {
  font-size: 12px;
  color: #8899aa;
}

.sr-river-wrap {
  overflow-x: auto;
  padding: 10px 0;
  position: relative;
}
.sr-svg {
  display: block;
  min-height: 200px;
}

.sr-tooltip {
  position: absolute;
  pointer-events: none;
  background: rgba(20, 20, 40, 0.95);
  border: 1px solid rgba(52, 152, 219, 0.4);
  border-radius: 8px;
  padding: 6px 10px;
  transform: translate(-50%, -100%);
  z-index: 10;
  min-width: 120px;
}
.sr-tooltip-title {
  font-size: 12px;
  font-weight: 600;
  color: #ecf0f1;
  margin-bottom: 2px;
}
.sr-tooltip-row {
  font-size: 11px;
  color: #bdc3c7;
}

.sr-misc-track {
  padding: 0 18px 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sr-misc-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
}
.sr-misc-triggered {
  background: rgba(231, 76, 60, 0.1);
  border: 1px solid rgba(231, 76, 60, 0.2);
}
.sr-misc-resolved {
  background: rgba(39, 174, 96, 0.1);
  border: 1px solid rgba(39, 174, 96, 0.2);
}
.sr-misc-icon { font-size: 10px; }
.sr-misc-name { color: #ecf0f1; font-weight: 500; }
.sr-misc-kc { color: #8899aa; }
.sr-misc-at { color: #7f8c8d; }

.sr-narrative {
  padding: 10px 18px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.sr-narrative-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(90deg, #2196f3, #4caf50);
  padding: 1px 8px;
  border-radius: 10px;
  margin-bottom: 6px;
}
.sr-narrative-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #bdc3c7;
}

.sr-empty {
  padding: 40px 20px;
  text-align: center;
  color: #8899aa;
  font-size: 13px;
}

:deep(.sr-bt-pulse ) {
  animation: sr-pulse 2s ease-out infinite;
}
@keyframes sr-pulse {
  0% { opacity: 0.8; }
  50% { opacity: 0.3; }
  100% { opacity: 0.8; }
}

@media (max-width: 1200px) {
  .sr-river-wrap { overflow-x: scroll; -webkit-overflow-scrolling: touch; }
}
</style>

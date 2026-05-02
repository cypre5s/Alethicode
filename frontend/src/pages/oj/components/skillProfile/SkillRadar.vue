<template>
  <div class="skill-radar">
    <div v-show="loading" class="radar-skeleton">
        <div class="skeleton-loading" style="width: 100%; height: 100%; border-radius: 50%;"></div>
    </div>
    <div ref="radarChart" class="radar-chart" :class="{ 'radar-chart-hidden': loading }"></div>
    <div v-show="dimTooltip.visible" class="dim-tooltip" :style="dimTooltip.style" v-html="dimTooltip.content"></div>
  </div>
</template>

<script>
import echarts from '@/utils/echarts'

export default {
  name: 'SkillRadar',
  props: {
    radarData: {
      type: Object,
      default: null
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      chart: null,
      dimTooltip: { visible: false, content: '', style: {} },
      _chartData: null
    }
  },
  watch: {
    radarData: {
      handler () {
        this.$nextTick(() => {
          this.renderChart()
        })
      },
      deep: true
    },
    loading (next) {
      if (!next) {
        this.$nextTick(() => {
          this.handleResize()
          this.renderChart()
        })
      }
    }
  },
  mounted () {
    if (this.radarData) {
      this.renderChart()
    }
    window.addEventListener('resize', this.handleResize)
  },
  beforeUnmount () {
    window.removeEventListener('resize', this.handleResize)
    const el = this.$refs.radarChart
    if (el && this._onMouseMove) {
      el.removeEventListener('mousemove', this._onMouseMove)
      el.removeEventListener('mouseleave', this._onMouseLeave)
    }
    if (this.chart) {
      this.chart.dispose()
    }
    this._tooltipBound = false
  },
  methods: {
    clamp01 (value) {
      const numberValue = Number(value)
      if (!Number.isFinite(numberValue)) return 0
      return Math.max(0, Math.min(1, numberValue))
    },
    sanitizeRadarPayload () {
      const data = this.radarData || {}
      const dimensions = Array.isArray(data.dimensions)
        ? data.dimensions.map(item => String(item == null ? '' : item).trim()).filter(Boolean)
        : []
      const valuesRaw = Array.isArray(data.values) ? data.values : []
      const rdRaw = Array.isArray(data.rd_values) ? data.rd_values : []
      const values = dimensions.map((_, index) => this.clamp01(valuesRaw[index]))
      const hasRd = rdRaw.length > 0
      const rdValues = hasRd ? dimensions.map((_, index) => this.clamp01(rdRaw[index])) : []
      const trends = data.trends && typeof data.trends === 'object' ? data.trends : {}
      const dataSources = Array.isArray(data.data_sources) ? data.data_sources : []
      const codeQuality = data.code_quality && typeof data.code_quality === 'object' ? data.code_quality : {}
      return { dimensions, values, rdValues, trends, dataSources, codeQuality }
    },
    renderChart () {
      if (!this.$refs.radarChart) return

      if (!this.chart) {
        this.chart = echarts.init(this.$refs.radarChart)
      }

      const { dimensions, values, rdValues, trends, dataSources, codeQuality } = this.sanitizeRadarPayload()
      if (!Array.isArray(dimensions) || dimensions.length === 0) {
        this.chart.clear()
        this._chartData = null
        this.dimTooltip.visible = false
        return
      }

      const trendIcons = { rising: ' ↑', declining: ' ↓', bottleneck: ' ⚠', plateau: '', new: '' }
      const indicator = dimensions.map(name => {
        const t = (trends || {})[name]
        const icon = t ? (trendIcons[t.label] || '') : ''
        return { name: name + icon, max: 1.0 }
      })
      const normalizedValues = values
      const normalizedRD = rdValues

      const dom = this.$refs.radarChart
      const hasLegend = normalizedRD.length > 0
      const legendSpace = hasLegend ? 45 : 0
      const cx = Math.round(dom.clientWidth / 2)
      const cy = Math.round((dom.clientHeight - legendSpace) / 2)

      this._chartData = { dimensions, normalizedValues, normalizedRD, trends: trends || {}, codeQuality, dataSources: dataSources || [], hasLegend }

      const option = {
        tooltip: { show: false },
        radar: {
          center: [cx, cy],
          radius: Math.min(cx, cy) - 35,
          indicator: indicator,
          shape: 'polygon',
          startAngle: 90,
          splitNumber: 4,
          name: {
            textStyle: {
              color: '#2c3e50',
              fontSize: 13,
              fontWeight: 500
            },
            formatter: (name) => {
              if (name.endsWith(' ↑')) return '{rising|' + name + '}'
              if (name.endsWith(' ↓')) return '{declining|' + name + '}'
              if (name.endsWith(' ⚠')) return '{bottleneck|' + name + '}'
              return name
            },
            rich: {
              rising: { color: '#4caf50', fontWeight: 600, fontSize: 13 },
              declining: { color: '#f44336', fontWeight: 600, fontSize: 13 },
              bottleneck: { color: '#ff9800', fontWeight: 600, fontSize: 13 }
            }
          },
          splitLine: { lineStyle: { color: '#e0e0e0' } },
          splitArea: {
            show: true,
            areaStyle: {
              color: ['rgba(33, 150, 243, 0.05)', 'rgba(33, 150, 243, 0.1)']
            }
          },
          axisLine: { lineStyle: { color: '#bdbdbd' } }
        },
        series: [
          {
            name: '能力值',
            type: 'radar',
            data: [
              {
                value: normalizedValues,
                name: '当前能力',
                areaStyle: { color: 'rgba(33, 150, 243, 0.2)' },
                lineStyle: { width: 3, color: '#2196f3' },
                itemStyle: { color: '#2196f3', borderColor: '#fff', borderWidth: 2 }
              }
            ],
            emphasis: { lineStyle: { width: 4 } }
          }
        ]
      }

      if (normalizedRD.length > 0) {
        option.series[0].data.push({
          value: normalizedRD,
          name: '置信度',
          areaStyle: { color: 'rgba(76, 175, 80, 0.15)' },
          lineStyle: { width: 2, color: '#4caf50', type: 'dashed' },
          itemStyle: { color: '#4caf50', borderColor: '#fff', borderWidth: 2 }
        })
        option.legend = {
          data: ['当前能力', '置信度'],
          bottom: 10,
          textStyle: { fontSize: 13 }
        }
      }

      this.chart.setOption(option)
      this._bindDimTooltip()
    },

    _bindDimTooltip () {
      if (this._tooltipBound) return
      this._tooltipBound = true

      const el = this.$refs.radarChart
      if (!el) return

      this._onMouseMove = (e) => {
        if (!this._chartData) return
        const dom = this.$refs.radarChart
        if (!dom) return
        const rect = dom.getBoundingClientRect()
        const mx = e.clientX - rect.left
        const my = e.clientY - rect.top
        const legendSpace = this._chartData.hasLegend ? 45 : 0
        const cx = dom.clientWidth / 2
        const cy = (dom.clientHeight - legendSpace) / 2
        const dx = mx - cx
        const dy = my - cy
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist < 15) {
          this.dimTooltip.visible = false
          return
        }

        const n = this._chartData.dimensions.length
        let mouseAngle = Math.atan2(-dx, -dy)
        if (mouseAngle < 0) mouseAngle += Math.PI * 2

        let bestIdx = 0
        let bestDiff = Infinity
        for (let i = 0; i < n; i++) {
          const axisAngle = (2 * Math.PI * i) / n
          let diff = Math.abs(mouseAngle - axisAngle)
          if (diff > Math.PI) diff = 2 * Math.PI - diff
          if (diff < bestDiff) {
            bestDiff = diff
            bestIdx = i
          }
        }

        this._showDimTooltip(bestIdx, mx, my)
      }

      this._onMouseLeave = () => {
        this.dimTooltip.visible = false
      }

      el.addEventListener('mousemove', this._onMouseMove)
      el.addEventListener('mouseleave', this._onMouseLeave)
    },

    _showDimTooltip (idx, x, y) {
      const { dimensions, normalizedValues, normalizedRD, trends, dataSources } = this._chartData
      const name = dimensions[idx]
      const mastery = normalizedValues[idx] || 0
      const masteryPct = (mastery * 100).toFixed(0)

      const sourceLabels = { kc: 'KC 追踪', elo: 'ELO 评分', submission: '提交记录', none: '暂无数据' }
      const src = (dataSources && dataSources[idx]) || 'none'
      const srcLabel = sourceLabels[src] || src

      let rows = `<strong>${name}</strong>`
      rows += ` <span style="color:#aaa;font-size:11px">[${srcLabel}]</span><br/>`
      rows += `<span style="color:#2196f3">●</span> 掌握度 <b>${masteryPct}%</b>`

      if (normalizedRD.length > idx) {
        const conf = (normalizedRD[idx] * 100).toFixed(0)
        rows += `<br/><span style="color:#4caf50">●</span> 置信度 <b>${conf}%</b>`
      }

      const cq = this._chartData.codeQuality[name]
      if (cq && cq.overall) {
        rows += `<br/><span style="color:#ff9800">●</span> 代码质量 <b>${cq.overall}</b>/100`
        if (cq.review_count) rows += ` <span style="color:#888;font-size:12px">(${cq.review_count}次评审)</span>`
      }

      const trend = trends[name]
      if (trend) {
        const labels = {
          rising: { text: '上升期 ↑', color: '#4caf50' },
          declining: { text: '下滑期 ↓', color: '#f44336' },
          bottleneck: { text: '瓶颈区 ⚠', color: '#ff9800' },
          plateau: { text: '平稳', color: '#9e9e9e' },
          new: { text: '数据不足', color: '#9e9e9e' }
        }
        const info = labels[trend.label] || labels.new
        rows += `<br/><span style="color:${info.color}">●</span> 趋势 <b style="color:${info.color}">${info.text}</b>`
        if (trend.recent_delta && trend.label !== 'new') {
          const delta = (trend.recent_delta * 100).toFixed(1)
          const sign = trend.recent_delta > 0 ? '+' : ''
          rows += ` <span style="color:#888;font-size:12px">(近期${sign}${delta}%)</span>`
        }
      }

      const dom = this.$refs.radarChart
      const w = dom ? dom.clientWidth : 500
      const h = dom ? dom.clientHeight : 400
      const tooltipW = 220
      const tooltipH = 120
      let tooltipX = x + 15
      let tooltipY = y - 10
      if (tooltipX + tooltipW > w) tooltipX = x - tooltipW - 10
      if (tooltipX < 5) tooltipX = 5
      if (tooltipY + tooltipH > h) tooltipY = h - tooltipH - 5
      if (tooltipY < 5) tooltipY = 5

      this.dimTooltip.content = rows
      this.dimTooltip.style = { left: tooltipX + 'px', top: tooltipY + 'px' }
      this.dimTooltip.visible = true
    },

    handleResize () {
      if (this.chart) {
        this.chart.resize()
        this.$nextTick(() => this.renderChart())
      }
    }
  }
}
</script>

<style lang="less" scoped>
.skill-radar {
  position: relative;
  width: 100%;
  height: 100%;

  .radar-chart {
    width: 100%;
    height: 400px;
  }
  .radar-chart-hidden {
    visibility: hidden;
  }

  .dim-tooltip {
    position: absolute;
    pointer-events: none;
    z-index: 10;
    padding: 8px 12px;
    background: rgba(255, 255, 255, 0.96);
    border: 1px solid #e0e0e0;
    border-radius: 6px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
    font-size: 13px;
    line-height: 1.6;
    color: #333;
    white-space: nowrap;
    transition: opacity 0.15s ease;
  }

  .radar-skeleton {
    width: 100%;
    height: 400px;
    padding: 40px;
    display: flex;
    justify-content: center;
    align-items: center;
    
    .skeleton-loading {
        width: 300px !important;
        height: 300px !important;
    }
  }
}
</style>

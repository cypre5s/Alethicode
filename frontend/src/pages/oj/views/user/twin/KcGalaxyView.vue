<template>
  <div class="kg-container" role="region" aria-label="KC 知识星系图">
    <div v-if="loading" class="kg-skeleton" aria-busy="true">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="error" class="kg-error" role="alert">
      <p>星系暂时连接不上，可能是网络问题</p>
      <button type="button" class="kg-retry-btn" @click="loadGalaxy">再试一次</button>
    </div>

    <div v-else-if="nodes.length === 0" class="kg-empty">
      <div class="kg-empty__icon" aria-hidden="true">
        <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
          <circle cx="32" cy="32" r="24" stroke="#0F4C81" stroke-width="2" fill="#E5EEF7"/>
          <circle cx="20" cy="24" r="3" fill="#0F4C81" opacity="0.3"/>
          <circle cx="40" cy="20" r="4" fill="#0F4C81" opacity="0.5"/>
          <circle cx="36" cy="40" r="3" fill="#0F4C81" opacity="0.4"/>
          <line x1="20" y1="24" x2="40" y2="20" stroke="#0F4C81" stroke-width="1" opacity="0.2"/>
          <line x1="40" y1="20" x2="36" y2="40" stroke="#0F4C81" stroke-width="1" opacity="0.2"/>
        </svg>
      </div>
      <p class="kg-empty__text">你的知识星系正在形成中</p>
      <router-link to="/problem" class="kg-empty__cta">做几道题点亮第一颗星 →</router-link>
    </div>

    <template v-else>
      <div ref="chartEl" class="kg-chart" role="img" aria-label="知识点关系图谱"></div>
      <KcDetailDrawer
        :visible="drawerVisible"
        :node="selectedNode"
        :all-nodes="nodes"
        :edges="edges"
        @close="drawerVisible = false"
        @focus-node="focusOnNode"
      />
    </template>
  </div>
</template>

<script>
import api from '@oj/api'
import KcDetailDrawer from './KcDetailDrawer.vue'

const MASTERY_COLORS = {
  low: '#6B7280',
  medium: '#F59E0B',
  high: '#0F4C81',
  mastered: '#10B981'
}

const EDGE_STYLES = {
  prerequisite: { type: 'solid' },
  related: { type: 'dashed' },
  applies_to: { type: 'dotted' }
}

function getMasteryColor (mastery) {
  if (mastery > 0.85) return MASTERY_COLORS.mastered
  if (mastery > 0.6) return MASTERY_COLORS.high
  if (mastery > 0.3) return MASTERY_COLORS.medium
  return MASTERY_COLORS.low
}

export default {
  name: 'KcGalaxyView',
  components: { KcDetailDrawer },
  data () {
    return {
      loading: false,
      error: false,
      nodes: [],
      edges: [],
      chartInstance: null,
      drawerVisible: false,
      selectedNode: null
    }
  },
  mounted () {
    this.loadGalaxy()
  },
  beforeUnmount () {
    if (this.chartInstance) {
      this.chartInstance.dispose()
      this.chartInstance = null
    }
  },
  methods: {
    async loadGalaxy () {
      this.loading = true
      this.error = false
      try {
        const res = await api.getTwinKcGalaxy({})
        this.nodes = res.data.data.nodes || []
        this.edges = res.data.data.edges || []
        if (this.nodes.length > 0) {
          this.$nextTick(() => this.renderChart())
        }
      } catch {
        this.error = true
        this.nodes = []
        this.edges = []
      } finally {
        this.loading = false
      }
    },

    async renderChart () {
      const echarts = await import('echarts/core')
      const { GraphChart } = await import('echarts/charts')
      const { TooltipComponent, LegendComponent } = await import('echarts/components')
      const { CanvasRenderer, SVGRenderer } = await import('echarts/renderers')

      echarts.use([GraphChart, TooltipComponent, LegendComponent,
        this.nodes.length > 100 ? CanvasRenderer : SVGRenderer])

      if (this.chartInstance) {
        this.chartInstance.dispose()
      }

      const chartEl = this.$refs.chartEl
      if (!chartEl) return

      this.chartInstance = echarts.init(chartEl, null, {
        renderer: this.nodes.length > 100 ? 'canvas' : 'svg'
      })

      const graphNodes = this.nodes.map(n => ({
        id: String(n.kc_id),
        name: n.name.length > 16 ? n.name.substring(0, 16) + '…' : n.name,
        fullName: n.name,
        symbolSize: Math.max(8, Math.min(32, n.mastery * 32)),
        itemStyle: {
          color: getMasteryColor(n.mastery),
          shadowBlur: n.recent_event_count > 0 ? 8 : 0,
          shadowColor: 'rgba(255,255,255,0.6)'
        },
        label: {
          show: n.mastery > 0.3 || this.nodes.length <= 30,
          fontSize: 11,
          color: '#374151'
        },
        value: n.mastery,
        _raw: n
      }))

      const graphEdges = this.edges.map(e => ({
        source: String(e.from_kc_id),
        target: String(e.to_kc_id),
        lineStyle: {
          width: Math.max(1, e.weight * 2),
          type: (EDGE_STYLES[e.relation_type] || EDGE_STYLES.related).type,
          color: '#E5E7EB'
        }
      }))

      this.chartInstance.setOption({
        tooltip: {
          trigger: 'item',
          formatter: (params) => {
            if (params.dataType === 'node' && params.data._raw) {
              const d = params.data._raw
              return `<b>${d.name}</b><br/>掌握度：${Math.round(d.mastery * 100)}%<br/>分类：${d.category || '未分类'}`
            }
            return ''
          }
        },
        series: [{
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          force: {
            repulsion: 80,
            gravity: 0.05,
            edgeLength: [60, 120],
            layoutAnimation: this.nodes.length <= 200
          },
          emphasis: {
            focus: 'adjacency',
            itemStyle: { shadowBlur: 10, shadowColor: 'rgba(15,76,129,0.3)' },
            lineStyle: { width: 3 }
          },
          data: graphNodes,
          links: graphEdges
        }]
      })

      this.chartInstance.on('click', (params) => {
        if (params.dataType === 'node' && params.data._raw) {
          this.selectedNode = params.data._raw
          this.drawerVisible = true
        }
      })

      window.addEventListener('resize', this.handleResize)
    },

    handleResize () {
      if (this.chartInstance) this.chartInstance.resize()
    },

    focusOnNode (kcId) {
      const node = this.nodes.find(n => n.kc_id === kcId)
      if (node) {
        this.selectedNode = node
      }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.kg-container {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-4;
  position: relative;
}

.kg-chart {
  width: 100%;
  height: 540px;
}

.kg-skeleton { padding: @l99-sp-6; }

.kg-error {
  text-align: center;
  padding: @l99-sp-10 0;
  color: @l99-neutral-700;
}

.kg-retry-btn {
  margin-top: @l99-sp-3;
  padding: @l99-sp-2 @l99-sp-4;
  border: 1px solid @l99-primary;
  border-radius: @l99-radius-sm;
  background: #fff;
  color: @l99-primary;
  font-size: @l99-fs-sm;
  cursor: pointer;
  &:hover { background: @l99-primary-soft; }
}

.kg-empty {
  text-align: center;
  padding: @l99-sp-10 0;
  &__text { font-size: @l99-fs-md; color: @l99-neutral-700; margin: @l99-sp-4 0 @l99-sp-3; }
  &__cta {
    display: inline-block;
    padding: @l99-sp-2 @l99-sp-5;
    background: @l99-primary;
    color: #fff;
    border-radius: @l99-radius-sm;
    font-size: @l99-fs-sm;
    text-decoration: none;
    &:hover { opacity: 0.9; }
  }
}

@media (max-width: 767px) {
  .kg-chart { height: 360px; }
}
</style>

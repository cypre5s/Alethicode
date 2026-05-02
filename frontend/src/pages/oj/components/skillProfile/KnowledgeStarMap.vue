<template>
  <div class="ksm-container" ref="container">
    <div v-if="loading" class="ksm-loading">
      <div class="ksm-spinner"></div>
      <span>加载知识星图...</span>
    </div>

    <template v-else-if="graphData && graphData.nodes && graphData.nodes.length">
      <!-- 顶部控制条 -->
      <div class="ksm-controls">
        <div class="ksm-chapters">
          <span
            class="ksm-chapter-tab"
            :class="{ active: activeChapter === null }"
            @click="activeChapter = null"
          >全部</span>
          <span
            v-for="ch in graphData.chapters"
            :key="ch.chapter"
            class="ksm-chapter-tab"
            :class="{ active: activeChapter === ch.chapter }"
            @click="activeChapter = ch.chapter"
          >{{ formatChapterDisplayName(ch.name || ch.chapter) }}</span>
        </div>
        <label class="ksm-toggle">
          <input type="checkbox" v-model="showRecommendedPath" />
          推荐路径
        </label>
      </div>

      <!-- 统计摘要 -->
      <div class="ksm-stats">
        <div class="ksm-stat-item">
          <span class="ksm-stat-num">{{ displayStats.total_kcs }}</span>
          <span class="ksm-stat-label">知识点</span>
        </div>
        <div class="ksm-stat-item ksm-stat-mastered">
          <span class="ksm-stat-num">{{ displayStats.mastered_count }}</span>
          <span class="ksm-stat-label">已掌握</span>
        </div>
        <div class="ksm-stat-item ksm-stat-weak">
          <span class="ksm-stat-num">{{ displayStats.weak_count }}</span>
          <span class="ksm-stat-label">薄弱</span>
        </div>
        <div class="ksm-stat-item ksm-stat-misc">
          <span class="ksm-stat-num">{{ displayStats.active_misconception_count }}</span>
          <span class="ksm-stat-label">易错点</span>
        </div>
      </div>

      <!-- SVG 画布 -->
      <svg ref="svg" class="ksm-svg"></svg>

      <!-- Tooltip -->
      <div
        v-show="tooltip.visible"
        class="ksm-tooltip"
        :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
      >
        <div class="ksm-tooltip-name">{{ tooltip.name }}</div>
        <div class="ksm-tooltip-desc" v-if="tooltip.desc">{{ tooltip.desc }}</div>
        <div class="ksm-tooltip-row">掌握度：{{ (tooltip.mastery * 100).toFixed(0) }}%</div>
        <div class="ksm-tooltip-row">题目数：{{ tooltip.problemCount }}</div>
        <div class="ksm-tooltip-row" v-if="tooltip.miscCount > 0" style="color:#e74c3c">
          易错点：{{ tooltip.miscCount }} 个
        </div>
        <div
          v-if="tooltip.reviewActions && tooltip.reviewActions.length"
          class="ksm-tooltip-actions"
        >
          <div class="ksm-tooltip-actions-title">推荐复习动作</div>
          <ul class="ksm-tooltip-actions-list">
            <li
              v-for="(action, ai) in tooltip.reviewActions"
              :key="ai"
              class="ksm-tooltip-action-item"
            >
              <div class="ksm-tooltip-action-label">{{ action.label }}</div>
              <div class="ksm-tooltip-action-hint" v-if="action.hint">{{ action.hint }}</div>
            </li>
          </ul>
        </div>
      </div>

      <!-- 时间旅行滑块 -->
      <div class="ksm-timeline" v-if="safeStats.learning_days > 1">
        <span class="ksm-timeline-label">第一天</span>
        <input
          type="range"
          class="ksm-timeline-slider"
          :min="0"
          :max="safeStats.learning_days - 1"
          v-model.number="timelineValue"
          @input="onTimelineInput"
          @change="onTimelineChange"
        />
        <span class="ksm-timeline-label">今天</span>
      </div>
    </template>

    <div v-else class="ksm-empty">
      <div class="ksm-empty-icon">🌌</div>
      <div class="ksm-empty-text">还没有知识点数据，开始做题后这里会显示你的学习宇宙</div>
    </div>
  </div>
</template>

<script>
import {
  forceSimulation, forceLink, forceManyBody, forceCenter,
  forceX, forceY, forceCollide,
  select, zoom, drag, event as d3event
} from 'd3'

export default {
  name: 'KnowledgeStarMap',
  props: {
    graphData: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    snapshotMastery: { type: Object, default: null },
    highlightKcIds: { type: Array, default: () => [] }
  },
  data () {
    return {
      activeChapter: null,
      showRecommendedPath: true,
      tooltip: { visible: false, x: 0, y: 0, name: '', desc: '', mastery: 0, problemCount: 0, miscCount: 0, reviewActions: [] },
      timelineValue: 0,
      timelineDebounceTimer: null,
      simulation: null,
      svgGroup: null
    }
  },
  computed: {
    safeStats () {
      const fallback = {
        total_kcs: 0,
        mastered_count: 0,
        weak_count: 0,
        active_misconception_count: 0,
        learning_days: 1
      }
      if (!this.graphData || !this.graphData.stats) return fallback
      return Object.assign({}, fallback, this.graphData.stats)
    },
    filteredNodesForStats () {
      if (!this.graphData || !Array.isArray(this.graphData.nodes)) return []
      const nodes = this.graphData.nodes
      if (this.activeChapter === null || typeof this.activeChapter === 'undefined') return nodes
      return nodes.filter(node => this.isNodeInChapter(node, this.activeChapter))
    },
    displayStats () {
      const fallback = Object.assign({}, this.safeStats)
      const nodes = this.filteredNodesForStats
      if (!nodes.length) {
        fallback.total_kcs = 0
        fallback.mastered_count = 0
        fallback.weak_count = 0
        fallback.active_misconception_count = 0
        return fallback
      }
      const masteredCount = nodes.filter(node => this.resolveNodeMastery(node) >= 0.7).length
      const weakCount = nodes.filter(node => this.resolveNodeMastery(node) < 0.3).length
      const misconceptionCount = nodes.reduce((sum, node) => {
        return sum + (Array.isArray(node.active_misconceptions) ? node.active_misconceptions.length : 0)
      }, 0)
      return {
        total_kcs: nodes.length,
        mastered_count: masteredCount,
        weak_count: weakCount,
        active_misconception_count: misconceptionCount,
        learning_days: this.safeStats.learning_days
      }
    }
  },
  watch: {
    graphData: {
      handler () { this.$nextTick(() => this.renderGraph()) },
      deep: false
    },
    activeChapter () { this.$nextTick(() => this.renderGraph()) },
    showRecommendedPath () { this.updateEdgeVisibility() },
    snapshotMastery () { this.updateNodeColors() }
  },
  mounted () {
    if (this.graphData) {
      this.timelineValue = (this.graphData.stats && this.graphData.stats.learning_days > 1)
        ? this.graphData.stats.learning_days - 1 : 0
      this.$nextTick(() => this.renderGraph())
    }
    window.addEventListener('resize', this.handleResize)
  },
  beforeUnmount () {
    if (this.simulation) this.simulation.stop()
    window.removeEventListener('resize', this.handleResize)
  },
  methods: {
    handleResize () { this.$nextTick(() => this.renderGraph()) },
    formatChapterDisplayName (value) {
      const raw = String(value == null ? '' : value).trim()
      if (!raw) return ''
      if (/^PPT\d+$/i.test(raw)) return raw.toUpperCase()
      const deduped = raw.replace(/^第第+/, '第')
      if (deduped.startsWith('第')) {
        return deduped.includes('章') ? deduped : `${deduped}章`
      }
      if (deduped.includes('章')) return deduped
      if (/^\d+$/.test(deduped) || /^[一二三四五六七八九十百千万零两]+$/.test(deduped)) {
        return `第${deduped}章`
      }
      return deduped
    },
    normalizeChapterKey (value) {
      return String(value == null ? '' : value).trim().toUpperCase()
    },
    isNodeInChapter (node, chapter) {
      return this.normalizeChapterKey(node && node.chapter) === this.normalizeChapterKey(chapter)
    },
    getEdgeEndpointId (endpoint) {
      if (endpoint && typeof endpoint === 'object') return endpoint.id
      return endpoint
    },
    resolveNodeMastery (node) {
      const nodeId = String(node && node.id != null ? node.id : '')
      if (this.snapshotMastery && Object.prototype.hasOwnProperty.call(this.snapshotMastery, nodeId)) {
        return Number(this.snapshotMastery[nodeId]) || 0
      }
      return Number(node && node.mastery != null ? node.mastery : 0) || 0
    },

    getFilteredData () {
      if (!this.graphData) return { nodes: [], edges: [] }
      let nodes = this.graphData.nodes
      let edges = this.graphData.edges
      if (this.activeChapter) {
        const ids = new Set(
          nodes
            .filter(n => this.isNodeInChapter(n, this.activeChapter))
            .map(n => String(n.id))
        )
        nodes = nodes.filter(n => ids.has(String(n.id)))
        edges = edges.filter(e => {
          const sourceId = String(this.getEdgeEndpointId(e.source))
          const targetId = String(this.getEdgeEndpointId(e.target))
          return ids.has(sourceId) && ids.has(targetId)
        })
      }
      const normalizedEdges = edges.map(e => ({
        ...e,
        source: this.getEdgeEndpointId(e.source),
        target: this.getEdgeEndpointId(e.target)
      }))
      return {
        nodes: nodes.map(n => ({ ...n })),
        edges: normalizedEdges
      }
    },

    getMasteryColor (mastery) {
      if (mastery >= 0.7) return '#3498db'
      if (mastery >= 0.5) return '#f39c12'
      if (mastery >= 0.3) return '#e74c3c'
      return '#3a3a4a'
    },
    getMasteryOpacity (mastery) {
      if (mastery >= 0.7) return 1.0
      if (mastery >= 0.5) return 0.8
      if (mastery >= 0.3) return 0.6
      return 0.4
    },
    getNodeRadius (problemCount) {
      return Math.max(12, Math.sqrt(problemCount || 1) * 10)
    },
    truncateName (name) {
      const maxLength = this.activeChapter ? 12 : 6
      return name && name.length > maxLength ? name.slice(0, maxLength) + '..' : name
    },

    renderGraph () {
      if (!this.$refs.svg || !this.graphData) return
      const svgEl = this.$refs.svg
      const container = this.$refs.container
      const width = container.clientWidth || 800
      const height = Math.max(400, Math.min(600, container.clientHeight - 160))

      select(svgEl).selectAll('*').remove()
      svgEl.setAttribute('width', width)
      svgEl.setAttribute('height', height)

      const svg = select(svgEl)
      const defs = svg.append('defs')

      defs.append('filter')
        .attr('id', 'glow')
        .append('feGaussianBlur')
        .attr('stdDeviation', '3')
        .attr('result', 'coloredBlur')

      defs.select('#glow').append('feMerge')
        .selectAll('feMergeNode')
        .data(['coloredBlur', 'SourceGraphic'])
        .enter().append('feMergeNode')
        .attr('in', d => d)

      defs.append('marker')
        .attr('id', 'arrowhead')
        .attr('viewBox', '0 -5 10 10')
        .attr('refX', 20)
        .attr('refY', 0)
        .attr('markerWidth', 6)
        .attr('markerHeight', 6)
        .attr('orient', 'auto')
        .append('path')
        .attr('d', 'M0,-5L10,0L0,5')
        .attr('fill', '#666')

      const g = svg.append('g')
      this.svgGroup = g

      const zoomBehavior = zoom()
        .scaleExtent([0.3, 3])
        .on('zoom', () => {
          g.attr('transform', d3event.transform)
        })
      svg.call(zoomBehavior)

      const { nodes, edges } = this.getFilteredData()
      if (!nodes.length) return

      const chapters = [...new Set(nodes.map(n => n.chapter).filter(Boolean))].sort()
      const chapterX = {}
      const segW = width / (chapters.length + 1)
      chapters.forEach((ch, i) => { chapterX[ch] = segW * (i + 1) })

      if (this.simulation) this.simulation.stop()

      const simulation = forceSimulation(nodes)
        .force('link', forceLink(edges).id(d => d.id).distance(80).strength(0.3))
        .force('charge', forceManyBody().strength(-200))
        .force('center', forceCenter(width / 2, height / 2))
        .force('x', forceX(d => chapterX[d.chapter] || width / 2).strength(0.15))
        .force('y', forceY(height / 2).strength(0.05))
        .force('collide', forceCollide(d => this.getNodeRadius(d.problem_count) + 8))
        .alphaDecay(0.05)
        .velocityDecay(0.3)

      for (let i = 0; i < 200; i++) {
        simulation.tick()
      }
      this.simulation = simulation

      // 全部视图不显示章节标题，避免顶端噪音；进入单章时再显示
      if (this.activeChapter) {
        g.selectAll('.ksm-chapter-label')
          .data(chapters)
          .enter().append('text')
          .attr('class', 'ksm-chapter-label')
          .attr('x', ch => chapterX[ch])
          .attr('y', 24)
          .attr('text-anchor', 'middle')
          .attr('fill', '#8899aa')
          .attr('font-size', '11px')
          .text(ch => {
            const info = (this.graphData.chapters || []).find(c => c.chapter === ch)
            return this.formatChapterDisplayName(info ? (info.name || info.chapter) : ch)
          })
      }

      const link = g.selectAll('.ksm-edge')
        .data(edges)
        .enter().append('line')
        .attr('class', d => 'ksm-edge' + (d.is_recommended_path ? ' ksm-edge-recommended' : ''))
        .attr('x1', d => d.source.x)
        .attr('y1', d => d.source.y)
        .attr('x2', d => d.target.x)
        .attr('y2', d => d.target.y)
        .attr('stroke', d => d.is_recommended_path ? '#00bcd4' : '#555')
        .attr('stroke-width', d => d.is_recommended_path ? 4 : 2)
        .attr('stroke-opacity', d => d.relation === 'related' ? 0.45 : 0.75)
        .attr('stroke-dasharray', d => d.relation === 'related' ? '4,3' : 'none')
        .attr('marker-end', d => d.relation === 'prerequisite' ? 'url(#arrowhead)' : '')
        .style('filter', d => d.is_recommended_path ? 'url(#glow)' : 'none')
        .style('display', d => {
          if (d.is_recommended_path && !this.showRecommendedPath) return 'none'
          return 'inline'
        })

      const self = this
      const nodeGroup = g.selectAll('.ksm-node')
        .data(nodes)
        .enter().append('g')
        .attr('class', 'ksm-node')
        .attr('transform', d => `translate(${d.x},${d.y})`)
        .call(drag()
          .on('start', d => { if (!d3event.active) simulation.alphaTarget(0.1).restart(); d.fx = d.x; d.fy = d.y })
          .on('drag', d => { d.fx = d3event.x; d.fy = d3event.y })
          .on('end', d => { if (!d3event.active) simulation.alphaTarget(0); d.fx = null; d.fy = null })
        )
        .on('mouseover', function (d) { self.showTooltip(d, this) })
        .on('mouseout', () => { self.tooltip.visible = false })
        .on('click', d => { self.$emit('node-click', d) })
        .on('dblclick', d => { self.$emit('node-dblclick', d) })

      const effectiveMastery = (d) => {
        return this.resolveNodeMastery(d)
      }

      // misconception pulse ring
      nodeGroup.filter(d => d.active_misconceptions && d.active_misconceptions.length > 0)
        .append('circle')
        .attr('class', 'ksm-pulse-ring')
        .attr('r', d => this.getNodeRadius(d.problem_count) + 6)
        .attr('fill', 'none')
        .attr('stroke', '#e74c3c')
        .attr('stroke-width', 2)

      // main circle
      nodeGroup.append('circle')
        .attr('class', 'ksm-node-circle')
        .attr('r', d => this.getNodeRadius(d.problem_count))
        .attr('fill', d => this.getMasteryColor(effectiveMastery(d)))
        .attr('fill-opacity', d => this.getMasteryOpacity(effectiveMastery(d)))
        .attr('stroke', d => effectiveMastery(d) >= 0.7 ? '#ecf0f1' : 'none')
        .attr('stroke-width', d => effectiveMastery(d) >= 0.7 ? 1.5 : 0)

      // glow for mastered nodes
      nodeGroup.filter(d => effectiveMastery(d) >= 0.7)
        .select('.ksm-node-circle')
        .classed('ksm-glow', true)

      // highlight current problem's KCs
      const hlSet = new Set(this.highlightKcIds.map(Number))
      if (hlSet.size > 0) {
        nodeGroup.filter(d => hlSet.has(d.kc_id || d.id))
          .append('circle')
          .attr('class', 'ksm-highlight-ring')
          .attr('r', d => this.getNodeRadius(d.problem_count) + 5)
          .attr('fill', 'none')
          .attr('stroke', '#fbbf24')
          .attr('stroke-width', 2.5)
          .attr('stroke-dasharray', '4 2')
      }

      // pulse animation for weak nodes
      nodeGroup.filter(d => effectiveMastery(d) < 0.35 && effectiveMastery(d) > 0)
        .append('circle')
        .attr('class', 'ksm-weak-pulse')
        .attr('r', d => this.getNodeRadius(d.problem_count) + 8)
        .attr('fill', 'none')
        .attr('stroke', '#ef4444')
        .attr('stroke-width', 1.5)
        .attr('opacity', 0.6)

      // label
      nodeGroup.append('text')
        .attr('class', 'ksm-node-label')
        .attr('text-anchor', 'middle')
        .attr('dy', '0.35em')
        .attr('fill', d => effectiveMastery(d) >= 0.3 ? '#fff' : '#aaa')
        .attr('font-size', '10px')
        .attr('pointer-events', 'none')
        .text(d => this.truncateName(d.name))

      // recommended badge
      nodeGroup.filter(d => d.is_recommended_next)
        .append('circle')
        .attr('cx', d => this.getNodeRadius(d.problem_count) - 3)
        .attr('cy', d => -(this.getNodeRadius(d.problem_count) - 3))
        .attr('r', 5)
        .attr('fill', '#00bcd4')

      simulation.on('tick', () => {
        link
          .attr('x1', d => d.source.x)
          .attr('y1', d => d.source.y)
          .attr('x2', d => d.target.x)
          .attr('y2', d => d.target.y)

        nodeGroup.attr('transform', d => `translate(${d.x},${d.y})`)
      })

      simulation.alphaTarget(0).restart()
    },

    showTooltip (d, el) {
      const rect = this.$refs.container.getBoundingClientRect()
      const nodeRect = el.getBoundingClientRect()
      const actions = Array.isArray(d.recommended_review_actions)
        ? d.recommended_review_actions
        : []
      this.tooltip = {
        visible: true,
        x: nodeRect.left - rect.left + nodeRect.width / 2,
        y: nodeRect.top - rect.top - 10,
        name: d.name,
        desc: d.description,
        mastery: this.resolveNodeMastery(d),
        problemCount: d.problem_count || 0,
        miscCount: d.active_misconceptions ? d.active_misconceptions.length : 0,
        reviewActions: actions
      }
    },

    updateEdgeVisibility () {
      if (!this.svgGroup) return
      const edges = this.svgGroup.selectAll('.ksm-edge-recommended')
      if (this.showRecommendedPath) {
        edges
          .style('display', 'inline')
          .style('filter', 'url(#glow)')
      } else {
        edges.style('display', 'none')
      }
    },

    updateNodeColors () {
      if (!this.svgGroup) return
      this.svgGroup.selectAll('.ksm-node-circle')
        .attr('fill', d => this.getMasteryColor(this.resolveNodeMastery(d)))
        .attr('fill-opacity', d => this.getMasteryOpacity(this.resolveNodeMastery(d)))
    },

    onTimelineInput () {
      clearTimeout(this.timelineDebounceTimer)
    },
    onTimelineChange () {
      clearTimeout(this.timelineDebounceTimer)
      this.timelineDebounceTimer = setTimeout(() => {
        const maxDays = this.safeStats.learning_days - 1
        if (this.timelineValue >= maxDays) {
          this.$emit('snapshot-request', null)
        } else {
          const d = new Date()
          d.setDate(d.getDate() - (maxDays - this.timelineValue))
          const dateStr = d.toISOString().slice(0, 10)
          this.$emit('snapshot-request', dateStr)
        }
      }, 500)
    }
  }
}
</script>

<style lang="less" scoped>
.ksm-container {
  position: relative;
  width: 100%;
  min-height: 400px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  border-radius: 12px;
  overflow: hidden;
}
.ksm-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: #8899aa;
  gap: 12px;
}
.ksm-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #334;
  border-top-color: #3498db;
  border-radius: 50%;
  animation: ksm-spin 0.8s linear infinite;
}
@keyframes ksm-spin { to { transform: rotate(360deg); } }

.ksm-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  gap: 8px;
  flex-wrap: wrap;
}
.ksm-chapters {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.ksm-chapter-tab {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  color: #8899aa;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { color: #ccc; background: rgba(255,255,255,0.05); }
  &.active { color: #fff; background: rgba(52,152,219,0.3); }
}
.ksm-toggle {
  font-size: 12px;
  color: #8899aa;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  input { margin: 0; }
}

.ksm-stats {
  display: flex;
  gap: 16px;
  padding: 0 16px 8px;
}
.ksm-stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ksm-stat-num {
  font-size: 20px;
  font-weight: 600;
  color: #ecf0f1;
}
.ksm-stat-label {
  font-size: 11px;
  color: #8899aa;
}
.ksm-stat-mastered .ksm-stat-num { color: #3498db; }
.ksm-stat-weak .ksm-stat-num { color: #e74c3c; }
.ksm-stat-misc .ksm-stat-num { color: #f39c12; }

.ksm-svg {
  width: 100%;
  display: block;
  cursor: grab;
  &:active { cursor: grabbing; }
}

.ksm-tooltip {
  position: absolute;
  pointer-events: none;
  background: rgba(20, 20, 40, 0.95);
  border: 1px solid rgba(52, 152, 219, 0.4);
  border-radius: 8px;
  padding: 8px 12px;
  transform: translate(-50%, -100%);
  z-index: 10;
  min-width: 140px;
}
.ksm-tooltip-name {
  font-size: 13px;
  font-weight: 600;
  color: #ecf0f1;
  margin-bottom: 4px;
}
.ksm-tooltip-desc {
  font-size: 11px;
  color: #8899aa;
  margin-bottom: 4px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ksm-tooltip-row {
  font-size: 11px;
  color: #bdc3c7;
}

.ksm-tooltip-actions {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.ksm-tooltip-actions-title {
  font-size: 11px;
  font-weight: 600;
  color: #f1c40f;
  margin-bottom: 4px;
}
.ksm-tooltip-actions-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.ksm-tooltip-action-item {
  margin: 0 0 4px 0;
}
.ksm-tooltip-action-item:last-child {
  margin-bottom: 0;
}
.ksm-tooltip-action-label {
  font-size: 11px;
  color: #ecf0f1;
  line-height: 1.35;
}
.ksm-tooltip-action-hint {
  font-size: 10px;
  color: #8899aa;
  line-height: 1.3;
  margin-top: 1px;
}

.ksm-timeline {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 12px;
}
.ksm-timeline-label {
  font-size: 11px;
  color: #8899aa;
  white-space: nowrap;
}
.ksm-timeline-slider {
  flex: 1;
  -webkit-appearance: none;
  height: 4px;
  background: linear-gradient(90deg, #3a3a4a, #3498db);
  border-radius: 2px;
  outline: none;
  &::-webkit-slider-thumb {
    -webkit-appearance: none;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #3498db;
    cursor: pointer;
    border: 2px solid #ecf0f1;
  }
}

.ksm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: #8899aa;
  gap: 12px;
}
.ksm-empty-icon { font-size: 48px; }
.ksm-empty-text { font-size: 14px; text-align: center; max-width: 280px; }

// D3 node animations
:deep(.ksm-glow ) {
  animation: ksm-breathe 3s ease-in-out infinite;
}
@keyframes ksm-breathe {
  0%, 100% { filter: drop-shadow(0 0 4px rgba(52,152,219,0.4)); }
  50% { filter: drop-shadow(0 0 10px rgba(52,152,219,0.8)); }
}
:deep(.ksm-pulse-ring ) {
  animation: ksm-pulse 2s ease-out infinite;
}
@keyframes ksm-pulse {
  0% { r: attr(r); opacity: 0.8; }
  100% { opacity: 0; }
}

:deep(.ksm-weak-pulse) {
  animation: ksm-weak-throb 1.8s ease-in-out infinite;
}
@keyframes ksm-weak-throb {
  0%, 100% { opacity: 0; transform: scale(0.95); }
  50% { opacity: 0.6; transform: scale(1.15); }
}
:deep(.ksm-highlight-ring) {
  animation: ksm-highlight-spin 8s linear infinite;
  transform-origin: center;
}
@keyframes ksm-highlight-spin {
  0% { stroke-dashoffset: 0; }
  100% { stroke-dashoffset: 24; }
}

@media (max-width: 1200px) {
  .ksm-container { min-height: 350px; }
  .ksm-controls { padding: 8px 10px; }
  .ksm-stats { gap: 10px; padding: 0 10px 6px; }
}
</style>

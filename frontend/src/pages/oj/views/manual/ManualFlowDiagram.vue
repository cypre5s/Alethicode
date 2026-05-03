<template>
  <div class="manual-flow" :class="{ 'is-revealed': revealed, 'is-rough': roughMode }" ref="rootRef">
    <svg
      class="manual-flow__svg"
      :viewBox="`0 0 ${svgW} ${svgH}`"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
      ref="svgRef"
      style="position:absolute;inset:0;width:100%;height:100%;pointer-events:none;z-index:1"
    >
      <defs>
        <linearGradient id="manual-flow-grad" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="#6366f1"/>
          <stop offset="100%" stop-color="#ec4899"/>
        </linearGradient>
        <linearGradient id="manual-flow-grad-v" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#a855f7"/>
          <stop offset="100%" stop-color="#ec4899"/>
        </linearGradient>
      </defs>
      <template v-if="!roughMode">
        <path
          v-for="(seg, idx) in segments"
          :key="`seg-${idx}`"
          :d="seg.d"
          class="manual-flow__path"
          :style="{ animationDelay: `${idx * 160}ms` }"
          :stroke="seg.vertical ? 'url(#manual-flow-grad-v)' : 'url(#manual-flow-grad)'"
        />
      </template>
    </svg>
    <ol class="manual-flow__nodes" style="position:absolute;inset:0;z-index:5">
      <li
        v-for="(node, idx) in nodes"
        :key="node.id"
        class="manual-flow__node"
        :class="{ 'is-highlight': node.highlight }"
        :style="{ ...nodePosition(idx), zIndex: 10 }"
      >
        <button type="button" class="manual-flow__node-btn" @click="$emit('jump', node.target)" style="position:relative;z-index:10">
          <span class="manual-flow__index">{{ idx + 1 }}</span>
          <span class="manual-flow__title">{{ node.title }}</span>
        </button>
      </li>
    </ol>
  </div>
</template>

<script>
import { FLOW_NODES } from './manualContent.js'
import rough from 'roughjs'

const SVG_W = 920
const SVG_H = 380
const COLS = 4
const X_START = 80
const Y_ROW0 = 70
const Y_ROW1 = 300
const X_STEP = 240
const CURVE_OFFSET = 60

export default {
  name: 'ManualFlowDiagram',
  data () {
    return {
      nodes: FLOW_NODES,
      revealed: false,
      roughMode: false,
      observer: null,
      svgW: SVG_W,
      svgH: SVG_H,
      roughGroup: null
    }
  },
  computed: {
    segments () {
      const out = []
      for (let i = 0; i < this.nodes.length - 1; i += 1) {
        const from = this.coord(i)
        const to = this.coord(i + 1)
        out.push({ d: this.buildPath(from, to), vertical: from.row !== to.row })
      }
      return out
    }
  },
  watch: {
    roughMode () {
      if (this.roughMode && this.revealed) {
        this.$nextTick(() => this.drawRough())
      } else {
        this.clearRough()
      }
    },
    revealed (val) {
      if (val && this.roughMode) {
        this.$nextTick(() => this.drawRough())
      }
    }
  },
  mounted () {
    if (typeof IntersectionObserver === 'undefined') {
      this.revealed = true
      return
    }
    this.observer = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          this.revealed = true
          this.observer.disconnect()
          break
        }
      }
    }, { threshold: 0.18 })
    this.observer.observe(this.$refs.rootRef)
  },
  beforeUnmount () {
    if (this.observer) this.observer.disconnect()
  },
  methods: {
    coord (idx) {
      const row = Math.floor(idx / COLS)
      const col = row % 2 === 0 ? idx % COLS : (COLS - 1 - idx % COLS)
      const y = row === 0 ? Y_ROW0 : Y_ROW1
      return { x: X_START + col * X_STEP, y, row, col }
    },
    buildPath (from, to) {
      const BTN_HALF = 70
      if (from.row !== to.row) {
        const startY = from.y + 22
        const endY = to.y - 22
        const midX = from.x + 30
        return `M ${from.x} ${startY} C ${midX} ${(startY + endY) / 2}, ${midX} ${(startY + endY) / 2}, ${to.x} ${endY}`
      }
      const dir = to.x > from.x ? 1 : -1
      const startX = from.x + dir * BTN_HALF
      const endX = to.x - dir * BTN_HALF
      const cy = from.y - CURVE_OFFSET
      const cx = (startX + endX) / 2
      return `M ${startX} ${from.y} Q ${cx} ${cy} ${endX} ${to.y}`
    },
    nodePosition (idx) {
      const c = this.coord(idx)
      return {
        '--manual-flow-x': `${(c.x / SVG_W) * 100}%`,
        '--manual-flow-y': `${(c.y / SVG_H) * 100}%`
      }
    },
    toggleRough () {
      this.roughMode = !this.roughMode
    },
    drawRough () {
      const svgEl = this.$refs.svgRef
      if (!svgEl) return
      this.clearRough()

      const rc = rough.svg(svgEl)
      const g = document.createElementNS('http://www.w3.org/2000/svg', 'g')
      g.setAttribute('class', 'manual-flow__rough-group')

      const colors = ['#6366f1', '#8b5cf6', '#a855f7', '#d946ef', '#ec4899', '#f43f5e', '#ef4444']

      for (let i = 0; i < this.nodes.length - 1; i++) {
        const from = this.coord(i)
        const to = this.coord(i + 1)
        const d = this.buildPath(from, to)
        const color = colors[i % colors.length]
        const node = rc.path(d, {
          stroke: color,
          strokeWidth: 2.5,
          roughness: 1.8,
          bowing: 2
        })
        g.appendChild(node)
      }

      for (let i = 0; i < this.nodes.length; i++) {
        const c = this.coord(i)
        const isHighlight = this.nodes[i].highlight
        const circleNode = rc.circle(c.x, c.y, 42, {
          stroke: isHighlight ? '#ec4899' : '#6366f1',
          strokeWidth: isHighlight ? 2.5 : 1.5,
          roughness: 1.5,
          fill: isHighlight ? 'rgba(236, 72, 153, 0.08)' : 'rgba(99, 102, 241, 0.05)',
          fillStyle: 'solid'
        })
        g.appendChild(circleNode)
      }

      svgEl.appendChild(g)
      this.roughGroup = g
    },
    clearRough () {
      if (this.roughGroup && this.roughGroup.parentNode) {
        this.roughGroup.parentNode.removeChild(this.roughGroup)
      }
      this.roughGroup = null
    }
  }
}
</script>

<style lang="less" scoped>
.manual-flow {
  position: relative;
  width: 100%;
  aspect-ratio: 920 / 380;
  background: var(--bg-warm);
  border-radius: var(--radius-lg);
  padding: 0;
  border: 1px solid var(--border-warm);
  overflow: hidden;
  isolation: isolate;
}

.manual-flow__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.manual-flow__path {
  fill: none;
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-dasharray: 500;
  stroke-dashoffset: 500;
  opacity: 0;
}

.is-revealed .manual-flow__path {
  animation: manual-flow-draw 1100ms cubic-bezier(0.65, 0, 0.35, 1) forwards;
}

@keyframes manual-flow-draw {
  0% { stroke-dashoffset: 500; opacity: 0; }
  20% { opacity: 1; }
  100% { stroke-dashoffset: 0; opacity: 0.75; }
}

.manual-flow__nodes {
  list-style: none;
  margin: 0;
  padding: 0;
  position: absolute;
  inset: 0;
  z-index: 5;
}

.manual-flow__node {
  position: absolute;
  left: var(--manual-flow-x);
  top: var(--manual-flow-y);
  transform: translate(-50%, -50%);
  z-index: 10;
}

.manual-flow__node-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border-default);
  background: #fff;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06), 0 0 0 3px rgba(255, 255, 255, 0.8);
  transition: transform 0.18s ease, color 0.18s ease, border-color 0.18s ease, background 0.18s ease;
  white-space: nowrap;
  position: relative;
  z-index: 10;

  &:hover, &:focus-visible {
    color: #fff;
    background: var(--warm-grad-primary);
    border-color: transparent;
    transform: translateY(-2px);
    outline: none;
  }
}

.manual-flow__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--warm-grad-primary);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
}

.manual-flow__node.is-highlight .manual-flow__node-btn {
  border-color: var(--warm-accent);
  color: var(--warm-accent);
  background: #fff;
  box-shadow: 0 2px 8px rgba(236, 72, 153, 0.12), 0 0 0 3px rgba(255, 255, 255, 0.9);
}

.manual-flow.is-rough .manual-flow__path {
  display: none;
}

@media (max-width: 768px) {
  .manual-flow { aspect-ratio: 1 / 1.5; }
  .manual-flow__node-btn { font-size: 11px; padding: 6px 10px; }
  .manual-flow__index { width: 18px; height: 18px; font-size: 10px; }
  .manual-flow__style-toggle { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .is-revealed .manual-flow__path { animation: none; stroke-dashoffset: 0; opacity: 0.7; }
}
</style>

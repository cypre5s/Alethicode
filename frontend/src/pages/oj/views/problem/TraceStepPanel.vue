<template>
  <div class="trace-panel">
    <div class="trace-header">
      <div class="trace-step-indicator">
        <span class="trace-step-label">步骤</span>
        <span class="trace-step-num">{{ currentStep + 1 }}</span>
        <span class="trace-step-sep">/</span>
        <span class="trace-step-total">{{ totalSteps }}</span>
      </div>
      <div class="trace-controls">
        <button
          class="trace-btn"
          :disabled="currentStep <= 0"
          @click="goFirst"
          aria-label="跳到第一步"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="11 17 6 12 11 7"/>
            <line x1="7" y1="12" x2="18" y2="12"/>
            <line x1="6" y1="5" x2="6" y2="19"/>
          </svg>
        </button>
        <button
          class="trace-btn"
          :disabled="currentStep <= 0"
          @click="goPrev"
          aria-label="上一步"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </button>
        <button
          class="trace-btn"
          :disabled="currentStep >= totalSteps - 1"
          @click="goNext"
          aria-label="下一步"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </button>
        <button
          class="trace-btn"
          :disabled="currentStep >= totalSteps - 1"
          @click="goLast"
          aria-label="跳到最后一步"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="13 17 18 12 13 7"/>
            <line x1="6" y1="12" x2="17" y2="12"/>
            <line x1="18" y1="5" x2="18" y2="19"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="trace-slider-row">
      <input
        type="range"
        class="trace-slider"
        :min="0"
        :max="totalSteps - 1"
        :value="currentStep"
        @input="onSlider($event.target.value)"
      />
      <div class="trace-slider-marks">
        <span
          v-for="mark in criticalSteps"
          :key="mark"
          class="trace-slider-mark"
          :style="{ left: ((mark / Math.max(totalSteps - 1, 1)) * 100) + '%' }"
          :title="'关键步骤 ' + (mark + 1)"
        ></span>
      </div>
    </div>

    <div class="trace-body">
      <div class="trace-code-pane">
        <div class="trace-pane-title">代码</div>
        <div class="trace-code-view" ref="codeViewRef">
          <div
            v-for="(line, idx) in codeLines"
            :key="idx"
            :class="['trace-code-line', { 'trace-code-line-active': idx + 1 === currentLineNo }]"
            :ref="idx + 1 === currentLineNo ? 'activeLine' : undefined"
          >
            <span class="trace-line-no">{{ idx + 1 }}</span>
            <pre class="trace-line-code">{{ line }}</pre>
          </div>
        </div>
      </div>

      <div class="trace-vars-pane">
        <div class="trace-pane-title">变量状态</div>
        <div class="trace-vars-table-wrap">
          <table class="trace-vars-table" v-if="currentLocals && Object.keys(currentLocals).length">
            <thead>
              <tr>
                <th>变量名</th>
                <th>值</th>
                <th>类型</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="v in sortedVars"
                :key="v.name"
                :class="{ 'trace-var-changed': v.changed }"
              >
                <td class="trace-var-name">
                  <span v-if="v.changed" class="trace-var-arrow">→</span>
                  {{ v.name }}
                </td>
                <td class="trace-var-value">{{ v.value }}</td>
                <td class="trace-var-type">{{ v.type }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="trace-vars-empty">此步骤暂无变量</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TraceStepPanel',
  props: {
    traceData: {
      type: Array,
      required: true
    },
    code: {
      type: String,
      default: ''
    },
    criticalSteps: {
      type: Array,
      default: () => []
    }
  },
  data () {
    return {
      currentStep: 0
    }
  },
  computed: {
    totalSteps () {
      return this.traceData ? this.traceData.length : 0
    },
    currentEntry () {
      return this.traceData && this.traceData[this.currentStep]
    },
    currentLineNo () {
      return this.currentEntry ? this.currentEntry.line : -1
    },
    currentLocals () {
      return this.currentEntry ? (this.currentEntry.locals || {}) : {}
    },
    previousLocals () {
      if (this.currentStep <= 0) return {}
      const prev = this.traceData[this.currentStep - 1]
      return prev ? (prev.locals || {}) : {}
    },
    sortedVars () {
      const curr = this.currentLocals
      const prev = this.previousLocals
      return Object.keys(curr).sort().map(name => {
        const val = String(curr[name])
        const changed = !(name in prev) || String(prev[name]) !== val
        return {
          name,
          value: val,
          type: this._guessType(val),
          changed
        }
      })
    },
    codeLines () {
      return this.code ? this.code.split('\n') : []
    }
  },
  watch: {
    currentStep () {
      this.$nextTick(() => {
        const el = this.$refs.activeLine
        if (el) {
          const target = Array.isArray(el) ? el[0] : el
          if (target && target.scrollIntoView) {
            target.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
          }
        }
      })
    }
  },
  methods: {
    goFirst () { this.currentStep = 0 },
    goPrev () { if (this.currentStep > 0) this.currentStep-- },
    goNext () { if (this.currentStep < this.totalSteps - 1) this.currentStep++ },
    goLast () { this.currentStep = this.totalSteps - 1 },
    onSlider (val) { this.currentStep = parseInt(val, 10) },
    jumpToStep (step) {
      if (step >= 0 && step < this.totalSteps) {
        this.currentStep = step
      }
    },
    _guessType (val) {
      if (val === 'None') return 'NoneType'
      if (val === 'True' || val === 'False') return 'bool'
      if (/^-?\d+$/.test(val)) return 'int'
      if (/^-?\d+\.\d+$/.test(val)) return 'float'
      if (/^['"]/.test(val)) return 'str'
      if (/^\[/.test(val)) return 'list'
      if (/^\{/.test(val)) return 'dict'
      if (/^\(/.test(val)) return 'tuple'
      return ''
    }
  }
}
</script>

<style lang="less" scoped>
.trace-panel {
  background: var(--bg-card);
  border: 0.5px solid var(--border-color);
  border-radius: var(--border-radius-md, 10px);
  overflow: hidden;
  font-size: 13px;
}

.trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 0.5px solid var(--border-color);
  background: rgba(99, 102, 241, 0.05);
}
.trace-step-indicator {
  display: flex;
  align-items: baseline;
  gap: 3px;
  font-size: 12px;
  color: var(--text-secondary);
}
.trace-step-label {
  font-weight: 500;
}
.trace-step-num {
  font-weight: 700;
  color: var(--primary-color, #6366f1);
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}
.trace-step-sep { color: var(--text-disabled, #ccc); }
.trace-step-total { font-variant-numeric: tabular-nums; }

.trace-controls {
  display: flex;
  gap: 4px;
}
.trace-btn {
  width: 30px;
  height: 30px;
  border: 0.5px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: all 0.15s;
  &:hover:not(:disabled) {
    background: rgba(99, 102, 241, 0.08);
    color: var(--primary-color, #6366f1);
    border-color: rgba(99, 102, 241, 0.3);
  }
  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }
}

.trace-slider-row {
  position: relative;
  padding: 8px 14px;
  border-bottom: 0.5px solid var(--border-color);
}
.trace-slider {
  width: 100%;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: var(--border-color);
  border-radius: 2px;
  outline: none;
  &::-webkit-slider-thumb {
    -webkit-appearance: none;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--primary-color, #6366f1);
    cursor: pointer;
    border: 2px solid #fff;
    box-shadow: 0 1px 3px rgba(0,0,0,0.15);
  }
  &::-moz-range-thumb {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--primary-color, #6366f1);
    cursor: pointer;
    border: 2px solid #fff;
    box-shadow: 0 1px 3px rgba(0,0,0,0.15);
  }
}
.trace-slider-marks {
  position: absolute;
  top: 8px;
  left: 14px;
  right: 14px;
  height: 4px;
  pointer-events: none;
}
.trace-slider-mark {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--danger-color, #ef4444);
  top: -1px;
  transform: translateX(-50%);
}

.trace-body {
  display: flex;
  height: 340px;
  overflow: hidden;
}

.trace-code-pane {
  flex: 1;
  min-width: 0;
  border-right: 0.5px solid var(--border-color);
  display: flex;
  flex-direction: column;
}
.trace-vars-pane {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.trace-pane-title {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-disabled, #999);
  padding: 8px 12px 6px;
  border-bottom: 0.5px solid var(--border-color);
}

.trace-code-view {
  overflow-y: auto;
  flex: 1;
  padding: 4px 0;
}
.trace-code-line {
  display: flex;
  align-items: stretch;
  line-height: 22px;
  padding: 0 12px;
  transition: background 0.12s;
}
.trace-code-line-active {
  background: rgba(250, 204, 21, 0.18);
}
.trace-line-no {
  width: 32px;
  flex-shrink: 0;
  text-align: right;
  padding-right: 10px;
  font-size: 11px;
  color: var(--text-disabled, #999);
  font-variant-numeric: tabular-nums;
  user-select: none;
}
.trace-line-code {
  margin: 0;
  font-family: 'JetBrains Mono', 'Fira Code', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  white-space: pre;
  color: var(--text-primary);
  line-height: 22px;
}

.trace-vars-table-wrap {
  overflow-y: auto;
  flex: 1;
  padding: 4px 0;
}
.trace-vars-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  th, td {
    text-align: left;
    padding: 5px 10px;
    border-bottom: 0.5px solid var(--border-color);
  }
  th {
    font-size: 10px;
    font-weight: 600;
    color: var(--text-disabled, #999);
    text-transform: uppercase;
    letter-spacing: 0.3px;
    position: sticky;
    top: 0;
    background: var(--bg-card);
    z-index: 1;
  }
}
.trace-var-name {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}
.trace-var-value {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: var(--primary-color, #6366f1);
  word-break: break-all;
  max-width: 140px;
}
.trace-var-type {
  font-size: 10px;
  color: var(--text-disabled, #999);
  white-space: nowrap;
}
.trace-var-changed {
  background: rgba(16, 185, 129, 0.08);
  .trace-var-value {
    color: var(--success-color, #10b981);
    font-weight: 600;
  }
}
.trace-var-arrow {
  color: var(--success-color, #10b981);
  font-weight: 700;
  margin-right: 3px;
}
.trace-vars-empty {
  padding: 20px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--text-disabled, #999);
}
</style>

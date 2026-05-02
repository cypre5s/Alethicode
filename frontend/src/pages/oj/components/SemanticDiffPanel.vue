<template>
  <transition name="sdp-expand">
    <div v-if="diff" class="sdp-panel">
      <div class="sdp-header">
        <div class="sdp-summary">{{ diff.summary }}</div>
        <div class="sdp-tags">
          <span
            v-for="(change, idx) in diff.structural_changes"
            :key="idx"
            class="sdp-tag"
            :class="tagClass(change)"
          >{{ tagLabel(change) }}</span>
          <span v-if="diff.agent_seen_between" class="sdp-tag sdp-tag-agent">💡 AI 提示后修改</span>
        </div>
        <button class="sdp-close" @click="$emit('close')">&times;</button>
      </div>

      <div class="sdp-diff-area">
        <div class="sdp-code-col">
          <div class="sdp-col-label">
            #{{ prevSubmission.attempt_number }} · {{ prevSubmission.result_label }}
          </div>
          <div class="sdp-code" ref="prevCode">
            <div
              v-for="(line, i) in prevLines"
              :key="'p' + i"
              class="sdp-line"
              :class="{ 'sdp-line-del': deletedLineNums.has(i) }"
            >
              <span class="sdp-linenum">{{ i + 1 }}</span>
              <span class="sdp-linetext">{{ line }}</span>
            </div>
          </div>
        </div>
        <div class="sdp-code-col">
          <div class="sdp-col-label">
            #{{ currSubmission.attempt_number }} · {{ currSubmission.result_label }}
          </div>
          <div class="sdp-code" ref="currCode">
            <div
              v-for="(line, i) in currLines"
              :key="'c' + i"
              class="sdp-line"
              :class="{ 'sdp-line-add': addedLineNums.has(i) }"
            >
              <span class="sdp-linenum">{{ i + 1 }}</span>
              <span class="sdp-linetext">{{ line }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="sdp-stats">
        <span class="sdp-stat-add">+{{ diff.lines_added }}</span>
        <span class="sdp-stat-del">-{{ diff.lines_deleted }}</span>
        <span class="sdp-stat-ratio">变化率 {{ (diff.diff_ratio * 100).toFixed(0) }}%</span>
      </div>
    </div>
  </transition>
</template>

<script>
const CHANGE_LABELS = {
  added_loop: '加入循环',
  added_condition: '加入条件判断',
  added_function_def: '提取函数',
  added_type_conversion: '加入类型转换',
  fixed_boundary: '修复循环边界',
  added_error_handling: '加入异常处理',
  refactored_structure: '代码重构',
  minor_change: '细节调整'
}

const CHANGE_COLORS = {
  added_loop: 'sdp-tag-struct',
  added_condition: 'sdp-tag-struct',
  added_function_def: 'sdp-tag-struct',
  added_type_conversion: 'sdp-tag-fix',
  fixed_boundary: 'sdp-tag-fix',
  added_error_handling: 'sdp-tag-struct',
  refactored_structure: 'sdp-tag-refactor',
  minor_change: 'sdp-tag-minor'
}

export default {
  name: 'SemanticDiffPanel',
  props: {
    diff: { type: Object, default: null },
    prevSubmission: { type: Object, default: null },
    currSubmission: { type: Object, default: null }
  },
  computed: {
    prevLines () {
      return (this.prevSubmission && this.prevSubmission.code_preview || '').split('\n')
    },
    currLines () {
      return (this.currSubmission && this.currSubmission.code_preview || '').split('\n')
    },
    deletedLineNums () {
      return this.computeDeletedLines()
    },
    addedLineNums () {
      return this.computeAddedLines()
    }
  },
  watch: {
    diff () {
      this.$nextTick(() => this.syncScroll())
    }
  },
  mounted () {
    this.syncScroll()
  },
  methods: {
    tagLabel (change) {
      return CHANGE_LABELS[change] || change
    },
    tagClass (change) {
      return CHANGE_COLORS[change] || 'sdp-tag-minor'
    },
    computeDeletedLines () {
      const s = new Set()
      const prev = this.prevLines
      const curr = this.currLines
      const currSet = new Set(curr)
      prev.forEach((line, i) => {
        if (line.trim() && !currSet.has(line)) {
          s.add(i)
        }
      })
      return s
    },
    computeAddedLines () {
      const s = new Set()
      const prev = this.prevLines
      const curr = this.currLines
      const prevSet = new Set(prev)
      curr.forEach((line, i) => {
        if (line.trim() && !prevSet.has(line)) {
          s.add(i)
        }
      })
      return s
    },
    syncScroll () {
      const prev = this.$refs.prevCode
      const curr = this.$refs.currCode
      if (!prev || !curr) return
      const onScroll = (source, target) => {
        return () => { target.scrollTop = source.scrollTop }
      }
      prev.addEventListener('scroll', onScroll(prev, curr))
      curr.addEventListener('scroll', onScroll(curr, prev))
    }
  }
}
</script>

<style lang="less" scoped>
.sdp-panel {
  background: rgba(15, 15, 30, 0.95);
  border: 1px solid rgba(52, 152, 219, 0.2);
  border-radius: 10px;
  margin: 10px 18px 16px;
  overflow: hidden;
}
.sdp-header {
  padding: 12px 16px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
  position: relative;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.sdp-summary {
  font-size: 15px;
  font-weight: 600;
  color: #ecf0f1;
  flex: 1;
  min-width: 200px;
}
.sdp-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.sdp-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  white-space: nowrap;
}
.sdp-tag-struct { background: rgba(46, 204, 113, 0.15); color: #2ecc71; border: 1px solid rgba(46, 204, 113, 0.3); }
.sdp-tag-fix { background: rgba(231, 76, 60, 0.15); color: #e74c3c; border: 1px solid rgba(231, 76, 60, 0.3); }
.sdp-tag-refactor { background: rgba(155, 89, 182, 0.15); color: #9b59b6; border: 1px solid rgba(155, 89, 182, 0.3); }
.sdp-tag-minor { background: rgba(127, 140, 141, 0.15); color: #bdc3c7; border: 1px solid rgba(127, 140, 141, 0.3); }
.sdp-tag-agent { background: rgba(241, 196, 15, 0.15); color: #f1c40f; border: 1px solid rgba(241, 196, 15, 0.3); }
.sdp-close {
  position: absolute;
  top: 8px;
  right: 12px;
  background: none;
  border: none;
  color: #8899aa;
  font-size: 20px;
  cursor: pointer;
  line-height: 1;
  &:hover { color: #ecf0f1; }
}

.sdp-diff-area {
  display: flex;
  max-height: 350px;
}
.sdp-code-col {
  flex: 1;
  min-width: 0;
  &:first-child {
    border-right: 1px solid rgba(255, 255, 255, 0.06);
  }
}
.sdp-col-label {
  padding: 6px 12px;
  font-size: 11px;
  color: #8899aa;
  background: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}
.sdp-code {
  overflow-y: auto;
  max-height: 310px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
}
.sdp-line {
  display: flex;
  padding: 0 8px;
  min-height: 20px;
}
.sdp-line-del {
  background: rgba(231, 76, 60, 0.12);
  .sdp-linetext { color: #e74c3c; }
}
.sdp-line-add {
  background: rgba(46, 204, 113, 0.12);
  .sdp-linetext { color: #2ecc71; }
}
.sdp-linenum {
  width: 32px;
  text-align: right;
  color: #555;
  padding-right: 8px;
  flex-shrink: 0;
  user-select: none;
}
.sdp-linetext {
  color: #bdc3c7;
  white-space: pre;
  flex: 1;
  overflow-x: auto;
}

.sdp-stats {
  padding: 8px 16px;
  display: flex;
  gap: 12px;
  font-size: 11px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.sdp-stat-add { color: #2ecc71; }
.sdp-stat-del { color: #e74c3c; }
.sdp-stat-ratio { color: #8899aa; }

.sdp-expand-enter-active,
.sdp-expand-leave-active {
  transition: all 0.3s ease;
  max-height: 500px;
  opacity: 1;
}
.sdp-expand-enter,
.sdp-expand-leave-to {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
}

@media (max-width: 1200px) {
  .sdp-diff-area {
    flex-direction: column;
  }
  .sdp-code-col:first-child {
    border-right: none;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  }
}
</style>

<template>
  <div v-if="calls && calls.length" class="tc-wrap">
    <div class="tc-toggle" @click="expanded = !expanded" role="button" tabindex="0" @keydown.enter="expanded = !expanded">
      <span class="tc-toggle-icon">{{ expanded ? '▾' : '▸' }}</span>
      <span class="tc-toggle-label">工具调用记录（{{ calls.length }}）</span>
    </div>
    <transition name="tc-slide">
      <div v-show="expanded" class="tc-timeline">
        <div v-for="(call, i) in calls" :key="i" class="tc-item">
          <div class="tc-dot"></div>
          <div class="tc-body">
            <div class="tc-head">
              <span class="tc-tool-name">{{ formatToolName(call.tool_name) }}</span>
              <span v-if="call.latency_ms" class="tc-latency">{{ call.latency_ms }}ms</span>
            </div>
            <div v-if="call.result_summary" class="tc-summary">{{ call.result_summary }}</div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
const TOOL_DISPLAY_NAMES = {
  get_learner_history: '查询学生历史',
  search_similar_errors: '检索相似错误',
  search_courseware: '检索课件内容'
}

export default {
  name: 'ToolCallTimeline',
  props: {
    calls: { type: Array, default: () => [] }
  },
  data () {
    return { expanded: false }
  },
  methods: {
    formatToolName (name) {
      return TOOL_DISPLAY_NAMES[name] || name
    }
  }
}
</script>

<style scoped>
.tc-wrap {
  margin-top: 8px;
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 8px;
  overflow: hidden;
}

.tc-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  background: rgba(248, 250, 252, 0.5);
  min-height: 44px;
  user-select: none;
}

.tc-toggle:hover {
  background: rgba(241, 245, 249, 0.8);
}

.tc-toggle-icon { font-size: 10px; }
.tc-toggle-label { font-weight: 500; }

.tc-timeline {
  padding: 8px 12px 8px 20px;
  position: relative;
}

.tc-timeline::before {
  content: '';
  position: absolute;
  left: 22px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: #e2e8f0;
  border-radius: 1px;
}

.tc-item {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  position: relative;
}

.tc-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #6366f1;
  flex-shrink: 0;
  margin-top: 5px;
  z-index: 1;
}

.tc-body { flex: 1; min-width: 0; }

.tc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tc-tool-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.tc-latency {
  font-size: 11px;
  color: var(--text-disabled, #94a3b8);
  font-family: monospace;
}

.tc-summary {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  margin-top: 2px;
  line-height: 1.5;
}

.tc-slide-enter-active,
.tc-slide-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s ease;
  max-height: 400px;
  overflow: hidden;
}

.tc-slide-enter-from,
.tc-slide-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>

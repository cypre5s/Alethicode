<template>
  <div class="visualize-card" :data-intent="intent">
    <div class="visualize-header">
      <span class="visualize-title">{{ title }}</span>
      <span class="visualize-source">来自 {{ sourceRole }}</span>
    </div>

    <div v-if="renderError" class="visualize-error">{{ renderError }}</div>
    <MermaidRenderer v-else-if="format === 'mermaid'" :payload="payload" />
    <ChartRenderer v-else-if="format === 'chart'" :payload="payload" />
    <SvgRenderer v-else-if="format === 'svg'" :payload="payload" />
    <div v-else class="visualize-error">不支持的可视化格式：{{ format || 'unknown' }}</div>

    <div v-if="altText" class="visualize-alt">{{ altText }}</div>
  </div>
</template>

<script>
import MermaidRenderer from './MermaidRenderer.vue'
import ChartRenderer from './ChartRenderer.vue'
import SvgRenderer from './SvgRenderer.vue'

const INTENT_TITLES = {
  for_loop_trace: '循环迭代图',
  recursion_stack: '递归调用图',
  data_structure_state: '数据结构状态图',
  complexity_compare: '复杂度对比图',
  kc_mastery_radar: '知识掌握雷达图',
  memory_layout: '内存示意图',
  data_flow: '数据流图',
  flowchart: '流程图'
}

export default {
  name: 'VisualizeRenderer',
  components: {
    MermaidRenderer,
    ChartRenderer,
    SvgRenderer
  },
  props: {
    data: {
      type: Object,
      default: () => ({})
    }
  },
  computed: {
    intent () {
      return String(this.data.intent || '').trim().toLowerCase()
    },
    format () {
      return String(this.data.format || '').trim().toLowerCase()
    },
    payload () {
      return typeof this.data.payload === 'string' ? this.data.payload : ''
    },
    altText () {
      return typeof this.data.alt_text === 'string' ? this.data.alt_text : ''
    },
    sourceRole () {
      return this.data.source_role || 'AI'
    },
    title () {
      return INTENT_TITLES[this.intent] || '教学可视化'
    },
    renderError () {
      if (!this.payload) {
        return '可视化内容为空'
      }
      return ''
    }
  }
}
</script>

<style scoped>
.visualize-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  padding: 12px;
}

.visualize-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.visualize-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.visualize-source {
  font-size: 12px;
  color: #6b7280;
}

.visualize-alt {
  margin-top: 8px;
  font-size: 12px;
  color: #374151;
}

.visualize-error {
  color: #b91c1c;
  font-size: 13px;
}
</style>

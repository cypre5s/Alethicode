<template>
  <BaseAgentCard v-if="data" accent="success" :icon="trophyIcon" title="恭喜通过！">
    <template #body>
      <div v-if="data.success_summary" class="pac-success">
        <el-icon :size="14" color="var(--card-accent)"><CircleCheck /></el-icon>
        <span>{{ data.success_summary }}</span>
      </div>

      <div v-if="data.key_action" class="pac-tip">
        <el-icon :size="14"><Compass /></el-icon>
        <span>{{ data.key_action }}</span>
      </div>

      <div v-if="data.knowledge_points && data.knowledge_points.length" class="pac-section">
        <span class="pac-label">掌握的知识点</span>
        <ul>
          <li v-for="(item, i) in data.knowledge_points" :key="i">{{ item }}</li>
        </ul>
      </div>

      <div v-if="data.code_quality_notes && data.code_quality_notes.length" class="pac-section">
        <span class="pac-label">代码质量建议</span>
        <ul>
          <li v-for="(item, i) in data.code_quality_notes" :key="'cq-' + i">{{ item }}</li>
        </ul>
      </div>

      <div v-if="data.next_practice_direction" class="pac-next">
        <span class="pac-label">下一步练习方向</span>
        <p>{{ data.next_practice_direction }}</p>
      </div>

      <div class="pac-notebook-actions">
        <button type="button" class="pac-breakthrough-chip" @click="$emit('add-to-notebook', { entry_type: 'breakthrough', breakthrough_insight: data.key_insight || data.success_summary || '' })">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v8"/><path d="m4.93 10.93 1.41 1.41"/><path d="M2 18h2"/><path d="M20 18h2"/><path d="m19.07 10.93-1.41 1.41"/><path d="M22 22H2"/><path d="m8 22 4-10 4 10"/></svg>
          记下顿悟
        </button>
      </div>

      <ReasoningChain v-if="data.reasoning_chain" :chain="data.reasoning_chain" />
      <ToolCallTimeline v-if="data.tool_calls" :calls="data.tool_calls" />
      <EvidenceRefs
        :courseware-refs="data.courseware_refs || []"
        :memories="data.memory_refs || []"
      />
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw } from 'vue'
import { Trophy, CircleCheck, Compass } from '@element-plus/icons-vue'
import BaseAgentCard from './BaseAgentCard.vue'
import ReasoningChain from './ReasoningChain.vue'
import ToolCallTimeline from './ToolCallTimeline.vue'
import EvidenceRefs from './EvidenceRefs.vue'

export default {
  name: 'PostACCard',
  components: { BaseAgentCard, CircleCheck, Compass, ReasoningChain, ToolCallTimeline, EvidenceRefs },
  emits: ['add-to-notebook'],
  props: {
    data: { type: Object, default: () => ({}) }
  },
  data () {
    return { trophyIcon: markRaw(Trophy) }
  }
}
</script>

<style lang="less" scoped>
.pac-section { padding: 0; }
.pac-label {
  display: block; font-weight: 600; font-size: var(--card-font-label);
  color: var(--card-accent); text-transform: uppercase; letter-spacing: 0.4px;
  margin-bottom: 8px;
}
.pac-section ul { padding-left: 20px; margin: 0; color: var(--text-primary); }
.pac-section li { margin-bottom: 5px; line-height: 1.6; font-size: var(--card-font-body); }

.pac-success {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 12px 14px; background: var(--card-accent-bg);
  border: 1px solid var(--card-accent-border); border-radius: 10px;
  color: var(--text-primary); font-weight: 500; font-size: var(--card-font-body);
}

.pac-tip {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 12px 14px;
  background: rgba(124, 58, 237, 0.05); border: 1px solid rgba(124, 58, 237, 0.15);
  border-radius: 10px; color: var(--text-primary);
  font-weight: 500; font-size: var(--card-font-body);
}

.pac-next {
  background: var(--card-accent-bg); border: 1px solid var(--card-accent-border);
  border-radius: 10px; padding: 12px 14px;
}
.pac-next p {
  color: var(--text-primary); margin: 0; line-height: 1.7; font-size: var(--card-font-body);
}
.pac-notebook-actions { margin-top: 10px; }
.pac-breakthrough-chip {
  display: inline-flex; align-items: center; gap: 6px;
  background: #f5f3ff; border: 1px solid #ddd6fe; color: #5b21b6;
  padding: 6px 14px; border-radius: 8px; font-size: 13px; font-weight: 500;
  cursor: pointer; font-family: inherit; min-height: 44px;
  transition: all 200ms;
  &:hover { background: #ede9fe; border-color: #c4b5fd; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
}
@media (prefers-reduced-motion: reduce) { .pac-breakthrough-chip { transition: none; } }
</style>

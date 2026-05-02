<template>
  <BaseAgentCard v-if="cardData" accent="transfer" :icon="shareIcon" title="举一反三">
    <template #body>
      <h3 v-if="cardData.title" class="tp-title">
        <span v-if="cardData.problem_display_id" class="tp-display-id">{{ cardData.problem_display_id }}</span>
        <span>{{ cardData.title }}</span>
      </h3>

      <div v-if="cardData.description" class="tp-block">
        <p class="tp-block-title">描述</p>
        <div class="tp-block-content" v-html="sanitize(cardData.description)"></div>
      </div>
      <div v-if="cardData.input_description" class="tp-block">
        <p class="tp-block-title">输入</p>
        <div class="tp-block-content" v-html="sanitize(cardData.input_description)"></div>
      </div>
      <div v-if="cardData.output_description" class="tp-block">
        <p class="tp-block-title">输出</p>
        <div class="tp-block-content" v-html="sanitize(cardData.output_description)"></div>
      </div>

      <div v-if="cardData.samples && cardData.samples.length" class="tp-samples">
        <div v-for="(sample, idx) in cardData.samples" :key="idx" class="tp-sample">
          <div class="tp-sample-label">样例 {{ idx + 1 }}</div>
          <pre class="tp-sample-pre"><span class="tp-sample-in">输入：</span>{{ sample.input }}</pre>
          <pre class="tp-sample-pre"><span class="tp-sample-out">输出：</span>{{ sample.output }}</pre>
        </div>
      </div>

      <div v-if="cardData.hint" class="tp-hint">
        <button class="tp-hint-toggle" @click="hintExpanded = !hintExpanded">
          <el-icon :size="14"><component :is="hintExpanded ? ArrowUp : ArrowDown" /></el-icon>
          <span>提示</span>
        </button>
        <div v-show="hintExpanded" class="tp-hint-content" v-html="sanitize(cardData.hint)"></div>
      </div>

      <div v-if="cardData.target_kcs && cardData.target_kcs.length" class="tp-kcs">
        <el-tag v-for="(kc, i) in cardData.target_kcs" :key="i" type="success" size="small" effect="plain">{{ kc }}</el-tag>
      </div>

      <ReasoningChain v-if="data.reasoning_chain" :chain="data.reasoning_chain" />
      <ToolCallTimeline v-if="data.tool_calls" :calls="data.tool_calls" />
      <EvidenceRefs
        :courseware-refs="data.courseware_refs || []"
        :memories="data.memory_refs || []"
      />
    </template>

    <template #foot>
      <el-button v-if="cardData.problem_display_id" type="primary" class="tp-enter-btn" @click="navigateToProblem()">
        进入临时题（仅自己可见，无 AI 导学）
      </el-button>
      <el-button v-else type="primary" class="tp-enter-btn" disabled>
        暂未生成可练习链接
      </el-button>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw } from 'vue'
import { sanitize } from '@/utils/sanitize'
import { Share, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import BaseAgentCard from './BaseAgentCard.vue'
import ReasoningChain from './ReasoningChain.vue'
import ToolCallTimeline from './ToolCallTimeline.vue'
import EvidenceRefs from './EvidenceRefs.vue'

export default {
  name: 'TransferProblemCard',
  components: { BaseAgentCard, ArrowUp, ArrowDown, ReasoningChain, ToolCallTimeline, EvidenceRefs },
  props: {
    data: {
      type: Object,
      default: () => ({ title: '', description: '', samples: [], hint: '', target_kcs: [] })
    }
  },
  data () {
    return {
      hintExpanded: false,
      shareIcon: markRaw(Share),
      ArrowUp: markRaw(ArrowUp),
      ArrowDown: markRaw(ArrowDown)
    }
  },
  computed: {
    cardData () {
      if (this.data && typeof this.data === 'object' && !Array.isArray(this.data)) {
        const displayId = (this.data.problem_display_id || '').trim()
        const rawTitle = (this.data.title || '举一反三').trim()
        const normalizedTitle = displayId && rawTitle.indexOf(displayId + ' ') === 0
          ? rawTitle.slice(displayId.length).trim()
          : rawTitle
        return {
          ...this.data,
          problem_display_id: displayId,
          title: (normalizedTitle || '举一反三').trim(),
          description: (this.data.description || '这道迁移题缺少完整描述，请使用临时题模式进入后，先对照样例推断任务目标。').trim(),
          input_description: (this.data.input_description || '').trim(),
          output_description: (this.data.output_description || '').trim()
        }
      }
      if (typeof this.data === 'string' && this.data.trim()) {
        return {
          title: '举一反三', description: this.data.trim(),
          samples: [], hint: '', target_kcs: [], problem_display_id: ''
        }
      }
      return null
    }
  },
  methods: {
    sanitize,
    navigateToProblem () {
      if (!this.cardData || !this.cardData.problem_display_id) return
      this.$router.push({
        path: '/problem/' + this.cardData.problem_display_id,
        query: {
          ...this.$route.query,
          temp_problem: '1', ai_tutor_allowed: '0', ai_tutor_reason: 'temporary_problem'
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
.tp-title {
  display: flex; align-items: baseline; gap: 8px;
  font-size: 15px; font-weight: 600; color: var(--text-primary); margin: 0;
}
.tp-display-id {
  font-family: var(--font-mono); font-size: var(--card-font-body); color: var(--text-secondary);
  background: var(--bg-panel); border: 1px solid var(--border-color);
  border-radius: 10px; padding: 2px 8px; line-height: 1.4; flex-shrink: 0;
}
.tp-block-title {
  margin: 0 0 6px; font-size: var(--card-font-body);
  font-weight: 600; color: var(--text-secondary);
}
.tp-block-content { color: var(--text-primary); margin: 0; }
.tp-block-content :deep(p) { margin: 0 0 8px; &:last-child { margin-bottom: 0; } }

.tp-samples { display: flex; flex-direction: column; gap: 12px; }
.tp-sample-label {
  font-size: var(--card-font-body); font-weight: 500; color: var(--text-secondary); margin-bottom: 6px;
}
.tp-sample-pre {
  background: var(--bg-panel); border: 1px solid var(--border-color);
  border-radius: var(--border-radius-sm); padding: 10px 12px;
  font-family: var(--font-mono); font-size: var(--card-font-body); line-height: 1.5;
  color: var(--text-primary); overflow-x: auto; white-space: pre-wrap; margin: 0 0 6px;
  &:last-of-type { margin-bottom: 0; }
}
.tp-sample-in { color: var(--card-accent); font-weight: 500; }
.tp-sample-out { color: var(--success-color); font-weight: 500; }

.tp-hint-toggle {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px; background: var(--card-accent-bg);
  border: 1px solid var(--card-accent-border); border-radius: var(--border-radius-sm);
  color: var(--card-accent); font-size: var(--card-font-body); cursor: pointer;
  width: 100%; text-align: left; transition: background 0.2s;
  &:hover { background: var(--card-accent-bg-strong); }
}
.tp-hint-content {
  padding: 12px 14px; margin-top: 6px;
  background: var(--bg-panel); border: 1px solid var(--border-color);
  border-radius: var(--border-radius-sm); color: var(--text-primary); font-size: var(--card-font-body);
}

.tp-kcs { display: flex; flex-wrap: wrap; gap: 6px; }

.tp-enter-btn { width: 100%; }
</style>

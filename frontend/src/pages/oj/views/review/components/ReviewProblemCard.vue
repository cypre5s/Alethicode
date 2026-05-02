<template>
  <div :class="['rpc-card', cardModifierClass, { 'is-just-added': justAdded }]" :data-problem-row-id="problem.id">
    <div class="rpc-seq" :class="{ 'rpc-seq-ai': problem.is_ai_generated }">{{ problem.sequence }}</div>
    <div class="rpc-info">
      <div class="rpc-info-top">
        <span class="rpc-key">{{ problem.problem_key }}</span>
        <span class="rpc-title">{{ problem.title }}</span>
        <span v-if="problem.is_ai_generated" class="rpc-ai-badge">AI 出题</span>
      </div>
      <div class="rpc-info-meta">
        <span v-if="problem.education_goal" class="rpc-meta-goal">{{ educationGoalLabel }}</span>
        <span v-if="problem.card_type" class="rpc-meta-type">{{ cardTypeLabel }}</span>
      </div>
      <div v-if="problem.why_this_now" class="rpc-why">{{ problem.why_this_now }}</div>
    </div>

    <div class="rpc-status">
      <template v-if="!problem.submitted">
        <span class="rpc-status-pending">待完成</span>
      </template>
      <template v-else-if="problem.user_rating === 'good'">
        <span class="rpc-badge-mastered">已掌握</span>
      </template>
      <template v-else-if="problem.user_rating === 'again'">
        <span class="rpc-badge-similar">已生成相似题</span>
      </template>
      <template v-else>
        <span v-if="problem.is_correct === true" class="rpc-status-correct">正确</span>
        <span v-else-if="problem.is_correct === false" class="rpc-status-wrong">错误</span>
      </template>
    </div>

    <div class="rpc-actions">
      <ElButton v-if="!problem.submitted" size="small" type="primary" @click="$emit('open-problem', problem)">去做题</ElButton>
      <template v-else-if="problem.user_rating === null || typeof problem.user_rating === 'undefined' || problem.user_rating === ''">
        <ElButton size="small" type="success" :loading="loading" @click="rate('good')">
          <ElIcon><Check /></ElIcon>
          <span>我会了</span>
        </ElButton>
        <ElButton size="small" :loading="loading" @click="rate('again')">
          <ElIcon><RefreshRight /></ElIcon>
          <span>再练一题</span>
        </ElButton>
        <ElButton size="small" plain @click="$emit('open-parsons', problem)">试试拼装版</ElButton>
      </template>
      <ElButton v-else size="small" plain @click="$emit('open-problem', problem)">查看</ElButton>
    </div>

    <div v-if="loading" class="rpc-loading-overlay">
      <span class="rpc-loading-spinner"></span>
      <span class="rpc-loading-text">{{ loadingText }}</span>
    </div>
  </div>
</template>

<script>
import { Check, RefreshRight } from '@element-plus/icons-vue'

const EDUCATION_GOAL_LABELS = Object.freeze({
  understand: '理解',
  recall: '回忆',
  apply: '应用',
  transfer: '迁移'
})

const CARD_TYPE_LABELS = Object.freeze({
  course_example: '课件例题',
  objective_problem: '短练习',
  faded_example: '渐退示例',
  coding_problem: '编程题',
  transfer_problem: '迁移题'
})

export default {
  name: 'ReviewProblemCard',
  components: { Check, RefreshRight },
  emits: ['rate', 'open-problem', 'open-parsons'],
  props: {
    problem: { type: Object, required: true },
    loading: { type: Boolean, default: false },
    loadingText: { type: String, default: '正在为你生成相似题…' },
    justAdded: { type: Boolean, default: false }
  },
  computed: {
    educationGoalLabel () {
      return EDUCATION_GOAL_LABELS[this.problem.education_goal] || this.problem.education_goal
    },
    cardTypeLabel () {
      return CARD_TYPE_LABELS[this.problem.card_type] || this.problem.card_type
    },
    cardModifierClass () {
      if (!this.problem.submitted) return ''
      if (this.problem.user_rating === 'good') return 'rpc-state-good'
      if (this.problem.user_rating === 'again') return 'rpc-state-again'
      if (this.problem.is_correct) return 'rpc-state-correct'
      if (this.problem.is_correct === false) return 'rpc-state-wrong'
      return ''
    }
  },
  methods: {
    rate (value) {
      if (this.loading) return
      this.$emit('rate', { problem: this.problem, rating: value })
    }
  }
}
</script>

<style lang="less" scoped>
.rpc-card {
  position: relative;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--list-item-padding);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-default);
  background: var(--bg-card);
  transition: box-shadow var(--motion-base), border-color var(--motion-base), background var(--motion-base);
  &:hover { box-shadow: var(--shadow-sm); }
  &.rpc-state-correct,
  &.rpc-state-good {
    border-color: rgba(16, 185, 129, 0.32);
    background: rgba(16, 185, 129, 0.06);
  }
  &.rpc-state-wrong {
    border-color: rgba(239, 68, 68, 0.30);
    background: rgba(239, 68, 68, 0.06);
  }
  &.rpc-state-again {
    border-color: rgba(245, 158, 11, 0.34);
    background: rgba(245, 158, 11, 0.07);
  }
  &.is-just-added {
    animation: rpc-flash 1.6s ease-out;
    border-color: var(--color-warning);
    box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.18);
  }
}

@keyframes rpc-flash {
  0% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
  20% { box-shadow: 0 0 0 6px rgba(245, 158, 11, 0.4); }
  100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
}

.rpc-seq {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-pill);
  background: var(--primary-50);
  color: var(--primary-700);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: var(--fs-base);
  flex-shrink: 0;
}
.rpc-seq-ai {
  background: rgba(99, 102, 241, 0.14);
  color: var(--warm-primary-strong);
}

.rpc-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.rpc-info-top { display: flex; align-items: baseline; gap: var(--space-2); flex-wrap: wrap; }
.rpc-info-meta { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.rpc-key {
  font-family: var(--font-mono, monospace);
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--primary-color);
  flex-shrink: 0;
}
.rpc-title {
  font-size: var(--fs-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rpc-ai-badge {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  font-size: var(--fs-xs);
  font-weight: 600;
  padding: 0 8px;
  border-radius: var(--radius-xs);
  background: var(--warm-grad-primary);
  color: #fff;
}
.rpc-meta-goal {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  font-size: var(--fs-xs);
  color: var(--primary-700);
  background: var(--primary-50);
  border-radius: var(--radius-pill);
  padding: 0 8px;
}
.rpc-meta-type {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  font-size: var(--fs-xs);
  color: var(--warm-primary-strong);
  background: rgba(99, 102, 241, 0.14);
  border-radius: var(--radius-pill);
  padding: 0 8px;
}
.rpc-why {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  line-height: var(--leading-tight);
}

.rpc-status { flex-shrink: 0; }
.rpc-status-pending {
  color: var(--text-disabled);
  font-size: var(--fs-sm);
}
.rpc-status-correct {
  color: var(--color-success);
  font-size: var(--fs-sm);
  font-weight: 600;
}
.rpc-status-wrong {
  color: var(--color-danger);
  font-size: var(--fs-sm);
  font-weight: 600;
}
.rpc-badge-mastered {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  font-size: var(--fs-sm);
  font-weight: 600;
  padding: 0 10px;
  border-radius: var(--radius-pill);
  background: rgba(16, 185, 129, 0.10);
  color: #047857;
  border: 1px solid rgba(16, 185, 129, 0.32);
}
.rpc-badge-similar {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  font-size: var(--fs-sm);
  font-weight: 600;
  padding: 0 10px;
  border-radius: var(--radius-pill);
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  border: 1px solid rgba(245, 158, 11, 0.34);
}

.rpc-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

.rpc-loading-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  border-radius: var(--radius-md);
  backdrop-filter: blur(2px);
}
.rpc-loading-spinner {
  width: 18px;
  height: 18px;
  border-radius: var(--radius-pill);
  border: 2px solid var(--border-default);
  border-top-color: var(--primary-color);
  animation: rpc-spin 0.8s linear infinite;
}
@keyframes rpc-spin { to { transform: rotate(360deg); } }
.rpc-loading-text {
  font-size: var(--fs-base);
  color: var(--text-secondary);
  font-weight: 500;
}
</style>

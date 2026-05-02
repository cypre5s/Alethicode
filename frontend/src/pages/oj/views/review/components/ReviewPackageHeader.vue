<template>
  <div class="rph-root">
    <div class="rph-left">
      <ElButton size="small" plain @click="$emit('go-back')">返回</ElButton>
      <TaxonomyTag :type="pkg.error_taxonomy" :label="pkg.error_label || '错题强化'" />
      <span class="rph-title">专项复习包</span>
    </div>
    <div class="rph-right">
      <span class="rph-fsrs">
        <span class="rph-fsrs-state" :class="'rph-fsrs-' + (pkg.fsrs_state || 'new')">{{ stateLabel }}</span>
        <span v-if="pkg.due_at" class="rph-fsrs-due">{{ dueLabel }}</span>
      </span>
      <span v-if="pkg.mastery_reached" class="rph-mastery">已掌握</span>
      <span class="rph-progress">{{ pkg.completed_count }} / {{ pkg.problem_count }}</span>
    </div>
  </div>
</template>

<script>
import TaxonomyTag from '@/components/TaxonomyTag.vue'

const STATE_LABELS = { new: '新计划', learning: '学习中', review: '复习中', relearning: '再练', graduated: '已毕业' }

function relativeDay (iso) {
  if (!iso) return ''
  const due = new Date(iso)
  if (Number.isNaN(due.getTime())) return ''
  const now = Date.now()
  const diffMs = due.getTime() - now
  const diffDay = Math.round(diffMs / 86400000)
  if (diffDay === 0) return '今日到期'
  if (diffDay > 0) return `${diffDay} 天后到期`
  return `已逾期 ${-diffDay} 天`
}

export default {
  name: 'ReviewPackageHeader',
  components: { TaxonomyTag },
  emits: ['go-back'],
  props: {
    pkg: { type: Object, required: true }
  },
  computed: {
    stateLabel () { return STATE_LABELS[this.pkg.fsrs_state] || this.pkg.fsrs_state || '新计划' },
    dueLabel () { return relativeDay(this.pkg.due_at) }
  }
}
</script>

<style lang="less" scoped>
.rph-root {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  box-shadow: var(--shadow-xs);
}
.rph-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}
.rph-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}
.rph-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--text-strong);
}
.rph-fsrs {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.rph-fsrs-state {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  padding: 0 10px;
  border-radius: var(--tag-radius);
  font-size: var(--fs-sm);
  font-weight: 600;
  line-height: 1;
}
.rph-fsrs-new {
  background: var(--primary-50);
  color: var(--primary-700);
}
.rph-fsrs-learning {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}
.rph-fsrs-review {
  background: rgba(99, 102, 241, 0.12);
  color: var(--warm-primary-strong);
}
.rph-fsrs-relearning {
  background: rgba(239, 68, 68, 0.10);
  color: #b91c1c;
}
.rph-fsrs-graduated {
  background: rgba(16, 185, 129, 0.10);
  color: #047857;
}
.rph-fsrs-due {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
}
.rph-mastery {
  color: var(--color-success);
  font-weight: 600;
  font-size: var(--fs-base);
}
.rph-progress {
  font-size: var(--fs-md);
  color: var(--text-secondary);
  font-weight: 500;
}
</style>

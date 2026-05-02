<template>
  <div class="rpc-card rpc-card-unavailable" :data-problem-row-id="problem.id">
    <div class="rpc-seq rpc-seq-unavailable">{{ problem.sequence }}</div>
    <div class="rpc-info">
      <div class="rpc-info-top">
        <span class="rpc-title rpc-title-unavailable">该题目已下架</span>
        <span class="rpc-unavailable-badge">不可练习</span>
      </div>
      <div class="rpc-why rpc-why-unavailable">原题已被管理员或系统从题库中移除，复习包保留这条历史记录用于追溯。</div>
    </div>
    <div class="rpc-status">
      <span class="rpc-status-disabled">不可用</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ReviewProblemUnavailableCard',
  props: {
    problem: { type: Object, required: true }
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
  border: 1px dashed var(--border-default);
  background: var(--bg-disabled, #f5f5f5);
  opacity: 0.85;
}
.rpc-seq {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-pill);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: var(--fs-base);
  flex-shrink: 0;
  background: rgba(148, 163, 184, 0.18);
  color: var(--text-disabled, #94a3b8);
}
.rpc-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.rpc-info-top { display: flex; align-items: baseline; gap: var(--space-2); flex-wrap: wrap; }
.rpc-title-unavailable {
  color: var(--text-secondary);
  font-style: italic;
  font-size: var(--fs-md);
}
.rpc-unavailable-badge {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  font-size: var(--fs-xs);
  font-weight: 600;
  padding: 0 8px;
  border-radius: var(--radius-xs);
  background: rgba(148, 163, 184, 0.18);
  color: var(--text-secondary);
}
.rpc-why-unavailable {
  font-size: var(--fs-sm);
  color: var(--text-disabled, #94a3b8);
  line-height: var(--leading-tight);
}
.rpc-status { flex-shrink: 0; }
.rpc-status-disabled {
  color: var(--text-disabled, #94a3b8);
  font-size: var(--fs-sm);
  font-weight: 500;
}
</style>

<template>
  <div v-if="evidence" class="rpe-root">
    <div class="rpe-title">错误概况</div>
    <div class="rpe-stats">
      <span>错题本记录：{{ evidence.notebook_count || 0 }} 条</span>
      <span>学习事件：{{ evidence.event_count || 0 }} 次</span>
    </div>
    <div v-if="recentRootCauses.length" class="rpe-causes">
      <div class="rpe-causes-title">常见原因：</div>
      <div v-for="(cause, idx) in recentRootCauses" :key="idx" class="rpe-cause-item">{{ cause }}</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ReviewPackageEvidence',
  props: {
    evidence: { type: Object, default: null }
  },
  computed: {
    recentRootCauses () {
      const list = this.evidence && this.evidence.recent_root_causes
      return Array.isArray(list) ? list : []
    }
  }
}
</script>

<style lang="less" scoped>
.rpe-root {
  background: #fafbfc; border: 1px solid #e8e8e8;
  border-radius: 10px; padding: 16px;
}
.rpe-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.rpe-stats { display: flex; gap: 16px; font-size: 13px; color: #666; margin-bottom: 8px; }
.rpe-causes { margin-top: 4px; }
.rpe-causes-title { font-size: 12px; color: #999; margin-bottom: 4px; }
.rpe-cause-item { font-size: 12px; color: #555; padding: 2px 0; line-height: 1.4; }
</style>

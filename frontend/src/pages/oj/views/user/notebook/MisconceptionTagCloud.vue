<template>
  <div v-if="cloud.length" class="mtc-block">
    <div class="mtc-title">高频易错点</div>
    <div class="mtc-cloud">
      <span
        v-for="m in cloud"
        :key="m.id || m.name"
        class="mtc-chip"
        :style="{ fontSize: chipFontSize(m) + 'px' }"
        :title="m.description || ''"
      >{{ m.name }}<span v-if="m.trigger_count" class="mtc-count">×{{ m.trigger_count }}</span></span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MisconceptionTagCloud',
  props: {
    misconceptions: { type: Array, default: () => [] }
  },
  computed: {
    cloud () {
      return Array.isArray(this.misconceptions)
        ? this.misconceptions.filter(m => m && m.name).slice(0, 24)
        : []
    },
    maxCount () {
      let max = 1
      for (const m of this.cloud) {
        const c = parseInt(m.trigger_count, 10) || 0
        if (c > max) max = c
      }
      return max
    }
  },
  methods: {
    chipFontSize (m) {
      const c = parseInt(m.trigger_count, 10) || 0
      const ratio = this.maxCount === 0 ? 0 : c / this.maxCount
      return Math.round(12 + ratio * 8)
    }
  }
}
</script>

<style lang="less" scoped>
.mtc-block {
  background: #fff; border: 1px solid #e8eaed;
  border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.05);
  padding: 16px 20px; margin-top: 16px;
}
.mtc-title { font-size: 13px; font-weight: 600; color: #1a1d2e; margin-bottom: 10px; }
.mtc-cloud { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; }
.mtc-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 12px; border-radius: 999px;
  background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca;
  font-weight: 500;
}
.mtc-count { font-size: 11px; opacity: 0.7; }
</style>

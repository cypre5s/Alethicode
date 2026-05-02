<template>
  <ElDialog
    :model-value="modelValue"
    :show-close="false"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    title="整体感觉如何？"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="rmd-body">
      <p class="rmd-prompt">你已完成本次复习包的全部题目。请告诉我，整体掌握程度如何？</p>
      <div class="rmd-options">
        <button
          type="button"
          class="rmd-option rmd-option-good"
          :disabled="loading"
          @click="emitChoice('good')"
        >
          <span class="rmd-option-emoji">🌟</span>
          <span class="rmd-option-title">已经掌握</span>
          <span class="rmd-option-sub">下一次安排到长间隔复习</span>
        </button>
        <button
          type="button"
          class="rmd-option rmd-option-again"
          :disabled="loading"
          @click="emitChoice('again')"
        >
          <span class="rmd-option-emoji">🔁</span>
          <span class="rmd-option-title">还要练</span>
          <span class="rmd-option-sub">明天再来一次</span>
        </button>
      </div>
    </div>
  </ElDialog>
</template>

<script>
export default {
  name: 'ReviewMasteryDialog',
  emits: ['update:modelValue', 'choose'],
  props: {
    modelValue: { type: Boolean, default: false },
    loading: { type: Boolean, default: false }
  },
  methods: {
    emitChoice (rating) {
      if (this.loading) return
      this.$emit('choose', rating)
    }
  }
}
</script>

<style lang="less" scoped>
.rmd-body { display: flex; flex-direction: column; gap: 16px; }
.rmd-prompt { margin: 0; font-size: 14px; color: #475569; line-height: 1.6; }
.rmd-options { display: flex; flex-direction: column; gap: 10px; }
.rmd-option {
  display: flex; flex-direction: column; align-items: flex-start; gap: 4px;
  padding: 14px 18px; border-radius: 10px;
  border: 1px solid #e2e8f0; background: #fff;
  cursor: pointer; transition: border-color 0.15s, background 0.15s;
  font-family: inherit; text-align: left;
  &:hover { background: #f8fafc; border-color: #1a73e8; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}
.rmd-option-emoji { font-size: 18px; }
.rmd-option-title { font-size: 14px; font-weight: 600; color: #1a1d2e; }
.rmd-option-sub { font-size: 12px; color: #64748b; }
.rmd-option-good { &:hover { background: #f0fdf4; border-color: #16a34a; } }
.rmd-option-again { &:hover { background: #fff7ed; border-color: #d97706; } }
</style>

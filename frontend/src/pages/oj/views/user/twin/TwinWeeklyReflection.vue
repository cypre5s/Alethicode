<template>
  <div class="tw-panel" role="region" aria-label="每周回顾与冥想">
    <h3 class="tw-panel__title">本周学习回顾</h3>

    <div v-if="loading" class="tw-skeleton">
      <el-skeleton :rows="3" animated />
    </div>

    <template v-else>
      <div class="tw-stats">
        <div class="tw-stat">
          <span class="tw-stat__num">{{ weekly.submits || 0 }}</span>
          <span class="tw-stat__label">本周提交</span>
        </div>
        <div class="tw-stat">
          <span class="tw-stat__num">{{ weekly.acs || 0 }}</span>
          <span class="tw-stat__label">AC 数</span>
        </div>
        <div class="tw-stat">
          <span class="tw-stat__num">{{ weekly.new_kcs || 0 }}</span>
          <span class="tw-stat__label">新涉及 KC</span>
        </div>
      </div>

      <div class="tw-reflection">
        <h4 class="tw-reflection__title">周日冥想</h4>
        <p class="tw-reflection__prompt">花 2 分钟回顾本周，写下你的感受和下周目标：</p>
        <textarea
          v-model="reflectionText"
          class="tw-reflection__textarea"
          placeholder="本周我学到了..."
          rows="4"
          maxlength="1000"
        ></textarea>
        <div class="tw-reflection__footer">
          <span class="tw-char-count">{{ reflectionText.length }} / 1000</span>
          <button type="button" class="tw-btn" :disabled="!reflectionText.trim()" @click="submitReflection">
            保存冥想
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'
import { notify } from '@/utils/notifications'

export default {
  name: 'TwinWeeklyReflection',
  data () {
    return {
      loading: false,
      weekly: {},
      reflectionText: ''
    }
  },
  mounted () { this.loadWeekly() },
  methods: {
    async loadWeekly () {
      this.loading = true
      try {
        const res = await api.getTwinWeekly()
        this.weekly = res.data.data || {}
      } catch { this.weekly = {} }
      finally { this.loading = false }
    },
    async submitReflection () {
      try {
        await api.submitSundayReflection({ text: this.reflectionText.trim() })
        notify.success('冥想已保存')
      } catch { notify.error('保存失败') }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.tw-panel {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-5;
  &__title { font-size: @l99-fs-lg; font-weight: 600; color: @l99-neutral-900; margin: 0 0 @l99-sp-4; }
}
.tw-skeleton { padding: @l99-sp-4; }
.tw-stats { display: flex; gap: @l99-sp-6; margin-bottom: @l99-sp-6; }
.tw-stat {
  text-align: center;
  &__num { display: block; font-size: @l99-fs-2xl; font-weight: 700; color: @l99-neutral-900; font-family: @l99-font-mono; }
  &__label { font-size: @l99-fs-xs; color: @l99-neutral-500; }
}
.tw-reflection {
  &__title { font-size: @l99-fs-md; font-weight: 600; color: @l99-neutral-900; margin: 0 0 @l99-sp-2; }
  &__prompt { font-size: @l99-fs-sm; color: @l99-neutral-500; margin: 0 0 @l99-sp-3; }
  &__textarea {
    width: 100%; padding: @l99-sp-3; border: 1px solid @l99-neutral-200; border-radius: @l99-radius-sm;
    font-size: @l99-fs-sm; line-height: 1.6; resize: vertical;
    &:focus { outline: none; border-color: @l99-primary; }
  }
  &__footer { display: flex; justify-content: space-between; align-items: center; margin-top: @l99-sp-2; }
}
.tw-char-count { font-size: @l99-fs-xs; color: @l99-neutral-500; }
.tw-btn {
  padding: @l99-sp-2 @l99-sp-4; background: @l99-primary; color: #fff; border: none;
  border-radius: @l99-radius-sm; font-size: @l99-fs-sm; cursor: pointer;
  &:hover { opacity: 0.9; } &:disabled { opacity: 0.4; cursor: not-allowed; }
}
</style>

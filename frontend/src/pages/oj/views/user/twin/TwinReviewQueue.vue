<template>
  <div class="trq-panel" role="region" aria-label="孪生复习队列">
    <h3 class="trq-title">你的孪生在等你复习</h3>
    <p class="trq-desc">这些知识点正在慢慢被遗忘，回顾一下帮孪生记住它们</p>

    <div v-if="loading" class="trq-skeleton">
      <el-skeleton :rows="2" animated />
    </div>

    <template v-else>
      <div v-if="forgotten.length > 0" class="trq-section">
        <h4 class="trq-section__title trq-section__title--urgent">快忘了</h4>
        <div v-for="item in forgotten" :key="item.kc_id" class="trq-item trq-item--forgotten">
          <span class="trq-item__name">{{ item.kc_name }}</span>
          <span class="trq-item__info">已复习 {{ item.fsrs_reps }} 次</span>
          <button type="button" class="trq-item__btn" @click="review(item.kc_id)">复习一下</button>
        </div>
      </div>

      <div v-if="fading.length > 0" class="trq-section">
        <h4 class="trq-section__title">快要忘了</h4>
        <div v-for="item in fading" :key="item.kc_id" class="trq-item trq-item--fading">
          <span class="trq-item__name">{{ item.kc_name }}</span>
          <span class="trq-item__info">已复习 {{ item.fsrs_reps }} 次</span>
          <button type="button" class="trq-item__btn" @click="review(item.kc_id)">巩固一下</button>
        </div>
      </div>

      <div v-if="forgotten.length === 0 && fading.length === 0" class="trq-all-fresh">
        <p>所有知识点都记得牢牢的，继续保持！</p>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'
import { notify } from '@/utils/notifications'

export default {
  name: 'TwinReviewQueue',
  data () {
    return { loading: false, fading: [], forgotten: [] }
  },
  mounted () { this.load() },
  methods: {
    async load () {
      this.loading = true
      try {
        const res = await api.getKcDecayQueue()
        const d = res.data.data
        this.fading = d.fading || []
        this.forgotten = d.forgotten || []
      } catch { this.fading = []; this.forgotten = [] }
      finally { this.loading = false }
    },
    async review (kcId) {
      try {
        await api.reviewDecayKc(kcId)
        this.fading = this.fading.filter(i => i.kc_id !== kcId)
        this.forgotten = this.forgotten.filter(i => i.kc_id !== kcId)
        notify.success('复习完成，记忆已刷新')
      } catch { notify.error('操作失败') }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.trq-panel {
  background: #fff; border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200; box-shadow: @l99-shadow-1; padding: @l99-sp-5;
}
.trq-title { font-size: @l99-fs-lg; font-weight: 600; color: @l99-neutral-900; margin: 0 0 @l99-sp-1; }
.trq-desc { font-size: @l99-fs-sm; color: @l99-neutral-500; margin: 0 0 @l99-sp-5; }
.trq-skeleton { padding: @l99-sp-4; }

.trq-section { margin-bottom: @l99-sp-4;
  &__title { font-size: @l99-fs-sm; font-weight: 600; color: @l99-neutral-700; margin: 0 0 @l99-sp-2;
    &--urgent { color: @l99-danger; }
  }
}

.trq-item {
  display: flex; align-items: center; gap: @l99-sp-3; padding: @l99-sp-3;
  border-radius: @l99-radius-sm; margin-bottom: @l99-sp-2;
  &--forgotten { background: fade(@l99-danger, 6%); }
  &--fading { background: fade(@l99-warn, 6%); }
  &__name { flex: 1; font-size: @l99-fs-sm; color: @l99-neutral-900; font-weight: 500; }
  &__info { font-size: @l99-fs-xs; color: @l99-neutral-500; }
  &__btn {
    padding: @l99-sp-1 @l99-sp-3; border: 1px solid @l99-primary; border-radius: @l99-radius-sm;
    background: #fff; color: @l99-primary; font-size: @l99-fs-xs; cursor: pointer;
    &:hover { background: @l99-primary-soft; }
  }
}

.trq-all-fresh { text-align: center; padding: @l99-sp-6; color: @l99-success; font-size: @l99-fs-sm; }
</style>

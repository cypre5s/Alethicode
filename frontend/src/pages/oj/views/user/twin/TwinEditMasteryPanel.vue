<template>
  <div class="te-panel" role="region" aria-label="编辑孪生掌握度">
    <h3 class="te-panel__title">编辑孪生判断</h3>
    <p class="te-panel__desc">系统不一定比你更了解你自己——觉得哪个判断不对，随时修正</p>

    <div v-if="loading" class="te-skeleton">
      <el-skeleton :rows="2" animated />
    </div>

    <div v-else class="te-overrides">
      <div v-for="item in overrides" :key="item.kc_id" class="te-override-row">
        <span class="te-kc-name">{{ item.kc_name }}</span>
        <span class="te-original">系统: {{ Math.round(item.original_mastery * 100) }}%</span>
        <span class="te-arrow">→</span>
        <span class="te-overridden">你说: {{ Math.round(item.overridden_mastery * 100) }}%</span>
      </div>
      <div v-if="overrides.length === 0" class="te-no-data">
        暂时还没有修正过。去星系图里点击任一知识点，就能告诉系统你的真实感受。
      </div>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'TwinEditMasteryPanel',
  data () {
    return { loading: false, overrides: [] }
  },
  mounted () { this.load() },
  methods: {
    async load () {
      this.loading = true
      try {
        const res = await api.getMasteryOverrides()
        this.overrides = res.data.data || []
      } catch { this.overrides = [] }
      finally { this.loading = false }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.te-panel {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-5;
  &__title { font-size: @l99-fs-lg; font-weight: 600; color: @l99-neutral-900; margin: 0 0 @l99-sp-1; }
  &__desc { font-size: @l99-fs-sm; color: @l99-neutral-500; margin: 0 0 @l99-sp-4; }
}
.te-skeleton { padding: @l99-sp-4; }
.te-overrides { display: flex; flex-direction: column; gap: @l99-sp-2; }
.te-override-row {
  display: flex; align-items: center; gap: @l99-sp-3; padding: @l99-sp-2 @l99-sp-3;
  border-radius: @l99-radius-sm; background: @l99-neutral-100; font-size: @l99-fs-sm;
}
.te-kc-name { flex: 1; color: @l99-neutral-900; font-weight: 500; }
.te-original { color: @l99-neutral-500; font-family: @l99-font-mono; }
.te-arrow { color: @l99-neutral-500; }
.te-overridden { color: @l99-primary; font-weight: 600; font-family: @l99-font-mono; }
.te-no-data { text-align: center; padding: @l99-sp-6; color: @l99-neutral-500; font-size: @l99-fs-sm; }
</style>

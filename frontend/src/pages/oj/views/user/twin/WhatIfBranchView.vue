<template>
  <div class="wi-view" role="region" aria-label="What-If 分叉模拟">
    <h3 class="wi-title">分叉模拟器</h3>
    <p class="wi-desc">如果你做对/做错这道题，知识掌握度会怎样变化？</p>

    <div class="wi-input-row">
      <input
        v-model.number="problemId"
        type="number"
        class="wi-input"
        placeholder="输入题目 ID"
      />
      <button type="button" class="wi-btn" :class="{ 'is-active': scenario === 'ac' }" @click="simulate('ac')">
        模拟 AC
      </button>
      <button type="button" class="wi-btn wi-btn--danger" :class="{ 'is-active': scenario === 'wa' }" @click="simulate('wa')">
        模拟 WA
      </button>
    </div>

    <div v-if="loading" class="wi-skeleton">
      <el-skeleton :rows="2" animated />
    </div>

    <div v-else-if="result" class="wi-result">
      <p class="wi-insight">{{ result.insight }}</p>
      <div v-if="result.affected_kcs && result.affected_kcs.length > 0" class="wi-kc-list">
        <div v-for="kc in result.affected_kcs" :key="kc.kc_id" class="wi-kc-row">
          <span class="wi-kc-name">{{ kc.kc_name }}</span>
          <span class="wi-kc-current">{{ Math.round(kc.current_mastery * 100) }}%</span>
          <span class="wi-kc-arrow" :class="{ 'wi-kc-arrow--up': kc.delta > 0, 'wi-kc-arrow--down': kc.delta < 0 }">
            {{ kc.delta > 0 ? '↑' : kc.delta < 0 ? '↓' : '→' }}
          </span>
          <span class="wi-kc-sim">{{ Math.round(kc.simulated_mastery * 100) }}%</span>
          <span class="wi-kc-delta" :class="{ 'wi-kc-delta--pos': kc.delta > 0, 'wi-kc-delta--neg': kc.delta < 0 }">
            {{ kc.delta > 0 ? '+' : '' }}{{ Math.round(kc.delta * 100) }}%
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'WhatIfBranchView',
  data () {
    return {
      problemId: null,
      scenario: null,
      loading: false,
      result: null
    }
  },
  methods: {
    async simulate (sc) {
      if (!this.problemId) return
      this.scenario = sc
      this.loading = true
      try {
        const res = await api.getWhatIfBranch({ problem_id: this.problemId, scenario: sc })
        this.result = res.data.data
      } catch {
        this.result = null
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.wi-view {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-5;
}
.wi-title { font-size: @l99-fs-lg; font-weight: 600; color: @l99-neutral-900; margin: 0 0 @l99-sp-1; }
.wi-desc { font-size: @l99-fs-sm; color: @l99-neutral-500; margin: 0 0 @l99-sp-4; }

.wi-input-row { display: flex; gap: @l99-sp-2; margin-bottom: @l99-sp-4; align-items: center; }
.wi-input {
  flex: 1; padding: @l99-sp-2 @l99-sp-3; border: 1px solid @l99-neutral-200; border-radius: @l99-radius-sm;
  font-size: @l99-fs-sm; &:focus { outline: none; border-color: @l99-primary; }
}
.wi-btn {
  padding: @l99-sp-2 @l99-sp-4; border: 1px solid @l99-success; border-radius: @l99-radius-sm;
  background: #fff; color: @l99-success; font-size: @l99-fs-sm; cursor: pointer;
  transition: all @l99-dur-fast @l99-ease;
  &:hover, &.is-active { background: @l99-success; color: #fff; }
  &--danger { border-color: @l99-danger; color: @l99-danger;
    &:hover, &.is-active { background: @l99-danger; color: #fff; }
  }
}

.wi-skeleton { padding: @l99-sp-4; }
.wi-insight { font-size: @l99-fs-md; color: @l99-neutral-900; font-weight: 500; margin: 0 0 @l99-sp-3; }

.wi-kc-list { display: flex; flex-direction: column; gap: @l99-sp-2; }
.wi-kc-row {
  display: flex; align-items: center; gap: @l99-sp-3; padding: @l99-sp-2 @l99-sp-3;
  border-radius: @l99-radius-sm; background: @l99-neutral-100; font-size: @l99-fs-sm;
}
.wi-kc-name { flex: 1; color: @l99-neutral-900; font-weight: 500; }
.wi-kc-current { color: @l99-neutral-500; font-family: @l99-font-mono; }
.wi-kc-arrow {
  font-size: @l99-fs-lg; color: @l99-neutral-500;
  &--up { color: @l99-success; }
  &--down { color: @l99-danger; }
}
.wi-kc-sim { color: @l99-primary; font-weight: 600; font-family: @l99-font-mono; }
.wi-kc-delta {
  font-family: @l99-font-mono; font-size: @l99-fs-xs; min-width: 40px; text-align: right;
  &--pos { color: @l99-success; }
  &--neg { color: @l99-danger; }
}
</style>

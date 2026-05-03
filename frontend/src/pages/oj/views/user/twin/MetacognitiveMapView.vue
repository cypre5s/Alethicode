<template>
  <div class="mc-map" role="region" aria-label="认知错觉地图">
    <h3 class="mc-map__title">认知错觉地图</h3>
    <p class="mc-map__subtitle">你的预测偏差分布</p>

    <div v-if="loading" class="mc-map__skeleton">
      <el-skeleton :rows="2" animated />
    </div>

    <template v-else-if="totalPredicts < 5">
      <div class="mc-map__min-data">
        <p>再做 {{ 5 - totalPredicts }} 个预测就能看到地图</p>
      </div>
    </template>

    <template v-else>
      <div class="mc-map__stats">
        <div class="mc-map__stat">
          <span class="mc-map__stat-num">{{ totalPredicts }}</span>
          <span class="mc-map__stat-label">总预测</span>
        </div>
        <div class="mc-map__stat">
          <span class="mc-map__stat-num">{{ exactMatchRate }}%</span>
          <span class="mc-map__stat-label">准确率</span>
        </div>
      </div>

      <div v-if="hotMisconceptions.length > 0" class="mc-map__heatmap">
        <div v-for="item in hotMisconceptions" :key="item.diff_kind" class="mc-map__heat-row">
          <span class="mc-map__heat-label">{{ diffKindLabel(item.diff_kind) }}</span>
          <div class="mc-map__heat-bar">
            <div
              class="mc-map__heat-fill"
              :style="{ width: (item.count / maxCount * 100) + '%' }"
            ></div>
          </div>
          <span class="mc-map__heat-count">{{ item.count }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'

const DIFF_LABELS = {
  partial: '部分正确',
  wrong_value: '值偏差',
  wrong_type: '类型错误',
  crash: '程序崩溃'
}

export default {
  name: 'MetacognitiveMapView',
  data () {
    return {
      loading: false,
      totalPredicts: 0,
      exactMatchRate: 0,
      hotMisconceptions: []
    }
  },
  computed: {
    maxCount () {
      return Math.max(1, ...this.hotMisconceptions.map(m => m.count))
    }
  },
  mounted () {
    this.loadMap()
  },
  methods: {
    async loadMap () {
      this.loading = true
      try {
        const res = await api.getMetacogMap()
        const d = res.data.data
        this.totalPredicts = d.total_predicts || 0
        this.exactMatchRate = Math.round((d.exact_match_rate || 0) * 100)
        this.hotMisconceptions = d.hot_misconceptions || []
      } catch {
        // keep defaults
      } finally {
        this.loading = false
      }
    },
    diffKindLabel (kind) {
      return DIFF_LABELS[kind] || kind
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.mc-map {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-5;

  &__title {
    font-size: @l99-fs-lg;
    font-weight: 600;
    color: @l99-neutral-900;
    margin: 0 0 @l99-sp-1;
  }
  &__subtitle {
    font-size: @l99-fs-sm;
    color: @l99-neutral-500;
    margin: 0 0 @l99-sp-4;
  }
  &__skeleton { padding: @l99-sp-4; }
  &__min-data {
    text-align: center;
    padding: @l99-sp-6;
    color: @l99-neutral-500;
    font-size: @l99-fs-sm;
  }

  &__stats {
    display: flex;
    gap: @l99-sp-6;
    margin-bottom: @l99-sp-5;
  }
  &__stat {
    text-align: center;
    &-num { display: block; font-size: @l99-fs-2xl; font-weight: 700; color: @l99-neutral-900; font-family: @l99-font-mono; }
    &-label { font-size: @l99-fs-xs; color: @l99-neutral-500; }
  }

  &__heatmap { display: flex; flex-direction: column; gap: @l99-sp-2; }
  &__heat-row { display: flex; align-items: center; gap: @l99-sp-3; }
  &__heat-label { width: 80px; font-size: @l99-fs-sm; color: @l99-neutral-700; flex-shrink: 0; }
  &__heat-bar {
    flex: 1;
    height: 12px;
    background: @l99-neutral-100;
    border-radius: 6px;
    overflow: hidden;
  }
  &__heat-fill {
    height: 100%;
    background: @l99-danger;
    border-radius: 6px;
    transition: width @l99-dur-slow @l99-ease;
  }
  &__heat-count {
    width: 28px;
    text-align: right;
    font-size: @l99-fs-xs;
    color: @l99-neutral-500;
    font-family: @l99-font-mono;
  }
}
</style>

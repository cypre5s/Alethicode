<template>
  <div class="lh-card" role="region" aria-label="学习健康度仪表盘">
    <div v-if="loading" class="lh-skeleton">
      <el-skeleton :rows="3" animated />
    </div>

    <template v-else>
      <div class="lh-grid">
        <div class="lh-cell lh-cell--mastery">
          <h3 class="lh-cell__title">整体掌握度</h3>
          <div class="lh-mastery-ring">
            <svg viewBox="0 0 80 80" class="lh-ring-svg">
              <circle cx="40" cy="40" r="34" fill="none" stroke="#E5E7EB" stroke-width="6"/>
              <circle
                cx="40" cy="40" r="34"
                fill="none"
                :stroke="masteryColor"
                stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="ringDash"
                transform="rotate(-90 40 40)"
              />
            </svg>
            <span class="lh-mastery-percent">{{ masteryPercent }}%</span>
          </div>
          <div v-if="topKcs.length > 0" class="lh-top-kcs">
            <div v-for="kc in topKcs" :key="kc.name" class="lh-kc-row">
              <span class="lh-kc-name">{{ kc.name }}</span>
              <span class="lh-kc-val">{{ Math.round(kc.mastery * 100) }}%</span>
            </div>
          </div>
        </div>

        <div class="lh-cell lh-cell--freq">
          <h3 class="lh-cell__title">30 天活跃度</h3>
          <div class="lh-stat-group">
            <div class="lh-stat">
              <span class="lh-stat__num">{{ frequency.submits_30d || 0 }}</span>
              <span class="lh-stat__label">提交</span>
            </div>
            <div class="lh-stat">
              <span class="lh-stat__num">{{ frequency.active_days || 0 }}</span>
              <span class="lh-stat__label">活跃天</span>
            </div>
            <div class="lh-stat">
              <span class="lh-stat__num">{{ frequency.streak_days || 0 }}</span>
              <span class="lh-stat__label">连续天</span>
            </div>
          </div>
        </div>

        <div class="lh-cell lh-cell--diff">
          <h3 class="lh-cell__title">难度曲线</h3>
          <div v-if="difficultyCurve.length === 0" class="lh-no-data">数据不足</div>
          <div v-else class="lh-sparkline">
            <svg :viewBox="`0 0 ${sparkWidth} 60`" class="lh-sparkline-svg">
              <polyline :points="sparkPoints" fill="none" stroke="#0F4C81" stroke-width="2" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>

        <div class="lh-cell lh-cell--due">
          <h3 class="lh-cell__title">待复习</h3>
          <div v-if="dueReviews.length === 0" class="lh-no-data">暂无待复习</div>
          <div v-else class="lh-due-list">
            <div
              v-for="item in dueReviews"
              :key="item.package_id"
              class="lh-due-item"
              tabindex="0"
              @click="$router.push({ name: 'error-review-package', query: { id: item.package_id } })"
              @keydown.enter="$router.push({ name: 'error-review-package', query: { id: item.package_id } })"
            >
              <span class="lh-due-title">{{ item.title || `复习包 #${item.package_id}` }}</span>
              <span :class="['lh-due-tag', { 'lh-due-tag--overdue': isOverdue(item.fsrs_due_at) }]">
                {{ isOverdue(item.fsrs_due_at) ? '已过期' : '待复习' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'LearningHealthCard',
  data () {
    return {
      loading: false,
      overall: 0,
      topKcs: [],
      frequency: {},
      difficultyCurve: [],
      dueReviews: []
    }
  },
  computed: {
    masteryPercent () { return Math.round(this.overall * 100) },
    masteryColor () {
      if (this.overall > 0.85) return '#10B981'
      if (this.overall > 0.6) return '#0F4C81'
      if (this.overall > 0.3) return '#F59E0B'
      return '#6B7280'
    },
    ringDash () {
      const circumference = 2 * Math.PI * 34
      const filled = circumference * this.overall
      return `${filled} ${circumference - filled}`
    },
    sparkWidth () { return Math.max(100, this.difficultyCurve.length * 20) },
    sparkPoints () {
      if (this.difficultyCurve.length === 0) return ''
      const maxVal = Math.max(...this.difficultyCurve.map(d => d.avg_diff), 1)
      return this.difficultyCurve.map((d, i) => {
        const x = (i / Math.max(this.difficultyCurve.length - 1, 1)) * (this.sparkWidth - 10) + 5
        const y = 55 - (d.avg_diff / maxVal) * 50
        return `${x},${y}`
      }).join(' ')
    }
  },
  mounted () {
    this.loadHealth()
  },
  methods: {
    async loadHealth () {
      this.loading = true
      try {
        const res = await api.getTwinHealth()
        const d = res.data.data
        this.overall = d.mastery?.overall || 0
        this.topKcs = d.mastery?.by_kc_top5 || []
        this.frequency = d.frequency || {}
        this.difficultyCurve = d.difficulty_curve || []
        this.dueReviews = d.due_reviews || []
      } catch {
        // keep defaults
      } finally {
        this.loading = false
      }
    },
    isOverdue (dueAt) {
      if (!dueAt) return false
      return new Date(dueAt) < new Date()
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.lh-card {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-5;
}

.lh-skeleton { padding: @l99-sp-4; }

.lh-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: @l99-sp-5;
}

.lh-cell {
  &__title {
    font-size: @l99-fs-sm;
    font-weight: 600;
    color: @l99-neutral-700;
    margin: 0 0 @l99-sp-3;
    text-transform: uppercase;
    letter-spacing: 0.3px;
  }
}

.lh-mastery-ring {
  position: relative;
  width: 80px;
  height: 80px;
  margin: 0 auto @l99-sp-3;
}
.lh-ring-svg { width: 100%; height: 100%; }
.lh-mastery-percent {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  font-size: @l99-fs-xl;
  font-weight: 700;
  color: @l99-neutral-900;
  font-family: @l99-font-mono;
}

.lh-top-kcs { display: flex; flex-direction: column; gap: @l99-sp-1; }
.lh-kc-row { display: flex; justify-content: space-between; font-size: @l99-fs-xs; }
.lh-kc-name { color: @l99-neutral-700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 120px; }
.lh-kc-val { color: @l99-neutral-500; font-family: @l99-font-mono; }

.lh-stat-group { display: flex; gap: @l99-sp-5; }
.lh-stat {
  text-align: center;
  &__num { display: block; font-size: @l99-fs-2xl; font-weight: 700; color: @l99-neutral-900; font-family: @l99-font-mono; }
  &__label { font-size: @l99-fs-xs; color: @l99-neutral-500; }
}

.lh-sparkline { width: 100%; }
.lh-sparkline-svg { width: 100%; height: 60px; }

.lh-no-data {
  font-size: @l99-fs-sm;
  color: @l99-neutral-500;
  text-align: center;
  padding: @l99-sp-4;
}

.lh-due-list { display: flex; flex-direction: column; gap: @l99-sp-2; }
.lh-due-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: @l99-sp-2 @l99-sp-3;
  border-radius: @l99-radius-sm;
  cursor: pointer;
  transition: background @l99-dur-fast @l99-ease;
  &:hover, &:focus-visible { background: @l99-neutral-100; outline: none; }
}
.lh-due-title { font-size: @l99-fs-sm; color: @l99-neutral-900; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lh-due-tag {
  flex-shrink: 0;
  font-size: @l99-fs-xs;
  padding: 1px 6px;
  border-radius: 8px;
  background: @l99-warn;
  color: #fff;
  &--overdue { background: @l99-danger; }
}

@media (max-width: 767px) {
  .lh-grid { grid-template-columns: 1fr; }
}
</style>

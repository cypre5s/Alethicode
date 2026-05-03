<template>
  <div class="lt-container" role="region" aria-label="学习时间轴">
    <div class="lt-toolbar">
      <div class="lt-toolbar__dates">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="false"
          :disabled-date="disableFuture"
          size="small"
          @change="loadTimeline"
        />
      </div>
      <div class="lt-toolbar__filters">
        <button
          v-for="kind in kindOptions"
          :key="kind.value"
          type="button"
          :class="['lt-filter-chip', { 'is-active': activeKinds.includes(kind.value) }]"
          :aria-pressed="activeKinds.includes(kind.value)"
          @click="toggleKind(kind.value)"
        >
          <span class="lt-filter-dot" :style="{ backgroundColor: kind.color }" aria-hidden="true"></span>
          {{ kind.label }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="lt-skeleton" aria-busy="true">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="error" class="lt-error" role="alert">
      <p>加载时间轴时出错</p>
      <button type="button" class="lt-retry-btn" @click="loadTimeline">重试</button>
    </div>

    <div v-else-if="events.length === 0" class="lt-empty">
      <div class="lt-empty__icon" aria-hidden="true">
        <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
          <rect x="8" y="12" width="48" height="40" rx="8" stroke="#0F4C81" stroke-width="2" fill="#E5EEF7"/>
          <line x1="20" y1="24" x2="44" y2="24" stroke="#0F4C81" stroke-width="2" stroke-linecap="round"/>
          <line x1="20" y1="32" x2="36" y2="32" stroke="#0F4C81" stroke-width="2" stroke-linecap="round" opacity="0.5"/>
          <line x1="20" y1="40" x2="40" y2="40" stroke="#0F4C81" stroke-width="2" stroke-linecap="round" opacity="0.3"/>
        </svg>
      </div>
      <p class="lt-empty__text">你还没有学习记录哦</p>
      <router-link to="/problem" class="lt-empty__cta">去做第一道题 →</router-link>
    </div>

    <div v-else class="lt-events" role="list" aria-label="时间轴事件列表">
      <div v-for="(group, dateKey) in groupedEvents" :key="dateKey" class="lt-day-group">
        <div class="lt-day-label">{{ dateKey }}</div>
        <div class="lt-day-line" aria-hidden="true"></div>
        <LearningTimelineEvent
          v-for="event in group"
          :key="event.event_id"
          :event="event"
          @open="handleOpenEvent"
        />
      </div>

      <div v-if="hasMore" class="lt-load-more">
        <button type="button" class="lt-load-more-btn" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'
import LearningTimelineEvent from './LearningTimelineEvent.vue'

const KIND_OPTIONS = [
  { value: 'submission', label: '提交', color: '#10B981' },
  { value: 'memory', label: '记忆', color: '#F39A2C' },
  { value: 'ai_event', label: 'AI', color: '#0F4C81' },
  { value: 'notebook', label: '笔记', color: '#6B7280' }
]

function formatDate (d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function defaultDateRange () {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - 30)
  return [from, to]
}

function toDateKey (isoStr) {
  const d = new Date(isoStr)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const weekdays = ['日', '一', '二', '三', '四', '五', '六']
  return `${m}月${day}日 周${weekdays[d.getDay()]}`
}

export default {
  name: 'LearningTimeline',
  components: { LearningTimelineEvent },
  data () {
    return {
      dateRange: defaultDateRange(),
      activeKinds: ['submission', 'memory', 'ai_event', 'notebook'],
      kindOptions: KIND_OPTIONS,
      events: [],
      loading: false,
      loadingMore: false,
      error: false,
      hasMore: false,
      currentLimit: 200
    }
  },
  computed: {
    groupedEvents () {
      const groups = {}
      for (const e of this.events) {
        const key = toDateKey(e.event_at)
        if (!groups[key]) groups[key] = []
        groups[key].push(e)
      }
      return groups
    }
  },
  mounted () {
    this.loadTimeline()
  },
  methods: {
    disableFuture (date) {
      return date > new Date()
    },
    toggleKind (kind) {
      const idx = this.activeKinds.indexOf(kind)
      if (idx >= 0) {
        if (this.activeKinds.length > 1) {
          this.activeKinds.splice(idx, 1)
        }
      } else {
        this.activeKinds.push(kind)
      }
      this.loadTimeline()
    },
    async loadTimeline () {
      if (!this.dateRange || this.dateRange.length < 2) return
      this.loading = true
      this.error = false
      this.currentLimit = 200
      try {
        const res = await api.getLearningTimeline({
          from: formatDate(this.dateRange[0]),
          to: formatDate(this.dateRange[1]),
          kinds: this.activeKinds,
          limit: this.currentLimit
        })
        this.events = res.data.data.events
        this.hasMore = res.data.data.has_more
      } catch {
        this.error = true
        this.events = []
      } finally {
        this.loading = false
      }
    },
    async loadMore () {
      if (this.loadingMore) return
      this.loadingMore = true
      this.currentLimit += 200
      try {
        const res = await api.getLearningTimeline({
          from: formatDate(this.dateRange[0]),
          to: formatDate(this.dateRange[1]),
          kinds: this.activeKinds,
          limit: this.currentLimit
        })
        this.events = res.data.data.events
        this.hasMore = res.data.data.has_more
      } catch {
        // keep existing data
      } finally {
        this.loadingMore = false
      }
    },
    handleOpenEvent (event) {
      if (event.event_kind === 'submission' && event.event_id) {
        this.$router.push({ name: 'submission-details', params: { id: event.event_id } })
      } else if (event.problem_id) {
        this.$router.push({ name: 'problem-details', params: { problemID: event.problem_id } })
      }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.lt-container {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-6;
}

.lt-toolbar {
  display: flex;
  align-items: center;
  gap: @l99-sp-4;
  margin-bottom: @l99-sp-6;
  flex-wrap: wrap;

  &__dates {
    flex-shrink: 0;
  }
  &__filters {
    display: flex;
    gap: @l99-sp-2;
    flex-wrap: wrap;
  }
}

.lt-filter-chip {
  display: inline-flex;
  align-items: center;
  gap: @l99-sp-1;
  padding: @l99-sp-1 @l99-sp-3;
  border: 1px solid @l99-neutral-200;
  border-radius: 20px;
  background: #fff;
  font-size: @l99-fs-sm;
  color: @l99-neutral-700;
  cursor: pointer;
  transition: all @l99-dur-fast @l99-ease;

  &.is-active {
    border-color: @l99-primary;
    background: @l99-primary-soft;
    color: @l99-primary;
  }
  &:hover {
    border-color: @l99-primary;
  }
}

.lt-filter-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.lt-skeleton {
  padding: @l99-sp-4 0;
}

.lt-error {
  text-align: center;
  padding: @l99-sp-10 0;
  color: @l99-neutral-700;
  font-size: @l99-fs-md;
}

.lt-retry-btn {
  margin-top: @l99-sp-3;
  padding: @l99-sp-2 @l99-sp-4;
  border: 1px solid @l99-primary;
  border-radius: @l99-radius-sm;
  background: #fff;
  color: @l99-primary;
  font-size: @l99-fs-sm;
  cursor: pointer;
  transition: background @l99-dur-fast @l99-ease;
  &:hover { background: @l99-primary-soft; }
}

.lt-empty {
  text-align: center;
  padding: @l99-sp-10 0;

  &__icon { margin-bottom: @l99-sp-4; }
  &__text {
    font-size: @l99-fs-md;
    color: @l99-neutral-700;
    margin-bottom: @l99-sp-3;
  }
  &__cta {
    display: inline-block;
    padding: @l99-sp-2 @l99-sp-5;
    background: @l99-primary;
    color: #fff;
    border-radius: @l99-radius-sm;
    font-size: @l99-fs-sm;
    text-decoration: none;
    transition: opacity @l99-dur-fast @l99-ease;
    &:hover { opacity: 0.9; }
  }
}

.lt-day-group {
  position: relative;
  padding-left: @l99-sp-6;
  margin-bottom: @l99-sp-4;
}

.lt-day-label {
  font-size: @l99-fs-sm;
  font-weight: 600;
  color: @l99-neutral-900;
  margin-bottom: @l99-sp-2;
}

.lt-day-line {
  position: absolute;
  left: 3px;
  top: 24px;
  bottom: 0;
  width: 2px;
  background: @l99-neutral-200;
  border-radius: 1px;
}

.lt-load-more {
  text-align: center;
  padding: @l99-sp-4 0;
}

.lt-load-more-btn {
  padding: @l99-sp-2 @l99-sp-6;
  border: 1px solid @l99-neutral-200;
  border-radius: @l99-radius-sm;
  background: #fff;
  color: @l99-neutral-700;
  font-size: @l99-fs-sm;
  cursor: pointer;
  transition: all @l99-dur-fast @l99-ease;
  &:hover {
    border-color: @l99-primary;
    color: @l99-primary;
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

@media (max-width: 767px) {
  .lt-container { padding: @l99-sp-4; }
  .lt-toolbar {
    flex-direction: column;
    align-items: stretch;
    &__filters { justify-content: flex-start; }
  }
}
</style>

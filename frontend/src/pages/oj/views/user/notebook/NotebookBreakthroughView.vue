<template>
  <div class="nbt-view">
    <div v-if="loading" class="nbt-skeleton-list">
      <div v-for="i in 3" :key="i" class="nbt-skeleton-item"></div>
    </div>
    <div v-else-if="!entries.length" class="nbt-empty">
      <span class="nbt-empty-icon" aria-hidden="true">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15.09 14c.18-.98.65-1.74 1.41-2.5A4.65 4.65 0 0 0 18 8 6 6 0 0 0 6 8c0 1 .23 2.23 1.5 3.5A4.61 4.61 0 0 1 8.91 14"/></svg>
      </span>
      <div class="nbt-empty-title">还没有顿悟记录</div>
      <div class="nbt-empty-sub">解决一道难题后，灵光一现的体会会自动出现在这里</div>
    </div>
    <div v-else class="nbt-timeline">
      <div v-for="(entry, i) in entries" :key="entry.id" class="nbt-item">
        <div class="nbt-rail" aria-hidden="true">
          <span class="nbt-node" :class="{ 'is-first': i === 0 }"></span>
        </div>
        <div class="nbt-block">
          <div class="nbt-date">{{ formatDate(entry.create_time) }}</div>
          <div class="nbt-card">
            <p class="nbt-insight">{{ entry.breakthrough_insight }}</p>
            <div class="nbt-foot">
              <router-link
                v-if="entry.problem_id"
                :to="{ name: 'problem-details', params: { problemID: entry.problem_id } }"
                class="nbt-problem-link"
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
                查看题目
              </router-link>
              <span v-if="entry.fsrs_due_at" class="nbt-fsrs">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                下次提醒 · {{ formatDate(entry.fsrs_due_at) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <BreakthroughReviewModal
      v-if="reviewEntry"
      :entry="reviewEntry"
      @close="reviewEntry = null"
      @rated="onRated"
    />
  </div>
</template>

<script>
import api from '@oj/api'
import BreakthroughReviewModal from './BreakthroughReviewModal.vue'

export default {
  name: 'NotebookBreakthroughView',
  components: { BreakthroughReviewModal },
  data () {
    return { loading: false, entries: [], reviewEntry: null }
  },
  mounted () { this.load() },
  methods: {
    async load () {
      this.loading = true
      try {
        const res = await api.getLearnerNotebook({ entry_type: 'breakthrough' })
        this.entries = (res.data && res.data.data && res.data.data.entries) || []
      } finally { this.loading = false }
    },
    formatDate (val) {
      if (!val) return ''
      const d = new Date(val)
      return Number.isNaN(d.getTime()) ? val : d.toLocaleDateString()
    },
    onRated () {
      this.reviewEntry = null
      this.load()
    }
  }
}
</script>

<style lang="less" scoped>
.nbt-view { padding: 4px 2px; }

.nbt-skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.nbt-skeleton-item {
  height: 88px;
  border-radius: var(--nb-radius-md);
  background: linear-gradient(90deg, var(--nb-bg-subtle) 25%, rgba(196, 181, 253, 0.18) 50%, var(--nb-bg-subtle) 75%);
  background-size: 200% 100%;
  animation: nbt-shimmer 1.4s linear infinite;
}

.nbt-empty {
  background: var(--nb-bg-surface);
  border: 1px dashed var(--nb-border-mid);
  border-radius: var(--nb-radius-lg);
  text-align: center;
  padding: 60px 20px;
  color: var(--nb-color-text-dim);
}

.nbt-empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: linear-gradient(135deg, #ede9fe 0%, #fce7f3 100%);
  color: var(--nb-color-primary-strong);
  margin-bottom: 12px;
}

.nbt-empty-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--nb-color-text);
}

.nbt-empty-sub {
  font-size: 12px;
  color: var(--nb-color-text-dim);
  margin-top: 4px;
}

.nbt-timeline {
  display: flex;
  flex-direction: column;
  position: relative;
}

.nbt-item {
  display: flex;
  align-items: stretch;
  gap: 14px;
  position: relative;

  &:last-child .nbt-rail::before {
    display: none;
  }
}

.nbt-rail {
  position: relative;
  width: 36px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;

  &::before {
    content: '';
    position: absolute;
    top: 28px;
    bottom: -14px;
    left: 50%;
    width: 2px;
    transform: translateX(-50%);
    background: linear-gradient(180deg, rgba(196, 181, 253, 0.6) 0%, rgba(196, 181, 253, 0.2) 100%);
  }
}

.nbt-node {
  position: relative;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--nb-grad-primary);
  box-shadow: 0 0 0 4px rgba(124, 58, 237, 0.14);
  margin-top: 14px;
  flex-shrink: 0;

  &.is-first {
    animation: nbt-pulse 2.4s ease-in-out infinite;
  }
}

.nbt-block {
  flex: 1;
  min-width: 0;
  padding-bottom: 14px;
}

.nbt-date {
  font-size: 11px;
  color: var(--nb-color-text-dim);
  font-weight: 600;
  letter-spacing: 0.4px;
  margin-bottom: 6px;
}

.nbt-card {
  background: linear-gradient(135deg, #faf5ff 0%, #fdf4ff 100%);
  border: 1px solid var(--nb-border-mid);
  border-radius: var(--nb-radius-md);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: transform var(--nb-transition), box-shadow var(--nb-transition);

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--nb-shadow-mid);
  }
}

.nbt-insight {
  font-size: 14px;
  color: var(--nb-color-text);
  line-height: 1.7;
  margin: 0;
}

.nbt-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.nbt-problem-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--nb-color-primary-strong);
  font-weight: 600;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.10);
  text-decoration: none;
  transition: all var(--nb-transition);

  &:hover {
    background: rgba(99, 102, 241, 0.18);
    color: var(--nb-color-primary-strong);
  }
}

.nbt-fsrs {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--nb-color-text-dim);
}

@keyframes nbt-pulse {
  0%, 100% {
    box-shadow: 0 0 0 4px rgba(124, 58, 237, 0.14);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(124, 58, 237, 0.06);
  }
}

@keyframes nbt-shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .nbt-node,
  .nbt-card,
  .nbt-skeleton-item {
    animation: none !important;
    transition: none !important;
  }
}
</style>

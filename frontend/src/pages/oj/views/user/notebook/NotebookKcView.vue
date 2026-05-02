<template>
  <div class="nkv-view">
    <div class="nkv-toolbar">
      <div class="nkv-tab-group" role="tablist" aria-label="排序方式">
        <button
          v-for="opt in sortOptions"
          :key="opt.value"
          type="button"
          role="tab"
          :aria-selected="sortBy === opt.value"
          :class="['nkv-tab', { 'is-active': sortBy === opt.value }]"
          @click="changeSort(opt.value)"
        >{{ opt.label }}</button>
      </div>
      <span v-if="unmappedCount > 0" class="nkv-unmapped">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        {{ unmappedCount }} 条未关联知识点
      </span>
    </div>

    <div v-if="loading" class="nkv-skeleton-grid">
      <div v-for="i in 6" :key="i" class="nkv-skeleton-card"></div>
    </div>
    <div v-else-if="!kcGroups.length" class="nkv-empty">
      <span class="nkv-empty-icon" aria-hidden="true">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
      </span>
      <div class="nkv-empty-title">暂无按知识点归类的错题</div>
      <div class="nkv-empty-sub">每次提交后，错题会自动关联到对应的知识点</div>
    </div>
    <div v-else class="nkv-grid">
      <NotebookKcCard
        v-for="kc in kcGroups"
        :key="kc.kc_id"
        :kc="kc"
        @expand="expandKcId = kc.kc_id"
      />
    </div>

    <NotebookKcExpandModal
      v-if="expandKcId"
      :kc-id="expandKcId"
      :kc-groups="kcGroups"
      @close="expandKcId = null"
    />
  </div>
</template>

<script>
import api from '@oj/api'
import NotebookKcCard from './NotebookKcCard.vue'
import NotebookKcExpandModal from './NotebookKcExpandModal.vue'

const SORT_OPTIONS = [
  { value: 'weak_first', label: '薄弱优先' },
  { value: 'most_errors', label: '错题最多' },
  { value: 'recent', label: '最近活跃' }
]

export default {
  name: 'NotebookKcView',
  components: { NotebookKcCard, NotebookKcExpandModal },
  data () {
    return {
      loading: false,
      kcGroups: [],
      unmappedCount: 0,
      sortBy: 'weak_first',
      sortOptions: SORT_OPTIONS,
      expandKcId: null
    }
  },
  mounted () { this.load() },
  methods: {
    changeSort (value) {
      this.sortBy = value
      this.load()
    },
    async load () {
      this.loading = true
      try {
        const res = await api.getNotebookByKc({ sort: this.sortBy })
        const data = res.data && res.data.data
        this.kcGroups = (data && data.kc_groups) || []
        this.unmappedCount = (data && data.unmapped_count) || 0
      } finally { this.loading = false }
    }
  }
}
</script>

<style lang="less" scoped>
.nkv-view { padding: 4px 0; }

.nkv-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.nkv-tab-group {
  display: inline-flex;
  background: var(--nb-bg-surface);
  border: 1px solid var(--nb-border-soft);
  border-radius: 999px;
  padding: 3px;
  gap: 2px;
  box-shadow: var(--nb-shadow-soft);
}

.nkv-tab {
  border: none;
  background: transparent;
  color: var(--nb-color-text-mid);
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all var(--nb-transition);

  &:hover {
    color: var(--nb-color-primary-strong);
  }

  &.is-active {
    background: var(--nb-grad-primary);
    color: #fff;
    box-shadow: 0 2px 8px rgba(124, 58, 237, 0.28);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--nb-shadow-glow);
  }
}

.nkv-unmapped {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--nb-color-warm);
  background: rgba(245, 158, 11, 0.1);
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.nkv-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.nkv-skeleton-card {
  height: 180px;
  border-radius: var(--nb-radius-lg);
  background: linear-gradient(90deg, var(--nb-bg-subtle) 25%, rgba(196, 181, 253, 0.18) 50%, var(--nb-bg-subtle) 75%);
  background-size: 200% 100%;
  animation: nkv-shimmer 1.4s linear infinite;
}

.nkv-empty {
  background: var(--nb-bg-surface);
  border: 1px dashed var(--nb-border-mid);
  border-radius: var(--nb-radius-lg);
  text-align: center;
  padding: 50px 20px;
  color: var(--nb-color-text-dim);
}

.nkv-empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: var(--nb-bg-subtle);
  color: var(--nb-color-primary);
  margin-bottom: 12px;
}

.nkv-empty-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--nb-color-text);
}

.nkv-empty-sub {
  font-size: 12px;
  color: var(--nb-color-text-dim);
  margin-top: 4px;
}

.nkv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

@keyframes nkv-shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .nkv-tab,
  .nkv-skeleton-card {
    transition: none !important;
    animation: none !important;
  }
}
</style>

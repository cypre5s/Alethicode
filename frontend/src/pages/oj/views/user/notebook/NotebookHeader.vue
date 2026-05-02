<template>
  <header class="nbh-hero" role="banner">
    <div class="nbh-hero-bg" aria-hidden="true">
      <span class="nbh-hero-blob nbh-blob-a"></span>
      <span class="nbh-hero-blob nbh-blob-b"></span>
      <span class="nbh-hero-blob nbh-blob-c"></span>
    </div>

    <div class="nbh-hero-top">
      <div class="nbh-title-block">
        <div class="nbh-eyebrow">学习成长追踪</div>
        <h1 class="nbh-title">个性化错题本</h1>
        <p class="nbh-subtitle">每一次出错都是进步的脚印——记录、复盘、攻克</p>
      </div>

      <div class="nbh-actions">
        <button type="button" class="nbh-action-btn" :disabled="loading" @click="$emit('refresh')">
          <span class="nbh-action-icon" :class="{ 'is-spinning': loading }">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7"/><polyline points="21 4 21 10 15 10"/></svg>
          </span>
          <span>刷新</span>
        </button>
        <button type="button" class="nbh-action-btn" @click="$emit('open-add-modal')">
          <span class="nbh-action-icon">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          </span>
          <span>手动添加</span>
        </button>
        <button type="button" class="nbh-action-btn nbh-action-warm" :disabled="creatingReview" @click="$emit('open-review-package')">
          <span class="nbh-action-icon">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg>
          </span>
          <span>{{ creatingReview ? '生成中...' : '错题强化训练' }}</span>
        </button>
        <button type="button" class="nbh-action-btn nbh-action-primary" @click="$emit('export')">
          <span class="nbh-action-icon">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          </span>
          <span>导出 JSON</span>
        </button>
      </div>
    </div>

    <nav class="nbh-tabs" role="tablist" aria-label="错题本视图切换">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        type="button"
        role="tab"
        :aria-selected="viewMode === tab.value"
        :class="['nbh-tab', { 'is-active': viewMode === tab.value }]"
        @click="$emit('change-view', tab.value)"
      >
        <span class="nbh-tab-icon" v-html="tab.icon" aria-hidden="true"></span>
        <span>{{ tab.label }}</span>
      </button>
    </nav>
  </header>
</template>

<script>
const TABS = [
  {
    value: 'calendar',
    label: '日历视图',
    icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>'
  },
  {
    value: 'archive',
    label: '错题档案',
    icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>'
  }
]

export default {
  name: 'NotebookHeader',
  emits: ['change-view', 'refresh', 'open-add-modal', 'open-review-package', 'export'],
  props: {
    viewMode: { type: String, required: true },
    loading: { type: Boolean, default: false },
    creatingReview: { type: Boolean, default: false },
    stats: {
      type: Object,
      default: () => ({ total: 0, conquered: 0, dueCount: 0, breakthroughCount: 0, streakDays: 0 })
    }
  },
  data () {
    return { tabs: TABS }
  }
}
</script>

<style src="./styles/notebookHeader.less" lang="less" scoped></style>

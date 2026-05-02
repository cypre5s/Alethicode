<template>
  <div class="nav-root">
    <div v-if="!entries.length" class="nav-empty">
      <div class="nav-empty-icon">📓</div>
      <div class="nav-empty-title">暂无错题记录</div>
      <div class="nav-empty-sub">继续刷题，错误记录会自动收集到这里</div>
    </div>

    <template v-else>
      <div class="nav-sidebar">
        <div class="nav-sidebar-title">错题目录</div>
        <div
          v-for="group in groupedEntries"
          :key="group.key"
          :class="['nav-sidebar-item', { 'is-active': selectedGroupKey === group.key }]"
          @click="$emit('select-group', group.key)"
        >
          <div class="nav-sidebar-top">
            <span class="nav-sidebar-prob">{{ group.problemId || '-' }}</span>
            <span class="nav-sidebar-lang" :class="getLangClass(group.language)">{{ group.language }}</span>
            <span class="nav-sidebar-count">{{ group.items.length }}次</span>
          </div>
          <div class="nav-sidebar-tags">
            <span
              v-for="cat in group.categories"
              :key="cat"
              class="nav-sidebar-tag"
              :class="getTagClass(cat)"
            >{{ getCategoryLabel(cat) }}</span>
          </div>
          <div class="nav-sidebar-time">{{ formatTime(group.latestTime) }}</div>
        </div>
      </div>

      <div class="nav-detail">
        <div v-if="!selectedGroup" class="nav-detail-empty">
          点击左侧题目查看错题详情
        </div>
        <div v-else>
          <div class="nav-detail-header">
            <router-link
              v-if="selectedGroup.problemId"
              :to="{ name: 'problem-details', params: { problemID: selectedGroup.problemId } }"
              class="nav-detail-title-link"
            >题目 {{ selectedGroup.problemId }}</router-link>
            <span v-else class="nav-detail-title">题目 -</span>
            <span class="nav-detail-lang" :class="getLangClass(selectedGroup.language)">{{ selectedGroup.language }}</span>
            <span class="nav-detail-count">{{ selectedGroup.items.length }} 次错误</span>
            <span v-if="isGroupConquered(selectedGroup)" class="nav-detail-badge nav-conquered">已攻克</span>
            <span v-else-if="selectedGroup.problemId" class="nav-detail-badge nav-unconquered">待攻克</span>
            <router-link
              v-if="selectedGroup.problemId"
              :to="{ name: 'problem-details', params: { problemID: selectedGroup.problemId }, query: { rechallenge: '1' } }"
              class="nav-rechallenge-btn"
            >重做此题</router-link>
          </div>

          <div class="nav-records">
            <NotebookEntryCard
              v-for="(item, idx) in selectedGroup.items"
              :key="item.id"
              :entry="item"
              :index="idx"
              :class-freq-pct="classFreqMap[item.error_taxonomy] || 0"
              :generating="generatingReflectionId === item.id"
              @remove="$emit('remove-entry', $event)"
              @save-reflection="$emit('save-reflection', $event)"
              @generate-reflection="$emit('generate-reflection', $event)"
              @add-tag="$emit('add-tag', $event)"
              @remove-tag="$emit('remove-tag', $event)"
            />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import NotebookEntryCard from './NotebookEntryCard.vue'
import { formatTime, getLangClass, getTagClass, getCategoryLabel } from './notebookFormatters.js'

export default {
  name: 'NotebookArchiveView',
  components: { NotebookEntryCard },
  emits: ['select-group', 'remove-entry', 'save-reflection', 'generate-reflection', 'add-tag', 'remove-tag'],
  props: {
    entries: { type: Array, default: () => [] },
    selectedGroupKey: { type: String, default: '' },
    classFreqMap: { type: Object, default: () => ({}) },
    generatingReflectionId: { type: String, default: '' }
  },
  computed: {
    groupedEntries () {
      const map = new Map()
      for (const e of this.entries) {
        const key = (e.problem_id || 'none') + '|' + (e.language || '')
        if (!map.has(key)) {
          map.set(key, {
            key,
            problemId: e.problem_id,
            language: e.language || '-',
            items: [],
            categories: new Set(),
            latestTime: e.create_time
          })
        }
        const g = map.get(key)
        g.items.push(e)
        if (e.error_taxonomy) g.categories.add(e.error_taxonomy)
        if (e.create_time > g.latestTime) g.latestTime = e.create_time
      }
      const groups = Array.from(map.values())
      for (const g of groups) {
        g.categories = Array.from(g.categories)
        g.items.sort((a, b) => (a.create_time || '').localeCompare(b.create_time || ''))
      }
      groups.sort((a, b) => (b.latestTime || '').localeCompare(a.latestTime || ''))
      return groups
    },
    selectedGroup () {
      if (!this.selectedGroupKey) return null
      return this.groupedEntries.find(g => g.key === this.selectedGroupKey) || null
    }
  },
  methods: {
    formatTime,
    getLangClass,
    getTagClass,
    getCategoryLabel,
    isGroupConquered (group) {
      return group && group.items.length > 0 && group.items.every(item => item.conquered)
    }
  }
}
</script>

<style lang="less" scoped>
.nav-root { display: flex; min-height: 480px; background: #fff; border: 1px solid #e8eaed; border-radius: 12px; overflow: hidden; }
.nav-empty { padding: 60px 20px; text-align: center; color: #94a3b8; flex: 1; }
.nav-empty-icon { font-size: 32px; margin-bottom: 8px; }
.nav-empty-title { font-size: 15px; font-weight: 600; color: #5f6368; margin-bottom: 6px; }
.nav-empty-sub { font-size: 12px; }

.nav-sidebar {
  width: 300px; flex-shrink: 0;
  border-right: 1px solid #e8eaed;
  background: #fafbff; overflow-y: auto;
}
.nav-sidebar-title {
  padding: 14px 16px; font-size: 13px; font-weight: 600; color: #1a1d2e;
  border-bottom: 1px solid #f1f5f9;
  position: sticky; top: 0; background: #fafbff; z-index: 1;
}
.nav-sidebar-item {
  padding: 12px 16px; cursor: pointer;
  border-bottom: 1px solid #f1f5f9; transition: background .15s;
  &:hover { background: #f1f5f9; }
  &.is-active { background: rgba(26, 115, 232, .08); border-left: 3px solid #1a73e8; padding-left: 13px; }
}
.nav-sidebar-top { display: flex; align-items: center; gap: 8px; }
.nav-sidebar-prob { font-size: 14px; font-weight: 600; color: #1a1d2e; }
.nav-sidebar-lang {
  font-size: 11px; padding: 2px 8px; border-radius: 999px;
  font-weight: 500;
}
.lang-python { background: #fef3c7; color: #92400e; border: 1px solid #fde68a; }
.lang-c      { background: #ede9fe; color: #5b21b6; border: 1px solid #ddd6fe; }
.lang-cpp    { background: #e0f2fe; color: #075985; border: 1px solid #bae6fd; }
.lang-java   { background: #fce7f3; color: #9d174d; border: 1px solid #fbcfe8; }
.lang-js     { background: #fefce8; color: #713f12; border: 1px solid #fef08a; }
.nav-sidebar-count { font-size: 11px; color: #5f6368; font-weight: 600; margin-left: auto; }
.nav-sidebar-tags { margin-top: 6px; display: flex; flex-wrap: wrap; gap: 4px; }
.nav-sidebar-tag { font-size: 11px; padding: 1px 8px; border-radius: 4px; }
.tag-unknown   { background: #f1f5f9; color: #64748b; border: 1px solid #e2e8f0; }
.tag-syntaxerr, .tag-rterr, .tag-nameerr { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
.tag-logicerr, .tag-algo { background: #f0f6ff; color: #3b82f6; border: 1px solid #dbeafe; }
.tag-boundary, .tag-perf { background: #fff7ed; color: #ea580c; border: 1px solid #fed7aa; }
.nav-sidebar-time { margin-top: 4px; font-size: 11px; color: #94a3b8; }

.nav-detail { flex: 1; min-width: 0; overflow-y: auto; }
.nav-detail-empty { display: flex; align-items: center; justify-content: center; height: 100%; color: #94a3b8; font-size: 13px; min-height: 300px; }
.nav-detail-header {
  padding: 18px 24px 12px; display: flex; align-items: center;
  flex-wrap: wrap; gap: 10px;
}
.nav-detail-title-link, .nav-detail-title { font-size: 18px; font-weight: 700; color: #1a73e8; text-decoration: none; }
.nav-detail-title { color: #1a1d2e; }
.nav-detail-lang { font-size: 12px; padding: 2px 10px; border-radius: 999px; }
.nav-detail-count {
  font-size: 12px; padding: 3px 10px; border-radius: 11px;
  background: #fee2e2; color: #ea4335; border: 1px solid #fecaca; font-weight: 700;
}
.nav-detail-badge { font-size: 12px; font-weight: 600; padding: 3px 10px; border-radius: 999px; }
.nav-conquered { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }
.nav-unconquered { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
.nav-rechallenge-btn {
  font-size: 12px; font-weight: 600; padding: 5px 14px;
  border-radius: 6px; background: #1a73e8; color: #fff;
  text-decoration: none; margin-left: auto;
  &:hover { background: #1558d6; }
}
.nav-records { padding: 0 8px 18px; }
</style>

<template>
  <div class="notebook-page">
    <NotebookHeader
      :view-mode="viewMode"
      :loading="loading"
      :creating-review="creatingReview"
      @change-view="setViewMode"
      @refresh="loadAll"
      @open-add-modal="showAddModal = true"
      @open-review-package="openReviewPackage"
      @export="exportEntries"
    />

    <NotebookFilterToolbar
      v-if="viewMode === 'archive'"
      v-model:selected-category="selectedCategory"
      v-model:selected-language="selectedLanguage"
      :category-options="categoryOptions"
      :language-options="languageOptions"
      :group-count="archiveGroupCount"
      :entry-count="entries.length"
      @change="applyFilters"
    />

    <div class="nb-content" v-loading="loading && viewMode !== 'timeline'">
      <LearningTimeline v-if="viewMode === 'timeline'" />
      <NotebookCalendarView v-else-if="viewMode === 'calendar'" :items="calendarItems" @open-day="openDay" />
      <NotebookArchiveView
        v-else
        :entries="entries"
        :selected-group-key="selectedGroupKey"
        :class-freq-map="classFreqMap"
        :generating-reflection-id="generatingReflectionId"
        @select-group="selectedGroupKey = $event"
        @remove-entry="removeEntry"
        @save-reflection="saveReflection"
        @generate-reflection="generateReflectionOf"
        @add-tag="addTag"
        @remove-tag="removeTag"
      />
    </div>

    <MisconceptionTagCloud :misconceptions="misconceptions" />

    <NotebookDayDrawer
      v-model="dayDrawerVisible"
      :title="dayDrawerTitle"
      :subtitle="dayDrawerSubtitle"
      :items="dayDrawerItems"
      @open-review-package="goReviewPackage"
      @open-problem="goProblem"
    />

    <NotebookAddDialog v-model="showAddModal" @submit="submitNewEntry" />
  </div>
</template>

<script>
import api from '@oj/api'
import NotebookHeader from './notebook/NotebookHeader.vue'
import NotebookFilterToolbar from './notebook/NotebookFilterToolbar.vue'
import NotebookCalendarView from './notebook/NotebookCalendarView.vue'
import NotebookDayDrawer from './notebook/NotebookDayDrawer.vue'
import NotebookArchiveView from './notebook/NotebookArchiveView.vue'
import NotebookAddDialog from './notebook/NotebookAddDialog.vue'
import MisconceptionTagCloud from './notebook/MisconceptionTagCloud.vue'
import LearningTimeline from './twin/LearningTimeline.vue'
import { getCategoryLabel, toLocalDateKey } from './notebook/notebookFormatters.js'
import { REVIEW_DUE_UPDATED_EVENT, VIEW_MODES } from './notebook/notebookConstants.js'
import {
  fetchNotebookEntries, fetchDueReviews, fetchMisconceptions, fetchClassFrequency,
  generateReflection, buildReviewPackageGroups, createReviewPackages, buildReviewPackageRoute, exportNotebookAsJsonFile
} from './notebook/notebookActions.js'

export default {
  name: 'LearnerNotebook',
  components: {
    NotebookHeader, NotebookFilterToolbar, NotebookCalendarView, NotebookDayDrawer,
    NotebookArchiveView, NotebookAddDialog, MisconceptionTagCloud, LearningTimeline
  },
  data () {
    return {
      viewMode: this.$route && this.$route.query && this.$route.query.view === 'archive' ? VIEW_MODES.ARCHIVE : VIEW_MODES.CALENDAR,
      loading: false,
      creatingReview: false,
      showAddModal: false,
      allEntries: [],
      entries: [],
      selectedCategory: '',
      selectedLanguage: '',
      selectedGroupKey: '',
      generatingReflectionId: '',
      dueReviews: [],
      misconceptions: [],
      classFreqMap: {},
      dayDrawerVisible: false,
      dayDrawerTitle: '当日复习',
      dayDrawerSubtitle: '',
      dayDrawerItems: []
    }
  },
  computed: {
    categoryOptions () {
      const set = new Set()
      this.allEntries.forEach(e => { if (e && e.error_taxonomy) set.add(e.error_taxonomy) })
      return Array.from(set).sort().map(v => ({ value: v, label: getCategoryLabel(v) }))
    },
    languageOptions () {
      const set = new Set()
      this.allEntries.forEach(e => { if (e && e.language) set.add(e.language) })
      return Array.from(set).sort()
    },
    archiveGroupCount () {
      const keys = new Set()
      for (const e of this.entries) keys.add((e.problem_id || 'none') + '|' + (e.language || ''))
      return keys.size
    },
    calendarItems () {
      const items = []
      for (const card of (this.dueReviews || [])) {
        const dateKey = toLocalDateKey(card.due_at)
        if (!dateKey) continue
        items.push({
          kind: 'review', date_key: dateKey,
          id: card.active_package_id || card.error_taxonomy,
          active_package_id: card.active_package_id,
          label: card.label || getCategoryLabel(card.error_taxonomy),
          stability: typeof card.stability === 'number' ? card.stability : null,
          retrievability: typeof card.retrievability === 'number' ? card.retrievability : null,
          fsrs_state: card.fsrs_state,
          is_due: card.is_due === true,
          last_package_mastery: card.last_package_mastery === true
        })
      }
      for (const e of this.allEntries) {
        const dateKey = toLocalDateKey(e.create_time)
        if (!dateKey) continue
        items.push({
          kind: 'entry', date_key: dateKey, id: e.id,
          problem_id: e.problem_id,
          label: getCategoryLabel(e.error_taxonomy),
          summary: e.root_cause || ''
        })
      }
      return items
    }
  },
  mounted () {
    this.loadAll()
    window.addEventListener(REVIEW_DUE_UPDATED_EVENT, this.refreshDueReviews)
  },
  beforeUnmount () {
    window.removeEventListener(REVIEW_DUE_UPDATED_EVENT, this.refreshDueReviews)
  },
  methods: {
    setViewMode (mode) {
      this.viewMode = mode === VIEW_MODES.ARCHIVE ? VIEW_MODES.ARCHIVE : VIEW_MODES.CALENDAR
      this.$router.replace({ query: { ...this.$route.query, view: this.viewMode } }).catch(() => {})
    },
    async loadAll () {
      this.loading = true
      try {
        const [entries, dueReviews, misconceptions, classFreqMap] = await Promise.all([
          fetchNotebookEntries(), fetchDueReviews(20), fetchMisconceptions(), fetchClassFrequency()
        ])
        this.allEntries = entries
        this.applyFilters()
        if (!this.selectedGroupKey && this.entries.length > 0) {
          const f = this.entries[0]
          this.selectedGroupKey = (f.problem_id || 'none') + '|' + (f.language || '')
        }
        this.dueReviews = dueReviews
        this.misconceptions = misconceptions
        this.classFreqMap = classFreqMap
      } finally { this.loading = false }
    },
    async refreshDueReviews () { this.dueReviews = await fetchDueReviews(20) },
    applyFilters () {
      let list = this.allEntries
      if (this.selectedCategory) list = list.filter(e => e.error_taxonomy === this.selectedCategory)
      if (this.selectedLanguage) list = list.filter(e => e.language === this.selectedLanguage)
      this.entries = list
    },
    openDay (payload) {
      const items = payload.dayItems || []
      this.dayDrawerTitle = payload.dateKey + ' 当日复习'
      this.dayDrawerSubtitle = items.length ? `共 ${items.length} 项` : ''
      this.dayDrawerItems = items
      this.dayDrawerVisible = true
    },
    goReviewPackage (item) {
      if (!item || !item.active_package_id) return
      this.dayDrawerVisible = false
      this.$router.push(buildReviewPackageRoute(item.active_package_id))
    },
    goProblem (item) {
      if (!item || !item.problem_id) return
      this.dayDrawerVisible = false
      this.$router.push({ name: 'problem-details', params: { problemID: item.problem_id }, query: { rechallenge: '1' } })
    },
    notifyReviewDueUpdated () { window.dispatchEvent(new CustomEvent(REVIEW_DUE_UPDATED_EVENT)) },
    async removeEntry (id) {
      await api.deleteLearnerNotebookEntry(id)
      this.allEntries = await fetchNotebookEntries()
      this.applyFilters()
      this.notifyReviewDueUpdated()
    },
    async saveReflection ({ entry, text }) {
      try {
        await api.updateLearnerNotebookEntry({ id: entry.id, student_reflection: text })
        entry.student_reflection = text
      } catch { this.$error && this.$error('保存失败') }
    },
    async generateReflectionOf (entry) {
      this.generatingReflectionId = entry.id
      try {
        const text = await generateReflection(entry)
        if (text) {
          await api.updateLearnerNotebookEntry({ id: entry.id, student_reflection: text })
          entry.student_reflection = text
        }
      } catch { this.$error && this.$error('AI 生成失败，请稍后重试') }
      finally { this.generatingReflectionId = '' }
    },
    async addTag ({ entry, tag }) {
      const tags = [...(entry.tags || []), tag]
      try {
        await api.updateLearnerNotebookEntry({ id: entry.id, tags })
        entry.tags = tags
      } catch { this.$error && this.$error('标签保存失败') }
    },
    async removeTag ({ entry, index }) {
      const tags = [...(entry.tags || [])]
      tags.splice(index, 1)
      try {
        await api.updateLearnerNotebookEntry({ id: entry.id, tags })
        entry.tags = tags
      } catch { this.$error && this.$error('标签删除失败') }
    },
    async openReviewPackage () {
      const groups = buildReviewPackageGroups(this.entries, this.selectedCategory)
      if (!groups.length) { this.$message && this.$message.warning('暂无错题记录，无法生成强化训练'); return }
      this.creatingReview = true
      try {
        const packages = await createReviewPackages({ groups })
        if (!packages.length) throw new Error('empty review packages')
        await this.$router.push(buildReviewPackageRoute(packages[0].id, packages.map(pkg => pkg.id)))
      } catch (err) {
        const msg = err.userMessage || (err.response && err.response.data && err.response.data.data) || '生成强化训练包失败'
        this.$message && this.$message.error(msg)
      } finally { this.creatingReview = false }
    },
    async exportEntries () { await exportNotebookAsJsonFile() },
    async submitNewEntry (payload) {
      try {
        await api.addLearnerNotebookEntry(payload)
        this.showAddModal = false
        this.allEntries = await fetchNotebookEntries()
        this.applyFilters()
        this.notifyReviewDueUpdated()
      } catch { this.$error && this.$error('添加失败') }
    }
  }
}
</script>

<style lang="less" scoped>
.notebook-page { max-width: 1400px; margin: 0 auto; padding: 22px 16px 60px; }
.nb-content { position: relative; min-height: 200px; }
</style>

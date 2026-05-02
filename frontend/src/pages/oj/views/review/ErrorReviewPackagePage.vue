<template>
  <div class="erp-page">
    <div v-if="loading" class="erp-loading">加载中...</div>
    <div v-else-if="!pkg" class="erp-empty">复习包不存在</div>
    <div v-else class="erp-container">
      <div v-if="packageOptions.length > 1" class="erp-switcher">
        <label class="erp-switcher-label" for="review-package-select">选择题单</label>
        <ElSelect
          id="review-package-select"
          v-model="selectedPackageId"
          size="small"
          aria-label="选择题单"
          class="erp-switcher-select"
          @change="switchPackage"
        >
          <ElOption
            v-for="option in packageOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </ElSelect>
      </div>
      <ReviewPackageHeader :pkg="pkg" @go-back="goBack" />
      <ReviewPackageEvidence :evidence="pkg.evidence_summary" />

      <section class="erp-section">
        <div class="erp-section-title">错题回顾</div>
        <div v-if="!reviewProblems.length && !aiProblems.length" class="erp-section-empty">
          复习包内的题目已全部下架或被移除，建议返回首页生成新的复习包。
        </div>
        <template v-for="problem in reviewProblems" :key="problem.id">
          <ReviewProblemUnavailableCard v-if="problem.is_unavailable" :problem="problem" />
          <ReviewProblemCard
            v-else
            :problem="problem"
            :loading="ratingProblemId === problem.id"
            :just-added="justAddedProblemId === problem.id"
            @rate="handleRate"
            @open-problem="goToProblem"
            @open-parsons="goToProblemAsParsons"
          />
        </template>
      </section>

      <section v-if="aiProblems.length" class="erp-section">
        <div class="erp-section-title">
          AI 特化练习
          <span class="erp-section-badge">AI 出题</span>
        </div>
        <template v-for="problem in aiProblems" :key="problem.id">
          <ReviewProblemUnavailableCard v-if="problem.is_unavailable" :problem="problem" />
          <ReviewProblemCard
            v-else
            :problem="problem"
            :loading="ratingProblemId === problem.id"
            :just-added="justAddedProblemId === problem.id"
            @rate="handleRate"
            @open-problem="goToProblem"
            @open-parsons="goToProblemAsParsons"
          />
        </template>
      </section>
    </div>

    <ReviewMasteryDialog
      v-model="masteryDialogVisible"
      :loading="masteryDialogLoading"
      @choose="finishMastery"
    />
  </div>
</template>

<script>
import api from '@oj/api'
import { decodeRouteCtx, encodeRouteCtx } from '@/utils/urlCipher'
import ReviewPackageHeader from './components/ReviewPackageHeader.vue'
import ReviewPackageEvidence from './components/ReviewPackageEvidence.vue'
import ReviewProblemCard from './components/ReviewProblemCard.vue'
import ReviewProblemUnavailableCard from './components/ReviewProblemUnavailableCard.vue'
import ReviewMasteryDialog from './components/ReviewMasteryDialog.vue'

const HIGHLIGHT_DURATION_MS = 1800

export default {
  name: 'ErrorReviewPackagePage',
  components: { ReviewPackageHeader, ReviewPackageEvidence, ReviewProblemCard, ReviewProblemUnavailableCard, ReviewMasteryDialog },
  data () {
    return {
      loading: false,
      pkg: null,
      packages: [],
      selectedPackageId: '',
      aiRefreshTimer: null,
      aiRefreshAttempts: 0,
      ratingProblemId: '',
      justAddedProblemId: '',
      highlightTimer: null,
      masteryDialogVisible: false,
      masteryDialogLoading: false,
      lastTotalProblemCount: 0
    }
  },
  computed: {
    reviewProblems () { return this.pkg ? (this.pkg.problems || []).filter(p => !p.is_ai_generated) : [] },
    aiProblems () { return this.pkg ? (this.pkg.problems || []).filter(p => p.is_ai_generated) : [] },
    packageOptions () {
      return (this.packages || []).map(pkg => ({ value: pkg.id, label: this.packageLabel(pkg) }))
    },
    allProblemsRatedGood () {
      return !!this.pkg
        && Array.isArray(this.pkg.problems)
        && this.pkg.problems.length > 0
        && this.pkg.problems.every(p => p.submitted && p.user_rating === 'good')
    }
  },
  mounted () {
    this.loadPackage()
    this.loadPackageList()
  },
  watch: {
    '$route.query.ctx' () {
      this.aiRefreshAttempts = 0
      this.loadPackage()
      this.loadPackageList()
    }
  },
  beforeUnmount () {
    this.clearAiRefreshTimer()
    if (this.highlightTimer) window.clearTimeout(this.highlightTimer)
  },
  methods: {
    routeCtx () { return decodeRouteCtx(this.$route.query.ctx) },
    currentPackageId () { return decodeRouteCtx(this.$route.query.ctx).pkg },
    routePackageIds () {
      const ids = this.routeCtx().pkgs
      return Array.isArray(ids) ? ids.filter(Boolean) : []
    },
    loadPackage ({ background = false } = {}) {
      const packageId = this.currentPackageId()
      if (!packageId) return Promise.resolve()
      if (!background) this.loading = true
      return api.getReviewPackage(packageId).then(res => {
        this.pkg = res.data.data
        this.selectedPackageId = this.pkg.id
        this.ensurePackageInList(this.pkg)
        this.scheduleAiRefresh()
        this.maybeShowMasteryDialog()
        if (!background) this.loading = false
      }).catch(() => {
        this.clearAiRefreshTimer()
        if (!background) {
          this.loading = false
          this.$error && this.$error('加载复习包失败')
        }
      })
    },
    loadPackageList () {
      return api.getReviewPackages().then(res => {
        const list = (res.data && res.data.data) || []
        const scopedIds = this.routePackageIds()
        this.packages = scopedIds.length > 1
          ? scopedIds.map(id => list.find(pkg => pkg.id === id)).filter(Boolean)
          : list
        this.ensurePackageInList(this.pkg)
      }).catch(() => {})
    },
    ensurePackageInList (pkg) {
      if (!pkg || (this.packages || []).some(item => item.id === pkg.id)) return
      this.packages = [pkg, ...(this.packages || [])]
    },
    packageLabel (pkg) {
      const title = pkg.error_label || pkg.error_taxonomy || '错题强化'
      return `${title} · ${pkg.completed_count || 0}/${pkg.problem_count || 0}`
    },
    switchPackage (packageId) {
      if (!packageId || (this.pkg && packageId === this.pkg.id)) return
      const scopedIds = this.routePackageIds()
      const ctx = scopedIds.length > 1
        ? encodeRouteCtx({ pkg: packageId, pkgs: scopedIds })
        : encodeRouteCtx({ pkg: packageId })
      this.clearAiRefreshTimer()
      this.$router.push({ name: 'error-review-package', query: { ctx } })
    },
    shouldRefreshForAiProblems () {
      if (!this.pkg || !Array.isArray(this.pkg.problems)) return false
      if (this.aiRefreshAttempts >= 8) return false
      return this.pkg.problems.length < 6
    },
    scheduleAiRefresh () {
      this.clearAiRefreshTimer()
      if (!this.shouldRefreshForAiProblems()) return
      this.aiRefreshTimer = window.setTimeout(() => {
        this.aiRefreshAttempts += 1
        this.loadPackage({ background: true })
      }, 1500)
    },
    clearAiRefreshTimer () {
      if (this.aiRefreshTimer) {
        window.clearTimeout(this.aiRefreshTimer)
        this.aiRefreshTimer = null
      }
    },
    goToProblem (problem) {
      if (!problem || !problem.problem_key) return
      this.$router.push({
        name: 'problem-details',
        params: { problemID: problem.problem_key },
        query: { ai_tutor_allowed: '0', ai_tutor_reason: 'review_mode' }
      })
    },
    goBack () { this.$router.push({ name: 'home' }) },
    goToProblemAsParsons (problem) {
      if (!problem || !problem.problem_key) return
      this.$router.push({
        name: 'problem-details',
        params: { problemID: problem.problem_key },
        query: { parsons: '1', fsrs_origin: (this.pkg && this.pkg.id) || '' }
      })
    },
    async handleRate ({ problem, rating }) {
      if (!problem || !this.pkg) return
      this.ratingProblemId = problem.id
      const previousIds = new Set((this.pkg.problems || []).map(p => p.id))
      try {
        const res = await api.rateReviewProblem(this.pkg.id, problem.id, rating)
        const updated = res.data && res.data.data
        if (updated) {
          this.pkg = updated
          if (rating === 'again') {
            const newProblem = (updated.problems || []).find(p => !previousIds.has(p.id))
            if (newProblem) this.flashJustAdded(newProblem)
          }
          this.maybeShowMasteryDialog()
        }
      } catch (err) {
        const msg = (err.response && err.response.data && err.response.data.data) || '评分失败'
        this.$message && this.$message.error(msg)
      } finally {
        this.ratingProblemId = ''
      }
    },
    flashJustAdded (problem) {
      this.justAddedProblemId = problem.id
      this.$nextTick(() => {
        const el = document.querySelector(`[data-problem-row-id="${problem.id}"]`)
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
      if (this.highlightTimer) window.clearTimeout(this.highlightTimer)
      this.highlightTimer = window.setTimeout(() => { this.justAddedProblemId = '' }, HIGHLIGHT_DURATION_MS)
    },
    maybeShowMasteryDialog () {
      if (!this.allProblemsRatedGood) return
      if (this.pkg.fsrs_state && this.pkg.fsrs_state !== 'new') return
      this.masteryDialogVisible = true
    },
    async finishMastery (rating) {
      if (!this.pkg) return
      this.masteryDialogLoading = true
      try {
        const res = await api.rateReviewPackage(this.pkg.id, rating)
        const updated = res.data && res.data.data
        if (updated) this.pkg = updated
        this.masteryDialogVisible = false
      } catch (err) {
        const msg = (err.response && err.response.data && err.response.data.data) || '推进 FSRS 失败'
        this.$message && this.$message.error(msg)
      } finally {
        this.masteryDialogLoading = false
      }
    }
  }
}
</script>

<style lang="less" scoped>
.erp-page { max-width: 760px; margin: 0 auto; padding: 32px 20px; }
.erp-loading, .erp-empty { text-align: center; padding: 60px 0; color: #999; font-size: 14px; }
.erp-container { display: flex; flex-direction: column; gap: 18px; }
.erp-switcher {
  display: flex; align-items: center; justify-content: flex-end; gap: 10px;
  background: #fff7ed; border: 1px solid #fed7aa; border-radius: 10px; padding: 10px 12px;
}
.erp-switcher-label { color: #9a3412; font-size: 13px; font-weight: 600; }
.erp-switcher-select { width: 240px; max-width: 100%; }
.erp-section { display: flex; flex-direction: column; gap: 10px; }
.erp-section-title {
  font-size: 15px; font-weight: 600; color: #1a1d2e;
  display: flex; align-items: center; gap: 8px;
}
.erp-section-empty { background: #fafbfc; border: 1px dashed #d9dde3; border-radius: 10px; padding: 18px 16px; color: #6b7280; font-size: 13px; line-height: 1.6; }
.erp-section-badge {
  display: inline-block; font-size: 11px; font-weight: 600;
  padding: 2px 8px; border-radius: 4px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}
</style>

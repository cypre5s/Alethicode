<template>
  <div ref="analyticsRoot" class="analytics-root">
    <!-- 0. 学情周报 -->
    <el-card data-section="report" shadow="hover" class="analytics-card report-card">
      <template #header>
        <div class="card-header">
          <span><el-icon :size="16"><Document /></el-icon> 班级学情周报</span>
          <el-button type="primary" size="small" round :loading="reportLoading" @click="generateReport">
            {{ reportLoading ? '生成中...' : '生成学情周报' }}
          </el-button>
        </div>
      </template>
      <div v-if="weeklyReport" class="report-content">
        <h3 class="report-title">{{ weeklyReport.report_title }}</h3>
        <div class="report-stats">
          <span class="report-stat">活跃 {{ weeklyReport.raw_data.active_students }}/{{ weeklyReport.raw_data.total_students }} 人</span>
          <span class="report-stat">提交 {{ weeklyReport.raw_data.total_submissions }} 次</span>
          <span class="report-stat">AC率 {{ weeklyReport.raw_data.ac_rate }}%</span>
          <span class="report-stat">平均掌握 {{ weeklyReport.raw_data.avg_mastery }}%</span>
          <span class="report-stat report-stat--warn" v-if="weeklyReport.raw_data.risk_count > 0">风险 {{ weeklyReport.raw_data.risk_count }} 人</span>
        </div>
        <div v-for="(section, idx) in weeklyReport.sections" :key="idx" class="report-section">
          <h4 class="report-section-heading">{{ section.heading }}</h4>
          <p class="report-section-content">{{ section.content }}</p>
        </div>
      </div>
      <el-empty v-else description="点击「生成学情周报」查看 AI 分析" :image-size="48" />
    </el-card>

    <!-- 1. 班级学习脉搏 -->
    <el-row data-section="pulse" :gutter="16" class="pulse-row">
      <el-col :span="16">
        <el-card ref="pulseCardRef" shadow="hover" class="analytics-card pulse-card">
          <template #header>
            <div class="card-header">
              <span><el-icon :size="16"><TrendCharts /></el-icon> 班级学习脉搏（{{ pulseRangeLabel }}）</span>
              <el-radio-group v-model="pulseRange" size="small" @change="onPulseRangeChange">
                <el-radio-button value="week">近 7 天</el-radio-button>
                <el-radio-button value="month">近 30 天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="pulseChart" class="chart-container chart-pulse" v-loading="pulseLoading"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="analytics-card kc-top-card" :style="{ height: pulseCardHeight }">
          <template #header>
            <div class="card-header">
              <span><el-icon :size="16"><Sunny /></el-icon> 活跃知识点 TOP</span>
            </div>
          </template>
          <div v-loading="pulseLoading" class="kc-top-body">
            <div v-if="topActiveKcs.length" class="kc-list">
              <div v-for="(kc, idx) in topActiveKcs" :key="idx" class="kc-item">
                <span class="kc-rank" :class="idx < 3 ? 'kc-rank-top' : ''">{{ idx + 1 }}</span>
                <span class="kc-name">{{ kc.kc_name }}</span>
                <span class="kc-count">{{ kc.submission_count }} 次</span>
              </div>
            </div>
            <el-empty v-else description="暂无数据" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 3. 薄弱知识点 TOP3 -->
    <el-card data-section="weak" shadow="hover" class="analytics-card">
      <template #header>
        <div class="card-header">
          <span><el-icon :size="16"><Warning /></el-icon> 薄弱知识点 TOP3</span>
          <el-button type="primary" size="small" round :loading="suggestionsLoading" @click="loadWeakKcSuggestions">
            {{ suggestionsLoading ? '分析中' : '获取教学建议' }}
          </el-button>
        </div>
      </template>
      <div v-loading="suggestionsLoading">
        <div v-if="weakKcs.length" class="weak-kc-list">
          <div v-for="(kc, idx) in weakKcs" :key="idx" class="weak-kc-item">
            <div class="weak-kc-head">
              <el-tag type="danger" size="small">TOP {{ idx + 1 }}</el-tag>
              <span class="weak-kc-name">{{ kc.kc_name }}</span>
            </div>
            <div class="weak-kc-meta">
              <span>章节：{{ kc.chapter_title }}</span>
              <el-progress
                :percentage="Math.round((kc.avg_mastery || 0) * 100)"
                :stroke-width="10"
                :color="masteryColor"
                style="flex: 1; margin-left: 12px" />
            </div>
            <div class="weak-kc-detail">
              薄弱学生 {{ kc.weak_count }}/{{ kc.total_students }}
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无掌握度数据" :image-size="60" />
        <div v-if="suggestions.length" class="suggestions-list" style="margin-top: 16px; border-top: 1px solid #ebeef5; padding-top: 12px;">
          <div v-for="(s, idx) in suggestions" :key="idx" class="suggestion-item">
            <el-icon :size="14" color="#409eff"><Sunny /></el-icon>
            <span>{{ s }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 4. 风险学生列表 -->
    <el-card data-section="risk" shadow="hover" class="analytics-card">
      <template #header>
        <div class="card-header">
          <span><el-icon :size="16"><UserFilled /></el-icon> 风险学生预警</span>
          <el-button type="primary" size="small" round :loading="riskLoading" @click="loadRiskStudents">刷新</el-button>
        </div>
      </template>
      <el-table :data="filteredRiskStudents" v-loading="riskLoading" :default-sort="{ prop: 'overall_mastery', order: 'ascending' }">
        <el-table-column label="学生" prop="username" min-width="100" align="center" />
        <el-table-column label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.risk_level)" size="small">{{ riskLabel(row.risk_level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="整体掌握度" min-width="180" align="center">
          <template #default="{ row }">
            <el-progress :percentage="Math.round((row.overall_mastery || 0) * 100)" :stroke-width="10" :color="masteryColor" />
          </template>
        </el-table-column>
        <el-table-column label="近7天提交" prop="recent_submissions" width="100" align="center" />
        <el-table-column label="连续错误" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.error_streak >= 5 ? '#f56c6c' : '', fontWeight: row.error_streak >= 5 ? 'bold' : '' }">
              {{ row.error_streak }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="风险原因" prop="risk_reason" min-width="200" align="center" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <div style="display: flex; gap: 6px; justify-content: center;">
              <el-button type="info" size="small" round
                         :loading="profileLoadingId === row.user_id"
                         @click="showProfile(row)">画像</el-button>
              <el-button type="primary" size="small" round
                         :loading="adviceLoadingId === row.user_id"
                         @click="showAdvice(row)">建议</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 5. 课件使用分析 -->
    <el-row data-section="courseware" :gutter="16">
      <el-col :span="24">
        <el-card shadow="hover" class="analytics-card">
          <template #header>
            <div class="card-header">
              <span><el-icon :size="16"><Reading /></el-icon> 课件使用分析（近 30 天）</span>
              <el-button type="primary" size="small" round :loading="coursewareLoading" @click="loadCoursewareUsage">刷新</el-button>
            </div>
          </template>
          <div ref="coursewareChart" class="chart-container chart-courseware" v-loading="coursewareLoading"></div>
          <div v-if="qaFrequencyData.length" class="qa-frequency-section">
            <div class="qa-freq-title">课件问答热度（近 30 天）</div>
            <div class="qa-freq-list">
              <div v-for="(item, idx) in qaFrequencyData" :key="idx" class="qa-freq-item qa-freq-item-link" @click="openQaPagePreview(item)">
                <span class="qa-freq-rank">{{ idx + 1 }}</span>
                <span class="qa-freq-pages">{{ item.document_title || '未知文档' }} · 第 {{ item.page_no }} 页</span>
                <span class="qa-freq-count">{{ item.query_count }} 次提问</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 学生画像弹窗 -->
    <el-dialog v-model="profileDialogVisible" :title="profileData ? profileData.basic.username + ' 学情画像' : '学情画像'" width="720px" top="5vh">
      <div v-if="profileData" class="profile-content">
        <div class="profile-summary-bar">
          <div class="profile-stat"><span class="profile-stat-val">{{ Math.round((profileData.basic.overall_mastery || 0) * 100) }}%</span><span class="profile-stat-label">掌握度</span></div>
          <div class="profile-stat"><span class="profile-stat-val">{{ profileData.basic.problems_attempted }}</span><span class="profile-stat-label">做题数</span></div>
          <div class="profile-stat"><span class="profile-stat-val">{{ profileData.basic.problems_solved }}</span><span class="profile-stat-label">通过数</span></div>
          <div class="profile-stat"><span class="profile-stat-val">{{ profileData.streak }}</span><span class="profile-stat-label">连续天数</span></div>
        </div>

        <div v-if="profileData.llm_summary" class="profile-llm-summary">
          <el-icon :size="14" color="#409eff"><ChatDotSquare /></el-icon>
          <span>{{ profileData.llm_summary }}</span>
        </div>

        <el-row :gutter="16" style="margin-top: 16px">
          <el-col :span="24">
            <div class="profile-chart-title">错题类型分布</div>
            <div ref="profilePieChart" class="profile-chart"></div>
          </el-col>
        </el-row>

        <div class="profile-chart-title" style="margin-top: 12px">近 30 天做题趋势</div>
        <div ref="profileTimelineChart" class="profile-chart" style="height: 200px"></div>

        <div class="profile-chart-title" style="margin-top: 12px">最近提交</div>
        <el-table :data="profileData.recent_submissions">
          <el-table-column label="题目" min-width="160">
            <template #default="{ row }">{{ row.problem_key || '' }} {{ row.title || '' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.result === 0 ? 'success' : 'danger'" size="small">{{ row.result === 0 ? 'AC' : 'WA' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="170" align="center">
            <template #default="{ row }">{{ formatProfileTime(row.create_time) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 干预建议弹窗 -->
    <el-dialog v-model="adviceDialogVisible" title="AI 干预建议" width="520px">
      <div v-if="adviceData" class="advice-content">
        <div class="advice-student-info">
          <div class="advice-row"><span class="advice-label">学生</span><span>{{ adviceData.student.username }}</span></div>
          <div class="advice-row"><span class="advice-label">掌握度</span><span>{{ Math.round((adviceData.student.overall_mastery || 0) * 100) }}%</span></div>
          <div class="advice-row"><span class="advice-label">连续错误</span><span>{{ adviceData.error_streak }} 次</span></div>
        </div>
        <div v-if="adviceData.weak_kcs && adviceData.weak_kcs.length" class="advice-weak-kcs">
          <div class="advice-section-title">最薄弱知识点</div>
          <div v-for="kc in adviceData.weak_kcs" :key="kc.kc_name" class="advice-kc-item">
            {{ kc.kc_name }}（{{ Math.round((kc.mastery || 0) * 100) }}%）
          </div>
        </div>
        <div class="advice-section-title" style="margin-top: 16px">LLM 建议</div>
        <div v-if="adviceData.advice && adviceData.advice.length" class="advice-list">
          <div v-for="(a, i) in adviceData.advice" :key="i" class="advice-item">
            <span class="advice-num">{{ i + 1 }}</span>
            <span>{{ a }}</span>
          </div>
        </div>
        <el-empty v-else description="未能生成建议" :image-size="40" />
      </div>
    </el-dialog>
  </div>
</template>

<script>
import echarts from '@/utils/echarts'
import api from '@oj/api'
import {
  TrendCharts, Sunny, Grid, Warning, ChatDotSquare,
  UserFilled, Reading, Document
} from '@element-plus/icons-vue'

export default {
  name: 'ClassroomAnalytics',
  components: { TrendCharts, Sunny, Grid, Warning, ChatDotSquare, UserFilled, Reading, Document },
  props: {
    classroomId: { type: String, required: true }
  },
  data () {
    return {
      pulseLoading: false,
      dailyTrend: [],
      topActiveKcs: [],
      suggestionsLoading: false,
      weakKcs: [],
      suggestions: [],
      riskLoading: false,
      riskStudents: [],
      adviceLoadingId: null,
      adviceDialogVisible: false,
      adviceData: null,
      coursewareLoading: false,
      coursewareData: null,
      qaFrequencyData: [],
      qaLanguagePackId: null,
      reportLoading: false,
      weeklyReport: null,
      profileLoadingId: null,
      profileDialogVisible: false,
      profileData: null,
      pulseRange: 'week',
      pulseCardHeight: 'auto',
      charts: {},
      resizeObserver: null
    }
  },
  computed: {
    filteredRiskStudents () {
      return this.riskStudents.filter(s => s.risk_level !== 'low')
    },
    pulseRangeLabel () {
      return this.pulseRange === 'month' ? '近 30 天' : '近 7 天'
    }
  },
  mounted () {
    this.loadAll()
    this.$nextTick(() => {
      this.initResizeObserver()
      this.handleResize()
    })
    window.addEventListener('resize', this.handleResize)
  },
  beforeUnmount () {
    window.removeEventListener('resize', this.handleResize)
    if (this.resizeObserver) {
      this.resizeObserver.disconnect()
      this.resizeObserver = null
    }
    Object.values(this.charts).forEach(c => c && c.dispose())
  },
  methods: {
    scrollToSection (sectionKey) {
      const root = this.$refs.analyticsRoot
      if (!root) return
      const target = root.querySelector(`[data-section="${sectionKey}"]`)
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    },

    initResizeObserver () {
      if (this.resizeObserver || typeof ResizeObserver === 'undefined' || !this.$refs.analyticsRoot) return
      this.resizeObserver = new ResizeObserver(() => {
        this.handleResize()
      })
      this.resizeObserver.observe(this.$refs.analyticsRoot)
    },

    loadAll () {
      this.loadWeeklyPulse()
      this.loadRiskStudents()
      this.loadCoursewareUsage()
    },

    async loadWeeklyPulse () {
      this.pulseLoading = true
      const res = await api.getWeeklyPulse(this.classroomId, this.pulseRange)
      const data = res.data.data
      this.dailyTrend = data.daily_trend || []
      this.topActiveKcs = data.top_active_kcs || []
      this.pulseLoading = false
      this.$nextTick(() => {
        this.renderPulseChart()
        this.syncKcCardHeight()
      })
    },

    onPulseRangeChange () {
      this.loadWeeklyPulse()
    },

    syncKcCardHeight () {
      const pulseEl = this.$refs.pulseCardRef?.$el
      if (pulseEl) {
        this.pulseCardHeight = pulseEl.offsetHeight + 'px'
      }
    },

    async loadWeakKcSuggestions () {
      this.suggestionsLoading = true
      const res = await api.getWeakKcSuggestions(this.classroomId)
      const data = res.data.data
      this.weakKcs = data.weak_kcs || []
      const raw = data.suggestions
      this.suggestions = Array.isArray(raw) ? raw : (raw ? [raw] : [])
      this.suggestionsLoading = false
    },

    async loadRiskStudents () {
      this.riskLoading = true
      const res = await api.getRiskStudents(this.classroomId)
      this.riskStudents = res.data.data || []
      this.riskLoading = false
    },

    async generateReport () {
      this.reportLoading = true
      try {
        const res = await api.getWeeklyReport(this.classroomId)
        this.weeklyReport = res.data.data
      } catch (e) {
        this.$message.error('生成周报失败')
      } finally {
        this.reportLoading = false
      }
    },

    async showProfile (row) {
      this.profileLoadingId = row.user_id
      try {
        const res = await api.getStudentProfile(this.classroomId, row.user_id)
        this.profileData = res.data.data
        this.profileDialogVisible = true
        this.$nextTick(() => {
          this.renderProfilePie()
          this.renderProfileTimeline()
        })
      } catch (e) {
        this.$message.error('获取画像失败')
      } finally {
        this.profileLoadingId = null
      }
    },

    renderProfilePie () {
      const el = this.$refs.profilePieChart
      if (!el || !this.profileData) return
      if (this.charts.profilePie) this.charts.profilePie.dispose()
      const chart = echarts.init(el)
      this.charts.profilePie = chart
      const errors = this.profileData.error_distribution || []
      const labelMap = {
        syntax_error: '语法错误', runtime_error: '运行时错误', logic_error: '逻辑错误',
        boundary_condition: '边界条件', performance: '性能问题', algorithm_error: '算法错误',
        input_parsing: '输入解析', name_or_type_error: '名称/类型'
      }
      chart.setOption({
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie', radius: ['35%', '65%'],
          data: errors.map(e => ({ name: labelMap[e.error_taxonomy] || e.error_taxonomy, value: e.count })),
          label: { fontSize: 11 }
        }]
      })
    },

    renderProfileTimeline () {
      const el = this.$refs.profileTimelineChart
      if (!el || !this.profileData) return
      if (this.charts.profileTimeline) this.charts.profileTimeline.dispose()
      const chart = echarts.init(el)
      this.charts.profileTimeline = chart
      const timeline = this.profileData.daily_timeline || []
      const days = timeline.map(d => {
        const dt = new Date(d.day)
        return `${dt.getMonth() + 1}/${dt.getDate()}`
      })
      chart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['提交', 'AC'], bottom: 0 },
        grid: { top: 8, right: 16, bottom: 32, left: 36 },
        xAxis: { type: 'category', data: days, axisLabel: { fontSize: 10 } },
        yAxis: { type: 'value', minInterval: 1 },
        series: [
          { name: '提交', type: 'bar', data: timeline.map(d => d.submission_count), itemStyle: { color: '#409eff' } },
          { name: 'AC', type: 'bar', data: timeline.map(d => d.ac_count), itemStyle: { color: '#67c23a' } }
        ]
      })
    },

    formatProfileTime (ts) {
      if (!ts) return '-'
      return new Date(ts).toLocaleString('zh-CN', { hour12: false })
    },

    async showAdvice (row) {
      this.adviceLoadingId = row.user_id
      try {
        const res = await api.getRiskStudentAdvice(this.classroomId, row.user_id)
        this.adviceData = res.data.data
        this.adviceDialogVisible = true
      } catch (e) {
        this.$message.error('获取建议失败')
      } finally {
        this.adviceLoadingId = null
      }
    },

    async loadCoursewareUsage () {
      this.coursewareLoading = true
      try {
        const res = await api.getCoursewareUsage(this.classroomId)
        this.coursewareData = res.data.data
        this.qaFrequencyData = (this.coursewareData && this.coursewareData.qa_frequency) || []
        this.qaLanguagePackId = (this.coursewareData && this.coursewareData.language_pack_id) || null
        this.$nextTick(() => this.renderCoursewareChart())
      } catch (e) {
        console.warn('[ClassroomAnalytics] getCoursewareUsage failed:', e)
      }
      this.coursewareLoading = false
    },

    openQaPagePreview (item) {
      if (!item || !item.document_id || !item.page_no || !this.qaLanguagePackId) return
      const previewUrl = api.getLanguagePackQaPreviewUrl(this.qaLanguagePackId, item.document_id, item.page_no)
      window.open(previewUrl, '_blank', 'noopener,noreferrer')
    },

    renderCoursewareChart () {
      const el = this.$refs.coursewareChart
      if (!el || !this.coursewareData) return
      if (this.charts.courseware) this.charts.courseware.dispose()
      const chart = echarts.init(el)
      this.charts.courseware = chart
      const chapters = this.coursewareData.submission_by_chapter || []
      const labels = chapters.map(c => c.chapter_title || `章节${c.chapter_index}`)
      chart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['提交数', 'AC 数', '活跃学生'], bottom: 0, itemGap: 16 },
        grid: { top: 16, right: 20, bottom: 42, left: 48, containLabel: true },
        xAxis: { type: 'category', data: labels, axisLabel: { fontSize: 12 }, axisTick: { alignWithLabel: true } },
        yAxis: { type: 'value', minInterval: 1 },
        series: [
          { name: '提交数', type: 'bar', data: chapters.map(c => c.submission_count), itemStyle: { color: '#409eff' }, barMaxWidth: 60 },
          { name: 'AC 数', type: 'bar', data: chapters.map(c => c.ac_count), itemStyle: { color: '#67c23a' }, barMaxWidth: 60 },
          { name: '活跃学生', type: 'bar', data: chapters.map(c => c.active_students), itemStyle: { color: '#e6a23c' }, barMaxWidth: 60 }
        ]
      })
    },

    renderPulseChart () {
      const el = this.$refs.pulseChart
      if (!el || !this.dailyTrend.length) return
      if (this.charts.pulse) this.charts.pulse.dispose()
      const chart = echarts.init(el)
      this.charts.pulse = chart
      const days = this.dailyTrend.map(d => {
        const dt = new Date(d.day)
        return `${dt.getMonth() + 1}/${dt.getDate()}`
      })
      chart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['提交数', 'AC 数', '活跃学生'], bottom: 0, itemGap: 16 },
        grid: { top: 16, right: 20, bottom: 42, left: 48, containLabel: true },
        xAxis: { type: 'category', data: days, axisLabel: { fontSize: 11 } },
        yAxis: { type: 'value', minInterval: 1 },
        series: [
          { name: '提交数', type: 'bar', data: this.dailyTrend.map(d => d.submission_count), itemStyle: { color: '#409eff' } },
          { name: 'AC 数', type: 'bar', data: this.dailyTrend.map(d => d.ac_count), itemStyle: { color: '#67c23a' } },
          { name: '活跃学生', type: 'line', data: this.dailyTrend.map(d => d.active_students), itemStyle: { color: '#e6a23c' }, yAxisIndex: 0 }
        ]
      })
    },

    handleResize () {
      Object.values(this.charts).forEach(c => c && c.resize())
      this.syncKcCardHeight()
    },

    masteryColor (percentage) {
      if (percentage < 40) return '#f56c6c'
      if (percentage < 70) return '#e6a23c'
      return '#67c23a'
    },
    riskTagType (level) {
      return { critical: 'danger', high: 'danger', medium: 'warning', low: 'success' }[level] || 'info'
    },
    riskLabel (level) {
      return { critical: '极高', high: '高', medium: '中', low: '低' }[level] || level
    }
  }
}
</script>

<style lang="less" scoped>
.analytics-root {
  padding: 0 0 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.analytics-card {
  border-radius: 12px;
  :deep(.el-card__header) {
    padding: 14px 20px;
    background: #fafbfc;
    border-bottom: 1px solid #f0f0f0;
  }
  :deep(.el-card__body) {
    padding: 20px;
  }
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  .el-icon { margin-right: 6px; vertical-align: -2px; }
}
.pulse-row {
  align-items: flex-start;
}
.pulse-card {
  min-height: 100%;
}
.chart-pulse {
  height: 300px;
  width: 100%;
}
.kc-top-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  :deep(.el-card__body) {
    flex: 1;
    overflow: hidden;
    padding: 0;
  }
}
.kc-top-body {
  height: 100%;
  overflow-y: auto;
  padding: 20px;
}
.chart-container {
  height: 260px;
  width: 100%;
}
.kc-list {
  .kc-item {
    display: flex;
    align-items: center;
    padding: 10px 0;
    border-bottom: 1px solid #f5f5f5;
    &:last-child { border-bottom: none; }
  }
  .kc-rank {
    width: 26px; height: 26px; border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    font-size: 12px; font-weight: 700;
    background: #f0f2f5; color: #909399; margin-right: 12px;
    &.kc-rank-top { background: #ecf5ff; color: #409eff; }
  }
  .kc-name { flex: 1; font-size: 14px; color: #303133; }
  .kc-count { font-size: 13px; color: #909399; font-weight: 500; }
}
.weak-kc-list {
  .weak-kc-item {
    padding: 10px 12px;
    border-radius: 8px;
    background: #fafbfc;
    margin-bottom: 8px;
    &:last-child { margin-bottom: 0; }
  }
  .weak-kc-head {
    display: flex; align-items: center; gap: 8px; margin-bottom: 6px;
  }
  .weak-kc-name { font-size: 14px; font-weight: 600; color: #303133; }
  .weak-kc-meta {
    display: flex; align-items: center; font-size: 13px; color: #909399;
  }
  .weak-kc-detail { font-size: 12px; color: #909399; margin-top: 4px; }
}
.suggestions-list {
  .suggestion-item {
    display: flex; align-items: flex-start; gap: 10px;
    padding: 10px 14px;
    border-radius: 8px;
    border-left: 3px solid #409eff;
    background: #f0f6ff;
    margin-bottom: 8px;
    font-size: 13px; color: #303133; line-height: 1.7;
    &:last-child { margin-bottom: 0; }
    .el-icon { margin-top: 3px; flex-shrink: 0; }
  }
}
.report-content {
  padding: 4px 0;
}
.report-title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 12px;
}
.report-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.report-stat {
  font-size: 14px;
  padding: 8px 18px;
  border-radius: 8px;
  background: #f0f6ff;
  color: #2563eb;
  font-weight: 600;
  &--warn {
    background: #fef0f0;
    color: #ef4444;
  }
}
.report-section {
  margin-bottom: 14px;
  &:last-child { margin-bottom: 0; }
}
.report-section-heading {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 6px;
}
.report-section-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  margin: 0;
}
.profile-content { padding: 0; }
.profile-summary-bar {
  display: flex; gap: 24px; justify-content: center;
  padding: 16px; background: #f5f7fa; border-radius: 8px; margin-bottom: 12px;
}
.profile-stat { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.profile-stat-val { font-size: 20px; font-weight: 700; color: #303133; }
.profile-stat-label { font-size: 12px; color: #909399; }
.profile-llm-summary {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 12px 16px; background: #ecf5ff; border-radius: 8px;
  font-size: 13px; color: #303133; line-height: 1.6;
  .el-icon { margin-top: 2px; flex-shrink: 0; }
}
.profile-chart-title {
  font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 4px;
}
.profile-chart { height: 240px; width: 100%; }
.chart-courseware {
  height: 340px;
}
.qa-frequency-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.qa-freq-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.qa-freq-list {
  .qa-freq-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 0;
    border-bottom: 1px solid #f5f5f5;
    font-size: 13px;
    &:last-child { border-bottom: none; }
  }
  .qa-freq-item-link {
    cursor: pointer;
    padding: 6px 8px;
    border-radius: 6px;
    transition: background 0.15s;
    &:hover {
      background: #f0f6ff;
      .qa-freq-pages { color: #2563eb; }
    }
  }
  .qa-freq-rank {
    width: 22px; height: 22px; border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    font-size: 11px; font-weight: 700;
    background: #f0f6ff; color: #409eff;
  }
  .qa-freq-pages { flex: 1; color: #303133; }
  .qa-freq-count { color: #909399; font-size: 12px; }
}
.advice-content {
  padding: 0 4px;
}
.advice-student-info {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
}
.advice-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
  color: #303133;
}
.advice-label {
  color: #909399;
  font-weight: 500;
}
.advice-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.advice-weak-kcs {
  margin-top: 12px;
}
.advice-kc-item {
  font-size: 13px;
  color: #606266;
  padding: 3px 0;
}
.advice-list {
  .advice-item {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 10px 0;
    border-bottom: 1px solid #f0f0f0;
    font-size: 13px;
    color: #303133;
    line-height: 1.6;
    &:last-child { border-bottom: none; }
  }
  .advice-num {
    min-width: 22px;
    height: 22px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    background: #ecf5ff;
    color: #409eff;
    flex-shrink: 0;
  }
}
</style>

<template>
  <div class="view">
    <Panel title="AI 助教工作台">
      <template #header>
        <div class="toolbar">
          <el-radio-group
            v-model="range"
            size="small"
            @change="loadOverviewDashboard"
            class="range-picker"
          >
            <el-radio-button value="today">今日</el-radio-button>
            <el-radio-button value="7d">近 7 天</el-radio-button>
            <el-radio-button value="30d">近 30 天</el-radio-button>
          </el-radio-group>
          <el-button
            size="small"
            type="primary"
            :loading="dashboardLoading"
            @click="loadOverviewDashboard"
          >刷新当前面板</el-button>
        </div>
      </template>

      <div v-if="dashboardLoading" class="loading-block">加载中...</div>
      <div v-else-if="dashboardError" class="error-block">{{ dashboardError }}</div>
      <div v-else class="dashboard-stack">
        <div class="surface-card">
          <div class="section-head">
            <h4>核心指标</h4>
            <p>运行效率 + 教学质量融合视图</p>
          </div>
          <div class="metric-cards">
            <div class="metric-card">
              <div class="metric-label">AI 辅导次数</div>
              <div class="metric-value">{{ overview.data.total_calls || 0 }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">教学任务派发</div>
              <div class="metric-value">{{ overview.data.total_dispatches || 0 }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">平均响应时间</div>
              <div class="metric-value">{{ overview.data.avg_latency_ms || 0 }} ms</div>
            </div>
            <div class="metric-card" :class="{ danger: failureRate > 0.05 }">
              <div class="metric-label">辅导失败率</div>
              <div class="metric-value">{{ formatPercent(failureRate) }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">学情记忆命中</div>
              <div class="metric-value">{{ formatPercent(overview.data.memory_hit_rate || 0) }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">最新平均分</div>
              <div class="metric-value">{{ formatScore(evaluations.data.latest?.avg_overall_score) }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">最近评测样本</div>
              <div class="metric-value">{{ evaluations.data.latest?.sample_count || 0 }}</div>
            </div>
          </div>
        </div>

        <div class="overview-split">
          <div class="surface-card">
            <div class="section-head">
              <h4>Agent 维度表现</h4>
              <p>按教学环节比较稳定性和效率</p>
            </div>
            <el-table
              :data="overview.data.by_agent || []"
              size="small"
              border
              stripe
              class="by-agent-table"
            >
              <el-table-column prop="agent" label="AI 助教 / 教学环节" header-align="center" align="center">
                <template #default="{ row }">
                  {{ translateAgentLabel(row.agent) }}
                </template>
              </el-table-column>
              <el-table-column prop="calls" label="辅导次数" width="100" header-align="center" align="center" />
              <el-table-column prop="avg_latency_ms" label="响应时间 (毫秒)" width="140" header-align="center" align="center" />
              <el-table-column prop="failure_count" label="失败次数" width="120" header-align="center" align="center" />
              <el-table-column label="失败率" width="120" header-align="center" align="center">
                <template #default="{ row }">
                  {{ formatPercent(row.failure_rate || 0) }}
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="surface-card side-card">
            <div class="section-head">
              <h4>质量失败桶</h4>
              <p>定位最易失分的环节</p>
            </div>
            <el-table
              :data="evaluations.data.failure_buckets || []"
              size="small"
              border
              stripe
              class="by-agent-table"
            >
              <el-table-column prop="failure_bucket" label="失败类型" header-align="center" align="center">
                <template #default="{ row }">
                  {{ translateFailureBucket(row.failure_bucket) }}
                </template>
              </el-table-column>
              <el-table-column prop="fail_count" label="次数" width="120" header-align="center" align="center" />
            </el-table>
          </div>
        </div>

        <div class="overview-split">
          <div class="surface-card">
            <div class="section-head">
              <h4>小时调用趋势</h4>
              <p>观察峰值时段，指导排班与资源预算</p>
            </div>
            <div ref="hourlyChart" class="chart-block"></div>
          </div>
          <div class="surface-card">
            <div class="section-head">
              <h4>平均评分趋势</h4>
              <p>{{ qualityTrendSubtitle }}</p>
            </div>
            <div ref="evalTrendChart" class="chart-block"></div>
          </div>
        </div>
      </div>
    </Panel>
  </div>
</template>

<script>
import api from '../../api.js'
import echarts from 'echarts'

const EMPTY_OVERVIEW = {
  total_calls: 0,
  total_dispatches: 0,
  avg_latency_ms: 0,
  failure_count: 0,
  failure_rate: 0,
  memory_hit_rate: 0,
  by_agent: [],
  hourly_trend: []
}

const AGENT_LABEL_MAP = {
  CHAT: '对话问答',
  IDEATING: '思路分析',
  KNOWLEDGE_REVIEW: '知识点回顾',
  PLAN_RESPONSE: '计划反馈',
  PLAN_START: '计划启动',
  PLAN_STEERING: '计划调控',
  READING: '审题导读',
  SKELETON: '骨架代码',
  VISUALIZE: '教学可视化',
  error_diagnosis: '错因诊断',
  problem_guide: '审题引导',
  ideate_analysis: '思路点拨',
  post_ac: '过题总结',
  transfer_problem: '迁移变式',
  worked_example: '例题讲解',
  minimal_hint: '最小提示',
  'event:KNOWLEDGE_REVIEW': '知识点回顾',
  'event:ERROR_FEEDBACK': '错误反馈',
  'event:READING': '阅读理解',
  'event:IDEATING': '思路构建',
  'event:AC_REVIEW': '过题复盘'
}

const FAILURE_BUCKET_LABEL_MAP = {
  SCHEMA_VIOLATION: '卡片结构不符合规范',
  SYSTEM_ERROR: '系统错误',
  TOOL_EXECUTION_FAILED: '工具调用失败',
  memory_miss: '记忆检索缺失',
  logic_branch: '逻辑分支错误',
  syntax_error: '语法错误',
  branch_mismatch: '分支条件不匹配',
  loop_boundary: '循环边界错误',
  loop_logic: '循环逻辑错误',
  indentation: '缩进错误'
}

export default {
  name: 'ObservabilityDashboard',
  data () {
    return {
      range: '7d',
      overview: { loading: false, error: '', data: { ...EMPTY_OVERVIEW } },
      evaluations: { loading: false, error: '', data: { latest: {}, trend: [], failure_buckets: [] } },
      resizeHandler: null,
      charts: {
        hourly: null,
        evalTrend: null
      }
    }
  },
  computed: {
    dashboardLoading () {
      return this.overview.loading || this.evaluations.loading
    },
    dashboardError () {
      return this.overview.error || this.evaluations.error
    },
    failureRate () {
      const value = Number(this.overview.data && this.overview.data.failure_rate)
      return Number.isFinite(value) ? value : 0
    },
    qualityTrendSubtitle () {
      return '近阶段 LLM-as-Judge 教学质量走势'
    }
  },
  mounted () {
    this.resizeHandler = () => this.resizeAllCharts()
    window.addEventListener('resize', this.resizeHandler)
    this.loadOverviewDashboard()
  },
  beforeUnmount () {
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler)
      this.resizeHandler = null
    }
    this.disposeAllCharts()
  },
  methods: {
    loadOverviewDashboard () {
      this.loadOverview()
      this.loadEvaluations()
    },
    loadOverview () {
      this.overview.loading = true
      this.overview.error = ''
      api.getAgentsOverview(this.range).then(res => {
        const data = (res && res.data && res.data.data) || { ...EMPTY_OVERVIEW }
        this.overview.data = Object.assign({}, EMPTY_OVERVIEW, data)
      }).catch(err => {
        this.overview.error = this.formatError(err)
      }).finally(() => {
        this.overview.loading = false
        this.renderOverviewChartsWhenReady()
      })
    },
    loadEvaluations () {
      this.evaluations.loading = true
      this.evaluations.error = ''
      api.getEvaluationsDashboard(this.range).then(res => {
        const data = (res && res.data && res.data.data) || {}
        this.evaluations.data = {
          latest: data.latest || {},
          trend: Array.isArray(data.trend) ? data.trend : [],
          failure_buckets: Array.isArray(data.failure_buckets) ? data.failure_buckets : []
        }
      }).catch(err => {
        this.evaluations.error = this.formatError(err)
      }).finally(() => {
        this.evaluations.loading = false
        this.renderOverviewChartsWhenReady()
      })
    },
    renderOverviewChartsWhenReady () {
      this.$nextTick(() => {
        if (this.overview.loading || this.evaluations.loading) return
        if (this.overview.error || this.evaluations.error) return
        this.renderHourlyChart()
        this.renderEvalTrendChart()
      })
    },
    renderHourlyChart () {
      if (!this.$refs.hourlyChart) return
      const series = (this.overview.data.hourly_trend || []).map(row => ({
        ts: this.formatHourLabel(row.bucket),
        value: Number(row.call_count) || 0
      }))
      this.disposeChart('hourly')
      const chart = echarts.init(this.$refs.hourlyChart)
      chart.setOption({
        backgroundColor: 'transparent',
        title: { text: 'Agent 调用小时分布', left: 'center', textStyle: { fontSize: 12, color: '#0f172a' } },
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: series.map(s => s.ts),
          axisLabel: { rotate: 30, fontSize: 10, color: '#1d4ed8' },
          axisLine: { lineStyle: { color: 'rgba(37, 99, 235, 0.3)' } }
        },
        yAxis: {
          type: 'value',
          name: '调用次数',
          axisLabel: { color: '#1d4ed8' },
          splitLine: { lineStyle: { color: 'rgba(37, 99, 235, 0.16)' } }
        },
        series: [{
          name: 'calls',
          type: 'line',
          smooth: true,
          data: series.map(s => s.value),
          lineStyle: { width: 3, color: '#2563eb' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(37, 99, 235, 0.32)' },
              { offset: 1, color: 'rgba(37, 99, 235, 0.04)' }
            ])
          },
          itemStyle: { color: '#2563eb' }
        }]
      })
      this.charts.hourly = chart
    },
    renderEvalTrendChart () {
      if (!this.$refs.evalTrendChart) return
      const trend = this.evaluations.data.trend || []
      this.disposeChart('evalTrend')
      const chart = echarts.init(this.$refs.evalTrendChart)
      chart.setOption({
        title: { text: '平均评分趋势', left: 'center', textStyle: { fontSize: 12, color: '#0f172a' } },
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: trend.map(p => this.formatHourLabel(p.created_at)),
          axisLabel: { rotate: 30, fontSize: 10, color: '#1d4ed8' },
          axisLine: { lineStyle: { color: 'rgba(59, 130, 246, 0.32)' } }
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 1,
          axisLabel: { color: '#1d4ed8' },
          splitLine: { lineStyle: { color: 'rgba(59, 130, 246, 0.16)' } }
        },
        series: [{
          name: 'avg_overall_score',
          type: 'line',
          smooth: true,
          data: trend.map(p => Number(p.avg_overall_score) || 0),
          lineStyle: { width: 3, color: '#3b82f6' },
          itemStyle: { color: '#3b82f6' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(59, 130, 246, 0.3)' },
              { offset: 1, color: 'rgba(59, 130, 246, 0.03)' }
            ])
          }
        }]
      })
      this.charts.evalTrend = chart
    },
    disposeChart (key) {
      if (this.charts[key]) {
        this.charts[key].dispose()
        this.charts[key] = null
      }
    },
    disposeAllCharts () {
      Object.keys(this.charts).forEach(k => this.disposeChart(k))
    },
    resizeAllCharts () {
      Object.values(this.charts).forEach(chart => {
        if (chart && typeof chart.resize === 'function') {
          chart.resize()
        }
      })
    },
    formatPercent (value) {
      if (typeof value !== 'number' || !Number.isFinite(value)) return '0%'
      return `${(value * 100).toFixed(1)}%`
    },
    formatScore (value) {
      if (typeof value !== 'number' || !Number.isFinite(value)) return '-'
      return value.toFixed(3)
    },
    formatHourLabel (value) {
      if (!value) return ''
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return String(value).slice(0, 13)
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hour = String(date.getHours()).padStart(2, '0')
      return `${month}-${day} ${hour}:00`
    },
    formatError (err) {
      if (!err) return '加载失败'
      if (err.message) return err.message
      if (err.data && err.data.data) return String(err.data.data)
      return '加载失败'
    },
    translateAgentLabel (value) {
      const key = typeof value === 'string' ? value.trim() : ''
      if (!key) return '-'
      return AGENT_LABEL_MAP[key] || key
    },
    translateFailureBucket (value) {
      const key = typeof value === 'string' ? value.trim() : ''
      if (!key) return '-'
      return FAILURE_BUCKET_LABEL_MAP[key] || key
    }
  }
}
</script>

<style scoped lang="less">
@import url('https://fonts.googleapis.com/css2?family=Fira+Code:wght@500;600&family=Fira+Sans:wght@400;500;600;700&display=swap');

.view {
  --dashboard-blue-700: #2563eb;
  --dashboard-text-900: #0f172a;

  display: flex;
  flex-direction: column;
  gap: 24px;
  font-family: 'Fira Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background:
    radial-gradient(circle at 16% 12%, rgba(59, 130, 246, 0.14), transparent 30%),
    radial-gradient(circle at 84% 8%, rgba(147, 197, 253, 0.2), transparent 28%),
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.range-picker {
  display: inline-flex;
}

.loading-block,
.error-block {
  padding: 20px;
  text-align: center;
  color: #64748b;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.74);
  border: 1px dashed rgba(37, 99, 235, 0.26);
}

.error-block {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.3);
}

.dashboard-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.overview-split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.surface-card {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  background: linear-gradient(162deg, rgba(255, 255, 255, 0.96) 0%, rgba(248, 250, 252, 0.94) 100%);
  box-shadow: 0 16px 28px -24px rgba(15, 23, 42, 0.3);
}

.surface-card.side-card {
  min-width: 280px;
}

.section-head {
  margin-bottom: 12px;
}

.section-head h4 {
  margin: 0;
  font-size: 14px;
  color: var(--dashboard-text-900);
}

.section-head p {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: #64748b;
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(158px, 1fr));
  gap: 12px;
  margin-bottom: 4px;
}

.metric-card {
  padding: 12px 14px;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.96) 0%, rgba(255, 255, 255, 0.96) 100%);
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 12px;
  transition: transform 180ms ease, box-shadow 180ms ease;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 22px -18px rgba(15, 23, 42, 0.42);
}

.metric-card.danger {
  background: linear-gradient(180deg, rgba(254, 242, 242, 0.96) 0%, rgba(255, 255, 255, 0.94) 100%);
  border-color: rgba(248, 113, 113, 0.44);
}

.metric-label {
  font-size: 12px;
  color: #475569;
  margin-bottom: 6px;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--dashboard-text-900);
}

.by-agent-table {
  margin-top: 4px;
  border-radius: 12px;
  overflow: hidden;

  :deep(.el-table__header-wrapper th) {
    background: rgba(37, 99, 235, 0.08);
    color: var(--dashboard-text-900);
    font-weight: 600;
  }
}

.chart-block {
  width: 100%;
  height: 300px;
  margin-top: 8px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(239, 246, 255, 0.92) 100%);
}

:deep(.el-button:focus-visible),
:deep(.el-input__wrapper.is-focus),
:deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.25);
}

@media (max-width: 1200px) {
  .overview-split {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .toolbar {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .metric-card {
    min-height: 92px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .metric-card {
    transition: none;
  }
}
</style>

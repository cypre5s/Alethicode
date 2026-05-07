<template>
  <div class="view">
    <Panel title="学生学习数据">
      <template #header>
        <div class="toolbar">
          <ElRadioGroup v-model="range" size="small" class="range-picker" @change="reload">
            <ElRadioButton value="today">今日</ElRadioButton>
            <ElRadioButton value="7d">近 7 天</ElRadioButton>
            <ElRadioButton value="30d">近 30 天</ElRadioButton>
          </ElRadioGroup>
          <ElButton size="small" type="primary" :loading="loading" @click="reload">刷新</ElButton>
        </div>
      </template>

      <div v-if="loading && !data" class="loading-block">加载中…</div>
      <div v-else-if="error" class="error-block">{{ error }}</div>
      <div v-else-if="data" class="dashboard-stack">
        <div class="surface-card">
          <div class="section-head">
            <h4>核心指标</h4>
            <p>{{ rangeLabel }}的整体活跃、学习效果与用户反馈一览</p>
          </div>
          <div class="kpi-grid">
            <div class="kpi-card kpi-card--primary">
              <div class="kpi-label">注册学生</div>
              <div class="kpi-value">{{ overview.total_students }}</div>
              <div class="kpi-foot">总账号 {{ overview.total_users }}</div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ rangeLabel }}活跃</div>
              <div class="kpi-value">{{ overview.active_users }}</div>
              <div class="kpi-foot">覆盖率 {{ formatPercent(activeCoverage) }}</div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ rangeLabel }}提交</div>
              <div class="kpi-value">{{ overview.recent_submissions }}</div>
              <div class="kpi-foot">累计 {{ overview.total_submissions }}</div>
            </div>
            <div class="kpi-card kpi-card--accent">
              <div class="kpi-label">AC 率</div>
              <div class="kpi-value">{{ formatPercent(overview.ac_rate) }}</div>
              <div class="kpi-foot">AC {{ overview.recent_ac }} / {{ overview.recent_submissions }}</div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ rangeLabel }}反馈</div>
              <div class="kpi-value">{{ feedbackSummary.total }}</div>
              <div class="kpi-foot">阻塞 {{ severityCount('blocker') }} · 严重 {{ severityCount('high') }}</div>
            </div>
          </div>
        </div>
        <div class="surface-card">
          <div class="section-head">
            <h4>每日活跃趋势</h4>
            <p>提交人数、提交总数随时间的分布</p>
          </div>
          <div class="chart-block">
            <Line v-if="dailyChartData" :data="dailyChartData" :options="dailyChartOptions" />
            <div v-else class="chart-empty">暂无活跃数据</div>
          </div>
        </div>
        <div class="row-split">
          <div class="surface-card">
            <div class="section-head">
              <h4>学习效果</h4>
              <p>反映从首次提交到通过的学习曲线，以及做题速度</p>
            </div>
            <div class="metric-cards">
              <div class="metric-card">
                <div class="metric-label">首次 AC 率</div>
                <div class="metric-value">{{ formatPercent(overview.first_ac_rate) }}</div>
                <div class="metric-foot">{{ overview.first_ac_count }} / {{ overview.first_attempt_total }} 首次提交</div>
              </div>
              <div class="metric-card">
                <div class="metric-label">平均到 AC 时长</div>
                <div class="metric-value">{{ formatDuration(overview.avg_to_ac_seconds) }}</div>
                <div class="metric-foot">从首次提交到 AC</div>
              </div>
              <div class="metric-card">
                <div class="metric-label">做题学生数</div>
                <div class="metric-value">{{ overview.active_users }}</div>
                <div class="metric-foot">{{ rangeLabel }}有提交的学生</div>
              </div>
              <div class="metric-card">
                <div class="metric-label">人均提交</div>
                <div class="metric-value">{{ overview.active_users > 0 ? Math.round(overview.recent_submissions / overview.active_users * 10) / 10 : 0 }}</div>
                <div class="metric-foot">{{ rangeLabel }}活跃学生平均</div>
              </div>
            </div>
          </div>
          <div class="surface-card">
            <div class="section-head">
              <h4>错误类型分布</h4>
              <p>{{ rangeLabel }}学生常见错误分类</p>
            </div>
            <div class="chart-block chart-block-short">
              <Bar v-if="errorTypeChartData" :data="errorTypeChartData" :options="cardChartOptions" />
              <div v-else class="chart-empty">暂无错误类型数据</div>
            </div>
          </div>
        </div>
        <div class="row-split">
          <div class="surface-card">
            <div class="section-head">
              <h4>高 WA 题目</h4>
              <p>≥ 5 次尝试且 AC 率最低的前 10 道</p>
            </div>
            <ElTable
              :data="painPoints.high_wa_problems || []"
              size="small"
              border
              stripe
              class="dashboard-table"
              empty-text="暂无数据"
            >
              <ElTableColumn prop="display_id" label="题号" width="120" header-align="center" align="center" />
              <ElTableColumn prop="title" label="标题" min-width="200" show-overflow-tooltip />
              <ElTableColumn prop="attempts" label="尝试" width="80" header-align="center" align="center" />
              <ElTableColumn label="AC 率" width="100" header-align="center" align="center">
                <template #default="scope">{{ formatPercent(scope.row.ac_count / scope.row.attempts) }}</template>
              </ElTableColumn>
              <ElTableColumn prop="user_count" label="学生数" width="90" header-align="center" align="center" />
            </ElTable>
          </div>
          <div class="surface-card">
            <div class="section-head">
              <h4>高重试题目</h4>
              <p>人均尝试 ≥ 5 次的前 10 道</p>
            </div>
            <ElTable
              :data="painPoints.stuck_problems || []"
              size="small"
              border
              stripe
              class="dashboard-table"
              empty-text="暂无数据"
            >
              <ElTableColumn prop="display_id" label="题号" width="120" header-align="center" align="center" />
              <ElTableColumn prop="title" label="标题" min-width="200" show-overflow-tooltip />
              <ElTableColumn prop="user_count" label="学生数" width="90" header-align="center" align="center" />
              <ElTableColumn prop="avg_attempts" label="人均尝试" width="100" header-align="center" align="center" />
              <ElTableColumn prop="stuck_users" label="未 AC 数" width="100" header-align="center" align="center" />
            </ElTable>
          </div>
        </div>
        <div class="row-split">
          <div class="surface-card">
            <div class="section-head">
              <h4>反馈按严重程度</h4>
              <p>{{ rangeLabel }}收到反馈共 {{ feedbackSummary.total }} 条</p>
            </div>
            <div v-if="!feedbackSummary.by_severity || feedbackSummary.by_severity.length === 0" class="chart-empty">暂无反馈</div>
            <div v-else class="feedback-rows">
              <div v-for="row in feedbackSummary.by_severity" :key="row.severity" class="feedback-row">
                <span class="feedback-key">{{ severityLabel(row.severity) }}</span>
                <span class="feedback-bar"><span class="feedback-bar-fill" :style="{ width: feedbackPercent(row.cnt, feedbackSummary.total) + '%' }"></span></span>
                <span class="feedback-count">{{ row.cnt }}</span>
              </div>
            </div>
          </div>
          <div class="surface-card">
            <div class="section-head">
              <h4>反馈按问题类型</h4>
              <p>用户自报的问题分类</p>
            </div>
            <div v-if="!feedbackSummary.by_type || feedbackSummary.by_type.length === 0" class="chart-empty">暂无反馈</div>
            <div v-else class="feedback-rows">
              <div v-for="row in feedbackSummary.by_type" :key="row.type" class="feedback-row">
                <span class="feedback-key">{{ typeLabel(row.type) }}</span>
                <span class="feedback-bar"><span class="feedback-bar-fill" :style="{ width: feedbackPercent(row.cnt, feedbackSummary.total) + '%' }"></span></span>
                <span class="feedback-count">{{ row.cnt }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Panel>
  </div>
</template>

<script>
import { Bar, Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  LineElement,
  PointElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js'
import api from '../../api'

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  LineElement,
  PointElement,
  Title,
  Tooltip,
  Legend,
  Filler
)

const SEVERITY_LABELS = {
  blocker: '阻塞',
  high: '严重',
  medium: '一般',
  low: '改进建议'
}

const TYPE_LABELS = {
  cant_open: '页面无法访问',
  button_dead: '按钮无响应',
  page_confusing: '界面表达不清',
  wrong_problem_or_answer: '题目/答案错误',
  ai_unclear: 'AI 解释不清',
  submit_wrong: '提交结果异常',
  other: '其他'
}

const CARD_TYPE_LABELS = {
  problem_guide: '审题导读',
  ideate_analysis: '思路分析',
  faded_example: '渐退示例',
  parsons_problem: '排序题',
  error_diagnosis: '错误诊断',
  execution_trace_explainer: '执行追踪',
  post_ac: 'AC 反思',
  transfer_problem: '迁移练习',
  knowledge_review: '知识回顾',
  ai_reply: '对话回复',
  visualize: '可视化'
}

export default {
  name: 'UsageStats',
  components: { Line, Bar },
  data () {
    return {
      range: '7d',
      data: null,
      loading: false,
      error: ''
    }
  },
  computed: {
    overview () { return (this.data && this.data.overview) || {} },
    aiValue () { return (this.data && this.data.ai_value) || {} },
    painPoints () { return (this.data && this.data.pain_points) || {} },
    feedbackSummary () { return (this.data && this.data.feedback_summary) || {} },
    dailyActive () { return (this.data && this.data.daily_active) || [] },
    rangeLabel () {
      switch (this.range) {
        case 'today': return '今日'
        case '30d': return '近 30 天'
        default: return '近 7 天'
      }
    },
    activeCoverage () {
      const total = this.overview.total_students || 0
      const active = this.overview.active_users || 0
      return total === 0 ? 0 : active / total
    },
    dailyChartData () {
      if (!this.dailyActive.length) return null
      const labels = this.dailyActive.map(r => this.formatDay(r.day))
      return {
        labels,
        datasets: [
          {
            label: '提交人数',
            data: this.dailyActive.map(r => r.sub_users || 0),
            borderColor: '#2563eb',
            backgroundColor: 'rgba(37, 99, 235, 0.15)',
            tension: 0.35,
            fill: true,
            yAxisID: 'y'
          },
          {
            label: '提交数',
            data: this.dailyActive.map(r => r.submissions || 0),
            borderColor: '#10b981',
            backgroundColor: 'transparent',
            tension: 0.35,
            yAxisID: 'y1'
          }
        ]
      }
    },
    dailyChartOptions () {
      return {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { position: 'bottom', labels: { boxWidth: 12, padding: 12, font: { size: 12 } } },
          tooltip: { mode: 'index', intersect: false }
        },
        scales: {
          y: {
            type: 'linear',
            position: 'left',
            title: { display: true, text: '人数', font: { size: 11 } },
            beginAtZero: true,
            ticks: { precision: 0 }
          },
          y1: {
            type: 'linear',
            position: 'right',
            title: { display: true, text: '次数', font: { size: 11 } },
            beginAtZero: true,
            grid: { drawOnChartArea: false },
            ticks: { precision: 0 }
          }
        }
      }
    },
    errorTypeChartData () {
      const dist = (this.painPoints && this.painPoints.error_distribution) || []
      if (!dist.length) return null
      const ERROR_LABELS = {
        syntax_error: '语法错误', runtime_error: '运行时错误',
        logic_error: '逻辑错误', algorithm_error: '算法错误',
        name_or_type_error: '命名/类型错误', indentation: '缩进错误',
        compilation_error: '编译错误', time_limit: '超时',
        memory_limit: '内存超限', unknown: '其他'
      }
      return {
        labels: dist.map(r => ERROR_LABELS[r.error_type] || r.error_type || '其他'),
        datasets: [{
          label: '出现次数',
          data: dist.map(r => Number(r.cnt) || 0),
          backgroundColor: [
            '#ef4444', '#f59e0b', '#3b82f6', '#8b5cf6',
            '#10b981', '#06b6d4', '#a855f7', '#84cc16',
            '#f97316', '#94a3b8'
          ],
          borderRadius: 6
        }]
      }
    },
    cardChartOptions () {
      return {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: { displayColors: false }
        },
        scales: {
          y: { beginAtZero: true, ticks: { precision: 0 } }
        }
      }
    }
  },
  mounted () {
    this.reload()
  },
  methods: {
    async reload () {
      this.loading = true
      this.error = ''
      try {
        const res = await api.getUsageStats(this.range)
        this.data = (res.data && res.data.data) || null
      } catch (err) {
        const msg = (err && err.data && err.data.data) || (err && err.message) || '加载失败'
        this.error = typeof msg === 'string' ? msg : '加载失败'
      } finally {
        this.loading = false
      }
    },
    formatPercent (val) {
      if (val === null || val === undefined || Number.isNaN(Number(val))) return '—'
      return (Number(val) * 100).toFixed(1) + '%'
    },
    formatDuration (seconds) {
      if (seconds === null || seconds === undefined || Number.isNaN(Number(seconds))) return '—'
      const s = Math.round(Number(seconds))
      if (s < 60) return s + ' 秒'
      const m = Math.floor(s / 60)
      if (m < 60) return m + ' 分 ' + (s % 60) + ' 秒'
      const h = Math.floor(m / 60)
      return h + ' 时 ' + (m % 60) + ' 分'
    },
    formatDay (raw) {
      if (!raw) return ''
      const date = typeof raw === 'string' ? raw.slice(0, 10) : new Date(raw).toISOString().slice(0, 10)
      return date.slice(5)
    },
    severityLabel (key) { return SEVERITY_LABELS[key] || key },
    typeLabel (key) { return TYPE_LABELS[key] || key },
    severityCount (key) {
      const list = (this.feedbackSummary && this.feedbackSummary.by_severity) || []
      const row = list.find(r => r.severity === key)
      return row ? row.cnt : 0
    },
    feedbackPercent (cnt, total) {
      const t = Number(total) || 0
      const c = Number(cnt) || 0
      if (t === 0) return 0
      return Math.round((c / t) * 100)
    }
  }
}
</script>

<style scoped lang="less">
@import url('https://fonts.googleapis.cn/css2?family=Fira+Sans:wght@400;500;600;700&display=swap');

.view {
  --dashboard-blue-700: #2563eb;
  --dashboard-text-900: #0f172a;
  --dashboard-text-600: #475569;
  --dashboard-text-500: #64748b;

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
.error-block,
.chart-empty {
  padding: 28px 20px;
  text-align: center;
  color: var(--dashboard-text-500);
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

.surface-card {
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  background: linear-gradient(162deg, rgba(255, 255, 255, 0.96) 0%, rgba(248, 250, 252, 0.94) 100%);
  box-shadow: 0 16px 28px -24px rgba(15, 23, 42, 0.3);
}

.section-head {
  margin-bottom: 14px;
}

.section-head h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--dashboard-text-900);
  letter-spacing: 0.2px;
}

.section-head p {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: var(--dashboard-text-500);
}

/* 顶部 KPI 6 张大卡片 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  position: relative;
  padding: 16px 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(248, 250, 252, 0.94) 100%);
  border: 1px solid rgba(37, 99, 235, 0.16);
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 110px;
  transition: transform 180ms ease, box-shadow 180ms ease;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 26px -18px rgba(15, 23, 42, 0.34);
}

.kpi-card--primary {
  background: linear-gradient(180deg, rgba(219, 234, 254, 0.96) 0%, rgba(239, 246, 255, 0.92) 100%);
  border-color: rgba(37, 99, 235, 0.32);
}

.kpi-card--accent {
  background: linear-gradient(180deg, rgba(254, 243, 199, 0.92) 0%, rgba(255, 251, 235, 0.92) 100%);
  border-color: rgba(217, 119, 6, 0.28);
}

.kpi-label {
  font-size: 12px;
  color: var(--dashboard-text-600);
  font-weight: 500;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--dashboard-text-900);
  line-height: 1.1;
  letter-spacing: -0.5px;
}

.kpi-foot {
  font-size: 11px;
  color: var(--dashboard-text-500);
  margin-top: auto;
}

/* 中部小数字卡 */
.metric-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 14px;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.96) 0%, rgba(255, 255, 255, 0.96) 100%);
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 12px;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 100px;
  transition: transform 180ms ease, box-shadow 180ms ease;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 22px -18px rgba(15, 23, 42, 0.42);
}

.metric-card--accent {
  background: linear-gradient(180deg, rgba(254, 243, 199, 0.92) 0%, rgba(255, 251, 235, 0.92) 100%);
  border-color: rgba(217, 119, 6, 0.28);
}

.metric-label {
  font-size: 12px;
  color: var(--dashboard-text-600);
  font-weight: 500;
}

.metric-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--dashboard-text-900);
  line-height: 1.1;
}

.metric-foot {
  font-size: 11px;
  color: var(--dashboard-text-500);
  margin-top: auto;
}

/* 两列并列布局 */
.row-split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.dashboard-table {
  margin-top: 4px;
  border-radius: 12px;
  overflow: hidden;

  :deep(.el-table__header-wrapper th) {
    background: rgba(37, 99, 235, 0.08);
    color: var(--dashboard-text-900);
    font-weight: 600;
  }

  :deep(.el-table__header-wrapper th .cell) {
    white-space: nowrap;
  }
}

.chart-block {
  width: 100%;
  height: 320px;
  margin-top: 4px;
  padding: 12px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(239, 246, 255, 0.92) 100%);
  position: relative;
}

.chart-block-short {
  height: 260px;
}

.feedback-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.feedback-row {
  display: grid;
  grid-template-columns: 110px 1fr 50px;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--dashboard-text-900);
}

.feedback-bar {
  height: 8px;
  background: rgba(37, 99, 235, 0.08);
  border-radius: 999px;
  overflow: hidden;
}

.feedback-bar-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #3b82f6 0%, #60a5fa 100%);
  border-radius: 999px;
  transition: width 240ms ease;
}

.feedback-count {
  text-align: right;
  color: var(--dashboard-text-600);
  font-weight: 500;
}

/* 响应式 */
@media (max-width: 1280px) {
  .kpi-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 900px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .row-split { grid-template-columns: minmax(0, 1fr); }
  .metric-cards { grid-template-columns: minmax(0, 1fr); }
  .toolbar { width: 100%; flex-wrap: wrap; }
}

@media (prefers-reduced-motion: reduce) {
  .kpi-card,
  .metric-card,
  .feedback-bar-fill {
    transition: none;
  }
}
</style>

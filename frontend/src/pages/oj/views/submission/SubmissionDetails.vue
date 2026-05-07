<template>
  <div class="submission-details-container">
    <div class="left-panel">
      <div class="performance-section">
        <div v-if="problemDisplayId" class="problem-info-bar">
          <router-link :to="'/problem/' + problemDisplayId" class="problem-link">
            <el-icon :size="16"><Document /></el-icon>
            <span class="problem-id">{{ problemDisplayId }}</span>
            <span v-if="problemTitle" class="problem-title">{{ problemTitle }}</span>
          </router-link>
          <el-tag type="primary">{{ submission.language }}</el-tag>
          <span v-if="statisticsTotalCount > 0" class="stats-count">共 {{ statisticsTotalCount }} 份 AC 提交</span>
        </div>

        <div class="distribution-chart">
          <div class="chart-header">
            <el-icon :size="20"><Clock /></el-icon>
            <span class="chart-title">执行用时分布</span>
          </div>
          <div class="stats-summary">
            <span class="stats-value">{{ submissionTime(submission.statistic_info.time_cost) }}</span>
            <span class="stats-beat">击败 {{ runtimePercentage }}%</span>
          </div>
          <div class="chart-container">
            <div class="y-axis">
              <span class="y-label">75%</span>
              <span class="y-label">50%</span>
              <span class="y-label">25%</span>
              <span class="y-label">0%</span>
            </div>
            <div class="bars-container">
              <div
                v-for="(bar, index) in runtimeBars"
                :key="'runtime-' + index"
                class="bar-wrapper"
              >
                <div
                  class="bar"
                  :class="{ 'bar-active': bar.isUserSubmission }"
                  :style="{ height: bar.height + '%' }"
                >
                  <div v-if="bar.isUserSubmission" class="user-marker">
                    <el-icon :size="20" color="#1890ff"><User /></el-icon>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="x-axis-labels">
            <span v-for="(label, index) in runtimeLabels" :key="'label-' + index">{{ label }}</span>
          </div>
          <div class="chart-footer">
            击败 {{ runtimePercentage }}% 使用 {{ submission.language }} 的用户
          </div>
        </div>

        <div class="distribution-chart">
          <div class="chart-header">
            <el-icon :size="20"><DataAnalysis /></el-icon>
            <span class="chart-title">内存消耗分布</span>
          </div>
          <div class="stats-summary">
            <span class="stats-value">{{ submissionMemory(submission.statistic_info.memory_cost) }}</span>
            <span class="stats-beat">击败 {{ memoryPercentage }}%</span>
          </div>
          <div class="chart-container">
            <div class="y-axis">
              <span class="y-label">75%</span>
              <span class="y-label">50%</span>
              <span class="y-label">25%</span>
              <span class="y-label">0%</span>
            </div>
            <div class="bars-container">
              <div
                v-for="(bar, index) in memoryBars"
                :key="'memory-' + index"
                class="bar-wrapper"
              >
                <div
                  class="bar"
                  :class="{ 'bar-active': bar.isUserSubmission }"
                  :style="{ height: bar.height + '%' }"
                >
                  <div v-if="bar.isUserSubmission" class="user-marker">
                    <el-icon :size="20" color="#1890ff"><User /></el-icon>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="x-axis-labels">
            <span v-for="(label, index) in memoryLabels" :key="'label-' + index">{{ label }}</span>
          </div>
          <div class="chart-footer">
            击败 {{ memoryPercentage }}% 使用 {{ submission.language }} 的用户
          </div>
        </div>
      </div>
    </div>
    <div class="right-panel">
      <el-row justify="space-around">
        <el-col :span="23" id="status">
          <el-alert :type="status.type" show-icon :closable="false">
            <template #title><span class="title">{{$t('m.' + status.statusName.replace(/ /g, "_"))}}</span></template>
            <div class="content">
              <template v-if="isCE">
                <pre>{{submission.statistic_info.err_info}}</pre>
              </template>
              <template v-else>
                <span>{{$t('m.Time')}}: {{ submissionTime(submission.statistic_info.time_cost) }}</span>
                <span>{{$t('m.Memory')}}: {{ submissionMemory(submission.statistic_info.memory_cost) }}</span>
                <span>{{$t('m.Lang')}}: {{submission.language}}</span>
                <span>{{$t('m.Author')}}: {{submission.username}}</span>
              </template>
            </div>
          </el-alert>
        </el-col>
        <el-col v-if="submission.info && !isCE" :span="23">
          <el-table stripe :data="submission.info.data" v-loading="loading">
            <el-table-column :label="$t('m.ID')" align="center" type="index" />

            <el-table-column :label="$t('m.Status')" align="center">
              <template #default="scope">
                <el-tag
                  :color="JUDGE_STATUS[scope.row.result].color"
                  effect="dark"
                  disable-transitions
                >
                  {{ $t('m.' + JUDGE_STATUS[scope.row.result].name.replace(/ /g, '_')) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column :label="$t('m.Memory')" align="center">
              <template #default="scope">
                {{ submissionMemoryFormat(scope.row.memory) }}
              </template>
            </el-table-column>

            <el-table-column :label="$t('m.Time')" align="center">
              <template #default="scope">
                {{ submissionTimeFormat(scope.row.cpu_time) }}
              </template>
            </el-table-column>

            <el-table-column v-if="hasScoreColumn" :label="$t('m.Score')" align="center" prop="score" />

            <el-table-column v-if="isAdminRole" :label="$t('m.Real_Time')" align="center">
              <template #default="scope">
                {{ submissionTimeFormat(scope.row.real_time) }}
              </template>
            </el-table-column>

            <el-table-column v-if="isAdminRole" :label="$t('m.Signal')" align="center" prop="signal" />
          </el-table>
        </el-col>

        <el-col :span="23">
          <Highlight :code="submission.code" :language="submission.language" :border-color="status.color"></Highlight>
        </el-col>
      </el-row>
    </div>

  </div>
</template>

<script>
  import api from '@oj/api'
  import { Document, Clock, User, DataAnalysis } from '@element-plus/icons-vue'
  import { JUDGE_STATUS } from '@/utils/constants'
  import { sanitize } from '@/utils/sanitize'
  import marked from 'marked'
  import utils from '@/utils/utils'
  import Highlight from '@/pages/oj/components/Highlight'

  const AI_TERMINOLOGY = Object.freeze({
    postSubmissionFix: 'AI 纠错（提交后）',
    postAcOptimization: 'AI 优化（AC 后）'
  })

  export default {
    name: 'SubmissionDetails',
    components: {
      Highlight,
      Document,
      Clock,
      User,
      DataAnalysis
    },
    data () {
      return {
        submission: {
          result: '0',
          code: '',
          info: {
            data: []
          },
          statistic_info: {
            time_cost: '',
            memory_cost: ''
          }
        },
        loading: false,
        hasScoreColumn: false,
        runtimeBars: [],
        memoryBars: [],
        runtimeLabels: [],
        memoryLabels: [],
        runtimePercentage: 0,
        memoryPercentage: 0,
        aiTerminology: AI_TERMINOLOGY,
        problemStatistics: null,
        problemDisplayId: '',
        problemTitle: '',
        statisticsTotalCount: 0,
      }
    },
    mounted () {
      this.getSubmission()
    },
    methods: {
      submissionTime: utils.submissionTimeFormat,
      submissionMemory: utils.submissionMemoryFormat,
      submissionTimeFormat: utils.submissionTimeFormat,
      submissionMemoryFormat: utils.submissionMemoryFormat,
      renderMarkdown (text) {
        if (!text) return ''
        return sanitize(marked(text))
      },
      getSubmission () {
        this.loading = true
        api.getSubmission(this.$route.params.id).then(res => {
          this.loading = false
          let data = res.data.data
          if (data.info && data.info.data) {
            if (data.info.data[0] && data.info.data[0].score !== undefined) {
              this.hasScoreColumn = true
            }
          }
          this.submission = data

          this.getProblemStatistics()
        }, () => {
          this.loading = false
        })
      },
      getProblemStatistics () {
        if (!this.submission.problem) return
        api.getProblemStatistics(this.submission.problem, this.submission.language).then(res => {
          const data = res.data.data
          this.problemStatistics = data
          this.problemDisplayId = data.problem_display_id || ''
          this.problemTitle = data.problem_title || ''
          this.statisticsTotalCount = data.total_count || 0
          this.generateDistributionData()
        })
      },
      generateDistributionData () {
        if (!this.submission || !this.submission.statistic_info) return

        const userTime = parseInt(this.submission.statistic_info.time_cost) || 0
        let userMemory
        const memStr = String(this.submission.statistic_info.memory_cost)
        if (memStr.indexOf('MB') > -1) {
          userMemory = parseFloat(memStr) * 1024 * 1024
        } else if (memStr.indexOf('KB') > -1) {
          userMemory = parseFloat(memStr) * 1024
        } else {
          userMemory = parseInt(memStr) || 0
        }
        const userMemoryMB = userMemory / (1024 * 1024)

        const timeCosts = (this.problemStatistics && this.problemStatistics.time_costs) ? this.problemStatistics.time_costs : []
        const memoryCosts = (this.problemStatistics && this.problemStatistics.memory_costs) ? this.problemStatistics.memory_costs : []

        let effectiveTimeCosts = [...timeCosts]
        if (effectiveTimeCosts.length === 0) {
          effectiveTimeCosts.push(userTime)
        }

        let effectiveMemoryCosts = [...memoryCosts]
        if (effectiveMemoryCosts.length === 0) {
          effectiveMemoryCosts.push(userMemoryMB)
        }

        this.generateRuntimeDistribution(userTime, effectiveTimeCosts)
        this.generateMemoryDistribution(userMemoryMB, effectiveMemoryCosts)
      },
      generateRuntimeDistribution (userTime, allTimes) {
        if (!allTimes || allTimes.length === 0) return

        let minTime = Math.min(...allTimes)
        let maxTime = Math.max(...allTimes)

        if (maxTime === minTime) {
          minTime = Math.max(0, minTime - 10)
          maxTime = maxTime + 10
        }

        minTime = Math.min(minTime, userTime)
        maxTime = Math.max(maxTime, userTime)

        minTime = Math.max(0, Math.floor(minTime * 0.9))
        maxTime = Math.ceil(maxTime * 1.1)

        if (maxTime - minTime < 20) {
          const center = (minTime + maxTime) / 2
          minTime = Math.max(0, Math.floor(center - 10))
          maxTime = Math.ceil(center + 10)
        }

        const barCount = 30
        const step = (maxTime - minTime) / barCount

        this.runtimeLabels = []
        for (let i = 0; i < barCount; i += 5) {
          const time = Math.floor(minTime + step * i)
          this.runtimeLabels.push(time + 'ms')
        }

        const buckets = new Array(barCount).fill(0)
        allTimes.forEach(t => {
          let idx = Math.floor((t - minTime) / step)
          if (idx >= barCount) idx = barCount - 1
          if (idx < 0) idx = 0
          buckets[idx]++
        })

        let userBarIndex = Math.floor((userTime - minTime) / step)
        if (userBarIndex >= barCount) userBarIndex = barCount - 1
        if (userBarIndex < 0) userBarIndex = 0

        let maxHeight = Math.max(...buckets)
        if (maxHeight === 0) maxHeight = 1

        this.runtimeBars = buckets.map((count, i) => ({
          height: (count / maxHeight) * 75,
          rawHeight: count,
          isUserSubmission: i === userBarIndex
        }))

        const slowerCount = allTimes.filter(t => t > userTime).length
        const totalCount = allTimes.length
        this.runtimePercentage = totalCount > 0 ? Math.floor((slowerCount / totalCount) * 100) : 100
      },
      generateMemoryDistribution (userMemoryMB, allMemories) {
        if (!allMemories || allMemories.length === 0) return

        const allMemoriesMB = allMemories.map(m => {
          let val
          if (typeof m === 'string') {
            if (m.indexOf('MB') > -1) val = parseFloat(m) * 1024 * 1024
            else if (m.indexOf('KB') > -1) val = parseFloat(m) * 1024
            else val = parseInt(m)
          } else {
            val = m
          }
          return val / (1024 * 1024)
        })

        let minMemory = Math.min(...allMemoriesMB)
        let maxMemory = Math.max(...allMemoriesMB)

        if (maxMemory === minMemory) {
          minMemory = Math.max(0, minMemory - 5)
          maxMemory = maxMemory + 5
        }

        minMemory = Math.min(minMemory, userMemoryMB)
        maxMemory = Math.max(maxMemory, userMemoryMB)

        minMemory = Math.max(0, Math.floor(minMemory * 0.9))
        maxMemory = Math.ceil(maxMemory * 1.1)

        if (maxMemory - minMemory < 1) {
          const center = (minMemory + maxMemory) / 2
          minMemory = Math.max(0, center - 1)
          maxMemory = center + 1
        }

        const barCount = 30
        const step = (maxMemory - minMemory) / barCount

        this.memoryLabels = []
        for (let i = 0; i < barCount; i += 5) {
          const mem = (minMemory + step * i).toFixed(1)
          this.memoryLabels.push(mem + 'MB')
        }

        const buckets = new Array(barCount).fill(0)
        allMemoriesMB.forEach(m => {
          let idx = Math.floor((m - minMemory) / step)
          if (idx >= barCount) idx = barCount - 1
          if (idx < 0) idx = 0
          buckets[idx]++
        })

        let userBarIndex = Math.floor((userMemoryMB - minMemory) / step)
        if (userBarIndex >= barCount) userBarIndex = barCount - 1
        if (userBarIndex < 0) userBarIndex = 0

        let maxHeight = Math.max(...buckets)
        if (maxHeight === 0) maxHeight = 1

        this.memoryBars = buckets.map((count, i) => ({
          height: (count / maxHeight) * 75,
          rawHeight: count,
          isUserSubmission: i === userBarIndex
        }))

        const largerCount = allMemoriesMB.filter(m => m > userMemoryMB).length
        const totalCount = allMemoriesMB.length
        this.memoryPercentage = totalCount > 0 ? Math.floor((largerCount / totalCount) * 100) : 100
      }
    },
    computed: {
      status () {
        return {
          type: JUDGE_STATUS[this.submission.result].type,
          statusName: JUDGE_STATUS[this.submission.result].name,
          color: JUDGE_STATUS[this.submission.result].color
        }
      },
      isCE () {
        return this.submission.result === -2
      },
      isAdminRole () {
        return this.$store.getters.isAdminRole
      },
      JUDGE_STATUS () {
        return JUDGE_STATUS
      }
    },
    beforeUnmount () {
    }
  }
</script>

<style scoped lang="less">
  .submission-details-container {
    display: flex;
    gap: 20px;
    padding: 20px;
    background: #f5f5f5;
    align-items: flex-start;
  }

  .left-panel {
    flex: 0 0 45%;
    background: white;
    border-radius: 8px;
    padding: 24px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    display: flex;
    flex-direction: column;
  }

  .right-panel {
    flex: 1;
    min-width: 0;
  }

  .performance-section {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .problem-info-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    background: #fff;
    border-radius: 8px;
    border: 1px solid #e8e8e8;
    flex-wrap: wrap;

    .problem-link {
      display: flex;
      align-items: center;
      gap: 4px;
      color: #1890ff;
      font-weight: 600;
      text-decoration: none;
      font-size: 14px;

      &:hover {
        text-decoration: underline;
      }

      .problem-id {
        font-family: 'SFMono-Regular', Consolas, monospace;
      }

      .problem-title {
        color: #333;
        font-weight: 500;
        margin-left: 4px;
      }
    }

    .stats-count {
      font-size: 12px;
      color: #8c8c8c;
      margin-left: auto;
    }
  }

  .distribution-chart {
    background: #fafafa;
    border-radius: 8px;
    padding: 15px 20px;
    border: 1px solid #e8e8e8;
    display: flex;
    flex-direction: column;
  }

  .chart-container {
      min-height: 150px;
      height: 200px;
      display: flex;
      align-items: flex-end;
      margin-bottom: 8px;
      position: relative;
  }

  .stats-value {
    font-size: 24px;
    font-weight: 700;
    color: #262626;
    margin-right: 12px;
  }

  .stats-beat {
    font-size: 14px;
    color: #8c8c8c;
    font-weight: 500;
  }

  .y-axis {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    height: 100%;
    padding-right: 8px;
    font-size: 12px;
    color: #8c8c8c;
    padding-bottom: 4px;

    .y-label {
      line-height: 1;
    }
  }

  .bars-container {
    flex: 1;
    display: flex;
    align-items: flex-end;
    height: 100%;
    gap: 2px;
    border-bottom: 2px solid #e8e8e8;
    padding-bottom: 4px;
  }

  .bar-wrapper {
    flex: 1;
    height: 100%;
    display: flex;
    align-items: flex-end;
    position: relative;
  }

  .bar {
    width: 100%;
    background: #d9d9d9;
    border-radius: 2px 2px 0 0;
    transition: all 0.3s ease;
    position: relative;
    min-height: 2px;

    &:hover {
      opacity: 0.8;
    }

    &.bar-active {
      background: #1890ff;
      box-shadow: 0 -2px 8px rgba(24, 144, 255, 0.3);
    }
  }

  .user-marker {
    position: absolute;
    top: -22px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
  }

  .x-axis-labels {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    color: #8c8c8c;
    margin-top: 8px;
    padding-left: 40px;
  }

  .chart-footer {
    margin-top: 16px;
    font-size: 13px;
    color: #595959;
    text-align: center;
    padding: 8px;
    background: #f0f0f0;
    border-radius: 4px;
  }

  #status {
    .title {
      font-size: 20px;
    }
    .content {
      margin-top: 10px;
      font-size: 14px;
      span {
        margin-right: 10px;
      }
      pre {
        white-space: pre-wrap;
        word-wrap: break-word;
        word-break: break-all;
      }
    }
  }

  .admin-info {
    margin: 5px 0;
    &-content {
      font-size: 16px;
      padding: 10px;
    }
  }

  pre {
    border: none;
    background: none;
  }

  /* 响应式设计 */
  @media (max-width: 1200px) {
    .submission-details-container {
      flex-direction: column;
    }

    .left-panel {
      flex: none;
      width: 100%;
    }
  }
</style>

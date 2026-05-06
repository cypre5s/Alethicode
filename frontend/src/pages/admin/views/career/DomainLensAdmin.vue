<template>
  <div class="view">
    <Panel title="Coding Lens 管理">
      <template #header>
        <div class="lens-toolbar">
          <el-input
            v-model="major"
            class="major-input"
            size="small"
            clearable
            placeholder="按专业代码过滤，如 biology"
            aria-label="按专业代码过滤"
            @keyup.enter="fetchVariants">
          </el-input>
          <el-select
            v-model="limit"
            class="limit-select"
            size="small"
            aria-label="列表条数"
            @change="fetchVariants">
            <el-option :value="20" label="最近 20 条"></el-option>
            <el-option :value="50" label="最近 50 条"></el-option>
            <el-option :value="100" label="最近 100 条"></el-option>
          </el-select>
          <el-button size="small" @click="resetFilters">重置</el-button>
          <el-button
            size="small"
            type="primary"
            icon="el-icon-refresh"
            :loading="loading"
            @click="fetchVariants">
            刷新
          </el-button>
          <el-button
            size="small"
            type="warning"
            :loading="evalLoading"
            @click="runCareerEvaluation">
            运行闭环评测
          </el-button>
        </div>
      </template>

      <div class="summary-row" aria-live="polite">
        <el-tag v-if="variants.length" type="success" size="small" effect="plain">
          已加载 {{ variants.length }} 个专业化变体
        </el-tag>
        <el-tag v-else type="info" size="small" effect="plain">
          暂无变体
        </el-tag>
        <span class="summary-hint">
          锁定后，该题任意专业请求都会返回锁定版本，用于考试模式保持题面一致。
        </span>
      </div>

      <div v-if="evalReport" class="metric-grid" aria-live="polite">
        <div v-for="metric in metricList" :key="metric.key" class="metric-card">
          <span class="metric-label">{{ metric.label }}</span>
          <strong class="metric-value">{{ formatScore(metric.value) }}</strong>
        </div>
      </div>

      <el-table
        v-loading="loading"
        element-loading-text="加载中"
        :data="variants"
        :header-cell-style="{ textAlign: 'center' }"
        style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" align="center">
          <template #default="scope">
            <span class="mono-text">#{{ scope.row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="problemId" label="题目" width="110" align="center">
          <template #default="scope">
            <span class="mono-text">{{ scope.row.problemId }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="majorCode" label="专业" width="140" align="center">
          <template #default="scope">
            <el-tag size="small" effect="plain">{{ scope.row.majorCode }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="title" label="专业化标题" min-width="220">
          <template #default="scope">
            <span class="title-text">{{ scope.row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="semanticDriftScore" label="Drift" width="110" align="center">
          <template #default="scope">
            <el-tag size="small" :type="driftTag(scope.row.semanticDriftScore)">
              {{ formatDrift(scope.row.semanticDriftScore) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="reflectionPassed" label="Critic" width="110" align="center">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.reflectionPassed ? 'success' : 'danger'">
              {{ scope.row.reflectionPassed ? '通过' : '拒绝' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="lockedForExam" label="考试锁定" width="120" align="center">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.lockedForExam ? 'warning' : 'info'">
              {{ scope.row.lockedForExam ? '已锁定' : '未锁定' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="generatedAt" label="生成时间" width="180">
          <template #default="scope">
            {{ localtime(scope.row.generatedAt) }}
          </template>
        </el-table-column>

        <el-table-column fixed="right" label="操作" width="140" align="center">
          <template #default="scope">
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="scope.row.lockedForExam"
              :loading="scope.row._locking"
              :aria-label="`锁定变体 ${scope.row.id} 用于考试模式`"
              @click="lockVariant(scope.row)">
              锁定
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </Panel>
  </div>
</template>

<script>
  import api from '../../api.js'
  import { utcToLocal } from '@/utils/time'

  export default {
    name: 'DomainLensAdmin',
    data () {
      return {
        loading: false,
        evalLoading: false,
        evalReport: null,
        variants: [],
        major: '',
        limit: 50
      }
    },
    computed: {
      metricList () {
        if (!this.evalReport) {
          return []
        }
        return [
          { key: 'grounding_accuracy', label: 'Why grounding', value: this.evalReport.grounding_accuracy },
          { key: 'semantic_drift_rate', label: 'Lens drift', value: this.evalReport.semantic_drift_rate },
          { key: 'solvability_rate', label: 'Studio 可解性', value: this.evalReport.solvability_rate },
          { key: 'unlock_consistency', label: 'Path 一致性', value: this.evalReport.unlock_consistency }
        ]
      }
    },
    mounted () {
      this.fetchVariants()
    },
    methods: {
      localtime: utcToLocal,
      fetchVariants () {
        this.loading = true
        api.getCodingLensVariants(this.major && this.major.trim(), this.limit)
          .then(res => {
            this.variants = ((res.data && res.data.data) || []).map(item => ({
              ...item,
              _locking: false
            }))
          })
          .catch(() => {
            this.variants = []
            this.$message.error('Coding Lens 变体加载失败')
          })
          .finally(() => {
            this.loading = false
          })
      },
      runCareerEvaluation () {
        this.evalLoading = true
        api.runCareerEvaluation(100)
          .then(res => {
            this.evalReport = (res.data && res.data.data) || null
            this.$message.success('Career 闭环评测已完成')
          })
          .catch(() => {
            this.$message.error('Career 闭环评测失败')
          })
          .finally(() => {
            this.evalLoading = false
          })
      },
      resetFilters () {
        this.major = ''
        this.limit = 50
        this.fetchVariants()
      },
      lockVariant (variant) {
        if (!variant || variant.lockedForExam) {
          return
        }
        variant._locking = true
        api.lockCodingLensVariant(variant.id)
          .then(() => {
            variant.lockedForExam = true
            this.$message.success('已锁定该专业化题面')
          })
          .catch(() => {
            this.$message.error('锁定失败，请稍后重试')
          })
          .finally(() => {
            variant._locking = false
          })
      },
      formatDrift (score) {
        if (score === null || score === undefined || score === '') {
          return '—'
        }
        return Number(score).toFixed(2)
      },
      driftTag (score) {
        const value = Number(score || 0)
        if (value <= 0.05) {
          return 'success'
        }
        if (value <= 0.15) {
          return 'warning'
        }
        return 'danger'
      },
      formatScore (score) {
        if (score === null || score === undefined || score === '') {
          return '—'
        }
        return `${Math.round(Number(score) * 100)}%`
      }
    }
  }
</script>

<style scoped lang="less">
  .lens-toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
  }

  .major-input {
    width: 260px;
  }

  .limit-select {
    width: 140px;
  }

  .summary-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    margin-bottom: 14px;
    color: #475569;
    font-size: 14px;
    line-height: 1.6;
  }

  .summary-hint {
    color: #64748b;
  }

  .metric-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 12px;
    margin-bottom: 16px;
  }

  .metric-card {
    min-height: 78px;
    border-radius: 14px;
    border: 1px solid rgba(30, 64, 175, 0.14);
    background: #f8fafc;
    padding: 14px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 6px;
  }

  .metric-label {
    color: #64748b;
    font-size: 13px;
  }

  .metric-value {
    color: #1e40af;
    font-size: 24px;
    line-height: 1.2;
  }

  .mono-text {
    font-family: "Fira Code", "SFMono-Regular", Consolas, monospace;
    color: #1e40af;
  }

  .title-text {
    color: #0f172a;
    font-weight: 600;
  }

  :deep(.el-button) {
    min-height: 36px;
  }

  @media (max-width: 768px) {
    .major-input,
    .limit-select {
      width: 100%;
    }
  }
</style>

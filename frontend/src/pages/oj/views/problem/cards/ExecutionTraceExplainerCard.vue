<template>
  <BaseAgentCard v-if="data" accent="primary" :icon="sortIcon" title="程序运行可视化">
    <template #body>
      <div v-if="isFailed" class="ete-failure">
        <el-icon :size="16"><Warning /></el-icon>
        <span>{{ data.failure_reason || '当前无法生成运行轨迹' }}</span>
      </div>

      <template v-else>
        <div class="ete-summary">
          <div v-if="data.input_sample" class="ete-summary-block">
            <div class="ete-label">输入样例</div>
            <pre class="ete-pre">{{ data.input_sample }}</pre>
          </div>
          <div class="ete-summary-row">
            <span>关键步骤 {{ steps.length }} 步</span>
            <span v-if="hasDivergence">从第 {{ divergenceStep + 1 }} 步开始偏离预期</span>
          </div>
        </div>

        <div class="ete-steps">
          <div
            v-for="step in steps"
            :key="'step-' + step.step_index"
            :class="['ete-step', { 'is-divergence': step.step_index === divergenceStep }]"
          >
            <div class="ete-step-top">
              <span class="ete-step-index">步骤 {{ step.step_index + 1 }}</span>
              <span v-if="step.line_number" class="ete-step-line">第 {{ step.line_number }} 行</span>
            </div>
            <pre v-if="step.code" class="ete-code">{{ step.code }}</pre>
            <div v-if="hasVariables(step.variables)" class="ete-vars">
              <div class="ete-label">变量快照</div>
              <div class="ete-var-list">
                <span v-for="(value, key) in step.variables" :key="String(key)" class="ete-var-chip">
                  {{ key }} = {{ formatValue(value) }}
                </span>
              </div>
            </div>
            <div v-if="step.output" class="ete-output">
              <div class="ete-label">当前输出</div>
              <pre class="ete-pre">{{ step.output }}</pre>
            </div>
            <div v-if="step.branch_taken" class="ete-branch">分支判断：{{ step.branch_taken }}</div>
            <div v-if="step.teaching_explanation" class="ete-explain">{{ step.teaching_explanation }}</div>
          </div>
        </div>
      </template>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw } from 'vue'
import { Sort, Warning } from '@element-plus/icons-vue'
import BaseAgentCard from './BaseAgentCard.vue'

export default {
  name: 'ExecutionTraceExplainerCard',
  components: { BaseAgentCard, Warning },
  props: {
    data: {
      type: Object,
      default: () => ({
        status: 'failed', input_sample: '', steps: [],
        divergence_step: -1, failure_reason: ''
      })
    }
  },
  data () {
    return { sortIcon: markRaw(Sort) }
  },
  computed: {
    isFailed () { return !this.data || this.data.status !== 'ready' },
    steps () { return Array.isArray(this.data.steps) ? this.data.steps : [] },
    divergenceStep () {
      return typeof this.data.divergence_step === 'number' ? this.data.divergence_step : -1
    },
    hasDivergence () { return this.divergenceStep >= 0 }
  },
  methods: {
    hasVariables (variables) {
      return variables && typeof variables === 'object' && Object.keys(variables).length > 0
    },
    formatValue (value) {
      return typeof value === 'string' ? value : JSON.stringify(value)
    }
  }
}
</script>

<style lang="less" scoped>
.ete-failure {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 10px 12px; border-radius: 10px;
  background: rgba(254, 242, 242, 0.92); border: 0.5px solid rgba(239, 68, 68, 0.18);
  color: #b91c1c; line-height: 1.7; font-size: var(--card-font-body);
}

.ete-summary {
  border-radius: 10px; padding: 12px;
  background: var(--card-accent-bg);
  border: 0.5px solid var(--card-accent-border);
}
.ete-summary-row {
  display: flex; gap: 10px; flex-wrap: wrap;
  font-size: var(--card-font-body); color: var(--card-accent); font-weight: 600;
}
.ete-summary-block + .ete-summary-row { margin-top: 10px; }

.ete-label {
  font-size: var(--card-font-label); font-weight: 600;
  color: var(--card-accent); margin-bottom: 6px;
}

.ete-pre, .ete-code {
  margin: 0; white-space: pre-wrap; word-break: break-word;
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
}
.ete-pre {
  border-radius: 8px; padding: 8px 10px;
  background: rgba(255, 255, 255, 0.75);
  font-size: var(--card-font-label); line-height: 1.6; color: #0f172a;
}

.ete-steps { display: flex; flex-direction: column; gap: 10px; }
.ete-step {
  border-radius: 10px; padding: 12px;
  border: 0.5px solid rgba(148, 163, 184, 0.25);
  background: rgba(255, 255, 255, 0.92);
}
.ete-step.is-divergence {
  border-color: rgba(249, 115, 22, 0.3);
  box-shadow: inset 0 0 0 1px rgba(249, 115, 22, 0.12);
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.9), rgba(255, 255, 255, 0.96));
}
.ete-step-top {
  display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px;
}
.ete-step-index { font-size: var(--card-font-body); font-weight: 700; color: #0f172a; }
.ete-step-line { font-size: var(--card-font-label); color: #64748b; }

.ete-code {
  border-radius: 8px; padding: 8px 10px;
  background: #0f172a; color: #e2e8f0;
  font-size: var(--card-font-label); line-height: 1.6;
}

.ete-vars, .ete-output { margin-top: 10px; }
.ete-var-list { display: flex; gap: 8px; flex-wrap: wrap; }
.ete-var-chip {
  border-radius: 999px; padding: 4px 10px;
  background: var(--card-accent-bg-strong);
  color: var(--card-accent); font-size: var(--card-font-label);
}

.ete-branch {
  margin-top: 10px; font-size: var(--card-font-label); color: #92400e;
}
.ete-explain {
  margin-top: 10px;
  border-left: 3px solid var(--card-accent-border);
  padding-left: 10px;
  font-size: var(--card-font-body); line-height: 1.7; color: #334155;
}
</style>

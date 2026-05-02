<template>
  <div class="plan-steps-card">
    <div class="plan-steps-track">
      <div
        v-for="(step, index) in steps"
        :key="step.step_id || index"
        :class="['plan-step-chip', `is-${normalizedStatus(step.status)}`]"
      >
        <span class="plan-step-index">{{ index + 1 }}</span>
        <span class="plan-step-title">{{ step.title }}</span>
      </div>
    </div>
    <div v-if="currentStep && !completed && !paused" class="plan-step-actions">
      <button
        v-if="currentStep.evidence_type === 'code_change'"
        type="button"
        class="plan-step-btn plan-step-btn-primary"
        @click="$emit('confirm-step', { step: currentStep })"
      >
        我已完成这一步
      </button>
      <button type="button" class="plan-step-btn" @click="$emit('skip-step', { step: currentStep })">
        跳过这一步
      </button>
      <span v-if="currentStep.evidence_type !== 'code_change'" class="plan-step-tip">
        在下方输入你的回答后发送，系统会据此评估当前步骤。
      </span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PlanStepsCard',
  emits: ['confirm-step', 'skip-step'],
  props: {
    steps: { type: Array, default: () => [] },
    paused: { type: Boolean, default: false },
    completed: { type: Boolean, default: false }
  },
  computed: {
    currentStep () {
      if (!this.steps.length) return null
      return this.steps.find(step => ['active', 'current', 'in_progress'].includes(this.normalizedStatus(step.status))) ||
        this.steps.find(step => this.normalizedStatus(step.status) === 'pending') ||
        this.steps[0]
    }
  },
  methods: {
    normalizedStatus (status) {
      return String(status || 'pending').toLowerCase()
    }
  }
}
</script>

<style lang="less" scoped>
.plan-steps-track {
  display: grid;
  gap: 8px;
}

.plan-step-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.72);
  color: #475569;
}

.plan-step-chip.is-active,
.plan-step-chip.is-current,
.plan-step-chip.is-in_progress {
  border-color: rgba(245, 158, 11, 0.45);
  background: rgba(255, 255, 255, 0.92);
  color: #92400e;
}

.plan-step-chip.is-completed {
  border-color: rgba(16, 185, 129, 0.3);
  color: #047857;
}

.plan-step-chip.is-skipped {
  opacity: 0.72;
}

.plan-step-index {
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
}

.plan-step-title {
  font-size: 12px;
  font-weight: 600;
}

.plan-step-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.plan-step-btn {
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(255, 255, 255, 0.78);
  color: #334155;
  border-radius: 10px;
  padding: 7px 10px;
  cursor: pointer;
}

.plan-step-btn-primary {
  border-color: rgba(245, 158, 11, 0.4);
  color: #92400e;
}

.plan-step-tip {
  font-size: 12px;
  color: #64748b;
}
</style>

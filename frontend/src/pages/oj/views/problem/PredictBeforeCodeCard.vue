<template>
  <div v-if="!dismissed" :class="['pbc-card', { 'pbc-card--collapsed': collapsed }]" role="complementary" aria-label="代码预测">
    <template v-if="!collapsed && !submitted">
      <h3 class="pbc-title">写代码前，先猜猜结果？</h3>
      <textarea
        v-model="predictedOutput"
        class="pbc-textarea pbc-textarea--mono"
        placeholder="我觉得会输出..."
        rows="3"
        maxlength="2000"
      ></textarea>
      <textarea
        v-model="predictedReason"
        class="pbc-textarea"
        placeholder="我的理由是...（选填）"
        rows="2"
        maxlength="1000"
      ></textarea>
      <div class="pbc-actions">
        <button type="button" class="pbc-btn pbc-btn--primary" :disabled="!predictedOutput.trim()" @click="submitPrediction">提交预测</button>
        <button type="button" class="pbc-link" @click="skip">
          跳过 <span class="pbc-hint">研究表明先预测的人学得更快</span>
        </button>
      </div>
    </template>

    <template v-else-if="submitted && !collapsed">
      <div class="pbc-submitted">
        <span class="pbc-submitted__icon">⚙</span>
        <span>预测已记录，做完题对照</span>
        <button type="button" class="pbc-collapse-btn" @click="collapsed = true">收起</button>
      </div>
    </template>

    <template v-else>
      <button type="button" class="pbc-expand-btn" @click="collapsed = false">
        📝 你的预测准确率：{{ accuracyDisplay }}
      </button>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'PredictBeforeCodeCard',
  props: {
    problemId: { type: Number, required: true }
  },
  data () {
    return {
      predictedOutput: '',
      predictedReason: '',
      submitted: false,
      collapsed: false,
      dismissed: false,
      eventId: null,
      accuracyDisplay: '--'
    }
  },
  methods: {
    async submitPrediction () {
      try {
        const res = await api.submitMetacogPrediction({
          problem_id: this.problemId,
          predicted_output: this.predictedOutput.trim(),
          predicted_reason: this.predictedReason.trim() || null
        })
        this.eventId = res.data.data.event_id
        this.submitted = true
      } catch {
        // silent
      }
    },
    skip () {
      this.dismissed = true
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.pbc-card {
  width: 260px;
  padding: @l99-sp-4;
  background: #fff;
  border-radius: @l99-radius-md;
  box-shadow: @l99-shadow-2;
  border: 1px solid @l99-neutral-200;

  &--collapsed {
    padding: @l99-sp-2 @l99-sp-3;
  }
}

.pbc-title {
  font-size: @l99-fs-sm;
  font-weight: 600;
  color: @l99-neutral-900;
  margin: 0 0 @l99-sp-3;
}

.pbc-textarea {
  width: 100%;
  padding: @l99-sp-2;
  border: 1px solid @l99-neutral-200;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-sm;
  margin-bottom: @l99-sp-2;
  resize: vertical;
  font-family: @l99-font-sans;
  &--mono { font-family: @l99-font-mono; }
  &:focus { outline: none; border-color: @l99-primary; }
}

.pbc-actions {
  display: flex;
  flex-direction: column;
  gap: @l99-sp-2;
}

.pbc-btn {
  padding: @l99-sp-2 @l99-sp-4;
  border: none;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-sm;
  font-weight: 500;
  cursor: pointer;
  &--primary { background: @l99-primary; color: #fff; &:hover { opacity: 0.9; } &:disabled { opacity: 0.4; cursor: not-allowed; } }
}

.pbc-link {
  background: none;
  border: none;
  color: @l99-neutral-500;
  font-size: @l99-fs-xs;
  cursor: pointer;
  text-align: left;
  padding: 0;
}

.pbc-hint {
  display: block;
  color: @l99-neutral-500;
  font-size: 10px;
  font-style: italic;
  margin-top: 2px;
}

.pbc-submitted {
  display: flex;
  align-items: center;
  gap: @l99-sp-2;
  font-size: @l99-fs-sm;
  color: @l99-neutral-700;
  &__icon { font-size: @l99-fs-lg; }
}

.pbc-collapse-btn, .pbc-expand-btn {
  background: none;
  border: none;
  font-size: @l99-fs-xs;
  color: @l99-primary;
  cursor: pointer;
  padding: 0;
  &:hover { text-decoration: underline; }
}

.pbc-expand-btn {
  font-size: @l99-fs-sm;
  color: @l99-neutral-700;
}
</style>

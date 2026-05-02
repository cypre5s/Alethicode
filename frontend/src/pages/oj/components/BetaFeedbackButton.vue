<template>
  <span
    v-if="isAuthenticated"
    class="beta-feedback-trigger"
    role="button"
    :title="tooltipText"
    :aria-label="tooltipText"
    tabindex="0"
    @click="openDialog"
    @keydown.enter.prevent="openDialog"
    @keydown.space.prevent="openDialog"
  >
    <ElIcon :size="18"><ChatDotRound /></ElIcon>
    <span class="beta-feedback-trigger-label">反馈</span>
  </span>
  <BetaFeedbackDialog
    v-if="isAuthenticated"
    v-model="dialogVisible"
    @submitted="onSubmitted"
  />
</template>

<script>
import { mapGetters } from 'vuex'
import { ChatDotRound } from '@element-plus/icons-vue'
import BetaFeedbackDialog from './BetaFeedbackDialog.vue'
import { recordEvent } from '@/utils/betaTelemetry'

export default {
  name: 'BetaFeedbackButton',
  components: { BetaFeedbackDialog, ChatDotRound },
  data () {
    return {
      dialogVisible: false,
      tooltipText: '遇到问题或想提建议？点击反馈给老师'
    }
  },
  computed: {
    ...mapGetters(['isAuthenticated'])
  },
  mounted () {
    if (this.isAuthenticated) {
      try {
        recordEvent('feature_click', { name: 'feedback_button_view' })
      } catch (e) { /* silent */ }
    }
  },
  methods: {
    openDialog () {
      this.dialogVisible = true
      try {
        recordEvent('feedback_opened', {})
      } catch (e) { /* silent */ }
    },
    onSubmitted (payload) {
      try {
        recordEvent('feedback_submitted', payload || {})
      } catch (e) { /* silent */ }
    }
  }
}
</script>

<style scoped lang="less">
.beta-feedback-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  margin-left: 4px;
  border-radius: var(--radius-pill);
  background: rgba(37, 99, 235, 0.08);
  color: var(--primary-color);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  user-select: none;
  transition: background var(--motion-fast), color var(--motion-fast), transform var(--motion-fast);

  :deep(.el-icon) {
    color: var(--primary-color);
  }

  &:hover {
    background: var(--primary-color);
    color: #fff;
    transform: translateY(-1px);

    :deep(.el-icon) {
      color: #fff;
    }
  }

  &:focus-visible {
    outline: 2px solid var(--primary-color);
    outline-offset: 2px;
  }
}

.beta-feedback-trigger-label {
  font-weight: 500;
  letter-spacing: 0.2px;
}
</style>

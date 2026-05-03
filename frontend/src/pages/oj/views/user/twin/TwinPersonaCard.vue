<template>
  <div class="tp-card" role="region" aria-label="孪生人格摘要">
    <div v-if="loading" class="tp-skeleton">
      <el-skeleton :rows="3" animated />
    </div>

    <template v-else-if="disabled">
      <div class="tp-disabled">
        <p>个性化已关闭，30 天内可恢复</p>
        <button type="button" class="tp-btn tp-btn--primary" @click="enablePersonalization">恢复个性化</button>
      </div>
    </template>

    <template v-else-if="!summaryText">
      <div class="tp-empty">
        <div class="tp-empty__icon" aria-hidden="true">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <circle cx="24" cy="24" r="20" stroke="#0F4C81" stroke-width="2" fill="#E5EEF7"/>
            <circle cx="24" cy="18" r="6" stroke="#0F4C81" stroke-width="2" fill="none"/>
            <path d="M12 38c0-6.627 5.373-12 12-12s12 5.373 12 12" stroke="#0F4C81" stroke-width="2" fill="none"/>
          </svg>
        </div>
        <p class="tp-empty__text">你的孪生正在慢慢认识你，多做几道题它就能描述你的学习风格了</p>
      </div>
    </template>

    <template v-else>
      <div class="tp-header">
        <div class="tp-avatar" aria-hidden="true">
          <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
            <circle cx="20" cy="20" r="20" fill="#E5EEF7"/>
            <circle cx="20" cy="15" r="5" fill="#0F4C81"/>
            <path d="M10 33c0-5.523 4.477-10 10-10s10 4.477 10 10" fill="#0F4C81"/>
          </svg>
        </div>
        <span class="tp-label">你的孪生说：</span>
        <span v-if="isOverridden" class="tp-badge">已编辑</span>
      </div>

      <div v-if="!editing" class="tp-body">
        <p class="tp-summary">{{ summaryText }}</p>
      </div>

      <div v-else class="tp-edit">
        <textarea
          v-model="editText"
          class="tp-textarea"
          maxlength="500"
          rows="5"
          placeholder="输入你的自述..."
        ></textarea>
        <div class="tp-edit__footer">
          <span class="tp-char-count">{{ editText.length }} / 500</span>
          <div class="tp-edit__actions">
            <button type="button" class="tp-btn tp-btn--ghost" @click="cancelEdit">取消</button>
            <button type="button" class="tp-btn tp-btn--primary" :disabled="!editText.trim()" @click="saveEdit">保存</button>
          </div>
        </div>
      </div>

      <div v-if="!editing" class="tp-actions">
        <button
          type="button"
          class="tp-btn tp-btn--success"
          :disabled="feedbackGiven"
          aria-label="摘要准确"
          @click="submitFeedback(true)"
        >精确</button>
        <button
          type="button"
          class="tp-btn tp-btn--danger-outline"
          aria-label="摘要不准确"
          @click="showInaccurateDialog"
        >不精确</button>
        <button
          type="button"
          class="tp-btn tp-btn--ghost"
          aria-label="编辑摘要"
          @click="startEdit"
        >编辑</button>
        <button
          type="button"
          class="tp-link"
          @click="disablePersonalization"
        >暂时关闭个性化</button>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'
import { notify } from '@/utils/notifications'

export default {
  name: 'TwinPersonaCard',
  data () {
    return {
      loading: false,
      summaryText: '',
      summaryVersion: 0,
      learningStyleKey: '',
      isOverridden: false,
      disabled: false,
      editing: false,
      editText: '',
      feedbackGiven: false
    }
  },
  mounted () {
    this.loadPersona()
  },
  methods: {
    async loadPersona () {
      this.loading = true
      try {
        const res = await api.getTwinPersona()
        const d = res.data.data
        this.summaryText = d.summary_text || ''
        this.summaryVersion = d.summary_version || 0
        this.learningStyleKey = d.learning_style_key || ''
        this.isOverridden = !!d.is_user_overridden
        this.disabled = !!d.user_disabled
      } catch {
        this.summaryText = ''
      } finally {
        this.loading = false
      }
    },
    startEdit () {
      this.editText = this.summaryText
      this.editing = true
    },
    cancelEdit () {
      this.editing = false
      this.editText = ''
    },
    async saveEdit () {
      try {
        await api.overrideTwinPersona({ summary_text: this.editText.trim() })
        this.summaryText = this.editText.trim()
        this.isOverridden = true
        this.editing = false
        notify.success('摘要已更新')
      } catch {
        notify.error('保存失败，请重试')
      }
    },
    async submitFeedback (isAccurate) {
      try {
        await api.feedbackTwinPersona({ is_accurate: isAccurate })
        if (isAccurate) {
          this.feedbackGiven = true
          notify.success('感谢反馈')
        }
      } catch {
        notify.error('提交失败')
      }
    },
    showInaccurateDialog () {
      const reason = window.prompt('请简要说明不准确的地方（可留空）：')
      if (reason === null) return
      this.submitInaccurateFeedback(reason)
    },
    async submitInaccurateFeedback (reason) {
      try {
        await api.feedbackTwinPersona({ is_accurate: false, reason })
        notify.success('感谢反馈，孪生会改进')
      } catch {
        notify.error('提交失败')
      }
    },
    async disablePersonalization () {
      try {
        this.disabled = true
      } catch {
        // handled
      }
    },
    async enablePersonalization () {
      this.disabled = false
      this.loadPersona()
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.tp-card {
  max-width: 720px;
  background: linear-gradient(135deg, @l99-primary-soft 0%, #fff 100%);
  border-radius: @l99-radius-md;
  box-shadow: @l99-shadow-2;
  padding: @l99-sp-6;
}

.tp-skeleton { padding: @l99-sp-4; }

.tp-header {
  display: flex;
  align-items: center;
  gap: @l99-sp-3;
  margin-bottom: @l99-sp-4;
}

.tp-label {
  font-size: @l99-fs-sm;
  color: @l99-neutral-500;
  font-weight: 500;
}

.tp-badge {
  font-size: @l99-fs-xs;
  padding: 2px 8px;
  background: @l99-accent;
  color: #fff;
  border-radius: 10px;
}

.tp-body { margin-bottom: @l99-sp-4; }

.tp-summary {
  font-size: @l99-fs-lg;
  line-height: 1.7;
  color: @l99-neutral-900;
  font-family: @l99-font-sans;
  margin: 0;
}

.tp-edit {
  margin-bottom: @l99-sp-4;
  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: @l99-sp-2;
  }
  &__actions {
    display: flex;
    gap: @l99-sp-2;
  }
}

.tp-textarea {
  width: 100%;
  padding: @l99-sp-3;
  border: 1px solid @l99-neutral-200;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-md;
  font-family: @l99-font-sans;
  line-height: 1.6;
  resize: vertical;
  &:focus {
    outline: none;
    border-color: @l99-primary;
    box-shadow: 0 0 0 2px rgba(15,76,129,0.1);
  }
}

.tp-char-count {
  font-size: @l99-fs-xs;
  color: @l99-neutral-500;
}

.tp-actions {
  display: flex;
  align-items: center;
  gap: @l99-sp-3;
  flex-wrap: wrap;
}

.tp-btn {
  padding: @l99-sp-2 @l99-sp-4;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-sm;
  font-weight: 500;
  cursor: pointer;
  transition: all @l99-dur-fast @l99-ease;
  border: 1px solid transparent;

  &--primary {
    background: @l99-primary;
    color: #fff;
    &:hover { opacity: 0.9; }
  }
  &--success {
    background: @l99-success;
    color: #fff;
    &:hover { opacity: 0.9; }
    &:disabled { opacity: 0.4; cursor: not-allowed; }
  }
  &--danger-outline {
    background: #fff;
    color: @l99-danger;
    border-color: @l99-danger;
    &:hover { background: #FEF2F2; }
  }
  &--ghost {
    background: none;
    color: @l99-primary;
    border-color: @l99-primary;
    &:hover { background: @l99-primary-soft; }
  }
}

.tp-link {
  background: none;
  border: none;
  color: @l99-neutral-500;
  font-size: @l99-fs-xs;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
  margin-left: auto;
  &:hover { color: @l99-neutral-700; }
}

.tp-empty, .tp-disabled {
  text-align: center;
  padding: @l99-sp-6;
  &__text { font-size: @l99-fs-md; color: @l99-neutral-700; margin-top: @l99-sp-3; }
}
</style>

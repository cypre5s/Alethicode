<template>
  <div class="career-preferences-page">
    <h1 class="page-title">Career 模块设置</h1>
    <p class="page-desc">每个模块都可以单独关闭。关闭后，相关入口仍然可见，但不会调用 LLM 生成专业化内容（节省你的注意力 / 算力）。</p>

    <p v-if="loading" class="muted-line">加载中…</p>

    <form v-else class="prefs-form" @submit.prevent="handleSave">
      <fieldset class="prefs-group">
        <legend class="prefs-legend">学习入口</legend>

        <label class="prefs-row">
          <input
            type="checkbox"
            v-model="form.career_bridging_disabled"
            :disabled="saving"
            aria-describedby="career-bridging-help"
          />
          <span class="prefs-label">关闭「专业 Why 报告」</span>
          <span id="career-bridging-help" class="prefs-help">关闭后，注册专业 / 章节进入 / KC 毕业等里程碑不再触发 LLM 生成 Why 报告。</span>
        </label>

        <label class="prefs-row">
          <input
            type="checkbox"
            v-model="form.coding_lens_disabled"
            :disabled="saving"
            aria-describedby="coding-lens-help"
          />
          <span class="prefs-label">关闭「题面专业化重写」</span>
          <span id="coding-lens-help" class="prefs-help">关闭后，题目页不再展示「我专业版」入口，所有题目保持原版叙事。</span>
        </label>

        <label class="prefs-row">
          <input
            type="checkbox"
            v-model="form.career_studio_disabled"
            :disabled="saving"
            aria-describedby="career-studio-help"
          />
          <span class="prefs-label">关闭「微项目工作室」</span>
          <span id="career-studio-help" class="prefs-help">关闭后，无法生成新的微项目；已生成的项目仍可查看与提交。</span>
        </label>

        <label class="prefs-row">
          <input
            type="checkbox"
            v-model="form.career_path_disabled"
            :disabled="saving"
            aria-describedby="career-path-help"
          />
          <span class="prefs-label">关闭「学习路径地图」</span>
          <span id="career-path-help" class="prefs-help">关闭后，路径页不再展示节点，节省查询 mastery 的开销。</span>
        </label>
      </fieldset>

      <div class="prefs-actions">
        <button type="submit" class="primary-btn" :disabled="saving" :aria-label="saving ? '正在保存设置' : '保存设置'">
          {{ saving ? '保存中…' : '保存设置' }}
        </button>
        <span v-if="savedAt" class="prefs-saved-line">最近保存：{{ savedAt }}</span>
      </div>
    </form>
  </div>
</template>

<script>
import api from '@oj/api'
import { notify } from '@/utils/notifications'

export default {
  name: 'CareerPreferencesPanel',
  data () {
    return {
      form: {
        career_bridging_disabled: false,
        coding_lens_disabled: false,
        career_studio_disabled: false,
        career_path_disabled: false
      },
      loading: true,
      saving: false,
      savedAt: ''
    }
  },
  async created () {
    try {
      const res = await api.getCareerPreferences()
      const d = res.data.data
      if (d) {
        this.form.career_bridging_disabled = !!(d.career_bridging_disabled || d.careerBridgingDisabled)
        this.form.coding_lens_disabled = !!(d.coding_lens_disabled || d.codingLensDisabled)
        this.form.career_studio_disabled = !!(d.career_studio_disabled || d.careerStudioDisabled)
        this.form.career_path_disabled = !!(d.career_path_disabled || d.careerPathDisabled)
      }
    } catch (e) {
      // by-design silent: 偏好读取失败不应阻塞页面，默认全部启用
    } finally {
      this.loading = false
    }
  },
  methods: {
    async handleSave () {
      this.saving = true
      try {
        await api.updateCareerPreferences(this.form)
        const now = new Date()
        this.savedAt = `${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}`
        notify.success('Career 模块设置已保存')
      } catch (e) {
        notify.error('保存失败，请稍后再试')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.career-preferences-page {
  max-width: 680px;
  margin: 40px auto;
  padding: 0 20px;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}
.page-desc {
  color: #6b7280;
  line-height: 1.6;
  margin-bottom: 24px;
}
.muted-line {
  text-align: center;
  color: #9ca3af;
  padding: 24px 0;
}
.prefs-form {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding: 4px 24px 20px;
}
.prefs-group {
  border: none;
  padding: 16px 0;
  margin: 0;
}
.prefs-legend {
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
  padding: 0 4px;
  margin-bottom: 8px;
}
.prefs-row {
  display: grid;
  grid-template-columns: 24px 1fr;
  column-gap: 12px;
  align-items: start;
  padding: 14px 0;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}
.prefs-row:last-child {
  border-bottom: none;
}
.prefs-row input[type="checkbox"] {
  width: 18px;
  height: 18px;
  margin-top: 2px;
  accent-color: #6366f1;
}
.prefs-label {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  grid-column: 2;
}
.prefs-help {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
  grid-column: 2;
  line-height: 1.5;
}
.prefs-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0 0;
}
.primary-btn {
  padding: 10px 22px;
  border: none;
  border-radius: 8px;
  background: #6366f1;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  min-height: 44px;
}
.primary-btn:hover:not(:disabled) {
  background: #4f46e5;
}
.primary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.primary-btn:focus-visible {
  outline: 3px solid rgba(99, 102, 241, 0.4);
  outline-offset: 2px;
}
.prefs-saved-line {
  font-size: 12px;
  color: #16a34a;
}
</style>

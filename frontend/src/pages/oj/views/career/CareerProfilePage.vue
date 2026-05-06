<template>
  <div class="career-profile-page">
    <h1 class="page-title">我的专业档案</h1>
    <p class="page-desc">填写你的专业，Alethicode 会根据你的专业定制学习体验。</p>

    <div class="profile-form">
      <label class="form-label" for="major-select">我的专业</label>
      <ElSelect
        id="major-select"
        v-model="majorCode"
        filterable
        allow-create
        default-first-option
        placeholder="搜索或输入你的专业…"
        :disabled="submitting"
        class="form-select-el"
      >
        <ElOption
          v-for="m in majors"
          :key="m.code"
          :value="m.code"
          :label="m.name_zh + '（' + m.discipline + '）'"
        />
      </ElSelect>

      <label class="form-label" for="career-intent">我的学习目标（选填）</label>
      <textarea
        id="career-intent"
        class="form-textarea"
        v-model="careerIntent"
        placeholder="例如：我想用 Python 处理实验数据、做统计分析…"
        rows="3"
        maxlength="1024"
        :disabled="submitting"
      ></textarea>

      <button
        class="submit-btn"
        :disabled="!majorCode || submitting"
        @click="handleSubmit"
      >
        {{ submitting ? '提交中…' : (hasProfile ? '更新专业' : '确认专业') }}
      </button>
    </div>

    <div v-if="report" class="report-preview">
      <h2 class="report-title">{{ report.title }}</h2>
      <div class="report-content" v-html="renderMd(report.content_md)"></div>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'
import { notify } from '@/utils/notifications'

export default {
  name: 'CareerProfilePage',
  data () {
    return {
      majors: [],
      majorCode: '',
      careerIntent: '',
      hasProfile: false,
      submitting: false,
      report: null
    }
  },
  async created () {
    await this.loadMajors()
    await this.loadProfile()
  },
  methods: {
    async loadMajors () {
      try {
        const res = await api.getCareerMajors()
        this.majors = res.data.data || []
      } catch { /* silent */ }
    },
    async loadProfile () {
      try {
        const res = await api.getCareerProfile()
        const d = res.data.data
        if (d && d.major_code) {
          this.majorCode = d.major_code
          this.careerIntent = d.career_intent || ''
          this.hasProfile = true
        }
      } catch { /* silent */ }
    },
    async handleSubmit () {
      this.submitting = true
      try {
        const knownCodes = this.majors.map(m => m.code)
        const isCustom = this.majorCode && !knownCodes.includes(this.majorCode)
        const effectiveCode = isCustom ? 'other' : this.majorCode
        const effectiveIntent = isCustom
          ? ('专业：' + this.majorCode + (this.careerIntent ? '；' + this.careerIntent : ''))
          : this.careerIntent
        const res = await api.updateCareerProfile(effectiveCode, effectiveIntent)
        const d = res.data.data
        this.hasProfile = true
        if (d && d.report) {
          this.report = d.report
        }
        notify.success(d.newly_enrolled ? '专业档案已创建' : '专业档案已更新')
      } catch (e) {
        notify.error('提交失败，请稍后重试')
      } finally {
        this.submitting = false
      }
    },
    renderMd (md) {
      if (!md) return ''
      return sanitize(marked(md))
    }
  }
}
</script>

<style scoped>
.career-profile-page {
  max-width: 640px;
  margin: 40px auto;
  padding: 0 20px;
}
.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}
.page-desc {
  color: #6b7280;
  margin-bottom: 32px;
  line-height: 1.6;
}
.profile-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.form-label {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}
.form-select-el {
  width: 100%;
}
.form-textarea {
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  color: #1f2937;
  background: #fff;
  transition: border-color 0.2s;
}
.form-textarea:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}
.submit-btn {
  align-self: flex-start;
  padding: 10px 24px;
  border: none;
  border-radius: 8px;
  background: #6366f1;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}
.submit-btn:hover:not(:disabled) {
  background: #4f46e5;
}
.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.report-preview {
  margin-top: 32px;
  padding: 24px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.report-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}
.report-content {
  font-size: 14px;
  color: #374151;
  line-height: 1.7;
}
</style>

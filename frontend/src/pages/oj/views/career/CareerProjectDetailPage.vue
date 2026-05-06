<template>
  <div class="project-detail-page">
    <header class="page-header">
      <router-link :to="{ name: 'career-studio' }" class="back-link" aria-label="返回 Studio 列表">&larr; 返回 Studio 列表</router-link>
      <h1 v-if="project" class="page-title">{{ project.title }}</h1>
      <h1 v-else class="page-title">微项目详情</h1>
    </header>

    <p v-if="loading" class="muted-line">加载中…</p>
    <p v-else-if="!project" class="empty-state">微项目不存在或不属于你。</p>

    <template v-else>
      <section class="meta-card">
        <div class="meta-row">
          <span class="meta-label">状态</span>
          <span class="status-badge" :class="'badge-' + project.status">{{ statusLabel(project.status) }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">专业</span>
          <span class="meta-value">{{ project.major_code }}</span>
        </div>
        <div v-if="project.judge_problem_id" class="meta-row">
          <span class="meta-label">关联题目</span>
          <router-link
            :to="{ name: 'problem-details', params: { problemID: project.judge_problem_id } }"
            class="link-btn"
          >前往判题页提交（problem #{{ project.judge_problem_id }}）</router-link>
        </div>
        <div v-if="project.score != null" class="meta-row">
          <span class="meta-label">得分</span>
          <span class="meta-value">{{ project.score }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">创建</span>
          <span class="meta-value">{{ formatDate(project.created_at) }}</span>
        </div>
        <div v-if="project.completed_at" class="meta-row">
          <span class="meta-label">完成</span>
          <span class="meta-value">{{ formatDate(project.completed_at) }}</span>
        </div>
      </section>

      <section class="brief-card">
        <h2 class="section-title">题目说明</h2>
        <div class="brief-content" v-html="renderedBrief"></div>
      </section>

      <section class="portfolio-card">
        <h2 class="section-title">作品集卡片</h2>
        <p class="muted-line">导出 Markdown 卡片用于个人作品集 / 简历附件。</p>
        <pre class="portfolio-preview">{{ portfolioMarkdown }}</pre>
        <div class="portfolio-actions">
          <button
            class="primary-btn"
            :aria-label="copied ? '作品集 Markdown 已复制' : '复制作品集 Markdown'"
            @click="copyPortfolio"
          >{{ copied ? '已复制' : '复制 Markdown' }}</button>
          <button class="ghost-btn" @click="downloadPortfolio" aria-label="下载作品集 Markdown 文件">下载 .md 文件</button>
        </div>
      </section>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'
import { notify } from '@/utils/notifications'

export default {
  name: 'CareerProjectDetailPage',
  data () {
    return {
      project: null,
      loading: true,
      copied: false
    }
  },
  computed: {
    renderedBrief () {
      if (!this.project || !this.project.brief_md) return ''
      return sanitize(marked(this.project.brief_md))
    },
    portfolioMarkdown () {
      if (!this.project) return ''
      const lines = []
      lines.push(`# ${this.project.title}`)
      lines.push('')
      lines.push(`- 专业: ${this.project.major_code}`)
      lines.push(`- 状态: ${this.statusLabel(this.project.status)}`)
      if (this.project.score != null) lines.push(`- 得分: ${this.project.score}`)
      lines.push(`- 创建: ${this.formatDate(this.project.created_at)}`)
      if (this.project.completed_at) lines.push(`- 完成: ${this.formatDate(this.project.completed_at)}`)
      if (this.project.judge_problem_id) lines.push(`- 判题机题号: #${this.project.judge_problem_id}`)
      lines.push('')
      lines.push('## 题目说明')
      lines.push('')
      lines.push(this.project.brief_md || '_未填写_')
      lines.push('')
      lines.push('---')
      lines.push('_由 Alethicode Project Studio 自动生成（reference solution 已通过真判题自验证）_')
      return lines.join('\n')
    }
  },
  async created () {
    try {
      const id = Number(this.$route.params.projectId)
      if (!Number.isFinite(id) || id <= 0) {
        this.project = null
        return
      }
      const res = await api.getStudioProject(id)
      this.project = res.data.data || null
    } catch (e) {
      this.project = null
    } finally {
      this.loading = false
    }
  },
  methods: {
    statusLabel (status) {
      const map = {
        recommended: '已生成', accepted: '已接受', submitted: '已提交',
        passed: '已通过', failed: '未通过', archived: '已归档', draft: '草稿'
      }
      return map[status] || status
    },
    formatDate (iso) {
      if (!iso) return ''
      const d = new Date(iso)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    },
    async copyPortfolio () {
      try {
        if (navigator && navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(this.portfolioMarkdown)
        } else {
          this.legacyCopy(this.portfolioMarkdown)
        }
        this.copied = true
        notify.success('已复制到剪贴板')
        setTimeout(() => { this.copied = false }, 2400)
      } catch (e) {
        notify.error('复制失败，请手动选择文本复制')
      }
    },
    legacyCopy (text) {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.setAttribute('readonly', '')
      ta.style.position = 'absolute'
      ta.style.left = '-9999px'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    },
    downloadPortfolio () {
      const blob = new Blob([this.portfolioMarkdown], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `career-project-${this.project.id}.md`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    }
  }
}
</script>

<style scoped>
.project-detail-page {
  max-width: 760px;
  margin: 40px auto;
  padding: 0 20px;
}
.page-header {
  margin-bottom: 24px;
}
.back-link {
  font-size: 13px;
  color: #6366f1;
  text-decoration: none;
  display: inline-block;
  margin-bottom: 12px;
}
.back-link:hover {
  text-decoration: underline;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
}
.muted-line, .empty-state {
  color: #9ca3af;
  font-size: 14px;
  text-align: center;
  padding: 24px 0;
}
.meta-card,
.brief-card,
.portfolio-card {
  margin-bottom: 20px;
  padding: 20px 24px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 12px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  margin-bottom: 6px;
}
.meta-label {
  color: #6b7280;
  min-width: 76px;
}
.meta-value {
  color: #1f2937;
}
.status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f3f4f6;
  color: #6b7280;
}
.badge-passed { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
.badge-recommended { background: rgba(99, 102, 241, 0.12); color: #4338ca; }
.badge-failed { background: rgba(239, 68, 68, 0.12); color: #b91c1c; }
.brief-content {
  font-size: 14px;
  color: #374151;
  line-height: 1.7;
}
.brief-content :deep(h2) { font-size: 16px; margin-top: 16px; }
.brief-content :deep(code) {
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, "SFMono", Menlo, Consolas, monospace;
}
.portfolio-preview {
  margin-top: 12px;
  padding: 12px 16px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, "SFMono", Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow-y: auto;
}
.portfolio-actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.primary-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: #6366f1;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  min-height: 36px;
}
.primary-btn:hover { background: #4f46e5; }
.primary-btn:focus-visible {
  outline: 3px solid rgba(99, 102, 241, 0.4);
  outline-offset: 2px;
}
.ghost-btn {
  padding: 8px 16px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #4b5563;
  font-size: 13px;
  cursor: pointer;
}
.link-btn {
  font-size: 13px;
  color: #6366f1;
  text-decoration: none;
  font-weight: 500;
}
.link-btn:hover { text-decoration: underline; }
</style>

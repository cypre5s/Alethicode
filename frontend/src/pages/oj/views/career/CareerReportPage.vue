<template>
  <div class="career-report-page">
    <h1 class="page-title">我的专业报告</h1>
    <p class="page-desc">这里展示根据你的专业和学习进度生成的 Why 报告。</p>

    <div v-if="loading" class="loading-state">加载中…</div>

    <div v-else-if="reports.length === 0" class="empty-state">
      <p>暂无报告。</p>
      <router-link to="/career/profile" class="link-cta">填写专业档案</router-link>
    </div>

    <div v-else class="report-list">
      <article
        v-for="r in reports"
        :key="r.id"
        class="report-card"
      >
        <div class="report-card-header">
          <h2 class="report-card-title">{{ r.title }}</h2>
          <time class="report-card-time">{{ formatDate(r.created_at) }}</time>
        </div>
        <div class="report-card-body" v-html="renderMd(r.content_md)"></div>
        <div v-if="r.citations && r.citations.length" class="report-citations">
          <span class="citation-label">来源：</span>
          <span
            v-for="(c, i) in r.citations"
            :key="i"
            class="citation-tag"
          >{{ c.source }}:{{ c.ref }}</span>
        </div>
      </article>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'

export default {
  name: 'CareerReportPage',
  data () {
    return {
      reports: [],
      loading: true
    }
  },
  async created () {
    try {
      const res = await api.getCareerReports(10)
      this.reports = res.data.data || []
    } catch { /* silent */ }
    this.loading = false
  },
  methods: {
    formatDate (iso) {
      if (!iso) return ''
      const d = new Date(iso)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    },
    renderMd (md) {
      if (!md) return ''
      return sanitize(marked(md))
    }
  }
}
</script>

<style scoped>
.career-report-page {
  max-width: 720px;
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
.loading-state,
.empty-state {
  text-align: center;
  color: #9ca3af;
  padding: 48px 0;
}
.link-cta {
  display: inline-block;
  margin-top: 12px;
  color: #6366f1;
  font-weight: 600;
  text-decoration: none;
}
.link-cta:hover {
  text-decoration: underline;
}
.report-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.report-card {
  padding: 24px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.report-card-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
}
.report-card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.report-card-time {
  font-size: 12px;
  color: #9ca3af;
}
.report-card-body {
  font-size: 14px;
  color: #374151;
  line-height: 1.7;
}
.report-citations {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.citation-label {
  font-size: 12px;
  color: #9ca3af;
}
.citation-tag {
  font-size: 11px;
  padding: 2px 8px;
  background: #f3f4f6;
  border-radius: 4px;
  color: #6b7280;
}
</style>

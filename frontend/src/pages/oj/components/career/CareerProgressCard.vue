<template>
  <section v-if="visible" class="career-progress-card">
    <div class="card-header">
      <svg class="card-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10"/>
        <path d="M12 16v-4"/>
        <path d="M12 8h.01"/>
      </svg>
      <span class="card-label">专业 × 编程</span>
    </div>

    <template v-if="profile && profile.major_code">
      <p class="card-major">{{ profile.major_name_zh || profile.major_code }}</p>
      <p v-if="latestTitle" class="card-latest">
        最近报告：<strong>{{ latestTitle }}</strong>
      </p>
      <div class="card-actions">
        <router-link to="/career/reports" class="card-link">查看全部报告</router-link>
      </div>
    </template>
    <template v-else>
      <p class="card-empty">填写专业，获取个性化学习路径</p>
      <router-link to="/career/profile" class="card-cta">填写专业档案</router-link>
    </template>
  </section>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'CareerProgressCard',
  data () {
    return {
      profile: null,
      latestTitle: '',
      visible: false
    }
  },
  async created () {
    // 主页常驻 widget，故意 silent：profile/reports 失败不弹 toast 干扰其它卡片。
    // 失败时 visible=false，整个卡片不渲染，不影响主页其它内容；用户去
    // /career/profile 页面手动操作时由那一页给明确错误提示。
    try {
      const res = await api.getCareerProfile()
      this.profile = res.data.data
      this.visible = true
      if (this.profile && this.profile.major_code) {
        const rRes = await api.getCareerReports(1)
        const reports = rRes.data.data || []
        if (reports.length > 0) {
          this.latestTitle = reports[0].title
        }
      }
    } catch { /* by-design silent，理由见上 */ }
  }
}
</script>

<style scoped>
.career-progress-card {
  padding: 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.card-icon {
  color: #6b7280;
}
.card-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  letter-spacing: 0.3px;
}
.card-major {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}
.card-latest {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 12px;
}
.card-actions {
  display: flex;
  gap: 12px;
}
.card-link {
  font-size: 13px;
  color: #2563eb;
  font-weight: 500;
  text-decoration: none;
}
.card-link:hover {
  text-decoration: underline;
}
.card-empty {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 12px;
}
.card-cta {
  display: inline-block;
  padding: 8px 20px;
  background: #2563eb;
  color: #fff;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: background 0.2s;
}
.card-cta:hover {
  background: #1d4ed8;
}
</style>

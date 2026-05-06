<template>
  <div class="career-studio-page">
    <h1 class="page-title">专业微项目工作室</h1>
    <p class="page-desc">基于你的专业与已掌握知识点，由 AI 出题并真判题自验证；通过后即作为正式题目挂到判题机，可正常提交。</p>

    <section v-if="recommendation" class="recommendation-card">
      <h2 class="section-title">推荐 KC 簇</h2>
      <p class="recommendation-rationale">{{ recommendation.rationale }}</p>
      <div class="kc-tags">
        <span v-for="kc in recommendation.kcCodes" :key="kc" class="kc-tag">{{ kc }}</span>
      </div>
      <button
        class="primary-btn"
        :disabled="!hasProfile || generating"
        :aria-label="generating ? '正在生成微项目' : '生成微项目'"
        @click="handleGenerate"
      >
        {{ generating ? '生成中…（含真判题，可能需 5-15 秒）' : '生成微项目' }}
      </button>
      <p v-if="!hasProfile" class="warn-note">先到「专业档案」填写专业，才能生成与专业相关的微项目。</p>
    </section>

    <section v-else-if="!recommendationLoading" class="empty-recommendation">
      <p>当前没有可推荐的 KC 簇——继续在课件包做题积累 mastery，到 0.5 以上即会出现推荐。</p>
    </section>

    <section class="projects-section">
      <div class="section-header">
        <h2 class="section-title">我的微项目</h2>
        <button class="ghost-btn" :disabled="loadingProjects" @click="loadProjects">{{ loadingProjects ? '刷新中…' : '刷新' }}</button>
      </div>

      <p v-if="loadingProjects" class="muted-line">加载中…</p>
      <p v-else-if="projects.length === 0" class="muted-line">暂无微项目，使用上方「生成微项目」开始你的第一个。</p>

      <ul v-else class="project-list">
        <li v-for="p in projects" :key="p.id" class="project-card" :class="'status-' + p.status">
          <header class="project-card-header">
            <h3 class="project-card-title">{{ p.title }}</h3>
            <span class="status-badge" :class="'badge-' + p.status">{{ statusLabel(p.status) }}</span>
          </header>
          <p class="project-card-meta">
            <span class="meta-item">专业：{{ p.major_code }}</span>
            <span class="meta-item">创建：{{ formatDate(p.created_at) }}</span>
            <span v-if="p.score != null" class="meta-item">得分：{{ p.score }}</span>
          </p>
          <div class="project-card-actions">
            <router-link
              v-if="p.judge_problem_id"
              :to="{ name: 'problem-details', params: { problemID: p.judge_problem_id } }"
              class="link-btn"
            >前往判题页提交</router-link>
            <router-link :to="{ name: 'career-project-detail', params: { projectId: p.id } }" class="link-btn">查看详情 / 导出作品集</router-link>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<script>
import api from '@oj/api'
import { notify } from '@/utils/notifications'

export default {
  name: 'CareerStudioPage',
  data () {
    return {
      hasProfile: false,
      majorCode: '',
      recommendation: null,
      recommendationLoading: true,
      projects: [],
      loadingProjects: false,
      generating: false
    }
  },
  async created () {
    await Promise.all([this.loadProfile(), this.loadRecommendation(), this.loadProjects()])
  },
  methods: {
    async loadProfile () {
      try {
        const res = await api.getCareerProfile()
        const d = res.data.data
        if (d && d.major_code) {
          this.majorCode = d.major_code
          this.hasProfile = true
        }
      } catch (e) {
        // by-design silent: 主页 widget 不打扰其它任务
      }
    },
    async loadRecommendation () {
      this.recommendationLoading = true
      try {
        const res = await api.getStudioRecommendations()
        const list = res.data.data || []
        this.recommendation = list.length > 0 ? list[0] : null
      } catch (e) {
        this.recommendation = null
      } finally {
        this.recommendationLoading = false
      }
    },
    async loadProjects () {
      this.loadingProjects = true
      try {
        const res = await api.listStudioProjects(20)
        this.projects = res.data.data || []
      } catch (e) {
        this.projects = []
      } finally {
        this.loadingProjects = false
      }
    },
    async handleGenerate () {
      if (!this.recommendation || !this.majorCode) return
      this.generating = true
      try {
        const res = await api.generateStudioProject(this.majorCode, this.recommendation.kcCodes)
        const error = res.data && res.data.error
        if (error) {
          notify.warning('微项目生成失败，请稍后再试（reference solution 自验证未通过 / Judge Server 暂不可用）')
        } else {
          notify.success('微项目已生成，刷新列表查看')
          await this.loadProjects()
        }
      } catch (e) {
        notify.error('微项目生成异常，请联系教师或稍后再试')
      } finally {
        this.generating = false
      }
    },
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
    }
  }
}
</script>

<style scoped>
.career-studio-page {
  max-width: 800px;
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
  line-height: 1.6;
  margin-bottom: 28px;
}
.recommendation-card,
.projects-section {
  margin-bottom: 28px;
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
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.recommendation-rationale {
  color: #4b5563;
  font-size: 14px;
  margin-bottom: 12px;
}
.kc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}
.kc-tag {
  font-size: 12px;
  padding: 4px 10px;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 6px;
  font-weight: 500;
}
.empty-recommendation {
  margin-bottom: 24px;
  padding: 16px 24px;
  color: #6b7280;
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  font-size: 14px;
}
.primary-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  background: #6366f1;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
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
.ghost-btn {
  padding: 6px 14px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #4b5563;
  font-size: 13px;
  cursor: pointer;
}
.ghost-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.warn-note {
  margin-top: 8px;
  font-size: 12px;
  color: #d97706;
}
.muted-line {
  color: #9ca3af;
  font-size: 14px;
  margin: 0;
}
.project-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.project-card {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
}
.project-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.project-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}
.status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f3f4f6;
  color: #6b7280;
}
.badge-passed {
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
}
.badge-recommended {
  background: rgba(99, 102, 241, 0.12);
  color: #4338ca;
}
.badge-failed {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}
.project-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
  margin: 0 0 12px;
}
.project-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.link-btn {
  font-size: 13px;
  color: #6366f1;
  text-decoration: none;
  font-weight: 500;
}
.link-btn:hover {
  text-decoration: underline;
}
</style>

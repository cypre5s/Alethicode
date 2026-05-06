<template>
  <div class="career-path-page">
    <h1 class="page-title">学习路径地图</h1>
    <p class="page-desc">查看你的专业在 Python 学习中的节点进展。</p>
    <div v-if="loading" class="loading-state">加载中…</div>
    <div v-else-if="!pathData || !pathData.nodes || pathData.nodes.length === 0" class="empty-state">
      <p>暂无路径数据。</p>
      <router-link to="/career/profile" class="link-cta">先填写专业档案</router-link>
    </div>
    <template v-else>
      <p class="path-major">专业：<strong>{{ pathData.major_name_zh }}</strong></p>
      <div class="dag-container">
        <template v-for="(n, idx) in pathData.nodes" :key="n.kc_code">
          <div class="dag-chip" :class="'dag-chip--' + n.status">
            <div class="dag-chip-name">{{ n.kc_code }}</div>
            <div class="dag-chip-pct">{{ formatPct(n.mastery) }}</div>
          </div>
          <span v-if="idx < pathData.nodes.length - 1" class="dag-arrow">&rarr;</span>
        </template>
      </div>
      <div class="node-list">
        <div v-for="n in pathData.nodes" :key="n.kc_code" class="node-card" :class="'node-' + n.status">
          <div class="node-header">
            <span class="node-dot" :class="'dot-' + n.status"></span>
            <strong>{{ n.kc_code }}</strong>
            <span v-if="n.mastery != null" class="node-mastery">{{ Math.round(n.mastery * 100) }}%</span>
          </div>
          <p class="node-why">{{ n.why_md }}</p>
          <div v-if="n.typical_use_cases && n.typical_use_cases.length" class="node-cases">
            <span v-for="(c, i) in n.typical_use_cases" :key="i" class="case-tag">{{ c }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'
export default {
  name: 'CareerPathPage',
  data () { return { pathData: null, loading: true } },
  async created () {
    try {
      const p = await api.getCareerProfile()
      const mc = p.data.data && p.data.data.major_code
      if (!mc) { this.loading = false; return }
      const r = await api.getCareerPath(mc)
      this.pathData = r.data.data
    } catch {}
    this.loading = false
  },
  methods: {
    formatPct (m) {
      return m == null ? '\u2014' : Math.round(m * 100) + '%'
    }
  }
}
</script>

<style scoped>
.career-path-page { max-width: 800px; margin: 40px auto; padding: 0 20px; }
.page-title { font-size: 24px; font-weight: 700; color: #1f2937; margin-bottom: 8px; }
.page-desc { color: #6b7280; margin-bottom: 24px; }
.path-major { font-size: 15px; color: #374151; margin-bottom: 20px; }
.loading-state, .empty-state { text-align: center; color: #9ca3af; padding: 48px 0; }
.link-cta { color: #6366f1; font-weight: 600; text-decoration: none; }
.dag-container { margin-bottom: 28px; overflow-x: auto; padding: 12px 0; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.dag-chip { padding: 10px 16px; border-radius: 10px; text-align: center; min-width: 100px; border: 2px solid #9ca3af; background: rgba(156, 163, 175, 0.09); }
.dag-chip-name { font-weight: 700; color: #4b5563; }
.dag-chip-pct { font-size: 12px; color: #6b7280; }
.dag-chip--unlocked { border-color: #22c55e; background: rgba(34, 197, 94, 0.09); }
.dag-chip--unlocked .dag-chip-name { color: #16a34a; }
.dag-chip--in_progress { border-color: #eab308; background: rgba(234, 179, 8, 0.09); }
.dag-chip--in_progress .dag-chip-name { color: #ca8a04; }
.dag-chip--locked { border-color: #9ca3af; background: rgba(156, 163, 175, 0.09); }
.dag-chip--locked .dag-chip-name { color: #6b7280; }
.dag-arrow { font-size: 20px; color: #d1d5db; }
.node-list { display: flex; flex-direction: column; gap: 12px; }
.node-card { padding: 16px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; }
.node-unlocked { border-left: 4px solid #22c55e; }
.node-in_progress { border-left: 4px solid #eab308; }
.node-locked { border-left: 4px solid #9ca3af; opacity: 0.7; }
.node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.node-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.dot-unlocked { background: #22c55e; }
.dot-in_progress { background: #eab308; }
.dot-locked { background: #9ca3af; }
.node-mastery { font-size: 12px; color: #6b7280; margin-left: auto; }
.node-why { font-size: 13px; color: #4b5563; line-height: 1.6; margin-bottom: 8px; }
.node-cases { display: flex; flex-wrap: wrap; gap: 6px; }
.case-tag { font-size: 11px; padding: 2px 8px; background: #f3f4f6; border-radius: 4px; color: #6b7280; }
</style>

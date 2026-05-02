<template>
  <div class="lp-wrap">
    <div class="lp-header" @click="expanded = !expanded">
      <span class="lp-title">
        <el-icon :size="14"><Guide /></el-icon>
        学习路线
      </span>
      <span class="lp-progress" v-if="pathData">{{ pathData.mastered_count || 0 }}/{{ pathData.total_count || 0 }}</span>
      <span class="lp-toggle">{{ expanded ? '▾' : '▸' }}</span>
    </div>

    <transition name="lp-slide">
      <div v-show="expanded" class="lp-body">
        <div v-if="loading" class="lp-loading">加载中...</div>
        <div v-else-if="error" class="lp-error">{{ error }}</div>
        <div v-else-if="nodes.length === 0" class="lp-empty">暂无学习路径数据</div>
        <div v-else class="lp-nodes">
          <div
            v-for="(node, idx) in nodes"
            :key="node.kc_id"
            class="lp-node"
            :class="'lp-status-' + node.status"
            @click="handleNodeClick(node)"
          >
            <div class="lp-node-indicator">
              <span v-if="node.status === 'mastered'" class="lp-check">&#10003;</span>
              <span v-else-if="node.status === 'current'" class="lp-dot"></span>
              <span v-else class="lp-lock">&#128274;</span>
            </div>
            <div class="lp-node-info">
              <div class="lp-node-name">{{ node.kc_name }}</div>
              <div class="lp-node-chapter">{{ node.chapter }}</div>
            </div>
            <div class="lp-node-mastery">
              <div class="lp-mastery-bar">
                <div class="lp-mastery-fill" :style="{ width: (node.mastery * 100) + '%' }"></div>
              </div>
              <span class="lp-mastery-text">{{ (node.mastery * 100).toFixed(0) }}%</span>
            </div>
            <div v-if="idx < nodes.length - 1" class="lp-connector"></div>
          </div>
        </div>

        <div v-if="recommendation" class="lp-recommendation">
          <div class="lp-rec-label">推荐下一题</div>
          <div class="lp-rec-title" @click="navigateToRecommended">
            {{ recommendation.title || '查看推荐' }}
          </div>
          <div class="lp-rec-meta">
            策略：{{ strategyLabel }} | 目标难度：{{ targetDifficultyLabel }}
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import api from '@oj/api'
import { Guide } from '@element-plus/icons-vue'

const STRATEGY_LABELS = {
  easy_reinforcement: '基础巩固',
  adaptive_boundary: '自适应难度',
  transfer_variant: '迁移变式'
}

export default {
  name: 'LearningPathMap',
  components: { Guide },
  props: {
    languagePackId: { type: [Number, String], default: null }
  },
  data () {
    return {
      expanded: false,
      loading: false,
      error: '',
      pathData: null,
      nodes: [],
      recommendation: null
    }
  },
  computed: {
    strategyLabel () {
      if (!this.recommendation) return ''
      return STRATEGY_LABELS[this.recommendation.selection_strategy] || this.recommendation.selection_strategy
    },
    targetDifficultyLabel () {
      const raw = this.recommendation && this.recommendation.target_difficulty
      if (raw === null || raw === undefined || raw === '') return '—'
      const map = { Low: '简单', Easy: '简单', Mid: '中等', Medium: '中等', High: '较难', Hard: '较难' }
      return map[raw] || String(raw)
    }
  },
  watch: {
    languagePackId: {
      handler (val) { if (val) this.loadPath() },
      immediate: true
    }
  },
  methods: {
    loadPath () {
      if (!this.languagePackId) return
      this.loading = true
      this.error = ''

      api.getLearningPath(this.languagePackId).then(res => {
        const data = (res && res.data && res.data.data) || {}
        this.pathData = data
        this.nodes = Array.isArray(data.path) ? data.path : []
      }).catch(err => {
        this.error = err.message || '加载失败'
      }).finally(() => {
        this.loading = false
      })

      api.getNextProblemRecommendation(this.languagePackId).then(res => {
        const data = (res && res.data && res.data.data) || {}
        this.recommendation = data.recommended ? data : null
      }).catch(() => {
        this.recommendation = null
        this.$error('加载推荐题目失败')
      })
    },
    handleNodeClick (node) {
      if (node.status === 'locked') return
      this.$emit('select-kc', node)
    },
    navigateToRecommended () {
      if (!this.recommendation || !this.recommendation.recommended) return
      const pid = this.recommendation.recommended.problem_id
      if (pid) {
        this.$router.push('/problem/' + pid)
      }
    }
  }
}
</script>

<style lang="less" scoped>
.lp-wrap {
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-card, #fff);
  margin-bottom: 12px;
}

.lp-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, #1e293b);
  background: rgba(37, 99, 235, 0.04);
  user-select: none;
}
.lp-header:hover { background: rgba(37, 99, 235, 0.08); }
.lp-title { display: flex; align-items: center; gap: 6px; flex: 1; }
.lp-progress { font-size: 11px; color: var(--text-secondary, #64748b); }
.lp-toggle { font-size: 10px; color: var(--text-disabled); }

.lp-body { padding: 12px 14px; }
.lp-loading, .lp-error, .lp-empty {
  font-size: 12px; color: var(--text-secondary); text-align: center; padding: 16px 0;
}

.lp-nodes { display: flex; flex-direction: column; gap: 0; }

.lp-node {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 10px; border-radius: 6px;
  position: relative; cursor: pointer;
  transition: background 0.15s;
}
.lp-node:hover { background: rgba(0,0,0,0.03); }
.lp-status-locked { opacity: 0.5; cursor: default; }
.lp-status-locked:hover { background: none; }

.lp-node-indicator {
  width: 22px; height: 22px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; font-size: 11px;
}
.lp-status-mastered .lp-node-indicator { background: #22c55e; color: #fff; }
.lp-status-current .lp-node-indicator { background: #3b82f6; }
.lp-status-locked .lp-node-indicator { background: #e2e8f0; color: #94a3b8; }

.lp-check { font-weight: 700; }
.lp-dot { width: 8px; height: 8px; border-radius: 50%; background: #fff; }
.lp-lock { font-size: 10px; }

.lp-node-info { flex: 1; min-width: 0; }
.lp-node-name { font-size: 12px; font-weight: 500; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.lp-node-chapter { font-size: 10px; color: var(--text-disabled); }

.lp-node-mastery { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.lp-mastery-bar { width: 40px; height: 4px; border-radius: 2px; background: #e2e8f0; overflow: hidden; }
.lp-mastery-fill { height: 100%; border-radius: 2px; transition: width 0.3s; }
.lp-status-mastered .lp-mastery-fill { background: #22c55e; }
.lp-status-current .lp-mastery-fill { background: #3b82f6; }
.lp-status-locked .lp-mastery-fill { background: #cbd5e1; }
.lp-mastery-text { font-size: 10px; color: var(--text-secondary); min-width: 28px; text-align: right; }

.lp-connector {
  position: absolute; left: 24px; bottom: -4px;
  width: 1px; height: 8px; background: var(--border-color, #e2e8f0);
}

.lp-recommendation {
  margin-top: 12px; padding: 10px 12px;
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: 8px; background: rgba(37, 99, 235, 0.04);
}
.lp-rec-label { font-size: 10px; color: var(--primary-color); font-weight: 600; text-transform: uppercase; letter-spacing: 0.3px; margin-bottom: 4px; }
.lp-rec-title { font-size: 13px; font-weight: 500; color: var(--primary-color); cursor: pointer; }
.lp-rec-title:hover { text-decoration: underline; }
.lp-rec-meta { font-size: 10px; color: var(--text-secondary); margin-top: 4px; }

.lp-slide-enter-active, .lp-slide-leave-active {
  transition: max-height 0.3s ease, opacity 0.2s ease;
  max-height: 600px; overflow: hidden;
}
.lp-slide-enter-from, .lp-slide-leave-to { max-height: 0; opacity: 0; }
</style>

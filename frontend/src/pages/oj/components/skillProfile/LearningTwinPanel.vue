<template>
  <transition name="twin-fade">
    <div v-if="visible" class="lt-panel">
      <div class="lt-header">
        <span class="lt-title">学习画像</span>
        <span class="lt-close" @click="$emit('close')" role="button" aria-label="关闭">&times;</span>
      </div>

      <div v-if="loading" class="lt-loading">
        <div class="lt-spinner"></div>
      </div>

      <template v-else-if="twin">
        <div class="lt-mastery-bar">
          <div class="lt-mastery-label">
            <span>课程掌握度</span>
            <span class="lt-mastery-pct">{{ Math.round((twin.overall_mastery || 0) * 100) }}%</span>
          </div>
          <div class="lt-bar-track">
            <div class="lt-bar-fill" :style="{ width: Math.round((twin.overall_mastery || 0) * 100) + '%' }"></div>
          </div>
          <div class="lt-stats-row">
            <span>已尝试 {{ twin.problems_attempted || 0 }} 题</span>
            <span>通过 {{ twin.problems_solved || 0 }} 题</span>
          </div>
        </div>

        <div v-if="twin.current_problem_kcs && twin.current_problem_kcs.length" class="lt-section">
          <div class="lt-section-title">本题知识点</div>
          <div class="lt-kc-list">
            <div
              v-for="kc in twin.current_problem_kcs"
              :key="kc.kc_id"
              class="lt-kc-card"
              :class="'lt-kc-' + kc.level"
            >
              <span class="lt-kc-name">{{ kc.name }}</span>
              <span class="lt-kc-mastery">{{ Math.round((kc.mastery || 0) * 100) }}%</span>
            </div>
          </div>
        </div>

        <div v-if="twin.predicted_blockers && twin.predicted_blockers.length" class="lt-section lt-blockers">
          <div class="lt-section-title">预测卡点</div>
          <div v-for="(b, i) in twin.predicted_blockers" :key="i" class="lt-blocker-item">
            <span class="lt-blocker-icon">&#9888;</span>
            <span>{{ b }}</span>
          </div>
        </div>

        <div v-if="twin.active_memories && twin.active_memories.length" class="lt-section">
          <div class="lt-section-title">历史学习记忆</div>
          <div v-for="m in twin.active_memories" :key="m.memory_key" class="lt-memory-item">
            <div class="lt-memory-conf">{{ Math.round((m.confidence || 0) * 100) }}%</div>
            <div class="lt-memory-text">{{ m.memory_value }}</div>
          </div>
        </div>

        <div v-if="twin.recommended_actions && twin.recommended_actions.length" class="lt-section">
          <div class="lt-section-title">推荐动作</div>
          <div class="lt-actions">
            <button
              v-for="(a, i) in twin.recommended_actions"
              :key="i"
              class="lt-action-btn"
              @click="$emit('action', a)"
            >{{ a }}</button>
          </div>
        </div>
      </template>

      <div v-else class="lt-empty">暂无学习数据</div>
    </div>
  </transition>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'LearningTwinPanel',
  props: {
    visible: { type: Boolean, default: false },
    problemId: { type: [Number, String], default: null },
    languagePackId: { type: [Number, String], default: null }
  },
  emits: ['close', 'action'],
  data () {
    return {
      twin: null,
      loading: false
    }
  },
  watch: {
    visible (v) {
      if (v && !this.twin) this.fetchTwin()
    },
    problemId () {
      if (this.visible) this.fetchTwin()
    }
  },
  methods: {
    async fetchTwin () {
      if (!this.problemId || !this.languagePackId) return
      this.loading = true
      try {
        const res = await api.getLearningTwin(this.languagePackId, this.problemId)
        if (res.data && res.data.data) {
          this.twin = res.data.data
        }
      } catch (e) {
        console.warn('LearningTwin fetch failed:', e)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.lt-panel {
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-radius: 12px;
  border: 1px solid var(--border-color, rgba(0,0,0,0.06));
  padding: 16px;
  color: var(--text-primary, #1e293b);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.08);
}

.lt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.lt-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #0f172a);
}

.lt-close {
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
  opacity: 0.4;
  transition: opacity 0.15s;
  min-width: 44px;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lt-close:hover { opacity: 0.8; }

.lt-mastery-bar { display: flex; flex-direction: column; gap: 6px; }

.lt-mastery-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.lt-mastery-pct {
  font-size: 20px;
  font-weight: 700;
  color: #6366f1;
}

.lt-bar-track {
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.lt-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #818cf8);
  border-radius: 4px;
  transition: width 0.6s ease;
}

.lt-stats-row {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

.lt-section { display: flex; flex-direction: column; gap: 8px; }

.lt-section-title {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-disabled, #94a3b8);
  font-weight: 600;
}

.lt-kc-list { display: flex; flex-direction: column; gap: 6px; }

.lt-kc-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-radius: 8px;
  border-left: 3px solid;
}

.lt-kc-weak {
  border-color: #ef4444;
  background: #fef2f2;
}

.lt-kc-medium {
  border-color: #f59e0b;
  background: #fffbeb;
}

.lt-kc-strong {
  border-color: #22c55e;
  background: #f0fdf4;
}

.lt-kc-name { font-weight: 500; }

.lt-kc-mastery {
  font-weight: 700;
  font-size: 14px;
}

.lt-blockers .lt-blocker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fef2f2;
  border-radius: 8px;
  color: #dc2626;
}

.lt-blocker-icon { font-size: 16px; }

.lt-memory-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.lt-memory-conf {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  color: #6366f1;
  min-width: 36px;
}

.lt-memory-text {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary, #475569);
}

.lt-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lt-action-btn {
  padding: 8px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: transparent;
  color: #6366f1;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  min-height: 44px;
}

.lt-action-btn:hover {
  background: #eef2ff;
  border-color: #c7d2fe;
}

.lt-loading {
  display: flex;
  justify-content: center;
  padding: 24px;
}

.lt-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #e2e8f0;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: lt-spin 0.6s linear infinite;
}

@keyframes lt-spin { to { transform: rotate(360deg); } }

.lt-empty {
  text-align: center;
  padding: 24px;
  color: var(--text-disabled, #94a3b8);
}

.twin-fade-enter-active,
.twin-fade-leave-active {
  transition: opacity 0.25s ease;
}

.twin-fade-enter-from,
.twin-fade-leave-to {
  opacity: 0;
}
</style>

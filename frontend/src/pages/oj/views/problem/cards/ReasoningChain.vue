<template>
  <div v-if="chain && chain.length" class="rc-wrap">
    <div class="rc-toggle" @click="expanded = !expanded" role="button" tabindex="0" @keydown.enter="expanded = !expanded">
      <span class="rc-toggle-icon">{{ expanded ? '▾' : '▸' }}</span>
      <span class="rc-toggle-label">AI 推理过程（{{ chain.length }} 步）</span>
    </div>
    <transition name="rc-slide">
      <div v-show="expanded" class="rc-steps">
        <div v-for="(step, i) in chain" :key="i" class="rc-step">
          <div class="rc-step-head">
            <span class="rc-step-icon">{{ stepIcons[step.step] || '●' }}</span>
            <span class="rc-step-name">{{ step.step }}</span>
          </div>
          <div class="rc-step-content">{{ step.content }}</div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
export default {
  name: 'ReasoningChain',
  props: {
    chain: { type: Array, default: () => [] }
  },
  data () {
    return {
      expanded: false,
      stepIcons: {
        '观察': '👁',
        '假设': '💡',
        '验证': '🔍',
        '结论': '✅',
        '建议': '📝',
        '分析': '🔬',
        '检索': '📚',
        '引导': '🧭',
        '回顾': '📋',
        '反思': '🪞',
        '关联': '🔗',
        '变式': '🔄'
      }
    }
  }
}
</script>

<style scoped>
.rc-wrap {
  margin-top: 10px;
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 8px;
  overflow: hidden;
}

.rc-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  background: rgba(248, 250, 252, 0.5);
  min-height: 44px;
  user-select: none;
}

.rc-toggle:hover {
  background: rgba(241, 245, 249, 0.8);
}

.rc-toggle-icon { font-size: 10px; }
.rc-toggle-label { font-weight: 500; }

.rc-steps {
  padding: 4px 0;
}

.rc-step {
  padding: 8px 16px;
  border-bottom: 1px solid rgba(0,0,0,0.04);
}

.rc-step:last-child { border-bottom: none; }

.rc-step-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.rc-step-icon { font-size: 14px; }

.rc-step-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.rc-step-content {
  font-size: 13px;
  color: var(--text-secondary, #475569);
  line-height: 1.6;
  padding-left: 20px;
}

.rc-slide-enter-active,
.rc-slide-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s ease;
  max-height: 500px;
  overflow: hidden;
}

.rc-slide-enter-from,
.rc-slide-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>

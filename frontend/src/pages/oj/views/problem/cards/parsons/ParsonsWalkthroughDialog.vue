<template>
  <transition name="pwd-fade">
    <div
      v-if="visible"
      class="pwd-mask"
      role="dialog"
      aria-modal="true"
      aria-labelledby="pwd-title"
      :aria-describedby="describedBy"
    >
      <div class="pwd-card" :class="cardStateClass">
        <header class="pwd-head">
          <h3 id="pwd-title">
            <component v-if="stage === 'pass'" :is="EurekaIcon" class="pwd-head-eureka" aria-hidden="true" />
            {{ headTitle }}
          </h3>
          <p id="pwd-subtitle" class="pwd-head-sub">{{ headSubtitle }}</p>
        </header>

        <section class="pwd-body" :id="describedBy">
          <div v-if="stage === 'pass'" class="pwd-eureka" role="status">
            <component :is="EurekaIcon" class="pwd-eureka-icon" aria-hidden="true" />
            <p class="pwd-eureka-text">已记入「顿悟笔记」，FSRS 也将顺势推进。</p>
          </div>

          <template v-else>
            <p class="pwd-tip">
              {{ stage === 'rewrite' ? '再说细一点：哪个 block 是关键？为什么这样排？' : '挑一个关键 block，说说它在做什么、为什么放在这里。' }}
            </p>
            <textarea
              ref="taRef"
              v-model="text"
              class="pwd-input"
              rows="4"
              :disabled="loading"
              :placeholder="placeholderText"
              :aria-invalid="lastEvaluated && !lastPassed && stage === 'rewrite' ? 'true' : 'false'"
            />
            <div v-if="text.length" class="pwd-counter">
              {{ text.trim().length }} 字 / 建议 ≥ {{ MIN_TEXT }} 字
            </div>
          </template>

          <div v-if="feedback && stage !== 'pass'" class="pwd-feedback" :class="feedbackStateClass">
            <strong>{{ feedbackTitle }}：{{ Math.round(score * 100) }} 分</strong>
            <p>{{ feedback }}</p>
          </div>

          <div v-if="stage === 'fail'" class="pwd-soft-warning" role="status">
            <strong>理解还不够稳</strong>
            <p>这一题先到这里，建议下次再练一道相似题。学习成长是迭代的。</p>
          </div>
        </section>

        <footer class="pwd-foot">
          <span v-if="canRewrite && lastEvaluated" class="pwd-attempts" aria-live="polite">
            还有 {{ remainingAttempts }} 次重写机会
          </span>
          <span v-else-if="stage === 'fail'" class="pwd-attempts">已用尽重写机会</span>

          <button
            v-if="stage === 'pass'"
            type="button"
            class="pwd-btn pwd-btn-primary"
            @click="$emit('continue')"
          >继续下一题</button>

          <button
            v-else-if="stage === 'fail'"
            type="button"
            class="pwd-btn pwd-btn-secondary"
            @click="$emit('continue')"
          >下次再练</button>

          <button
            v-else
            type="button"
            class="pwd-btn pwd-btn-primary"
            :disabled="loading || !canSubmit"
            @click="onSubmit"
          >
            <span v-if="loading">评估中…</span>
            <span v-else-if="stage === 'rewrite'">重写并提交</span>
            <span v-else>提交</span>
          </button>
        </footer>
      </div>
    </div>
  </transition>
</template>

<script>
import { h, markRaw } from 'vue'

const MIN_TEXT = 6
const MAX_TOTAL_ATTEMPTS = 2

const EurekaIcon = markRaw({
  name: 'EurekaIcon',
  render () {
    return h('svg', {
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: 'currentColor',
      'stroke-width': '2',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      'aria-hidden': 'true'
    }, [
      h('path', { d: 'M9 18h6' }),
      h('path', { d: 'M10 22h4' }),
      h('path', { d: 'M12 2a7 7 0 0 0-4 12.7c.6.5 1 1.2 1 2v.3h6v-.3c0-.8.4-1.5 1-2A7 7 0 0 0 12 2z' })
    ])
  }
})

export default {
  name: 'ParsonsWalkthroughDialog',
  props: {
    visible: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    score: { type: Number, default: 0 },
    feedback: { type: String, default: '' },
    lastPassed: { type: Boolean, default: false },
    canRewrite: { type: Boolean, default: false },
    attempts: { type: Number, default: 0 }
  },
  emits: ['submit', 'continue'],
  data () {
    return {
      text: '',
      MIN_TEXT,
      EurekaIcon
    }
  },
  computed: {
    lastEvaluated () {
      return this.attempts > 0 || this.feedback.length > 0
    },
    stage () {
      if (!this.lastEvaluated) return 'initial'
      if (this.lastPassed) return 'pass'
      if (this.canRewrite) return 'rewrite'
      return 'fail'
    },
    cardStateClass () {
      return `pwd-card--${this.stage}`
    },
    feedbackStateClass () {
      if (this.lastPassed) return 'pwd-feedback--pass'
      if (this.canRewrite) return 'pwd-feedback--rewrite'
      return 'pwd-feedback--fail'
    },
    feedbackTitle () {
      if (this.lastPassed) return '通过'
      if (this.canRewrite) return '再细一点'
      return '本次评分'
    },
    headTitle () {
      switch (this.stage) {
        case 'pass': return '顿悟时刻'
        case 'rewrite': return '再讲一次'
        case 'fail': return '本次先到这里'
        default: return '用一句话讲清你的代码'
      }
    },
    headSubtitle () {
      switch (this.stage) {
        case 'pass': return '说清楚自己写的代码，理解就稳了。'
        case 'rewrite': return '上次还差一点，AI 给了反馈，再补一刀。'
        case 'fail': return '讲清楚不容易，下一题继续练。'
        default: return '挑一个关键 block，说说它在做什么、为什么放在这里。'
      }
    },
    placeholderText () {
      if (this.stage === 'rewrite') {
        return '例如：第二行的 for i in range(n+1) 是闭区间遍历，因为题目要求 0..n 都覆盖到。'
      }
      return '例如：第二行的 for i in range(n+1) 是为了把 0..n 都遍历到，因为题目要求闭区间。'
    },
    canSubmit () {
      if (this.stage === 'pass' || this.stage === 'fail') return false
      return this.text.trim().length >= MIN_TEXT
    },
    remainingAttempts () {
      const used = this.attempts || 0
      return Math.max(0, MAX_TOTAL_ATTEMPTS - used)
    },
    describedBy () {
      return 'pwd-subtitle'
    }
  },
  watch: {
    visible (val) {
      if (val) {
        this.$nextTick(() => {
          if (this.$refs.taRef) this.$refs.taRef.focus()
        })
      } else {
        this.text = ''
      }
    },
    stage (next) {
      if (next === 'rewrite') {
        // 进入重写阶段时清空文本，避免误以为之前的文本就是已提交内容
        this.text = ''
        this.$nextTick(() => {
          if (this.$refs.taRef) this.$refs.taRef.focus()
        })
      }
    }
  },
  methods: {
    onSubmit () {
      if (!this.canSubmit) return
      this.$emit('submit', this.text.trim())
    }
  }
}
</script>

<style lang="less" scoped>
.pwd-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  padding: var(--space-4);
}
.pwd-card {
  width: min(560px, 100%);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-default);
}
.pwd-card--pass {
  border-color: rgba(16, 185, 129, 0.40);
  box-shadow: var(--shadow-warm), 0 0 0 4px rgba(16, 185, 129, 0.12);
}
.pwd-card--rewrite {
  border-color: rgba(245, 158, 11, 0.45);
}
.pwd-card--fail {
  border-color: rgba(239, 68, 68, 0.30);
}
.pwd-head {
  padding: var(--space-4) var(--space-5);
  background: var(--warm-grad-primary);
  color: #fff;
}
.pwd-card--pass .pwd-head {
  background: linear-gradient(135deg, #10b981 0%, #6366f1 100%);
}
.pwd-card--fail .pwd-head {
  background: linear-gradient(135deg, #ef4444 0%, #f59e0b 100%);
}
.pwd-head h3 {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.pwd-head-sub {
  margin: var(--space-1) 0 0;
  font-size: var(--fs-sm);
  opacity: 0.92;
}
.pwd-head-eureka {
  display: inline-flex;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  color: #fde68a;
  filter: drop-shadow(0 0 6px rgba(253, 224, 71, 0.6));
  animation: pwd-eureka-pop 0.6s cubic-bezier(0.18, 1.25, 0.4, 1.0);
}
.pwd-body {
  padding: var(--space-4) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.pwd-tip {
  margin: 0;
  font-size: var(--fs-base);
  color: var(--text-secondary);
}
.pwd-input {
  width: 100%;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  font-family: inherit;
  font-size: var(--fs-base);
  resize: vertical;
  min-height: 96px;
  background: var(--bg-card);
  color: var(--text-strong);
}
.pwd-input:focus-visible {
  outline: none;
  border-color: var(--warm-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.18);
}
.pwd-counter {
  font-size: var(--fs-xs);
  color: var(--text-disabled);
  text-align: right;
}
.pwd-feedback {
  background: rgba(245, 158, 11, 0.08);
  border-left: 3px solid rgba(245, 158, 11, 0.6);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  color: #92400e;
}
.pwd-feedback strong { display: block; font-size: var(--fs-base); }
.pwd-feedback--pass {
  background: rgba(16, 185, 129, 0.10);
  border-color: rgba(16, 185, 129, 0.6);
  color: #047857;
}
.pwd-feedback--fail {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.6);
  color: #b91c1c;
}
.pwd-feedback p { margin: var(--space-1) 0 0; }

.pwd-eureka {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  background: rgba(16, 185, 129, 0.08);
  border-radius: var(--radius-md);
  border: 1px solid rgba(16, 185, 129, 0.30);
  text-align: center;
}
.pwd-eureka-icon {
  width: 56px;
  height: 56px;
  color: #f59e0b;
  animation: pwd-eureka-pop 0.7s cubic-bezier(0.2, 1.4, 0.4, 1.0),
             pwd-eureka-glow 1.6s ease-in-out 0.4s 2;
}
.pwd-eureka-text {
  margin: 0;
  font-size: var(--fs-base);
  color: #047857;
  font-weight: 500;
  line-height: var(--leading-body);
}

.pwd-soft-warning {
  background: rgba(239, 68, 68, 0.05);
  border: 1px solid rgba(239, 68, 68, 0.20);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  color: #b91c1c;
  font-size: var(--fs-sm);
}
.pwd-soft-warning strong { display: block; margin-bottom: var(--space-1); }
.pwd-soft-warning p { margin: 0; line-height: var(--leading-body); }

.pwd-foot {
  padding: var(--space-3) var(--space-5) var(--space-4);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  border-top: 1px solid var(--border-default);
}
.pwd-attempts {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
}
.pwd-btn {
  padding: 0 18px;
  min-height: var(--control-height-lg);
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  font-size: var(--fs-base);
  font-weight: 600;
  cursor: pointer;
  transition: opacity var(--motion-fast), background var(--motion-fast);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.pwd-btn:focus-visible {
  outline: 2px solid var(--warm-primary);
  outline-offset: 2px;
}
.pwd-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.pwd-btn-primary {
  background: var(--warm-primary);
  color: #fff;
}
.pwd-btn-primary:hover:not(:disabled) { background: var(--warm-primary-strong); }
.pwd-btn-secondary {
  background: var(--bg-panel);
  color: var(--text-secondary);
  border-color: var(--border-default);
}
.pwd-btn-secondary:hover:not(:disabled) {
  background: var(--bg-base);
}

.pwd-fade-enter-active, .pwd-fade-leave-active { transition: opacity var(--motion-base); }
.pwd-fade-enter-from, .pwd-fade-leave-to { opacity: 0; }

@keyframes pwd-eureka-pop {
  0% { transform: scale(0.6); opacity: 0; }
  60% { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
}
@keyframes pwd-eureka-glow {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(16, 185, 129, 0)); }
  50% { filter: drop-shadow(0 0 12px rgba(16, 185, 129, 0.4)); }
}

@media (prefers-reduced-motion: reduce) {
  .pwd-head-eureka,
  .pwd-eureka-icon { animation: none; }
}
</style>

<template>
  <teleport to="body">
    <transition name="nb-reflect-fade">
      <div v-if="modelValue" class="nb-reflect-overlay" @click.self="close" @keydown.esc="close">
        <div class="nb-reflect-dialog" role="dialog" aria-modal="true" aria-label="反思教练">
          <div class="nb-reflect-progress">
            <span v-for="s in 3" :key="s" class="nb-reflect-dot" :class="{ active: s <= step, current: s === step }"></span>
          </div>

          <div class="nb-reflect-header">
            <svg class="nb-reflect-coach-icon" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z"/>
              <path d="M12 16v-4"/>
              <path d="M12 8h.01"/>
            </svg>
            <span class="nb-reflect-step-label">第 {{ step }} 步 / 共 3 步</span>
          </div>

          <div v-if="loading" class="nb-reflect-loading">教练正在思考...</div>
          <div v-else class="nb-reflect-body">
            <p class="nb-reflect-question">{{ question }}</p>
            <div class="nb-reflect-candidates">
              <button
                v-for="(c, ci) in candidates"
                :key="ci"
                type="button"
                class="nb-reflect-candidate-chip"
                :class="{ selected: answer === c }"
                @click="answer = c"
              >{{ c }}</button>
            </div>
            <textarea
              v-model="customAnswer"
              class="nb-reflect-custom"
              rows="2"
              placeholder="或者写下你自己的想法..."
              @keydown.ctrl.enter="next"
            ></textarea>
          </div>

          <div class="nb-reflect-footer">
            <button v-if="step > 1" type="button" class="nb-reflect-btn nb-reflect-prev" @click="prev">上一步</button>
            <a v-if="!loading" class="nb-reflect-skip" @click="skipCoach">跳过教练，直接写</a>
            <button
              type="button"
              class="nb-reflect-btn nb-reflect-next"
              :disabled="loading || (!answer && !customAnswer.trim())"
              @click="next"
            >{{ step === 3 ? '完成保存' : '下一步' }}</button>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script>
import { useNotebookReflectionApi } from '@/composables/notebook/useNotebookReflectionApi'

export default {
  name: 'NotebookReflectionDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    problemId: { type: [Number, String], default: null },
    language: { type: String, default: 'Python3' },
    errorTaxonomy: { type: String, default: '' },
    rootCause: { type: String, default: '' },
    codeSnippet: { type: String, default: '' },
    entryType: { type: String, default: 'error' },
    breakthroughInsight: { type: String, default: '' }
  },
  emits: ['update:modelValue', 'saved'],
  data () {
    return {
      step: 1,
      loading: false,
      sessionId: null,
      question: '',
      candidates: [],
      answer: '',
      customAnswer: '',
      history: [],
      skipMode: false
    }
  },
  watch: {
    modelValue (val) {
      if (val) this.startSession()
    }
  },
  methods: {
    close () {
      this.$emit('update:modelValue', false)
      this.resetState()
    },
    resetState () {
      this.step = 1
      this.sessionId = null
      this.question = ''
      this.candidates = []
      this.answer = ''
      this.customAnswer = ''
      this.history = []
      this.loading = false
      this.skipMode = false
    },
    async startSession () {
      this.resetState()
      this.loading = true
      try {
        const api = useNotebookReflectionApi()
        const result = await api.startReflection({
          problem_id: this.problemId,
          language: this.language,
          error_taxonomy: this.errorTaxonomy,
          root_cause: this.rootCause,
          code_snippet: this.codeSnippet
        })
        this.sessionId = result.session_id
        this.question = result.question
        this.candidates = result.candidates || []
        this.step = result.step || 1
      } catch (err) {
        this.question = '教练暂时无法连接，请直接写下你的反思。'
        this.skipMode = true
      } finally {
        this.loading = false
      }
    },
    async next () {
      const currentAnswer = this.customAnswer.trim() || this.answer
      if (!currentAnswer) return
      this.history.push({ step: this.step, question: this.question, answer: currentAnswer })

      if (this.step === 3 || this.skipMode) {
        await this.finalize(currentAnswer)
        return
      }

      this.loading = true
      this.answer = ''
      this.customAnswer = ''
      try {
        const api = useNotebookReflectionApi()
        const result = await api.continueReflection({
          session_id: this.sessionId,
          last_answer: currentAnswer
        })
        this.question = result.question
        this.candidates = result.candidates || []
        this.step = result.step || this.step + 1
      } catch {
        this.step += 1
        this.question = this.step === 2
          ? '下次遇到类似情境，你会先检查什么？'
          : '你能把这次经验迁移到什么类似场景？'
        this.candidates = []
      } finally {
        this.loading = false
      }
    },
    async finalize (lastAnswer) {
      this.loading = true
      try {
        const api = useNotebookReflectionApi()
        const result = await api.finalizeReflection({
          session_id: this.sessionId,
          final_answer: lastAnswer
        })
        this.$emit('saved', {
          structured_reflection: result.structured_reflection,
          free_form_reflection: result.free_form_reflection,
          entry_type: this.entryType,
          breakthrough_insight: this.breakthroughInsight
        })
        this.close()
      } catch {
        this.$emit('saved', {
          structured_reflection: {},
          free_form_reflection: this.history.map(h => h.answer).join('；'),
          entry_type: this.entryType,
          breakthrough_insight: this.breakthroughInsight
        })
        this.close()
      }
    },
    prev () {
      if (this.step <= 1) return
      const last = this.history.pop()
      this.step -= 1
      this.question = last ? last.question : ''
      this.answer = last ? last.answer : ''
      this.customAnswer = ''
      this.candidates = []
    },
    skipCoach () {
      this.skipMode = true
      this.question = '请直接写下你的反思：'
      this.candidates = []
      this.step = 3
    }
  }
}
</script>

<style lang="less" scoped>
.nb-reflect-overlay {
  position: fixed; inset: 0; z-index: 2000;
  display: flex; align-items: center; justify-content: center;
  background: rgba(30, 27, 58, 0.42); backdrop-filter: blur(4px);
}
.nb-reflect-dialog {
  background: #fff; border-radius: 22px; padding: 28px 26px 22px;
  width: 100%; max-width: 500px;
  box-shadow: 0 24px 60px rgba(30, 27, 58, 0.22);
  display: flex; flex-direction: column; gap: 18px;
}
.nb-reflect-progress {
  display: flex; gap: 8px; justify-content: center;
}
.nb-reflect-dot {
  width: 10px; height: 10px; border-radius: 50%;
  background: rgba(196, 181, 253, 0.32);
  transition: all 200ms ease;
  &.active { background: linear-gradient(135deg, #a78bfa, #7c3aed); }
  &.current { background: linear-gradient(135deg, #6366f1, #7c3aed); box-shadow: 0 0 0 4px rgba(124, 58, 237, 0.18); }
}
.nb-reflect-header {
  display: flex; align-items: center; gap: 10px;
}
.nb-reflect-coach-icon { color: #7c3aed; flex-shrink: 0; }
.nb-reflect-step-label {
  font-size: 12px; font-weight: 700;
  color: #4f46e5;
  background: rgba(196, 181, 253, 0.32);
  padding: 3px 12px; border-radius: 999px;
  letter-spacing: 0.2px;
}
.nb-reflect-loading {
  text-align: center; color: #9d9bb1; font-size: 14px; padding: 20px 0;
  font-style: italic;
}
.nb-reflect-body { display: flex; flex-direction: column; gap: 14px; }
.nb-reflect-question {
  font-size: 15px; color: #1e1b3a; line-height: 1.7;
  font-weight: 600; margin: 0;
}
.nb-reflect-candidates { display: flex; flex-wrap: wrap; gap: 8px; }
.nb-reflect-candidate-chip {
  border: 1px solid rgba(196, 181, 253, 0.4);
  background: #fbfaff; color: #5b5973;
  padding: 8px 14px; border-radius: 999px; font-size: 13px; cursor: pointer;
  transition: all 200ms ease; font-family: inherit; min-height: 44px;
  &:hover {
    border-color: rgba(165, 180, 252, 0.6);
    background: linear-gradient(135deg, #f5f3ff 0%, #fdf4ff 100%);
    color: #4f46e5;
    transform: translateY(-1px);
    box-shadow: 0 4px 10px rgba(99, 102, 241, 0.14);
  }
  &.selected {
    border-color: transparent;
    background: linear-gradient(135deg, #6366f1 0%, #7c3aed 100%);
    color: #fff; font-weight: 700;
    box-shadow: 0 4px 12px rgba(124, 58, 237, 0.28);
  }
}
.nb-reflect-custom {
  width: 100%;
  border: 1px solid rgba(196, 181, 253, 0.4);
  border-radius: 12px;
  background: #fbfaff;
  padding: 10px 12px; font-family: inherit; font-size: 13px; line-height: 1.6;
  resize: vertical; min-height: 56px;
  color: #1e1b3a;
  transition: all 200ms ease;
  &:focus {
    border-color: #6366f1;
    outline: none;
    box-shadow: 0 0 0 4px rgba(124, 58, 237, 0.12);
    background: #fff;
  }
}
.nb-reflect-footer {
  display: flex; align-items: center; gap: 10px; justify-content: flex-end;
}
.nb-reflect-skip {
  font-size: 12px; color: #9d9bb1; cursor: pointer; margin-right: auto;
  font-weight: 500;
  &:hover { color: #7c3aed; text-decoration: underline; }
}
.nb-reflect-btn {
  border: none; padding: 9px 20px; border-radius: 999px;
  font-size: 13px; font-weight: 700; cursor: pointer; font-family: inherit;
  min-height: 44px; transition: all 200ms ease;
  &.nb-reflect-prev {
    background: #fbfaff; color: #5b5973;
    border: 1px solid rgba(196, 181, 253, 0.4);
    &:hover { background: #fff; color: #4f46e5; }
  }
  &.nb-reflect-next {
    background: linear-gradient(135deg, #6366f1 0%, #7c3aed 100%);
    color: #fff;
    box-shadow: 0 4px 12px rgba(124, 58, 237, 0.28);
    &:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(124, 58, 237, 0.4); }
    &:disabled { opacity: 0.5; cursor: not-allowed; transform: none; box-shadow: none; }
  }
}
.nb-reflect-fade-enter-active, .nb-reflect-fade-leave-active {
  transition: opacity 200ms ease;
  .nb-reflect-dialog { transition: transform 280ms cubic-bezier(0.34, 1.56, 0.64, 1); }
}
.nb-reflect-fade-enter-from, .nb-reflect-fade-leave-to {
  opacity: 0;
  .nb-reflect-dialog { transform: translateY(12px) scale(0.96); }
}
@media (prefers-reduced-motion: reduce) {
  .nb-reflect-fade-enter-active, .nb-reflect-fade-leave-active,
  .nb-reflect-fade-enter-active .nb-reflect-dialog,
  .nb-reflect-fade-leave-active .nb-reflect-dialog,
  .nb-reflect-dot, .nb-reflect-candidate-chip,
  .nb-reflect-btn, .nb-reflect-custom {
    transition: none !important;
  }
}
</style>

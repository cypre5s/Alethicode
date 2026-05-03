<template>
  <div class="ta-card" role="region" aria-label="教 AI 学习">
    <template v-if="!started">
      <div class="ta-card__invite">
        <div class="ta-card__avatar" aria-hidden="true">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <circle cx="24" cy="24" r="22" fill="#E5EEF7" stroke="#0F4C81" stroke-width="2"/>
            <circle cx="18" cy="20" r="2" fill="#0F4C81"/><circle cx="30" cy="20" r="2" fill="#0F4C81"/>
            <path d="M18 30 Q24 34 30 30" stroke="#0F4C81" stroke-width="2" fill="none"/>
            <circle cx="14" cy="16" r="4" fill="none" stroke="#0F4C81" stroke-width="1.5"/>
            <circle cx="34" cy="16" r="4" fill="none" stroke="#0F4C81" stroke-width="1.5"/>
          </svg>
        </div>
        <p class="ta-card__intro">我是个刚学编程的新手，能教教我吗？</p>
        <button type="button" class="ta-card__start-btn" @click="startSession">
          好呀，我来教你
        </button>
      </div>
    </template>

    <template v-else-if="!completed">
      <div class="ta-card__chat">
        <div class="ta-card__misconception">
          <span class="ta-card__ai-label">AI 小白</span>
          <blockquote class="ta-card__quote">{{ misconception }}</blockquote>
          <p class="ta-card__prompt">你能帮我理解为什么这个想法不对吗？</p>
        </div>

        <textarea
          v-model="explanation"
          class="ta-card__textarea"
          placeholder="我来给你解释一下..."
          rows="5"
          maxlength="2000"
        ></textarea>
        <div class="ta-card__hints">
          <span class="ta-card__hint-chip" @click="addHint('比如')">💡 举个例子</span>
          <span class="ta-card__hint-chip" @click="addHint('就像')">🔄 打个比方</span>
          <span class="ta-card__hint-chip" @click="addHint('```python\n\n```')">💻 写段代码</span>
        </div>
        <div class="ta-card__footer">
          <span class="ta-card__char-count">{{ explanation.length }} 字</span>
          <button
            type="button"
            class="ta-card__submit-btn"
            :disabled="explanation.trim().length < 10 || submitting"
            @click="submitExplanation"
          >
            {{ submitting ? '评分中...' : '提交我的解释' }}
          </button>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="ta-card__result">
        <div class="ta-card__score-ring">
          <span class="ta-card__score-num">{{ score }}</span>
          <span class="ta-card__score-label">教学分</span>
        </div>
        <p class="ta-card__feedback">{{ feedback }}</p>
        <div v-if="followupQuestion" class="ta-card__followup">
          <p class="ta-card__followup-q">{{ followupQuestion }}</p>
          <button type="button" class="ta-card__continue-btn" @click="completed = false">
            继续回答
          </button>
        </div>
        <button v-else type="button" class="ta-card__done-btn" @click="$emit('close')">
          完成
        </button>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'TeachAiCard',
  props: {
    targetKcId: { type: Number, required: true },
    problemId: { type: Number, default: null }
  },
  emits: ['close'],
  data () {
    return {
      started: false,
      completed: false,
      submitting: false,
      sessionId: null,
      misconception: '',
      explanation: '',
      score: 0,
      feedback: '',
      followupQuestion: null
    }
  },
  methods: {
    async startSession () {
      try {
        const res = await api.startTeachAiSession({
          target_kc_id: this.targetKcId,
          problem_id: this.problemId
        })
        const d = res.data.data
        this.sessionId = d.session_id
        this.misconception = d.ai_misconception
        this.started = true
      } catch {
        // silent
      }
    },
    async submitExplanation () {
      if (this.submitting) return
      this.submitting = true
      try {
        const res = await api.submitTeachAiExplanation(this.sessionId, {
          explanation: this.explanation.trim()
        })
        const d = res.data.data
        this.score = d.grader_score
        this.feedback = d.grader_feedback
        this.followupQuestion = d.ai_followup_question || null
        this.completed = true
      } catch {
        // silent
      } finally {
        this.submitting = false
      }
    },
    addHint (prefix) {
      this.explanation += (this.explanation ? '\n' : '') + prefix
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.ta-card {
  width: 540px;
  max-width: 100%;
  background: #fff;
  border-radius: @l99-radius-md;
  box-shadow: @l99-shadow-2;
  padding: @l99-sp-6;
}

.ta-card__invite {
  text-align: center;
  padding: @l99-sp-4;
}
.ta-card__intro {
  font-size: @l99-fs-md;
  color: @l99-neutral-700;
  margin: @l99-sp-4 0;
  line-height: 1.6;
}
.ta-card__start-btn {
  padding: @l99-sp-3 @l99-sp-6;
  background: @l99-primary;
  color: #fff;
  border: none;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-md;
  font-weight: 500;
  cursor: pointer;
  transition: opacity @l99-dur-fast @l99-ease;
  &:hover { opacity: 0.9; }
}

.ta-card__ai-label {
  font-size: @l99-fs-xs;
  color: @l99-neutral-500;
  display: block;
  margin-bottom: @l99-sp-1;
}
.ta-card__quote {
  margin: 0 0 @l99-sp-3;
  padding: @l99-sp-3 @l99-sp-4;
  border-left: 3px solid @l99-accent;
  background: fade(@l99-accent, 6%);
  border-radius: 0 @l99-radius-sm @l99-radius-sm 0;
  font-size: @l99-fs-md;
  color: @l99-neutral-900;
  line-height: 1.6;
}
.ta-card__prompt {
  font-size: @l99-fs-sm;
  color: @l99-neutral-500;
  margin: 0 0 @l99-sp-3;
}
.ta-card__textarea {
  width: 100%;
  padding: @l99-sp-3;
  border: 1px solid @l99-neutral-200;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-sm;
  font-family: @l99-font-sans;
  line-height: 1.7;
  resize: vertical;
  &:focus { outline: none; border-color: @l99-primary; box-shadow: 0 0 0 2px rgba(15,76,129,0.08); }
}
.ta-card__hints {
  display: flex;
  gap: @l99-sp-2;
  margin: @l99-sp-2 0 @l99-sp-3;
}
.ta-card__hint-chip {
  padding: 2px @l99-sp-2;
  background: @l99-neutral-100;
  border-radius: 12px;
  font-size: @l99-fs-xs;
  color: @l99-neutral-700;
  cursor: pointer;
  &:hover { background: @l99-primary-soft; color: @l99-primary; }
}
.ta-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ta-card__char-count { font-size: @l99-fs-xs; color: @l99-neutral-500; }
.ta-card__submit-btn {
  padding: @l99-sp-2 @l99-sp-5;
  background: @l99-primary;
  color: #fff;
  border: none;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-sm;
  cursor: pointer;
  &:hover { opacity: 0.9; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
}

.ta-card__result { text-align: center; }
.ta-card__score-ring {
  margin: 0 auto @l99-sp-4;
}
.ta-card__score-num {
  display: block;
  font-size: @l99-fs-3xl;
  font-weight: 700;
  color: @l99-primary;
  font-family: @l99-font-mono;
}
.ta-card__score-label {
  font-size: @l99-fs-xs;
  color: @l99-neutral-500;
}
.ta-card__feedback {
  font-size: @l99-fs-md;
  color: @l99-neutral-700;
  line-height: 1.6;
  margin: 0 0 @l99-sp-4;
}
.ta-card__followup {
  background: @l99-neutral-100;
  border-radius: @l99-radius-sm;
  padding: @l99-sp-3 @l99-sp-4;
}
.ta-card__followup-q {
  font-size: @l99-fs-sm;
  color: @l99-neutral-700;
  margin: 0 0 @l99-sp-2;
  font-style: italic;
}
.ta-card__continue-btn, .ta-card__done-btn {
  padding: @l99-sp-2 @l99-sp-4;
  border: 1px solid @l99-primary;
  border-radius: @l99-radius-sm;
  background: @l99-primary-soft;
  color: @l99-primary;
  font-size: @l99-fs-sm;
  cursor: pointer;
  &:hover { background: @l99-primary; color: #fff; }
}
</style>

<template>
  <div class="tc-panel" role="region" aria-label="与孪生对话">
    <h3 class="tc-panel__title">想聊点什么？</h3>

    <div v-if="quickQuestions.length > 0 && messages.length === 0" class="tc-quick">
      <button
        v-for="q in quickQuestions"
        :key="q.id"
        type="button"
        class="tc-quick__btn"
        @click="askQuestion(q.text)"
      >{{ q.text }}</button>
    </div>

    <div class="tc-messages" ref="messagesEl">
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        :class="['tc-msg', `tc-msg--${msg.role}`]"
      >
        <div class="tc-msg__bubble">{{ msg.text }}</div>
      </div>
    </div>

    <div class="tc-input">
      <input
        v-model="inputText"
        type="text"
        class="tc-input__field"
        placeholder="随便聊聊你的学习..."
        maxlength="500"
        @keydown.enter="sendMessage"
      />
      <button
        type="button"
        class="tc-input__send"
        :disabled="!inputText.trim() || sending"
        aria-label="发送"
        @click="sendMessage"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
      </button>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'TwinChatPanel',
  data () {
    return {
      quickQuestions: [],
      messages: [],
      inputText: '',
      sending: false
    }
  },
  mounted () {
    this.loadQuickQuestions()
  },
  methods: {
    async loadQuickQuestions () {
      try {
        const res = await api.getTwinQuickQuestions()
        this.quickQuestions = res.data.data || []
      } catch {
        this.quickQuestions = []
      }
    },
    askQuestion (text) {
      this.inputText = text
      this.sendMessage()
    },
    async sendMessage () {
      const question = this.inputText.trim()
      if (!question || this.sending) return
      this.messages.push({ role: 'user', text: question })
      this.inputText = ''
      this.sending = true
      this.$nextTick(() => this.scrollToBottom())

      try {
        const res = await api.askTwin({ question })
        const answer = res.data.data.answer || '我暂时没法回答这个问题'
        this.messages.push({ role: 'twin', text: answer })
      } catch {
        this.messages.push({ role: 'twin', text: '出错了，请稍后再试' })
      } finally {
        this.sending = false
        this.$nextTick(() => this.scrollToBottom())
      }
    },
    scrollToBottom () {
      const el = this.$refs.messagesEl
      if (el) el.scrollTop = el.scrollHeight
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.tc-panel {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-5;
  display: flex;
  flex-direction: column;
  height: 480px;

  &__title {
    font-size: @l99-fs-lg;
    font-weight: 600;
    color: @l99-neutral-900;
    margin: 0 0 @l99-sp-4;
  }
}

.tc-quick {
  display: flex;
  flex-wrap: wrap;
  gap: @l99-sp-2;
  margin-bottom: @l99-sp-4;
  &__btn {
    padding: @l99-sp-2 @l99-sp-3;
    border: 1px solid @l99-primary;
    border-radius: 20px;
    background: @l99-primary-soft;
    color: @l99-primary;
    font-size: @l99-fs-sm;
    cursor: pointer;
    transition: background @l99-dur-fast @l99-ease;
    &:hover { background: darken(@l99-primary-soft, 5%); }
  }
}

.tc-messages {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: @l99-sp-3;
  margin-bottom: @l99-sp-4;
  padding-right: @l99-sp-2;
}

.tc-msg {
  display: flex;
  &--user { justify-content: flex-end; }
  &--twin { justify-content: flex-start; }
  &__bubble {
    max-width: 75%;
    padding: @l99-sp-3 @l99-sp-4;
    border-radius: @l99-radius-md;
    font-size: @l99-fs-sm;
    line-height: 1.6;
    white-space: pre-wrap;
  }
  &--user &__bubble {
    background: @l99-primary;
    color: #fff;
    border-bottom-right-radius: @l99-radius-sm;
  }
  &--twin &__bubble {
    background: @l99-neutral-100;
    color: @l99-neutral-900;
    border-bottom-left-radius: @l99-radius-sm;
  }
}

.tc-input {
  display: flex;
  gap: @l99-sp-2;
  &__field {
    flex: 1;
    padding: @l99-sp-2 @l99-sp-3;
    border: 1px solid @l99-neutral-200;
    border-radius: @l99-radius-sm;
    font-size: @l99-fs-sm;
    &:focus { outline: none; border-color: @l99-primary; }
  }
  &__send {
    width: 36px;
    height: 36px;
    border-radius: @l99-radius-sm;
    background: @l99-primary;
    color: #fff;
    border: none;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    &:hover { opacity: 0.9; }
    &:disabled { opacity: 0.4; cursor: not-allowed; }
  }
}
</style>

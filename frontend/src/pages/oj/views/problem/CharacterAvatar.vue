<template>
  <div v-if="currentChar" class="char-avatar-strip" :style="stripStyle" @click="handleCharClick">
    <div class="char-sprite-wrap" :class="{ 'is-thinking': isThinking }">
      <transition name="char-expr-blend">
        <img
          :key="currentExpression"
          :src="spriteSrc"
          :alt="currentChar.name"
          class="char-sprite"
          draggable="false"
          @error="handleImgError"
        />
      </transition>
      <div class="char-glow" :style="glowStyle"></div>
    </div>
    <div class="char-right">
      <div class="char-info">
        <span class="char-name" :style="{ color: currentChar.color }">{{ currentChar.name }}</span>
        <span class="char-role">{{ currentChar.role }}</span>
      </div>
      <transition name="bubble-pop">
        <div v-if="speechBubble" class="char-speech" :key="speechBubble">
          <span class="speech-text">{{ speechBubble }}</span>
          <div class="speech-arrow" :style="{ borderTopColor: currentChar.colorLight }"></div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script>
import { getCharacter, getSpritePath, getExpressionForEvent, getCharacterForCardType } from './characterConfig'

const IDLE_LINES = {
  nene: ['有什么不懂的可以问我哦～', '我在这里陪着你呢', '别急，慢慢来～', '需要帮忙就说一声吧'],
  yoshino: ['……有问题就问', '代码要写规范', '我看着呢', '别浪费时间'],
  ayase: ['加油加油！', '我也在做题呢！', '不会比我快的！', '嘿嘿，有意思'],
  kanna: ['……嗯', '……在看', '……', '安静地写吧'],
  murasame: ['切，继续', '别停下来', '……有进步', '这题不难']
}

const EVENT_LINES = {
  student_ac: {
    nene: '太棒了！通过啦～',
    yoshino: '……还行',
    ayase: '耶！AC了！',
    kanna: '……通过了',
    murasame: '切，算你行'
  },
  student_wa: {
    nene: '没关系，再试试看～',
    yoshino: '看看哪里错了',
    ayase: '啊！错了！',
    kanna: '……再想想',
    murasame: '这都能错？'
  },
  student_ce: {
    nene: '编译错误呢，检查一下语法吧',
    yoshino: '语法错误，注意细节',
    ayase: '诶？编译不过！',
    kanna: '……语法',
    murasame: '基础不牢'
  },
  student_tle: {
    nene: '超时了呢，想想有没有更快的方法？',
    yoshino: '时间复杂度不够',
    ayase: '太慢了！',
    kanna: '……优化一下',
    murasame: '效率太低'
  },
  student_submit: {
    nene: '让我看看……',
    yoshino: '正在审查',
    ayase: '提交了！紧张！',
    kanna: '……',
    murasame: '看看结果'
  },
  thinking: {
    nene: '让我想想怎么帮你……',
    yoshino: '正在分析',
    ayase: '等等等等让我看看！',
    kanna: '……思考中',
    murasame: '……'
  }
}

export default {
  name: 'CharacterAvatar',
  props: {
    cardType: { type: String, default: '' },
    studentEvent: { type: String, default: '' },
    overrideCharacter: { type: String, default: '' }
  },
  data () {
    return {
      imgFailed: false,
      idleTimer: null,
      blinkTimer: null,
      bubbleTimer: null,
      transientExpression: null,
      speechBubble: '',
      clickCount: 0
    }
  },
  computed: {
    activeCharId () {
      if (this.overrideCharacter) return this.overrideCharacter
      return getCharacterForCardType(this.cardType) || 'nene'
    },
    currentChar () {
      return getCharacter(this.activeCharId)
    },
    currentExpression () {
      if (this.transientExpression) return this.transientExpression
      if (this.studentEvent) {
        return getExpressionForEvent(this.activeCharId, this.studentEvent)
      }
      return getExpressionForEvent(this.activeCharId, 'idle')
    },
    isThinking () {
      return this.studentEvent === 'student_submit' || this.currentExpression === 'thinking' || this.currentExpression === 'absorbed'
    },
    spriteSrc () {
      if (this.imgFailed) return ''
      return getSpritePath(this.activeCharId, this.currentExpression)
    },
    stripStyle () {
      if (!this.currentChar) return {}
      return {
        '--char-accent': this.currentChar.color,
        '--char-accent-light': this.currentChar.colorLight
      }
    },
    glowStyle () {
      if (!this.currentChar) return {}
      return { background: `radial-gradient(ellipse at center, ${this.currentChar.color}33 0%, transparent 70%)` }
    }
  },
  watch: {
    studentEvent (newEvent) {
      if (!newEvent) return
      this.transientExpression = getExpressionForEvent(this.activeCharId, newEvent)
      this.showEventBubble(newEvent)
      clearTimeout(this.idleTimer)
      this.idleTimer = setTimeout(() => {
        this.transientExpression = null
      }, 3000)
    },
    activeCharId () {
      this.imgFailed = false
      this.transientExpression = null
      this.speechBubble = ''
      this.showGreeting()
    }
  },
  mounted () {
    this.startIdleBlink()
    this.startIdleBubble()
    this.showGreeting()
  },
  beforeUnmount () {
    clearTimeout(this.idleTimer)
    clearInterval(this.blinkTimer)
    clearTimeout(this.bubbleTimer)
    clearInterval(this._idleBubbleTimer)
  },
  methods: {
    handleImgError () {
      this.imgFailed = true
    },
    handleCharClick () {
      this.clickCount++
      const lines = IDLE_LINES[this.activeCharId] || IDLE_LINES.nene
      const pick = lines[this.clickCount % lines.length]
      this.showBubble(pick, 3000)
      this.transientExpression = getExpressionForEvent(this.activeCharId, 'greeting')
      clearTimeout(this.idleTimer)
      this.idleTimer = setTimeout(() => {
        this.transientExpression = null
      }, 2000)
    },
    showGreeting () {
      const greetings = {
        nene: '你好呀～有什么需要帮忙的吗？',
        yoshino: '……有问题就问吧',
        ayase: '嘿！准备好了吗？',
        kanna: '……来了',
        murasame: '说吧，什么问题'
      }
      setTimeout(() => {
        this.showBubble(greetings[this.activeCharId] || greetings.nene, 4000)
      }, 500)
    },
    showEventBubble (event) {
      const lines = EVENT_LINES[event]
      if (!lines) return
      const line = lines[this.activeCharId] || lines.nene
      if (line) this.showBubble(line, 4000)
    },
    showBubble (text, duration) {
      this.speechBubble = text
      clearTimeout(this.bubbleTimer)
      this.bubbleTimer = setTimeout(() => {
        this.speechBubble = ''
      }, duration || 3000)
    },
    startIdleBlink () {
      this.blinkTimer = setInterval(() => {
        if (this.transientExpression || this.studentEvent) return
        const char = this.currentChar
        if (!char) return
        const blinkExprs = char.expressions.filter(e => e !== 'normal' && e !== getExpressionForEvent(this.activeCharId, 'idle'))
        if (blinkExprs.length === 0) return
        const pick = blinkExprs[Math.floor(Math.random() * blinkExprs.length)]
        this.transientExpression = pick
        setTimeout(() => {
          if (this.transientExpression === pick) {
            this.transientExpression = null
          }
        }, 1200)
      }, 8000 + Math.random() * 4000)
    },
    startIdleBubble () {
      this._idleBubbleTimer = setInterval(() => {
        if (this.speechBubble || this.studentEvent) return
        if (Math.random() > 0.7) return
        const lines = IDLE_LINES[this.activeCharId] || IDLE_LINES.nene
        const pick = lines[Math.floor(Math.random() * lines.length)]
        this.showBubble(pick, 4000)
      }, 10000 + Math.random() * 5000)
    }
  }
}
</script>

<style lang="less" scoped>
.char-avatar-strip {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 12px;
  margin: 0 8px 4px;
  border-radius: 12px;
  background: var(--char-accent-light, rgba(244,194,208,0.12));
  border: 1px solid color-mix(in srgb, var(--char-accent, #F4C2D0) 30%, transparent);
  transition: background 0.5s ease, border-color 0.5s ease;
  user-select: none;
  cursor: pointer;
  overflow: visible;

  &:hover .char-sprite-wrap {
    transform: scale(1.03);
  }

  &:active .char-sprite-wrap {
    transform: scale(0.97);
  }
}

.char-sprite-wrap {
  position: relative;
  width: 64px;
  height: 82px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 10px;
  transition: transform 0.2s ease;
  animation: char-breathe 4s ease-in-out infinite;

  &.is-thinking {
    animation: char-think-bob 0.8s ease-in-out infinite;
  }
}

.char-sprite {
  position: absolute;
  top: 0;
  left: 0;
  width: 64px;
  height: 82px;
  max-width: 64px;
  max-height: 82px;
  object-fit: cover;
  object-position: top center;
  display: block;
  filter: drop-shadow(0 2px 8px rgba(0,0,0,0.15));
}

.char-glow {
  position: absolute;
  bottom: -4px;
  left: -10%;
  width: 120%;
  height: 24px;
  pointer-events: none;
  opacity: 0.6;
}

.char-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 2px;
}

.char-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.char-name {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.5s ease;
}

.char-role {
  font-size: 11px;
  color: var(--text-secondary, #8b8fa3);
  line-height: 1.2;
}

.char-speech {
  position: relative;
  padding: 5px 10px;
  border-radius: 8px;
  background: var(--char-accent-light, rgba(244,194,208,0.15));
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-primary, #303133);
  max-width: 220px;
  word-break: break-word;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.speech-text {
  display: block;
}

.char-expr-blend-enter-active {
  transition: opacity 0.5s ease;
}
.char-expr-blend-leave-active {
  transition: opacity 0.4s ease;
  position: absolute;
  top: 0;
  left: 0;
  width: 64px;
  height: 82px;
}
.char-expr-blend-enter-from {
  opacity: 0;
}
.char-expr-blend-leave-to {
  opacity: 0;
}

.bubble-pop-enter-active {
  transition: opacity 0.3s ease, transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.bubble-pop-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.bubble-pop-enter-from {
  opacity: 0;
  transform: translateY(6px) scale(0.9);
}
.bubble-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.95);
}

@keyframes char-breathe {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}

@keyframes char-think-bob {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  25% { transform: translateY(-3px) rotate(-1deg); }
  75% { transform: translateY(-1px) rotate(1deg); }
}
</style>

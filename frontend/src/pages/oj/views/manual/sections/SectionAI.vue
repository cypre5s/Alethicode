<template>
  <section id="ai" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="4" size="md" :rotate="-4" />
      <div>
        <span class="manual-section__kicker">05 · AI</span>
        <h2>智能辅助说明</h2>
        <p>5 位角色背后是不同分工的 AI 助手。它们的目标是让你看懂自己的题，不是替你写。</p>
      </div>
    </header>

    <div class="ai-grid">
      <article
        v-for="ch in characters"
        :key="ch.id"
        class="ai-card"
        :class="{ 'is-expanded': expandedId === ch.id }"
        @click="toggleExpand(ch.id)"
      >
        <div class="ai-card__header">
          <figure class="ai-card__avatar" :style="{ '--ch-color': ch.color }">
            <img :src="ch.avatar" :alt="ch.name" loading="lazy" @error="onAvatarError($event, ch)">
            <span v-if="ch._avatarFailed" class="ai-card__initial">{{ ch.initial }}</span>
          </figure>
          <div class="ai-card__summary">
            <h3>{{ ch.name }}</h3>
            <p class="ai-card__duty">{{ ch.duty }}</p>
            <p class="ai-card__when"><strong>什么时候用：</strong>{{ ch.when }}</p>
          </div>
          <span class="ai-card__expand-icon">{{ expandedId === ch.id ? '▲' : '▼' }}</span>
        </div>

        <transition name="ai-detail">
          <div v-if="expandedId === ch.id" class="ai-card__detail">
            <div class="ai-card__howto">
              <h4>使用步骤</h4>
              <ol>
                <li v-for="(step, i) in ch.howTo" :key="i">{{ step }}</li>
              </ol>
            </div>
            <div v-if="ch.example" class="ai-card__example">
              <h4>示例场景</h4>
              <p>{{ ch.example }}</p>
            </div>
          </div>
        </transition>
      </article>
    </div>

    <div class="ai-qa-guide" id="qa-guide">
      <h3 class="ai-qa-guide__title">{{ qaGuide.title }}</h3>
      <p class="ai-qa-guide__intro">{{ qaGuide.intro }}</p>

      <div class="ai-qa-guide__steps">
        <div v-for="s in qaGuide.steps" :key="s.step" class="ai-qa-step">
          <span class="ai-qa-step__num">{{ s.step }}</span>
          <div>
            <strong>{{ s.title }}</strong>
            <p>{{ s.desc }}</p>
          </div>
        </div>
      </div>

      <div class="ai-qa-guide__tips">
        <h4>使用技巧</h4>
        <ul>
          <li v-for="(tip, i) in qaGuide.tips" :key="i">{{ tip }}</li>
        </ul>
      </div>
    </div>

    <div class="ai-warning">
      <strong>记住：</strong>AI 给的引导和分析有时会错。写完代码自己读一遍，再用脑子跑一遍样例输入，比盲从 AI 安全得多。
    </div>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import { AI_CHARACTERS, QA_GUIDE } from '../manualContent.js'
import Atropos from 'atropos'
import 'atropos/css'

export default {
  name: 'SectionAI',
  components: { ManualNaiwaSticker },
  data () {
    return {
      characters: AI_CHARACTERS.map(ch => ({ ...ch, _avatarFailed: false })),
      qaGuide: QA_GUIDE,
      expandedId: null,
      atroposInstances: []
    }
  },
  mounted () {
    this.$nextTick(() => this.initAtropos())
  },
  beforeUnmount () {
    this.atroposInstances.forEach(a => { try { a.destroy() } catch (e) { /* noop */ } })
  },
  methods: {
    onAvatarError (event, ch) {
      event.target.style.display = 'none'
      ch._avatarFailed = true
    },
    toggleExpand (id) {
      this.expandedId = this.expandedId === id ? null : id
    },
    initAtropos () {
      const cards = this.$el.querySelectorAll('.ai-card__header')
      cards.forEach(card => {
        const wrapper = document.createElement('div')
        wrapper.className = 'atropos'
        wrapper.innerHTML = '<div class="atropos-scale"><div class="atropos-rotate"><div class="atropos-inner"></div></div></div>'
        const inner = wrapper.querySelector('.atropos-inner')
        const parent = card.parentElement
        if (!parent) return

        const avatarFig = card.querySelector('.ai-card__avatar')
        if (avatarFig) {
          avatarFig.setAttribute('data-atropos-offset', '5')
        }

        try {
          const instance = Atropos({
            el: parent,
            activeOffset: 30,
            shadowScale: 0.92,
            rotateXMax: 8,
            rotateYMax: 8,
            duration: 400,
            shadow: false,
            highlight: false
          })
          if (instance) this.atroposInstances.push(instance)
        } catch (e) {
          // Atropos requires specific DOM structure; silently skip if not compatible
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.ai-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;

  &:hover {
    box-shadow: var(--shadow-sm);
    border-color: var(--warm-primary);
  }

  &.is-expanded {
    border-color: var(--warm-primary);
    box-shadow: var(--shadow-md);
  }
}

.ai-card__header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
}

.ai-card__avatar {
  margin: 0;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--bg-panel);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;

  img { width: 100%; height: 100%; object-fit: cover; object-position: top center; }
}

.ai-card__initial {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
  color: #fff;
  background: var(--ch-color, var(--warm-primary));
  border-radius: 50%;
}

.ai-card__summary {
  flex: 1;
  min-width: 0;

  h3 {
    margin: 0 0 4px;
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.ai-card__expand-icon {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-disabled);
  transition: transform 0.2s ease;
}

.ai-card__duty {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.55;
  text-wrap: pretty;
}

.ai-card__when {
  margin: 0;
  font-size: 12px;
  color: var(--text-disabled);
  line-height: 1.5;

  strong { color: var(--text-secondary); font-weight: 600; }
}

.ai-card__detail {
  padding: 0 16px 16px;
  border-top: 1px dashed var(--border-color);
  margin: 0 16px;
  padding-top: 14px;
}

.ai-card__howto {
  margin-bottom: 12px;

  h4 {
    margin: 0 0 8px;
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);
  }

  ol {
    margin: 0;
    padding-left: 20px;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.7;

    li { margin-bottom: 4px; }
  }
}

.ai-card__example {
  background: var(--warm-bg-subtle);
  border-radius: var(--radius-sm);
  padding: 12px 14px;
  border: 1px dashed var(--border-warm);

  h4 {
    margin: 0 0 6px;
    font-size: 12px;
    font-weight: 700;
    color: var(--warm-primary-strong);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
  }
}

.ai-detail-enter-active,
.ai-detail-leave-active {
  transition: max-height 0.3s ease, opacity 0.25s ease;
  overflow: hidden;
}
.ai-detail-enter-from,
.ai-detail-leave-to {
  max-height: 0;
  opacity: 0;
}
.ai-detail-enter-to,
.ai-detail-leave-from {
  max-height: 500px;
  opacity: 1;
}

.ai-qa-guide {
  margin-top: 32px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 24px;
}

.ai-qa-guide__title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.ai-qa-guide__intro {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
  text-wrap: pretty;
}

.ai-qa-guide__steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 20px;
}

.ai-qa-step {
  display: flex;
  gap: 14px;
  align-items: flex-start;

  strong {
    display: block;
    font-size: 14px;
    color: var(--text-primary);
    margin-bottom: 4px;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
  }
}

.ai-qa-step__num {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--warm-grad-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
}

.ai-qa-guide__tips {
  background: var(--warm-bg-subtle);
  border-radius: var(--radius-md);
  padding: 16px 18px;
  border: 1px dashed var(--border-warm);

  h4 {
    margin: 0 0 8px;
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);
  }

  ul {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.7;

    li { margin-bottom: 4px; }
  }
}

.ai-warning {
  margin-top: 18px;
  padding: 14px 18px;
  background: rgba(245, 158, 11, 0.08);
  border-left: 3px solid var(--color-warning);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.6;
  text-wrap: pretty;

  strong { color: var(--color-warning); margin-right: 4px; }
}
</style>

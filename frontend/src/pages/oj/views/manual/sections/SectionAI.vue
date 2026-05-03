<template>
  <section id="ai" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="4" size="md" :rotate="-4" />
      <div>
        <span class="manual-section__kicker">02 · AI Tutor</span>
        <h2>AI 导学助手</h2>
        <p>AI 不是替你写代码，是和你一起想清楚下一步。下面分四段说清它能做什么、5 位角色各管什么、怎么问最有效、哪些问法会反过来害自己。</p>
      </div>
    </header>

    <div class="ai-block">
      <h3 class="ai-block__title">能帮你做什么</h3>
      <ul class="ai-cap-grid">
        <li v-for="cap in capabilities" :key="cap.id" class="ai-cap-card">
          <span class="ai-cap-card__dot" aria-hidden="true">·</span>
          <strong>{{ cap.title }}</strong>
          <p>{{ cap.desc }}</p>
        </li>
      </ul>
    </div>

    <div class="ai-block">
      <h3 class="ai-block__title">5 位角色 · 各管一段学习节奏</h3>
      <p class="ai-block__lead">点开角色卡查看适用场景与使用步骤；任何角色都不会直接给答案，而是把你的下一步说清楚。</p>
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
              <h4>{{ ch.name }}</h4>
              <p class="ai-card__duty">{{ ch.duty }}</p>
              <p class="ai-card__when"><strong>什么时候用：</strong>{{ ch.when }}</p>
            </div>
            <span class="ai-card__expand-icon" aria-hidden="true">{{ expandedId === ch.id ? '−' : '+' }}</span>
          </div>

          <transition name="ai-detail">
            <div v-if="expandedId === ch.id" class="ai-card__detail">
              <div class="ai-card__howto">
                <h5>使用步骤</h5>
                <ol>
                  <li v-for="(step, i) in ch.howTo" :key="i">{{ step }}</li>
                </ol>
              </div>
              <div v-if="ch.example" class="ai-card__example">
                <h5>示例场景</h5>
                <p>{{ ch.example }}</p>
              </div>
            </div>
          </transition>
        </article>
      </div>
    </div>

    <div class="ai-block">
      <h3 class="ai-block__title">推荐这样问 AI</h3>
      <p class="ai-block__lead">直接复制下面的提问模板，按需要把关键词替换成你正在做的题或正在看的报错。</p>
      <div class="ai-prompt-grid">
        <ManualPromptCard
          v-for="item in recommendedPrompts"
          :key="item.id"
          :label="`${item.label} · ${item.when}`"
          :prompt="item.prompt"
          :note="`为什么这样问：${item.why}`"
        />
      </div>
    </div>

    <div class="ai-block">
      <h3 class="ai-block__title">不推荐这样问</h3>
      <ul class="ai-discouraged">
        <li v-for="item in discouragedPrompts" :key="item.id">
          <code>{{ item.label }}</code>
          <p>{{ item.why }}</p>
        </li>
      </ul>
    </div>

    <div class="ai-warning">
      <strong>记住：</strong>AI 给的引导和分析有时会错。写完代码自己读一遍，再用脑子跑一遍样例输入，比盲从 AI 安全得多。
    </div>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import ManualPromptCard from '../ManualPromptCard.vue'
import {
  AI_CHARACTERS,
  AI_CAPABILITIES,
  RECOMMENDED_PROMPTS,
  DISCOURAGED_PROMPTS
} from '../manualContent.js'

export default {
  name: 'SectionAI',
  components: { ManualNaiwaSticker, ManualPromptCard },
  data () {
    return {
      characters: AI_CHARACTERS.map(ch => ({ ...ch, _avatarFailed: false })),
      capabilities: AI_CAPABILITIES,
      recommendedPrompts: RECOMMENDED_PROMPTS,
      discouragedPrompts: DISCOURAGED_PROMPTS,
      expandedId: null
    }
  },
  methods: {
    onAvatarError (event, ch) {
      event.target.style.display = 'none'
      ch._avatarFailed = true
    },
    toggleExpand (id) {
      this.expandedId = this.expandedId === id ? null : id
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.ai-block {
  margin-top: 32px;

  &:first-of-type { margin-top: 4px; }
}

.ai-block__title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.ai-block__lead {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
  max-width: 62ch;
  text-wrap: pretty;
}

.ai-cap-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.ai-cap-card {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: 8px 12px;
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  transition: border-color 0.18s ease;

  &:hover { border-color: var(--text-disabled); }

  strong {
    grid-column: 2;
    font-size: 14px;
    color: var(--text-primary);
  }

  p {
    grid-column: 2;
    margin: 0;
    font-size: 12.5px;
    color: var(--text-secondary);
    line-height: 1.6;
    text-wrap: pretty;
  }
}

.ai-cap-card__dot {
  grid-row: 1 / span 2;
  font-family: var(--font-mono);
  font-size: 18px;
  color: var(--text-disabled);
  line-height: 1;
}

.ai-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.18s ease;

  &:hover { border-color: var(--text-disabled); }

  &.is-expanded { border-color: var(--text-secondary); }
}

.ai-card__header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
}

.ai-card__avatar {
  margin: 0;
  width: 44px;
  height: 44px;
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
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  background: var(--ch-color, var(--text-secondary));
  border-radius: 50%;
}

.ai-card__summary {
  flex: 1;
  min-width: 0;

  h4 {
    margin: 0 0 4px;
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.ai-card__expand-icon {
  flex-shrink: 0;
  font-family: var(--font-mono);
  font-size: 18px;
  color: var(--text-disabled);
  width: 18px;
  text-align: center;
}

.ai-card__duty {
  margin: 0 0 4px;
  font-size: 12.5px;
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
  border-top: 1px dashed var(--border-color);
  padding: 12px 16px 16px;
}

.ai-card__howto {
  margin-bottom: 12px;

  h5 {
    margin: 0 0 6px;
    font-size: 12px;
    font-weight: 700;
    color: var(--text-primary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  ol {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.7;

    li { margin-bottom: 2px; }
  }
}

.ai-card__example {
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 10px 12px;

  h5 {
    margin: 0 0 4px;
    font-size: 11px;
    font-weight: 700;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  p {
    margin: 0;
    font-size: 12.5px;
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
  max-height: 600px;
  opacity: 1;
}

.ai-prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.ai-discouraged {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;

  li {
    border-left: 3px solid var(--color-warning, #f59e0b);
    background: rgba(245, 158, 11, 0.05);
    border-radius: var(--radius-sm);
    padding: 12px 16px;

    code {
      font-family: var(--font-mono);
      font-size: 13px;
      font-weight: 600;
      color: var(--text-primary);
      background: transparent;
      padding: 0;
    }

    p {
      margin: 4px 0 0;
      font-size: 12.5px;
      color: var(--text-secondary);
      line-height: 1.6;
      text-wrap: pretty;
    }
  }
}

.ai-warning {
  margin-top: 28px;
  padding: 14px 18px;
  background: rgba(245, 158, 11, 0.08);
  border-left: 3px solid var(--color-warning, #f59e0b);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.6;
  text-wrap: pretty;

  strong { color: var(--color-warning, #f59e0b); margin-right: 4px; }
}
</style>

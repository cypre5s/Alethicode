<template>
  <section id="context" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="2" size="md" :rotate="3" />
      <div>
        <span class="manual-section__kicker">03 · Context</span>
        <h2>@ 上下文引用</h2>
        <p>在对话框输入 <code class="inline-code">@</code> 会弹出可引用的卡片清单。把当前题、刚出的错误诊断、或某张课件塞进对话，AI 才能针对你这次的具体内容回答。</p>
      </div>
    </header>

    <div class="ctx-block">
      <h3 class="ctx-block__title">@ 是什么</h3>
      <div class="ctx-explainer">
        <div class="ctx-explainer__copy">
          <p>
            像 Notion / Cursor / Slack 里那样：在 AI 对话输入框里键入 <code class="inline-code">@</code>，会浮出一个可引用项的菜单。
            选中一项后，文本里会插入一个 token（如 <code class="inline-code">@last_error</code>），AI 收到时会自动把对应的卡片或文档作为这次对话的"附件"读进上下文。
          </p>
          <p>这是把"我正在看的内容"传给 AI 最准的方式，比反复粘贴文本要靠谱。</p>
        </div>
        <figure class="ctx-explainer__figure" aria-label="@ 引用菜单示意图">
          <svg viewBox="0 0 320 240" role="img" aria-hidden="true">
            <rect x="8" y="160" width="304" height="64" rx="10" fill="var(--bg-card)" stroke="var(--border-color)"/>
            <text x="20" y="188" font-family="var(--font-mono)" font-size="14" fill="var(--text-disabled)">@</text>
            <text x="36" y="188" font-family="var(--font-mono)" font-size="14" fill="var(--text-primary)">page:1.7</text>
            <text x="20" y="210" font-family="var(--font-sans)" font-size="11" fill="var(--text-disabled)">输入 @ 浮出可引用菜单</text>

            <rect x="8" y="8" width="304" height="140" rx="10" fill="var(--bg-card)" stroke="var(--border-color)"/>

            <text x="20" y="26" font-family="var(--font-sans)" font-size="10" font-weight="700" fill="var(--text-disabled)" letter-spacing="0.5">会话卡片</text>
            <rect x="20" y="32" width="280" height="20" rx="4" fill="var(--bg-panel)"/>
            <text x="28" y="46" font-family="var(--font-mono)" font-size="11" fill="var(--text-secondary)">@last_error · 错误诊断</text>

            <line x1="20" y1="62" x2="300" y2="62" stroke="var(--border-color)" stroke-dasharray="2 3"/>
            <text x="20" y="78" font-family="var(--font-sans)" font-size="10" font-weight="700" fill="var(--text-disabled)" letter-spacing="0.5">第 1 章 · 入门.pptx</text>
            <rect x="20" y="84" width="280" height="20" rx="4" fill="var(--bg-panel)"/>
            <text x="28" y="98" font-family="var(--font-mono)" font-size="11" fill="var(--text-secondary)">@page:1.7 · 第 7 页</text>

            <line x1="20" y1="114" x2="300" y2="114" stroke="var(--border-color)" stroke-dasharray="2 3"/>
            <text x="20" y="128" font-family="var(--font-sans)" font-size="10" font-weight="700" fill="var(--text-disabled)" letter-spacing="0.5">第 2 章 · 进阶.pptx</text>
            <rect x="20" y="134" width="280" height="10" rx="3" fill="var(--bg-panel)"/>
          </svg>
        </figure>
      </div>
    </div>

    <div class="ctx-block">
      <h3 class="ctx-block__title">什么时候用 @</h3>
      <ul class="ctx-when-list">
        <li>问报错应该怎么改 → @ 报错诊断卡</li>
        <li>问"我这段代码思路对吗" → @ 当前代码或刚出的卡片</li>
        <li>问课件里某节具体在讲什么 → @ 对应课件</li>
        <li>让 AI 顺着上一轮回答继续展开 → @ 上一次的卡片</li>
        <li>复盘时把之前某张卡片再翻出来讨论 → @card:&lt;id&gt;</li>
      </ul>
    </div>

    <div class="ctx-block">
      <h3 class="ctx-block__title">常用 @ 引用一览</h3>
      <p class="ctx-block__lead">实际可用的 token 与它们对应的卡片类型，都是大小写敏感的。</p>
      <div class="ctx-token-table" role="table" aria-label="可用的 @ 引用 token">
        <div class="ctx-token-row ctx-token-row--head" role="row">
          <span role="columnheader">Token</span>
          <span role="columnheader">中文名</span>
          <span role="columnheader">什么时候用</span>
        </div>
        <div
          v-for="row in tokens"
          :key="row.token"
          class="ctx-token-row"
          role="row"
        >
          <code role="cell">{{ row.token }}</code>
          <span role="cell">{{ row.name }}</span>
          <span role="cell">{{ row.when }}</span>
        </div>
      </div>
    </div>

    <div class="ctx-block">
      <h3 class="ctx-block__title">示例提问</h3>
      <div class="ctx-prompt-grid">
        <ManualPromptCard
          v-for="ex in examples"
          :key="ex.id"
          :label="ex.label"
          :prompt="ex.prompt"
          :note="ex.note"
        />
      </div>
    </div>

    <aside class="ctx-tips">
      <h3>使用建议</h3>
      <ul>
        <li v-for="(tip, i) in tips" :key="i">{{ tip }}</li>
      </ul>
    </aside>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import ManualPromptCard from '../ManualPromptCard.vue'
import {
  CONTEXT_TOKENS,
  CONTEXT_EXAMPLES,
  CONTEXT_TIPS
} from '../manualContent.js'

export default {
  name: 'SectionContext',
  components: { ManualNaiwaSticker, ManualPromptCard },
  data () {
    return {
      tokens: CONTEXT_TOKENS,
      examples: CONTEXT_EXAMPLES,
      tips: CONTEXT_TIPS
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.inline-code {
  font-family: var(--font-mono);
  font-size: 0.92em;
  color: var(--text-primary);
  background: var(--bg-panel);
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.ctx-block {
  margin-top: 32px;

  &:first-of-type { margin-top: 4px; }
}

.ctx-block__title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.ctx-block__lead {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
  max-width: 62ch;
  text-wrap: pretty;
}

.ctx-explainer {
  display: grid;
  grid-template-columns: 1fr minmax(260px, 360px);
  gap: 24px;
  align-items: center;

  p {
    margin: 0 0 12px;
    font-size: 13.5px;
    color: var(--text-secondary);
    line-height: 1.75;
    text-wrap: pretty;

    &:last-child { margin-bottom: 0; }
  }
}

.ctx-explainer__figure {
  margin: 0;
  padding: 12px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);

  svg { width: 100%; height: auto; display: block; }
}

.ctx-when-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 8px;

  li {
    position: relative;
    padding: 10px 14px 10px 26px;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
    text-wrap: pretty;

    &::before {
      content: '→';
      position: absolute;
      left: 12px;
      top: 10px;
      color: var(--text-disabled);
      font-family: var(--font-mono);
      font-size: 13px;
    }
  }
}

.ctx-token-table {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
}

.ctx-token-row {
  display: grid;
  grid-template-columns: 180px 160px 1fr;
  gap: 16px;
  padding: 10px 16px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  border-top: 1px solid var(--border-color);

  &:first-child { border-top: 0; }

  code {
    font-family: var(--font-mono);
    font-size: 12.5px;
    color: var(--text-primary);
    background: var(--bg-panel);
    padding: 1px 6px;
    border-radius: 4px;
    border: 1px solid var(--border-color);
    width: max-content;
    max-width: 100%;
  }
}

.ctx-token-row--head {
  background: var(--bg-panel);
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.ctx-prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.ctx-tips {
  margin-top: 28px;
  padding: 16px 20px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);

  h3 {
    margin: 0 0 8px;
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);
    text-transform: uppercase;
    letter-spacing: 0.6px;
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

@media (max-width: 768px) {
  .ctx-explainer {
    grid-template-columns: 1fr;
  }

  .ctx-token-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>

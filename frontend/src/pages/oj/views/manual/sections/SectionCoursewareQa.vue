<template>
  <section id="qa" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="1" size="md" :rotate="-3" />
      <div>
        <span class="manual-section__kicker">04 · Courseware QA</span>
        <h2>课件问答</h2>
        <p>从课件 PDF 里直接检索答案、对照原文页码。它和「AI 导学助手」分工不同：导学帮你解决"题"，课件问答帮你解决"概念"。</p>
      </div>
    </header>

    <div class="qa-block">
      <h3 class="qa-block__title">是什么</h3>
      <p class="qa-lead">
        课件问答是基于你选择的某份课件 PDF，由 AI 在课件原文里检索并给出答案的工具。
        进入页面后选定一份课件，<strong>之后所有的提问都自动以该课件为唯一检索源</strong>——
        无需在对话里输入 <code class="inline-code">@课件</code> 之类的 token，AI 已经知道你要问哪份课件了。
        优势是回答会附原文页码，可以一键打开 PDF 对照原文。
      </p>
      <aside class="qa-vs">
        <h4>和「AI 导学助手」的区别</h4>
        <dl>
          <div>
            <dt>AI 导学助手</dt>
            <dd>解决具体的"题"——审题、纠错、总结、推荐相似题；@ 引用支持 7 种 <code class="inline-code">@last_*</code> 卡片。</dd>
          </div>
          <div>
            <dt>课件问答</dt>
            <dd>解决"概念" / "知识点"——查定义、比较术语、定位原文页码；上下文 = 进入页面时选定的那份课件。</dd>
          </div>
        </dl>
        <p class="qa-vs__note">
          <strong>规划中</strong>：让 AI 导学助手对话里也能 <code class="inline-code">@课件:&lt;id&gt;</code> 引用某份具体课件，把课件检索结果作为对话 context 塞进 AI 导学助手——目前还没上线，先用「课件问答」入口处理课件相关问题。
        </p>
      </aside>
    </div>

    <div class="qa-block">
      <h3 class="qa-block__title">可以问什么</h3>
      <ul class="qa-scope-list">
        <li v-for="(item, i) in scope" :key="i">{{ item }}</li>
      </ul>
    </div>

    <div class="qa-block">
      <h3 class="qa-block__title">示例提问</h3>
      <div class="qa-prompt-grid">
        <ManualPromptCard
          v-for="prompt in prompts"
          :key="prompt.id"
          :label="prompt.label"
          :prompt="prompt.prompt"
          :note="prompt.note"
        />
      </div>
    </div>

    <div class="qa-block">
      <h3 class="qa-block__title">注意事项</h3>
      <ul class="qa-notes">
        <li v-for="(note, i) in notes" :key="i">{{ note }}</li>
      </ul>
    </div>

    <div class="qa-cta">
      <button type="button" class="qa-cta__btn" @click="openCoursewareQa">
        打开课件问答 →
      </button>
      <p class="qa-cta__hint">未登录会先跳到登录页；登录后可直接选择已发布的课件包。</p>
    </div>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import ManualPromptCard from '../ManualPromptCard.vue'
import {
  COURSEWARE_QA_SCOPE,
  COURSEWARE_QA_PROMPTS,
  COURSEWARE_QA_NOTES
} from '../manualContent.js'

export default {
  name: 'SectionCoursewareQa',
  components: { ManualNaiwaSticker, ManualPromptCard },
  data () {
    return {
      scope: COURSEWARE_QA_SCOPE,
      prompts: COURSEWARE_QA_PROMPTS,
      notes: COURSEWARE_QA_NOTES
    }
  },
  methods: {
    openCoursewareQa () {
      this.$router.push('/language-pack-qa').catch(() => {})
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.qa-block {
  margin-top: 32px;

  &:first-of-type { margin-top: 4px; }
}

.qa-block__title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.qa-lead {
  margin: 0 0 16px;
  font-size: 13.5px;
  color: var(--text-secondary);
  line-height: 1.75;
  max-width: 62ch;
  text-wrap: pretty;

  strong { color: var(--text-primary); font-weight: 600; }
}

.qa-vs {
  padding: 16px 20px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);

  h4 {
    margin: 0 0 10px;
    font-size: 12px;
    font-weight: 700;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.6px;
  }

  dl {
    margin: 0;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 14px;
  }

  dt {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 4px;
  }

  dd {
    margin: 0;
    font-size: 12.5px;
    color: var(--text-secondary);
    line-height: 1.6;
    text-wrap: pretty;
  }
}

.qa-vs__note {
  grid-column: 1 / -1;
  margin: 14px 0 0;
  padding: 10px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.65;
  background: var(--bg-card);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-sm);
  text-wrap: pretty;

  strong {
    color: var(--text-primary);
    font-weight: 700;
    margin-right: 4px;
  }
}

.inline-code {
  font-family: var(--font-mono);
  font-size: 0.92em;
  color: var(--text-primary);
  background: var(--bg-panel);
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.qa-scope-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
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
      content: '·';
      position: absolute;
      left: 14px;
      top: 8px;
      color: var(--text-disabled);
      font-family: var(--font-mono);
      font-size: 16px;
    }
  }
}

.qa-prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.qa-notes {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;

  li {
    border-left: 3px solid var(--color-warning, #f59e0b);
    background: rgba(245, 158, 11, 0.05);
    padding: 10px 14px;
    border-radius: var(--radius-sm);
    font-size: 13px;
    color: var(--text-primary);
    line-height: 1.6;
    text-wrap: pretty;
  }
}

.qa-cta {
  margin-top: 32px;
  padding: 18px 22px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.qa-cta__btn {
  border: 1px solid var(--text-primary);
  background: var(--text-primary);
  color: var(--bg-card);
  font-size: 13.5px;
  font-weight: 600;
  padding: 9px 18px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: opacity 0.18s ease;

  &:hover, &:focus-visible {
    opacity: 0.85;
    outline: none;
  }
}

.qa-cta__hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-disabled);
  line-height: 1.5;
}

@media (max-width: 768px) {
  .qa-vs dl {
    grid-template-columns: 1fr;
  }
}
</style>

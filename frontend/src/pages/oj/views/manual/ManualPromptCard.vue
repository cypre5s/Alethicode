<template>
  <article class="manual-prompt-card" :class="{ 'is-copied': copied }">
    <header class="manual-prompt-card__bar">
      <span class="manual-prompt-card__label">{{ label }}</span>
      <button
        type="button"
        class="manual-prompt-card__copy"
        :aria-label="`复制示例：${label}`"
        @click="copy"
      >
        <span class="manual-prompt-card__copy-icon" aria-hidden="true">{{ copied ? '✓' : '⧉' }}</span>
        <span class="manual-prompt-card__copy-text">{{ copied ? '已复制' : '复制' }}</span>
      </button>
    </header>
    <pre ref="bodyRef" class="manual-prompt-card__body"><code>{{ prompt }}</code></pre>
    <p v-if="note" class="manual-prompt-card__note">{{ note }}</p>
  </article>
</template>

<script>
/**
 * 用于 SectionAI / SectionContext / SectionCoursewareQa 三个章节的"示例提问"卡片。
 *
 * 行为：
 *   - 顶部 label 区放分类标签 + 复制按钮；
 *   - 主体用 monospace block 渲染示例提问文本；
 *   - 复制按钮优先 navigator.clipboard.writeText，失败 fallback 到选中文本 + execCommand('copy')。
 *
 * 不引入新依赖；纯 Vue + scoped less。
 */
export default {
  name: 'ManualPromptCard',
  props: {
    label: { type: String, required: true },
    prompt: { type: String, required: true },
    note: { type: String, default: '' }
  },
  data () {
    return {
      copied: false,
      copiedTimer: null
    }
  },
  beforeUnmount () {
    if (this.copiedTimer) clearTimeout(this.copiedTimer)
  },
  methods: {
    async copy () {
      const text = this.prompt
      let ok = false
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        try {
          await navigator.clipboard.writeText(text)
          ok = true
        } catch {
          ok = false
        }
      }
      if (!ok) {
        ok = this.fallbackCopy()
      }
      if (ok) {
        this.copied = true
        if (this.copiedTimer) clearTimeout(this.copiedTimer)
        this.copiedTimer = setTimeout(() => { this.copied = false }, 1800)
        this.$emit('copy', text)
      }
    },
    fallbackCopy () {
      const node = this.$refs.bodyRef
      if (!node) return false
      const range = document.createRange()
      range.selectNodeContents(node)
      const selection = window.getSelection()
      if (!selection) return false
      selection.removeAllRanges()
      selection.addRange(range)
      try {
        const ok = document.execCommand('copy')
        selection.removeAllRanges()
        return ok
      } catch {
        selection.removeAllRanges()
        return false
      }
    }
  }
}
</script>

<style lang="less" scoped>
.manual-prompt-card {
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    border-color: var(--text-disabled);
  }

  &.is-copied {
    border-color: var(--color-success, #10b981);
  }
}

.manual-prompt-card__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: var(--bg-panel);
  border-bottom: 1px solid var(--border-color);
}

.manual-prompt-card__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.2px;
}

.manual-prompt-card__copy {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: color 0.18s ease, border-color 0.18s ease, background 0.18s ease;

  &:hover, &:focus-visible {
    color: var(--text-primary);
    border-color: var(--text-disabled);
    outline: none;
  }
}

.manual-prompt-card.is-copied .manual-prompt-card__copy {
  color: var(--color-success, #10b981);
  border-color: var(--color-success, #10b981);
}

.manual-prompt-card__copy-icon {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1;
}

.manual-prompt-card__body {
  margin: 0;
  padding: 14px 16px;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  background: transparent;
}

.manual-prompt-card__note {
  margin: 0;
  padding: 10px 16px 14px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  border-top: 1px dashed var(--border-color);
  text-wrap: pretty;
}
</style>

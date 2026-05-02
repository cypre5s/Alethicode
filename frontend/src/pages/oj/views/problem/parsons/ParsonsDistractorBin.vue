<template>
  <div v-if="distractors.length" class="pdb">
    <button
      type="button"
      class="pdb-toggle"
      :aria-expanded="expanded ? 'true' : 'false'"
      aria-controls="parsons-distractor-list"
      @click="expanded = !expanded"
    >
      <span class="pdb-toggle-label">
        <ElIcon :size="14" class="pdb-toggle-icon"><WarningFilled /></ElIcon>
        <span>本次拼装混入了 {{ distractors.length }} 个干扰块</span>
      </span>
      <span class="pdb-meta">
        <span v-if="notebookCount" class="pdb-meta-pill pdb-meta-pill--notebook">
          {{ notebookCount }} 来自你的历史错题
        </span>
        <span v-if="llmCount" class="pdb-meta-pill pdb-meta-pill--llm">
          {{ llmCount }} 来自模型补全
        </span>
        <ElIcon :size="12" class="pdb-toggle-caret">
          <component :is="expanded ? ArrowUp : ArrowDown" />
        </ElIcon>
      </span>
    </button>
    <div
      v-show="expanded"
      id="parsons-distractor-list"
      class="pdb-list"
      role="list"
    >
      <div
        v-for="d in normalizedDistractors"
        :key="d.id"
        class="pdb-item"
        :class="`pdb-item--${d.source}`"
        role="listitem"
      >
        <span class="pdb-source-tag">
          {{ d.source === 'notebook' ? '历史错题' : '模型补全' }}
        </span>
        <code class="pdb-code">{{ d.code }}</code>
        <span v-if="d.kc_hint" class="pdb-kc-hint">{{ d.kc_hint }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import { WarningFilled, ArrowUp, ArrowDown } from '@element-plus/icons-vue'

export default {
  name: 'ParsonsDistractorBin',
  components: { WarningFilled },
  props: {
    distractors: { type: Array, default: () => [] },
    initiallyExpanded: { type: Boolean, default: false }
  },
  data () {
    return {
      expanded: this.initiallyExpanded,
      ArrowUp,
      ArrowDown
    }
  },
  computed: {
    notebookCount () {
      return this.distractors.filter((d) => d && d.source === 'notebook').length
    },
    llmCount () {
      return this.distractors.filter((d) => d && d.source !== 'notebook').length
    },
    normalizedDistractors () {
      return this.distractors.map((d) => ({
        id: d.id,
        code: d.code,
        kc_hint: d.kc_hint || '',
        source: d.source === 'notebook' ? 'notebook' : 'llm'
      }))
    }
  }
}
</script>

<style lang="less" scoped>
.pdb {
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-md);
  background: var(--warm-bg-subtle);
}
.pdb-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 10px var(--space-3);
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--warm-primary-strong);
  font-size: var(--fs-sm);
  font-weight: 600;
  border-radius: var(--radius-md);
  min-height: var(--control-height-lg);

  &:hover {
    background: rgba(99, 102, 241, 0.06);
  }
  &:focus-visible {
    outline: 2px solid var(--warm-primary);
    outline-offset: 2px;
  }
}
.pdb-toggle-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.pdb-toggle-icon {
  color: var(--color-warning);
}
.pdb-meta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.pdb-meta-pill {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  padding: 0 8px;
  border-radius: var(--tag-radius);
  font-size: var(--fs-xs);
  font-weight: 600;
}
.pdb-meta-pill--notebook {
  background: rgba(239, 68, 68, 0.10);
  color: #b91c1c;
  border: 1px solid rgba(239, 68, 68, 0.22);
}
.pdb-meta-pill--llm {
  background: rgba(99, 102, 241, 0.10);
  color: var(--warm-primary-strong);
  border: 1px solid rgba(99, 102, 241, 0.22);
}
.pdb-toggle-caret {
  color: var(--text-secondary);
}
.pdb-list {
  border-top: 1px solid var(--border-warm);
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.pdb-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  border: 1px solid var(--border-default);
}
.pdb-item--notebook {
  border-color: rgba(239, 68, 68, 0.22);
  background: rgba(239, 68, 68, 0.04);
}
.pdb-item--llm {
  border-color: rgba(99, 102, 241, 0.22);
  background: rgba(99, 102, 241, 0.04);
}
.pdb-source-tag {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  padding: 0 8px;
  border-radius: var(--tag-radius);
  font-size: var(--fs-xs);
  font-weight: 600;
  background: var(--bg-panel);
  color: var(--text-secondary);
  border: 1px solid var(--border-default);
  white-space: nowrap;
}
.pdb-item--notebook .pdb-source-tag {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
  border-color: rgba(239, 68, 68, 0.30);
}
.pdb-item--llm .pdb-source-tag {
  background: rgba(99, 102, 241, 0.12);
  color: var(--warm-primary-strong);
  border-color: rgba(99, 102, 241, 0.30);
}
.pdb-code {
  font-family: var(--font-mono);
  font-size: var(--fs-sm);
  color: var(--text-strong);
  background: transparent;
  white-space: pre-wrap;
  word-break: break-word;
  min-width: 0;
  overflow-wrap: anywhere;
}
.pdb-kc-hint {
  font-size: var(--fs-xs);
  color: var(--text-secondary);
  white-space: nowrap;
  font-style: italic;
}

@media (max-width: 720px) {
  .pdb-item {
    grid-template-columns: 1fr;
    gap: var(--space-1);
  }
  .pdb-meta {
    display: none;
  }
}
</style>

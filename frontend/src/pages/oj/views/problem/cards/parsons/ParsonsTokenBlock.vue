<template>
  <div
    :class="[
      'ptb',
      'ptb-state-' + fadingState,
      {
        'ptb-distractor': isDistractor,
        'ptb-dragging': dragging,
        'ptb-misplaced': misplaced
      }
    ]"
    :draggable="!isPlaceholder"
    :tabindex="isPlaceholder ? -1 : 0"
    role="listitem"
    :aria-label="ariaLabel"
    :aria-grabbed="dragging ? 'true' : 'false'"
    :data-block-id="block.id"
    @dragstart="onDragStart"
    @dragend="onDragEnd"
    @keydown="onKeyDown"
  >
    <span class="ptb-indent" v-for="i in block.indent" :key="i" />
    <span v-if="fadingState === 'hidden'" class="ptb-hidden-slot">__&nbsp;{{ block.fade_hint || '关键步骤' }}&nbsp;__</span>
    <span v-else class="ptb-code">{{ block.code }}</span>
    <span v-if="fadingState === 'faded'" class="ptb-fade-hint">提示：{{ block.fade_hint || '关键步骤' }}</span>
    <span
      v-if="effectiveFlaggedReason"
      class="ptb-flag-hint"
      :title="effectiveFlaggedReason"
    >{{ flaggedHintLabel }}</span>
    <span v-if="misplaced" class="ptb-misplaced-tag" aria-hidden="true">!</span>
  </div>
</template>

<script>
export default {
  name: 'ParsonsTokenBlock',
  props: {
    block: { type: Object, required: true },
    isDistractor: { type: Boolean, default: false },
    isPlaceholder: { type: Boolean, default: false },
    misplaced: { type: Boolean, default: false },
    flaggedReason: { type: String, default: '' }
  },
  emits: ['drag-start', 'drag-end', 'keyboard-action'],
  data () {
    return { dragging: false }
  },
  computed: {
    fadingState () {
      if (this.isDistractor) return 'visible'
      return this.block.fading_state || 'visible'
    },
    effectiveFlaggedReason () {
      if (!this.isDistractor) return ''
      return this.flaggedReason || this.block.kc_hint || ''
    },
    flaggedHintLabel () {
      const source = this.block && this.block.source
      return source === 'notebook' ? '历史错题模式' : '可能的陷阱'
    },
    ariaLabel () {
      const code = this.block.code || ''
      const stateLabel = this.fadingState === 'visible'
        ? '可见块'
        : (this.fadingState === 'faded' ? '需补全块' : '完全空白块')
      const prefix = this.isDistractor ? '干扰块：' : stateLabel + '：'
      const suffix = this.misplaced ? '（位置可能不对）' : ''
      return prefix + code + suffix
    }
  },
  methods: {
    onDragStart (event) {
      if (this.isPlaceholder) {
        event.preventDefault()
        return
      }
      this.dragging = true
      this.$emit('drag-start', this.block.id, event)
    },
    onDragEnd (event) {
      this.dragging = false
      this.$emit('drag-end', this.block.id, event)
    },
    onKeyDown (event) {
      if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
        event.preventDefault()
        this.$emit('keyboard-action', { id: this.block.id, key: event.key })
      } else if (event.key === ' ' || event.key === 'Enter') {
        event.preventDefault()
        this.$emit('keyboard-action', { id: this.block.id, key: 'toggle' })
      }
    }
  }
}
</script>

<style lang="less" scoped>
.ptb {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 10px var(--space-3);
  border-radius: var(--radius-sm);
  border: 1px solid rgba(99, 102, 241, 0.20);
  background: var(--bg-card);
  font-family: var(--font-mono);
  font-size: var(--fs-base);
  line-height: var(--leading-body);
  cursor: grab;
  user-select: none;
  transition: background var(--motion-fast), border-color var(--motion-fast), box-shadow var(--motion-fast);
  min-height: var(--control-height-lg);
  position: relative;
}
.ptb:hover {
  border-color: rgba(99, 102, 241, 0.45);
  background: rgba(99, 102, 241, 0.06);
}
.ptb:focus-visible {
  outline: 2px solid var(--warm-primary);
  outline-offset: 2px;
}
.ptb-state-visible {
  border-color: rgba(99, 102, 241, 0.20);
  color: var(--text-strong);
}
.ptb-state-faded {
  background: repeating-linear-gradient(135deg,
    rgba(245, 158, 11, 0.08), rgba(245, 158, 11, 0.08) 6px,
    rgba(245, 158, 11, 0.16) 6px, rgba(245, 158, 11, 0.16) 12px);
  border-color: rgba(245, 158, 11, 0.50);
  color: #92400e;
}
.ptb-state-hidden {
  background: rgba(15, 23, 42, 0.05);
  border-color: rgba(15, 23, 42, 0.25);
  border-style: dashed;
  color: var(--text-secondary);
  font-style: italic;
}
.ptb-distractor {
  border-color: rgba(239, 68, 68, 0.40);
  background: rgba(239, 68, 68, 0.06);
  color: #b91c1c;
}
.ptb-dragging {
  opacity: 0.5;
  cursor: grabbing;
  box-shadow: var(--shadow-warm);
}
.ptb-misplaced {
  border-color: var(--color-warning);
  background: rgba(245, 158, 11, 0.10);
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.18);
  animation: ptb-shake 0.4s ease-in-out;
}
@keyframes ptb-shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  50% { transform: translateX(3px); }
  75% { transform: translateX(-2px); }
}
.ptb-indent {
  display: inline-block;
  width: 18px;
  height: 1px;
  border-left: 2px solid rgba(99, 102, 241, 0.18);
}
.ptb-code {
  white-space: pre;
}
.ptb-hidden-slot {
  font-family: var(--font-mono);
  letter-spacing: 0.05em;
}
.ptb-fade-hint {
  margin-left: auto;
  font-size: var(--fs-xs);
  color: #92400e;
  background: rgba(245, 158, 11, 0.18);
  padding: 2px 6px;
  border-radius: var(--radius-xs);
  font-family: var(--font-sans);
}
.ptb-flag-hint {
  margin-left: auto;
  font-size: var(--fs-xs);
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.12);
  padding: 2px 6px;
  border-radius: var(--radius-xs);
  font-family: var(--font-sans);
  border: 1px solid rgba(239, 68, 68, 0.22);
}
.ptb-misplaced-tag {
  position: absolute;
  top: -8px;
  right: -8px;
  width: 22px;
  height: 22px;
  border-radius: var(--radius-pill);
  background: var(--color-warning);
  color: #fff;
  font-weight: 700;
  font-size: var(--fs-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(245, 158, 11, 0.4);
}
</style>

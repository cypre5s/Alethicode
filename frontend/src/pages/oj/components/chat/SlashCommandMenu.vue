<template>
  <div
    v-if="visible"
    class="slash-command-menu"
    role="listbox"
    :aria-activedescendant="activeId"
  >
    <div
      v-for="(group, gi) in groups"
      :key="group.group || gi"
      class="slash-command-group"
    >
      <div class="slash-command-group-title">{{ group.group }}</div>
      <button
        v-for="(item, ii) in group.items"
        :id="itemId(gi, ii)"
        :key="item.key || item.command"
        type="button"
        :class="[
          'slash-command-item',
          { 'is-active': isActive(gi, ii), 'is-placeholder': item.status === 'placeholder' }
        ]"
        role="option"
        :aria-selected="isActive(gi, ii)"
        @mousedown.prevent="$emit('select', item)"
      >
        <span class="slash-command-cmd">{{ item.command }}</span>
        <span class="slash-command-label">{{ item.label || item.command }}</span>
        <span v-if="item.hint" class="slash-command-hint">{{ item.hint }}</span>
        <span v-if="item.status === 'placeholder'" class="slash-command-tag">即将上线</span>
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SlashCommandMenu',
  emits: ['select', 'close'],
  props: {
    visible: { type: Boolean, default: false },
    groups: { type: Array, default: () => [] },
    activeIndex: { type: Number, default: 0 }
  },
  computed: {
    activeId () {
      let cursor = 0
      for (let gi = 0; gi < this.groups.length; gi++) {
        const items = (this.groups[gi] && this.groups[gi].items) || []
        for (let ii = 0; ii < items.length; ii++) {
          if (cursor === this.activeIndex) return this.itemId(gi, ii)
          cursor++
        }
      }
      return null
    }
  },
  methods: {
    isActive (gi, ii) {
      let cursor = 0
      for (let g = 0; g < gi; g++) {
        const items = (this.groups[g] && this.groups[g].items) || []
        cursor += items.length
      }
      return (cursor + ii) === this.activeIndex
    },
    itemId (gi, ii) {
      return 'slash-command-item-' + gi + '-' + ii
    }
  }
}
</script>

<style lang="less" scoped>
.slash-command-menu {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
  padding: 8px 6px;
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  z-index: 30;
}

.slash-command-group + .slash-command-group {
  border-top: 1px dashed var(--border-color, #e5e7eb);
  padding-top: 6px;
  margin-top: 2px;
}

.slash-command-group-title {
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-disabled, #94a3b8);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.slash-command-item {
  display: grid;
  grid-template-columns: 110px 1fr auto auto;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  background: transparent;
  text-align: left;
  font-size: 13px;
  color: var(--text-primary, #0f172a);
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 150ms ease, opacity 150ms ease;
}

.slash-command-item:hover,
.slash-command-item.is-active {
  background: var(--bg-panel, #f1f5f9);
}

.slash-command-item.is-placeholder {
  cursor: not-allowed;
  opacity: 0.55;
}

.slash-command-cmd {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary, #0f172a);
  background: var(--bg-panel, #f1f5f9);
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.slash-command-label {
  font-size: 13px;
  color: var(--text-primary, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slash-command-hint {
  font-size: 11.5px;
  color: var(--text-disabled, #94a3b8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.slash-command-tag {
  font-size: 11px;
  color: var(--color-warning, #d97706);
  background: rgba(217, 119, 6, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .slash-command-item { transition: none; }
}
</style>

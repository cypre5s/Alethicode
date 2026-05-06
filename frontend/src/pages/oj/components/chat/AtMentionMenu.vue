<template>
  <div
    v-if="visible"
    class="at-mention-menu"
    role="listbox"
    :aria-activedescendant="activeId"
  >
    <div
      v-for="(group, gi) in groups"
      :key="group.key || group.group || gi"
      class="at-mention-group"
    >
      <div class="at-mention-group-title">{{ group.group }}</div>
      <button
        v-for="(item, ii) in group.items"
        :id="itemId(gi, ii)"
        :key="item.key || item.token"
        type="button"
        :class="['at-mention-item', { 'is-active': isActive(gi, ii) }]"
        role="option"
        :aria-selected="isActive(gi, ii)"
        @mousedown.prevent="$emit('select', item)"
        @mouseenter="onHoverItem(item, gi, ii)"
      >
        <span class="at-mention-token">{{ item.token }}</span>
        <span class="at-mention-label">{{ item.label || item.token }}</span>
        <span v-if="item.desc" class="at-mention-desc">{{ item.desc }}</span>
        <span v-if="item.placeholder" class="at-mention-placeholder">即将上线</span>
      </button>
    </div>

    <div
      v-if="showHoverPreview && hoveredItem && hoveredItem.hoverPreview"
      class="at-mention-preview"
      role="tooltip"
    >
      <div class="at-mention-preview-title">{{ hoveredItem.label || hoveredItem.token }}</div>
      <div class="at-mention-preview-body">{{ hoveredItem.hoverPreview }}</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AtMentionMenu',
  emits: ['select', 'hover', 'close'],
  props: {
    visible: { type: Boolean, default: false },
    groups: { type: Array, default: () => [] },
    activeIndex: { type: Number, default: 0 },
    showHoverPreview: { type: Boolean, default: true }
  },
  data () {
    return { hoveredItem: null }
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
  watch: {
    visible (next) {
      if (!next) this.hoveredItem = null
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
      return 'at-mention-item-' + gi + '-' + ii
    },
    onHoverItem (item, gi, ii) {
      this.hoveredItem = item
      this.$emit('hover', { item, groupIndex: gi, itemIndex: ii })
    }
  }
}
</script>

<style lang="less" scoped>
.at-mention-menu {
  position: relative;
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

.at-mention-group + .at-mention-group {
  border-top: 1px dashed var(--border-color, #e5e7eb);
  padding-top: 6px;
  margin-top: 2px;
}

.at-mention-group-title {
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-disabled, #94a3b8);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.at-mention-item {
  display: grid;
  grid-template-columns: minmax(72px, auto) 1fr auto;
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
  transition: background-color 150ms ease;
}

.at-mention-item:hover,
.at-mention-item.is-active {
  background: var(--bg-panel, #f1f5f9);
}

.at-mention-token {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
  color: var(--text-secondary, #475569);
  background: var(--bg-panel, #f1f5f9);
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.at-mention-label {
  font-size: 13px;
  color: var(--text-primary, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.at-mention-desc {
  font-size: 11.5px;
  color: var(--text-disabled, #94a3b8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}

.at-mention-placeholder {
  font-size: 11px;
  color: var(--color-warning, #d97706);
  background: rgba(217, 119, 6, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.at-mention-preview {
  position: absolute;
  top: 8px;
  left: calc(100% + 12px);
  width: 240px;
  padding: 12px 14px;
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  pointer-events: none;
}

.at-mention-preview-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary, #0f172a);
  margin-bottom: 6px;
}

.at-mention-preview-body {
  font-size: 12px;
  color: var(--text-secondary, #475569);
  line-height: 1.55;
}

@media (max-width: 768px) {
  .at-mention-preview { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .at-mention-item { transition: none; }
}
</style>

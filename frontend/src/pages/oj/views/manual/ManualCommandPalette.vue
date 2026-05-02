<template>
  <div
    v-if="visible"
    class="manual-command-palette"
    role="dialog"
    aria-modal="true"
    aria-label="命令面板"
    @click.self="close"
  >
    <div class="manual-command-palette__panel" ref="panelRef">
      <div class="manual-command-palette__head">
        <span class="manual-command-palette__hint">⌘ Cmd / Ctrl + K · 输入任意章节标题或动作</span>
        <span class="manual-command-palette__shortcut">Esc 关闭</span>
      </div>
      <input
        ref="inputRef"
        v-model="query"
        type="text"
        class="manual-command-palette__input"
        placeholder="搜索章节、切主题、让奶蛙笑一下…"
        aria-label="命令面板搜索"
        @keydown.down.prevent="moveFocus(1)"
        @keydown.up.prevent="moveFocus(-1)"
        @keydown.enter.prevent="trigger(filtered[activeIdx])"
      >
      <ul class="manual-command-palette__list" role="listbox">
        <li
          v-for="(item, idx) in filtered"
          :key="item.id"
          :class="['manual-command-palette__item', { 'is-active': idx === activeIdx, 'is-multiline': overflowMap[item.id] }]"
          role="option"
          :aria-selected="idx === activeIdx"
          @mousedown.prevent="trigger(item)"
          @mousemove="activeIdx = idx"
        >
          <span class="manual-command-palette__kind">{{ kindLabel(item.kind) }}</span>
          <span class="manual-command-palette__label">{{ item.label }}</span>
          <span v-if="item.hint" class="manual-command-palette__hint-text">{{ item.hint }}</span>
        </li>
        <li v-if="!filtered.length" class="manual-command-palette__empty">没有匹配的命令</li>
      </ul>
    </div>
  </div>
</template>

<script>
import { COMMAND_PALETTE_ITEMS } from './manualContent.js'
import { measureCommandItem } from './manualPretextLayout.js'

export default {
  name: 'ManualCommandPalette',
  data () {
    return {
      visible: false,
      query: '',
      activeIdx: 0,
      overflowMap: {}
    }
  },
  computed: {
    filtered () {
      const q = this.query.trim().toLowerCase()
      if (!q) return COMMAND_PALETTE_ITEMS
      return COMMAND_PALETTE_ITEMS.filter(item => {
        const blob = `${item.label} ${item.hint || ''} ${item.keywords || ''}`.toLowerCase()
        return blob.includes(q) || this.fuzzyMatch(blob, q)
      })
    }
  },
  watch: {
    filtered () {
      this.activeIdx = 0
      this.$nextTick(this.recomputeOverflow)
    },
    visible (val) {
      if (val) {
        this.$nextTick(() => {
          if (this.$refs.inputRef) this.$refs.inputRef.focus()
          this.recomputeOverflow()
        })
      }
    }
  },
  mounted () {
    window.addEventListener('keydown', this.handleHotkey)
  },
  beforeUnmount () {
    window.removeEventListener('keydown', this.handleHotkey)
  },
  methods: {
    open () {
      this.visible = true
      this.query = ''
      this.activeIdx = 0
    },
    close () {
      this.visible = false
    },
    moveFocus (delta) {
      const max = this.filtered.length
      if (!max) return
      this.activeIdx = (this.activeIdx + delta + max) % max
    },
    trigger (item) {
      if (!item) return
      this.$emit('command', item)
      this.visible = false
    },
    handleHotkey (event) {
      const isCmdK = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k'
      if (isCmdK) {
        event.preventDefault()
        this.visible = !this.visible
        if (this.visible) {
          this.query = ''
          this.activeIdx = 0
          this.$nextTick(() => this.$refs.inputRef && this.$refs.inputRef.focus())
        }
        return
      }
      if (event.key === 'Escape' && this.visible) {
        this.visible = false
      }
    },
    kindLabel (kind) {
      const dict = {
        goto: '跳转',
        theme: '主题',
        laugh: '彩蛋',
        fun: '设置',
        widget: '设置',
        top: '导航'
      }
      return dict[kind] || '命令'
    },
    fuzzyMatch (haystack, needle) {
      let i = 0
      let j = 0
      while (i < haystack.length && j < needle.length) {
        if (haystack[i] === needle[j]) j += 1
        i += 1
      }
      return j === needle.length
    },
    recomputeOverflow () {
      const panel = this.$refs.panelRef
      if (!panel) return
      const items = panel.querySelectorAll('.manual-command-palette__item')
      const map = {}
      items.forEach((node, idx) => {
        const labelEl = node.querySelector('.manual-command-palette__label')
        if (!labelEl) return
        const cs = window.getComputedStyle(labelEl)
        const font = `${cs.fontSize} ${cs.fontFamily}`
        const maxWidth = labelEl.clientWidth
        const item = this.filtered[idx]
        if (!item) return
        const stat = measureCommandItem(item.label, font, maxWidth)
        map[item.id] = stat.willOverflow
      })
      this.overflowMap = map
    }
  }
}
</script>

<style lang="less" scoped>
.manual-command-palette {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
  z-index: 1200;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 14vh;
}

.manual-command-palette__panel {
  width: min(640px, 92vw);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.25);
  overflow: hidden;
  border: 1px solid var(--border-default);
  display: flex;
  flex-direction: column;
}

.manual-command-palette__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-color);
  font-size: 11px;
  color: var(--text-secondary);
}

.manual-command-palette__shortcut {
  font-family: var(--font-mono);
  background: var(--bg-panel);
  padding: 1px 6px;
  border-radius: 4px;
}

.manual-command-palette__input {
  border: 0;
  outline: none;
  width: 100%;
  padding: 14px 18px;
  font-size: 16px;
  font-family: var(--font-sans);
  color: var(--text-primary);
  background: transparent;
  font-weight: 500;

  &::placeholder { color: var(--text-disabled); }
}

.manual-command-palette__list {
  list-style: none;
  margin: 0;
  padding: 6px;
  max-height: 360px;
  overflow-y: auto;
  border-top: 1px solid var(--border-color);
}

.manual-command-palette__item {
  display: grid;
  grid-template-columns: 60px 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s ease;

  &.is-active {
    background: var(--warm-glow);
  }

  &.is-multiline .manual-command-palette__label {
    white-space: normal;
    line-height: 1.4;
  }
}

.manual-command-palette__kind {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: var(--primary-color);
  background: rgba(99, 102, 241, 0.1);
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  text-align: center;
  letter-spacing: 0.5px;
}

.manual-command-palette__label {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.manual-command-palette__hint-text {
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.manual-command-palette__empty {
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
  padding: 20px;
}

@media (max-width: 640px) {
  .manual-command-palette { display: none; }
}
</style>

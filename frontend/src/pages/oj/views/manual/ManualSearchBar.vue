<template>
  <div class="manual-search-bar">
    <span class="manual-search-bar__icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="16" height="16">
        <path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35M11 19a8 8 0 100-16 8 8 0 000 16z"/>
      </svg>
    </span>
    <input
      v-model="query"
      type="search"
      class="manual-search-bar__input"
      placeholder="搜章节标题…试试'AI'或'错题'"
      aria-label="搜索章节"
      @keydown.enter.prevent="jumpToFirst"
      @keydown.esc="query = ''"
    >
    <ul v-if="query && results.length" class="manual-search-bar__list" role="listbox">
      <li
        v-for="(item, idx) in results"
        :key="item.id"
        class="manual-search-bar__item"
        :class="{ 'is-focus': idx === activeIdx }"
        role="option"
        :aria-selected="idx === activeIdx"
        @mousedown.prevent="jumpTo(item.id)"
      >
        <strong>{{ item.title }}</strong>
        <span>{{ item.subtitle }}</span>
      </li>
    </ul>
    <div v-else-if="query" class="manual-search-bar__empty">没有匹配的章节</div>
  </div>
</template>

<script>
import { SECTIONS } from './manualContent.js'

export default {
  name: 'ManualSearchBar',
  data () {
    return {
      query: '',
      activeIdx: 0
    }
  },
  computed: {
    results () {
      const q = this.query.trim().toLowerCase()
      if (!q) return []
      return SECTIONS.filter(s => {
        return s.title.toLowerCase().includes(q) ||
          (s.subtitle || '').toLowerCase().includes(q) ||
          s.id.toLowerCase().includes(q)
      })
    }
  },
  methods: {
    jumpToFirst () {
      if (!this.results.length) return
      this.jumpTo(this.results[0].id)
    },
    jumpTo (id) {
      this.$emit('jump', id)
      this.query = ''
    }
  }
}
</script>

<style lang="less" scoped>
.manual-search-bar {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  padding: 6px 14px;
  min-width: 220px;
  transition: border-color 0.2s, box-shadow 0.2s;

  &:focus-within {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.18);
  }

  &__icon {
    color: var(--text-disabled);
    display: inline-flex;
  }

  &__input {
    flex: 1;
    border: 0;
    outline: none;
    background: transparent;
    font-size: 13px;
    color: var(--text-primary);
    min-width: 0;
    padding: 4px 0;
    font-family: var(--font-sans);

    &::placeholder { color: var(--text-disabled); }
  }

  &__list {
    list-style: none;
    margin: 0;
    padding: 6px;
    position: absolute;
    top: calc(100% + 6px);
    left: 0;
    right: 0;
    background: var(--bg-card);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-md);
    box-shadow: var(--shadow-md);
    z-index: 10;
    max-height: 280px;
    overflow: auto;
  }

  &__item {
    padding: 8px 10px;
    border-radius: var(--radius-sm);
    cursor: pointer;
    display: flex;
    flex-direction: column;
    gap: 2px;

    strong {
      color: var(--text-primary);
      font-size: 13px;
      font-weight: 600;
    }

    span {
      color: var(--text-secondary);
      font-size: 12px;
    }

    &:hover, &.is-focus {
      background: var(--warm-glow);
    }
  }

  &__empty {
    position: absolute;
    top: calc(100% + 6px);
    left: 0;
    right: 0;
    background: var(--bg-card);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-md);
    box-shadow: var(--shadow-md);
    padding: 12px;
    text-align: center;
    color: var(--text-secondary);
    font-size: 12px;
  }
}
</style>

<template>
  <nav class="manual-sidebar" :class="{ 'is-fun': funMode }" aria-label="章节目录">
    <div class="manual-sidebar__head">
      <span class="manual-sidebar__title">目录</span>
      <span class="manual-sidebar__hint">Cmd/Ctrl + K 也能搜</span>
    </div>
    <ul class="manual-sidebar__list">
      <li
        v-for="(s, idx) in visibleSections"
        :key="s.id"
        class="manual-sidebar__item"
        :class="{ 'is-active': activeId === s.id }"
      >
        <a :href="`#${s.id}`" @click.prevent="onClick(s.id)">
          <span class="manual-sidebar__index">{{ String(idx + 1).padStart(2, '0') }}</span>
          <span class="manual-sidebar__text">
            <strong>{{ s.title }}</strong>
            <small>{{ s.subtitle }}</small>
          </span>
        </a>
      </li>
    </ul>
  </nav>
</template>

<script>
import { SECTIONS } from './manualContent.js'

export default {
  name: 'ManualSidebar',
  props: {
    activeId: { type: String, default: '' },
    funMode: { type: Boolean, default: false }
  },
  computed: {
    visibleSections () {
      return SECTIONS.filter(s => this.funMode || !s.funOnly)
    }
  },
  methods: {
    onClick (id) {
      this.$emit('jump', id)
    }
  }
}
</script>

<style lang="less" scoped>
.manual-sidebar {
  position: sticky;
  top: 88px;
  width: 240px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 16px 12px;
  box-shadow: none;
}

/* 趣味模式下恢复磨砂玻璃质感（由 funMode prop → .is-fun class 控制）。 */
.manual-sidebar.is-fun {
  background: rgba(255, 255, 255, 0.65);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  backdrop-filter: blur(20px) saturate(180%);
  border-color: var(--glass-border);
  box-shadow: var(--shadow-sm);
}

.manual-sidebar__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 8px 12px;
  border-bottom: 1px dashed var(--border-color);
  margin-bottom: 8px;
}

.manual-sidebar__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.5px;
}

.manual-sidebar__hint {
  font-size: 11px;
  color: var(--text-secondary);
}

.manual-sidebar__list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.manual-sidebar__item {
  position: relative;

  a {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 8px 10px;
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    text-decoration: none;
    transition: background 0.18s ease, color 0.18s ease;
    cursor: pointer;

    &:hover, &:focus-visible {
      background: var(--warm-glow);
      color: var(--primary-color);
      outline: none;
    }
  }

  &.is-active a {
    color: var(--primary-color);
    background: var(--warm-glow);
  }

  &.is-active::before {
    content: '';
    position: absolute;
    left: 0;
    top: 8px;
    bottom: 8px;
    width: 3px;
    border-radius: 2px;
    background: var(--warm-grad-primary);
  }
}

.manual-sidebar__index {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-disabled);
  font-weight: 600;
  margin-top: 2px;
}

.manual-sidebar__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;

  strong {
    font-size: 13px;
    font-weight: 600;
    line-height: 1.3;
    color: inherit;
  }

  small {
    font-size: 11px;
    color: var(--text-secondary);
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}

@media (max-width: 1023px) {
  .manual-sidebar {
    display: none;
  }
}
</style>

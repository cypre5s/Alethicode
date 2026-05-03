<template>
  <transition name="kc-drawer-slide">
    <aside v-if="visible" class="kc-drawer" role="complementary" aria-label="知识点详情">
      <div class="kc-drawer__header">
        <h3 class="kc-drawer__title">{{ node ? node.name : '' }}</h3>
        <button type="button" class="kc-drawer__close" aria-label="关闭" @click="$emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>

      <div v-if="node" class="kc-drawer__body">
        <div class="kc-drawer__mastery-section">
          <div class="kc-drawer__mastery-bar">
            <div class="kc-drawer__mastery-fill" :style="{ width: masteryPercent + '%', backgroundColor: masteryColor }"></div>
          </div>
          <span class="kc-drawer__mastery-label">掌握度 {{ masteryPercent }}%</span>
        </div>

        <div v-if="node.category" class="kc-drawer__meta">
          <span class="kc-drawer__meta-label">分类</span>
          <span class="kc-drawer__meta-value">{{ node.category }}</span>
        </div>

        <div v-if="node.last_touched_at" class="kc-drawer__meta">
          <span class="kc-drawer__meta-label">最近学习</span>
          <span class="kc-drawer__meta-value">{{ formattedLastTouched }}</span>
        </div>

        <div class="kc-drawer__meta">
          <span class="kc-drawer__meta-label">近 7 天事件</span>
          <span class="kc-drawer__meta-value">{{ node.recent_event_count }} 次</span>
        </div>

        <div v-if="relatedNodes.length > 0" class="kc-drawer__related">
          <h4 class="kc-drawer__section-title">相关知识点</h4>
          <div
            v-for="related in relatedNodes"
            :key="related.kc_id"
            class="kc-drawer__related-item"
            tabindex="0"
            @click="$emit('focus-node', related.kc_id)"
            @keydown.enter="$emit('focus-node', related.kc_id)"
          >
            <span class="kc-drawer__related-dot" :style="{ backgroundColor: getMasteryColor(related.mastery) }"></span>
            <span class="kc-drawer__related-name">{{ related.name }}</span>
            <span class="kc-drawer__related-mastery">{{ Math.round(related.mastery * 100) }}%</span>
          </div>
        </div>
      </div>
    </aside>
  </transition>
</template>

<script>
const MASTERY_COLORS = {
  low: '#6B7280',
  medium: '#F59E0B',
  high: '#0F4C81',
  mastered: '#10B981'
}

function getMasteryColor (mastery) {
  if (mastery > 0.85) return MASTERY_COLORS.mastered
  if (mastery > 0.6) return MASTERY_COLORS.high
  if (mastery > 0.3) return MASTERY_COLORS.medium
  return MASTERY_COLORS.low
}

export default {
  name: 'KcDetailDrawer',
  props: {
    visible: { type: Boolean, default: false },
    node: { type: Object, default: null },
    allNodes: { type: Array, default: () => [] },
    edges: { type: Array, default: () => [] }
  },
  emits: ['close', 'focus-node'],
  computed: {
    masteryPercent () {
      return this.node ? Math.round(this.node.mastery * 100) : 0
    },
    masteryColor () {
      return this.node ? getMasteryColor(this.node.mastery) : MASTERY_COLORS.low
    },
    formattedLastTouched () {
      if (!this.node || !this.node.last_touched_at) return ''
      const d = new Date(this.node.last_touched_at)
      return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    },
    relatedNodes () {
      if (!this.node) return []
      const kcId = this.node.kc_id
      const relatedIds = new Set()
      for (const e of this.edges) {
        if (e.from_kc_id === kcId) relatedIds.add(e.to_kc_id)
        if (e.to_kc_id === kcId) relatedIds.add(e.from_kc_id)
      }
      return this.allNodes.filter(n => relatedIds.has(n.kc_id))
    }
  },
  methods: {
    getMasteryColor
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.kc-drawer {
  position: fixed;
  top: 0;
  right: 0;
  width: 320px;
  height: 100vh;
  background: #fff;
  box-shadow: @l99-shadow-3;
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow-y: auto;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: @l99-sp-4 @l99-sp-5;
    border-bottom: 1px solid @l99-neutral-200;
  }
  &__title {
    font-size: @l99-fs-lg;
    font-weight: 600;
    color: @l99-neutral-900;
    margin: 0;
  }
  &__close {
    background: none;
    border: none;
    color: @l99-neutral-500;
    cursor: pointer;
    padding: @l99-sp-1;
    border-radius: @l99-radius-sm;
    &:hover { background: @l99-neutral-100; }
  }

  &__body {
    padding: @l99-sp-5;
    flex: 1;
  }

  &__mastery-section {
    margin-bottom: @l99-sp-5;
  }
  &__mastery-bar {
    height: 8px;
    background: @l99-neutral-100;
    border-radius: 4px;
    overflow: hidden;
    margin-bottom: @l99-sp-2;
  }
  &__mastery-fill {
    height: 100%;
    border-radius: 4px;
    transition: width @l99-dur-slow @l99-ease;
  }
  &__mastery-label {
    font-size: @l99-fs-sm;
    color: @l99-neutral-700;
    font-weight: 500;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    padding: @l99-sp-2 0;
    border-bottom: 1px solid @l99-neutral-100;
    &-label { font-size: @l99-fs-sm; color: @l99-neutral-500; }
    &-value { font-size: @l99-fs-sm; color: @l99-neutral-900; font-weight: 500; }
  }

  &__related {
    margin-top: @l99-sp-5;
  }
  &__section-title {
    font-size: @l99-fs-sm;
    font-weight: 600;
    color: @l99-neutral-700;
    margin: 0 0 @l99-sp-3;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  &__related-item {
    display: flex;
    align-items: center;
    gap: @l99-sp-2;
    padding: @l99-sp-2 @l99-sp-3;
    border-radius: @l99-radius-sm;
    cursor: pointer;
    transition: background @l99-dur-fast @l99-ease;
    &:hover, &:focus-visible {
      background: @l99-neutral-100;
      outline: none;
    }
  }
  &__related-dot {
    width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
  }
  &__related-name {
    flex: 1; font-size: @l99-fs-sm; color: @l99-neutral-900;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  &__related-mastery {
    font-size: @l99-fs-xs; color: @l99-neutral-500; font-family: @l99-font-mono;
  }
}

.kc-drawer-slide-enter-active,
.kc-drawer-slide-leave-active {
  transition: transform @l99-dur-slow @l99-ease;
}
.kc-drawer-slide-enter-from,
.kc-drawer-slide-leave-to {
  transform: translateX(100%);
}

@media (max-width: 767px) {
  .kc-drawer {
    width: 100%;
    height: 50vh;
    top: auto;
    bottom: 0;
    border-top-left-radius: @l99-radius-lg;
    border-top-right-radius: @l99-radius-lg;
  }
  .kc-drawer-slide-enter-from,
  .kc-drawer-slide-leave-to {
    transform: translateY(100%);
  }
}
</style>

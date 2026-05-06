<template>
  <div class="composer-hint-bar" :aria-live="atActive || slashActive ? 'polite' : 'off'">
    <span class="composer-hint-group">
      <span :class="['composer-hint-chip', { 'is-active': slashActive }]">/ 命令</span>
      <span :class="['composer-hint-chip', { 'is-active': atActive }]">@ 引用</span>
      <span class="composer-hint-chip">↑ 历史</span>
    </span>
    <span class="composer-hint-extra">
      <slot name="extra" />
    </span>
  </div>
</template>

<script>
export default {
  name: 'ComposerHintBar',
  props: {
    atActive: { type: Boolean, default: false },
    slashActive: { type: Boolean, default: false }
  }
}
</script>

<style lang="less" scoped>
.composer-hint-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 2px;
  font-size: 11px;
  color: var(--text-disabled, #94a3b8);
}

.composer-hint-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.composer-hint-chip {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  letter-spacing: 0.2px;
  background: var(--bg-panel, #f1f5f9);
  color: var(--text-disabled, #94a3b8);
  transition: color 150ms ease, background-color 150ms ease;
}

.composer-hint-chip.is-active {
  color: var(--text-primary, #0f172a);
  background: rgba(59, 130, 246, 0.08);
  font-weight: 600;
}

.composer-hint-extra {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

@media (prefers-reduced-motion: reduce) {
  .composer-hint-chip { transition: none; }
}
</style>

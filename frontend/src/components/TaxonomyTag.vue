<template>
  <span :class="['taxonomy-tag', `taxonomy-tag--${resolvedVariant}`, { 'taxonomy-tag--solid': solid }]">
    <slot>{{ label }}</slot>
  </span>
</template>

<script>
const VARIANT_BY_TYPE = Object.freeze({
  syntax_error: 'danger',
  runtime_error: 'danger',
  logic_error: 'warning',
  algorithm_error: 'warning',
  boundary_condition: 'info',
  input_parsing: 'info',
  name_or_type_error: 'neutral',
  performance: 'neutral'
})

const VALID_VARIANTS = Object.freeze(['primary', 'danger', 'warning', 'info', 'neutral', 'success'])

export default {
  name: 'TaxonomyTag',
  props: {
    type: { type: String, default: '' },
    variant: { type: String, default: '' },
    label: { type: String, default: '' },
    solid: { type: Boolean, default: false }
  },
  computed: {
    resolvedVariant () {
      if (this.variant && VALID_VARIANTS.includes(this.variant)) return this.variant
      const mapped = VARIANT_BY_TYPE[this.type]
      return mapped || 'primary'
    }
  }
}
</script>

<style lang="less" scoped>
.taxonomy-tag {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  padding: 0 10px;
  border-radius: var(--tag-radius);
  border: 1px solid transparent;
  font-size: var(--fs-sm);
  font-weight: 600;
  letter-spacing: 0.01em;
  line-height: 1;
  white-space: nowrap;
}
.taxonomy-tag--primary {
  background: var(--primary-50);
  color: var(--primary-700);
  border-color: rgba(37, 99, 235, 0.18);
}
.taxonomy-tag--danger {
  background: rgba(239, 68, 68, 0.10);
  color: #b91c1c;
  border-color: rgba(239, 68, 68, 0.22);
}
.taxonomy-tag--warning {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.25);
}
.taxonomy-tag--info {
  background: rgba(14, 165, 233, 0.10);
  color: #0369a1;
  border-color: rgba(14, 165, 233, 0.22);
}
.taxonomy-tag--neutral {
  background: var(--bg-panel);
  color: var(--text-secondary);
  border-color: var(--border-default);
}
.taxonomy-tag--success {
  background: rgba(16, 185, 129, 0.10);
  color: #047857;
  border-color: rgba(16, 185, 129, 0.22);
}
.taxonomy-tag--solid {
  border-color: transparent;
  color: #fff;
  &.taxonomy-tag--primary { background: var(--primary-600); }
  &.taxonomy-tag--danger { background: var(--color-danger); }
  &.taxonomy-tag--warning { background: var(--color-warning); }
  &.taxonomy-tag--info { background: var(--color-info); }
  &.taxonomy-tag--neutral { background: var(--color-neutral); }
  &.taxonomy-tag--success { background: var(--color-success); }
}
</style>

<template>
  <div class="bac-card" :class="['bac-accent-' + accent]" :data-accent="accent">
    <header class="bac-head">
      <div class="bac-head-icon">
        <slot name="icon">
          <component v-if="icon" :is="icon" :size="14" />
        </slot>
      </div>
      <span class="bac-head-title">{{ title }}</span>
      <div v-if="$slots['head-extra']" class="bac-head-extra">
        <slot name="head-extra" />
      </div>
    </header>

    <section class="bac-body">
      <slot name="body" />
    </section>

    <footer v-if="$slots.foot" class="bac-foot">
      <slot name="foot" />
    </footer>
  </div>
</template>

<script>
const ALLOWED_ACCENTS = [
  'primary', 'guide', 'ideate', 'success',
  'danger', 'transfer', 'review', 'encouragement'
]

export default {
  name: 'BaseAgentCard',
  props: {
    accent: {
      type: String,
      required: true,
      validator (value) {
        return ALLOWED_ACCENTS.indexOf(value) !== -1
      }
    },
    title: {
      type: String,
      required: true
    },
    icon: {
      type: [Object, Function],
      default: null
    }
  }
}
</script>

<style lang="less" scoped>
@import '@/styles/cardSizingTokens.less';
@import '@/styles/cardAccentTokens.less';

.bac-card {
  .card-sizing-tokens();
  background: var(--bg-card, #fff);
  border-radius: var(--card-radius);
  border: 1px solid var(--card-accent-border);
  overflow: hidden;
  font-size: var(--card-font-body);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 12px var(--card-accent-bg);
}

.bac-accent-primary { .card-accent-primary(); }
.bac-accent-guide { .card-accent-guide(); }
.bac-accent-ideate { .card-accent-ideate(); }
.bac-accent-success { .card-accent-success(); }
.bac-accent-danger { .card-accent-danger(); }
.bac-accent-transfer { .card-accent-transfer(); }
.bac-accent-review { .card-accent-review(); }
.bac-accent-encouragement { .card-accent-encouragement(); }

.bac-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--card-head-py) var(--card-head-px);
  border-bottom: 1px solid var(--card-accent-border);
  background: var(--card-accent-bg);
}

.bac-head-icon {
  width: var(--card-icon-size);
  height: var(--card-icon-size);
  border-radius: var(--card-icon-radius);
  background: var(--bg-card, #fff);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--card-accent);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.bac-head-title {
  font-size: var(--card-font-title);
  font-weight: 600;
  color: var(--card-accent);
  letter-spacing: 0.2px;
}

.bac-head-extra {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
}

.bac-body {
  padding: var(--card-body-py) var(--card-body-px);
  display: flex;
  flex-direction: column;
  gap: var(--card-body-gap);
  font-size: var(--card-font-body);
  line-height: var(--card-line-height);
  color: var(--text-primary, #1f2937);
}

.bac-foot {
  padding: var(--card-foot-py) var(--card-foot-px);
  border-top: 1px solid var(--card-accent-border);
  background: var(--card-accent-bg);
}
</style>

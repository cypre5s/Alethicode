<template>
  <section id="tips" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="6" size="md" :rotate="-5" />
      <div>
        <span class="manual-section__kicker">06 · Tips</span>
        <h2>学习建议</h2>
        <p>5 条容易被忽略、但对学习节奏影响很大的最佳实践。不是必须照做，但你越坚持，越快找到适合自己的节奏。</p>
      </div>
    </header>

    <ul class="tips-list">
      <li v-for="(t, idx) in tips" :key="idx" class="tip-card">
        <span class="tip-card__num">{{ String(idx + 1).padStart(2, '0') }}</span>
        <h3>{{ t.title }}</h3>
        <p>{{ t.desc }}</p>
      </li>
    </ul>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import { TIPS } from '../manualContent.js'

export default {
  name: 'SectionTips',
  components: { ManualNaiwaSticker },
  data () {
    return { tips: TIPS }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.tips-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.tip-card {
  background: var(--warm-bg-subtle);
  border: 1px dashed var(--border-warm);
  border-radius: var(--radius-md);
  padding: 18px 20px;
  position: relative;
  transition: transform 0.18s ease, border-color 0.18s ease;

  &:hover {
    transform: translateY(-2px);
    border-color: var(--warm-primary);
    border-style: solid;
  }

  h3 {
    margin: 8px 0 6px;
    font-size: 14px;
    color: var(--text-primary);
    text-wrap: balance;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.65;
    text-wrap: pretty;
  }
}

.tip-card__num {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--warm-primary);
  letter-spacing: 1px;
}
</style>

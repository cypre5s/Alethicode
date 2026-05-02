<template>
  <section id="faq" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="5" size="md" :rotate="2" />
      <div>
        <span class="manual-section__kicker">06 · FAQ</span>
        <h2>常见问题</h2>
        <p>{{ items.length }} 条最常被问的问题，按"做题流程"排序，看到对应的就点开。</p>
      </div>
    </header>

    <ul class="faq-list">
      <li
        v-for="(item, idx) in items"
        :key="idx"
        class="faq-item"
        :class="{ 'is-open': openIdx === idx }"
      >
        <button type="button" class="faq-item__head" @click="toggle(idx)" :aria-expanded="openIdx === idx">
          <img class="faq-item__icon" :src="iconFor(idx).src" :alt="iconFor(idx).alt" loading="lazy">
          <span class="faq-item__q">{{ item.q }}</span>
          <span class="faq-item__chevron" aria-hidden="true">{{ openIdx === idx ? '−' : '+' }}</span>
        </button>
        <div v-show="openIdx === idx" class="faq-item__a">
          <p>{{ item.a }}</p>
        </div>
      </li>
    </ul>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import { FAQ_ITEMS, NAIWA_FAQ_ICONS } from '../manualContent.js'

export default {
  name: 'SectionFAQ',
  components: { ManualNaiwaSticker },
  data () {
    return {
      items: FAQ_ITEMS,
      openIdx: -1
    }
  },
  methods: {
    toggle (idx) {
      this.openIdx = this.openIdx === idx ? -1 : idx
    },
    iconFor (idx) {
      const list = NAIWA_FAQ_ICONS
      return list[idx % list.length]
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.faq-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.faq-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--bg-card);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;

  &.is-open {
    border-color: var(--warm-primary);
    box-shadow: var(--shadow-sm);
  }
}

.faq-item__head {
  display: grid;
  grid-template-columns: 32px 1fr 24px;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 12px 16px;
  text-align: left;
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
  transition: background 0.18s ease;

  &:hover { background: var(--warm-glow); }
  &:focus-visible { outline: 2px solid var(--primary-color); outline-offset: -2px; }
}

.faq-item__icon {
  width: 28px;
  height: 28px;
  object-fit: contain;
  flex-shrink: 0;
}

.faq-item__q {
  text-wrap: balance;
}

.faq-item__chevron {
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 18px;
  color: var(--text-secondary);
  width: 24px;
  text-align: right;
}

.faq-item__a {
  padding: 4px 16px 14px 60px;
  border-top: 1px dashed var(--border-color);

  p {
    margin: 8px 0 0;
    color: var(--text-secondary);
    font-size: 13px;
    line-height: 1.7;
    text-wrap: pretty;
  }
}

@media (max-width: 640px) {
  .faq-item__a { padding-left: 16px; }
}
</style>

<template>
  <section id="feedback" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="0" size="md" :rotate="0" />
      <div>
        <span class="manual-section__kicker">08 · 反馈</span>
        <h2>反馈与帮助</h2>
        <p>看到 bug、想关掉某个动效、或者只是想吐槽，都从这里走。</p>
      </div>
    </header>

    <ul class="feedback-list">
      <li v-for="(item, idx) in items" :key="idx" class="feedback-card">
        <h3>{{ item.title }}</h3>
        <p>{{ item.desc }}</p>
      </li>
    </ul>

    <div class="feedback-cta">
      <button type="button" class="btn primary" @click="$emit('jump', 'welcome')">回到顶部，再走一遍</button>
      <button type="button" class="btn ghost" @click="goPractice">直接去做题 →</button>
    </div>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import { FEEDBACK_ITEMS } from '../manualContent.js'

export default {
  name: 'SectionFeedback',
  components: { ManualNaiwaSticker },
  data () {
    return { items: FEEDBACK_ITEMS }
  },
  methods: {
    goPractice () {
      this.$router.push('/problem').catch(() => {})
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.feedback-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}

.feedback-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 16px 18px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    border-color: var(--warm-primary);
    box-shadow: var(--shadow-sm);
  }

  h3 {
    margin: 0 0 6px;
    font-size: 14px;
    color: var(--text-primary);
  }

  p {
    margin: 0;
    color: var(--text-secondary);
    font-size: 13px;
    line-height: 1.6;
    text-wrap: pretty;
  }
}

.feedback-cta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;

  .btn {
    border: 1px solid var(--border-default);
    background: var(--bg-card);
    color: var(--text-secondary);
    border-radius: var(--radius-pill);
    padding: 9px 22px;
    font-size: 14px;
    cursor: pointer;
    font-weight: 500;
    transition: all 0.18s ease;

    &:hover {
      color: var(--primary-color);
      border-color: var(--primary-color);
    }

    &.primary {
      background: var(--warm-grad-primary);
      color: #fff;
      border-color: transparent;
      &:hover { transform: translateY(-1px); color: #fff; border-color: transparent; }
    }
  }
}
</style>

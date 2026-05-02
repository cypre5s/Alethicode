<template>
  <section id="welcome" class="manual-section manual-section--welcome">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="0" size="md" :rotate="-6" />
      <div>
        <span class="manual-section__kicker">01 · 起步</span>
        <h2>欢迎与快速开始</h2>
        <p>Alethicode 是一个能让你写真代码、被纠错、被陪着学的地方。先认识一下你最常打开的几扇门。</p>
      </div>
    </header>

    <div class="quick-start-grid">
      <article
        v-for="step in steps"
        :key="step.step"
        class="quick-start-card"
      >
        <span class="quick-start-card__num">{{ step.step }}</span>
        <h3>{{ step.title }}</h3>
        <p>{{ step.desc }}</p>
      </article>
    </div>

    <div class="welcome-cta">
      <button type="button" class="btn primary" @click="$emit('jump', 'flow')">
        立刻看完整路径 →
      </button>
      <button type="button" class="btn ghost" @click="$emit('jump', 'tour')">
        想先看页面截图
      </button>
    </div>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import { QUICK_START_STEPS } from '../manualContent.js'

export default {
  name: 'SectionWelcome',
  components: { ManualNaiwaSticker },
  data () {
    return {
      steps: QUICK_START_STEPS
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.quick-start-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin: 24px 0 8px;
}

.quick-start-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 22px 20px 20px;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.06) 0%, rgba(236, 72, 153, 0.04) 100%);
    pointer-events: none;
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
    border-color: var(--warm-primary);
    &::before { opacity: 1; }
  }

  h3 {
    font-size: 16px;
    color: var(--text-primary);
    margin: 8px 0 6px;
  }

  p {
    color: var(--text-secondary);
    font-size: 13px;
    line-height: 1.6;
    margin: 0;
    text-wrap: pretty;
  }
}

.quick-start-card__num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--warm-grad-primary);
  color: #fff;
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 14px;
}

.welcome-cta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;

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
      box-shadow: var(--shadow-warm);
      &:hover { transform: translateY(-1px); color: #fff; border-color: transparent; }
    }
  }
}
</style>

<template>
  <section id="welcome" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="0" size="md" :rotate="-6" />
      <div>
        <span class="manual-section__kicker">01 · Quick start</span>
        <h2>快速开始</h2>
        <p>第一次用 Alethicode，按这 4 步走一遍。每一步会告诉你「在哪做、看什么、为什么」。</p>
      </div>
    </header>

    <ol class="quick-start-list">
      <li v-for="step in steps" :key="step.step" class="quick-start-item">
        <div class="quick-start-item__num">
          <span class="quick-start-item__num-digit">{{ String(step.step).padStart(2, '0') }}</span>
        </div>
        <div class="quick-start-item__body">
          <h3>{{ step.title }}</h3>
          <dl class="quick-start-item__meta">
            <div>
              <dt>在哪做</dt>
              <dd>{{ step.where }}</dd>
            </div>
            <div>
              <dt>看什么</dt>
              <dd>{{ step.look }}</dd>
            </div>
            <div>
              <dt>为什么</dt>
              <dd>{{ step.why }}</dd>
            </div>
          </dl>
        </div>
      </li>
    </ol>

    <div class="welcome-cta">
      <button type="button" class="btn primary" @click="$emit('jump', 'flow')">
        跟着 8 步走完整流程 →
      </button>
      <button type="button" class="btn ghost" @click="$emit('jump', 'ai')">
        先读 AI 导学助手说明
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

.quick-start-list {
  list-style: none;
  margin: 0 0 24px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  counter-reset: quick-start;
}

.quick-start-item {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 16px;
  padding: 18px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: border-color 0.18s ease;

  &:hover { border-color: var(--text-disabled); }
}

.quick-start-item__num {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 2px;
}

.quick-start-item__num-digit {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  color: var(--text-disabled);
  letter-spacing: -0.5px;
  line-height: 1;
}

.quick-start-item__body {
  min-width: 0;

  h3 {
    margin: 0 0 12px;
    font-size: 16px;
    font-weight: 700;
    color: var(--text-primary);
    text-wrap: balance;
    letter-spacing: -0.2px;
  }
}

.quick-start-item__meta {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px 18px;

  div {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  dt {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: var(--text-disabled);
    text-transform: uppercase;
    letter-spacing: 0.8px;
  }

  dd {
    margin: 0;
    font-size: 12.5px;
    color: var(--text-secondary);
    line-height: 1.55;
    text-wrap: pretty;
  }
}

.welcome-cta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;

  .btn {
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    color: var(--text-primary);
    border-radius: var(--radius-md);
    padding: 9px 18px;
    font-size: 13.5px;
    cursor: pointer;
    font-weight: 600;
    transition: all 0.18s ease;

    &:hover {
      border-color: var(--text-disabled);
    }

    &.primary {
      background: var(--text-primary);
      color: var(--bg-card);
      border-color: var(--text-primary);
      &:hover { opacity: 0.85; }
    }
  }
}

@media (max-width: 640px) {
  .quick-start-item {
    grid-template-columns: 40px 1fr;
    padding: 14px 14px;
  }

  .quick-start-item__num-digit { font-size: 18px; }
}
</style>

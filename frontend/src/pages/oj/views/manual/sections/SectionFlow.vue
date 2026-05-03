<template>
  <section id="flow" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="3" size="md" :rotate="4" />
      <div>
        <span class="manual-section__kicker">05 · Loop</span>
        <h2>完整学习闭环 · 8 步</h2>
        <p>从读题到复盘的标准流程。点击图上节点会跳到与该步相关的章节；带粉色描边的几步是初学者最容易跳过、也最影响学习效果的环节。</p>
      </div>
    </header>

    <ManualFlowDiagram @jump="(target) => $emit('jump', target)" />

    <div class="flow-explain">
      <h3 class="flow-explain__title">为什么要走这 8 步</h3>
      <ol class="flow-explain__list">
        <li v-for="step in steps" :key="step.step" class="flow-explain__item">
          <span class="flow-explain__num">{{ String(step.step).padStart(2, '0') }}</span>
          <div>
            <strong>{{ step.title }}</strong>
            <p>{{ step.desc }}</p>
          </div>
        </li>
      </ol>
    </div>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import ManualFlowDiagram from '../ManualFlowDiagram.vue'
import { LEARNING_LOOP_STEPS } from '../manualContent.js'

export default {
  name: 'SectionFlow',
  components: { ManualNaiwaSticker, ManualFlowDiagram },
  data () {
    return { steps: LEARNING_LOOP_STEPS }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.flow-explain {
  margin-top: 28px;
}

.flow-explain__title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.2px;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.flow-explain__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
}

.flow-explain__item {
  display: grid;
  grid-template-columns: 32px 1fr;
  gap: 10px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: border-color 0.18s ease;

  &:hover { border-color: var(--text-disabled); }

  strong {
    display: block;
    font-size: 13.5px;
    color: var(--text-primary);
    font-weight: 700;
    margin-bottom: 4px;
  }

  p {
    margin: 0;
    font-size: 12.5px;
    color: var(--text-secondary);
    line-height: 1.6;
    text-wrap: pretty;
  }
}

.flow-explain__num {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-disabled);
  letter-spacing: -0.5px;
  padding-top: 2px;
}
</style>

<template>
  <section id="flow" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="1" size="md" :rotate="4" />
      <div>
        <span class="manual-section__kicker">02 · 路径</span>
        <h2>新手路径</h2>
        <p>从打开 Alethicode 到看懂第一份判题结果，整张图按顺序点一遍最多 8 步。</p>
      </div>
    </header>

    <ManualFlowDiagram @jump="(target) => $emit('jump', target)" />

    <p class="flow-hint">点击节点可直接跳到对应章节；带粉色边框的几个是初学者最容易卡住的环节，已经特意标出来。</p>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import ManualFlowDiagram from '../ManualFlowDiagram.vue'

export default {
  name: 'SectionFlow',
  components: { ManualNaiwaSticker, ManualFlowDiagram }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.flow-hint {
  margin: 18px 0 0;
  padding: 12px 16px;
  background: var(--warm-bg-subtle);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  text-wrap: pretty;
  border: 1px dashed var(--border-warm);
}
</style>

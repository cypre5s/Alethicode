<template>
  <section id="core" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="3" size="md" :rotate="6" />
      <div>
        <span class="manual-section__kicker">04 · 操作</span>
        <h2>核心操作</h2>
        <p>5 个最常用的动作，点开折叠面板看具体怎么做。</p>
      </div>
    </header>

    <ElCollapse v-model="activeKeys" accordion>
      <ElCollapseItem
        v-for="op in operations"
        :key="op.id"
        :name="op.id"
      >
        <template #title>
          <div class="core-title">
            <span class="core-title__index">{{ String(operations.indexOf(op) + 1).padStart(2, '0') }}</span>
            <span>{{ op.title }}</span>
          </div>
        </template>
        <ol class="core-body">
          <li v-for="(line, i) in op.body" :key="i">{{ line }}</li>
        </ol>
      </ElCollapseItem>
    </ElCollapse>
  </section>
</template>

<script>
import { ElCollapse, ElCollapseItem } from 'element-plus'
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import { CORE_OPERATIONS } from '../manualContent.js'

export default {
  name: 'SectionCore',
  components: { ElCollapse, ElCollapseItem, ManualNaiwaSticker },
  data () {
    return {
      operations: CORE_OPERATIONS,
      activeKeys: 'write-problem'
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.core-title {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.core-title__index {
  font-family: var(--font-mono);
  background: var(--warm-glow);
  color: var(--warm-primary);
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
}

.core-body {
  margin: 0;
  padding: 6px 0 8px 22px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.75;
  list-style: decimal;
  text-wrap: pretty;

  li { padding: 2px 0; }
}

:deep(.el-collapse-item__header) {
  font-size: 14px;
}

:deep(.el-collapse-item) {
  border-radius: var(--radius-md);
  margin-bottom: 6px;
  border: 1px solid var(--border-color);
  overflow: hidden;
}

:deep(.el-collapse) {
  border: 0;
}

:deep(.el-collapse-item__header) {
  padding: 0 16px;
  border-bottom: 0;
}

:deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

:deep(.el-collapse-item__content) {
  padding: 4px 18px 14px;
}
</style>

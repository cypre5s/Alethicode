<template>
  <BaseAgentCard v-if="data" accent="primary" :icon="monitorIcon" title="骨架代码">
    <template #body>
      <p v-if="data.description" class="sk-desc">{{ data.description }}</p>
      <pre v-if="data.skeleton" class="sk-code"><code>{{ data.skeleton }}</code></pre>

      <button
        type="button"
        class="skeleton-btn-primary"
        @click.stop.prevent="handleInsertClick"
      >
        <el-icon :size="14"><Download /></el-icon>
        插入编辑器，开始填写
      </button>

      <button
        type="button"
        class="skeleton-btn-parsons"
        @click.stop.prevent="handleParsonsClick"
      >
        <el-icon :size="14"><Grid /></el-icon>
        有点难？试试拼装版
      </button>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw } from 'vue'
import { Monitor, Download, Grid } from '@element-plus/icons-vue'
import BaseAgentCard from './BaseAgentCard.vue'

export default {
  name: 'SkeletonCodeCard',
  emits: ['insert-code', 'request-parsons'],
  components: { BaseAgentCard, Download, Grid },
  props: {
    data: {
      type: Object,
      default: () => ({ skeleton: '', description: '' })
    }
  },
  data () {
    return { monitorIcon: markRaw(Monitor) }
  },
  methods: {
    handleInsertClick () {
      if (!this.data || !this.data.skeleton) return
      this.$emit('insert-code', { code: this.data.skeleton, position: 'append' })
    },
    handleParsonsClick () {
      this.$emit('request-parsons')
    }
  }
}
</script>

<style lang="less" scoped>
.sk-desc {
  color: var(--text-secondary, #6b7280);
  margin: 0; white-space: pre-line;
  font-size: var(--card-font-body); line-height: 1.6;
}

.sk-code {
  background: #1e293b;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 14px 16px;
  font-family: var(--font-mono);
  font-size: var(--card-font-body);
  line-height: 1.6;
  color: #e2e8f0;
  overflow-x: auto;
  white-space: pre-wrap;
  margin: 0;
}
.sk-code code {
  font-family: inherit; color: inherit;
}

.skeleton-btn-primary {
  width: 100%; padding: 10px 16px;
  border: none; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; gap: 7px;
  background: var(--card-accent); color: #fff;
  font-size: var(--card-font-body); font-weight: 500;
  font-family: inherit; cursor: pointer;
  transition: filter 0.15s;
  &:hover { filter: brightness(0.92); }
  &:active { filter: brightness(0.85); }
  &:focus-visible { outline: 2px solid rgba(59, 130, 246, 0.28); outline-offset: 2px; }
}

.skeleton-btn-parsons {
  width: 100%; margin-top: 8px;
  padding: 8px 14px;
  border: 1px solid rgba(59, 130, 246, 0.32);
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center; gap: 7px;
  background: transparent;
  color: var(--card-accent);
  font-size: var(--card-font-body); font-weight: 500;
  font-family: inherit; cursor: pointer;
  transition: background-color 0.15s, border-color 0.15s, color 0.15s;
  &:hover {
    background: rgba(59, 130, 246, 0.08);
    border-color: rgba(59, 130, 246, 0.48);
  }
  &:active { background: rgba(59, 130, 246, 0.14); }
  &:focus-visible { outline: 2px solid rgba(59, 130, 246, 0.32); outline-offset: 2px; }
}
</style>

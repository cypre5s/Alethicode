<template>
  <ElCard shadow="never" :body-style="{ padding: '0px' }">
    <ul>
      <slot></slot>
    </ul>
  </ElCard>
</template>

<script>
  import { provide } from 'vue'

  export default {
    name: 'VerticalMenu',
    emits: ['on-click'],
    setup (props, { emit }) {
      provide('onMenuItemClick', (route) => emit('on-click', route))
    }
  }
</script>

<style scoped lang="less">
</style>

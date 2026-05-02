<template>
  <ElCard :shadow="shadow ? 'always' : 'hover'" :body-style="{ padding: padding + 'px' }">
    <template #header>
      <div class="panel-header">
        <div class="panel-title"><slot name="title"></slot></div>
        <div class="panel-extra"><slot name="extra"></slot></div>
      </div>
    </template>
    <div class="panel-body">
      <slot></slot>
    </div>
  </ElCard>
</template>

<script>
  export default {
    name: 'OjPanel',
    props: {
      shadow: {
        required: false,
        type: Boolean,
        default: false
      },
      padding: {
        required: false,
        type: Number,
        default: 0
      },
      disHover: {
        required: false,
        type: Boolean,
        default: false
      },
      bordered: {
        required: false,
        type: Boolean,
        default: true
      }
    }
  }
</script>

<style lang="less">
  @import (reference) '../../../styles/common.less';

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .panel-title {
    .section-title;
    padding: 5px 15px;
  }

  .panel-extra {
    line-height: 40px;
    ul.filter {
      > li {
        display: inline-block;
        padding: 0 10px;
      }
    }
  }
  .panel-body {
    word-break: break-all;
    word-wrap: break-word;
  }
</style>

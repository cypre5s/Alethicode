<template>
  <div class="icon-btn-wrap">
    <el-tooltip class="item" effect="dark" :content="name" placement="top">
      <el-button plain class="icon-btn" size="small">
        <span class="icon-btn-text">{{ iconText }}</span>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script>
  const ICON_MAP = {
    trash: '🗑',
    edit: '✏️',
    download: '⬇',
    gavel: '⚖️',
    plus: '+',
    close: '✕'
  }

  export default {
    name: 'IconBtn',
    props: {
      name: {
        type: String,
        required: true
      },
      icon: {
        type: String,
        required: true
      }
    },
    computed: {
      iconText() {
        return ICON_MAP[this.icon] || this.icon
      }
    }
  }
</script>
<style scoped lang="less">
  .icon-btn-wrap {
    display: inline-block;
  }

  .icon-btn {
    min-width: 34px;
    padding: 8px 10px;
  }

  .icon-btn-text {
    font-size: 14px;
    line-height: 1;
  }
</style>

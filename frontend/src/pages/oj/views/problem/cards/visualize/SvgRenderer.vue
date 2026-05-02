<template>
  <div class="viz-svg-wrap">
    <div v-if="!svgText" class="viz-error">SVG 内容为空</div>
    <div v-else class="viz-svg-content" v-html="svgText"></div>
  </div>
</template>

<script>
export default {
  name: 'SvgRenderer',
  props: {
    payload: {
      type: String,
      default: ''
    }
  },
  computed: {
    svgText () {
      return (this.payload || '').trim()
    }
  }
}
</script>

<style scoped>
.viz-svg-wrap {
  width: 100%;
}

.viz-svg-content {
  overflow-x: auto;
}

.viz-error {
  color: #b91c1c;
  font-size: 13px;
}
</style>

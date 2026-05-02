<template>
  <div class="viz-chart">
    <div v-if="error" class="viz-error">{{ error }}</div>
    <canvas v-show="!error" ref="chartCanvas"></canvas>
  </div>
</template>

<script>
import { Chart, registerables } from 'chart.js'

Chart.register(...registerables)

export default {
  name: 'ChartRenderer',
  props: {
    payload: {
      type: String,
      default: ''
    }
  },
  data () {
    return {
      error: '',
      chartInstance: null
    }
  },
  watch: {
    payload: {
      immediate: true,
      handler () {
        this.$nextTick(() => this.renderChart())
      }
    }
  },
  beforeUnmount () {
    this.destroyChart()
  },
  methods: {
    destroyChart () {
      if (this.chartInstance) {
        this.chartInstance.destroy()
        this.chartInstance = null
      }
    },
    renderChart () {
      this.error = ''
      this.destroyChart()
      const text = (this.payload || '').trim()
      if (!text) {
        this.error = 'Chart 配置为空'
        return
      }
      let config
      try {
        config = JSON.parse(text)
      } catch {
        this.error = 'Chart 配置不是合法 JSON'
        return
      }

      const canvas = this.$refs.chartCanvas
      if (!canvas) {
        this.error = 'Chart 容器未就绪'
        return
      }
      try {
        this.chartInstance = new Chart(canvas, config)
      } catch {
        this.error = 'Chart 渲染失败'
      }
    }
  }
}
</script>

<style scoped>
.viz-chart {
  width: 100%;
  min-height: 220px;
}

.viz-chart canvas {
  width: 100% !important;
  max-height: 320px;
}

.viz-error {
  color: #b91c1c;
  font-size: 13px;
}
</style>

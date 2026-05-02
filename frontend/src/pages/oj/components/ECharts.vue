<template>
  <div ref="chartRoot" class="vue3-echarts"></div>
</template>

<script>
  import echarts from '@/utils/echarts'

  export default {
    name: 'ECharts',
    props: {
      options: {
        type: Object,
        default: () => ({})
      },
      initOptions: {
        type: Object,
        default: () => ({})
      },
      autoResize: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        chart: null
      }
    },
    mounted () {
      this.$nextTick(() => {
        this.initChart()
      })
      if (this.autoResize) {
        window.addEventListener('resize', this.resizeChart)
      }
    },
    beforeUnmount () {
      if (this.autoResize) {
        window.removeEventListener('resize', this.resizeChart)
      }
      if (this.chart) {
        this.chart.dispose()
        this.chart = null
      }
    },
    methods: {
      initChart () {
        if (!this.$refs.chartRoot) return
        if (this.chart) {
          this.chart.dispose()
        }
        this.chart = echarts.init(this.$refs.chartRoot, null, this.initOptions || {})
        this.chart.setOption(this.options || {}, true)
      },
      resizeChart () {
        if (this.chart) {
          this.chart.resize()
        }
      }
    },
    watch: {
      options: {
        deep: true,
        handler (val) {
          if (!this.chart) {
            this.initChart()
            return
          }
          this.chart.setOption(val || {}, true)
        }
      },
      initOptions: {
        deep: true,
        handler () {
          this.initChart()
        }
      }
    }
  }
</script>

<style scoped>
  .vue3-echarts {
    width: 100%;
    height: 100%;
  }
</style>

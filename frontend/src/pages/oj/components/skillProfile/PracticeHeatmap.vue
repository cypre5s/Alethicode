<template>
  <div
    class="practice-heatmap"
    role="region"
    aria-label="Submission Heatmap"
    :style="heatmapVars"
  >
    <div v-if="loading && !safeHeatmapData.total_ac" class="heatmap-loading-overlay">
      <div class="skeleton-loading" style="width: 100%; height: 100%; min-height: 180px;"></div>
    </div>

    <div v-if="error && !loading" class="heatmap-error">
      <div class="error-content">
        <el-icon :size="32" class="error-icon"><Connection /></el-icon>
        <p>数据加载失败</p>
        <el-button type="primary" size="small" @click="$emit('retry')">重试</el-button>
      </div>
    </div>

    <div v-else class="heatmap-content" :class="{ 'is-stale': loading }">
      <div class="heatmap-stats">
        <div class="stat-item">
          <div class="stat-value">{{ safeHeatmapData.total_ac || 0 }}</div>
          <div class="stat-label">累计通过</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ safeHeatmapData.active_days || 0 }}</div>
          <div class="stat-label">活跃天数</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ safeHeatmapData.max_streak || 0 }}</div>
          <div class="stat-label">最长连胜</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ safeHeatmapData.current_streak || 0 }}</div>
          <div class="stat-label">当前连胜</div>
        </div>
        <div class="stat-item last-updated" v-if="lastUpdated">
          <div class="stat-value time">{{ lastUpdatedTime }}</div>
          <div class="stat-label">最后更新</div>
        </div>
      </div>

      <div class="heatmap-container" ref="heatmapContainer">
        <div class="heatmap-scroll-wrapper">
          <div class="heatmap-main" role="grid">
            <div class="weekday-labels" aria-hidden="true">
              <span
                v-for="(label, index) in weekdayRows"
                :key="index"
                class="weekday-label"
              >
                {{ label }}
              </span>
            </div>
            
            <div class="days-grid">
              <el-tooltip
                v-for="(day, index) in processedData"
                :key="index"
                :content="day.tooltip"
                placement="top"
                effect="dark"
                :teleported="true"
                :show-after="60"
                :hide-after="0"
                :enterable="false"
                :persistent="false"
                transition=""
              >
                <div
                  role="gridcell"
                  :aria-label="day.tooltip"
                  :class="['day-cell', `level-${day.level}`]"
                  @click="handleDayClick(day)"
                  :tabindex="0"
                  @keydown.enter="handleDayClick(day)"
                ></div>
              </el-tooltip>
            </div>
          </div>
          
          <div class="month-labels" aria-hidden="true">
            <span v-for="month in monthLabels" :key="month.index" :style="{left: month.position}">
              {{ month.name }}
            </span>
          </div>
        </div>

        <div class="heatmap-legend" aria-hidden="true">
          <span class="legend-label">Less</span>
          <div class="legend-colors">
            <div class="legend-item level-0" title="No contributions"></div>
            <div class="legend-item level-1" title="Low activity"></div>
            <div class="legend-item level-2" title="Moderate activity"></div>
            <div class="legend-item level-3" title="High activity"></div>
            <div class="legend-item level-4" title="Very high activity"></div>
          </div>
          <span class="legend-label">More</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import moment from 'moment'
import { Connection } from '@element-plus/icons-vue'

export default {
  name: 'PracticeHeatmap',
  components: { Connection },
  data () {
    return {
      cellSize: 11,
      cellGap: 3,
      weekdayLabelWidth: 24,
      resizeObserver: null
    }
  },
  props: {
    heatmapData: {
      type: Object,
      default: () => ({})
    },
    loading: {
      type: Boolean,
      default: false
    },
    error: {
      type: Boolean,
      default: false
    },
    lastUpdated: {
      type: Date,
      default: null
    }
  },
  computed: {
    weekCount () {
      const total = this.processedData.length
      if (!total) return 53
      return Math.max(1, Math.ceil(total / 7))
    },
    cellStep () {
      return this.cellSize + this.cellGap
    },
    heatmapVars () {
      return {
        '--hm-cell-size': `${this.cellSize}px`,
        '--hm-cell-gap': `${this.cellGap}px`,
        '--hm-weekday-width': `${this.weekdayLabelWidth}px`
      }
    },
    safeHeatmapData () {
      return this.heatmapData || {
        activity_levels: [],
        dates: [],
        ac_counts: []
      }
    },
    processedData () {
      const levels = this.safeHeatmapData.activity_levels || []
      const dates = this.safeHeatmapData.dates || []
      const counts = this.safeHeatmapData.ac_counts || []

      return dates.map((date, index) => {
        // 后端可能漏算低频活跃等级，前端按提交数补齐颜色层级。
        let level = levels[index] || 0
        const count = counts[index] || 0

        if (count > 0 && level === 0) {
          if (count === 1) level = 1
          else if (count <= 3) level = 2
          else if (count <= 5) level = 3
          else level = 4
        }

        return {
          date: date,
          level: level,
          count: count,
          tooltip: `${moment(date).format('YYYY-MM-DD')}: ${count} submissions`
        }
      })
    },
    monthLabels () {
      const dates = this.safeHeatmapData.dates || []
      if (dates.length === 0) return []

      const labels = []
      let currentMonth = -1
      let lastLabelIndex = -999 // 防标签重叠

      dates.forEach((dateStr, index) => {
        // 月份标签只挂在每列首日，避免同一周重复显示。
        if (index % 7 === 0) {
          const date = moment(dateStr)
          const month = date.month()

          if (month !== currentMonth) {
            // 月份标签至少间隔 4 周，避免小屏重叠。
            if (index - lastLabelIndex > 28) {
              const weekIndex = Math.floor(index / 7)
              labels.push({
                name: date.format('MMM'),
                position: `${weekIndex * this.cellStep}px`,
                index: index
              })
              lastLabelIndex = index
            }
            currentMonth = month
          }
        }
      })
      return labels
    },
    weekdayRows () {
      return ['Mon', '', 'Wed', '', 'Fri', '', '']
    },
    lastUpdatedTime () {
      if (!this.lastUpdated) return ''
      return moment(this.lastUpdated).format('HH:mm:ss')
    }
  },
  methods: {
    recalcLayout () {
      const container = this.$refs.heatmapContainer
      const weeks = this.weekCount
      if (!container || !weeks) return
      const containerWidth = container.clientWidth
      const usableWidth = Math.max(280, containerWidth - this.weekdayLabelWidth - 8)
      const slot = usableWidth / weeks
      const nextGap = slot >= 11 ? 3 : (slot >= 9 ? 2 : 1)
      const nextSize = Math.floor(slot - nextGap)
      this.cellGap = Math.max(1, Math.min(3, nextGap))
      this.cellSize = Math.max(7, Math.min(11, nextSize))
    },
    handleDayClick (day) {
      if (day && day.date) {
        this.$emit('click-day', day)
      }
    }
  },
  watch: {
    processedData () {
      this.$nextTick(() => {
        this.recalcLayout()
      })
    }
  },
  mounted () {
    this.$nextTick(() => {
      this.recalcLayout()
      if (window.ResizeObserver && this.$refs.heatmapContainer) {
        this.resizeObserver = new window.ResizeObserver(() => {
          this.recalcLayout()
        })
        this.resizeObserver.observe(this.$refs.heatmapContainer)
      }
    })
  },
  beforeUnmount () {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect()
      this.resizeObserver = null
    }
  }
}
</script>

<style lang="less" scoped>
.practice-heatmap {
  position: relative;
  padding: 0;
  background: transparent;
  min-height: 180px;

  .heatmap-loading-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 10;
    border-radius: var(--border-radius-md);
    overflow: hidden;
  }

  .heatmap-error {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--bg-base);
    z-index: 5;
    border-radius: var(--border-radius-md);

    .error-content {
      text-align: center;
      color: var(--text-secondary);
      
      .error-icon {
        color: var(--danger-color);
        margin-bottom: 8px;
      }
      
      p {
        margin-bottom: 12px;
        font-size: 14px;
      }
    }
  }

  .heatmap-content {
    transition: opacity 0.3s;
    &.is-stale {
      opacity: 0.6;
      pointer-events: none;
    }
  }

  .heatmap-stats {
    display: flex;
    justify-content: flex-start;
    gap: 32px;
    margin-bottom: 16px;
    padding-bottom: 0;
    border-bottom: none;

    .stat-item {
      display: flex;
      flex-direction: column;
      align-items: flex-start;

      .stat-value {
        font-size: 20px;
        font-weight: 600;
        color: var(--text-primary);
        line-height: 1.2;
        font-family: var(--font-mono);
        
        &.time {
          font-size: 14px;
          color: var(--text-secondary);
          margin-top: 4px;
        }
      }

      .stat-label {
        font-size: 12px;
        color: var(--text-secondary);
        margin-top: 4px;
      }
      
      &.last-updated {
        margin-left: auto;
        align-items: flex-end;
      }
    }
  }

  .heatmap-container {
    width: 100%;
    
    .heatmap-scroll-wrapper {
      overflow-x: hidden;
      padding-bottom: 10px;
    }

    .month-labels {
      position: relative;
      height: 15px;
      margin-top: 6px;
      margin-left: calc(var(--hm-weekday-width) + 4px); // 为星期标签留偏移

      span {
        position: absolute;
        font-size: 10px;
        color: var(--text-secondary);
        font-family: var(--font-sans);
      }
    }

    .heatmap-main {
      display: flex;
    }

    .weekday-labels {
      display: grid;
      grid-template-rows: repeat(7, var(--hm-cell-size));
      grid-gap: var(--hm-cell-gap);
      margin-right: 4px;
      width: var(--hm-weekday-width);

      .weekday-label {
        display: flex;
        align-items: center;
        font-size: 9px;
        line-height: 1;
        color: var(--text-secondary);
        font-family: var(--font-sans);
      }
    }

    .days-grid {
      display: grid;
      grid-template-rows: repeat(7, var(--hm-cell-size));
      grid-auto-flow: column;
      grid-gap: var(--hm-cell-gap);

      .day-cell {
        width: var(--hm-cell-size);
        height: var(--hm-cell-size);
        border-radius: 2px;
        background-color: var(--bg-panel);
        transition: transform 0.1s;
        
        /* 贡献度绿色：0 空，4 最深。 */
        &.level-0 { background-color: #ebedf0; }
        &.level-1 { background-color: #9be9a8; }
        &.level-2 { background-color: #40c463; }
        &.level-3 { background-color: #30a14e; }
        &.level-4 { background-color: #216e39; }

        &:hover, &:focus {
          transform: scale(1.2);
          z-index: 2;
          cursor: pointer;
          border: 1px solid rgba(0,0,0,0.1);
        }
      }
    }

    .heatmap-legend {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      margin-top: 8px;
      font-size: 10px;
      color: var(--text-secondary);

      .legend-colors {
        display: flex;
        gap: 3px;
        margin: 0 5px;

        .legend-item {
          width: 11px;
          height: 11px;
          border-radius: 2px;
        }
        
        .level-0 { background-color: #ebedf0; }
        .level-1 { background-color: #9be9a8; }
        .level-2 { background-color: #40c463; }
        .level-3 { background-color: #30a14e; }
        .level-4 { background-color: #216e39; }
      }
    }
  }
}
</style>

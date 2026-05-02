<template>
  <div class="ncv-root">
    <div class="ncv-toolbar">
      <button type="button" class="ncv-nav-btn" @click="shiftMonth(-1)" aria-label="上一月">&lsaquo;</button>
      <div class="ncv-month-label">{{ year }} 年 {{ month + 1 }} 月</div>
      <button type="button" class="ncv-nav-btn" @click="shiftMonth(1)" aria-label="下一月">&rsaquo;</button>
      <button type="button" class="ncv-today-btn" @click="goToToday">今天</button>
    </div>

    <div class="ncv-week-row">
      <div v-for="w in weekHeaders" :key="w" class="ncv-week-head">{{ w }}</div>
    </div>

    <div class="ncv-grid">
      <NotebookCalendarCell
        v-for="cell in gridCells"
        :key="cell.dateKey"
        :date-key="cell.dateKey"
        :day-date="cell.date"
        :is-current-month="cell.isCurrentMonth"
        :is-today="cell.isToday"
        :day-items="dayItemsByKey[cell.dateKey] || []"
        @open-day="$emit('open-day', $event)"
      />
    </div>

    <div class="ncv-legend">
      <span class="ncv-lg ncv-lg-danger"></span> 今日待复习
      <span class="ncv-lg ncv-lg-primary"></span> 计划复习
      <span class="ncv-lg ncv-lg-success"></span> 已掌握
    </div>
  </div>
</template>

<script>
import NotebookCalendarCell from './NotebookCalendarCell.vue'
import { toLocalDateKey } from './notebookFormatters.js'

const WEEK_HEADERS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function startOfMonth (year, month) {
  return new Date(year, month, 1)
}

function buildGrid (year, month, todayKey) {
  const first = startOfMonth(year, month)
  const startWeekday = first.getDay()
  const cells = []
  const start = new Date(first)
  start.setDate(first.getDate() - startWeekday)
  for (let i = 0; i < 42; i++) {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    const key = toLocalDateKey(d)
    cells.push({
      date: d,
      dateKey: key,
      isCurrentMonth: d.getMonth() === month,
      isToday: key === todayKey
    })
  }
  return cells
}

export default {
  name: 'NotebookCalendarView',
  components: { NotebookCalendarCell },
  emits: ['open-day'],
  props: {
    /** Array<{ kind: 'review' | 'entry', date_key: 'YYYY-MM-DD', ...payload }> */
    items: { type: Array, default: () => [] }
  },
  data () {
    const now = new Date()
    return {
      year: now.getFullYear(),
      month: now.getMonth(),
      weekHeaders: WEEK_HEADERS
    }
  },
  computed: {
    todayKey () { return toLocalDateKey(new Date()) },
    gridCells () { return buildGrid(this.year, this.month, this.todayKey) },
    dayItemsByKey () {
      const map = {}
      for (const item of this.items) {
        const key = item.date_key || ''
        if (!key) continue
        if (!map[key]) map[key] = []
        map[key].push(item)
      }
      return map
    }
  },
  methods: {
    shiftMonth (delta) {
      const next = new Date(this.year, this.month + delta, 1)
      this.year = next.getFullYear()
      this.month = next.getMonth()
    },
    goToToday () {
      const now = new Date()
      this.year = now.getFullYear()
      this.month = now.getMonth()
    }
  }
}
</script>

<style lang="less" scoped>
.ncv-root {
  background: #fff; border: 1px solid #e8eaed; border-radius: 12px;
  padding: 16px 18px; box-shadow: 0 1px 3px rgba(0,0,0,.05);
}
.ncv-toolbar {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 12px;
}
.ncv-month-label { font-size: 15px; font-weight: 700; color: #1a1d2e; }
.ncv-nav-btn {
  width: 28px; height: 28px; border-radius: 6px;
  border: 1px solid #e8eaed; background: #fff;
  font-size: 18px; line-height: 1; color: #475569;
  cursor: pointer; font-family: inherit;
  &:hover { background: #f1f5f9; color: #1a73e8; }
}
.ncv-today-btn {
  margin-left: auto; padding: 6px 12px; border-radius: 6px;
  border: 1px solid #1a73e8; background: #fff; color: #1a73e8;
  font-size: 12px; font-weight: 600; cursor: pointer; font-family: inherit;
  &:hover { background: #e0e7ff; }
}

.ncv-week-row {
  display: grid; grid-template-columns: repeat(7, 1fr);
  gap: 6px; margin-bottom: 4px;
}
.ncv-week-head {
  font-size: 11px; font-weight: 600; color: #64748b;
  text-align: center; padding: 4px 0;
}

.ncv-grid {
  display: grid; grid-template-columns: repeat(7, 1fr);
  gap: 6px;
}

.ncv-legend {
  margin-top: 14px; display: flex; gap: 10px; align-items: center;
  font-size: 11px; color: #64748b;
}
.ncv-lg { width: 12px; height: 12px; border-radius: 999px; display: inline-block; margin-left: 12px; }
.ncv-lg:first-of-type { margin-left: 0; }
.ncv-lg-danger { background: #fee2e2; border: 1px solid #fecaca; }
.ncv-lg-primary { background: #e0e7ff; border: 1px solid #c7d2fe; }
.ncv-lg-success { background: #dcfce7; border: 1px solid #bbf7d0; }
</style>

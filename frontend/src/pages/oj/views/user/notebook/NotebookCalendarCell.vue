<template>
  <button
    type="button"
    :class="['notebook-cell', cellModifiers]"
    :data-date="dateKey"
    @click="$emit('open-day', { dateKey, dayItems, dayDate })"
  >
    <div class="ncc-num">{{ dayDate.getDate() }}</div>
    <div v-if="visibleChips.length" class="ncc-chips">
      <span
        v-for="chip in visibleChips"
        :key="chip.key"
        :class="['ncc-chip', 'ncc-chip-' + chip.tone]"
        :title="chip.tooltip"
      >{{ chip.label }}</span>
      <span v-if="overflowCount > 0" class="ncc-chip ncc-chip-overflow">+{{ overflowCount }}</span>
    </div>
  </button>
</template>

<script>
const MAX_CHIP = 3

export default {
  name: 'NotebookCalendarCell',
  emits: ['open-day'],
  props: {
    dateKey: { type: String, required: true },
    dayDate: { type: Date, required: true },
    isCurrentMonth: { type: Boolean, default: true },
    isToday: { type: Boolean, default: false },
    dayItems: { type: Array, default: () => [] }
  },
  computed: {
    chips () {
      return this.dayItems.map((item) => {
        const isReview = item.kind === 'review'
        const label = item.label || (isReview ? '复习' : '错题')
        let tone = 'primary'
        if (isReview) {
          if (item.last_package_mastery) tone = 'success'
          else if (item.is_due) tone = 'danger'
          else tone = 'primary'
        } else {
          tone = 'danger'
        }
        const tooltip = (() => {
          const parts = []
          if (item.label) parts.push(item.label)
          if (isReview) {
            if (typeof item.stability === 'number') parts.push('稳定度 ' + item.stability.toFixed(2))
            if (typeof item.retrievability === 'number') parts.push('回忆度 ' + item.retrievability.toFixed(2))
          }
          if (item.summary) parts.push(item.summary)
          return parts.join(' · ')
        })()
        return {
          key: item.id || item.key || ((item.kind || 'i') + '-' + label),
          label,
          tone,
          tooltip
        }
      })
    },
    visibleChips () { return this.chips.slice(0, MAX_CHIP) },
    overflowCount () { return Math.max(0, this.chips.length - MAX_CHIP) },
    cellModifiers () {
      return {
        'is-other-month': !this.isCurrentMonth,
        'is-today': this.isToday,
        'is-due': this.dayItems.some(it => it.kind === 'review' && it.is_due),
        'has-items': this.dayItems.length > 0
      }
    }
  }
}
</script>

<style lang="less" scoped>
.notebook-cell {
  display: flex; flex-direction: column; gap: 4px;
  background: #fff; border: 1px solid #f1f5f9; border-radius: 8px;
  min-height: 80px; padding: 6px 8px; cursor: pointer;
  text-align: left; font-family: inherit; transition: border-color .15s, box-shadow .15s;
  &:hover { border-color: #c7d2fe; box-shadow: 0 1px 4px rgba(99, 102, 241, 0.12); }
  &.is-other-month { background: #fafbfc; opacity: 0.55; }
  &.is-today { border-color: #1a73e8; box-shadow: inset 0 0 0 1px rgba(26, 115, 232, 0.32); }
  &.is-due { border-color: rgba(239, 68, 68, 0.45); }
}
.ncc-num { font-size: 12px; font-weight: 600; color: #64748b; }
.notebook-cell.is-today .ncc-num { color: #1a73e8; }

.ncc-chips { display: flex; flex-wrap: wrap; gap: 3px; margin-top: auto; }
.ncc-chip {
  display: inline-flex; align-items: center;
  border-radius: 999px; padding: 1px 7px;
  font-size: 10px; font-weight: 600; line-height: 1.5;
  border: 1px solid transparent;
}
.ncc-chip-primary { background: #e0e7ff; color: #4f46e5; border-color: #c7d2fe; }
.ncc-chip-danger { background: #fee2e2; color: #b91c1c; border-color: #fecaca; }
.ncc-chip-success { background: #dcfce7; color: #15803d; border-color: #bbf7d0; }
.ncc-chip-overflow { background: #f1f5f9; color: #475569; border-color: #e2e8f0; }
</style>

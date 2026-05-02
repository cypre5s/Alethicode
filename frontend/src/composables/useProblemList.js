import { ref } from 'vue'
import utils from '@/utils/utils'

export function useProblemList () {
  const statusColumn = ref(false)

  function getACRate (ACCount, TotalCount) {
    return utils.getACRate(ACCount, TotalCount)
  }

  function addStatusColumn (tableColumns, dataProblems) {
    if (statusColumn.value) return
    let needAdd = dataProblems.some((item) => {
      if (item.my_status !== null && item.my_status !== undefined) {
        return true
      }
    })
    if (!needAdd) return
    tableColumns.splice(0, 0, {
      width: 60,
      title: ' ',
      render: (h, params) => {
        let status = params.row.my_status
        if (status === null || status === undefined) {
          return undefined
        }
        const isAccepted = status === 0
        return h('span', {
          class: `problem-status-indicator ${isAccepted ? 'problem-status-indicator-pass' : 'problem-status-indicator-fail'}`,
          title: isAccepted ? '已通过' : '未通过',
          'aria-label': isAccepted ? '已通过' : '未通过'
        }, isAccepted ? '✓' : '−')
      }
    })
    statusColumn.value = true
  }

  return { statusColumn, getACRate, addStatusColumn }
}

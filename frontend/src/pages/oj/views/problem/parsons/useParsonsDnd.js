import { ref, computed, watch } from 'vue'

const DATA_TYPE = 'text/parsons-block-id'

function shuffleInPlace(arr) {
  const a = arr.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
      ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

export function useParsonsDnd({ blocks, distractors, onChange }) {
  const poolOrder = ref([])
  const answerOrder = ref([])
  const draggingId = ref(null)
  const focusedZone = ref('pool')

  const allMap = computed(() => {
    const map = {}
    for (const block of blocks.value || []) map[block.id] = block
    for (const distractor of distractors.value || []) map[distractor.id] = distractor
    return map
  })

  const distractorIdSet = computed(() => {
    const set = new Set()
    for (const distractor of distractors.value || []) set.add(distractor.id)
    return set
  })

  const poolBlocks = computed(() =>
    poolOrder.value.map((id) => allMap.value[id]).filter(Boolean)
  )

  const answerBlocks = computed(() =>
    answerOrder.value.map((id) => allMap.value[id]).filter(Boolean)
  )

  function isDistractor(id) {
    return distractorIdSet.value.has(id)
  }

  function findBlock(id) {
    return allMap.value[id] || null
  }

  function emitOrder() {
    if (typeof onChange === 'function') {
      onChange(answerOrder.value.slice())
    }
  }

  function reset() {
    const ids = [
      ...((blocks.value || []).map((block) => block.id)),
      ...((distractors.value || []).map((distractor) => distractor.id))
    ]
    poolOrder.value = shuffleInPlace(ids)
    answerOrder.value = []
    draggingId.value = null
    emitOrder()
  }

  watch([blocks, distractors], () => {
    reset()
  }, { immediate: true, deep: false })

  function removeFrom(id) {
    poolOrder.value = poolOrder.value.filter((x) => x !== id)
    answerOrder.value = answerOrder.value.filter((x) => x !== id)
  }

  function moveTo(zone, id) {
    if (!findBlock(id)) return
    removeFrom(id)
    if (zone === 'pool') {
      poolOrder.value = [...poolOrder.value, id]
    } else {
      answerOrder.value = [...answerOrder.value, id]
    }
    emitOrder()
  }

  function moveToIndex(zone, id, index) {
    if (!findBlock(id)) return
    removeFrom(id)
    if (zone === 'pool') {
      const next = poolOrder.value.slice()
      next.splice(index, 0, id)
      poolOrder.value = next
    } else {
      const next = answerOrder.value.slice()
      next.splice(index, 0, id)
      answerOrder.value = next
    }
    emitOrder()
  }

  function onBlockDragStart(id, event) {
    if (event && event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move'
      event.dataTransfer.setData(DATA_TYPE, id)
    }
    draggingId.value = id
  }

  function onBlockDragEnd() {
    draggingId.value = null
  }

  function readDropId(event) {
    if (event && event.dataTransfer) {
      const fromEvent = event.dataTransfer.getData(DATA_TYPE)
      if (fromEvent) return fromEvent
    }
    return draggingId.value
  }

  function onZoneDrop(zone, event) {
    const id = readDropId(event)
    if (!id) return
    moveTo(zone, id)
  }

  function onIndexDrop(zone, index, event) {
    const id = readDropId(event)
    if (!id) return
    moveToIndex(zone, id, index)
  }

  function onKeyboardAction({ id, key }) {
    if (!findBlock(id)) return
    if (key === 'toggle') {
      if (answerOrder.value.includes(id)) moveTo('pool', id)
      else moveTo('answer', id)
      return
    }
    if (key === 'ArrowUp' || key === 'ArrowDown') {
      const current = answerOrder.value
      if (!current.includes(id)) return
      const i = current.indexOf(id)
      const j = key === 'ArrowUp' ? i - 1 : i + 1
      if (j < 0 || j >= current.length) return
      const next = current.slice()
      next[i] = current[j]
      next[j] = id
      answerOrder.value = next
      emitOrder()
    }
  }

  function getOrderedBlockIds() {
    return answerOrder.value.slice()
  }

  function findFirstMisplaced(referenceOrder) {
    if (!Array.isArray(referenceOrder) || referenceOrder.length === 0) return null
    const expected = referenceOrder.slice()
    const actual = answerOrder.value.slice()
    const len = Math.min(expected.length, actual.length)
    for (let i = 0; i < len; i++) {
      if (expected[i] !== actual[i]) return actual[i]
    }
    if (actual.length > expected.length) return actual[expected.length]
    return null
  }

  return {
    poolOrder,
    answerOrder,
    poolBlocks,
    answerBlocks,
    draggingId,
    focusedZone,
    isDistractor,
    findBlock,
    reset,
    moveTo,
    moveToIndex,
    onBlockDragStart,
    onBlockDragEnd,
    onZoneDrop,
    onIndexDrop,
    onKeyboardAction,
    getOrderedBlockIds,
    findFirstMisplaced
  }
}

export const PARSONS_DRAG_DATA_TYPE = DATA_TYPE

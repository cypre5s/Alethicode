<template>
  <div class="ptr">
    <div class="ptr-board">
      <div class="ptr-col ptr-pool">
        <div class="ptr-col-title">候选块</div>
        <div
          ref="poolRef"
          class="ptr-zone ptr-zone-pool"
          role="list"
          aria-label="候选代码块"
          @dragover.prevent="onZoneDragOver"
          @drop.prevent="onZoneDrop('pool', $event)"
        >
          <ParsonsTokenBlock
            v-for="block in poolBlocks"
            :key="block.id"
            :block="block"
            :is-distractor="isDistractor(block.id)"
            :misplaced="block.id === misplacedId"
            :flagged-reason="distractorReasonFor(block.id)"
            @drag-start="onBlockDragStart"
            @drag-end="onBlockDragEnd"
            @keyboard-action="onKeyboardAction"
          />
          <div v-if="poolBlocks.length === 0" class="ptr-empty">候选区已空，把多余块拖回这里。</div>
        </div>
      </div>

      <div class="ptr-col ptr-answer">
        <div class="ptr-col-title">答题区</div>
        <ol
          ref="answerRef"
          class="ptr-zone ptr-zone-answer"
          role="list"
          aria-label="答题排序区"
          @dragover.prevent="onZoneDragOver"
          @drop.prevent="onZoneDrop('answer', $event)"
        >
          <li
            v-for="(blockId, idx) in answerOrder"
            :key="blockId"
            class="ptr-answer-item"
            @dragover.prevent
            @drop.prevent="onIndexDrop('answer', idx, $event)"
          >
            <span class="ptr-answer-idx">{{ idx + 1 }}</span>
            <ParsonsTokenBlock
              :block="findBlock(blockId)"
              :is-distractor="isDistractor(blockId)"
              :misplaced="blockId === misplacedId"
              :flagged-reason="distractorReasonFor(blockId)"
              @drag-start="onBlockDragStart"
              @drag-end="onBlockDragEnd"
              @keyboard-action="onKeyboardAction"
            />
          </li>
          <li v-if="answerOrder.length === 0" class="ptr-empty">把候选块拖到这里，按你认为的执行顺序排列。</li>
        </ol>
      </div>
    </div>

    <div v-if="hint" class="ptr-hint" role="status" aria-live="polite">{{ hint }}</div>
  </div>
</template>

<script>
import { computed, ref, toRef, watch } from 'vue'
import ParsonsTokenBlock from './ParsonsTokenBlock.vue'
import { useParsonsDnd } from '../../parsons/useParsonsDnd.js'

export default {
  name: 'ParsonsRenderer',
  components: { ParsonsTokenBlock },
  props: {
    blocks: { type: Array, required: true },
    distractors: { type: Array, default: () => [] },
    hint: { type: String, default: '' },
    misplacedId: { type: String, default: '' },
    revealDistractorReasons: { type: Boolean, default: false }
  },
  emits: ['order-change', 'reset-request'],
  setup (props, { emit }) {
    const blocksRef = toRef(props, 'blocks')
    const distractorsRef = toRef(props, 'distractors')

    const dnd = useParsonsDnd({
      blocks: blocksRef,
      distractors: distractorsRef,
      onChange: (order) => emit('order-change', order)
    })

    const poolRef = ref(null)
    const answerRef = ref(null)

    const distractorReasonMap = computed(() => {
      if (!props.revealDistractorReasons) return {}
      const map = {}
      for (const distractor of distractorsRef.value || []) {
        if (distractor && distractor.kc_hint) {
          map[distractor.id] = distractor.kc_hint
        }
      }
      return map
    })

    function distractorReasonFor (id) {
      return distractorReasonMap.value[id] || ''
    }

    function onZoneDragOver () { /* 拖拽视觉反馈扩展点 */ }

    watch(() => props.misplacedId, (id) => {
      if (!id) return
      // 当外部要求高亮错位 block 时，等下一帧把焦点移到该 block 方便 a11y 用户感知
      // 不强制滚动，避免破坏正在进行的拖拽动作
    })

    return {
      poolRef,
      answerRef,
      poolBlocks: dnd.poolBlocks,
      answerOrder: dnd.answerOrder,
      isDistractor: dnd.isDistractor,
      findBlock: dnd.findBlock,
      onBlockDragStart: dnd.onBlockDragStart,
      onBlockDragEnd: dnd.onBlockDragEnd,
      onZoneDrop: dnd.onZoneDrop,
      onIndexDrop: dnd.onIndexDrop,
      onKeyboardAction: dnd.onKeyboardAction,
      onZoneDragOver,
      distractorReasonFor,
      reset: dnd.reset
    }
  }
}
</script>

<style lang="less" scoped>
.ptr {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.ptr-board {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}
@media (max-width: 720px) {
  .ptr-board { grid-template-columns: 1fr; }
}
.ptr-col {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.ptr-col-title {
  font-size: var(--fs-sm);
  color: var(--warm-primary);
  font-weight: 600;
}
.ptr-zone {
  border: 1px dashed rgba(99, 102, 241, 0.30);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
  min-height: 200px;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  background: rgba(99, 102, 241, 0.02);
}
.ptr-zone-answer {
  list-style: none;
  margin: 0;
  padding: var(--space-2);
  background: rgba(16, 185, 129, 0.04);
  border-color: rgba(16, 185, 129, 0.30);
}
.ptr-answer-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.ptr-answer-idx {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-pill);
  background: rgba(16, 185, 129, 0.18);
  color: #047857;
  font-size: var(--fs-sm);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ptr-answer-item > :deep(.ptb) {
  flex: 1;
}
.ptr-empty {
  color: var(--text-disabled);
  font-size: var(--fs-sm);
  padding: var(--space-2);
}
.ptr-hint {
  background: rgba(245, 158, 11, 0.10);
  border: 1px solid rgba(245, 158, 11, 0.45);
  color: #92400e;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
}
</style>

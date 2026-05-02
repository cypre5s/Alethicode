<template>
  <BaseAgentCard v-if="data" accent="ideate" :icon="puzzleIcon" title="拼装挑战">
    <template #head-extra>
      <span class="ppc-level">L{{ data.fading_level }}</span>
    </template>

    <template #body>
      <div v-if="originBadges.length" class="ppc-origin-row">
        <span
          v-for="badge in originBadges"
          :key="badge.id"
          :class="['ppc-origin-pill', `ppc-origin-pill--${badge.tone}`]"
          :title="badge.title || badge.label"
        >{{ badge.label }}</span>
      </div>

      <p class="ppc-instructions">{{ data.instructions }}</p>

      <ParsonsDistractorBin
        v-if="distractors.length"
        :distractors="distractors"
        :initially-expanded="cascadeAttempt >= 2"
      />

      <ParsonsRenderer
        :blocks="blocks"
        :distractors="distractors"
        :hint="hint"
        :misplaced-id="misplacedId"
        :reveal-distractor-reasons="cascadeAttempt >= 2"
        @order-change="onOrderChange"
      />

      <div v-if="cascadeMessage" class="ppc-cascade" :class="cascadeStateClass">
        <ElIcon :size="14" class="ppc-cascade-icon"><WarningFilled /></ElIcon>
        <span>{{ cascadeMessage }}</span>
      </div>

      <details v-if="routingEntries.length" class="ppc-routing">
        <summary class="ppc-routing-summary">
          <span class="ppc-routing-label">本次难度依据</span>
          <span class="ppc-routing-meta">
            <span class="ppc-routing-pill ppc-routing-pill--nfk" v-if="routingCounts.nfk">
              NFK × {{ routingCounts.nfk }}
            </span>
            <span class="ppc-routing-pill ppc-routing-pill--bkt" v-if="routingCounts.bkt">
              BKT × {{ routingCounts.bkt }}
            </span>
          </span>
        </summary>
        <ul class="ppc-routing-list">
          <li
            v-for="entry in routingEntries"
            :key="entry.kc"
            :class="['ppc-routing-item', `ppc-routing-item--${entry.source}`]"
          >
            <span class="ppc-routing-kc">KC #{{ entry.kc }}</span>
            <span class="ppc-routing-source">{{ entry.source.toUpperCase() }}</span>
            <span class="ppc-routing-mastery">
              {{ Math.round((entry.mastery || 0) * 100) }}% 掌握度
            </span>
            <span v-if="entry.fallback_reason" class="ppc-routing-fallback">
              回退原因：{{ fallbackLabel(entry.fallback_reason) }}
            </span>
            <span v-if="entry.source === 'nfk' && entry.nfk_sequence_length" class="ppc-routing-seq">
              序列长度 {{ entry.nfk_sequence_length }}
            </span>
          </li>
        </ul>
      </details>
    </template>

    <template #foot>
      <div class="ppc-foot-row">
        <span class="ppc-attempts">已尝试 {{ attempts }} 次</span>
        <div class="ppc-actions">
          <button type="button" class="ppc-btn ppc-btn-secondary" @click="$emit('reset')" :disabled="submitting">重新打乱</button>
          <button type="button" class="ppc-btn ppc-btn-primary" :disabled="!canSubmit" @click="onSubmit">
            <span v-if="submitting">评估中…</span>
            <span v-else>提交拼装</span>
          </button>
        </div>
      </div>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw, h } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import BaseAgentCard from './BaseAgentCard.vue'
import ParsonsRenderer from './parsons/ParsonsRenderer.vue'
import ParsonsDistractorBin from '../parsons/ParsonsDistractorBin.vue'

const PuzzleIcon = {
  render () {
    return h('svg', {
      width: '14', height: '14', viewBox: '0 0 24 24', fill: 'none',
      stroke: 'currentColor', 'stroke-width': '2',
      'stroke-linecap': 'round', 'stroke-linejoin': 'round'
    }, [
      h('path', { d: 'M19 11h-1V7a3 3 0 0 0-3-3h-4a3 3 0 0 0-3 3v0a3 3 0 0 1-3 3H4a3 3 0 0 0-3 3v4a3 3 0 0 0 3 3h2v-1a3 3 0 0 1 6 0v1h4a3 3 0 0 0 3-3v-4h1a3 3 0 0 0 0-6z' })
    ])
  }
}

const FALLBACK_LABEL = Object.freeze({
  coverage: 'KC 训练覆盖不足',
  interaction_count: '学情序列长度不足',
  nfk_unavailable: 'NFK 服务暂不可用'
})

export default {
  name: 'ParsonsProblemCard',
  components: { BaseAgentCard, ParsonsRenderer, ParsonsDistractorBin, WarningFilled },
  props: {
    data: { type: Object, default: () => null },
    submitting: { type: Boolean, default: false },
    hint: { type: String, default: '' },
    lastResult: { type: Object, default: () => null }
  },
  emits: ['submit', 'reset'],
  data () {
    return {
      puzzleIcon: markRaw(PuzzleIcon),
      currentOrder: []
    }
  },
  computed: {
    blocks () {
      return (this.data && this.data.blocks) || []
    },
    distractors () {
      return (this.data && this.data.distractors) || []
    },
    canSubmit () {
      if (this.submitting) return false
      const blockIds = this.blocks.map((b) => b.id)
      const orderSet = new Set(this.currentOrder)
      return blockIds.length > 0 && blockIds.every((id) => orderSet.has(id))
    },
    attempts () {
      return (this.lastResult && this.lastResult.attempts) || 0
    },
    cascadeAttempt () {
      if (!this.lastResult) return 0
      if (this.lastResult.cascadeFailfast) return 4
      if (this.lastResult.cascadeDegrade) return 3
      return Math.max(0, this.attempts)
    },
    misplacedId () {
      if (!this.lastResult || this.lastResult.passed) return ''
      return this.lastResult.misplacedBlockId || ''
    },
    cascadeMessage () {
      if (!this.lastResult || this.lastResult.passed) return ''
      if (this.lastResult.cascadeFailfast) {
        return '已多次失败，建议回到错误诊断主链路稍后再来。'
      }
      if (this.lastResult.cascadeDegrade) {
        return '多次未通过，下一次将自动降一级难度并重新发牌。'
      }
      if (this.cascadeAttempt === 2) {
        return '再来一次。注意上面已展开的干扰块来源——这是你过去踩过的坑。'
      }
      if (this.cascadeAttempt === 1 && this.misplacedId) {
        return '位置不对，已为你高亮第一个错位的代码块。'
      }
      return ''
    },
    cascadeStateClass () {
      if (!this.lastResult) return ''
      if (this.lastResult.cascadeFailfast) return 'ppc-cascade--failfast'
      if (this.lastResult.cascadeDegrade) return 'ppc-cascade--degrade'
      if (this.cascadeAttempt >= 2) return 'ppc-cascade--repeat'
      return 'ppc-cascade--first'
    },
    routingEntries () {
      const snapshot = this.data && this.data.mastery_snapshot
      if (!snapshot || !snapshot.routing) return []
      return Object.entries(snapshot.routing).map(([kc, info]) => ({
        kc,
        source: info && info.source ? info.source : 'bkt',
        mastery: info ? info.mastery : 0,
        fallback_reason: info && info.fallback_reason ? info.fallback_reason : '',
        nfk_sequence_length: info && info.nfk_sequence_length ? info.nfk_sequence_length : 0
      }))
    },
    routingCounts () {
      let nfk = 0
      let bkt = 0
      for (const entry of this.routingEntries) {
        if (entry.source === 'nfk') nfk += 1
        else bkt += 1
      }
      return { nfk, bkt }
    },
    originBadges () {
      const badges = []
      const data = this.data || {}
      if (data.fsrs_origin) {
        badges.push({
          id: 'fsrs_origin',
          label: '来自错题包复习',
          tone: 'fsrs',
          title: 'FSRS 错题包派生的拼装变式'
        })
      }
      if (data.previous_session_id) {
        badges.push({
          id: 'previous_session',
          label: '上次失败复盘',
          tone: 'previous',
          title: '由上一次未通过的拼装自动降级派发'
        })
      }
      return badges
    }
  },
  methods: {
    onOrderChange (order) {
      this.currentOrder = order
    },
    onSubmit () {
      if (!this.canSubmit) return
      this.$emit('submit', this.currentOrder.slice())
    },
    fallbackLabel (key) {
      return FALLBACK_LABEL[key] || key
    }
  }
}
</script>

<style lang="less" scoped>
.ppc-instructions {
  margin: var(--space-2) 0;
  font-size: var(--fs-base);
  color: var(--text-strong);
  line-height: var(--leading-body);
}
.ppc-level {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--warm-primary-strong);
  background: rgba(99, 102, 241, 0.14);
  padding: 0 10px;
  border-radius: var(--tag-radius);
}
.ppc-origin-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}
.ppc-origin-pill {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  padding: 0 10px;
  border-radius: var(--tag-radius);
  font-size: var(--fs-xs);
  font-weight: 600;
  border: 1px solid transparent;
}
.ppc-origin-pill--fsrs {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.30);
}
.ppc-origin-pill--previous {
  background: rgba(99, 102, 241, 0.12);
  color: var(--warm-primary-strong);
  border-color: rgba(99, 102, 241, 0.30);
}
.ppc-cascade {
  margin-top: var(--space-2);
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  border: 1px solid transparent;
}
.ppc-cascade-icon {
  flex-shrink: 0;
}
.ppc-cascade--first {
  background: rgba(245, 158, 11, 0.08);
  border-color: rgba(245, 158, 11, 0.30);
  color: #92400e;
}
.ppc-cascade--repeat {
  background: rgba(245, 158, 11, 0.14);
  border-color: rgba(245, 158, 11, 0.45);
  color: #92400e;
}
.ppc-cascade--degrade {
  background: rgba(99, 102, 241, 0.10);
  border-color: rgba(99, 102, 241, 0.30);
  color: var(--warm-primary-strong);
}
.ppc-cascade--failfast {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.30);
  color: #b91c1c;
}
.ppc-routing {
  margin-top: var(--space-3);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-panel);
  font-size: var(--fs-sm);
}
.ppc-routing > summary {
  list-style: none;
}
.ppc-routing-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 8px var(--space-3);
  cursor: pointer;
  min-height: var(--control-height-md);
  font-weight: 600;
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
}
.ppc-routing-summary::-webkit-details-marker { display: none; }
.ppc-routing-summary::marker { content: ''; }
.ppc-routing[open] .ppc-routing-summary {
  border-bottom: 1px solid var(--border-default);
}
.ppc-routing-summary:focus-visible {
  outline: 2px solid var(--warm-primary);
  outline-offset: 2px;
}
.ppc-routing-meta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.ppc-routing-pill {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  padding: 0 8px;
  border-radius: var(--tag-radius);
  font-size: var(--fs-xs);
  font-weight: 600;
}
.ppc-routing-pill--nfk {
  background: var(--primary-50);
  color: var(--primary-700);
}
.ppc-routing-pill--bkt {
  background: rgba(100, 116, 139, 0.14);
  color: var(--text-secondary);
}
.ppc-routing-list {
  list-style: none;
  margin: 0;
  padding: var(--space-2) var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.ppc-routing-item {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  gap: var(--space-2);
  align-items: center;
  font-size: var(--fs-sm);
  color: var(--text-strong);
}
.ppc-routing-item--nfk .ppc-routing-source {
  color: var(--primary-700);
}
.ppc-routing-item--bkt .ppc-routing-source {
  color: var(--text-secondary);
}
.ppc-routing-kc {
  font-family: var(--font-mono);
  color: var(--text-secondary);
}
.ppc-routing-source {
  font-weight: 700;
  letter-spacing: 0.04em;
  font-size: var(--fs-xs);
}
.ppc-routing-mastery {
  font-feature-settings: 'tnum';
  color: var(--text-strong);
}
.ppc-routing-fallback,
.ppc-routing-seq {
  grid-column: 1 / -1;
  font-size: var(--fs-xs);
  color: var(--text-secondary);
}
.ppc-foot-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: var(--space-3);
}
.ppc-attempts {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
}
.ppc-actions {
  display: flex;
  gap: var(--space-2);
}
.ppc-btn {
  padding: 0 18px;
  min-height: var(--control-height-lg);
  font-size: var(--fs-base);
  font-weight: 600;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background var(--motion-fast), box-shadow var(--motion-fast);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
}
.ppc-btn:focus-visible {
  outline: 2px solid var(--warm-primary);
  outline-offset: 2px;
}
.ppc-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ppc-btn-primary {
  background: var(--warm-primary);
  color: #fff;
}
.ppc-btn-primary:hover:not(:disabled) { background: var(--warm-primary-strong); }
.ppc-btn-secondary {
  background: var(--bg-panel);
  color: var(--text-secondary);
  border-color: var(--border-default);
}
.ppc-btn-secondary:hover:not(:disabled) {
  background: var(--bg-base);
}
</style>

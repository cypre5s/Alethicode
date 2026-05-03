<template>
  <div
    :class="['tl-event', `tl-event--${event.event_kind}`, { 'tl-event--ac': isAc }]"
    :tabindex="0"
    role="listitem"
    :aria-label="event.summary"
    @mouseenter="showCard = true"
    @mouseleave="showCard = false"
    @focus="showCard = true"
    @blur="showCard = false"
    @keydown.enter="$emit('open', event)"
  >
    <span class="tl-event-dot" :style="dotStyle" aria-hidden="true"></span>
    <span class="tl-event-time">{{ formattedTime }}</span>

    <transition name="tl-card-fade">
      <div v-if="showCard" class="tl-event-card" role="tooltip">
        <div class="tl-event-card__kind">{{ kindLabel }}</div>
        <div v-if="event.problem_title" class="tl-event-card__title">{{ event.problem_title }}</div>
        <div class="tl-event-card__summary">{{ event.summary }}</div>
        <button class="tl-event-card__cta" type="button" @click="$emit('open', event)">查看详情</button>
      </div>
    </transition>
  </div>
</template>

<script>
const KIND_LABELS = {
  submission: '代码提交',
  memory: '学习记忆',
  ai_event: 'AI 事件',
  notebook: '错题笔记'
}

const KIND_COLORS = {
  submission_ac: '#10B981',
  submission: '#EF4444',
  memory: '#F39A2C',
  ai_event: '#0F4C81',
  notebook: '#6B7280'
}

export default {
  name: 'LearningTimelineEvent',
  props: {
    event: { type: Object, required: true }
  },
  emits: ['open'],
  data () {
    return { showCard: false }
  },
  computed: {
    isAc () {
      return this.event.event_kind === 'submission' && this.event.meta && this.event.meta.is_ac
    },
    dotStyle () {
      const colorKey = this.isAc ? 'submission_ac' : this.event.event_kind
      return { backgroundColor: KIND_COLORS[colorKey] || KIND_COLORS.notebook }
    },
    kindLabel () {
      return KIND_LABELS[this.event.event_kind] || '学习事件'
    },
    formattedTime () {
      if (!this.event.event_at) return ''
      const d = new Date(this.event.event_at)
      const month = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const hours = String(d.getHours()).padStart(2, '0')
      const minutes = String(d.getMinutes()).padStart(2, '0')
      return `${month}-${day} ${hours}:${minutes}`
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.tl-event {
  position: relative;
  display: flex;
  align-items: center;
  gap: @l99-sp-3;
  padding: @l99-sp-2 @l99-sp-3;
  border-radius: @l99-radius-sm;
  cursor: pointer;
  transition: background-color @l99-dur-fast @l99-ease;

  &:hover, &:focus-visible {
    background-color: @l99-neutral-100;
    outline: none;
  }
}

.tl-event-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.tl-event-time {
  flex-shrink: 0;
  font-family: @l99-font-mono;
  font-size: @l99-fs-xs;
  color: @l99-neutral-500;
  min-width: 88px;
}

.tl-event-card {
  position: absolute;
  left: 100%;
  top: 50%;
  transform: translateY(-50%);
  margin-left: @l99-sp-2;
  width: 240px;
  padding: @l99-sp-3 @l99-sp-4;
  background: #fff;
  border-radius: @l99-radius-md;
  box-shadow: @l99-shadow-2;
  z-index: 10;

  &__kind {
    font-size: @l99-fs-xs;
    color: @l99-neutral-500;
    margin-bottom: @l99-sp-1;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  &__title {
    font-size: @l99-fs-md;
    font-weight: 600;
    color: @l99-neutral-900;
    margin-bottom: @l99-sp-1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &__summary {
    font-size: @l99-fs-sm;
    color: @l99-neutral-700;
    line-height: 1.5;
    margin-bottom: @l99-sp-2;
  }
  &__cta {
    display: inline-block;
    font-size: @l99-fs-sm;
    color: @l99-primary;
    background: none;
    border: none;
    padding: 0;
    cursor: pointer;
    font-weight: 500;
    &:hover { text-decoration: underline; }
  }
}

.tl-card-fade-enter-active,
.tl-card-fade-leave-active {
  transition: opacity @l99-dur-mid @l99-ease, transform @l99-dur-mid @l99-ease;
}
.tl-card-fade-enter-from,
.tl-card-fade-leave-to {
  opacity: 0;
  transform: translateY(-50%) translateX(-4px);
}

@media (max-width: 767px) {
  .tl-event-card {
    left: 0;
    top: 100%;
    transform: none;
    margin-left: 0;
    margin-top: @l99-sp-1;
    width: 100%;
  }
}
</style>

<template>
  <div
    :class="['em-exhibit', { 'em-exhibit--empty': !pin }]"
    :tabindex="0"
    role="listitem"
    :aria-label="pin ? pin.memory_key : '空展位'"
    @dblclick="pin && startEditAnnotation()"
    @keydown.enter="pin && startEditAnnotation()"
    @keydown.delete="pin && $emit('unpin', pin.pin_id)"
  >
    <template v-if="pin">
      <div class="em-exhibit__icon" aria-hidden="true">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#EF4444" stroke-width="2" stroke-linecap="round">
          <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
      </div>
      <div class="em-exhibit__summary">{{ pin.memory_value || pin.memory_key }}</div>
      <div v-if="pin.problem_title" class="em-exhibit__problem">{{ pin.problem_title }}</div>

      <div v-if="editingAnnotation" class="em-exhibit__edit">
        <textarea
          v-model="annotationDraft"
          class="em-exhibit__textarea"
          maxlength="280"
          rows="2"
          placeholder="我从这里学到了..."
        ></textarea>
        <div class="em-exhibit__edit-actions">
          <button type="button" class="em-exhibit__btn" @click="cancelEditAnnotation">取消</button>
          <button type="button" class="em-exhibit__btn em-exhibit__btn--save" @click="saveAnnotation">保存</button>
        </div>
      </div>
      <blockquote v-else-if="pin.annotation" class="em-exhibit__annotation">
        {{ pin.annotation }}
      </blockquote>

      <button
        type="button"
        class="em-exhibit__unpin"
        aria-label="取消钉选"
        @click.stop="$emit('unpin', pin.pin_id)"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
      </button>
    </template>

    <template v-else>
      <div class="em-exhibit__placeholder">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#6B7280" stroke-width="1.5" stroke-linecap="round">
          <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        <span>钉一个你的错误</span>
      </div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'ErrorMuseumExhibit',
  props: {
    pin: { type: Object, default: null }
  },
  emits: ['unpin', 'update-annotation'],
  data () {
    return {
      editingAnnotation: false,
      annotationDraft: ''
    }
  },
  methods: {
    startEditAnnotation () {
      this.annotationDraft = this.pin.annotation || ''
      this.editingAnnotation = true
    },
    cancelEditAnnotation () {
      this.editingAnnotation = false
    },
    saveAnnotation () {
      this.$emit('update-annotation', {
        pinId: this.pin.pin_id,
        annotation: this.annotationDraft.trim()
      })
      this.editingAnnotation = false
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.em-exhibit {
  position: relative;
  width: 240px;
  min-height: 280px;
  padding: @l99-sp-4;
  border-radius: @l99-radius-md;
  box-shadow: @l99-shadow-2;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: @l99-sp-2;
  transition: transform @l99-dur-mid @l99-ease, box-shadow @l99-dur-mid @l99-ease;
  cursor: default;

  &:hover, &:focus-visible {
    transform: translateY(-2px);
    box-shadow: @l99-shadow-3;
    outline: none;
  }

  &--empty {
    border: 2px dashed @l99-neutral-200;
    box-shadow: none;
    justify-content: center;
    align-items: center;
    cursor: pointer;
    &:hover { border-color: @l99-primary; }
  }

  &__icon { flex-shrink: 0; }

  &__summary {
    font-size: @l99-fs-sm;
    color: @l99-neutral-900;
    line-height: 1.5;
    flex: 1;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 4;
    -webkit-box-orient: vertical;
  }

  &__problem {
    font-size: @l99-fs-xs;
    color: @l99-neutral-500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__annotation {
    margin: @l99-sp-2 0 0;
    padding: @l99-sp-2 @l99-sp-3;
    border-left: 3px solid @l99-accent;
    font-size: @l99-fs-xs;
    color: @l99-neutral-700;
    font-style: italic;
    line-height: 1.5;
  }

  &__edit { margin-top: @l99-sp-2; }
  &__textarea {
    width: 100%;
    padding: @l99-sp-2;
    border: 1px solid @l99-neutral-200;
    border-radius: @l99-radius-sm;
    font-size: @l99-fs-xs;
    resize: none;
    &:focus { outline: none; border-color: @l99-primary; }
  }
  &__edit-actions {
    display: flex;
    gap: @l99-sp-2;
    margin-top: @l99-sp-1;
    justify-content: flex-end;
  }
  &__btn {
    padding: 2px 8px;
    border: 1px solid @l99-neutral-200;
    border-radius: @l99-radius-sm;
    background: #fff;
    font-size: @l99-fs-xs;
    cursor: pointer;
    &--save { background: @l99-primary; color: #fff; border-color: @l99-primary; }
  }

  &__unpin {
    position: absolute;
    top: @l99-sp-2;
    right: @l99-sp-2;
    background: none;
    border: none;
    color: @l99-neutral-500;
    cursor: pointer;
    opacity: 0;
    transition: opacity @l99-dur-fast @l99-ease;
    padding: @l99-sp-1;
    border-radius: @l99-radius-sm;
    &:hover { background: @l99-neutral-100; color: @l99-danger; }
  }
  &:hover &__unpin { opacity: 1; }

  &__placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: @l99-sp-2;
    color: @l99-neutral-500;
    font-size: @l99-fs-sm;
  }
}
</style>

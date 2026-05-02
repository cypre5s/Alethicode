<template>
  <div class="steering-bar">
    <button
      type="button"
      class="steering-btn"
      :disabled="disabled || planCompleted || planSurrendered"
      @click="$emit(planPaused ? 'resume' : 'pause')"
    >
      {{ planPaused ? '继续陪练' : '暂停陪练' }}
    </button>
    <button
      type="button"
      class="steering-btn"
      :disabled="disabled || planCompleted || planSurrendered"
      @click="$emit('take-over')"
    >
      我自己继续
    </button>
    <div class="steering-redirect">
      <input
        v-model.trim="redirectInstruction"
        :disabled="disabled || planCompleted || planSurrendered"
        class="steering-input"
        placeholder="想换一种引导方式？"
      >
      <button
        type="button"
        class="steering-btn steering-btn-accent"
        :disabled="disabled || planCompleted || planSurrendered || !redirectInstruction"
        @click="emitRedirect"
      >
        调整引导
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SteeringBar',
  emits: ['pause', 'resume', 'take-over', 'redirect'],
  props: {
    disabled: { type: Boolean, default: false },
    planPaused: { type: Boolean, default: false },
    planCompleted: { type: Boolean, default: false },
    planSurrendered: { type: Boolean, default: false }
  },
  data () {
    return {
      redirectInstruction: ''
    }
  },
  methods: {
    emitRedirect () {
      if (!this.redirectInstruction) return
      this.$emit('redirect', this.redirectInstruction)
      this.redirectInstruction = ''
    }
  }
}
</script>

<style lang="less" scoped>
.steering-bar {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.steering-redirect {
  display: flex;
  gap: 8px;
}

.steering-input {
  min-width: 0;
  flex: 1;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.78);
  color: #334155;
}

.steering-btn {
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(255, 255, 255, 0.78);
  color: #334155;
  border-radius: 10px;
  padding: 8px 10px;
  cursor: pointer;
}

.steering-btn-accent {
  border-color: rgba(245, 158, 11, 0.4);
  color: #92400e;
}

.steering-btn:disabled,
.steering-input:disabled {
  opacity: 0.56;
  cursor: not-allowed;
}
</style>

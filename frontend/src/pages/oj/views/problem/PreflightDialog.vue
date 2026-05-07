<template>
  <transition name="pf-slide">
    <div v-if="visible" class="pf-overlay" @click.self="handleDismiss">
      <div class="pf-sheet">
        <div class="pf-head">
          <div class="pf-head-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <span class="pf-head-title">{{ alertTitle || '有个地方值得再想想' }}</span>
          <button class="pf-head-close" @click="handleDismiss" aria-label="关闭">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2.5"
                 stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        <div v-if="codeSnippet" class="pf-code">
          <div class="pf-code-lineno">第 {{ lineNumber }} 行</div>
          <pre class="pf-code-text"><code>{{ codeSnippet }}</code></pre>
          <span
            v-if="highlightReason"
            class="pf-code-tag"
          >{{ highlightReason }}</span>
        </div>

        <div class="pf-question">
          <div class="pf-question-avatar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </div>
          <div class="pf-question-body">
            <div class="pf-question-text">{{ question }}</div>
            <div v-if="hint" class="pf-question-hint">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="16" x2="12" y2="12"/>
                <line x1="12" y1="8" x2="12.01" y2="8"/>
              </svg>
              {{ hint }}
            </div>
          </div>
        </div>

        <div class="pf-actions">
          <button class="pf-btn pf-btn-primary" @click="handleGoEdit">
            去修改
          </button>
          <button
            v-if="!confirmExpanded"
            class="pf-btn pf-btn-secondary"
            @click="confirmExpanded = true"
          >
            我确认没问题
          </button>
        </div>

        <transition name="pf-expand">
          <div v-if="confirmExpanded" class="pf-confirm">
            <div class="pf-confirm-text">
              系统会记录本次判断，继续提交后如果出错，助教会结合这次提示帮你分析。
            </div>
            <div class="pf-confirm-actions">
              <button class="pf-btn pf-btn-danger" @click="handleForceSubmit">
                确认提交
              </button>
              <button class="pf-btn pf-btn-ghost" @click="confirmExpanded = false">
                取消
              </button>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'PreflightDialog',
  props: {
    visible: { type: Boolean, default: false },
    question: { type: String, default: '' },
    hint: { type: String, default: '' },
    highlightReason: { type: String, default: '' },
    alertTitle: { type: String, default: '' },
    lineNumber: { type: Number, default: 0 },
    codeSnippet: { type: String, default: '' },
    detectorName: { type: String, default: '' }
  },
  data () {
    return {
      confirmExpanded: false
    }
  },
  watch: {
    visible (val) {
      if (val) this.confirmExpanded = false
    }
  },
  methods: {
    handleGoEdit () {
      this.$emit('go-edit', {
        lineNumber: this.lineNumber,
        detectorName: this.detectorName,
        override: false
      })
    },
    handleForceSubmit () {
      this.$emit('force-submit', {
        lineNumber: this.lineNumber,
        detectorName: this.detectorName,
        override: true
      })
    },
    handleDismiss () {
      this.$emit('go-edit', {
        lineNumber: this.lineNumber,
        detectorName: this.detectorName,
        override: false
      })
    }
  }
}
</script>

<style scoped lang="less">
@amber-50: #fffbeb;
@amber-100: #fef3c7;
@amber-200: #fde68a;
@amber-500: #f59e0b;
@amber-600: #d97706;
@amber-700: #b45309;
@amber-800: #92400e;
@gray-50: #f9fafb;
@gray-100: #f3f4f6;
@gray-200: #e5e7eb;
@gray-400: #9ca3af;
@gray-500: #6b7280;
@gray-600: #4b5563;
@gray-700: #374151;
@gray-800: #1f2937;
@red-50: #fef2f2;
@red-500: #ef4444;
@red-600: #dc2626;
@blue-50: #eff6ff;
@blue-500: #3b82f6;
@blue-600: #2563eb;

.pf-overlay {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  top: 0;
  z-index: 1050;
  background: rgba(0, 0, 0, 0.25);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.pf-sheet {
  width: 100%;
  max-width: 560px;
  background: #fff;
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.12);
  padding: 0 0 20px 0;
  max-height: 80vh;
  overflow-y: auto;
}

.pf-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px 12px;
  border-bottom: 2px solid @amber-200;
  background: @amber-50;
  border-radius: 16px 16px 0 0;
}

.pf-head-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: @amber-600;
  flex-shrink: 0;
}

.pf-head-title {
  font-size: 14px;
  font-weight: 600;
  color: @amber-800;
  flex: 1;
}

.pf-head-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: @gray-400;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: @gray-100;
    color: @gray-600;
  }
}

.pf-code {
  margin: 12px 20px;
  background: @gray-50;
  border: 1px solid @gray-200;
  border-left: 3px solid @amber-500;
  border-radius: 6px;
  padding: 10px 14px;
  position: relative;
}

.pf-code-lineno {
  font-size: 11px;
  color: @gray-500;
  margin-bottom: 4px;
}

.pf-code-text {
  margin: 0;
  padding: 0;
  font-size: 13px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  color: @gray-800;
  white-space: pre-wrap;
  word-break: break-all;

  code {
    background: none;
    padding: 0;
  }
}

.pf-code-tag {
  position: absolute;
  top: 8px;
  right: 10px;
  font-size: 10px;
  color: @amber-700;
  background: @amber-100;
  padding: 1px 6px;
  border-radius: 3px;
}

.pf-question {
  margin: 16px 20px;
  display: flex;
  gap: 10px;
}

.pf-question-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: @blue-50;
  display: flex;
  align-items: center;
  justify-content: center;
  color: @blue-500;
  flex-shrink: 0;
}

.pf-question-body {
  flex: 1;
}

.pf-question-text {
  font-size: 14px;
  line-height: 1.6;
  color: @gray-800;
}

.pf-question-hint {
  margin-top: 8px;
  font-size: 12px;
  color: @gray-500;
  display: flex;
  align-items: flex-start;
  gap: 4px;
  line-height: 1.5;

  svg {
    flex-shrink: 0;
    margin-top: 2px;
  }
}

.pf-actions {
  display: flex;
  gap: 10px;
  padding: 0 20px;
  margin-top: 16px;
}

.pf-btn {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;

  &:active {
    transform: scale(0.98);
  }
}

.pf-btn-primary {
  background: @blue-500;
  color: #fff;

  &:hover {
    background: @blue-600;
  }
}

.pf-btn-secondary {
  background: @gray-100;
  color: @gray-600;

  &:hover {
    background: @gray-200;
    color: @gray-700;
  }
}

.pf-btn-danger {
  background: @red-500;
  color: #fff;

  &:hover {
    background: @red-600;
  }
}

.pf-btn-ghost {
  background: transparent;
  color: @gray-500;
  border: 1px solid @gray-200;

  &:hover {
    background: @gray-50;
  }
}

.pf-confirm {
  margin: 12px 20px 0;
  padding: 12px 14px;
  background: @red-50;
  border-radius: 8px;
  border: 1px solid fade(@red-500, 15%);
}

.pf-confirm-text {
  font-size: 12px;
  color: @gray-600;
  line-height: 1.5;
  margin-bottom: 10px;
}

.pf-confirm-actions {
  display: flex;
  gap: 8px;

  .pf-btn {
    height: 34px;
    font-size: 13px;
  }
}

// 过渡动画。
.pf-slide-enter-active,
.pf-slide-leave-active {
  transition: opacity 0.2s ease;

  .pf-sheet {
    transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  }
}

.pf-slide-enter,
.pf-slide-leave-to {
  opacity: 0;

  .pf-sheet {
    transform: translateY(100%);
  }
}

.pf-expand-enter-active,
.pf-expand-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.pf-expand-enter,
.pf-expand-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
  padding: 0 14px;
}

@media (prefers-reduced-motion: reduce) {
  .pf-slide-enter-active,
  .pf-slide-leave-active,
  .pf-expand-enter-active,
  .pf-expand-leave-active {
    transition: none;
  }
}
</style>

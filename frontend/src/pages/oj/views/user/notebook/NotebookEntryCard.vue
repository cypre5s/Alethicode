<template>
  <div class="nec-card">
    <div class="nec-head">
      <div class="nec-head-left">
        <span class="nec-num">#{{ index + 1 }}</span>
        <span class="err-tag" :class="getTagClass(entry.error_taxonomy)">{{ getCategoryLabel(entry.error_taxonomy) }}</span>
        <span v-if="classFreqPct" class="nec-class-freq">班级 {{ classFreqPct }}% 同学</span>
      </div>
      <div class="nec-head-right">
        <span class="nec-time">{{ formatTime(entry.create_time) }}</span>
        <button type="button" class="nec-del-btn" @click.stop="$emit('remove', entry.id)">删除</button>
      </div>
    </div>

    <div v-if="entry.root_cause" class="nec-field">
      <div class="nec-field-key">根因分析</div>
      <div class="nec-field-val nec-root-cause">{{ entry.root_cause }}</div>
    </div>

    <div v-if="entry.fix_outcome" class="nec-field">
      <div class="nec-field-key">修复结果</div>
      <div class="nec-field-val">{{ entry.fix_outcome }}</div>
    </div>

    <div class="nec-field">
      <div class="nec-field-key">
        学生反思
        <button v-if="!isEditing" type="button" class="nec-inline-btn" @click.stop="startEdit">
          {{ entry.student_reflection ? '编辑' : '添加反思' }}
        </button>
        <button
          v-if="!isEditing && !entry.student_reflection"
          type="button"
          class="nec-inline-btn nec-ai-btn"
          :disabled="generating"
          @click.stop="$emit('generate-reflection', entry)"
        >{{ generating ? '生成中...' : 'AI 帮我写' }}</button>
      </div>
      <div v-if="isEditing" class="nec-reflection-editor">
        <textarea
          v-model="draft"
          rows="3"
          placeholder="写下你对这次错误的反思..."
          class="nec-reflection-textarea"
          @keydown.ctrl.enter="commit"
        ></textarea>
        <div class="nec-reflection-actions">
          <button type="button" class="nec-action-btn nec-save" @click.stop="commit">保存</button>
          <button type="button" class="nec-action-btn nec-cancel" @click.stop="cancel">取消</button>
          <span class="nec-hint">Ctrl+Enter 保存</span>
        </div>
      </div>
      <div v-else-if="entry.student_reflection" class="nec-field-val nec-reflection">{{ entry.student_reflection }}</div>
      <div v-else class="nec-field-val nec-empty-reflection">暂无反思，点击"添加反思"记录你的学习心得</div>
    </div>

    <div class="nec-tags-section">
      <div class="nec-field-key">标签</div>
      <div class="nec-tag-editor">
        <span
          v-for="(t, ti) in (entry.tags || [])"
          :key="'tag-' + ti"
          class="nec-sys-tag"
          :class="{ highlight: ti < 2 }"
        >
          {{ t }}
          <button type="button" class="nec-tag-remove" @click.stop="$emit('remove-tag', { entry, index: ti })">×</button>
        </span>
        <span v-if="addingTag" class="nec-tag-input-wrap">
          <input
            ref="tagInput"
            v-model="newTagText"
            class="nec-tag-input"
            placeholder="标签名"
            maxlength="30"
            @keydown.enter="confirmAddTag"
            @keydown.esc="cancelAddTag"
            @blur="onTagInputBlur"
          />
        </span>
        <button v-else type="button" class="nec-tag-add-btn" @click.stop="startAddTag">+ 标签</button>
      </div>
    </div>
  </div>
</template>

<script>
import { formatTime, getCategoryLabel, getTagClass } from './notebookFormatters.js'

export default {
  name: 'NotebookEntryCard',
  emits: ['remove', 'save-reflection', 'generate-reflection', 'add-tag', 'remove-tag'],
  props: {
    entry: { type: Object, required: true },
    index: { type: Number, default: 0 },
    classFreqPct: { type: [Number, String], default: 0 },
    generating: { type: Boolean, default: false }
  },
  data () {
    return {
      isEditing: false,
      draft: '',
      addingTag: false,
      newTagText: ''
    }
  },
  methods: {
    formatTime,
    getCategoryLabel,
    getTagClass,
    startEdit () {
      this.isEditing = true
      this.draft = this.entry.student_reflection || ''
    },
    cancel () { this.isEditing = false; this.draft = '' },
    commit () {
      this.$emit('save-reflection', { entry: this.entry, text: this.draft })
      this.isEditing = false
    },
    startAddTag () {
      this.addingTag = true
      this.newTagText = ''
      this.$nextTick(() => { if (this.$refs.tagInput) this.$refs.tagInput.focus() })
    },
    cancelAddTag () { this.addingTag = false; this.newTagText = '' },
    onTagInputBlur () {
      if (this.newTagText.trim()) this.confirmAddTag()
      else this.cancelAddTag()
    },
    confirmAddTag () {
      const tag = this.newTagText.trim()
      if (!tag) { this.cancelAddTag(); return }
      this.$emit('add-tag', { entry: this.entry, tag })
      this.cancelAddTag()
    }
  }
}
</script>

<style lang="less" scoped>
.nec-card { padding: 14px 18px; border-bottom: 1px solid #f1f5f9; }
.nec-card:last-child { border-bottom: none; }
.nec-head { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.nec-head-left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.nec-head-right { display: flex; align-items: center; gap: 10px; }
.nec-num { font-size: 11px; font-weight: 700; color: #94a3b8; }
.err-tag { font-size: 12px; padding: 3px 10px; border-radius: 5px; font-weight: 500; flex-shrink: 0; }
.tag-unknown { background: #f1f5f9; color: #64748b; border: 1px solid #e2e8f0; }
.tag-syntaxerr, .tag-rterr, .tag-nameerr { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
.tag-logicerr, .tag-algo, .tag-typeerr { background: #f0f6ff; color: #3b82f6; border: 1px solid #dbeafe; }
.tag-boundary, .tag-perf { background: #fff7ed; color: #ea580c; border: 1px solid #fed7aa; }
.nec-class-freq {
  font-size: 11px; padding: 3px 8px; border-radius: 5px;
  background: #f0f6ff; color: #3b82f6; border: 1px solid #dbeafe;
}
.nec-time { font-size: 11px; color: #94a3b8; white-space: nowrap; }
.nec-del-btn {
  border: none; background: transparent; color: #94a3b8;
  font-size: 12px; cursor: pointer; font-family: inherit;
  &:hover { color: #ef4444; text-decoration: underline; }
}
.nec-field { margin-top: 8px; }
.nec-field-key {
  font-size: 11px; font-weight: 600; color: #5f6368;
  text-transform: uppercase; letter-spacing: 0.4px;
  margin-bottom: 4px; display: flex; align-items: center; gap: 6px;
}
.nec-field-val { font-size: 13px; color: #1a1d2e; line-height: 1.6; }
.nec-root-cause {
  background: #fff7ed; border: 1px solid #fed7aa;
  border-radius: 6px; padding: 8px 10px; color: #9a3412;
}
.nec-empty-reflection { font-size: 12px; color: #94a3b8; font-style: italic; }
.nec-inline-btn {
  border: none; background: transparent; color: #1a73e8;
  font-size: 11px; font-weight: 600; cursor: pointer; font-family: inherit;
  text-transform: none; letter-spacing: 0;
  &:hover { text-decoration: underline; }
}
.nec-ai-btn { color: #7c3aed; }
.nec-reflection-editor { display: flex; flex-direction: column; gap: 6px; }
.nec-reflection-textarea {
  width: 100%; border: 1px solid #cbd5e1; border-radius: 6px;
  padding: 8px 10px; font-family: inherit; font-size: 13px; line-height: 1.6;
  resize: vertical; min-height: 60px;
}
.nec-reflection-actions { display: flex; align-items: center; gap: 8px; font-size: 11px; color: #94a3b8; }
.nec-action-btn {
  border: none; padding: 5px 12px; border-radius: 5px;
  font-size: 12px; cursor: pointer; font-family: inherit;
  &.nec-save { background: #1a73e8; color: #fff; &:hover { background: #1558d6; } }
  &.nec-cancel { background: #f1f5f9; color: #475569; &:hover { background: #e2e8f0; } }
}
.nec-tags-section { margin-top: 10px; }
.nec-tag-editor { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.nec-sys-tag {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 10px; border-radius: 999px;
  background: #f1f5f9; color: #475569;
  font-size: 12px; border: 1px solid #e2e8f0;
  &.highlight { background: #e0e7ff; color: #4338ca; border-color: #c7d2fe; }
}
.nec-tag-remove {
  border: none; background: transparent; cursor: pointer;
  color: inherit; font-size: 14px; line-height: 1; opacity: 0.6; padding: 0;
  &:hover { opacity: 1; }
}
.nec-tag-input-wrap { display: inline-flex; }
.nec-tag-input {
  border: 1px solid #cbd5e1; border-radius: 999px;
  padding: 3px 10px; font-size: 12px; width: 90px;
}
.nec-tag-add-btn {
  border: 1px dashed #cbd5e1; background: transparent;
  border-radius: 999px; padding: 3px 12px;
  font-size: 12px; color: #94a3b8; cursor: pointer; font-family: inherit;
  &:hover { color: #1a73e8; border-color: #1a73e8; }
}
.nec-reflection {
  background: #f8fafc; border: 1px solid #e2e8f0;
  border-radius: 6px; padding: 8px 10px;
}
</style>

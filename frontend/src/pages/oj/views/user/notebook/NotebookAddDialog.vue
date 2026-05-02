<template>
  <el-dialog :model-value="modelValue" title="手动添加错题记录" width="520px" @update:model-value="$emit('update:modelValue', $event)">
    <div class="nbd-form">
      <div class="nbd-row">
        <label>题目 ID</label>
        <el-input v-model="entry.problem_id" placeholder="可选，如 1" style="width: 120px" />
      </div>
      <div class="nbd-row">
        <label>编程语言</label>
        <el-select v-model="entry.language" style="width: 160px">
          <el-option v-for="l in LANG_OPTIONS" :key="l" :value="l" :label="l" />
        </el-select>
      </div>
      <div class="nbd-row">
        <label>错误类别</label>
        <el-select v-model="entry.error_taxonomy" style="width: 200px">
          <el-option v-for="opt in CATEGORY_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
        </el-select>
      </div>
      <div class="nbd-row">
        <label>根因分析</label>
        <el-input v-model="entry.root_cause" type="textarea" :rows="2" placeholder="描述错误根因" />
      </div>
      <div class="nbd-row">
        <label>修复结果</label>
        <el-input v-model="entry.fix_outcome" type="textarea" :rows="2" placeholder="修复方法（可选）" />
      </div>
      <div class="nbd-row">
        <label>学生反思</label>
        <el-input v-model="entry.student_reflection" type="textarea" :rows="2" placeholder="反思笔记（可选）" />
      </div>
      <div class="nbd-row">
        <label>标签</label>
        <el-input v-model="entry.tagsRaw" placeholder="逗号分隔，如: 递归,边界" />
      </div>
    </div>
    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button type="primary" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script>
const LANG_OPTIONS = ['Python3', 'C', 'C++', 'Java', 'JavaScript']
const CATEGORY_OPTIONS = [
  { value: 'syntax_error', label: '语法错误' },
  { value: 'runtime_error', label: '运行时错误' },
  { value: 'logic_error', label: '逻辑错误' },
  { value: 'boundary_condition', label: '边界条件' },
  { value: 'performance', label: '性能问题' },
  { value: 'algorithm_error', label: '算法错误' },
  { value: 'input_parsing', label: '输入解析' },
  { value: 'name_or_type_error', label: '名称/类型错误' },
  { value: 'unknown', label: '未分类' }
]

function defaultEntry () {
  return {
    problem_id: '',
    language: '',
    error_taxonomy: 'unknown',
    root_cause: '',
    fix_outcome: '',
    student_reflection: '',
    tagsRaw: ''
  }
}

export default {
  name: 'NotebookAddDialog',
  emits: ['update:modelValue', 'submit'],
  props: {
    modelValue: { type: Boolean, default: false }
  },
  data () {
    return {
      entry: defaultEntry(),
      LANG_OPTIONS,
      CATEGORY_OPTIONS
    }
  },
  watch: {
    modelValue (next) {
      if (next) this.entry = defaultEntry()
    }
  },
  methods: {
    cancel () { this.$emit('update:modelValue', false) },
    submit () {
      const ne = this.entry
      if (!ne.language || !String(ne.language).trim()) { this.$error && this.$error('编程语言不能为空'); return }
      if (!ne.root_cause.trim()) { this.$error && this.$error('根因分析不能为空'); return }
      const tags = ne.tagsRaw ? ne.tagsRaw.split(/[,，]/).map(s => s.trim()).filter(Boolean) : []
      this.$emit('submit', {
        problem_id: ne.problem_id ? parseInt(ne.problem_id, 10) : null,
        language: ne.language,
        error_taxonomy: ne.error_taxonomy,
        root_cause: ne.root_cause,
        fix_outcome: ne.fix_outcome,
        student_reflection: ne.student_reflection,
        tags
      })
    }
  }
}
</script>

<style lang="less" scoped>
.nbd-form { display: flex; flex-direction: column; gap: 10px; }
.nbd-row { display: flex; align-items: center; gap: 10px; }
.nbd-row label { width: 80px; font-size: 13px; color: #5f6368; flex-shrink: 0; }
</style>

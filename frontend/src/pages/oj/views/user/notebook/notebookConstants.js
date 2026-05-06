/**
 * LearnerNotebook 共享常量。
 * 拆分自原 LearnerNotebook.vue 内的 inline maps，避免子组件多份重复声明。
 */

export const TELEMETRY_EVENT_TYPES = new Set([
  'problem_opened',
  'problem_closed',
  'submission_attempt',
  'code_edit_summary',
  'submission',
  'kc_review',
  'kc_practice',
  'problem_guide_requested'
])

export const ALLOWED_TAXONOMIES = new Set([
  'unknown',
  'syntax_error',
  'runtime_error',
  'logic_error',
  'boundary_condition',
  'performance',
  'algorithm_error',
  'input_parsing',
  'name_or_type_error'
])

export const CATEGORY_ALIAS_MAP = {
  syntax: 'syntax_error',
  invalid_syntax: 'syntax_error',
  logic: 'logic_error',
  boundary: 'boundary_condition',
  boundary_error: 'boundary_condition',
  index_error: 'boundary_condition',
  name_error: 'name_or_type_error',
  type_error: 'name_or_type_error',
  value_error: 'name_or_type_error',
  timeout: 'performance',
  time_limit_exceeded: 'performance',
  memory_error: 'performance',
  memory_limit_exceeded: 'performance',
  runtime: 'runtime_error'
}

export const CATEGORY_LABEL_MAP = {
  syntax_error: '语法错误',
  runtime_error: '运行时错误',
  logic_error: '逻辑错误',
  boundary_condition: '边界条件',
  performance: '性能问题',
  algorithm_error: '算法错误',
  input_parsing: '输入解析',
  name_or_type_error: '名称/类型错误',
  unknown: '未分类'
}

export const LANG_CLASS_MAP = {
  Python3: 'lang-python',
  Python: 'lang-python',
  C: 'lang-c',
  'C++': 'lang-cpp',
  Java: 'lang-java',
  JavaScript: 'lang-js'
}

export const TAG_CLASS_MAP = {
  syntax_error: 'tag-syntaxerr',
  runtime_error: 'tag-rterr',
  logic_error: 'tag-logicerr',
  boundary_condition: 'tag-boundary',
  performance: 'tag-perf',
  algorithm_error: 'tag-algo',
  name_or_type_error: 'tag-nameerr',
  input_parsing: 'tag-syntaxerr',
  unknown: 'tag-unknown'
}

export const REVIEW_DUE_UPDATED_EVENT = 'oj:review-due-updated'

export const VIEW_MODES = {
  CALENDAR: 'calendar',
  ARCHIVE: 'archive'
}

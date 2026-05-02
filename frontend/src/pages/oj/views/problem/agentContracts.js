export const PHASES = Object.freeze([
  'READING',
  'IDEATING',
  'CODING',
  'ERROR_FEEDBACK',
  'AC_REVIEW',
  'TRANSFER'
])

export const WORKFLOW_EVENTS = Object.freeze([
  'CALIBRATING',
  'READING',
  'IDEATING',
  'SKELETON',
  'CODING',
  'ERROR_FEEDBACK',
  'AC_REVIEW',
  'TRANSFER',
  'CHAT',
  'AGENT_FEEDBACK',
  'KNOWLEDGE_REVIEW',
  'VISUALIZE',
  'PARSONS'
])

export const CARD_TYPES = Object.freeze([
  'problem_guide',
  'ideate_analysis',
  'skeleton_code',
  'error_diagnosis',
  'post_ac',
  'transfer_problem',
  'ai_reply',
  'execution_trace_explainer',
  'knowledge_review',
  'visualize',
  'parsons_problem'
])

export const PENDING_HUMAN_ACTIONS = Object.freeze([
  '',
  'confirm_transfer'
])

export const FEEDBACK_LABELS = Object.freeze([
  'helpful',
  'unhelpful',
  'confusing'
])

/**
 * Unified Chat (P3) Mode keys – mirrors backend `ConversationMode`.
 * Mode is the user-facing view of "what do I want now"; switching Mode does NOT change Phase.
 */
export const CONVERSATION_MODES = Object.freeze([
  'reading',
  'ideate',
  'coding',
  'error_diag',
  'visualize',
  'ac_review',
  'transfer',
  'knowledge_review',
  'chat'
])

export const CONVERSATION_MODE_ALLOWED_BY_PHASE = Object.freeze({
  READING: ['reading', 'ideate', 'visualize', 'knowledge_review', 'chat'],
  IDEATING: ['reading', 'ideate', 'coding', 'visualize', 'knowledge_review', 'chat'],
  CODING: ['ideate', 'coding', 'error_diag', 'visualize', 'knowledge_review', 'chat'],
  ERROR_FEEDBACK: ['reading', 'ideate', 'coding', 'error_diag', 'visualize', 'knowledge_review', 'chat'],
  AC_REVIEW: ['ac_review', 'visualize', 'transfer', 'knowledge_review', 'chat'],
  TRANSFER: ['transfer', 'coding', 'visualize', 'knowledge_review', 'chat']
})

export const CONVERSATION_MODE_LABEL = Object.freeze({
  reading: '导读',
  ideate: '思路',
  coding: '编码',
  error_diag: '纠错',
  visualize: '可视化',
  ac_review: '复盘',
  transfer: '迁移',
  knowledge_review: '复习',
  chat: '闲聊'
})

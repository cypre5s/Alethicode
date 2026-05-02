const CHARACTERS = {
  nene: {
    id: 'nene',
    name: '寧寧',
    role: 'AI助教 · 温柔导师',
    color: '#F4C2D0',
    colorLight: 'rgba(244,194,208,0.15)',
    basePath: '/assets/characters/nene/',
    expressions: ['normal', 'smile', 'gentle_smile', 'blush', 'confused', 'surprised', 'sad', 'thinking']
  },
  yoshino: {
    id: 'yoshino',
    name: '芳乃',
    role: '班长 · 代码审查官',
    color: '#c4b5fd',
    colorLight: 'rgba(196,181,253,0.15)',
    basePath: '/assets/characters/yoshino/',
    expressions: ['normal', 'cold', 'slight_smile', 'blush', 'tsundere_pout', 'angry', 'glasses_adjust', 'rare_gentle']
  },
  ayase: {
    id: 'ayase',
    name: '綾瀨',
    role: '同桌 · 元气少女',
    color: '#FF8C42',
    colorLight: 'rgba(255,140,66,0.15)',
    basePath: '/assets/characters/ayase/',
    expressions: ['normal', 'grin', 'competitive', 'blush', 'pout', 'fired_up', 'surprised', 'soft_smile']
  },
  kanna: {
    id: 'kanna',
    name: '栞那',
    role: '图书馆常客 · 算法迷',
    color: '#7BA7C9',
    colorLight: 'rgba(123,167,201,0.15)',
    basePath: '/assets/characters/kanna/',
    expressions: ['normal', 'slight_smile', 'absorbed', 'blush', 'surprised', 'contemplative', 'warm_smile', 'teary']
  },
  murasame: {
    id: 'murasame',
    name: '村雨',
    role: '传说学姐 · 竞赛冠军',
    color: '#DC3545',
    colorLight: 'rgba(220,53,69,0.15)',
    basePath: '/assets/characters/murasame/',
    expressions: ['normal', 'smirk', 'impressed', 'blush', 'cold', 'genuine_smile', 'vulnerable', 'fierce']
  }
}

const CARD_TYPE_TO_CHARACTER = {
  problem_guide: 'nene',
  ideate_analysis: 'ayase',
  skeleton_code: 'ayase',
  error_diagnosis: 'yoshino',
  execution_trace_explainer: 'yoshino',
  visualize: 'yoshino',
  post_ac: 'kanna',
  transfer_problem: 'murasame',
  ai_reply: null
}

const EVENT_EXPRESSIONS = {
  nene: {
    idle: 'gentle_smile',
    thinking: 'thinking',
    card_delivered: 'smile',
    student_submit: 'normal',
    student_ac: 'smile',
    student_wa: 'confused',
    student_ce: 'sad',
    student_tle: 'surprised',
    student_re: 'confused',
    student_typing: 'gentle_smile',
    greeting: 'smile'
  },
  yoshino: {
    idle: 'glasses_adjust',
    thinking: 'cold',
    card_delivered: 'slight_smile',
    student_submit: 'normal',
    student_ac: 'rare_gentle',
    student_wa: 'tsundere_pout',
    student_ce: 'angry',
    student_tle: 'cold',
    student_re: 'angry',
    student_typing: 'glasses_adjust',
    greeting: 'normal'
  },
  ayase: {
    idle: 'grin',
    thinking: 'competitive',
    card_delivered: 'fired_up',
    student_submit: 'competitive',
    student_ac: 'grin',
    student_wa: 'pout',
    student_ce: 'surprised',
    student_tle: 'fired_up',
    student_re: 'pout',
    student_typing: 'soft_smile',
    greeting: 'grin'
  },
  kanna: {
    idle: 'contemplative',
    thinking: 'absorbed',
    card_delivered: 'slight_smile',
    student_submit: 'normal',
    student_ac: 'warm_smile',
    student_wa: 'contemplative',
    student_ce: 'normal',
    student_tle: 'absorbed',
    student_re: 'contemplative',
    student_typing: 'absorbed',
    greeting: 'slight_smile'
  },
  murasame: {
    idle: 'smirk',
    thinking: 'cold',
    card_delivered: 'impressed',
    student_submit: 'smirk',
    student_ac: 'impressed',
    student_wa: 'cold',
    student_ce: 'fierce',
    student_tle: 'smirk',
    student_re: 'cold',
    student_typing: 'normal',
    greeting: 'smirk'
  }
}

export function getCharacterForCardType (cardType) {
  return CARD_TYPE_TO_CHARACTER[cardType] || null
}

export function getCharacter (characterId) {
  return CHARACTERS[characterId] || null
}

export function getSpritePath (characterId, expression) {
  const char = CHARACTERS[characterId]
  if (!char) return ''
  const expr = char.expressions.includes(expression) ? expression : 'normal'
  return char.basePath + expr + '.webp'
}

export function getExpressionForEvent (characterId, event) {
  const map = EVENT_EXPRESSIONS[characterId]
  if (!map) return 'normal'
  return map[event] || map.idle || 'normal'
}

export function resolveCharacterAndExpression (cardType, studentEvent) {
  const charId = getCharacterForCardType(cardType)
  if (!charId) return null
  const expression = studentEvent
    ? getExpressionForEvent(charId, studentEvent)
    : getExpressionForEvent(charId, 'card_delivered')
  return {
    character: CHARACTERS[charId],
    characterId: charId,
    expression,
    spritePath: getSpritePath(charId, expression)
  }
}

export { CHARACTERS, CARD_TYPE_TO_CHARACTER, EVENT_EXPRESSIONS }

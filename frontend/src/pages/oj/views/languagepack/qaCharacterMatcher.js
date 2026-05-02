const CHARACTER_KEYWORDS = {
  nene: {
    id: 'nene',
    keywords: [
      '变量', '赋值', '类型', '字符串', '整数', '浮点', 'print', '输入', '输出',
      '基础', '入门', '什么是', '怎么理解', '概念', '定义', '语法', '格式',
      '循环', 'for', 'while', 'if', 'else', '条件', '判断',
      '列表', 'list', '数组', '字典', 'dict', '元组',
      '函数', 'def', 'return', '参数', '调用',
      '简单', '初学', '新手', '不懂', '看不懂', '教我'
    ],
    location: 'computer_room',
    timeOfDay: 'day'
  },
  yoshino: {
    id: 'yoshino',
    keywords: [
      '错误', '报错', 'error', 'bug', '异常', 'exception', '调试', 'debug',
      '规范', '命名', '格式化', '缩进', '风格', 'PEP', 'style',
      '优化', '重构', '改进', '效率', '性能',
      'class', '类', '面向对象', '继承', '封装', '多态',
      '文件', 'IO', '读写', 'open', 'with',
      '模块', 'import', '库', '包', 'pip'
    ],
    location: 'classroom',
    timeOfDay: 'day'
  },
  ayase: {
    id: 'ayase',
    keywords: [
      '项目', '实战', '练习', '案例', '示例', '例子', '怎么做', '怎么写',
      '网页', 'HTML', 'CSS', 'web', '游戏', 'game',
      '快速', '简单方法', '捷径', '技巧', 'tips',
      '比较', '区别', '不同', '对比', 'vs',
      '有趣', '好玩', '酷', '厉害'
    ],
    location: 'rooftop',
    timeOfDay: 'day'
  },
  kanna: {
    id: 'kanna',
    keywords: [
      '算法', '数据结构', '排序', '搜索', '查找', '二分',
      '递归', '动态规划', 'dp', '贪心', '回溯',
      '时间复杂度', '空间复杂度', 'O(n)', '大O', '复杂度',
      '栈', '队列', '链表', '树', '图', '哈希',
      '数学', '公式', '证明', '推导',
      '最优', '最短', '最小', '最大'
    ],
    location: 'library',
    timeOfDay: 'day'
  },
  murasame: {
    id: 'murasame',
    keywords: [
      '竞赛', 'ACM', 'OI', '比赛', '竞技',
      '进阶', '高级', '深入', '底层', '原理',
      '多线程', '并发', '异步', 'async', 'await',
      '设计模式', '架构', '系统', '工程',
      '难', '挑战', '困难', '复杂',
      '网络', '安全', '加密', '协议'
    ],
    location: 'computer_room',
    timeOfDay: 'night'
  }
}

export function matchCharacterForQuestion (questionText) {
  if (!questionText || typeof questionText !== 'string') {
    return CHARACTER_KEYWORDS.nene
  }

  const text = questionText.toLowerCase()
  const scores = {}

  for (const [charId, config] of Object.entries(CHARACTER_KEYWORDS)) {
    let score = 0
    for (const keyword of config.keywords) {
      if (text.includes(keyword.toLowerCase())) {
        score++
      }
    }
    scores[charId] = score
  }

  let bestId = 'nene'
  let bestScore = 0
  for (const [charId, score] of Object.entries(scores)) {
    if (score > bestScore) {
      bestScore = score
      bestId = charId
    }
  }

  return CHARACTER_KEYWORDS[bestId]
}

export function getCharacterBackground (characterId, timeOfDay) {
  const config = CHARACTER_KEYWORDS[characterId]
  if (!config) return '/assets/backgrounds/computer_room/day.webp'
  const tod = timeOfDay || config.timeOfDay || 'day'
  return `/assets/backgrounds/${config.location}/${tod}.webp`
}

export function getCurrentTimeOfDay () {
  const hour = new Date().getHours()
  if (hour >= 6 && hour < 16) return 'day'
  if (hour >= 16 && hour < 19) return 'evening'
  return 'night'
}

export { CHARACTER_KEYWORDS }

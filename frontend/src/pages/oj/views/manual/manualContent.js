/**
 * Alethicode 使用指南文案中心。
 *
 * 仅描述用户在前端能看到、能点的功能；不暴露后端实现、API、模型、提示词等技术内幕。
 * 章节、FAQ、命令面板条目集中在此，组件按 id/anchor 渲染。
 *
 * 视觉默认走 Notion / Cursor / Claude 文档风（白底 + 灰边 + 留白 + mono kicker）；
 * 趣味模式（funMode）通过右上角开关启用，启用后才出现奶蛙、贴片、confetti、流程手绘版。
 */

const NAIWA_BASE = '/assets/manual/naiwa'
const SCREENSHOT_BASE = '/assets/manual/screenshots'

export const FUN_MODE_KEY = 'manual.fun_mode'
export const COMPLETED_KEY = 'manual.completed_at'

export const NAIWA_STICKERS = [
  { src: `${NAIWA_BASE}/stickers/sticker-01-wave.png`, alt: '挥手的奶蛙' },
  { src: `${NAIWA_BASE}/stickers/sticker-02-think.png`, alt: '思考的奶蛙' },
  { src: `${NAIWA_BASE}/stickers/sticker-03-point.png`, alt: '指引的奶蛙' },
  { src: `${NAIWA_BASE}/stickers/sticker-04-laugh.png`, alt: '大笑的奶蛙' },
  { src: `${NAIWA_BASE}/stickers/sticker-05-shy.png`, alt: '害羞的奶蛙' },
  { src: `${NAIWA_BASE}/stickers/sticker-06-peek.png`, alt: '探头的奶蛙' },
  { src: `${NAIWA_BASE}/stickers/sticker-07-cheer.png`, alt: '欢呼的奶蛙' },
  { src: `${NAIWA_BASE}/stickers/sticker-08-sleep.png`, alt: '打盹的奶蛙' }
]

export const NAIWA_FAQ_ICONS = [
  { src: `${NAIWA_BASE}/faq-icons/icon-question.png`, alt: '问号奶蛙' },
  { src: `${NAIWA_BASE}/faq-icons/icon-confused.png`, alt: '困惑奶蛙' },
  { src: `${NAIWA_BASE}/faq-icons/icon-aha.png`, alt: '恍然大悟奶蛙' },
  { src: `${NAIWA_BASE}/faq-icons/icon-shrug.png`, alt: '耸肩奶蛙' }
]

export const NAIWA_GALLERY = [
  { src: `${NAIWA_BASE}/gallery/01-classic-laugh.png`, alt: '经典大笑', label: '经典大笑' },
  { src: `${NAIWA_BASE}/gallery/02-thinking.png`, alt: '抱头思考', label: '抱头思考' },
  { src: `${NAIWA_BASE}/gallery/03-stare.png`, alt: '认真凝视', label: '认真凝视' },
  { src: `${NAIWA_BASE}/gallery/04-rolling.png`, alt: '前倾大笑', label: '前倾大笑' },
  { src: `${NAIWA_BASE}/gallery/05-cheer.png`, alt: '哪吒造型', label: '哪吒造型' },
  { src: `${NAIWA_BASE}/gallery/06-coding.png`, alt: '抱臂沉思', label: '抱臂沉思' },
  { src: `${NAIWA_BASE}/gallery/07-thumbup.png`, alt: '指引方向', label: '指引方向' },
  { src: `${NAIWA_BASE}/gallery/08-sip.png`, alt: '叉腰自信', label: '叉腰自信' },
  { src: `${NAIWA_BASE}/gallery/09-tongue.gif`, alt: '调皮吐舌', label: '调皮吐舌' },
  { src: `${NAIWA_BASE}/gallery/10-stride.gif`, alt: '气场全开', label: '气场全开' },
  { src: `${NAIWA_BASE}/gallery/11-faint.gif`, alt: '草地躺平', label: '草地躺平' },
  { src: `${NAIWA_BASE}/gallery/12-lion-dance.gif`, alt: '舞狮变装', label: '舞狮变装' }
]

export const NAIWA_MOTION = {
  laughLoop: `${NAIWA_BASE}/motion/naiwa-laugh-loop.gif`,
  bounce: `${NAIWA_BASE}/motion/naiwa-bounce.gif`,
  spin: `${NAIWA_BASE}/motion/naiwa-spin.gif`,
  celebrate: `${NAIWA_BASE}/motion/naiwa-celebrate.gif`
}

export const NAIWA_HERO = `${NAIWA_BASE}/hero/naiwa-hero.png`
export const NAIWA_LAUGH_AUDIO = `${NAIWA_BASE}/audio/nailong-laugh.m4a`

export const SECTIONS = [
  {
    id: 'welcome',
    title: '快速开始',
    subtitle: '第一次用 Alethicode，按 4 步走完一遍',
    sticker: 0
  },
  {
    id: 'ai',
    title: 'AI 导学助手',
    subtitle: '它能帮你做什么、怎么问最有效',
    sticker: 4
  },
  {
    id: 'context',
    title: '@ 上下文引用',
    subtitle: '让 AI 看见你正在看的题、写的代码、读的课件',
    sticker: 2
  },
  {
    id: 'qa',
    title: '课件问答',
    subtitle: '从课件 PDF 里直接检索、查页码、对原文',
    sticker: 1
  },
  {
    id: 'flow',
    title: '完整学习闭环',
    subtitle: '从读题到复盘的 8 步标准流程',
    sticker: 3
  },
  {
    id: 'tips',
    title: '学习建议',
    subtitle: '5 条容易被忽略的最佳实践',
    sticker: 6
  },
  {
    id: 'faq',
    title: '常见问题',
    subtitle: '判题卡住、看不懂报错、AI 卡片不出现……',
    sticker: 5
  },
  {
    id: 'tour',
    title: '附录 · 页面导览',
    subtitle: '14 张页面截图，遇到陌生页面回来对照',
    sticker: 2
  },
  {
    id: 'gallery',
    title: '奶蛙图鉴',
    subtitle: '本节是趣味彩蛋，关闭趣味模式后整段隐藏',
    sticker: 7,
    funOnly: true
  },
  {
    id: 'feedback',
    title: '反馈与帮助',
    subtitle: '看到 bug、想吐槽、想关闭奶蛙都从这里走',
    sticker: 0
  }
]

export const HERO_CAPABILITIES = [
  {
    id: 'ai',
    label: 'AI 导学助手',
    desc: '5 位角色覆盖审题、纠错、总结、进阶、陪练',
    target: 'ai'
  },
  {
    id: 'context',
    label: '@ 上下文引用',
    desc: '把当前题、代码、错误诊断卡塞进对话',
    target: 'context'
  },
  {
    id: 'qa',
    label: '课件问答',
    desc: '从课件 PDF 检索答案，附原文页码',
    target: 'qa'
  },
  {
    id: 'flow',
    label: '完整学习闭环',
    desc: '8 步从读题到复盘，每一步知道在做什么',
    target: 'flow'
  }
]

export const QUICK_START_STEPS = [
  {
    step: 1,
    title: '挑一道难度合适的题',
    where: '题库或主页推荐',
    look: '题号 / 难度 / 标签',
    why: '难度差太大会卡住或失去兴趣，挑刚好略难一点的'
  },
  {
    step: 2,
    title: '读懂题目再动手',
    where: '做题页左侧题面',
    look: '输入格式、输出格式、样例',
    why: '一半的 WA 都是题意理解错，先想清楚要做什么'
  },
  {
    step: 3,
    title: '写不出来就让 AI 提点',
    where: '右下角 AI 导学卡片',
    look: '审题卡 / 思路分析 / 报错诊断',
    why: 'AI 不替你写代码，是和你一起想清楚下一步'
  },
  {
    step: 4,
    title: '提交、看反馈、改一遍',
    where: '底部「提交」按钮 + 提交记录',
    look: '通过 / 错答 / 超时 + 错误用例',
    why: '不要看到错就放弃，改一遍比换一题更长记忆'
  }
]

export const FLOW_NODES = [
  { id: 'read', title: '01 读题', target: 'ai' },
  { id: 'io', title: '02 拆 I/O', target: 'ai' },
  { id: 'idea', title: '03 想思路', target: 'ai' },
  { id: 'code', title: '04 写代码', target: 'ai', highlight: true },
  { id: 'submit', title: '05 提交', target: 'faq', highlight: true },
  { id: 'feedback', title: '06 看反馈', target: 'context' },
  { id: 'fix', title: '07 跟 AI 改', target: 'context', highlight: true },
  { id: 'review', title: '08 复盘', target: 'tips' }
]

export const LEARNING_LOOP_STEPS = [
  {
    step: 1,
    title: '读题',
    desc: '把题面读两遍，圈出关键词；不要只看样例就猜题意。'
  },
  {
    step: 2,
    title: '拆 I/O',
    desc: '把"输入是什么 / 输出什么 / 边界条件"逐项写下来；可以问寧寧（审题）帮你拆。'
  },
  {
    step: 3,
    title: '想思路',
    desc: '不写代码，先用中文写步骤；卡住了 @ 当前题问 AI 思路分析。'
  },
  {
    step: 4,
    title: '写代码',
    desc: '把伪代码翻译成真代码；先写能跑的版本，不追求一次最优。'
  },
  {
    step: 5,
    title: '提交',
    desc: '点提交看判题结果；通过则继续，错了别急着改。'
  },
  {
    step: 6,
    title: '看反馈',
    desc: '看错在哪个用例、报错信息是什么；@last_error 让芳乃帮你翻译报错。'
  },
  {
    step: 7,
    title: '跟 AI 改',
    desc: 'AI 只指出最关键的一处错，自己改、再提交；不要让 AI 把整段代码重写。'
  },
  {
    step: 8,
    title: '复盘',
    desc: '通过后用一句话讲清这题的核心思路；存进错题本反思框，比看十篇题解都管用。'
  }
]

export const TOUR_PAGES = [
  {
    id: 'login-register',
    title: '登录 / 注册',
    desc: '进入 Alethicode 的第一步。填写用户名、邮箱、密码即可注册，已有账号直接登录。',
    points: ['支持"忘记密码"邮箱重置', '注册后自动登录，无需二次操作', '用户名注册后不可修改，请认真填写'],
    screenshot: `${SCREENSHOT_BASE}/login-register.png`,
    target: '/login'
  },
  {
    id: 'home',
    title: '主页 Dashboard',
    desc: '登录后第一个看到的页面，左侧是当前要做的题、右侧是最近的判题结果与角色互动入口。',
    points: ['看自己当前的学习路线', '快速跳转到上次没做完的题', '看公告与教师警报（教师视角）'],
    screenshot: `${SCREENSHOT_BASE}/home-dashboard.png`,
    target: '/'
  },
  {
    id: 'problem-list',
    title: '题库列表',
    desc: '所有题目按难度、标签、知识点筛选；每题前面会显示你的状态（未做 / 已通过 / 错过）。',
    points: ['用左侧标签筛选知识点', '点表头切换难度排序', '右上角搜索按题号或关键词'],
    screenshot: `${SCREENSHOT_BASE}/problem-list.png`,
    target: '/problem'
  },
  {
    id: 'problem-detail',
    title: '做题页 — 题面与编辑器',
    desc: '左边是题目描述与示例 I/O，右边是代码编辑器。这是你最常待的地方。',
    points: ['切换语言（默认 Python 3）', '编辑器支持语法高亮与自动缩进', '示例输入/输出直接在题面里，可复制粘贴到本地测试'],
    screenshot: `${SCREENSHOT_BASE}/problem-detail.png`,
    target: '/problem'
  },
  {
    id: 'problem-ai',
    title: '做题页 — AI 辅导区',
    desc: '做题页下方的 AI 卡片区域。根据你的提交状态，5 位 AI 角色会依次出现不同类型的引导。',
    points: ['审题卡：写代码前先看，帮你理清输入输出', '纠错卡：判错后出现，定位最关键的一处错误', '陪练卡：自由追问"为什么这样写"'],
    screenshot: `${SCREENSHOT_BASE}/problem-ai-cards.png`,
    target: '/problem'
  },
  {
    id: 'problem-submission',
    title: '做题页 — 提交记录',
    desc: '每次提交后，判题结果（通过 / 错答 / 超时 / 编译错误）会按时间列出，点击可看详细测试用例。',
    points: ['不同结果用不同颜色区分，一目了然', '点开单条记录可查看具体哪个用例没过', '历史代码可回溯，帮你对比每次修改'],
    screenshot: `${SCREENSHOT_BASE}/problem-submissions.png`,
    target: '/problem'
  },
  {
    id: 'notebook',
    title: '错题本',
    desc: '系统自动收录你判错或卡住的题，按知识点分组；你可以自己写反思、做复习包。',
    points: ['看哪些知识点错得最多', '点"再做一次"重写题目', '导出"专项复习"在指定时间复习'],
    screenshot: `${SCREENSHOT_BASE}/notebook.png`,
    target: '/learner-notebook'
  },
  {
    id: 'review-package',
    title: '专项复习',
    desc: '从错题本生成的定向复习包。按薄弱知识点组织 3-5 道同类题，集中攻克。',
    points: ['系统根据错误模式自动生成', '也可手动从错题本导出', '做完后知识点掌握度会更新'],
    screenshot: `${SCREENSHOT_BASE}/review-package.png`,
    target: '/review-package'
  },
  {
    id: 'qa',
    title: '课件问答 — 提问界面',
    desc: '选一份课件包，输入你的问题，平台会从课件内容里检索并给出答案与具体页码引用。',
    points: ['提问越具体回答越准', '支持自然语言，像问老师一样问', '可连续追问，上下文会保留'],
    screenshot: `${SCREENSHOT_BASE}/qa.png`,
    target: '/language-pack-qa'
  },
  {
    id: 'qa-viewer',
    title: '课件问答 — PDF 原页预览',
    desc: '点击问答回答中的页码引用，会打开课件的 PDF 原页，直接定位到引用的那一页。',
    points: ['不用自己翻 PDF 找页码', '可以对照原文验证 AI 回答', '支持缩放和翻页浏览整份课件'],
    screenshot: `${SCREENSHOT_BASE}/qa-pdf-viewer.png`,
    target: '/language-pack-qa/viewer'
  },
  {
    id: 'classroom',
    title: '班级列表',
    desc: '查看你已加入的班级，或用班级码加入新班级。',
    points: ['用班级码加入', '看老师布置的题集', '查看班级成员与进度'],
    screenshot: `${SCREENSHOT_BASE}/classroom.png`,
    target: '/classroom'
  },
  {
    id: 'classroom-detail',
    title: '班级详情',
    desc: '进入某个班级后，可以看到老师布置的题单、截止时间和同学进度。',
    points: ['查看老师布置的作业题单', '看自己在班级中的排名与进度', '确认老师发布的学习要求与截止时间'],
    screenshot: `${SCREENSHOT_BASE}/classroom-detail.png`,
    target: '/classroom/detail'
  },
  {
    id: 'user-home',
    title: '个人主页',
    desc: '你的学习数据中心：做题数量、通过率、知识点掌握度雷达图、近期活动轨迹。',
    points: ['查看累计做题量和通过率', '知识点掌握度雷达图一目了然', '近期提交活动热力图'],
    screenshot: `${SCREENSHOT_BASE}/user-home.png`,
    target: '/user-home'
  },
  {
    id: 'settings',
    title: '个人设置',
    desc: '修改头像、昵称、密码等个人信息，以及账号安全相关操作。',
    points: ['修改个人资料（昵称、头像等）', '修改密码', '账号安全设置'],
    screenshot: `${SCREENSHOT_BASE}/settings.png`,
    target: '/setting'
  }
]

export const AI_CHARACTERS = [
  {
    id: 'nene',
    name: '寧寧（审题）',
    avatar: '/assets/characters/nene/normal.webp',
    initial: '寧',
    color: '#6366f1',
    duty: '把抽象的题面翻译成你能直接照着写的步骤，标出输入边界与输出格式。',
    when: '题目读三遍还没头绪、不知道该输入输出什么的时候。',
    howTo: [
      '打开一道题后，在 AI 辅导区找到"审题"卡片',
      '寧寧会把题面拆解成：输入是什么 → 要做什么处理 → 输出什么格式',
      '重点关注她标出的"边界条件"——比如 n=0、空列表、负数等特殊情况',
      '看完审题卡再开始写代码，能大幅减少"题意理解错误"导致的 WA'
    ],
    example: '题目说"给定 n 个整数，输出最大值"→ 寧寧会告诉你：输入第一行是 n，第二行是 n 个空格分隔的整数；输出一行，最大值；注意 n≥1。'
  },
  {
    id: 'yoshino',
    name: '芳乃（纠错）',
    avatar: '/assets/characters/yoshino/normal.webp',
    initial: '芳',
    color: '#ec4899',
    duty: '只挑你这次代码里最关键的一处错，配最小修改提示，不刷一堆建议。',
    when: '提交错答 / 编译错误 / 输出不对，但自己肉眼看不出来。',
    howTo: [
      '提交判错后，芳乃的纠错卡会自动出现',
      '她只指出最关键的一个错误，不会一次列一堆问题让你懵',
      '会告诉你"错在哪一行"和"为什么错"，并给出最小修改方向',
      '修改后再提交，如果还有其他错，她会接着指出下一个'
    ],
    example: '你的 for 循环写了 range(1, n)，但题目要求从 0 开始 → 芳乃会指出：第 3 行 range 起点应该是 0，少算了第一个元素。'
  },
  {
    id: 'kanna',
    name: '栞那（总结）',
    avatar: '/assets/characters/kanna/normal.webp',
    initial: '栞',
    color: '#10b981',
    duty: '过题后帮你梳理"这一题用了什么思路 / 还能怎么改更简洁"。',
    when: 'AC 一题想确认自己有没有学到东西。',
    howTo: [
      'AC（通过）一道题后，栞那的总结卡会出现',
      '她会梳理你用到的核心思路和知识点',
      '会指出代码中"能简化"的地方，但不是要你重写——是帮你看到改进空间',
      '如果你的解法跟最优解差距大，她会温和地提一下更好的方向'
    ],
    example: 'AC 后栞那说：这题用了"累加器模式"，核心是 for 循环 + 变量累加；你的写法已经正确，但可以用 sum() 内置函数一行搞定。'
  },
  {
    id: 'murasame',
    name: '村雨（进阶）',
    avatar: '/assets/characters/murasame/normal.webp',
    initial: '村',
    color: '#f59e0b',
    duty: '基于你刚做的题推荐 1-2 道迁移题，把同一个知识点用到新场景。',
    when: '会做基本题型，想看看自己是不是真懂。',
    howTo: [
      'AC 后如果想继续巩固，看村雨的进阶卡',
      '她会推荐 1-2 道知识点相同但场景不同的题目',
      '点题目链接可以直接跳转过去做',
      '连续做完迁移题，说明你真的掌握了这个知识点'
    ],
    example: '你刚做完一道"列表求最大值"→ 村雨推荐："求列表中第二大的数"和"找出列表中出现最多的元素"。'
  },
  {
    id: 'ayase',
    name: '綾瀨（陪练）',
    avatar: '/assets/characters/ayase/normal.webp',
    initial: '綾',
    color: '#8b5cf6',
    duty: '自由对话角色，可以追问"为什么"，也可以单纯吐槽。',
    when: '只想找个不会嫌弃你 baby 问题的人聊几句。',
    howTo: [
      '在做题页任何时候都可以跟綾瀨对话',
      '可以问她"为什么 Python 的列表从 0 开始"这种基础问题',
      '也可以让她帮你解释报错信息或某个概念',
      '她不会嫌弃任何问题，但也不会直接给你答案——会引导你自己想'
    ],
    example: '你问"什么是缩进错误"→ 綾瀨会解释 Python 用缩进表示代码块，然后让你看看哪一行缩进不对齐。'
  }
]

export const AI_CAPABILITIES = [
  {
    id: 'explain-problem',
    title: '把题目讲明白',
    desc: '把抽象的题面拆成"输入是什么、输出什么、边界条件"。'
  },
  {
    id: 'split-io',
    title: '拆 I/O 与样例',
    desc: '把样例输入输出对应到题面要求，标出容易看漏的边界。'
  },
  {
    id: 'split-thought',
    title: '拆解思路步骤',
    desc: '帮你把"我大概要这么做"翻成可以一步步实现的步骤。'
  },
  {
    id: 'locate-bug',
    title: '定位代码错误',
    desc: '只指出最关键的一处错，不一次刷十条建议把人搞晕。'
  },
  {
    id: 'translate-error',
    title: '翻译报错信息',
    desc: '把 Python Traceback 翻成"第几行、为什么错、先看哪里"。'
  },
  {
    id: 'review-code',
    title: '检查通过的代码',
    desc: 'AC 后帮你看看有没有更简洁的写法，但不会让你重写。'
  },
  {
    id: 'summarize-kc',
    title: '总结知识点',
    desc: '通过一题后用一两句话告诉你"这题考的是什么"。'
  },
  {
    id: 'recommend-similar',
    title: '推荐相似题',
    desc: '基于刚做完的题推荐 1-2 道同知识点不同场景的迁移题。'
  }
]

export const RECOMMENDED_PROMPTS = [
  {
    id: 'understand-problem',
    label: '让 AI 帮你理解题目',
    when: '题目读两遍还不懂；不知道要输入输出什么。',
    prompt: '@当前题目 这道题在问什么？输入和输出分别是什么？有哪些容易看漏的边界条件？',
    why: '直接让 AI 把题面"翻译"成你能听懂的中文，比直接抓代码更稳。'
  },
  {
    id: 'analyze-error',
    label: '让 AI 解释报错',
    when: '提交后报错信息看不懂；自己 review 代码看不出问题。',
    prompt: '@last_error 这个报错是什么意思？我应该先检查哪里？',
    why: '把"错误诊断卡"塞回对话，AI 就能针对你这次的错给具体方向，而不是泛泛而谈。'
  },
  {
    id: 'check-code',
    label: '让 AI 检查思路',
    when: '已经写完代码但不确定对不对；AC 后想看看有没有更优解。',
    prompt: '@我的代码 这段代码的思路有什么问题？有没有更简洁的写法？',
    why: '强调"思路"和"更简洁"，引导 AI 给方向而不是替你重写。'
  },
  {
    id: 'explain-concept',
    label: '让 AI 解释概念',
    when: '碰到不会的语法 / 数据结构 / 算法术语。',
    prompt: '@课件 第 X 节提到的「递归」是什么意思？能用一个简单例子讲讲吗？',
    why: '把课件塞给 AI 做语境，回答会引用课件原文页码，比直接搜更靠谱。'
  }
]

export const DISCOURAGED_PROMPTS = [
  {
    id: 'give-answer',
    label: '"直接给我答案"',
    why: '会跳过"理解 → 设计"这两步学习里最关键的环节；下次遇到同类型还是不会。'
  },
  {
    id: 'write-full-code',
    label: '"帮我把整段代码写完"',
    why: 'AI 也不知道你"哪里不会"，只能猜一段最常见的写法；代码即使能跑，也不是你写的。'
  }
]

export const CONTEXT_TOKENS = [
  {
    token: '@card:<id>',
    name: '具体卡片',
    when: '想引用某一张已经出现的 AI 卡片（如 C-V-001）。'
  },
  {
    token: '@last_guide',
    name: '最近一次「题目导读」',
    when: '让 AI 接着寧寧的审题继续讲。'
  },
  {
    token: '@last_ideate',
    name: '最近一次「思路分析」',
    when: '把刚生成的思路再展开、加约束、问反例。'
  },
  {
    token: '@last_error',
    name: '最近一次「错误诊断」',
    when: '把芳乃的报错诊断作为上下文，问"接下来怎么改"。'
  },
  {
    token: '@last_post_ac',
    name: '最近一次「过题总结」',
    when: '让栞那的总结再展开，问"还能怎么优化"。'
  },
  {
    token: '@last_transfer',
    name: '最近一次「迁移题推荐」',
    when: '让村雨从推荐里挑一道讲思路。'
  },
  {
    token: '@last_review',
    name: '最近一次「知识点回顾」',
    when: '把刚回顾的知识点带进新一轮对话。'
  },
  {
    token: '@last_visualize',
    name: '最近一次「教学可视化」',
    when: '让 AI 围绕刚生成的图 / 动画继续解释。'
  }
]

export const CONTEXT_EXAMPLES = [
  {
    id: 'ctx-current-problem',
    label: '@当前题目',
    prompt: '@当前题目 这道题在考什么知识点？我应该先学什么再来做？',
    note: '把题面塞给 AI，让它结合题目本身回答而不是凭空猜。'
  },
  {
    id: 'ctx-my-code',
    label: '@我的代码',
    prompt: '@我的代码 这段代码的思路对吗？有没有更简洁的写法？',
    note: '用「思路 / 简洁」这种关键词引导方向，避免被改写整段。'
  },
  {
    id: 'ctx-courseware',
    label: '@课件',
    prompt: '@课件 第 3 节提到的「列表推导式」是什么意思？给个例子。',
    note: '让回答带原文引用页码，可以一键打开 PDF 对照。'
  },
  {
    id: 'ctx-error',
    label: '@last_error',
    prompt: '@last_error 这个报错是什么意思？我应该先检查哪里？',
    note: '把错误诊断卡作为对话上下文，回答才会针对你的具体错误。'
  }
]

export const CONTEXT_TIPS = [
  '一次只 @ 一两条最相关的，全部塞过去反而会稀释 AI 的注意力。',
  'token 大小写敏感，必须是 `@last_error` 这样的小写下划线写法。',
  '如果回答跟你预期的不一样，先确认 token 名称是否正确，再换问法。',
  '@card:<id> 适合复盘——把之前的某张卡片拿出来继续问，避免反复重新生成。'
]

export const COURSEWARE_QA_SCOPE = [
  '问课件里某个名词、定义、公式的具体含义',
  '问某一节讲了什么、和上一节的关系是什么',
  '让 AI 用你能听懂的话重述某个概念',
  '让 AI 举例说明某个语法或数据结构',
  '让 AI 比较两个相似概念的差别（比如 for 和 while）',
  '让 AI 总结某一章的要点 / 给学习路线建议',
  '查具体页码原文，对照 AI 回答验证'
]

export const COURSEWARE_QA_PROMPTS = [
  {
    id: 'qa-summary',
    label: '让课件帮你总结一节',
    prompt: '第 2 节主要讲了什么？和第 1 节的关系是什么？',
    note: '提问时指明章节号，回答会更有针对性。'
  },
  {
    id: 'qa-define',
    label: '让课件解释一个名词',
    prompt: '课件里说的「字典推导式」是什么意思？给两个例子，分别说明用法。',
    note: '加上"举例"会让回答更具体。'
  },
  {
    id: 'qa-compare',
    label: '让课件帮你比较两个概念',
    prompt: 'for 循环和 while 循环什么时候用哪个？课件里有没有具体的例子？',
    note: '比较类问题让 AI 结合课件原文给指导。'
  },
  {
    id: 'qa-roadmap',
    label: '让课件帮你规划学习顺序',
    prompt: '我刚学完第 3 章「列表」，下一步该学什么？这本课件后面哪几节是基础必看？',
    note: '让 AI 围绕课件目录给推荐，比通用教程靠谱。'
  },
  {
    id: 'qa-locate',
    label: '让课件帮你定位原文',
    prompt: '课件里提到「切片」的部分在哪几页？',
    note: '回答会列出页码，点击可以直接打开 PDF。'
  }
]

export const COURSEWARE_QA_NOTES = [
  '课件问答只能基于已收录的课件 PDF 内容，超出范围的问题会无答。',
  '回答里的页码引用可以点开直接定位原文，遇到不放心的内容务必对照。',
  'AI 生成的回答可能存在偏差，关键概念以课件原文为准。',
  '提问越具体效果越好——「第 3 章的递归是什么意思」远比「递归」精准。'
]

export const FAQ_ITEMS = [
  {
    q: '提交后判题一直 Pending 怎么办？',
    a: '通常 30 秒内会出结果。超过 1 分钟仍是 Pending，刷新页面，或在"提交记录"里看是否已经更新。如果集群临时拥塞，最多排队 2-3 分钟；持续超过 5 分钟可点反馈按钮告诉我们。'
  },
  {
    q: '看不懂判题给的报错信息？',
    a: '把芳乃（纠错）的卡片用 @last_error 塞进对话，让她翻译成中文版"哪一行错了、为什么错"。如果是 Python 的 Traceback，从最底下一行的错误类型读起，往上找代码行号。'
  },
  {
    q: 'AI 卡片不出现 / 加载很久？',
    a: '先确认你已经"提交"了至少一次（卡片基于你的提交内容生成）。如果仍然空白，刷新页面；偶尔后台模型负载高会延迟 30-60 秒。'
  },
  {
    q: '课件问答没结果 / 答不对？',
    a: '换个更具体的问法（指明哪一节或哪一个名词）。课件问答只能从这份课件已收录的内容里找答案，超出范围就会无答；务必点回答下方的页码引用对照原文。'
  },
  {
    q: '@ 引用没生效，AI 答得很泛？',
    a: '先确认 token 写对了（如 @last_error 中间是下划线）。一次别同时 @ 太多张卡片，AI 注意力会被稀释；保留最相关的一两条即可。'
  },
  {
    q: 'AI 直接把答案给我了，但下次遇到还是不会？',
    a: '把"直接给我答案"改成"帮我理解题目 / 思路"。AI 越像答案机就越不教你，越像 review 老师就越教你。'
  },
  {
    q: '错题本里的"反思"该写什么？',
    a: '一两句话就够，例如"我把 range(n) 当成 1 到 n 了"、"我忘了字符串切片是左闭右开"。重点是用自己的话讲，不要复制题解。'
  },
  {
    q: '我能不能不开 AI，自己默默练？',
    a: '可以。AI 卡片是辅助，不开也能正常做题。只要你点"提交"判题就会运行。AI 区域可以折叠收起。'
  }
]

export const TIPS = [
  {
    title: '把题读懂再写代码',
    desc: '一半的 WA 都是题意理解错。读两遍题面、圈关键词、写下输入输出格式，再开始写。'
  },
  {
    title: '让 AI 当 review 老师，不是答案机',
    desc: '问"思路对吗"而不是"答案是什么"；问"为什么错"而不是"帮我改"。'
  },
  {
    title: '@ 上下文比纯聊天有效十倍',
    desc: '把当前题、刚出的错误诊断、相关课件用 @ 塞进去，AI 才能给出对你这次具体的建议。'
  },
  {
    title: '错题本反思用自己的话',
    desc: 'AC 之后用一句话讲清"这题考什么 / 我之前哪里想错"，比看十篇题解都长记性。'
  },
  {
    title: '每天写一点，胜过周末爆肝',
    desc: '哪怕 10 分钟，重点是每天都让大脑想一次"这道题怎么解"，节奏比时长重要。'
  }
]

export const FEEDBACK_ITEMS = [
  {
    title: '看到 bug 怎么办',
    desc: '右下角"Beta 反馈"按钮里写下你正在做什么、看到了什么、期望的结果是什么。能截图就截图。'
  },
  {
    title: '关掉奶蛙趣味模式',
    desc: '本页右上角"关闭趣味模式"开关；浮动挂件第三颗按钮也是同一开关，关闭后即时生效。'
  },
  {
    title: '键盘可达性',
    desc: '所有按钮可用 Tab 聚焦，Enter / Space 触发。Cmd/Ctrl + K 打开命令面板，Esc 收起任何弹层。'
  }
]

export const COMMAND_PALETTE_ITEMS = [
  ...SECTIONS.map((s, idx) => ({
    id: `goto-${s.id}`,
    label: `跳到：${s.title}`,
    hint: s.subtitle,
    keywords: [s.id, s.title, s.subtitle].join(' '),
    kind: 'goto',
    payload: { section: s.id, order: idx }
  })),
  {
    id: 'naiwa-laugh',
    label: '让奶蛙笑一下',
    hint: '需要趣味模式开启',
    keywords: 'naiwa laugh sound 笑声 奶蛙',
    kind: 'laugh'
  },
  {
    id: 'fun-toggle',
    label: '打开 / 关闭趣味模式',
    hint: '关掉后所有动画与彩蛋退场',
    keywords: 'fun mode 趣味 关闭 关掉',
    kind: 'fun'
  },
  {
    id: 'widget-toggle',
    label: '关闭奶蛙挂件',
    hint: '只关右下角挂件，不动其他装饰',
    keywords: 'widget 挂件 隐藏 关闭',
    kind: 'widget'
  },
  {
    id: 'back-to-top',
    label: '回到顶部',
    hint: '一键滚动到 hero',
    keywords: 'top scroll 顶部',
    kind: 'top'
  }
]

export const NAIWA_BUBBLE_LINES = [
  '想读哪一节？目录里点一下就好',
  'Cmd/Ctrl + K 也能搜章节哦',
  '看错题比刷新题更有用',
  '今天写一行也算',
  '遇到不会的，问问课件问答'
]

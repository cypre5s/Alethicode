/**
 * Alethicode 新手指南文案中心。
 *
 * 仅描述用户可见的页面与操作；不暴露后端实现、API、模型、提示词等技术内幕。
 * 章节、FAQ、命令面板条目集中在此，组件按 id/anchor 渲染。
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
    title: '欢迎与快速开始',
    subtitle: '花 3 分钟，把 Alethicode 走一遍',
    sticker: 0
  },
  {
    id: 'flow',
    title: '新手路径',
    subtitle: '从注册到看懂第一份判题结果',
    sticker: 1
  },
  {
    id: 'tour',
    title: '页面导览',
    subtitle: '所有核心页面与子视图，逐个讲清',
    sticker: 2
  },
  {
    id: 'core',
    title: '核心操作',
    subtitle: '写题、看 AI 卡、用错题本、问课件、入班级',
    sticker: 3
  },
  {
    id: 'ai',
    title: '智能辅助说明',
    subtitle: '5 位角色分工与正确使用姿势',
    sticker: 4
  },
  {
    id: 'faq',
    title: '常见问题',
    subtitle: '判题卡住、看不懂报错、AI 卡片不出现……',
    sticker: 5
  },
  {
    id: 'tips',
    title: '使用建议',
    subtitle: '比起追正确答案，更要看懂自己的错误',
    sticker: 6
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

export const QUICK_START_STEPS = [
  {
    step: 1,
    title: '注册账号',
    desc: '点击右上角"注册"，填用户名、邮箱、密码即可。已有账号直接登录。'
  },
  {
    step: 2,
    title: '挑一份课件',
    desc: '主页或顶部"课件问答"列出已发布的课件包，按内容选一份贴近你目前学习进度的。'
  },
  {
    step: 3,
    title: '做第一道题',
    desc: '进入"做题"页，挑一道难度低、知识点你刚好学过的题；写代码、提交、看判题结果。'
  }
]

export const FLOW_NODES = [
  { id: 'register', title: '注册 / 登录', target: 'welcome' },
  { id: 'home', title: '主页 Dashboard', target: 'tour' },
  { id: 'pack', title: '挑课件', target: 'tour' },
  { id: 'problem', title: '题库浏览', target: 'tour' },
  { id: 'code', title: '写第一份代码', target: 'core' },
  { id: 'ai', title: '看 AI 卡片', target: 'ai', highlight: true },
  { id: 'submit', title: '提交判题', target: 'core', highlight: true },
  { id: 'review', title: '看错题 / 复习', target: 'core', highlight: true }
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

export const CORE_OPERATIONS = [
  {
    id: 'write-problem',
    title: '写一道题：从打开到提交',
    body: [
      '在做题页右上角选语言（推荐 Python 3）。',
      '把代码写在编辑器里，可以使用快捷键运行（Ctrl/Cmd + Enter）。',
      '点底部"提交"按钮，结果会出现在"提交记录"里：通过、错答、超时、编译错误都各有不同颜色。',
      '点击某条记录可以查看具体测试用例输出，配合下方 AI 卡片定位错在哪一步。'
    ]
  },
  {
    id: 'use-ai-card',
    title: '看 AI 卡片：5 张分别在干嘛',
    body: [
      '做题页底部"AI 辅导"区会按你写代码、提交、判错的进度依次出现 5 种卡片。',
      '寧寧（审题卡）：把题目重写成你能听懂的版本，标出"该输入什么 / 输出什么"。',
      '芳乃（纠错卡）：定位你这次代码里最关键的一处错；不要一次给一堆建议。',
      '栞那（总结卡）：通过后给出"这一题用到了什么思路"的整理。',
      '村雨（进阶卡）：会推荐 1~2 道相关题，帮你把学过的知识点迁移过去。',
      '綾瀨（陪练卡）：自由对话，可以追问"为什么这样写"。',
      '所有 AI 输出都是辅助，不是替你写代码；写完一定要自己读一遍。'
    ]
  },
  {
    id: 'use-notebook',
    title: '用错题本：让自己变得不再害怕错题',
    body: [
      '判错的题会自动进错题本，按知识点分组。',
      '点开一题可以看到"上次错在哪 / 你当时写了什么"。',
      '在反思框里写一两句"我下次不会再 X"，比看十篇题解更有用。',
      '当某一类错累计 3 次以上，错题本会推送一个"专项复习包"，里面是同类型 3-5 题。'
    ]
  },
  {
    id: 'use-qa',
    title: '用课件问答：把概念讲明白',
    body: [
      '选一份课件包，问"这一节讲了什么"或具体名词解释。',
      '回答下方会列出引用的课件页，点页码可以打开 PDF 直接定位到那一页。',
      '问题越具体效果越好。例如把"什么是循环"换成"for 循环和 while 循环什么时候用哪个"。'
    ]
  },
  {
    id: 'join-classroom',
    title: '加入班级：和老师同学一起练',
    body: [
      '点导航栏"班级 → 加入班级"，输入老师给你的班级码。',
      '加入后能看到老师布置的题单与截止时间。',
      '按题单顺序练习，完成后回到班级页查看自己的进度变化。'
    ]
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

export const QA_GUIDE = {
  title: '课件问答使用指南',
  intro: '课件问答是基于你选择的课件包内容，用 AI 帮你快速找到概念解释和知识点的工具。它不是通用搜索引擎——只能回答课件里有的内容。',
  steps: [
    {
      step: 1,
      title: '选择课件包',
      desc: '进入"课件问答"页面后，先从下拉列表中选择一份课件包。课件包由老师上传并发布，对应某门课或某章内容。'
    },
    {
      step: 2,
      title: '输入你的问题',
      desc: '用自然语言提问，像问老师一样。越具体越好：把"什么是循环"换成"for 循环和 while 循环什么时候用哪个"效果会好很多。'
    },
    {
      step: 3,
      title: '阅读回答与引用',
      desc: '回答下方会列出引用的课件页码。点页码可直接打开 PDF 定位到那一页，对照原文确认 AI 回答是否准确。'
    },
    {
      step: 4,
      title: '追问或换问法',
      desc: '如果回答不够清楚，可以继续追问细节。如果完全没结果，说明这个问题超出了该课件的覆盖范围，换一份课件或换个问法试试。'
    }
  ],
  tips: [
    '问题越具体，回答越精准——"第 3 章讲的递归是什么意思"比"递归"好得多',
    '回答引用的页码可以直接点击跳转，不用自己翻 PDF',
    '课件问答只能基于已收录的课件内容，超出范围的问题会无答',
    'AI 回答可能有误，务必对照原文确认关键信息'
  ]
}

export const FAQ_ITEMS = [
  {
    q: '提交后判题一直 Pending 怎么办？',
    a: '通常 30 秒内会出结果。超过 1 分钟仍是 Pending，刷新页面，或在"提交记录"里看是否已经更新。如果集群临时拥塞，最多排队 2-3 分钟；持续超过 5 分钟可点反馈按钮告诉我们。'
  },
  {
    q: '看不懂判题给的报错信息？',
    a: '把报错信息原样贴进 AI 卡片中的"芳乃（纠错）"区，让她翻译成中文版"哪一行错了、为什么错"。如果是 Python 的 Traceback，从最底下一行的错误类型读起，往上找代码行号。'
  },
  {
    q: 'AI 卡片不出现 / 加载很久？',
    a: '先确认你已经"提交"了至少一次（卡片基于你的提交内容生成）。如果仍然空白，刷新页面；偶尔后台模型负载高会延迟 30-60 秒。'
  },
  {
    q: '课件问答没结果 / 答不对？',
    a: '换个更具体的问法（指明哪一节或哪一个名词）。课件问答只能从这份课件已收录的内容里找答案，超出范围就会无答。'
  },
  {
    q: '错题本里的"反思"该写什么？',
    a: '一两句话就够，例如"我把 range(n) 当成 1 到 n 了"、"我忘了字符串切片是左闭右开"。重点是用自己的话讲，不要复制题解。'
  },
  {
    q: '完全不会写代码可以先看示例吗？',
    a: '可以。点 AI 卡里的"看示例"，会出现一段"渐退示例"——大部分代码已经写好，只留几个关键空让你填。逐题填一两次就能上手。'
  },
  {
    q: '为什么有的课件我看不到？',
    a: '课件需要老师"发布"后学生才能看到。班级专属课件只对班内学生开放。'
  },
  {
    q: '我能不能不开 AI，自己默默练？',
    a: '可以。AI 卡片是辅助，不开也能正常做题。只要你点"提交"判题就会运行。AI 区域可以折叠收起。'
  }
]

export const TIPS = [
  {
    title: '每天写一点，胜过周末爆肝',
    desc: '哪怕 10 分钟，关键是每天都让大脑想一次"这道题怎么解"。'
  },
  {
    title: '看错题比看答案更有用',
    desc: '直接看正确题解只能让你"懂"，看自己错在哪才能让你"会"。'
  },
  {
    title: '用自己的话讲一遍代码',
    desc: 'AC 完，把代码每一行用一句话讲清楚。讲不通就是没真懂。'
  },
  {
    title: '不会的概念用课件问答兜底',
    desc: '别在百度搜里转 30 分钟。回到课件 + 问答里直接问，5 分钟搞定。'
  },
  {
    title: 'AI 是辅助，不是答案',
    desc: '它能给方向但会错。写完读一遍，自己跑一下脑里的逻辑。'
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

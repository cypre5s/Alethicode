-- Career Path Map + Micro Project V86（plan 2.4 节）
--   1. career_micro_project：Project Studio 生成的微项目（学生 × 专业 × KC 集）；
--      judge_problem_id 关联至 problem 表（Studio 生成的题目作为正常 problem 流走判题）
--   2. career_path_node：Career Path Map 的「专业 × KC」桥接关系；同 KC 在
--      不同专业有不同的 why_md（GraphRAG 解释）。kc_code 复用现有 KC 体系
--      （V13 / V25 / V51 已建），不引入新 KC——只新增「Domain × KC」一层。
--   3. 落 12 个高占比专业的种子 path_node（plan 2.4 节强约束：第一批人工编辑，
--      不允许 LLM 直接写入；后续 LLM 辅助仅做候选，需人审才入库）

CREATE TABLE IF NOT EXISTS career_micro_project (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    major_code VARCHAR(64) NOT NULL,
    title TEXT NOT NULL,
    brief_md TEXT NOT NULL,
    judge_problem_id BIGINT REFERENCES problem(id) ON DELETE SET NULL,
    related_kcs JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    score INTEGER,
    completed_at TIMESTAMPTZ,
    portfolio_card_uri VARCHAR(512),
    rollout_mode VARCHAR(16) NOT NULL DEFAULT 'baseline',
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_micro_project_user_status
    ON career_micro_project(user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_micro_project_judge_problem
    ON career_micro_project(judge_problem_id) WHERE judge_problem_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS career_path_node (
    id BIGSERIAL PRIMARY KEY,
    major_code VARCHAR(64) NOT NULL,
    kc_code VARCHAR(128) NOT NULL,
    parent_kc_code VARCHAR(128),
    why_md TEXT NOT NULL,
    typical_use_cases JSONB NOT NULL DEFAULT '[]'::jsonb,
    sort_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (major_code, kc_code)
);

CREATE INDEX IF NOT EXISTS idx_career_path_node_major
    ON career_path_node(major_code, sort_order);

-- 种子：12 个专业 × 5 个核心 Python KC（variables / data_types / collections /
-- control_flow / functions），形成 5 节点的线性 DAG（链式 parent_kc_code）。
-- 每个 why_md 与 typical_use_cases 严格基于 V83 字典里该专业的 seed_use_cases，
-- 不编造。用 ON CONFLICT DO NOTHING 保证幂等。

INSERT INTO career_path_node (major_code, kc_code, parent_kc_code, why_md, typical_use_cases, sort_order) VALUES
-- biology
('biology', 'variables', NULL,
 '生物学里的样本编号、基因长度、温度记录都是变量。把 sample_id="S001"、gene_length=1500 装进 Python 变量，是后续所有计算的起点。',
 '["实验记录编号","基因长度记账"]'::jsonb, 1),
('biology', 'data_types', 'variables',
 '统计 GC 含量需要 int / float；基因序列是 str；多组测量值用 list。类型选错（如把序列当数字加）就直接报错，是初学者最常踩的坑。',
 '["GC 含量分布","序列读写"]'::jsonb, 2),
('biology', 'collections', 'data_types',
 '物种丰度调查的核心动作是「按物种名累加出现次数」——dict 的 key=物种、value=次数；FASTA 文件的多个序列存进 list 后逐条处理。',
 '["物种频次表","FASTA 多序列处理"]'::jsonb, 3),
('biology', 'control_flow', 'collections',
 '剔除异常孔位的计数表 = for 遍历 + if 阈值；PCR 引物长度筛选 = for + if 18 ≤ len ≤ 25。条件与循环把"实验直觉"翻译成可执行代码。',
 '["孔位异常剔除","引物长度筛选"]'::jsonb, 4),
('biology', 'functions', 'control_flow',
 '把 GC 含量计算、组对照差异、引物筛选包成函数后，整个分析流程可以串起来跑、写测试、复用到下一批数据。',
 '["GC 含量函数","对照组差异函数"]'::jsonb, 5),

-- chemistry
('chemistry', 'variables', NULL,
 '化学的浓度 c=0.1、体积 V=25.0、pH=7.4 都是变量。把实验参数装进有意义名字（不是 a/b/c）能让你三个月后回看实验记录还看得懂。',
 '["实验参数记账","浓度跟踪"]'::jsonb, 1),
('chemistry', 'data_types', 'variables',
 '摩尔浓度需要 float（小数关键）；元素符号是 str；滴定数据是 list of float。误把质量当浓度（漏除以体积）是手算时最常见错误，类型化让计算更显性。',
 '["摩尔浓度计算","滴定序列"]'::jsonb, 2),
('chemistry', 'collections', 'data_types',
 '反应物配平在内部就是 dict（元素 → 系数）的相等校验；pH-加酸量曲线是 list of (酸量, pH) 二元组。',
 '["方程配平","曲线数据"]'::jsonb, 3),
('chemistry', 'control_flow', 'collections',
 '滴定终点 = for 遍历曲线 + 找 pH 突变点；缓冲容量 = if pH 在 [pKa-1, pKa+1] 内累加。控制流让"找拐点"这种实验直觉精确化。',
 '["滴定终点","缓冲区间"]'::jsonb, 4),
('chemistry', 'functions', 'control_flow',
 '把摩尔浓度换算、滴定终点定位、配平校验封成函数后，可以批量跑多组实验数据，输出一份汇总表。',
 '["浓度换算函数","终点定位函数"]'::jsonb, 5),

-- medicine
('medicine', 'variables', NULL,
 '体检的身高、体重、收缩压、舒张压都是变量。给变量起个像 systolic_bp 这样的名字（而不是 sbp 或 a）后，临床同事能直接读懂你的脚本。',
 '["体检参数命名","随访间隔记账"]'::jsonb, 1),
('medicine', 'data_types', 'variables',
 'BMI 需要 float（保留小数）；性别是 str；多次随访时间是 list of date 字符串。类型选错（如用整数算 BMI 把小数截断）会让风险分层结果偏移。',
 '["BMI 计算","随访时间序列"]'::jsonb, 2),
('medicine', 'collections', 'data_types',
 '队列基线表的核心动作 = dict（性别/年龄段 → 计数）；用药冲突清单 = set 取交集判断同日多种药。',
 '["队列基线汇总","用药冲突检测"]'::jsonb, 3),
('medicine', 'control_flow', 'collections',
 '高血压风险分层 = if 嵌套规则；随访时间窗校验 = for + if 间隔超阈值。控制流把"分层规则"从指南文档变成可跑的代码。',
 '["风险分层","随访时窗"]'::jsonb, 4),
('medicine', 'functions', 'control_flow',
 '把 BMI 与风险分层、随访窗校验、病案号脱敏封成函数后，整张体检 csv 可以一行调用产出报告。',
 '["分层函数","脱敏函数"]'::jsonb, 5),

-- pharmacy
('pharmacy', 'variables', NULL,
 '药学的剂量 dose、半衰期 t_half、批号 lot_no 都是变量；给变量起对名字让处方审核脚本读起来像「半衰期 < 4 小时则警告」而不是「t < 4」。',
 '["处方剂量记账","批号管理"]'::jsonb, 1),
('pharmacy', 'data_types', 'variables',
 '半衰期估算需要 float；药品名 str；血药浓度序列 list of float。类型化让一阶动力学拟合（log 计算）不出错。',
 '["半衰期估算","浓度序列"]'::jsonb, 2),
('pharmacy', 'collections', 'data_types',
 '配伍禁忌检索 = set 查交；库存到期清单 = list 按日期过滤；药品台账 = dict (药名 → {批号, 数量, 到期}).',
 '["配伍禁忌","库存管理"]'::jsonb, 3),
('pharmacy', 'control_flow', 'collections',
 '处方剂量审核 = for + if 超说明书上下限；批号末位校验 = if 计算出的校验位 != 实际位则报警。',
 '["剂量审核","批号校验"]'::jsonb, 4),
('pharmacy', 'functions', 'control_flow',
 '把半衰期估算、剂量审核、库存到期提醒封装成函数后，调度系统可以每天自动生成一份预警报告。',
 '["半衰期函数","审核函数"]'::jsonb, 5),

-- clinical-medicine
('clinical-medicine', 'variables', NULL,
 '临床的 GCS 三项（睁眼/语言/运动）评分、生命体征（体温/脉搏/呼吸/血压）都是变量；命名为 eye_score/verbal_score/motor_score 远好于 a/b/c。',
 '["GCS 三项评分","NEWS2 体征"]'::jsonb, 1),
('clinical-medicine', 'data_types', 'variables',
 '昏迷分级是 int（求和）；生命体征 float；分诊优先级 str（红/黄/绿）。类型化让 GCS 求和与 NEWS2 阈值打分都不会出错。',
 '["GCS 求和","NEWS2 打分"]'::jsonb, 2),
('clinical-medicine', 'collections', 'data_types',
 '急诊候诊队列 = list of (患者, 优先级, 到诊时间)；ICU 出入量平衡 = dict (类型 → 累计ml)；手术核查清单 = set 比对。',
 '["候诊队列","出入量"]'::jsonb, 3),
('clinical-medicine', 'control_flow', 'collections',
 'NEWS2 打分 = for 遍历体征区间 + if 累加；急诊分诊排队 = sorted by (优先级 desc, 到诊时间 asc)；都是规则到代码的直接映射。',
 '["NEWS2 流水线","分诊排序"]'::jsonb, 4),
('clinical-medicine', 'functions', 'control_flow',
 '把 GCS、NEWS2、分诊、出入量平衡各封一个函数，整个值班逻辑就可以拼成可测、可复用、可审的工作流。',
 '["GCS 函数","NEWS2 函数"]'::jsonb, 5),

-- management
('management', 'variables', NULL,
 '工商管理的 KPI 数值、月份、部门代码都是变量；给变量起 monthly_revenue / dept_code 这种名字能让财务同事直接看懂。',
 '["KPI 命名","部门代码"]'::jsonb, 1),
('management', 'data_types', 'variables',
 '销售金额 float；订单 id str；月度数据 list of float。类型化让汇总（sum）、排序（sorted）这些常用动作不会出 bug。',
 '["销售金额","月度序列"]'::jsonb, 2),
('management', 'collections', 'data_types',
 '销售报表汇总 = dict (区域 → 金额累加)；流程审批耗时 = list of timedelta；预算执行率 = dict (部门 → 实际/预算).',
 '["销售汇总","审批耗时"]'::jsonb, 3),
('management', 'control_flow', 'collections',
 '考勤异常筛查 = for + if 累计迟到 > 阈值；预算执行率告警 = for + if 比例超 90%；用 if/else 把规则写出来。',
 '["考勤异常","预算告警"]'::jsonb, 4),
('management', 'functions', 'control_flow',
 '把销售汇总、考勤筛查、预算执行率封成函数后，月度报表可以一行命令生成，复用到任意月份。',
 '["销售汇总函数","报表函数"]'::jsonb, 5),

-- economics
('economics', 'variables', NULL,
 '经济学的 GDP、CPI、失业率、汇率都是变量；命名要让人一眼看出含义，比如 cpi_yoy（同比）/cpi_mom（环比）。',
 '["指数命名","汇率记账"]'::jsonb, 1),
('economics', 'data_types', 'variables',
 'CPI 序列 list of float；国家代码 str；汇率 float；类型化让回归系数 / 汇率换算这些数值计算不会被字符串拼接误导。',
 '["CPI 序列","汇率换算"]'::jsonb, 2),
('economics', 'collections', 'data_types',
 'CPI 同比环比 = dict (月份 → 指数)；面板数据 = list of dict；基尼系数计算 = list of (累计人口比, 累计收入比) 二元组。',
 '["CPI 表","面板数据"]'::jsonb, 3),
('economics', 'control_flow', 'collections',
 '季节调整 = for 12 个月窗口 + 平均剔除；线性回归手算 = for 累加 (xi-x̄)(yi-ȳ) / sum((xi-x̄)²)；都是数学公式直译。',
 '["季节调整","回归手算"]'::jsonb, 4),
('economics', 'functions', 'control_flow',
 '把 CPI 同环比、回归手算、汇率换算各封成函数，做一次研究即可重复跑多次模型。',
 '["CPI 函数","回归函数"]'::jsonb, 5),

-- finance
('finance', 'variables', NULL,
 '金融的价格 price、收益率 return、净值 nav、持仓 position 都是变量；命名清晰能让风控同事一眼看懂你的脚本逻辑。',
 '["价格记账","收益率命名"]'::jsonb, 1),
('finance', 'data_types', 'variables',
 '日收益率 float；股票代码 str；价格序列 list of float。类型化让年化（**365）、最大回撤（min/max）这些计算不出整数除法陷阱。',
 '["日收益率","年化"]'::jsonb, 2),
('finance', 'collections', 'data_types',
 '组合权重 = dict (股票 → 权重)；价格历史 = list of float；股息率排序 = list of (股票, 股息率) 后 sorted by 股息率 desc。',
 '["组合权重","股息排序"]'::jsonb, 3),
('finance', 'control_flow', 'collections',
 '最大回撤 = for 遍历净值 + 维护历史峰值；等额本息月供计算 = for 期数 + 利息累计；规则到代码直译。',
 '["最大回撤","月供计算"]'::jsonb, 4),
('finance', 'functions', 'control_flow',
 '把日收益、最大回撤、月供、再平衡都封成函数后，回测一个新策略只需替换数据即可。',
 '["回撤函数","月供函数"]'::jsonb, 5),

-- statistics
('statistics', 'variables', NULL,
 '统计学的样本量 n、均值 mean、方差 var、p_value 都是变量；命名要遵循统计学习惯（mu / sigma / x_bar），让同行一眼读懂。',
 '["统计量命名","样本量记账"]'::jsonb, 1),
('statistics', 'data_types', 'variables',
 '样本数据 list of float；分组列 str；频数 int；类型化让方差 / 中位数 / 卡方期望频数计算保持精度。',
 '["样本序列","频数表"]'::jsonb, 2),
('statistics', 'collections', 'data_types',
 '描述性统计 = dict (列名 → {均值, 中位数, 方差})；分组对比 = dict (组 → list of values)；列联表 = list of list of int。',
 '["描述统计","分组数据"]'::jsonb, 3),
('statistics', 'control_flow', 'collections',
 '简单随机抽样 = for + random（无放回）；卡方期望频数 = for 遍历行列乘积 / 总频数。控制流把"抽样规则"从教材变成可跑代码。',
 '["简单抽样","卡方频数"]'::jsonb, 4),
('statistics', 'functions', 'control_flow',
 '把描述统计、分组对比、卡方期望封成函数后，分析每一份新数据只需要几行调用，整个流程可测可复用。',
 '["描述函数","卡方函数"]'::jsonb, 5),

-- psychology
('psychology', 'variables', NULL,
 '心理学的受试编号 subject_id、量表得分 score、反应时 rt 都是变量；命名清晰能让你和同行直接基于变量名复现实验。',
 '["受试编号","量表得分"]'::jsonb, 1),
('psychology', 'data_types', 'variables',
 '反应时 float（毫秒精度）；量表选项 int 1-5；试次列表 list of dict；类型化让反向计分（6 - 原始）这些常见操作不出错。',
 '["反应时记账","反向计分"]'::jsonb, 2),
('psychology', 'collections', 'data_types',
 '量表反向计分 = list 反向题 + dict 映射；反应时清洗 = list 剔除超过 3σ 的极端值；分组得分 = dict (受试 → 累加).',
 '["反向计分","RT 清洗"]'::jsonb, 3),
('psychology', 'control_flow', 'collections',
 '反应时清洗 = for + if 超 3σ 则丢；克隆巴赫 alpha 估算 = for 遍历题项协方差 + 公式累加。',
 '["RT 阈值","alpha 估算"]'::jsonb, 4),
('psychology', 'functions', 'control_flow',
 '把反向计分、RT 清洗、alpha 估算封成函数后，每收一批新数据只要一行代码就能产出量表信度报告。',
 '["计分函数","alpha 函数"]'::jsonb, 5),

-- mechanical-engineering
('mechanical-engineering', 'variables', NULL,
 '机械的转速 rpm、扭矩 torque、公差 tolerance 都是变量；命名要遵循专业（spindle_rpm / cutter_life）让 CAM 同事直接看懂脚本。',
 '["转速命名","公差记账"]'::jsonb, 1),
('mechanical-engineering', 'data_types', 'variables',
 '齿轮齿数 int；扭矩 float；BOM 编码 str；类型化让传动比换算（转速比 = 主齿/从齿）整数除法不出陷阱。',
 '["齿数计算","扭矩拆解"]'::jsonb, 2),
('mechanical-engineering', 'collections', 'data_types',
 '装配 BOM 拆解 = dict (零件 → 数量)；刀具寿命统计 = list of (工时, 切削次数)；测温曲线 = list of float。',
 '["BOM 拆解","曲线平滑"]'::jsonb, 3),
('mechanical-engineering', 'control_flow', 'collections',
 '齿轮传动比 = for 多级累乘；公差等级判定 = if 偏差落在标准区间；测温曲线滑动平均 = for 窗口求均。',
 '["传动比","滑动平均"]'::jsonb, 4),
('mechanical-engineering', 'functions', 'control_flow',
 '把传动比、公差判定、刀具寿命估算各封成函数后，加工车间每天的报表脚本几行就能生成。',
 '["传动函数","寿命函数"]'::jsonb, 5),

-- civil-engineering
('civil-engineering', 'variables', NULL,
 '土木的跨度 span、荷载 load、混凝土用量 volume 都是变量；命名遵循专业（beam_span / dead_load）让结构同事直接看懂脚本。',
 '["跨度命名","荷载记账"]'::jsonb, 1),
('civil-engineering', 'data_types', 'variables',
 '梁跨度 float；钢筋型号 str；构件数量 int；类型化让弯矩计算（q*L²/8）保持精度，避免单位换算错误。',
 '["弯矩计算","钢筋型号"]'::jsonb, 2),
('civil-engineering', 'collections', 'data_types',
 '钢筋表 = dict (规格 → 总长)；施工进度 = list of (工序, 起, 止)；测量序列 = list of float。',
 '["钢筋表","进度表"]'::jsonb, 3),
('civil-engineering', 'control_flow', 'collections',
 '关键路径 = for 工序 + 累计前置；水准测量闭合差 = for 累加高差 + if 闭合差 > 阈值；规则直译。',
 '["关键路径","闭合差"]'::jsonb, 4),
('civil-engineering', 'functions', 'control_flow',
 '把弯矩、钢筋表、进度甘特、混凝土用量封成函数，整个项目交付脚本可以批量跑多个工地。',
 '["弯矩函数","进度函数"]'::jsonb, 5)
ON CONFLICT (major_code, kc_code) DO NOTHING;

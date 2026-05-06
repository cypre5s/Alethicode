-- Career Bridging V83：扩展 user_profile + 新建 career_major_dictionary + 12 条种子
--   1. user_profile 追加 major_code / career_intent / career_profile_completed_at
--      (plan 0 节强约束：不新建用户档案表，仅扩展现有 user_profile)
--   2. career_major_dictionary：专业字典表，承载 Career Bridging / Coding Lens 的
--      seed_keywords / seed_use_cases / seed_kcs，是模块 1-4 的共享 evidence 源
--   3. 种子 12 条高占比专业，全部为 Python 标准库可达成的真实场景

ALTER TABLE user_profile
    ADD COLUMN IF NOT EXISTS major_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS career_intent TEXT,
    ADD COLUMN IF NOT EXISTS career_profile_completed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_user_profile_major_code
    ON user_profile(major_code) WHERE major_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS career_major_dictionary (
    code VARCHAR(64) PRIMARY KEY,
    name_zh VARCHAR(128) NOT NULL,
    name_en VARCHAR(128),
    discipline VARCHAR(64) NOT NULL,
    seed_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    seed_use_cases JSONB NOT NULL DEFAULT '[]'::jsonb,
    seed_kcs JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_career_major_dictionary_discipline
    ON career_major_dictionary(discipline) WHERE enabled = TRUE;

INSERT INTO career_major_dictionary (code, name_zh, name_en, discipline, seed_keywords, seed_use_cases) VALUES
('biology', '生物科学', 'Biology', 'natural-science',
 '["DNA","序列","基因","实验数据","物种"]'::jsonb,
 '[
   {"name":"DNA 序列 GC 含量计算","detail":"读取 FASTA 文本，统计 G/C 碱基占比，定位高 GC 区域"},
   {"name":"细胞计数表清洗","detail":"读取显微镜计数 csv，剔除异常孔位，输出每孔均值"},
   {"name":"物种丰度排序","detail":"读取群落调查 tsv，按出现频次降序输出物种名单"},
   {"name":"实验组对照组对比","detail":"按时间窗汇总两组测量值，计算差异并打印"},
   {"name":"PCR 引物长度筛选","detail":"读引物列表，按 18-25 nt 长度过滤合格引物"}
 ]'::jsonb),
('chemistry', '化学', 'Chemistry', 'natural-science',
 '["反应","摩尔","浓度","pH","滴定"]'::jsonb,
 '[
   {"name":"摩尔浓度换算","detail":"输入溶质质量、分子量、体积，输出摩尔浓度"},
   {"name":"滴定终点计算","detail":"输入滴定数据序列，定位电导率突变点"},
   {"name":"pH 缓冲容量","detail":"读取 pH-加酸量曲线，估算缓冲区间"},
   {"name":"反应物配平校验","detail":"按元素守恒校验给定方程式系数"},
   {"name":"实验记录批量改写","detail":"批处理实验编号、温度单位与小数位"}
 ]'::jsonb),
('medicine', '医学（基础）', 'Medicine', 'medical-science',
 '["体征","随访","队列","诊断","用药"]'::jsonb,
 '[
   {"name":"BMI 与高血压风险分层","detail":"读取体检 csv，按规则输出风险等级"},
   {"name":"门诊随访时间窗校验","detail":"判断每位患者两次随访间隔是否超阈值"},
   {"name":"队列基线表汇总","detail":"按性别/年龄段统计样本量与缺失率"},
   {"name":"用药剂量重叠提醒","detail":"识别同一日期多种药物冲突组合"},
   {"name":"病案号脱敏","detail":"按规则替换病历号末位为遮罩字符"}
 ]'::jsonb),
('pharmacy', '药学', 'Pharmacy', 'medical-science',
 '["剂量","半衰期","处方","药代","制剂"]'::jsonb,
 '[
   {"name":"半衰期估算","detail":"按一阶动力学输入血药浓度序列，计算半衰期"},
   {"name":"处方剂量审核","detail":"按体重换算给药量并比对说明书上下限"},
   {"name":"药品库存到期提醒","detail":"按生产日期与保质期筛选 30 天内到期药品"},
   {"name":"配伍禁忌检索","detail":"在禁忌表中查找处方组合冲突"},
   {"name":"批号尾数校验","detail":"按算法计算批号校验位是否匹配"}
 ]'::jsonb),
('clinical-medicine', '临床医学', 'Clinical Medicine', 'medical-science',
 '["主诉","查体","评分","急诊","ICU"]'::jsonb,
 '[
   {"name":"GCS 评分计算","detail":"输入睁眼/语言/运动评分，输出昏迷等级"},
   {"name":"早期预警评分 NEWS2","detail":"按生命体征区间打分，超阈值标红"},
   {"name":"急诊分诊排队","detail":"按优先级与到诊时间生成候诊顺序"},
   {"name":"ICU 出入量平衡","detail":"汇总 24 小时液体出入并提醒失衡"},
   {"name":"手术核查清单匹配","detail":"按清单条目检查电子病历完整性"}
 ]'::jsonb),
('management', '工商管理', 'Business Management', 'social-science',
 '["KPI","报表","流程","人力","预算"]'::jsonb,
 '[
   {"name":"销售报表汇总","detail":"按月份/区域聚合订单金额并排序"},
   {"name":"考勤异常筛查","detail":"识别迟到早退累计超阈值的员工"},
   {"name":"库存周转天数","detail":"按销售与库存历史计算周转指标"},
   {"name":"流程审批耗时","detail":"按节点时间戳输出平均审批时长"},
   {"name":"预算执行率","detail":"按部门聚合实际支出/预算比例"}
 ]'::jsonb),
('economics', '经济学', 'Economics', 'social-science',
 '["GDP","通胀","回归","面板","CPI"]'::jsonb,
 '[
   {"name":"CPI 同比环比","detail":"读取月度价格指数序列，输出同比/环比"},
   {"name":"线性回归手算","detail":"对小样本数据用最小二乘求斜率截距"},
   {"name":"基尼系数","detail":"按收入分组数据计算洛伦兹曲线与基尼"},
   {"name":"季节调整","detail":"按 12 月移动平均剔除季节项"},
   {"name":"汇率换算","detail":"按汇率表换算多币种金额到本币"}
 ]'::jsonb),
('finance', '金融学', 'Finance', 'social-science',
 '["收益率","净值","风险","组合","回撤"]'::jsonb,
 '[
   {"name":"日收益率与年化","detail":"读取价格序列，计算日收益、年化收益"},
   {"name":"最大回撤","detail":"在净值序列上求最大回撤区间与幅度"},
   {"name":"组合权重再平衡","detail":"按目标权重计算调仓买卖量"},
   {"name":"贷款等额本息","detail":"按利率/期限计算月供与利息总额"},
   {"name":"股息率排序","detail":"按当前价与去年股息计算股息率并排序"}
 ]'::jsonb),
('statistics', '统计学', 'Statistics', 'natural-science',
 '["样本","均值","方差","假设检验","抽样"]'::jsonb,
 '[
   {"name":"描述性统计","detail":"对数值列输出均值/方差/中位数/分位数"},
   {"name":"分组对比","detail":"按因子列拆分子样本输出组间均值差"},
   {"name":"频数分布表","detail":"按区间宽度生成直方分布与累计频率"},
   {"name":"简单随机抽样","detail":"对人员名单做无放回抽样并输出编号"},
   {"name":"卡方期望频数","detail":"对二维列联表计算期望频数与差异"}
 ]'::jsonb),
('psychology', '心理学', 'Psychology', 'social-science',
 '["量表","受试","反应时","信度","因素"]'::jsonb,
 '[
   {"name":"量表反向计分","detail":"按反向题列表对原始得分批量翻转"},
   {"name":"反应时清洗","detail":"剔除超过 3 倍标准差的极端反应时"},
   {"name":"分组得分汇总","detail":"按受试编号聚合多次试次得分"},
   {"name":"克隆巴赫 alpha 估算","detail":"按题目协方差近似计算量表信度"},
   {"name":"实验编号去重","detail":"识别重复登记的受试编号"}
 ]'::jsonb),
('mechanical-engineering', '机械工程', 'Mechanical Engineering', 'engineering',
 '["扭矩","传动","加工","公差","刀具"]'::jsonb,
 '[
   {"name":"齿轮传动比换算","detail":"输入主从动齿数，计算转速比与扭矩比"},
   {"name":"公差等级判定","detail":"按基本尺寸与偏差判断公差等级"},
   {"name":"刀具寿命统计","detail":"按工时与切削次数估算剩余寿命"},
   {"name":"BOM 拆解","detail":"按层级展开装配清单并汇总数量"},
   {"name":"测温曲线平滑","detail":"对加工温度序列做滑动平均"}
 ]'::jsonb),
('civil-engineering', '土木工程', 'Civil Engineering', 'engineering',
 '["梁","荷载","钢筋","施工","进度"]'::jsonb,
 '[
   {"name":"梁的均布荷载弯矩","detail":"按跨度与线荷载计算最大弯矩"},
   {"name":"钢筋表汇总","detail":"按规格直径计算理论质量与总长度"},
   {"name":"施工进度甘特","detail":"按工序起止时间生成关键路径列表"},
   {"name":"测量点高差校核","detail":"对水准测量序列校核闭合差"},
   {"name":"商品混凝土用量","detail":"按构件几何尺寸估算混凝土方量"}
 ]'::jsonb)
ON CONFLICT (code) DO NOTHING;

# Classroom 教师端监控增强 — 分阶段实施计划

## 现状审计（2026-04-07）

### 已完成功能

| 功能 | 后端 | 前端 | 说明 |
|------|------|------|------|
| 周脉搏（7天趋势） | ✅ `ClassroomAnalyticsService.getWeeklyPulse` | ✅ ECharts 柱状+折线图 | 每日提交数/AC数/活跃学生 |
| KC 掌握度热力图 | ✅ `ClassroomAnalyticsService.getKcMasteryHeatmap` | ✅ ECharts 热力图 | 学生×知识点矩阵 |
| 薄弱知识点 TOP3 + LLM 建议 | ✅ `ClassroomAnalyticsService.getWeakKcSuggestions` | ✅ 列表+LLM建议 | LLM 基于薄弱KC生成教学建议 |
| 风险学生预警 | ✅ `StudentRiskDetectionService.getEnhancedRiskList` | ✅ 表格展示 | 规则引擎：掌握度/连续错误/活跃度 |
| 课件使用分析 | ✅ `ClassroomAnalyticsService.getCoursewareUsage` | ✅ 已接入 | 按章节柱状图 + QA频率列表 |

---

## 第一阶段：风险预警 LLM 增强 + 课件分析前端接入 ✅

### 1.1 风险预警 LLM 干预建议 ✅

**后端**：`StudentRiskDetectionService.generateInterventionAdvice(classroomId, studentUserId, auth)`
- 采集：掌握度、做题量、连续错误数、最薄弱3个KC
- LLM prompt 基于真实数据生成 2-3 条干预建议
- API: `GET /api/classroom/{id}/analytics/risk-students/{userId}/advice`

**前端**：`ClassroomAnalytics.vue`
- 风险学生表格操作列新增「建议」按钮（仅 medium/high/critical 显示）
- 弹窗展示：学生基本信息 + 薄弱KC列表 + LLM 建议列表

**验收**：
- [x] 教师点击高风险学生「建议」按钮，弹窗展示 LLM 干预建议
- [x] 建议内容基于学生实际数据，非固定模板

### 1.2 课件使用分析前端接入 ✅

**前端**：`ClassroomAnalytics.vue`
- 新增「课件使用分析」卡片，ECharts 柱状图展示按章节提交量/AC数/活跃学生数
- 柱状图下方展示 QA 频率列表（课件页码 + 提问次数）

**验收**：
- [x] 数据看板底部展示课件使用分析区域
- [x] 柱状图正确渲染各章节数据
- [x] QA 频率列表展示热门提问页码

---

## 第二阶段：LLM 班级学情周报 ✅

**后端**：`ClassroomAnalyticsService.generateWeeklyReport(classroomId, auth)`
- 数据采集：总学生数、活跃人数、提交总量、AC率、班级平均掌握度、薄弱KC TOP3、风险学生数
- LLM 生成 3-4 段分析报告（活跃度/掌握进展/风险点/教学建议）
- API: `GET /api/classroom/{id}/analytics/weekly-report`

**前端**：`ClassroomAnalytics.vue`
- 数据看板顶部「生成学情周报」按钮
- 报告展示：标题 + 数据摘要标签（活跃/提交/AC率/掌握度/风险） + 分段内容

**验收**：
- [x] 教师点击「生成学情周报」，展示 LLM 分析报告
- [x] 报告包含活跃度分析、知识掌握进展、风险学生提醒、教学建议
- [x] 数据摘要标签与实际数据一致

---

## 第三阶段：学生个体学情画像 ✅

**后端**：`ClassroomAnalyticsService.getStudentProfile(classroomId, studentUserId, auth)`
- 6维数据聚合：基本信息、KC掌握度列表（按章节排序）、30天做题时间线、错题类型分布、最近5条提交、连续做题天数
- LLM 生成 2-3 句个性化学情总结
- API: `GET /api/classroom/{id}/analytics/student/{userId}/profile`

**前端**：`ClassroomAnalytics.vue`
- 风险学生表格操作列新增「画像」按钮
- 弹窗展示：
  - 数据摘要栏（掌握度/做题数/通过数/连续天数）
  - LLM 学情总结
  - KC 掌握度雷达图（ECharts radar）
  - 错题类型饼图（ECharts pie）
  - 30天做题趋势柱状图（ECharts bar）
  - 最近提交记录表格

**验收**：
- [x] 教师点击「画像」，弹窗展示完整学情画像
- [x] 雷达图反映各 KC 掌握度
- [x] LLM 总结基于真实数据
- [x] 无虚构数据

---

## 测试数据

注入 5 个测试学生（stu_1~stu_5）：

| 学生 | 掌握度 | 做题/通过 | 近7天提交 | 特征 |
|------|--------|----------|----------|------|
| stu_1 | 35% | 12/4 | 6 | 薄弱学生 |
| stu_2 | 55% | 20/11 | 6 | 中等水平 |
| stu_3 | 72% | 28/20 | 6 | 较好 |
| stu_4 | 18% | 6/1 | 8（全WA） | 极高风险，连续错误 |
| stu_5 | 80% | 32/26 | 0 | 优秀但不活跃 |

密码：`root123456`（与 root 账户相同的 hash）

---

## 实施约束（来自 AGENTS.md）

- 不允许兼容性/补丁性方案
- 不允许过度设计，保持最短路径实现
- 不要写防御性逻辑，要求 fail-fast
- 必须确保逻辑正确，经过全链路验证
- LLM 输出不得造假，必须基于真实数据

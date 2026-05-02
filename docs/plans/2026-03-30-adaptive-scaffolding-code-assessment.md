# Adaptive Scaffolding And Code Assessment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在现有 workflow 与 submission 主链上，完成自适应渐退脚手架和 Python3 多维代码评估的完整闭环。

**Architecture:** 后端继续收口在 `AITutorWorkflowAdminServiceImpl` 和 `SubmissionServiceImpl`，脚手架按 `ScaffoldLevelResolver` 分流到不同 card generator，代码评估继续写回 `submission.statistic_info`。前端只在现有 AI 面板和提交详情页增加对应卡片与展示分支。

**Tech Stack:** Spring Boot, JdbcTemplate, Vue 3, view-ui-plus, Jest, JUnit 5, PostgreSQL JSONB

---

### Task 1: 脚手架基础契约

**Files:**
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/contract/CardType.java`
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/schema/CardSchemaRegistry.java`
- Test: `backend/src/test/java/com/alethicode/service/aitutor/schema/CardSchemaValidatorTest.java`

**Step 1:** 写失败测试，锁定三种新脚手架卡片 schema。

**Step 2:** 运行测试确认失败。

**Step 3:** 最小实现 `CardType` 与 schema 注册。

**Step 4:** 运行测试确认通过。

### Task 2: 脚手架分层决策

**Files:**
- Create: `backend/src/main/java/com/alethicode/service/aitutor/scaffolding/ScaffoldLevel.java`
- Create: `backend/src/main/java/com/alethicode/service/aitutor/scaffolding/ScaffoldLevelResolver.java`
- Test: `backend/src/test/java/com/alethicode/service/aitutor/scaffolding/ScaffoldLevelResolverTest.java`

**Step 1:** 写失败测试，覆盖阈值边界与多 KC 最小值聚合。

**Step 2:** 运行测试确认失败。

**Step 3:** 最小实现 resolver。

**Step 4:** 运行测试确认通过。

### Task 3: 脚手架 payload 生成与 workflow 接线

**Files:**
- Create: `backend/src/main/java/com/alethicode/service/aitutor/scaffolding/WorkedExampleGenerator.java`
- Create: `backend/src/main/java/com/alethicode/service/aitutor/scaffolding/FadedExampleGenerator.java`
- Create: `backend/src/main/java/com/alethicode/service/aitutor/scaffolding/MinimalHintGenerator.java`
- Modify: `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/policy/TutorActionPolicy.java`
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/policy/ContextualBanditReranker.java`
- Test: `backend/src/test/java/com/alethicode/integration/AITutorWorkflowStateMachineIntegrationTest.java`

**Step 1:** 写失败测试，锁定 `SCAFFOLDING` 四档输出。

**Step 2:** 运行测试确认失败。

**Step 3:** 最小实现 generator 与 workflow 接线。

**Step 4:** 运行测试确认通过。

### Task 4: Faded Example 填空验证闭环

**Files:**
- Modify: `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- Test: `backend/src/test/java/com/alethicode/integration/AITutorWorkflowStateMachineIntegrationTest.java`

**Step 1:** 写失败测试，锁定填空提交、逐步反馈、mastery 更新。

**Step 2:** 运行测试确认失败。

**Step 3:** 最小实现验证分支。

**Step 4:** 运行测试确认通过。

### Task 5: 代码评估与部分分

**Files:**
- Create: `backend/src/main/java/com/alethicode/service/aitutor/assessment/CodeQualityAssessmentService.java`
- Modify: `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`
- Test: `backend/src/test/java/com/alethicode/service/aitutor/assessment/CodeQualityAssessmentServiceTest.java`
- Test: `backend/src/test/java/com/alethicode/integration/SubmissionJudgeThrottleIntegrationTest.java`

**Step 1:** 写失败测试，锁定 Python3 AC 评估与非 AC 部分分行为。

**Step 2:** 运行测试确认失败。

**Step 3:** 最小实现评估服务与 submission 回写。

**Step 4:** 运行测试确认通过。

### Task 6: 前端脚手架卡片与提交详情展示

**Files:**
- Create: `frontend/src/pages/oj/views/problem/cards/WorkedExampleCard.vue`
- Create: `frontend/src/pages/oj/views/problem/cards/FadedExampleCard.vue`
- Create: `frontend/src/pages/oj/views/problem/cards/MinimalHintCard.vue`
- Modify: `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`
- Modify: `frontend/src/pages/oj/views/problem/workflowStateMachine.js`
- Modify: `frontend/src/pages/oj/views/problem/agentContracts.js`
- Modify: `frontend/src/pages/oj/views/problem/ParsonsPanel.vue`
- Modify: `frontend/src/pages/oj/views/submission/SubmissionDetails.vue`
- Test: `frontend/tests/unit/workflow-private-ai-contract.spec.js`
- Test: `frontend/tests/unit/ai-terminology-consistency.spec.js`
- Test: `frontend/tests/unit/submission-details-*.spec.js`

**Step 1:** 写失败测试，锁定三种脚手架卡片与代码评估/部分分展示。

**Step 2:** 运行测试确认失败。

**Step 3:** 最小实现前端分支与页面展示。

**Step 4:** 运行测试确认通过。

### Task 7: 数据库迁移、日志与收尾

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__adaptive_scaffolding_and_code_assessment.sql`
- Modify: `CHANGELOG.md`

**Step 1:** 增加 `ai_tutor_trace.scaffold_level` 等本轮所需迁移。

**Step 2:** 运行相关后端测试与启动验证。

**Step 3:** 用中文补全 `CHANGELOG.md`。

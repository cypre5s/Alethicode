# Alethicode 全量 Code Review 报告

> 审查日期：2026-04-18  
> 项目规模：后端 381 Java 文件 / 62,529 行，前端 172 Vue/JS 文件 / 54,951 行，测试 100 文件

---

## Critical Issues

### [CR-C01] ✅ `open-in-view` 开发环境未关闭
- **文件**: `backend/src/main/resources/application.yml`
- **修复**: 在 `application.yml` 加 `open-in-view: false`
- **状态**: ✅ 已修复

### [CR-C02] ✅ RateLimitFilter Redis increment + expire 非原子
- **文件**: `backend/src/main/java/com/alethicode/middleware/RateLimitFilter.java`
- **修复**: 使用 Lua 脚本 `INCR + EXPIRE` 保证原子性
- **状态**: ✅ 已修复

### [CR-C03] ✅ X-Forwarded-For 可伪造绕过限流
- **文件**: `backend/src/main/java/com/alethicode/middleware/RateLimitFilter.java`
- **修复**: 改为取 XFF 最右侧 IP（反向代理追加的真实 IP）
- **状态**: ✅ 已修复

---

## High Priority Issues

### [CR-H01] ⏳ SubmissionServiceImpl 过于庞大（2,053 行）
- **已完成**: 类级 `@Transactional` 移除，改为 `createSubmission`/`debugSubmission`/`rejudgeSubmission` 三个写方法精确标注
- **待做**: 完整拆分（2053 行 → 4-5 个 domain service）需专项 sprint，涉及 ~10 个 record 类型和 ~30 个 helper 方法的迁移
- **状态**: ⏳ 部分修复（@Transactional 精确化 ✅ / 完整拆分待专项）

### [CR-H02] 🔲 Problem.vue 过大（3,464 行）
- **说明**: 已拆出 CodeEditorPanel、UnifiedAgentPanel 等子组件，剩余的客观题面板、AST 对话框等拆分需专项前端重构
- **状态**: 🔲 需专项 sprint

### [CR-H03] ✅ pickAvailableJudgeServer 串行 ping 风暴
- **文件**: `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`
- **修复**: 改为 CompletableFuture 并行 ping 所有 candidate，全局超时 2s，按 task_number 优先级返回
- **状态**: ✅ 已修复

### [CR-H04] ✅ randomString 每次 new SecureRandom
- **修复**: 提取为静态 `SECURE_RANDOM` 字段
- **状态**: ✅ 已修复

### [CR-H05] 🔲 CSP 包含 unsafe-inline / unsafe-eval
- **说明**: Vue 2 运行时 + KaTeX 数学公式渲染依赖内联脚本/样式，无法在当前技术栈下移除
- **状态**: 🔲 无法修复（技术栈限制）

### [CR-H06] ✅ 测试类 @MockBean 高耦合
- **修复**: 创建 `AbstractControllerContractTest` 基类，16 个测试文件统一继承，消除 ~240 行重复 MockBean 声明
- **状态**: ✅ 已修复

---

## Medium Priority Issues

### [CR-M01] ✅ AI Tutor Controller Map 请求体（部分）
- **修复**: 为 3 个有手动验证的核心 API 创建 typed DTO：`IdeateAnalyzeRequest`（@NotNull problemId + @NotBlank thoughtText）、`IdeateInsertedRequest`、`StrategyFeedbackRequest`
- **待做**: 剩余 15 个 Map 请求体需同步改造 Domain Service 接口签名
- **状态**: ⏳ 部分修复

### [CR-M02] 🔲 SQL 查询重复
- **说明**: 合并 CR-H01 完整拆分时处理
- **状态**: 🔲 待 CR-H01

### [CR-M03] ✅ 包名 judgeMonitor
- **修复**: 空目录，已直接删除
- **状态**: ✅ 已修复

### [CR-M04] ✅ localStorage.clear() 过度清除
- **修复**: 改为 `storage.remove(STORAGE_KEY.AUTHED)`
- **状态**: ✅ 已修复

### [CR-M05] ✅ 类级 @Transactional
- **修复**: 移除类级注解，仅在 3 个写方法上标注 `@Transactional(rollbackFor = Exception.class)`
- **状态**: ✅ 已修复（合并 CR-H01 处理）

### [CR-M06] ✅ Dockerfile pip 无版本锁定
- **修复**: 锁定 `pypdf==6.9.2 python-pptx==1.0.2 python-docx==1.2.0`
- **状态**: ✅ 已修复

---

## 正面评价

- ✅ CSRF 防护完善
- ✅ WebSocket Origin 限制合理
- ✅ XSS 防护：DOMPurify 严格白名单
- ✅ 路径穿越防护：normalize() + startsWith()
- ✅ 全局异常处理完善，敏感信息清洗
- ✅ Java 21 虚拟线程已启用
- ✅ 生产环境资源限制合理

---

## 修复总结

| 编号 | 等级 | 描述 | 状态 |
|------|------|------|------|
| CR-C01 | Critical | open-in-view | ✅ |
| CR-C02 | Critical | Redis 限流原子性 | ✅ |
| CR-C03 | Critical | XFF IP 伪造 | ✅ |
| CR-H01 | High | God Class 拆分 | ⏳ @Transactional 精确化已完成 |
| CR-H02 | High | Problem.vue 3464 行 | 🔲 需专项 sprint |
| CR-H03 | High | 串行 ping 风暴 | ✅ |
| CR-H04 | High | SecureRandom 每次 new | ✅ |
| CR-H05 | High | CSP unsafe-inline | 🔲 技术栈限制 |
| CR-H06 | High | 测试 MockBean 耦合 | ✅ |
| CR-M01 | Medium | Map 请求体 | ⏳ 3 个核心 API 已改 |
| CR-M02 | Medium | SQL 重复 | 🔲 待 CR-H01 |
| CR-M03 | Medium | 包名违规 | ✅ |
| CR-M04 | Medium | localStorage.clear | ✅ |
| CR-M05 | Medium | 类级 @Transactional | ✅ |
| CR-M06 | Medium | pip 无版本锁定 | ✅ |

**已修复 11 项 / 部分修复 2 项 / 待专项 2 项 / 无法修复 1 项**

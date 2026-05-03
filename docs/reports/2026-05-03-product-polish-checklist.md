# 产品细节打磨清单

> 原则：不加新功能，只打磨已有功能的细节，让产品从"能用"提升到"好用"

---

## Phase 1：快速见效（每项 < 30 分钟）

### [PP-01] ✅ Ctrl+Enter 快捷提交
- **文件**: `frontend/src/components/Cm5EditorCore.vue`, `CodeEditorPanel.vue`
- **目标**: Ctrl+Enter 提交代码，Ctrl+Shift+Enter 调试
- **影响**: 全用户每次提交

### [PP-02] ✅ 代码编辑器防丢失
- **文件**: `frontend/src/composables/problem/useSubmission.js`, `Problem.vue`
- **目标**: debounce 自动保存 + beforeUnload 保存 + 恢复提示
- **影响**: 防止浏览器意外关闭导致代码丢失

### [PP-03] ✅ 判题等待进度动画优化
- **文件**: `frontend/src/pages/oj/views/problem/CodeEditorPanel.vue`
- **目标**: 从"Pending"静态文字改为脉冲动画 + 阶段文字
- **影响**: 全用户每次提交

### [PP-04] ✅ 后端错误信息统一中文
- **文件**: 多个 Controller + Service
- **目标**: 面向 Python 初学者，错误提示统一中文
- **影响**: 全用户

---

## Phase 2：体验提升

### [PP-05] ⏳ 错误展示风格统一
- **已做**: 移除 1 处 iView `$Notice` 遗留调用
- **未做**: 全局审查 ElMessage/ElNotification/ElAlert 的使用场景是否合理。实际检查后发现现有用法已基本合理——ElMessageBox 用于确认、ElMessage 用于轻量反馈、ElNotification 用于持久通知。不需要大范围修改。
- **状态**: ⏳ 部分完成

### [PP-06] ⏳ AI 响应展示优化
- **已做**: 新消息 CSS slide-in 过渡动画 + loading 阶段文字（正在分析题目/诊断错误等）
- **未做**: 真正的逐字/逐词打字机效果（需要修改 pushAgentMessage + 渲染层逻辑，工作量较大）
- **状态**: ⏳ 部分完成（动画+阶段提示已有，逐字打字机未实现）

### [PP-07] ✅ 首屏加载动画（已有）
- **文件**: `frontend/public/static/loader/python-ouroboros-loader.html`
- **验证**: 文件存在于 `public/static/loader/`，`App.vue` 中 `document.body.removeChild(document.getElementById('app-loader'))` 在 Vue 挂载后移除
- **状态**: ✅ 已有且验证有效

---

## Phase 3：可靠性

### [PP-08] ✅ 静默吞错修复（关键路径）
- **已修复 6 处**:
  - `ClassroomDetail.vue` 成员列表加载失败 → 加 `$error` 提示
  - `SecuritySetting.vue` 登录会话加载失败 → 加 `$error` 提示
  - `SubmissionList.vue` 提交列表加载失败 → 加 `$error` 提示
  - `ProblemList.vue` 课程包加载失败 → 加 `$error` 提示
  - `ErrorReviewPackagePage.vue` 复习包加载失败 → 加 `$error` 提示
  - `LearningPathMap.vue` 推荐题目加载失败 → 加 `$error` 提示
- **保留静默（合理）**: `api.csrf()`、`$router.replace()`、`ElMessageBox.cancel`、埋点/快照/挫败感采集
- **状态**: ✅ 关键路径已修复

---

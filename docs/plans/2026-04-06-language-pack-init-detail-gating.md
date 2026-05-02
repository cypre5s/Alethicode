# Language Pack Init Detail Gating Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让语言包初始化页只在点击语言包名称时才展开详情，并让当前运行阶段显示转圈反馈且发布阶段保持静态。

**Architecture:** 以前端单文件组件 `LanguagePackInit.vue` 为唯一实现核心，通过移除自动选中逻辑、限制详情入口、补充阶段节点自定义标记与样式来完成交互收口。测试采用现有前端契约测试模式，直接校验源码中关键结构，保证这次交互约束不会被回退。

**Tech Stack:** Vue 3 SFC、Element Plus、Jest、LESS

---

### Task 1: 写入失败契约测试

**Files:**
- Create: `frontend/tests/unit/admin-language-pack-init-contract.spec.js`
- Test: `frontend/tests/unit/admin-language-pack-init-contract.spec.js`

**Step 1: Write the failing test**

新增源码契约测试，断言：
- 不再存在 `this.selectedTask = this.filteredTasks[0]`
- 表格不再使用 `@row-click="selectTask"`
- 名称区存在显式点击入口
- 当前阶段转圈逻辑排除 `published`

**Step 2: Run test to verify it fails**

Run: `npx jest tests/unit/admin-language-pack-init-contract.spec.js --runInBand`

Expected: FAIL，因为源码仍保留自动展开和整行点击行为。

**Step 3: Write minimal implementation**

在 `LanguagePackInit.vue` 中删除自动展开逻辑，改为名称按钮触发 `selectTask`，并为阶段节点增加运行标记。

**Step 4: Run test to verify it passes**

Run: `npx jest tests/unit/admin-language-pack-init-contract.spec.js --runInBand`

Expected: PASS

### Task 2: 实现详情显式展开与进度动效

**Files:**
- Modify: `frontend/src/pages/admin/views/general/LanguagePackInit.vue`

**Step 1: Write the failing test**

复用 Task 1 失败测试作为行为保护。

**Step 2: Run test to verify it fails**

Run: `npx jest tests/unit/admin-language-pack-init-contract.spec.js --runInBand`

Expected: FAIL

**Step 3: Write minimal implementation**

- 表格移除整行点击事件
- 语言包名称改为按钮
- 切换语言包筛选时不再默认展开首条
- 自定义阶段节点内容，当前阶段显示旋转指示器，`published` 不旋转

**Step 4: Run test to verify it passes**

Run: `npx jest tests/unit/admin-language-pack-init-contract.spec.js --runInBand`

Expected: PASS

### Task 3: 验证并记录变更

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Run focused verification**

Run:
- `npx jest tests/unit/admin-language-pack-init-contract.spec.js --runInBand`
- `npx eslint src/pages/admin/views/general/LanguagePackInit.vue`

Expected: tests pass，lint 通过。

**Step 2: Update changelog**

将本次交互变更用中文追加到 `CHANGELOG.md`。

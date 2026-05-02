# Admin AI Hidden Entries Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 隐藏 Admin 端的 `McMining 审核` 和 `预检帮助率` 入口，并让对应路由不再可访问。

**Architecture:** 以前端导航与路由表为唯一修改面，删除侧边栏入口与 Admin 路由注册项；同时补充契约测试，锁定“菜单不可见”和“路由不存在”两项行为。测试环境登记的可访问 Admin 页面列表同步删除这两个页面，避免测试基线与运行时配置分裂。

**Tech Stack:** Vue Router、Vue SFC、Jest

---

### Task 1: 写失败测试

**Files:**
- Create: `frontend/tests/unit/admin-hidden-ai-entry-contract.spec.js`
- Test: `frontend/tests/unit/admin-hidden-ai-entry-contract.spec.js`

**Step 1: Write the failing test**

新增源码契约测试，断言：
- `SideMenu.vue` 不再包含 `/mcmining-review` 和 `/preflight-stats`
- `admin/router.js` 不再声明这两个路由
- `replacementConfig.js` 不再登记这两个 Admin 页面

**Step 2: Run test to verify it fails**

Run: `npx jest tests/unit/admin-hidden-ai-entry-contract.spec.js --runInBand`

Expected: FAIL，因为源码当前仍暴露这两个入口。

**Step 3: Write minimal implementation**

移除菜单项、路由项与测试路由登记。

**Step 4: Run test to verify it passes**

Run: `npx jest tests/unit/admin-hidden-ai-entry-contract.spec.js --runInBand`

Expected: PASS

### Task 2: 更新变更记录并验证

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Run focused verification**

Run:
- `npx jest tests/unit/admin-hidden-ai-entry-contract.spec.js --runInBand`
- `npx eslint src/pages/admin/components/SideMenu.vue src/pages/admin/router.js`

Expected: tests pass，lint 通过。

**Step 2: Update changelog**

用中文记录本次 Admin 入口收缩变更。

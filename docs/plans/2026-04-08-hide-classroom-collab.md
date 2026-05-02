# Hide Classroom Collaboration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Hide classroom collaboration from normal frontend usage paths by removing the classroom detail tab, removing the dedicated route, and stopping unnecessary collaboration session loading.

**Architecture:** Keep the backend collaboration implementation intact and only contract the OJ frontend surface area. Enforce the change with source-based contract tests so future edits cannot accidentally re-expose the feature.

**Tech Stack:** Vue 2 single-file components, OJ frontend router, Jest source contract tests

---

### Task 1: Add failing contract test for hidden collaboration entry

**Files:**
- Create: `frontend/tests/unit/classroom-collaboration-hidden-contract.spec.js`
- Test: `frontend/tests/unit/classroom-collaboration-hidden-contract.spec.js`

**Step 1: Write the failing test**

Add assertions that:
- `frontend/src/pages/oj/views/classroom/ClassroomDetail.vue` no longer contains the `协作编程` tab label.
- `frontend/src/pages/oj/router/routes.js` no longer contains `/classroom/collab`.
- `frontend/src/pages/oj/router/routes.js` no longer contains `name: 'collaborative-coding'`.

**Step 2: Run test to verify it fails**

Run: `npm test -- --runInBand tests/unit/classroom-collaboration-hidden-contract.spec.js`

Expected: FAIL because the current source still exposes the tab and route.

### Task 2: Remove collaboration entry from classroom detail and router

**Files:**
- Modify: `frontend/src/pages/oj/views/classroom/ClassroomDetail.vue`
- Modify: `frontend/src/pages/oj/router/routes.js`

**Step 1: Write the minimal implementation**

- Remove the `协作编程` tab pane from classroom detail.
- Remove the `/classroom/collab` route and its `CollaborativeCoding` import.
- Stop `getClassroomDetail()` from calling `loadSessions()`.
- Remove the `activeTab === 'collaboration'` watch branch.

**Step 2: Run test to verify it passes**

Run: `npm test -- --runInBand tests/unit/classroom-collaboration-hidden-contract.spec.js`

Expected: PASS

### Task 3: Record the change

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Update changelog**

Add one Chinese entry under `Unreleased` describing that classroom collaboration entry and route were hidden from the frontend, while the rest of the classroom functionality remains unchanged.

### Task 4: Final verification

**Files:**
- Test: `frontend/tests/unit/classroom-collaboration-hidden-contract.spec.js`

**Step 1: Re-run the targeted test**

Run: `npm test -- --runInBand tests/unit/classroom-collaboration-hidden-contract.spec.js`

Expected: PASS

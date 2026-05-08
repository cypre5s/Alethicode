# Unified Chat KC Notebook Mentions Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let the problem-page Unified Chat `@` menu expose real `@kc:<id>` and `@notebook:<id>` candidates like the courseware QA page.

**Architecture:** Keep the existing reference protocol. The frontend builds KC candidates from the current problem `languagePackId` through `api.getKcGraph(languagePackId)` and notebook candidates from `api.getLearnerNotebook({})`; message submission continues to call `parseReferences(text)` and sends tokens to the existing backend resolver.

**Tech Stack:** Vue 3, Element Plus, existing `useChatComposer`, Jest static contract tests.

---

### Task 1: Contract Test

**Files:**
- Modify: `frontend/tests/unit/unified-chat-context-contract.spec.js`

**Step 1: Write the failing test**

Add assertions that `UnifiedAgentPanel.vue`:
- no longer contains the `phase2-placeholders` provider.
- no longer marks `@kc:<id>` and `@notebook:<id>` as placeholder entries.
- contains provider keys for `knowledge-components` and `learner-notebooks`.
- calls `api.getKcGraph(props.languagePackId)` and `api.getLearnerNotebook({})`.
- builds `@kc:` and `@notebook:` tokens.

**Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- tests/unit/unified-chat-context-contract.spec.js --runInBand`

Expected: FAIL because the current panel still has the Phase 2 placeholder provider and does not call the real data APIs.

### Task 2: Minimal Implementation

**Files:**
- Modify: `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`

**Step 1: Add local mention state**

Add refs and loaded/loading flags for:
- `knowledgeComponentItems`
- `learnerNotebookItems`

**Step 2: Add lazy loaders**

Implement:
- `ensureKnowledgeComponentsLoaded()`
- `buildKnowledgeComponentItems()`
- `ensureLearnerNotebooksLoaded()`
- `buildLearnerNotebookItems()`

The implementation mirrors `LanguagePackQaPage.vue`: map valid IDs to `@kc:<id>` and `@notebook:<id>` tokens; use labels, descriptions, and hover previews from available fields; return an empty list when no current course pack or no entries exist.

**Step 3: Replace placeholder provider**

Remove the Phase 2 placeholder provider and add:
- group `知识点 · 当前课程包`
- group `学习笔记`

Both providers use `lazyLoad: true`.

**Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- tests/unit/unified-chat-context-contract.spec.js --runInBand`

Expected: PASS.

### Task 3: Changelog And Verification

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Record the change**

Add a Chinese changelog entry under the current unreleased area or at the top if the file is chronological.

**Step 2: Run focused checks**

Run:
- `cd frontend && npm test -- tests/unit/unified-chat-context-contract.spec.js tests/unit/chat-composer.spec.js --runInBand`
- `cd frontend && npm run lint -- src/pages/oj/views/problem/UnifiedAgentPanel.vue tests/unit/unified-chat-context-contract.spec.js`

Expected: commands exit 0.

### Task 4: Review

Use `code-reviewer` on the diff, focused on:
- no cross-user notebook exposure.
- no API calls when `languagePackId` is absent.
- no stale placeholder UI.
- no unrelated edits to existing user-modified files.

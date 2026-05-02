# Problem Generation Prompt Stability Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Tighten OJ problem-generation prompt constraints so repeated runs over the same PPT inputs produce more stable titles, source-task alignment, and IO/sample consistency.

**Architecture:** Keep the existing generation pipeline unchanged and only strengthen the system/user prompts in `ProblemGenerationServiceImpl`. Lock the new behavior with prompt-content integration tests first, then validate the change by replaying the full parse -> extract-kcs -> extract-examples -> generate-problems -> validate-problems -> publish chain on one fixed PPT pair and one different PPT pair.

**Tech Stack:** Java, Spring Boot, JUnit 5, MockMvc integration tests, PostgreSQL, shell automation.

---

### Task 1: Add prompt regression tests

**Files:**
- Modify: `backend/src/test/java/com/alethicode/integration/LanguagePackInitIntegrationTest.java`
- Test: `backend/src/test/java/com/alethicode/integration/LanguagePackInitIntegrationTest.java`

**Step 1: Write the failing test**
Add an integration test that triggers `generate-problems`, captures the LLM prompt, and asserts the prompt requires:
- title to stay close to `source_title`
- no generic duplicate titles across unrelated source units
- source-title/body/evidence to determine the dominant task
- strict `stdin/stdout`
- `samples[0] == test_cases[0]`

**Step 2: Run test to verify it fails**
Run: `cd backend && mvn -q -Dtest=LanguagePackInitIntegrationTest#generateProblemsPromptShouldAnchorTitleAndTaskSelection test`
Expected: FAIL because current prompt does not include the new constraints.

**Step 3: Write minimal implementation**
Update `ProblemGenerationServiceImpl` prompt text only, with the smallest set of deterministic constraints needed to address the observed drift.

**Step 4: Run test to verify it passes**
Run: `cd backend && mvn -q -Dtest=LanguagePackInitIntegrationTest#generateProblemsPromptShouldAnchorTitleAndTaskSelection test`
Expected: PASS.

**Step 5: Commit**
```bash
git add backend/src/test/java/com/alethicode/integration/LanguagePackInitIntegrationTest.java \
        backend/src/main/java/com/alethicode/service/languagepack/impl/ProblemGenerationServiceImpl.java \
        CHANGELOG.md docs/plans/2026-04-02-problem-generation-prompt-stability.md
git commit -m "fix: stabilize language pack problem generation prompt"
```

### Task 2: Update prompt and changelog

**Files:**
- Modify: `backend/src/main/java/com/alethicode/service/languagepack/impl/ProblemGenerationServiceImpl.java`
- Modify: `CHANGELOG.md`

**Step 1: Implement prompt constraints**
Strengthen `buildSystemPrompt` and/or `buildUserPrompt` with explicit rules for title anchoring, task fidelity, duplicate-title avoidance, and IO/sample consistency.

**Step 2: Document the behavior change**
Append a Chinese changelog entry describing the prompt stabilization and why it was added.

**Step 3: Run focused verification**
Run: `cd backend && mvn -q -Dtest=LanguagePackInitIntegrationTest#generateProblemsPromptShouldAnchorTitleAndTaskSelection,LanguagePackInitIntegrationTest#generateProblemsShouldIncludeChapterMemoryNeighborUnitsAndCanonicalKcsInPrompt test`
Expected: PASS.

### Task 3: Replay fixed-input experiment

**Files:**
- Reuse shell scripts under `/tmp` or create a new repeat runner if needed
- Read artifacts under `/tmp/two_ppt_repeat3*`

**Step 1: Re-run the same PPT pair three times**
Use the same two PPTs from the baseline experiment and capture per-run snapshots.

**Step 2: Compare results**
Check generated count, published count, titles, validation failures, and source-signature/title mapping drift.

**Step 3: Record whether stability improved**
Summarize which prior drift cases disappeared and which remain.

### Task 4: Replay with a different PPT pair

**Files:**
- No repo changes required unless new scripts are helpful

**Step 1: Pick a different two-chapter PPT pair**
Choose another representative pair that is distinct from the baseline pair.

**Step 2: Run the full chain end-to-end**
Execute the same parse -> publish chain and save snapshots.

**Step 3: Compare repeat behavior**
Confirm the prompt improvement generalizes beyond the original PPT pair.

### Task 5: Final review and commit

**Files:**
- Review modified Java test and production files
- Review `CHANGELOG.md`

**Step 1: Run code review**
Perform a focused correctness-oriented review on the prompt change and its tests.

**Step 2: Run final verification**
Run the exact focused test command plus the real replay commands used for the experiment, then inspect outputs.

**Step 3: Commit the changes**
Create a non-amended commit with the prompt stabilization changes and plan/changelog updates.

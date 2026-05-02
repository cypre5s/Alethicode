# PPT Page Count Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复课件 PPT 页数显示错误，确保新上传课件写入真实页数，并让历史 `total_pages=1` 的 PPT 记录在读取时自动纠正并回填数据库。

**Architecture:** 后端集中修复，不改前端展示逻辑。上传接口继续在 `ClassroomServiceImpl` 内计算页数，读取课件列表和详情时增加 PPT 页数自愈逻辑：当数据库页数异常且文件存在时，按真实文件重新计算并回写，统一保证前端读到正确的 `total_pages`。

**Tech Stack:** Spring Boot 3、JdbcTemplate、MockMvc、JUnit 5、现有本地文件存储。

---

### Task 1: 建立失败测试

**Files:**
- Modify: `backend/src/test/java/com/alethicode/integration/ClassroomModuleIntegrationTest.java`

**Step 1: Write the failing test**

- 新增一个上传 `.pptx` 的集成测试，断言响应中的 `total_pages` 为真实页数。
- 新增一个课件列表/详情读取测试，先插入 `total_pages=1` 的脏数据和真实 `.pptx` 文件，再断言列表与详情接口都会返回修正后的页数。

**Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=ClassroomModuleIntegrationTest test`

Expected: 新增测试失败，表现为上传返回或列表返回的 `total_pages` 仍然是 `1`。

### Task 2: 修复上传统计与读取自愈

**Files:**
- Modify: `backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java`

**Step 1: Write minimal implementation**

- 复查并收紧 `countFilePages`/`countPptxSlides`，确保上传时 `.pptx` 直接按真实 slide 数写库。
- 提取“读取并纠正课件页数”的私有方法，供 `lessonList` 与 `lessonRetrieve` 复用。
- 自愈逻辑只针对 `lesson_type = ppt` 且 `total_pages <= 1` 的记录执行，修正后立即 `update classroom_lesson set total_pages = ?`。

**Step 2: Run test to verify it passes**

Run: `./mvnw -q -Dtest=ClassroomModuleIntegrationTest test`

Expected: 新增 PPT 页数测试通过，原有课堂模块测试继续通过。

### Task 3: 记录与复核

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Update changelog**

- 使用中文追加本次修复记录，说明上传统计与读取自愈都已纳入。

**Step 2: Run final verification**

Run: `./mvnw -q -Dtest=ClassroomModuleIntegrationTest,ClassroomM11IntegrationTest test`

Expected: 指定测试全绿。

package com.alethicode.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
class ClassroomM11IntegrationTest extends AbstractJdbcIntegrationTest {

    private static final Path LESSON_ROOT = Paths.get("/home/cypress/Alethicode/deploy/data/classroom_lessons");
    private static final Path TEST_CASE_ROOT = Paths.get("/home/cypress/Alethicode/deploy/data/test_case");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private JudgeServerService judgeServerService;
    @MockBean private SystemAdminService systemAdminService;
    @MockBean private ReleaseNotesService releaseNotesService;
    @MockBean private PlatformConfigService platformConfigService;
    @MockBean private SystemOptionService systemOptionService;
    @MockBean private AdminUploadService adminUploadService;
    @MockBean private AiModelGateway aiModelGateway;

    private final Set<Path> createdLessonFolders = new LinkedHashSet<>();
    private final Set<Path> createdTestCaseFolders = new LinkedHashSet<>();

    @BeforeEach
    void setUp() {
        insertUser("root", "Admin");
        insertUser("student", "Regular User");
    }

    @AfterEach
    void tearDown() {
        for (Path folder : createdLessonFolders) {
            deleteRecursively(folder);
        }
        for (Path folder : createdTestCaseFolders) {
            deleteRecursively(folder);
        }
        createdLessonFolders.clear();
        createdTestCaseFolders.clear();
    }

    @Test
    void m11AiGenerationFlowShouldWork() throws Exception {
        String classroomId = createClassroomAndStudentJoin();
        insertPdfLesson(classroomId, "lesson-m11", "lesson.pdf",
                "Page 1 Python variables. Variables store input values and print results.",
                "Page 2 Python conditions and loops. if handles branches and for handles iteration.");
        mockAiGeneratedProblemPayloads();

        MvcResult createTask = mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "lesson_id":"lesson-m11",
                                  "question_types":["coding","choice","fill_blank"],
                                  "counts":{"coding":1,"choice":1,"fill_blank":1},
                                  "page_start":1,
                                  "page_end":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.task_id").isNotEmpty())
                .andReturn();
        String taskId = json(createTask).at("/data/task_id").asText();
        assertThat(taskId).isNotBlank();

        mockMvc.perform(get("/api/classroom/" + classroomId + "/ai/generated-problems/task-status/" + taskId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.generated_count").value(3));

        Integer chunkCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_courseware_chunk where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ?",
                Integer.class,
                classroomId,
                "lesson-m11"
        );
        assertThat(chunkCount).isEqualTo(2);
        String firstChunkContent = jdbcTemplate.queryForObject(
                "select content from ai_courseware_chunk where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ? order by (metadata->>'page_no')::int asc limit 1",
                String.class,
                classroomId,
                "lesson-m11"
        );
        String firstChunkMetadata = jdbcTemplate.queryForObject(
                "select metadata::text from ai_courseware_chunk where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ? order by (metadata->>'page_no')::int asc limit 1",
                String.class,
                classroomId,
                "lesson-m11"
        );
        JsonNode firstChunkMetadataJson = objectMapper.readTree(firstChunkMetadata);
        assertThat(firstChunkContent).contains("Python variables");
        assertThat(firstChunkMetadataJson.path("page_no").asInt()).isEqualTo(1);
        assertThat(firstChunkMetadataJson.path("lesson_type").asText()).isEqualTo("pdf");
        assertThat(firstChunkMetadataJson.path("file_hash").asText()).isNotBlank();

        MvcResult list = mockMvc.perform(get("/api/classroom/" + classroomId + "/ai/generated-problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(3))
                .andReturn();

        String codingId = null;
        String choiceId = null;
        String fillBlankId = null;
        String choiceDescription = null;
        for (JsonNode node : json(list).at("/data/results")) {
            String qt = node.at("/question_type").asText();
            if ("coding".equals(qt)) {
                codingId = node.at("/id").asText();
            } else if ("choice".equals(qt)) {
                choiceId = node.at("/id").asText();
                choiceDescription = node.at("/description").asText();
            } else if ("fill_blank".equals(qt)) {
                fillBlankId = node.at("/id").asText();
            }
        }
        assertThat(codingId).isNotBlank();
        assertThat(choiceId).isNotBlank();
        assertThat(fillBlankId).isNotBlank();
        assertThat(choiceDescription).isEqualTo("根据课件内容，下面哪个关键字用于根据条件决定是否执行某段代码？");

        MvcResult codingDetail = mockMvc.perform(get("/api/classroom/" + classroomId + "/ai/generated-problems/" + codingId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.samples[0].input").value("3"))
                .andExpect(jsonPath("$.data.test_cases[0].input").value("5"))
                .andExpect(jsonPath("$.data.validation_log").exists())
                .andReturn();
        assertThat(json(codingDetail).at("/data/validation_log").asText()).contains("page_start");

        mockMvc.perform(patch("/api/classroom/" + classroomId + "/ai/generated-problems/" + codingId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "generated_problem_json":{"title":"Edited Coding","difficulty":"Medium","description":"desc"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.title").value("Edited Coding"));

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems/" + codingId + "/validate")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.result.status").value("passed"))
                .andExpect(jsonPath("$.data.result.test_cases_count").value(2));

        MvcResult publishResult = mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems/" + codingId + "/publish")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.problem_id").isNumber())
                .andReturn();
        Long publishedProblemId = json(publishResult).at("/data/problem_id").asLong();
        String publishedTestCaseId = jdbcTemplate.queryForObject("select test_case_id from problem where id = ?", String.class, publishedProblemId);
        assertThat(publishedTestCaseId).isNotBlank();
        createdTestCaseFolders.add(TEST_CASE_ROOT.resolve(publishedTestCaseId));
        assertThat(Files.isRegularFile(TEST_CASE_ROOT.resolve(publishedTestCaseId).resolve("1.in"))).isTrue();
        assertThat(Files.isRegularFile(TEST_CASE_ROOT.resolve(publishedTestCaseId).resolve("info"))).isTrue();

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems/" + codingId + "/promote")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems/" + choiceId + "/review-pass")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"notes\":\"manual pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.status").value("passed"));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/ai/generated-problems/export-reviewed-json")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.exported_count").value(1));

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems/" + choiceId + "/review-reject")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"notes\":\"reject\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.status").value("failed"));

        mockMvc.perform(delete("/api/classroom/" + classroomId + "/ai/generated-problems/" + choiceId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("success"));
    }

    @Test
    void aiGenerationShouldFailForDocLesson() throws Exception {
        String classroomId = createClassroomAndStudentJoin();
        insertDocLesson(classroomId, "lesson-doc");

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "lesson_id":"lesson-doc",
                                  "question_types":["choice"],
                                  "counts":{"choice":1},
                                  "page_start":1,
                                  "page_end":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("不支持该课件类型进行基于页码的生成"));

        verify(aiModelGateway, never()).callForJson(anyString(), anyString());
    }

    @Test
    void aiGenerationShouldFailWhenSelectedPagesHaveNoText() throws Exception {
        String classroomId = createClassroomAndStudentJoin();
        insertPdfLesson(classroomId, "lesson-blank", "blank.pdf", "", "");

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "lesson_id":"lesson-blank",
                                  "question_types":["coding"],
                                  "counts":{"coding":1},
                                  "page_start":1,
                                  "page_end":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("所选页码范围未提取到可用课件内容"));

        Integer generatedCount = jdbcTemplate.queryForObject("select count(*) from ai_generated_problem where classroom_id = ?", Integer.class, classroomId);
        assertThat(generatedCount).isEqualTo(0);
    }

    @Test
    void aiGenerationShouldReindexLessonWhenFileHashChanges() throws Exception {
        String classroomId = createClassroomAndStudentJoin();
        insertPdfLesson(classroomId, "lesson-reindex", "reindex.pdf",
                "Page 1 original content about variables.",
                "Page 2 original content about if.");
        mockSingleChoicePayload();

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "lesson_id":"lesson-reindex",
                                  "question_types":["choice"],
                                  "counts":{"choice":1},
                                  "page_start":1,
                                  "page_end":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        String initialHash = jdbcTemplate.queryForObject(
                "select metadata->>'file_hash' from ai_courseware_chunk where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ? order by (metadata->>'page_no')::int asc limit 1",
                String.class,
                classroomId,
                "lesson-reindex"
        );
        Path lessonPath = LESSON_ROOT.resolve(classroomId).resolve("reindex.pdf");
        writePdf(lessonPath,
                "Page 1 updated content about while.",
                "Page 2 updated content about break.");

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "lesson_id":"lesson-reindex",
                                  "question_types":["choice"],
                                  "counts":{"choice":1},
                                  "page_start":1,
                                  "page_end":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        String updatedHash = jdbcTemplate.queryForObject(
                "select metadata->>'file_hash' from ai_courseware_chunk where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ? order by (metadata->>'page_no')::int asc limit 1",
                String.class,
                classroomId,
                "lesson-reindex"
        );
        String updatedContent = jdbcTemplate.queryForObject(
                "select content from ai_courseware_chunk where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ? order by (metadata->>'page_no')::int asc limit 1",
                String.class,
                classroomId,
                "lesson-reindex"
        );
        assertThat(updatedHash).isNotEqualTo(initialHash);
        assertThat(updatedContent).contains("updated content");
    }

    @Test
    void aiGenerationShouldSupportPptLessonIndexing() throws Exception {
        String classroomId = createClassroomAndStudentJoin();
        insertPptLesson(classroomId, "lesson-ppt", "lesson.pptx",
                "Slide 1 Python input output",
                "Slide 2 Python conditional statements");
        mockSingleChoicePayload();

        mockMvc.perform(post("/api/classroom/" + classroomId + "/ai/generated-problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "lesson_id":"lesson-ppt",
                                  "question_types":["choice"],
                                  "counts":{"choice":1},
                                  "page_start":1,
                                  "page_end":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        String firstChunkContent = jdbcTemplate.queryForObject(
                "select content from ai_courseware_chunk where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ? order by (metadata->>'page_no')::int asc limit 1",
                String.class,
                classroomId,
                "lesson-ppt"
        );
        assertThat(firstChunkContent).contains("Python input output");
    }

    private String createClassroomAndStudentJoin() throws Exception {
        MvcResult createClassroom = mockMvc.perform(post("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"M11班\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();
        String classroomId = json(createClassroom).at("/data/id").asText();
        assertThat(classroomId).isNotBlank();

        MvcResult invitation = mockMvc.perform(post("/api/classroom/invitation/generate/" + classroomId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"max_uses\":3,\"expire_hours\":24,\"default_role\":\"student\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = json(invitation).at("/data/code").asText();

        mockMvc.perform(post("/api/classroom/invitation/join")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
        return classroomId;
    }

    private void insertPdfLesson(String classroomId, String lessonId, String filename, String... pageTexts) throws IOException {
        Path folder = LESSON_ROOT.resolve(classroomId);
        Files.createDirectories(folder);
        createdLessonFolders.add(folder);
        Path file = folder.resolve(filename);
        writePdf(file, pageTexts);
        insertLessonRecord(classroomId, lessonId, "pdf", classroomId + "/" + filename, pageTexts.length, Files.size(file));
    }

    private void insertPptLesson(String classroomId, String lessonId, String filename, String... slideTexts) throws IOException {
        Path folder = LESSON_ROOT.resolve(classroomId);
        Files.createDirectories(folder);
        createdLessonFolders.add(folder);
        Path file = folder.resolve(filename);
        writePptx(file, slideTexts);
        insertLessonRecord(classroomId, lessonId, "ppt", classroomId + "/" + filename, slideTexts.length, Files.size(file));
    }

    private void insertDocLesson(String classroomId, String lessonId) throws IOException {
        Path folder = LESSON_ROOT.resolve(classroomId);
        Files.createDirectories(folder);
        createdLessonFolders.add(folder);
        Path file = folder.resolve("lesson.docx");
        Files.writeString(file, "doc content");
        insertLessonRecord(classroomId, lessonId, "doc", classroomId + "/lesson.docx", 1, Files.size(file));
    }

    private void insertLessonRecord(String classroomId, String lessonId, String lessonType, String filePath, int totalPages, long fileSize) {
        Long rootId = jdbcTemplate.queryForObject("select id from \"user\" where username='root'", Long.class);
        jdbcTemplate.update(
                """
                insert into classroom_lesson(id, classroom_id, title, description, lesson_type, file_path, file_size,
                                             total_pages, table_of_contents, display_order, created_by_id, create_time, update_time)
                values (?, ?, 'Lesson', 'M11 lesson', ?, ?, ?, ?, cast('[]' as jsonb), 0, ?, now(), now())
                """,
                lessonId,
                classroomId,
                lessonType,
                filePath,
                fileSize,
                totalPages,
                rootId
        );
    }

    private void mockAiGeneratedProblemPayloads() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1, String.class);
            if (prompt.contains("\"question_type\":\"coding\"")) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("title", "读取一个整数并输出它的两倍");
                payload.put("description", "根据课件中关于变量与输入输出的讲解，编写程序读取一个整数并输出它的两倍。");
                payload.put("input_description", "输入一个整数 n。");
                payload.put("output_description", "输出 2 * n 的结果。");
                payload.put("samples", java.util.List.of(Map.of("input", "3", "output", "6")));
                payload.put("test_cases", java.util.List.of(
                        Map.of("input", "5", "output", "10"),
                        Map.of("input", "-2", "output", "-4")
                ));
                payload.put("reference_solution_code", "n = int(input())\nprint(n * 2)\n");
                payload.put("difficulty", "Easy");
                payload.put("hint", "先读取整数，再输出它的两倍。");
                payload.put("explanation", "考查输入输出和变量使用。");
                payload.put("extracted_concepts", java.util.List.of("变量", "输入输出"));
                return payload;
            }
            if (prompt.contains("\"question_type\":\"fill_blank\"")) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("title", "补全条件分支关键字");
                payload.put("description", "根据课件内容，填写 Python 中用于条件判断的关键字以及条件成立后的冒号写法。");
                payload.put("blanks", java.util.List.of("if", ":"));
                payload.put("answer", "if");
                payload.put("difficulty", "Easy");
                payload.put("explanation", "if 用于条件判断。");
                payload.put("extracted_concepts", java.util.List.of("条件分支"));
                return payload;
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("title", "判断条件语句关键字");
            payload.put("description", "根据课件内容，下面哪个关键字用于根据条件决定是否执行某段代码？");
            payload.put("options", java.util.List.of(
                    Map.of("label", "A", "text", "if"),
                    Map.of("label", "B", "text", "for"),
                    Map.of("label", "C", "text", "print"),
                    Map.of("label", "D", "text", "input")
            ));
            payload.put("answer", "A");
            payload.put("difficulty", "Easy");
            payload.put("explanation", "if 用于条件判断。");
            payload.put("extracted_concepts", java.util.List.of("条件分支"));
            return payload;
        });
    }

    private void mockSingleChoicePayload() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "title", "单选题",
                "description", "根据课件内容选择正确答案。",
                "options", java.util.List.of(
                        Map.of("label", "A", "text", "正确"),
                        Map.of("label", "B", "text", "错误")
                ),
                "answer", "A",
                "difficulty", "Easy",
                "explanation", "依据课件内容作答。",
                "extracted_concepts", java.util.List.of("课件知识点")
        ));
    }

    private void writePdf(Path file, String... pageTexts) throws IOException {
        runPython("""
import sys
from pathlib import Path
from pypdf import PdfWriter
from pypdf.generic import NameObject, DictionaryObject, DecodedStreamObject

target = Path(sys.argv[1])
pages = sys.argv[2:]
writer = PdfWriter()
font_ref = writer._add_object(DictionaryObject({
    NameObject("/Type"): NameObject("/Font"),
    NameObject("/Subtype"): NameObject("/Type1"),
    NameObject("/BaseFont"): NameObject("/Helvetica"),
}))
for raw_text in pages:
    page = writer.add_blank_page(width=595, height=842)
    text = (raw_text or "").replace("\\\\", "\\\\\\\\").replace("(", "\\\\(").replace(")", "\\\\)")
    stream = DecodedStreamObject()
    stream.set_data(f"BT /F1 12 Tf 50 780 Td ({text}) Tj ET".encode("latin-1", errors="ignore"))
    stream_ref = writer._add_object(stream)
    page[NameObject("/Resources")] = DictionaryObject({
        NameObject("/Font"): DictionaryObject({NameObject("/F1"): font_ref})
    })
    page[NameObject("/Contents")] = stream_ref
with target.open("wb") as handle:
    writer.write(handle)
""", file, pageTexts);
    }

    private void writePptx(Path file, String... slideTexts) throws IOException {
        runPython("""
import sys
from pathlib import Path
from pptx import Presentation

target = Path(sys.argv[1])
slides = sys.argv[2:]
presentation = Presentation()
layout = presentation.slide_layouts[6]
for text in slides:
    slide = presentation.slides.add_slide(layout)
    box = slide.shapes.add_textbox(left=914400, top=914400, width=7315200, height=3657600)
    box.text_frame.text = text or ""
if len(presentation.slides) > len(slides):
    presentation.slides._sldIdLst.remove(presentation.slides._sldIdLst[0])
presentation.save(target)
""", file, slideTexts);
    }

    private void runPython(String script, Path file, String... values) throws IOException {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("python3");
        command.add("-c");
        command.add(script);
        command.add(file.toString());
        for (String value : values) {
            command.add(value == null ? "" : value);
        }
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException exception) {
            throw new IOException("python3 is required for classroom lesson test fixtures", exception);
        }
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("python fixture generation failed: " + output);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("python fixture generation interrupted", exception);
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void insertUser(String username, String adminType) {
        jdbcTemplate.update(
                "insert into \"user\"(username, admin_type, create_time) values (?, ?, now())",
                username,
                adminType
        );
    }
}

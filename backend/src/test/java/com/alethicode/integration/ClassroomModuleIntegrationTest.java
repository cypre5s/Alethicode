package com.alethicode.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "alethicode.stream.judge-dispatch.enabled=false"
})
class ClassroomModuleIntegrationTest extends AbstractJdbcIntegrationTest {

    private static final Path LESSON_ROOT = Paths.get("/home/cypress/Alethicode/deploy/data/classroom_lessons");
    private static final Path LANGUAGE_PACK_ROOT = Paths.get("/tmp/pytutor-language-pack-tests");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final Set<Path> createdLessonFolders = new LinkedHashSet<>();
    private final Set<Path> createdLanguagePackFiles = new LinkedHashSet<>();

    @MockBean private JudgeServerService judgeServerService;
    @MockBean private SystemAdminService systemAdminService;
    @MockBean private ReleaseNotesService releaseNotesService;
    @MockBean private PlatformConfigService platformConfigService;
    @MockBean private SystemOptionService systemOptionService;
    @MockBean private AdminUploadService adminUploadService;
    @MockBean private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        insertUser("root", "Admin");
        insertUser("student", "Regular User");
        jdbcTemplate.update(
                """
                insert into problem(id, _id, title, description, visible, is_public, difficulty,
                                    statistic_info, source, submission_number, accepted_number,
                                    created_by_id, create_time, last_update_time)
                values (1001, 'P1001', 'A+B', 'desc', true, true, 'Low', cast('{}' as jsonb), 'test', 0, 0,
                        (select id from \"user\" where username='root'), now(), now())
                """
        );
    }

    @AfterEach
    void tearDown() {
        for (Path folder : createdLessonFolders) {
            deleteRecursively(folder);
        }
        createdLessonFolders.clear();
        for (Path path : createdLanguagePackFiles) {
            deleteRecursively(path);
        }
        createdLanguagePackFiles.clear();
    }

    @Test
    void classroomCreateShouldFailWhenLanguagePackIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"缺语言包班级",
                                  "course_code":"CS101",
                                  "semester":"2026春",
                                  "description":"missing language pack"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));
    }

    @Test
    void classroomCreateShouldFailWhenLanguagePackResourcesAreIncomplete() throws Exception {
        Long languagePackId = insertPublishedLanguagePack("java-basic", 1, "Java 基础", "Java");

        mockMvc.perform(post("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Java 班",
                                  "course_code":"CS101",
                                  "semester":"2026春",
                                  "description":"incomplete pack",
                                  "language_pack_id":%s
                                }
                                """.formatted(languagePackId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("language pack is incomplete"));

        Integer classroomCount = jdbcTemplate.queryForObject("select count(*) from classroom", Integer.class);
        assertThat(classroomCount).isEqualTo(0);
    }

    @Test
    void classroomCreateShouldBindLanguagePackAndImportLessonsAndProblems() throws Exception {
        Long languagePackId = insertPublishedLanguagePack("python-intro", 1, "Python 入门", "Python3");
        insertLanguagePackKc(languagePackId, "顺序结构", "sequential_flow");
        Path canonicalPdf = writeLanguagePackFile("python-intro-1.pdf", "%PDF-1.4 language pack".getBytes());
        insertLanguagePackDocument(languagePackId, canonicalPdf, 6);
        insertLanguagePackProblemMapping(languagePackId, 1001L);

        MvcResult createClassroom = mockMvc.perform(post("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Python 班",
                                  "course_code":"CS101",
                                  "semester":"2026春",
                                  "description":"language pack import",
                                  "language_pack_id":%s
                                }
                                """.formatted(languagePackId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.language_pack.id").value(languagePackId))
                .andExpect(jsonPath("$.data.language_pack.name").value("Python 入门"))
                .andExpect(jsonPath("$.data.problem_count").value(1))
                .andExpect(jsonPath("$.data.lesson_count").value(1))
                .andReturn();

        String classroomId = json(createClassroom).at("/data/id").asText();
        assertThat(classroomId).isNotBlank();

        Integer packBindingCount = jdbcTemplate.queryForObject(
                "select count(*) from classroom_language_pack where classroom_id = ? and language_pack_id = ?",
                Integer.class,
                classroomId,
                languagePackId
        );
        assertThat(packBindingCount).isEqualTo(1);

        mockMvc.perform(get("/api/classroom/" + classroomId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.language_pack.version").value(1))
                .andExpect(jsonPath("$.data.language_pack.primary_language").value("Python3"));

        mockMvc.perform(get("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].language_pack.id").value(languagePackId));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/lessons")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0].title").value("python-intro-1.pdf"))
                .andExpect(jsonPath("$.data.results[0].total_pages").value(6));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].problem_id").value(1001))
                .andExpect(jsonPath("$.data.results[0].is_visible").value(true))
                .andExpect(jsonPath("$.data.results[0].is_private").value(false));

        MvcResult invitation = mockMvc.perform(post("/api/classroom/invitation/generate/" + classroomId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"max_uses\":5,\"expire_hours\":24,\"default_role\":\"student\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();
        String code = json(invitation).at("/data/code").asText();

        mockMvc.perform(post("/api/classroom/invitation/join")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.classroom.id").value(classroomId));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].problem_id").value(1001));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/lessons")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/language-packs/visible")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data[0].id").value(languagePackId));
    }

    @Test
    void m9ClassroomCoreFlowShouldWork() throws Exception {
        Long languagePackId = createDefaultLanguagePackForClassroom();
        MvcResult createClassroom = mockMvc.perform(post("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Java 班",
                                  "course_code":"CS101",
                                  "semester":"2026春",
                                  "description":"M9 Integration",
                                  "language_pack_id":%s
                                }
                                """.formatted(languagePackId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        String classroomId = json(createClassroom).at("/data/id").asText();
        assertThat(classroomId).isNotBlank();

        MvcResult invitation = mockMvc.perform(post("/api/classroom/invitation/generate/" + classroomId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"max_uses\":5,\"expire_hours\":24,\"default_role\":\"student\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.code").isNotEmpty())
                .andReturn();
        String code = json(invitation).at("/data/code").asText();

        mockMvc.perform(post("/api/classroom/invitation/join")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.classroom.id").value(classroomId));

        MvcResult memberList = mockMvc.perform(get("/api/classroom/" + classroomId + "/members")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();

        String studentMemberId = null;
        for (JsonNode node : json(memberList).at("/data/results")) {
            if ("student".equals(node.at("/user/username").asText())) {
                studentMemberId = node.at("/id").asText();
                break;
            }
        }
        assertThat(studentMemberId).isNotBlank();

        mockMvc.perform(post("/api/classroom/" + classroomId + "/members/" + studentMemberId + "/promote")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.message").exists());

        MockMultipartFile lessonFile = new MockMultipartFile(
                "file",
                "intro.pdf",
                "application/pdf",
                "%PDF-1.4 fake pdf".getBytes()
        );
        MvcResult lessonCreate = mockMvc.perform(multipart("/api/classroom/" + classroomId + "/lessons")
                        .file(lessonFile)
                        .param("title", "第一讲")
                        .param("notes", "课程导论")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        String lessonId = json(lessonCreate).at("/data/id").asText();

        mockMvc.perform(get("/api/classroom/" + classroomId + "/lessons/" + lessonId + "/download")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/lessons/" + lessonId + "/view")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));

        mockMvc.perform(delete("/api/classroom/" + classroomId + "/lessons/" + lessonId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        MvcResult createClassroomProblem = mockMvc.perform(post("/api/classroom/" + classroomId + "/problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"problem_id\":1001,\"is_visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        String classroomProblemId = json(createClassroomProblem).at("/data/id").asText();

        mockMvc.perform(patch("/api/classroom/" + classroomId + "/problems/" + classroomProblemId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"category\":\"intro\",\"is_visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.category").value("intro"));

        MvcResult assignmentCreate = mockMvc.perform(post("/api/classroom/" + classroomId + "/assignments")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"第一次作业",
                                  "sections":[
                                    {
                                      "title":"Section A",
                                      "description":"基础题",
                                      "order":1,
                                      "problems":[
                                        {"problem_id":"%s","score":10,"order":1}
                                      ]
                                    }
                                  ]
                                }
                                """.formatted(classroomProblemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        String assignmentId = json(assignmentCreate).at("/data/id").asText();
        String assignmentProblemId = json(assignmentCreate).at("/data/sections/0/problems/0/id").asText();
        assertThat(assignmentProblemId).isNotBlank();

        Long studentUserId = jdbcTemplate.queryForObject("select id from \"user\" where username='student'", Long.class);
        jdbcTemplate.update(
                "insert into submission(id, problem_id, user_id, username, result, language, code, create_time) values (?, 1001, ?, 'student', 0, 'Python3', 'print(1)', now())",
                "sub-ac-1",
                studentUserId
        );

        mockMvc.perform(post("/api/classroom/" + classroomId + "/assignments/" + assignmentId + "/submit")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "answers":{},
                                  "fill_answers":{}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total_score").value(10.0));

        MvcResult submissions = mockMvc.perform(get("/api/classroom/" + classroomId + "/assignments/" + assignmentId + "/submissions")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].details").isArray())
                .andReturn();

        String detailId = json(submissions).at("/data/results/0/details/0/id").asText();
        assertThat(detailId).isNotBlank();

        mockMvc.perform(put("/api/classroom/" + classroomId + "/assignments/" + assignmentId + "/submissions/" + detailId + "/grade")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"ta_score\":9.0,\"ta_comment\":\"good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total_score").value(9.0));

        mockMvc.perform(post("/api/classroom/" + classroomId + "/problems/import-objective-json")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "questions":[
                                    {
                                      "question_type":"choice",
                                      "title":"二选一",
                                      "description":"A or B",
                                      "options":["A","B"],
                                      "answer":"A"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.imported_count").value(1));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/problems/export-objective-json")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.questions").isArray());

        Integer classroomCount = jdbcTemplate.queryForObject("select count(*) from classroom", Integer.class);
        assertThat(classroomCount).isEqualTo(1);
    }

    @Test
    void classroomSummaryShouldCountActualMemberRows() throws Exception {
        String classroomId = createClassroomAsRoot("成员计数班级");
        Long studentUserId = jdbcTemplate.queryForObject("select id from \"user\" where username='student'", Long.class);
        jdbcTemplate.update(
                """
                insert into classroom_member(id, classroom_id, user_id, role, join_method, join_time, update_time)
                values ('manual-student-member', ?, ?, 'student', 'invited', now(), now())
                """,
                classroomId,
                studentUserId
        );

        mockMvc.perform(get("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].member_count").value(2));

        mockMvc.perform(get("/api/classroom/" + classroomId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.member_count").value(2));
    }

    @Test
    void adminInsightErrorRankingShouldResolveKcBySyncedAiKcId() throws Exception {
        String classroomId = createClassroomAsRoot("映射一致性班级");
        Long languagePackId = jdbcTemplate.queryForObject(
                "select language_pack_id from classroom_language_pack where classroom_id = ?",
                Long.class,
                classroomId
        );
        Long classroomKcId = jdbcTemplate.queryForObject(
                "select id from language_pack_kc where language_pack_id = ? and name_normalized = 'input_output' limit 1",
                Long.class,
                languagePackId
        );
        assertThat(classroomKcId).isNotNull();
        assertThat(classroomKcId).isNotEqualTo(9001L);

        jdbcTemplate.update(
                """
                insert into ai_knowledge_component(
                    id, name, name_en, chapter, description,
                    p_init, p_transit, p_slip, p_guess, language_pack_id, name_normalized
                )
                values (9001, '输入输出-AI', '', 'chapter-1', '',
                        0.3, 0.2, 0.1, 0.2, ?, 'input_output_ai')
                """,
                languagePackId
        );
        int updated = jdbcTemplate.update(
                "update language_pack_kc set synced_ai_kc_id = 9001 where id = ?",
                classroomKcId
        );
        assertThat(updated).isEqualTo(1);
        jdbcTemplate.update(
                "insert into ai_problem_kc_mapping(problem_id, kc_id, weight, language_pack_id) values (1001, 9001, 1.0, ?)",
                languagePackId
        );

        Long rootUserId = jdbcTemplate.queryForObject("select id from \"user\" where username='root'", Long.class);
        jdbcTemplate.update(
                """
                insert into ai_learning_event(user_id, problem_id, event_type, extra_data, created_at)
                values (?, 1001, 'misconception_detected_ast', cast(? as jsonb), now())
                """,
                rootUserId,
                "{\"detector_name\":\"loop_boundary_error\"}"
        );

        mockMvc.perform(get("/api/admin/insight/error-ranking")
                        .param("classroom_id", classroomId)
                        .param("days", "30")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data[0].kc_name").value("输入输出"))
                .andExpect(jsonPath("$.data[0].error_pattern").value("loop_boundary_error"))
                .andExpect(jsonPath("$.data[0].frequency").value(1));
    }

    @Test
    void lessonUploadShouldPersistRealPptxPageCount() throws Exception {
        String classroomId = createClassroomAsRoot("PPT 上传页数");
        MockMultipartFile lessonFile = createPptLessonFile("chapter1.pptx", 3);

        mockMvc.perform(multipart("/api/classroom/" + classroomId + "/lessons")
                        .file(lessonFile)
                        .param("title", "第一章")
                        .param("notes", "PPT upload")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total_pages").value(3));
    }

    @Test
    void lessonListAndRetrieveShouldHealLegacyPptPageCount() throws Exception {
        String classroomId = createClassroomAsRoot("PPT 自愈页数");
        String lessonId = "lesson-ppt-heal";
        Path lessonFile = writeStoredLessonFile(classroomId, "legacy-heal.pptx", 4);
        Long rootId = jdbcTemplate.queryForObject("select id from \"user\" where username='root'", Long.class);

        jdbcTemplate.update(
                """
                insert into classroom_lesson(id, classroom_id, title, description, lesson_type, file_path, file_size,
                                             total_pages, table_of_contents, display_order, created_by_id, create_time, update_time)
                values (?, ?, 'Legacy PPT', 'legacy', 'ppt', ?, ?, 1, cast('[]' as jsonb), 0, ?, now(), now())
                """,
                lessonId,
                classroomId,
                classroomId + "/" + lessonFile.getFileName(),
                Files.size(lessonFile),
                rootId
        );

        mockMvc.perform(get("/api/classroom/" + classroomId + "/lessons")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].total_pages").value(4));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/lessons/" + lessonId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total_pages").value(4));

        Integer persistedPages = jdbcTemplate.queryForObject(
                "select total_pages from classroom_lesson where id = ?",
                Integer.class,
                lessonId
        );
        assertThat(persistedPages).isEqualTo(4);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createClassroomAsRoot(String name) throws Exception {
        MvcResult createClassroom = mockMvc.perform(post("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "course_code":"CS101",
                                  "semester":"2026春",
                                  "description":"PPT pages",
                                  "language_pack_id":%s
                                }
                                """.formatted(name, createDefaultLanguagePackForClassroom())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();
        return json(createClassroom).at("/data/id").asText();
    }

    private Long createDefaultLanguagePackForClassroom() throws IOException {
        Long languagePackId = insertPublishedLanguagePack("default-pack-" + System.nanoTime(), 1, "默认语言包", "Python3");
        insertLanguagePackKc(languagePackId, "输入输出", "input_output");
        Path canonicalPdf = writeLanguagePackFile("default-pack.pdf", "%PDF-1.4 default pack".getBytes());
        insertLanguagePackDocument(languagePackId, canonicalPdf, 1);
        insertLanguagePackProblemMapping(languagePackId, 1001L);
        return languagePackId;
    }

    private Long insertPublishedLanguagePack(String slug, int version, String name, String primaryLanguage) {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                insert into language_pack(slug, version, name, primary_language, status, create_time, update_time)
                values (?, ?, ?, ?, 'published', now(), now())
                returning id
                """,
                Long.class,
                slug,
                version,
                name,
                primaryLanguage
        );
        jdbcTemplate.update(
                """
                insert into language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                values (?, 'published', false, now(), now())
                """,
                languagePackId
        );
        return languagePackId;
    }

    private void insertLanguagePackKc(Long languagePackId, String name, String normalizedName) {
        Long initTaskId = findLanguagePackInitTaskId(languagePackId);
        jdbcTemplate.update(
                """
                insert into language_pack_kc(language_pack_id, init_task_id, chapter_id, name, name_en, description, name_normalized, create_time)
                values (?, ?, null, ?, '', '', ?, now())
                """,
                languagePackId,
                initTaskId,
                name,
                normalizedName
        );
    }

    private void insertLanguagePackDocument(Long languagePackId, Path canonicalPath, int pageCount) throws IOException {
        Long initTaskId = findLanguagePackInitTaskId(languagePackId);
        long fileSize = Files.size(canonicalPath);
        jdbcTemplate.update(
                """
                insert into language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'normalized', now(), now())
                """,
                initTaskId,
                languagePackId,
                canonicalPath.getFileName().toString(),
                canonicalPath.toString(),
                canonicalPath.toString(),
                canonicalPath.toString(),
                "hash-" + canonicalPath.getFileName(),
                fileSize,
                pageCount
        );
    }

    private Long findLanguagePackInitTaskId(Long languagePackId) {
        return jdbcTemplate.queryForObject(
                "select id from language_pack_init_task where language_pack_id = ? order by id asc limit 1",
                Long.class,
                languagePackId
        );
    }

    private void insertLanguagePackProblemMapping(Long languagePackId, Long problemId) {
        jdbcTemplate.update(
                """
                insert into language_pack_problem_mapping(language_pack_id, problem_id, generation_log_id, create_time)
                values (?, ?, null, now())
                """,
                languagePackId,
                problemId
        );
    }

    private Path writeLanguagePackFile(String filename, byte[] content) throws IOException {
        Files.createDirectories(LANGUAGE_PACK_ROOT);
        createdLanguagePackFiles.add(LANGUAGE_PACK_ROOT);
        Path file = LANGUAGE_PACK_ROOT.resolve(filename);
        Files.write(file, content);
        return file;
    }

    private MockMultipartFile createPptLessonFile(String filename, int slideCount) throws IOException {
        return new MockMultipartFile(
                "file",
                filename,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                createFakePptxBytes(slideCount)
        );
    }

    private Path writeStoredLessonFile(String classroomId, String filename, int slideCount) throws IOException {
        Path folder = LESSON_ROOT.resolve(classroomId);
        Files.createDirectories(folder);
        createdLessonFolders.add(folder);
        Path file = folder.resolve(filename);
        Files.write(file, createFakePptxBytes(slideCount));
        return file;
    }

    private byte[] createFakePptxBytes(int slideCount) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>".getBytes());
            zip.closeEntry();
            for (int i = 1; i <= slideCount; i++) {
                zip.putNextEntry(new ZipEntry("ppt/slides/slide" + i + ".xml"));
                zip.write(("<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><p:cSld/></p:sld>").getBytes());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private void deleteRecursively(Path path) {
        if (path == null || Files.notExists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((left, right) -> right.compareTo(left))
                    .forEach(one -> {
                        try {
                            Files.deleteIfExists(one);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private void insertUser(String username, String adminType) {
        jdbcTemplate.update(
                "insert into \"user\"(username, admin_type, create_time) values (?, ?, now())",
                username,
                adminType
        );
    }
}

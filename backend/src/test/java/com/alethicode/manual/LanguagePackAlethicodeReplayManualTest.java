package com.alethicode.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import com.alethicode.service.languagepack.impl.LanguagePackChapterIndexResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5436/alethicode",
        "spring.datasource.username=onlinejudge",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
@EnabledIfEnvironmentVariable(named = "ALETHICODE_MANUAL_INIT", matches = "1")
class LanguagePackAlethicodeReplayManualTest {

    private static final String PPTX_MIME =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String PACK_SLUG = "python-basic";
    private static final String PACK_NAME = "Python -v2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JudgeServerService judgeServerService;

    @MockBean
    private SystemAdminService systemAdminService;

    @MockBean
    private ReleaseNotesService releaseNotesService;

    @MockBean
    private PlatformConfigService platformConfigService;

    @MockBean
    private SystemOptionService systemOptionService;

    @MockBean
    private AdminUploadService adminUploadService;

    @Test
    void deleteHistoricalPythonVersionsAndRerunAsPythonV2() throws Exception {
        Path pptDir = Path.of("/home/cypress/Alethicode/docs/ppt");
        List<Path> pptFiles = listPptFiles(pptDir);
        assertThat(pptFiles).hasSize(7);

        Long defaultPackId = jdbcTemplate.queryForObject(
                "select id from language_pack where slug = ? and version = 1",
                Long.class,
                PACK_SLUG
        );
        assertThat(defaultPackId).isNotNull();

        List<String> reboundClassroomIds = jdbcTemplate.queryForList(
                """
                select classroom_id
                from classroom_language_pack
                where language_pack_id in (
                    select id from language_pack where slug = ? and version >= 2
                )
                order by classroom_id
                """,
                String.class,
                PACK_SLUG
        );

        List<Long> oldTaskIds = jdbcTemplate.queryForList(
                """
                select t.id
                from language_pack_init_task t
                join language_pack lp on lp.id = t.language_pack_id
                where lp.slug = ?
                  and lp.version >= 2
                order by t.id
                """,
                Long.class,
                PACK_SLUG
        );

        List<Map<String, Object>> oldProblems = jdbcTemplate.queryForList(
                """
                select distinct p.id as problem_id, p.test_case_id
                from language_pack_problem_mapping lppm
                join problem p on p.id = lppm.problem_id
                join language_pack lp on lp.id = lppm.language_pack_id
                where lp.slug = ?
                  and lp.version >= 2
                order by p.id
                """,
                PACK_SLUG
        );

        if (!reboundClassroomIds.isEmpty()) {
            jdbcTemplate.update(
                    """
                    update classroom_language_pack
                    set language_pack_id = ?
                    where language_pack_id in (
                        select id from language_pack where slug = ? and version >= 2
                    )
                    """,
                    defaultPackId,
                    PACK_SLUG
            );
        }

        for (Map<String, Object> row : oldProblems) {
            Long problemId = ((Number) row.get("problem_id")).longValue();
            String testCaseId = row.get("test_case_id") == null ? null : String.valueOf(row.get("test_case_id"));
            jdbcTemplate.update("delete from problem where id = ?", problemId);
            if (testCaseId != null && !testCaseId.isBlank()) {
                deleteRecursivelyIfExists(projectRoot().resolve("deploy/data/test_case").resolve(testCaseId));
            }
        }

        jdbcTemplate.update(
                "delete from language_pack where slug = ? and version >= 2",
                PACK_SLUG
        );

        deleteTaskDirectories(oldTaskIds);

        Integer remainingVersions = jdbcTemplate.queryForObject(
                "select count(*) from language_pack where slug = ? and version >= 2",
                Integer.class,
                PACK_SLUG
        );
        int remainingProblems = 0;
        for (Map<String, Object> row : oldProblems) {
            Long problemId = ((Number) row.get("problem_id")).longValue();
            Integer exists = jdbcTemplate.queryForObject(
                    "select count(*) from problem where id = ?",
                    Integer.class,
                    problemId
            );
            remainingProblems += exists == null ? 0 : exists;
        }
        assertThat(remainingVersions).isZero();
        assertThat(remainingProblems).isZero();

        MockMultipartHttpServletRequestBuilder createRequest = multipart("/api/admin/language-packs/init-tasks");
        createRequest.param("name", PACK_NAME);
        createRequest.param("slug", PACK_SLUG);
        createRequest.param("primary_language", "Python3");
        for (Path pptFile : pptFiles) {
            createRequest.file(new MockMultipartFile(
                    "files",
                    pptFile.getFileName().toString(),
                    PPTX_MIME,
                    Files.readAllBytes(pptFile)
            ));
        }

        JsonNode created = invokeMultipart(createRequest);
        Long taskId = created.path("id").asLong();
        Long languagePackId = created.path("language_pack").path("id").asLong();
        assertThat(taskId).isPositive();
        assertThat(languagePackId).isPositive();
        assertThat(created.path("stage").asText()).isEqualTo("normalizing");
        assertThat(created.path("language_pack").path("version").asInt()).isEqualTo(2);
        assertThat(created.path("language_pack").path("name").asText()).isEqualTo(PACK_NAME);

        assertThat(invokeStage(taskId, "parse").path("stage").asText()).isEqualTo("parsing");
        assertThat(invokeStage(taskId, "extract-kcs").path("stage").asText()).isEqualTo("kc_ready");
        assertThat(invokeStage(taskId, "extract-examples").path("stage").asText()).isEqualTo("oj_candidates_ready");
        assertThat(invokeStage(taskId, "generate-problems").path("stage").asText()).isEqualTo("problem_packages_ready");
        assertThat(invokeStage(taskId, "validate-problems").path("stage").asText()).isEqualTo("problems_validated");

        JsonNode published = invokeStage(taskId, "publish");
        assertThat(published.path("stage").asText()).isEqualTo("published");
        assertThat(published.path("language_pack").path("status").asText()).isEqualTo("published");
        assertThat(published.path("language_pack").path("version").asInt()).isEqualTo(2);

        String packStatus = jdbcTemplate.queryForObject(
                "select status from language_pack where id = ?",
                String.class,
                languagePackId
        );
        assertThat(packStatus).isEqualTo("published");

        if (!reboundClassroomIds.isEmpty()) {
            for (String classroomId : reboundClassroomIds) {
                Long reboundPackId = jdbcTemplate.queryForObject(
                        "select language_pack_id from classroom_language_pack where classroom_id = ?",
                        Long.class,
                        classroomId
                );
                assertThat(reboundPackId).isEqualTo(languagePackId);
            }
        }
    }

    private List<Path> listPptFiles(Path pptDir) throws IOException {
        try (var files = Files.list(pptDir)) {
            return files.filter(path -> LanguagePackChapterIndexResolver.isPptFilename(path.getFileName().toString()))
                    .sorted(Comparator.comparingInt(this::resolveChapterIndex))
                    .toList();
        }
    }

    private int resolveChapterIndex(Path pptFile) {
        Integer chapterIndex = LanguagePackChapterIndexResolver.resolveForPptFilename(
                pptFile.getFileName().toString()
        );
        if (chapterIndex == null) {
            throw new IllegalStateException("Unable to resolve chapter index from filename: " + pptFile.getFileName());
        }
        return chapterIndex;
    }

    private JsonNode invokeMultipart(MockMultipartHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(
                        request.with(admin()).with(csrf())
                )
                .andReturn();
        return extractData(result, "create-task");
    }

    private JsonNode invokeStage(Long taskId, String stageAction) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/admin/language-packs/init-tasks/{taskId}/" + stageAction, taskId)
                                .with(admin())
                                .with(csrf())
                )
                .andReturn();
        return extractData(result, stageAction);
    }

    private JsonNode extractData(MvcResult result, String action) throws Exception {
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        if (status != 200) {
            throw new AssertionError(action + " failed: status=" + status + ", body=" + body);
        }
        JsonNode root = objectMapper.readTree(body);
        if (!root.path("error").isNull() && !root.path("error").asText().isBlank()) {
            throw new AssertionError(action + " failed with api error: " + body);
        }
        return root.path("data");
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("root").roles("ADMIN");
    }

    private void deleteTaskDirectories(List<Long> taskIds) throws IOException {
        Path storageRoot = projectRoot().resolve("deploy/data/language_pack");
        for (Long taskId : taskIds) {
            deleteRecursivelyIfExists(storageRoot.resolve("tasks").resolve(String.valueOf(taskId)));
            deleteRecursivelyIfExists(storageRoot.resolve("preview/tasks").resolve(String.valueOf(taskId)));
        }
    }

    private void deleteRecursivelyIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path current : paths) {
                Files.deleteIfExists(current);
            }
        }
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (current.endsWith("backend")) {
            return current.getParent();
        }
        return current;
    }
}

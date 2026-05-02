package com.alethicode.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "alethicode.language-pack.libre-office-path=/definitely-missing/libreoffice"
})
class LanguagePackInitIntegrationTest extends AbstractJdbcIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AlethicodeProperties alethicodeProperties;

    @MockBean private JudgeServerService judgeServerService;
    @MockBean private SystemAdminService systemAdminService;
    @MockBean private ReleaseNotesService releaseNotesService;
    @MockBean private PlatformConfigService platformConfigService;
    @MockBean private SystemOptionService systemOptionService;
    @MockBean private AdminUploadService adminUploadService;
    @MockBean private AiModelGateway aiModelGateway;
    @MockBean private com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService judgeCheckService;

    @BeforeEach
    void setUp() {
        alethicodeProperties.getLanguagePack().getPublish().setSkipCoverageGate(false);
        jdbcTemplate.update(
                """
                INSERT INTO "user"(username, admin_type, create_time)
                VALUES (?, ?, now())
                ON CONFLICT (username) DO NOTHING
                """,
                "root", "Admin"
        );
        stubLlmDefaults();
        stubJudgeCheckDefaults();
        // Phase 3 切流：callForEmbedding 已从 AiModelGateway 删除，本 stub 一并清理。
    }

    @Test
    void createTaskShouldPersistLanguagePackAndTask() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "lesson.pdf", "application/pdf", "fake-pdf".getBytes());
        MvcResult result = mockMvc.perform(multipart("/api/admin/language-packs/init-tasks")
                        .file(file)
                        .param("name", "Python 入门")
                        .param("slug", "python-intro")
                        .param("primary_language", "Python3")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("normalizing"))
                .andExpect(jsonPath("$.data.language_pack.slug").value("python-intro"))
                .andExpect(jsonPath("$.data.language_pack.version").value(1))
                .andExpect(jsonPath("$.data.language_pack.primary_language").value("Python3"))
                .andExpect(jsonPath("$.data.language_pack.status").value("draft"))
                .andReturn();

        JsonNode data = json(result).at("/data");
        Long taskId = data.at("/id").asLong();
        Long languagePackId = data.at("/language_pack_id").asLong();
        assertThat(taskId).isPositive();
        assertThat(languagePackId).isPositive();

        Long packCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM language_pack WHERE id = ?", Long.class, languagePackId);
        assertThat(packCount).isEqualTo(1);

        Long logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM language_pack_init_stage_log WHERE task_id = ?", Long.class, taskId);
        assertThat(logCount).isEqualTo(2);
    }

    @Test
    void duplicateSlugShouldAutoIncrementVersion() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "lesson-a.pdf", "application/pdf", "fake-pdf-a".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "lesson-b.pdf", "application/pdf", "fake-pdf-b".getBytes());
        mockMvc.perform(multipart("/api/admin/language-packs/init-tasks")
                        .file(file1)
                        .param("name", "Python 入门 v1")
                        .param("slug", "python-intro")
                        .param("primary_language", "Python3")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/admin/language-packs/init-tasks")
                        .file(file2)
                        .param("name", "Python 入门 v2")
                        .param("slug", "python-intro")
                        .param("primary_language", "Python3")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language_pack.version").value(2));
    }

    @Test
    void getTaskShouldReturn404ForNonexistent() throws Exception {
        mockMvc.perform(get("/api/admin/language-packs/init-tasks/99999")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not-found"));
    }

    @Test
    void listTasksShouldReturnAllTasks() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "java.pdf", "application/pdf", "fake-java-pdf".getBytes());
        mockMvc.perform(multipart("/api/admin/language-packs/init-tasks")
                        .file(file)
                        .param("name", "Java 基础")
                        .param("slug", "java-basic")
                        .param("primary_language", "Java")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/language-packs/init-tasks")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void nonAdminShouldBeForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "lesson.pdf", "application/pdf", "fake-pdf".getBytes());
        mockMvc.perform(multipart("/api/admin/language-packs/init-tasks")
                        .file(file)
                        .param("name", "Python 入门")
                        .param("slug", "python-intro")
                        .param("primary_language", "Python3")
                        .with(user("student").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void parseDocumentsShouldPersistPageEmbeddingsForPptxPages() throws Exception {
        Path canonicalPath = writePptxFixture("language-pack-init-parse", "第一页：变量", "第二页：循环");

        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'normalizing', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, 'python-basic.pptx', ?, ?, ?, 'fixture-hash', 1024, 0, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId,
                canonicalPath.toString(),
                canonicalPath.toString(),
                "/tmp/python-basic-preview.pdf"
        );

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/parse")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("parsing"))
                .andExpect(jsonPath("$.data.language_pack.page_count").value(2));

        Integer pageCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_page WHERE document_id = ?",
                Integer.class,
                documentId
        );
        Integer embeddedPageCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_page WHERE document_id = ? AND page_embedding IS NOT NULL",
                Integer.class,
                documentId
        );
        assertThat(pageCount).isEqualTo(2);
        assertThat(embeddedPageCount).isEqualTo(2);
    }

    @Test
    void createTaskShouldNormalizePptxIntoPreviewPdfWithoutLibreOffice() throws Exception {
        Path pptxPath = writePptxFixture("language-pack-init-normalize", "第一页：变量", "第二页：循环");
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "python-basic.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                Files.readAllBytes(pptxPath)
        );

        MvcResult result = mockMvc.perform(multipart("/api/admin/language-packs/init-tasks")
                        .file(file)
                        .param("name", "Python 基础")
                        .param("slug", "python-basic")
                        .param("primary_language", "Python3")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("normalizing"))
                .andReturn();

        Long taskId = json(result).at("/data/id").asLong();
        String previewPdfPath = jdbcTemplate.queryForObject(
                """
                SELECT preview_pdf_path
                FROM language_pack_document
                WHERE init_task_id = ?
                """,
                String.class,
                taskId
        );
        String documentStatus = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM language_pack_document
                WHERE init_task_id = ?
                """,
                String.class,
                taskId
        );

        assertThat(documentStatus).isEqualTo("normalized");
        assertThat(previewPdfPath).isNotBlank();
        assertThat(Path.of(previewPdfPath)).isRegularFile();
        assertThat(Files.size(Path.of(previewPdfPath))).isPositive();
    }

    @Test
    void extractKcsShouldProcessEachDocumentSeparately() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'parsing', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long firstDocumentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：变量.pptx', '/tmp/doc-a.pptx', '/tmp/doc-a.pptx', '/tmp/doc-a.pdf',
                        'doc-a-hash', 1024, 2, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long secondDocumentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第二章：列表.pptx', '/tmp/doc-b.pptx', '/tmp/doc-b.pptx', '/tmp/doc-b.pdf',
                        'doc-b-hash', 1024, 2, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES
                    (?, ?, 1, 0, '变量', '变量用于保存数据', 'hash-a1', '/tmp/doc-a.pdf', '变量用于保存数据', cast(? as vector), to_tsvector('simple', '变量用于保存数据'), now()),
                    (?, ?, 2, 0, '变量练习', '练习：输出变量', 'hash-a2', '/tmp/doc-a.pdf', '练习：输出变量', cast(? as vector), to_tsvector('simple', '练习：输出变量'), now()),
                    (?, ?, 1, 0, '列表', '列表可以存放多个元素', 'hash-b1', '/tmp/doc-b.pdf', '列表可以存放多个元素', cast(? as vector), to_tsvector('simple', '列表可以存放多个元素'), now()),
                    (?, ?, 2, 0, '列表练习', '练习：遍历列表', 'hash-b2', '/tmp/doc-b.pdf', '练习：遍历列表', cast(? as vector), to_tsvector('simple', '练习：遍历列表'), now())
                """,
                firstDocumentId, languagePackId, embeddingVector(),
                firstDocumentId, languagePackId, embeddingVector(),
                secondDocumentId, languagePackId, embeddingVector(),
                secondDocumentId, languagePackId, embeddingVector()
        );
        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(Map.of(
                        "kcs", List.of(
                                Map.of("name", "变量", "name_en", "Variables", "description", "理解变量含义", "pages", List.of(1, 2))
                        )
                ))
                .thenReturn(Map.of(
                        "kcs", List.of(
                                Map.of("name", "列表", "name_en", "Lists", "description", "理解列表基础", "pages", List.of(1, 2))
                        )
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-kcs")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("kc_ready"))
                .andExpect(jsonPath("$.data.language_pack.chapter_count").value(2))
                .andExpect(jsonPath("$.data.language_pack.kc_count").value(2));

        verify(aiModelGateway, times(2)).callForJson(anyString(), anyString());
        Integer mappedPageCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_kc_page_mapping",
                Integer.class
        );
        assertThat(mappedPageCount).isEqualTo(4);
    }

    @Test
    void extractExamplesShouldProcessEachDocumentSeparately() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'kc_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long firstDocumentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：变量.pptx', '/tmp/doc-a.pptx', '/tmp/doc-a.pptx', '/tmp/doc-a.pdf',
                        'doc-a-hash', 1024, 2, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long secondDocumentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第二章：列表.pptx', '/tmp/doc-b.pptx', '/tmp/doc-b.pptx', '/tmp/doc-b.pdf',
                        'doc-b-hash', 1024, 2, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long firstKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '变量', '变量', 'Variables', '理解变量含义', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long secondKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '列表', '列表', 'Lists', '理解列表基础', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES
                    (?, ?, 1, 0, '变量', '示例：print(name)', 'hash-a1', '/tmp/doc-a.pdf', '示例：print(name)', cast(? as vector), to_tsvector('simple', '示例：print(name)'), now()),
                    (?, ?, 2, 0, '变量练习', '练习：输入姓名并输出', 'hash-a2', '/tmp/doc-a.pdf', '练习：输入姓名并输出', cast(? as vector), to_tsvector('simple', '练习：输入姓名并输出'), now()),
                    (?, ?, 1, 0, '列表', '示例：nums = [1, 2, 3]', 'hash-b1', '/tmp/doc-b.pdf', '示例：nums = [1, 2, 3]', cast(? as vector), to_tsvector('simple', '示例：nums = [1, 2, 3]'), now()),
                    (?, ?, 2, 0, '列表练习', '练习：遍历列表并求和', 'hash-b2', '/tmp/doc-b.pdf', '练习：遍历列表并求和', cast(? as vector), to_tsvector('simple', '练习：遍历列表并求和'), now())
                """,
                firstDocumentId, languagePackId, embeddingVector(),
                firstDocumentId, languagePackId, embeddingVector(),
                secondDocumentId, languagePackId, embeddingVector(),
                secondDocumentId, languagePackId, embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_kc_page_mapping(kc_id, page_id)
                SELECT ?, id FROM language_pack_page WHERE document_id = ? AND language_pack_id = ?
                UNION ALL
                SELECT ?, id FROM language_pack_page WHERE document_id = ? AND language_pack_id = ?
                """,
                firstKcId, firstDocumentId, languagePackId,
                secondKcId, secondDocumentId, languagePackId
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(1, String.class);
                    if (prompt.contains("变量 (id=" + firstKcId + ")")) {
                        return Map.of(
                                "units", List.of(
                                        Map.of(
                                                "raw_text", "练习：输入姓名并输出问候语",
                                                "normalized_body", "读取一个姓名并输出 Hello, <name>",
                                                "input_description", "输入一个姓名",
                                                "output_description", "输出 Hello, <name>",
                                                "evidence_excerpt", "练习：输入姓名并输出问候语",
                                                "page_range_start", 2,
                                                "page_range_end", 2,
                                                "kc_ids", List.of(firstKcId),
                                                "source_title", "练习：输入姓名并输出问候语",
                                                "unit_type", "exercise"
                                        )
                                )
                        );
                    }
                    return Map.of(
                            "units", List.of(
                                    Map.of(
                                            "raw_text", "练习：遍历列表并求和",
                                            "normalized_body", "读取一个整数列表并输出所有元素之和",
                                            "input_description", "第一行输入整数个数 n，第二行输入 n 个整数",
                                            "output_description", "输出这 n 个整数的和",
                                            "evidence_excerpt", "练习：遍历列表并求和",
                                            "page_range_start", 2,
                                            "page_range_end", 2,
                                            "kc_ids", List.of(secondKcId),
                                            "source_title", "练习：遍历列表并求和",
                                            "unit_type", "exercise"
                                    )
                            )
                    );
                });

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-examples")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("oj_candidates_ready"))
                .andExpect(jsonPath("$.data.language_pack.example_count").value(2));

        ArgumentCaptor<String> examplePromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiModelGateway, times(2)).callForJson(anyString(), examplePromptCaptor.capture());
        List<String> prompts = examplePromptCaptor.getAllValues();
        assertThat(prompts.get(0)).contains("变量 (id=" + firstKcId + ")");
        assertThat(prompts.get(0)).doesNotContain("列表 (id=" + secondKcId + ")");
        assertThat(prompts.get(1)).contains("列表 (id=" + secondKcId + ")");
        assertThat(prompts.get(1)).doesNotContain("变量 (id=" + firstKcId + ")");
        Integer exampleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_example",
                Integer.class
        );
        Integer exampleKcCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_example_kc_mapping",
                Integer.class
        );
        assertThat(exampleCount).isEqualTo(2);
        assertThat(exampleKcCount).isEqualTo(2);
        Integer ojConvertibleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_example WHERE language_pack_id = ? AND oj_convertible = true",
                Integer.class,
                languagePackId
        );
        Integer artifactCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_artifact WHERE task_id = ? AND artifact_type IN ('courseware_segments.json', 'courseware_units.json', 'oj_candidates.json', 'escalation_review.json')",
                Integer.class,
                taskId
        );
        Integer agentRunCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_agent_run WHERE task_id = ? AND agent_name IN ('CoursewareSegmentationAgent', 'CoursewareUnitExtractionAgent', 'OjCandidateJudgementAgent', 'EscalationReviewAgent')",
                Integer.class,
                taskId
        );
        assertThat(ojConvertibleCount).isEqualTo(2);
        assertThat(artifactCount).isEqualTo(4);
        assertThat(agentRunCount).isEqualTo(4);
    }

    @Test
    void extractExamplesShouldFilterNonConvertibleFixedOutputCandidate() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-fixed-output-filter', 1, 'Python 固定输出过滤', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'kc_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, 'fixed-output.pptx', '/tmp/fixed-output.pptx', '/tmp/fixed-output.pptx', '/tmp/fixed-output.pdf',
                        'fixed-output-hash', 1024, 1, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '输出', '输出', 'Output', '理解输出', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES (?, ?, 1, 0, '生日歌', '输出生日歌歌词十遍', 'hash-fixed-output', '/tmp/fixed-output.pdf',
                        '输出生日歌歌词十遍', cast(? as vector), to_tsvector('simple', '输出生日歌歌词十遍'), now())
                """,
                documentId,
                languagePackId,
                embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_kc_page_mapping(kc_id, page_id)
                SELECT ?, id FROM language_pack_page WHERE document_id = ? AND language_pack_id = ?
                """,
                kcId,
                documentId,
                languagePackId
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(Map.of(
                        "examples", List.of(
                                Map.of(
                                        "raw_text", "输出生日歌歌词十遍",
                                        "normalized_body", "for _ in range(10): print('Happy Birthday')",
                                        "input_description", "无输入",
                                        "output_description", "输出生日歌歌词十遍",
                                        "evidence_excerpt", "输出生日歌歌词十遍",
                                        "page_range_start", 1,
                                        "page_range_end", 1,
                                        "kc_ids", List.of(kcId)
                                )
                        )
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-examples")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("oj_candidates_ready"));

        Boolean ojConvertible = jdbcTemplate.queryForObject(
                "SELECT oj_convertible FROM language_pack_example WHERE language_pack_id = ?",
                Boolean.class,
                languagePackId
        );
        String ojBlockReason = jdbcTemplate.queryForObject(
                "SELECT oj_block_reason FROM language_pack_example WHERE language_pack_id = ?",
                String.class,
                languagePackId
        );
        assertThat(ojConvertible).isFalse();
        assertThat(ojBlockReason).isEqualTo("not_stdin_stdout_convertible");
    }

    @Test
    void extractExamplesShouldOverrideMismatchedSourceTitleWithTaskLine() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-source-title-fix', 1, 'Python 标题纠偏', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'kc_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, 'title-fix.pptx', '/tmp/title-fix.pptx', '/tmp/title-fix.pptx', '/tmp/title-fix.pdf',
                        'title-fix-hash', 1024, 1, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '分支判断', '分支判断', 'Branch Judge', '理解分支判断', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES (?, ?, 1, 0, '三角形判断', '举例：三角形判断（多条件判断）', 'hash-title-fix', '/tmp/title-fix.pdf',
                        '举例：三角形判断（多条件判断）', cast(? as vector), to_tsvector('simple', '举例：三角形判断（多条件判断）'), now())
                """,
                documentId,
                languagePackId,
                embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_kc_page_mapping(kc_id, page_id)
                SELECT ?, id FROM language_pack_page WHERE document_id = ? AND language_pack_id = ?
                """,
                kcId,
                documentId,
                languagePackId
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(Map.of(
                        "units", List.of(
                                Map.of(
                                        "raw_text", "举例：三角形判断（多条件判断）\n编写程序，从键盘输入三条边，判断是否能够构成一个三角形。",
                                        "normalized_body", "输入三条边，判断是否能构成三角形",
                                        "input_description", "输入三条边",
                                        "output_description", "输出是否可构成三角形",
                                        "evidence_excerpt", "举例：三角形判断（多条件判断）",
                                        "page_range_start", 1,
                                        "page_range_end", 1,
                                        "kc_ids", List.of(kcId),
                                        "source_title", "4.18 九九乘法表",
                                        "unit_type", "exercise"
                                )
                        )
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-examples")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("oj_candidates_ready"));

        String persistedSourceTitle = jdbcTemplate.queryForObject(
                "SELECT source_title FROM language_pack_example WHERE language_pack_id = ?",
                String.class,
                languagePackId
        );
        String persistedSignature = jdbcTemplate.queryForObject(
                "SELECT source_signature FROM language_pack_example WHERE language_pack_id = ?",
                String.class,
                languagePackId
        );
        assertThat(persistedSourceTitle).isEqualTo("举例：三角形判断（多条件判断）");
        assertThat(persistedSignature).contains("举例：三角形判断（多条件判断）");
        assertThat(persistedSignature).doesNotContain("4.18九九乘法表");
    }

    @Test
    void extractExamplesShouldKeepParameterizableOutputOnlyCandidateAsOjConvertible() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-parameterizable-output', 1, 'Python 参数化输出题', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'kc_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, 'sum-10000.pptx', '/tmp/sum-10000.pptx', '/tmp/sum-10000.pptx', '/tmp/sum-10000.pdf',
                        'sum-10000-hash', 1024, 1, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '循环求和', '循环求和', 'Loop Sum', '理解循环求和', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES (?, ?, 1, 0, '自然数求和', '计算 1 到 10000 的自然数之和', 'hash-sum-10000', '/tmp/sum-10000.pdf',
                        '计算 1 到 10000 的自然数之和', cast(? as vector), to_tsvector('simple', '计算 1 到 10000 的自然数之和'), now())
                """,
                documentId,
                languagePackId,
                embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_kc_page_mapping(kc_id, page_id)
                SELECT ?, id FROM language_pack_page WHERE document_id = ? AND language_pack_id = ?
                """,
                kcId,
                documentId,
                languagePackId
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(Map.of(
                        "examples", List.of(
                                Map.of(
                                        "raw_text", "计算 1 到 10000 的自然数之和",
                                        "normalized_body", "sum(range(1, 10001))",
                                        "input_description", "无输入",
                                        "output_description", "输出 1 到 10000 的自然数之和",
                                        "evidence_excerpt", "计算 1 到 10000 的自然数之和",
                                        "page_range_start", 1,
                                        "page_range_end", 1,
                                        "kc_ids", List.of(kcId)
                                )
                        )
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-examples")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("oj_candidates_ready"));

        Boolean ojConvertible = jdbcTemplate.queryForObject(
                "SELECT oj_convertible FROM language_pack_example WHERE language_pack_id = ?",
                Boolean.class,
                languagePackId
        );
        String ojBlockReason = jdbcTemplate.queryForObject(
                "SELECT oj_block_reason FROM language_pack_example WHERE language_pack_id = ?",
                String.class,
                languagePackId
        );
        assertThat(ojConvertible).isTrue();
        assertThat(ojBlockReason).isEmpty();
    }

    @Test
    void extractKcsShouldSplitLongDocumentsIntoSmallBatches() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'parsing', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：长文档.pptx', '/tmp/doc-long.pptx', '/tmp/doc-long.pptx', '/tmp/doc-long.pdf',
                        'doc-long-hash', 1024, 31, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );

        for (int pageNo = 1; pageNo <= 31; pageNo++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_page(
                        document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                        text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                    )
                    VALUES (?, ?, ?, 0, ?, ?, ?, '/tmp/doc-long.pdf', ?, cast(? as vector),
                            to_tsvector('simple', ?), now())
                    """,
                    documentId,
                    languagePackId,
                    pageNo,
                    "第" + pageNo + "页",
                    "这一页讲变量、分支和循环，第" + pageNo + "页",
                    "hash-long-" + pageNo,
                    "这一页讲变量、分支和循环，第" + pageNo + "页",
                    embeddingVector(),
                    "这一页讲变量、分支和循环，第" + pageNo + "页"
            );
        }

        reset(aiModelGateway);
        stubLlmDefaults();
        Map<String, Object> kcResult = Map.of(
                "kcs", List.of(
                        Map.of("name", "变量", "name_en", "Variables", "description", "理解变量含义", "pages", List.of(1, 2, 3))
                )
        );
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(kcResult);

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-kcs")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("kc_ready"))
                .andExpect(jsonPath("$.data.language_pack.kc_count").value(1));

        verify(aiModelGateway, times(1)).callForJson(anyString(), anyString());
        Integer splitCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_batch_run WHERE task_id = ? AND stage_name = 'extract-kcs' AND status = 'split'",
                Integer.class,
                taskId
        );
        assertThat(splitCount).isEqualTo(0);
    }

    @Test
    void extractKcsShouldFallbackByBinaryAndWriteCanonicalArtifacts() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'parsing', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：二进制回退.pptx', '/tmp/doc-adaptive.pptx', '/tmp/doc-adaptive.pptx', '/tmp/doc-adaptive.pdf',
                        'doc-adaptive-hash', 1024, 32, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );

        for (int pageNo = 1; pageNo <= 32; pageNo++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_page(
                        document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                        text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                    )
                    VALUES (?, ?, ?, 0, ?, ?, ?, '/tmp/doc-adaptive.pdf', ?, cast(? as vector),
                            to_tsvector('simple', ?), now())
                    """,
                    documentId,
                    languagePackId,
                    pageNo,
                    "第" + pageNo + "页",
                    "第" + pageNo + "页讲变量相关知识",
                    "hash-adaptive-" + pageNo,
                    "第" + pageNo + "页讲变量相关知识",
                    embeddingVector(),
                    "第" + pageNo + "页讲变量相关知识"
            );
        }

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenThrow(new IllegalStateException("request timed out"))
                .thenReturn(Map.of(
                        "kcs", List.of(
                                Map.of("name", "变量与赋值", "name_en", "Variables and Assignment", "description", "理解变量与赋值", "pages", List.of(1, 2, 3, 4))
                        )
                ))
                .thenReturn(Map.of(
                        "kcs", List.of(
                                Map.of("name", "变量", "name_en", "Variables", "description", "理解变量", "pages", List.of(16, 17, 18))
                        )
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-kcs")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("kc_ready"))
                .andExpect(jsonPath("$.data.language_pack.kc_count").value(1));

        verify(aiModelGateway, times(3)).callForJson(anyString(), anyString());
        Integer artifactCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_artifact WHERE task_id = ? AND artifact_type IN ('kc_batch_results.json', 'chapter_memory.json', 'kc_catalog.json')",
                Integer.class,
                taskId
        );
        Integer splitBatchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_batch_run WHERE task_id = ? AND stage_name = 'extract-kcs' AND status = 'split'",
                Integer.class,
                taskId
        );
        Integer completedBatchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_batch_run WHERE task_id = ? AND stage_name = 'extract-kcs' AND status = 'completed'",
                Integer.class,
                taskId
        );
        String canonicalName = jdbcTemplate.queryForObject(
                "SELECT name FROM language_pack_kc WHERE language_pack_id = ? ORDER BY id LIMIT 1",
                String.class,
                languagePackId
        );
        Integer mappedPageCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_kc_page_mapping",
                Integer.class
        );
        assertThat(artifactCount).isEqualTo(3);
        assertThat(splitBatchCount).isEqualTo(1);
        assertThat(completedBatchCount).isEqualTo(2);
        assertThat(canonicalName).isEqualTo("变量与赋值");
        assertThat(mappedPageCount).isEqualTo(7);
    }

    @Test
    void extractKcsShouldReuseCompletedBatchesWhenRetryingFailedTask() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'parsing', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：断点续跑.pptx', '/tmp/doc-resume.pptx', '/tmp/doc-resume.pptx', '/tmp/doc-resume.pdf',
                        'doc-resume-hash', 1024, 40, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );

        for (int pageNo = 1; pageNo <= 40; pageNo++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_page(
                        document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                        text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                    )
                    VALUES (?, ?, ?, 0, ?, ?, ?, '/tmp/doc-resume.pdf', ?, cast(? as vector),
                            to_tsvector('simple', ?), now())
                    """,
                    documentId,
                    languagePackId,
                    pageNo,
                    "第" + pageNo + "页",
                    "第" + pageNo + "页讲循环与列表",
                    "hash-resume-" + pageNo,
                    "第" + pageNo + "页讲循环与列表",
                    embeddingVector(),
                    "第" + pageNo + "页讲循环与列表"
            );
        }

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(1, String.class);
                    if (prompt.contains("--- Page 40")) {
                        throw new IllegalStateException("request timed out");
                    }
                    return Map.of(
                            "kcs", List.of(
                                    Map.of("name", "循环", "name_en", "Loops", "description", "理解循环", "pages", List.of(1, 2, 3))
                            )
                    );
                });

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-kcs")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is5xxServerError());

        String failedStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        assertThat(failedStage).isEqualTo("failed");

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(Map.of(
                        "kcs", List.of(
                                Map.of("name", "循环", "name_en", "Loops", "description", "理解循环", "pages", List.of(1, 2, 3))
                        )
                ));

        ArgumentCaptor<String> retryPromptCaptor = ArgumentCaptor.forClass(String.class);
        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-kcs")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("kc_ready"));

        verify(aiModelGateway, atLeastOnce()).callForJson(anyString(), retryPromptCaptor.capture());
        List<String> retryPrompts = retryPromptCaptor.getAllValues();
        assertThat(retryPrompts).noneMatch(prompt -> prompt.contains("--- Page 1") && prompt.contains("--- Page 32"));
        Integer reusedBatchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_batch_run WHERE task_id = ? AND stage_name = 'extract-kcs' AND status = 'reused'",
                Integer.class,
                taskId
        );
        assertThat(reusedBatchCount).isGreaterThan(0);
    }

    @Test
    void extractExamplesShouldPersistFailedStageWhenMiniMaxReturnsInvalidJson() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'kc_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：变量.pptx', '/tmp/doc-a.pptx', '/tmp/doc-a.pptx', '/tmp/doc-a.pdf',
                        'doc-a-hash', 1024, 1, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '变量', '变量', 'Variables', '理解变量含义', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES (?, ?, 1, 0, '变量', '示例：print(name)', 'hash-a1', '/tmp/doc-a.pdf',
                        '示例：print(name)', cast(? as vector), to_tsvector('simple', '示例：print(name)'), now())
                """,
                documentId,
                languagePackId,
                embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_kc_page_mapping(kc_id, page_id)
                SELECT ?, id FROM language_pack_page WHERE document_id = ? AND language_pack_id = ?
                """,
                kcId,
                documentId,
                languagePackId
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM message.content is not valid JSON object"));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-examples")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is5xxServerError());

        String stage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        String failureReason = jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        assertThat(stage).isEqualTo("failed");
        assertThat(failureReason).contains("LLM message.content is not valid JSON object");

        Integer stageLogCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_stage_log WHERE task_id = ? AND to_stage = 'failed'",
                Integer.class,
                taskId
        );
        assertThat(stageLogCount).isEqualTo(1);
        Integer exampleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_example WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        assertThat(exampleCount).isEqualTo(0);
    }

    @Test
    void generateProblemsShouldCallMiniMaxMultipleTimesAndReplaceOldCandidates() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'oj_candidates_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, 'python-basic.pptx', '/tmp/python-basic.pptx', '/tmp/python-basic.pptx', '/tmp/python-basic.pdf',
                        'doc-hash-generate', 1024, 5, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );

        java.util.ArrayList<Long> kcIds = new java.util.ArrayList<>();
        java.util.ArrayList<Long> exampleIds = new java.util.ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            Long kcId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, now())
                    RETURNING id
                    """,
                    Long.class,
                    languagePackId,
                    taskId,
                    "知识点" + index,
                    "知识点" + index,
                    "KC " + index,
                    "知识点描述" + index
            );
            kcIds.add(kcId);
            Long exampleId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO language_pack_example(
                        language_pack_id, init_task_id, document_id, raw_text, normalized_body,
                        input_description, output_description, evidence_excerpt,
                        page_range_start, page_range_end, source_title, unit_type,
                        oj_convertible, oj_block_reason, source_signature, create_time
                    )
                    VALUES (?, ?, ?, ?, ?, '', ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    RETURNING id
                    """,
                    Long.class,
                    languagePackId,
                    taskId,
                    documentId,
                    "示例" + index,
                    "print(" + index + ")",
                    "输出数字" + index,
                    "示例" + index,
                    index,
                    index,
                    "第" + index + "题",
                    "exercise",
                    true,
                    "",
                    "chapter:测试|title:第" + index + "题|pages:" + index + "-" + index + "|type:exercise"
            );
            exampleIds.add(exampleId);
            jdbcTemplate.update(
                "INSERT INTO language_pack_example_kc_mapping(example_id, kc_id) VALUES (?, ?)",
                exampleId,
                    kcId
            );
        }

        for (int pageNo = 1; pageNo <= 5; pageNo++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_page(
                        document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                        text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                    )
                    VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, cast(? as vector), to_tsvector('simple', ?), now())
                    """,
                    documentId,
                    languagePackId,
                    pageNo,
                    "第" + pageNo + "页",
                    "课件正文" + pageNo,
                    "page-hash-" + pageNo,
                    "/tmp/page-" + pageNo + ".pdf",
                    "课件正文" + pageNo,
                    embeddingVector(),
                    "课件正文" + pageNo
            );
        }

        for (int index = 0; index < kcIds.size(); index++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_kc_page_mapping(kc_id, page_id)
                    VALUES (?, (
                        SELECT id
                        FROM language_pack_page
                        WHERE language_pack_id = ? AND page_no = ?
                        LIMIT 1
                    ))
                    """,
                    kcIds.get(index),
                    languagePackId,
                    index + 1
            );
        }

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_problem_generation_log(
                    init_task_id, language_pack_id, kc_id, example_id,
                    candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                    candidate_samples_json, reference_solution, test_cases_json,
                    teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                    validation_status, create_time
                )
                VALUES (?, ?, ?, ?, '旧候选题', '旧题干', '', '', '[]', 'print(1)', '[]', '旧解释', '[]', '[]', '[]', 'pending', now())
                """,
                taskId,
                languagePackId,
                kcIds.getFirst(),
                exampleIds.getFirst()
        );
        String ojCandidatesJson = objectMapper.writeValueAsString(Map.of(
                "oj_candidates", List.of(
                        candidateArtifact(exampleIds.get(0), documentId, "第1题", 1, kcIds.get(0)),
                        candidateArtifact(exampleIds.get(1), documentId, "第2题", 2, kcIds.get(1)),
                        candidateArtifact(exampleIds.get(2), documentId, "第3题", 3, kcIds.get(2)),
                        candidateArtifact(exampleIds.get(3), documentId, "第4题", 4, kcIds.get(3)),
                        candidateArtifact(exampleIds.get(4), documentId, "第5题", 5, kcIds.get(4))
                )
        ));
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'oj_candidates.json', 'oj_candidates_ready', ?, '', 'fixture-hash', now())
                """,
                taskId,
                ojCandidatesJson
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(singleProblemResponse("变量练习", "请输出 1", 1))
                .thenReturn(singleProblemResponse("分支练习", "请输出 2", 2))
                .thenReturn(singleProblemResponse("循环练习", "请输出 3", 3))
                .thenReturn(singleProblemResponse("函数练习", "请输出 4", 4))
                .thenReturn(singleProblemResponse("列表练习", "请输出 5", 5));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/generate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problem_packages_ready"));

        verify(aiModelGateway, times(5)).callForJson(anyString(), anyString());
        Integer candidateCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                Integer.class,
                taskId
        );
        assertThat(candidateCount).isEqualTo(5);
        Integer artifactCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_artifact WHERE task_id = ? AND artifact_type IN ('problem_packages.json', 'problem_packages.md', 'coverage_report.json')",
                Integer.class,
                taskId
        );
        Integer agentRunCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_agent_run WHERE task_id = ? AND agent_name = 'OjProblemPackageAgent'",
                Integer.class,
                taskId
        );
        assertThat(artifactCount).isEqualTo(3);
        assertThat(agentRunCount).isEqualTo(1);
    }

    @Test
    void generateProblemsShouldRejectDuplicateStartWhileGenerationIsRunning() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-problem-lock', 1, 'Python 并发保护', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'oj_candidates_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, 'python-problem-lock.pptx', '/tmp/python-problem-lock.pptx', '/tmp/python-problem-lock.pptx',
                        '/tmp/python-problem-lock.pdf', 'doc-hash-problem-lock', 1024, 1, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '知识点1', '知识点1', 'KC 1', '知识点描述1', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long exampleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_example(
                    language_pack_id, init_task_id, document_id, raw_text, normalized_body,
                    input_description, output_description, evidence_excerpt,
                    page_range_start, page_range_end, source_title, unit_type,
                    oj_convertible, oj_block_reason, source_signature, create_time
                )
                VALUES (?, ?, ?, ?, ?, '', ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId,
                documentId,
                "示例1",
                "print(1)",
                "输出数字1",
                "示例1",
                1,
                1,
                "第1题",
                "exercise",
                true,
                "",
                "chapter:测试|title:第1题|pages:1-1|type:exercise"
        );
        jdbcTemplate.update(
                "INSERT INTO language_pack_example_kc_mapping(example_id, kc_id) VALUES (?, ?)",
                exampleId,
                kcId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES (?, ?, 1, 0, '第1页', '课件正文1', 'page-hash-lock-1', '/tmp/page-lock-1.pdf', '课件正文1',
                        cast(? as vector), to_tsvector('simple', '课件正文1'), now())
                """,
                documentId,
                languagePackId,
                embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_kc_page_mapping(kc_id, page_id)
                VALUES (?, (
                    SELECT id
                    FROM language_pack_page
                    WHERE language_pack_id = ? AND page_no = 1
                    LIMIT 1
                ))
                """,
                kcId,
                languagePackId
        );
        String ojCandidatesJson = objectMapper.writeValueAsString(Map.of(
                "oj_candidates", List.of(candidateArtifact(exampleId, documentId, "第1题", 1, kcId))
        ));
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'oj_candidates.json', 'oj_candidates_ready', ?, '', 'fixture-hash-lock', now())
                """,
                taskId,
                ojCandidatesJson
        );

        CountDownLatch firstGenerationStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstGeneration = new CountDownLatch(1);
        AtomicInteger llmCallCount = new AtomicInteger(0);
        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    int currentCall = llmCallCount.incrementAndGet();
                    if (currentCall == 1) {
                        firstGenerationStarted.countDown();
                        boolean released = releaseFirstGeneration.await(5, TimeUnit.SECONDS);
                        assertThat(released).isTrue();
                    }
                    return singleProblemResponse("变量练习", "请输出 1", 1);
                });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> firstRequest = executor.submit(() -> mockMvc.perform(
                            post("/api/admin/language-packs/init-tasks/" + taskId + "/generate-problems")
                                    .with(user("root").roles("ADMIN"))
                                    .with(csrf()))
                    .andReturn()
                    .getResponse()
                    .getStatus());

            assertThat(firstGenerationStarted.await(3, TimeUnit.SECONDS)).isTrue();
            Map<String, Object> runningState = jdbcTemplate.queryForMap(
                    "SELECT stage, active_step_key, active_status FROM language_pack_init_task WHERE id = ?",
                    taskId
            );
            assertThat(runningState.get("stage")).isEqualTo("oj_candidates_ready");
            assertThat(runningState.get("active_step_key")).isEqualTo("problem_packages_ready");
            assertThat(runningState.get("active_status")).isEqualTo("running");

            mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/generate-problems")
                            .with(user("root").roles("ADMIN"))
                            .with(csrf()))
                    .andExpect(status().isConflict());

            releaseFirstGeneration.countDown();
            assertThat(firstRequest.get(5, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            releaseFirstGeneration.countDown();
            executor.shutdownNow();
        }

        String finalStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        assertThat(finalStage).isEqualTo("problem_packages_ready");
        Map<String, Object> idleState = jdbcTemplate.queryForMap(
                "SELECT active_step_key, active_status FROM language_pack_init_task WHERE id = ?",
                taskId
        );
        assertThat(idleState.get("active_step_key")).isNull();
        assertThat(idleState.get("active_status")).isEqualTo("idle");
        assertThat(llmCallCount.get()).isEqualTo(1);
    }

    @Test
    void extractExamplesShouldIncludeChapterMemoryAndNeighborAnchorsInUnitPrompt() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'kc_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：章节记忆.pptx', '/tmp/doc-memory.pptx', '/tmp/doc-memory.pptx', '/tmp/doc-memory.pdf',
                        'doc-memory-hash', 1024, 6, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long variableKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '变量', '变量', 'Variables', '理解变量', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long listKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '列表', '列表', 'Lists', '理解列表', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );

        for (int pageNo = 1; pageNo <= 6; pageNo++) {
            String title = switch (pageNo) {
                case 1 -> "4.1 变量练习";
                case 4 -> "4.2 列表练习";
                default -> "第" + pageNo + "页";
            };
            String text = pageNo <= 3 ? "变量练习与输入输出，第" + pageNo + "页" : "列表练习与遍历，第" + pageNo + "页";
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_page(
                        document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                        text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                    )
                    VALUES (?, ?, ?, 0, ?, ?, ?, '/tmp/doc-memory.pdf', ?, cast(? as vector),
                            to_tsvector('simple', ?), now())
                    """,
                    documentId,
                    languagePackId,
                    pageNo,
                    title,
                    text,
                    "hash-memory-" + pageNo,
                    text,
                    embeddingVector(),
                    text
            );
        }

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_kc_page_mapping(kc_id, page_id)
                SELECT ?, id FROM language_pack_page WHERE language_pack_id = ? AND document_id = ? AND page_no BETWEEN 1 AND 3
                UNION ALL
                SELECT ?, id FROM language_pack_page WHERE language_pack_id = ? AND document_id = ? AND page_no BETWEEN 4 AND 6
                """,
                variableKcId, languagePackId, documentId,
                listKcId, languagePackId, documentId
        );

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'chapter_memory.json', 'kc_ready', ?, '', 'chapter-memory-hash', now()),
                       (?, 'kc_catalog.json', 'kc_ready', ?, '', 'kc-catalog-hash', now())
                """,
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "chapters", List.of(Map.of(
                                "document_id", documentId,
                                "chapter_index", 1,
                                "chapter_title", "第一章：章节记忆",
                                "chapter_synopsis", "本章重点：变量、列表与跨页练习",
                                "canonical_kc_count", 2,
                                "conflict_count", 0,
                                "safe_window_size", 16
                        ))
                )),
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "kcs", List.of(
                                Map.of("document_id", documentId, "chapter_index", 1, "chapter_title", "第一章：章节记忆", "canonical_kc_id", variableKcId, "canonical_name", "变量", "aliases", List.of("变量"), "page_numbers", List.of(1, 2, 3)),
                                Map.of("document_id", documentId, "chapter_index", 1, "chapter_title", "第一章：章节记忆", "canonical_kc_id", listKcId, "canonical_name", "列表", "aliases", List.of("列表"), "page_numbers", List.of(4, 5, 6))
                        )
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(Map.of(
                        "units", List.of(
                                Map.of(
                                        "raw_text", "变量练习：输入姓名并输出",
                                        "normalized_body", "输入姓名并输出",
                                        "input_description", "输入一个姓名",
                                        "output_description", "输出姓名",
                                        "evidence_excerpt", "变量练习：输入姓名并输出",
                                        "page_range_start", 1,
                                        "page_range_end", 3,
                                        "kc_ids", List.of(variableKcId),
                                        "source_title", "4.1 变量练习",
                                        "unit_type", "exercise"
                                )
                        )
                ))
                .thenReturn(Map.of(
                        "units", List.of(
                                Map.of(
                                        "raw_text", "列表练习：遍历列表并求和",
                                        "normalized_body", "遍历列表并求和",
                                        "input_description", "输入一组整数",
                                        "output_description", "输出整数和",
                                        "evidence_excerpt", "列表练习：遍历列表并求和",
                                        "page_range_start", 4,
                                        "page_range_end", 6,
                                        "kc_ids", List.of(listKcId),
                                        "source_title", "4.2 列表练习",
                                        "unit_type", "exercise"
                                )
                        )
                ));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/extract-examples")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        verify(aiModelGateway, times(2)).callForJson(anyString(), promptCaptor.capture());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts).anyMatch(prompt -> prompt.contains("本章重点：变量、列表与跨页练习"));
        assertThat(prompts).anyMatch(prompt -> prompt.contains("4.2 列表练习"));
    }

    @Test
    void generateProblemsShouldIncludeChapterMemoryNeighborUnitsAndCanonicalKcsInPrompt() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'oj_candidates_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：生成记忆.pptx', '/tmp/doc-generate-memory.pptx', '/tmp/doc-generate-memory.pptx', '/tmp/doc-generate-memory.pdf',
                        'doc-generate-memory-hash', 1024, 2, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long ioKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, chapter_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, null, '输入输出', '输入输出', 'IO', '理解输入输出', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long listKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, chapter_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, null, '列表', '列表', 'Lists', '理解列表', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'chapter_memory.json', 'kc_ready', ?, '', 'chapter-memory-generate-hash', now()),
                       (?, 'kc_catalog.json', 'kc_ready', ?, '', 'kc-catalog-generate-hash', now()),
                       (?, 'oj_candidates.json', 'oj_candidates_ready', ?, '', 'oj-candidates-generate-hash', now())
                """,
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "chapters", List.of(Map.of(
                                "document_id", documentId,
                                "chapter_index", 1,
                                "chapter_title", "第一章：生成记忆",
                                "chapter_synopsis", "本章重点：输入输出与列表",
                                "canonical_kc_count", 2,
                                "conflict_count", 0,
                                "safe_window_size", 16
                        ))
                )),
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "kcs", List.of(
                                Map.of("document_id", documentId, "chapter_index", 1, "chapter_title", "第一章：生成记忆", "canonical_kc_id", ioKcId, "canonical_name", "输入输出", "aliases", List.of("输入输出"), "page_numbers", List.of(1)),
                                Map.of("document_id", documentId, "chapter_index", 1, "chapter_title", "第一章：生成记忆", "canonical_kc_id", listKcId, "canonical_name", "列表", "aliases", List.of("列表"), "page_numbers", List.of(2))
                        )
                )),
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "oj_candidates", List.of(
                                candidateArtifact(101L, documentId, "4.1 输入输出练习", 1, ioKcId),
                                candidateArtifact(102L, documentId, "4.2 列表练习", 2, listKcId)
                        )
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(singleProblemResponse("输入输出练习", "请输出 1", 1))
                .thenReturn(singleProblemResponse("列表练习", "请输出 2", 2));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/generate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problem_packages_ready"));

        verify(aiModelGateway, times(2)).callForJson(anyString(), promptCaptor.capture());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts).allMatch(prompt -> prompt.contains("本章重点：输入输出与列表"));
        assertThat(prompts).anyMatch(prompt -> prompt.contains("4.1 输入输出练习"));
        assertThat(prompts).anyMatch(prompt -> prompt.contains("4.2 列表练习"));
    }

    @Test
    void generateProblemsPromptShouldAnchorTitleAndTaskSelection() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-prompt-stability', 1, 'Python Prompt Stability', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'oj_candidates_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, 'prompt-stability.pptx', '/tmp/prompt-stability.pptx', '/tmp/prompt-stability.pptx', '/tmp/prompt-stability.pdf',
                        'doc-prompt-stability-hash', 1024, 2, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long vehicleKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '列表筛选', '列表筛选', 'List Filtering', '理解列表筛选', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long wordCountKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '字典统计', '字典统计', 'Dictionary Counting', '理解字典统计', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );

        Map<String, Object> vehicleCandidate = candidateArtifact(301L, documentId, "举例：统计某区域出现的车辆信息", 1, vehicleKcId);
        vehicleCandidate.put("normalized_body", "根据输入的车辆记录，筛选指定区域并输出车辆信息");
        vehicleCandidate.put("evidence_excerpt", "统计某区域出现的车辆信息");
        vehicleCandidate.put("input_description", "输入多条车辆记录与目标区域");
        vehicleCandidate.put("output_description", "输出属于目标区域的车辆信息");

        Map<String, Object> wordCountCandidate = candidateArtifact(302L, documentId, "词频统计代码实现", 2, wordCountKcId);
        wordCountCandidate.put("normalized_body", "读取文本并统计每个单词出现的次数");
        wordCountCandidate.put("evidence_excerpt", "词频统计代码实现");
        wordCountCandidate.put("input_description", "输入一段文本");
        wordCountCandidate.put("output_description", "输出每个单词及其出现次数");

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'oj_candidates.json', 'oj_candidates_ready', ?, '', 'oj-candidates-prompt-stability-hash', now())
                """,
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "oj_candidates", List.of(vehicleCandidate, wordCountCandidate)
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(singleProblemResponse("车辆区域筛选", "请筛选指定区域的车辆信息", 1))
                .thenReturn(singleProblemResponse("单词词频统计", "请统计词频", 2));

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/generate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problem_packages_ready"));

        verify(aiModelGateway, times(2)).callForJson(systemPromptCaptor.capture(), userPromptCaptor.capture());
        List<String> combinedPrompts = java.util.stream.IntStream.range(0, systemPromptCaptor.getAllValues().size())
                .mapToObj(index -> systemPromptCaptor.getAllValues().get(index) + "\n" + userPromptCaptor.getAllValues().get(index))
                .toList();

        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "The title must preserve the core task named by source_title and must not switch to a different exercise."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "Do not collapse different source units into the same overly generic title unless source_title already uses that exact title."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "If the material suggests multiple possible tasks, choose the one most directly supported by source_title, normalized_body, and evidence_excerpt."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "When adapting the material to stdin/stdout format, preserve the same core task rather than inventing a new scenario."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "Keep the same computational goal and required output semantics from source_title/normalized_body; do not replace it with a nearby variant."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "If the source material is output-only but still expresses a real computational task, convert it into a stdin/stdout OJ problem by introducing the minimal input needed to preserve the same task."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "For fixed-bound computational tasks such as summing 1..10000 or printing a fixed-size table, parameterize the bound through stdin instead of keeping a no-input version."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "For fixed-table exercises such as 九九乘法表, parameterize the table size through stdin so the OJ version becomes an n*n multiplication table instead of a hard-coded 9x9 printout."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "Do not force OJ conversion for internally-terminated approximation exercises with no natural external input, such as computing π until a term threshold is reached; these should be treated as non-convertible instead of inventing fake stdin."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "Do not return an output-only problem; every final OJ problem must consume stdin and produce stdout."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains(
                "Self-check before returning: first sample equals first test case, test case count is between 3 and 5, every test case input/output is non-empty, and reference_solution_code reads stdin, writes stdout, and satisfies all listed test cases."
        ));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains("- unit_id: "));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains("- required_source_example_ids: ["));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains("- required_source_pages: ["));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains("- required_related_kc_ids: ["));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains("Do not keep placeholder ids such as [1] from the JSON example."));
        assertThat(combinedPrompts).allMatch(prompt -> prompt.contains("Copy the required_source_example_ids, required_source_pages, and required_related_kc_ids exactly as provided."));
    }

    @Test
    void generateProblemsShouldCanonicalizeDeterministicSourceMetadata() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-canonicalize-source-metadata', 1, 'Python Canonicalize', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'oj_candidates_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, 'canonicalize-metadata.pptx', '/tmp/canonicalize-metadata.pptx', '/tmp/canonicalize-metadata.pptx',
                        '/tmp/canonicalize-metadata.pdf', 'canonicalize-metadata-hash', 1024, 1, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long canonicalKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '字符串', '字符串', 'String', '理解字符串', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long exampleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_example(
                    language_pack_id, init_task_id, document_id, raw_text, normalized_body,
                    input_description, output_description, evidence_excerpt,
                    page_range_start, page_range_end, source_title, unit_type,
                    oj_convertible, oj_block_reason, source_signature, create_time
                )
                VALUES (?, ?, ?, '字符串练习', '读取一个整数并输出对应星期', '输入一个 1-7 的整数', '输出星期字符串', '星期字符串',
                        74, 74, '3.74 字符串操作符', 'exercise', true, '',
                        'chapter:1|title:3.74 字符串操作符|pages:74-74|type:exercise', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId,
                documentId
        );
        Map<String, Object> candidate = candidateArtifact(exampleId, documentId, "3.74 字符串操作符", 74, canonicalKcId);
        candidate.put("normalized_body", "根据输入的数字输出对应星期字符串");
        candidate.put("input_description", "输入一个 1-7 的整数");
        candidate.put("output_description", "输出对应星期");
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'oj_candidates.json', 'oj_candidates_ready', ?, '', 'canonicalize-source-metadata-hash', now())
                """,
                taskId,
                objectMapper.writeValueAsString(Map.of("oj_candidates", List.of(candidate)))
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(problemResponseWithOverrides(
                        "输出星期字符串",
                        "输入一个 1 到 7 之间的整数，输出对应的星期字符串。",
                        Map.of(
                                "input_description", "输入一个整数 n",
                                "output_description", "输出对应的星期字符串",
                                "samples", List.of(Map.of("input", "3", "output", "星期三")),
                                "test_cases", List.of(
                                        Map.of("input", "3", "output", "星期三"),
                                        Map.of("input", "1", "output", "星期一"),
                                        Map.of("input", "7", "output", "星期日")
                                ),
                                "reference_solution_code", "n = int(input())\nweek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']\nprint(week[n - 1])\n",
                                "source_pages", List.of(999),
                                "source_example_ids", List.of(1),
                                "related_kc_ids", List.of(999)
                        )
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/generate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problem_packages_ready"));

        String sourceExampleIds = jdbcTemplate.queryForObject(
                "SELECT source_example_ids_json FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                String.class,
                taskId
        );
        String sourcePages = jdbcTemplate.queryForObject(
                "SELECT source_pages_json FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                String.class,
                taskId
        );
        String relatedKcIds = jdbcTemplate.queryForObject(
                "SELECT related_kc_ids_json FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                String.class,
                taskId
        );
        assertThat(sourceExampleIds).isEqualTo("[" + exampleId + "]");
        assertThat(sourcePages).isEqualTo("[74]");
        assertThat(relatedKcIds).isEqualTo("[" + canonicalKcId + "]");
    }

    @Test
    void validateProblemsShouldAutoCorrectDeterministicSourceMetadataWithoutRegeneration() throws Exception {
        ValidationFixture fixture = createValidationFixture("validate-autocorrect-source-metadata");
        Long candidateId = insertGeneratedProblem(
                fixture,
                problemPackageJson(Map.of(
                        "title", "输出星期字符串",
                        "description", "输入一个 1 到 7 之间的整数，输出对应的星期字符串。",
                        "input_description", "输入一个整数 n",
                        "output_description", "输出对应的星期字符串",
                        "samples", List.of(Map.of("input", "3", "output", "星期三")),
                        "test_cases", List.of(
                                Map.of("input", "3", "output", "星期三"),
                                Map.of("input", "1", "output", "星期一"),
                                Map.of("input", "7", "output", "星期日")
                        ),
                        "reference_solution_code", "n = int(input())\nweek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']\nprint(week[n - 1])\n",
                        "source_pages", List.of(999),
                        "source_example_ids", List.of(1),
                        "related_kc_ids", List.of(999)
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + fixture.taskId() + "/validate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problems_validated"));

        String validationStatus = jdbcTemplate.queryForObject(
                "SELECT validation_status FROM language_pack_problem_generation_log WHERE id = ?",
                String.class,
                candidateId
        );
        String sourceExampleIds = jdbcTemplate.queryForObject(
                "SELECT source_example_ids_json FROM language_pack_problem_generation_log WHERE id = ?",
                String.class,
                candidateId
        );
        String sourcePages = jdbcTemplate.queryForObject(
                "SELECT source_pages_json FROM language_pack_problem_generation_log WHERE id = ?",
                String.class,
                candidateId
        );
        String relatedKcIds = jdbcTemplate.queryForObject(
                "SELECT related_kc_ids_json FROM language_pack_problem_generation_log WHERE id = ?",
                String.class,
                candidateId
        );
        assertThat(validationStatus).isEqualTo("passed");
        assertThat(sourceExampleIds).isEqualTo("[" + fixture.exampleId() + "]");
        assertThat(sourcePages).isEqualTo("[74]");
        assertThat(relatedKcIds).isEqualTo("[" + fixture.kcId() + "]");
        verify(aiModelGateway, times(0)).callForJson(anyString(), anyString());
    }

    @Test
    void validateProblemsShouldRetryFailedCandidateOnceAndPassWhenRegeneratedProblemIsValid() throws Exception {
        ValidationFixture fixture = createValidationFixture("validate-retry-once-pass");
        Long candidateId = insertGeneratedProblem(
                fixture,
                problemPackageJson(Map.of(
                        "title", "输出星期字符串",
                        "description", "",
                        "input_description", "输入一个整数 n",
                        "output_description", "输出对应的星期字符串",
                        "samples", List.of(Map.of("input", "3", "output", "星期三")),
                        "test_cases", List.of(
                                Map.of("input", "3", "output", "星期三"),
                                Map.of("input", "1", "output", "星期一"),
                                Map.of("input", "7", "output", "星期日")
                        ),
                        "reference_solution_code", "n = int(input())\nprint(n)\n",
                        "source_pages", List.of(74),
                        "source_example_ids", List.of(fixture.exampleId()),
                        "related_kc_ids", List.of(fixture.kcId())
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(problemResponseWithOverrides(
                        "输出星期字符串",
                        "输入一个 1 到 7 之间的整数，输出对应的星期字符串。",
                        Map.of(
                                "input_description", "输入一个整数 n",
                                "output_description", "输出对应的星期字符串",
                                "samples", List.of(Map.of("input", "3", "output", "星期三")),
                                "test_cases", List.of(
                                        Map.of("input", "3", "output", "星期三"),
                                        Map.of("input", "1", "output", "星期一"),
                                        Map.of("input", "7", "output", "星期日")
                                ),
                                "reference_solution_code", "n = int(input())\nweek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']\nprint(week[n - 1])\n",
                                "source_pages", List.of(74),
                                "source_example_ids", List.of(fixture.exampleId()),
                                "related_kc_ids", List.of(fixture.kcId())
                        )
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + fixture.taskId() + "/validate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problems_validated"));

        String validationStatus = jdbcTemplate.queryForObject(
                "SELECT validation_status FROM language_pack_problem_generation_log WHERE id = ?",
                String.class,
                candidateId
        );
        String validationMessage = jdbcTemplate.queryForObject(
                "SELECT validation_message FROM language_pack_problem_generation_log WHERE id = ?",
                String.class,
                candidateId
        );
        assertThat(validationStatus).isEqualTo("passed");
        assertThat(validationMessage).isEqualTo("Problem package validation passed");
        verify(aiModelGateway, times(1)).callForJson(anyString(), anyString());
    }

    @Test
    void validateProblemsShouldRetryFailedCandidateOnceAndKeepFailedWhenRegeneratedProblemIsStillInvalid() throws Exception {
        ValidationFixture fixture = createValidationFixture("validate-retry-once-fail");
        Long candidateId = insertGeneratedProblem(
                fixture,
                problemPackageJson(Map.of(
                        "title", "输出星期字符串",
                        "description", "",
                        "input_description", "输入一个整数 n",
                        "output_description", "输出对应的星期字符串",
                        "samples", List.of(Map.of("input", "3", "output", "星期三")),
                        "test_cases", List.of(
                                Map.of("input", "3", "output", "星期三"),
                                Map.of("input", "1", "output", "星期一"),
                                Map.of("input", "7", "output", "星期日")
                        ),
                        "reference_solution_code", "n = int(input())\nprint(n)\n",
                        "source_pages", List.of(74),
                        "source_example_ids", List.of(fixture.exampleId()),
                        "related_kc_ids", List.of(fixture.kcId())
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(problemResponseWithOverrides(
                        "输出星期字符串",
                        "",
                        Map.of(
                                "input_description", "输入一个整数 n",
                                "output_description", "输出对应的星期字符串",
                                "samples", List.of(Map.of("input", "3", "output", "星期三")),
                                "test_cases", List.of(
                                        Map.of("input", "3", "output", "星期三"),
                                        Map.of("input", "1", "output", "星期一"),
                                        Map.of("input", "7", "output", "星期日")
                                ),
                                "reference_solution_code", "n = int(input())\nprint(n)\n",
                                "source_pages", List.of(74),
                                "source_example_ids", List.of(fixture.exampleId()),
                                "related_kc_ids", List.of(fixture.kcId())
                        )
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + fixture.taskId() + "/validate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problems_validated"));

        String validationStatus = jdbcTemplate.queryForObject(
                "SELECT validation_status FROM language_pack_problem_generation_log WHERE id = ?",
                String.class,
                candidateId
        );
        String validationMessage = jdbcTemplate.queryForObject(
                "SELECT validation_message FROM language_pack_problem_generation_log WHERE id = ?",
                String.class,
                candidateId
        );
        assertThat(validationStatus).isEqualTo("failed");
        assertThat(validationMessage).contains("Missing description");
        verify(aiModelGateway, times(1)).callForJson(anyString(), anyString());
    }

    @Test
    void validateProblemsShouldRejectRelatedKcsOutsideCanonicalCatalog() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('python-basic', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'problem_packages_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, '第一章：校验目录.pptx', '/tmp/doc-validate-catalog.pptx', '/tmp/doc-validate-catalog.pptx', '/tmp/doc-validate-catalog.pdf',
                        'doc-validate-catalog-hash', 1024, 1, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long canonicalKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '变量', '变量', 'Variables', '理解变量', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long nonCanonicalKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '分支', '分支', 'Branch', '理解分支', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long exampleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_example(
                    language_pack_id, init_task_id, document_id, raw_text, normalized_body,
                    input_description, output_description, evidence_excerpt,
                    page_range_start, page_range_end, source_title, unit_type,
                    oj_convertible, oj_block_reason, source_signature, create_time
                )
                VALUES (?, ?, ?, '变量练习', '变量练习', '无输入', '输出 1', '变量练习', 1, 1, '4.1 变量练习', 'exercise', true, '', 'chapter:1|title:4.1变量练习|pages:1-1|type:exercise', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId,
                documentId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES (?, ?, 1, 0, '变量练习', '变量练习', 'hash-validate-1', '/tmp/doc-validate-catalog.pdf',
                        '变量练习', cast(? as vector), to_tsvector('simple', '变量练习'), now())
                """,
                documentId,
                languagePackId,
                embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'kc_catalog.json', 'kc_ready', ?, '', 'kc-catalog-validation-hash', now())
                """,
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "kcs", List.of(
                                Map.of("canonical_kc_id", canonicalKcId, "canonical_name", "变量", "chapter_index", 1, "chapter_title", "第一章：校验目录")
                        )
                ))
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_problem_generation_log(
                    init_task_id, language_pack_id, kc_id, example_id,
                    candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                    candidate_samples_json, reference_solution, test_cases_json,
                    teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                    validation_status, create_time, source_example_ids_json, source_signature
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', now(), ?, ?)
                """,
                taskId,
                languagePackId,
                canonicalKcId,
                exampleId,
                "变量练习",
                "请输出 1。",
                "无输入",
                "输出 1",
                "[{\"input\":\"\",\"output\":\"1\"}]",
                "import sys\nsys.stdout.write('1\\n')\n",
                "[{\"input\":\"\",\"output\":\"1\"},{\"input\":\"\",\"output\":\"1\"},{\"input\":\"\",\"output\":\"1\"}]",
                "帮助学生掌握基础输出。",
                "[\"忘记换行\"]",
                "[1]",
                "[" + nonCanonicalKcId + "]",
                "[" + exampleId + "]",
                "chapter:1|title:4.1变量练习|pages:1-1|type:exercise"
        );

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/validate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problems_validated"));

        String validationStatus = jdbcTemplate.queryForObject(
                "SELECT validation_status FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                String.class,
                taskId
        );
        String validationMessage = jdbcTemplate.queryForObject(
                "SELECT validation_message FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                String.class,
                taskId
        );
        assertThat(validationStatus).isEqualTo("failed");
        assertThat(validationMessage).contains("canonical");
    }

    @Test
    void publishShouldNotRebindExistingClassrooms() throws Exception {
        Long oldLanguagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('publish-fixture', 1, 'Python 基础', 'Python3', 'published', now(), now())
                RETURNING id
                """,
                Long.class
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'published', false, now(), now())
                """,
                oldLanguagePackId
        );

        Long newLanguagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('publish-fixture', 2, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'problems_validated', false, now(), now())
                RETURNING id
                """,
                Long.class,
                newLanguagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '变量', '变量', 'Variables', '理解变量', now())
                RETURNING id
                """,
                Long.class,
                newLanguagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_task
                SET coverage_report_json = ?
                WHERE id = ?
                """,
                "{\"baseline_problem_count\":0,\"generated_problem_count\":1,\"final_oj_candidate_count\":1,\"missing\":[],\"extra\":[],\"blocked_candidates\":[],\"chapter_stats\":[],\"high_risk_chapters\":[],\"unresolved_review_required\":[]}",
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_problem_generation_log(
                    init_task_id, language_pack_id, kc_id, example_id,
                    candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                    candidate_samples_json, reference_solution, test_cases_json,
                    teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                    validation_status, create_time
                )
                VALUES (?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'passed', now())
                """,
                taskId,
                newLanguagePackId,
                kcId,
                "输出数字",
                "请输出数字 1。",
                "无输入",
                "输出 1",
                "[{\"input\":\"\",\"output\":\"1\"}]",
                "import sys\nsys.stdout.write('1\\n')\n",
                "[{\"input\":\"\",\"output\":\"1\"},{\"input\":\"\",\"output\":\"1\"},{\"input\":\"\",\"output\":\"1\"}]",
                "帮助学生理解最基础输出。",
                "[\"忘记换行\",\"输出了多余空格\"]",
                "[1]",
                "[" + kcId + "]"
        );

        Long rootUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM \"user\" WHERE username = 'root' ORDER BY id ASC LIMIT 1",
                Long.class
        );
        jdbcTemplate.update(
                """
                INSERT INTO classroom(id, name, created_by_id, is_active, create_time, update_time)
                VALUES ('class-publish-rebind', 'Publish Rebind', ?, true, now(), now())
                """,
                rootUserId
        );
        jdbcTemplate.update(
                """
                INSERT INTO classroom_language_pack(classroom_id, language_pack_id, create_time)
                VALUES ('class-publish-rebind', ?, now())
                """,
                oldLanguagePackId
        );

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/publish")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("published"))
                .andExpect(jsonPath("$.data.language_pack.status").value("published"));

        Long boundPackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM classroom_language_pack WHERE classroom_id = 'class-publish-rebind'",
                Long.class
        );
        assertThat(boundPackId)
                .as("existing classroom must stay on old version after new version is published")
                .isEqualTo(oldLanguagePackId);
    }

    @Test
    void validateProblemsShouldRejectOutputOnlyProblemThatWasNotParameterized() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('validate-output-only-fixture', 1, 'Python 输出题', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'problem_packages_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order, create_time, update_time
                )
                VALUES (?, ?, 'output-only.pdf', '/tmp/output-only.pdf', '/tmp/output-only.pdf', '/tmp/output-only-preview.pdf',
                        'output-only-document-hash', 1024, 1, 'normalized', 1, now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long chapterId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_chapter(language_pack_id, init_task_id, chapter_index, title, description,
                                                  page_range_start, page_range_end, create_time)
                VALUES (?, ?, 1, '第一章：输出题', '', 1, 1, now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long canonicalKcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, chapter_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, ?, '输出', '输出', 'Output', '理解固定输出题', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId,
                chapterId
        );
        Long exampleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_example(
                    language_pack_id, init_task_id, document_id, raw_text, normalized_body,
                    input_description, output_description, evidence_excerpt,
                    page_range_start, page_range_end, source_title, unit_type,
                    oj_convertible, oj_block_reason, source_signature, create_time
                )
                VALUES (?, ?, ?, '打印九九乘法表', '打印九九乘法表', '无输入', '输出九九乘法表', '打印九九乘法表',
                        1, 1, '4.18 九九乘法表', 'exercise', true, '',
                        'chapter:1|title:4.18九九乘法表|pages:1-1|type:exercise', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId,
                documentId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES (?, ?, 1, 0, '打印九九乘法表', '打印九九乘法表', 'hash-output-only', '/tmp/output-only.pdf',
                        '打印九九乘法表', cast(? as vector), to_tsvector('simple', '打印九九乘法表'), now())
                """,
                documentId,
                languagePackId,
                embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'kc_catalog.json', 'kc_ready', ?, '', 'kc-catalog-output-only-hash', now())
                """,
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "kcs", List.of(
                                Map.of("canonical_kc_id", canonicalKcId, "canonical_name", "输出", "chapter_index", 1, "chapter_title", "第一章：输出题")
                        )
                ))
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_problem_generation_log(
                    init_task_id, language_pack_id, kc_id, example_id,
                    candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                    candidate_samples_json, reference_solution, test_cases_json,
                    teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                    validation_status, create_time, source_example_ids_json, source_signature
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', now(), ?, ?)
                """,
                taskId,
                languagePackId,
                canonicalKcId,
                exampleId,
                "打印九九乘法表",
                "请按格式输出九九乘法表。",
                "本题无显式输入要求，直接输出九九乘法表。",
                "输出九九乘法表",
                "[{\"input\":\"\",\"output\":\"1*1=1\"}]",
                "for i in range(1, 2):\n    print(f'{i}*{i}={i*i}')\n",
                "[{\"input\":\"\",\"output\":\"1*1=1\"}]",
                "帮助学生理解固定输出程序。",
                "[\"遗漏换行\"]",
                "[1]",
                "[" + canonicalKcId + "]",
                "[" + exampleId + "]",
                "chapter:1|title:4.18九九乘法表|pages:1-1|type:exercise"
        );

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/validate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problems_validated"));

        String validationStatus = jdbcTemplate.queryForObject(
                "SELECT validation_status FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                String.class,
                taskId
        );
        String validationMessage = jdbcTemplate.queryForObject(
                "SELECT validation_message FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                String.class,
                taskId
        );
        assertThat(validationStatus).isEqualTo("failed");
        assertThat(validationMessage).contains("Output-only problems must be parameterized to stdin/stdout");
        assertThat(validationMessage).contains("Fewer than 3 test cases");
    }

    @Test
    void publishShouldAllowChapterMemoryAliasMergesWithoutBaseline() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('publish-conflict-fixture', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'problems_validated', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '输出', '输出', 'Output', '理解输出', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_task
                SET coverage_report_json = ?
                WHERE id = ?
                """,
                "{\"baseline_problem_count\":0,\"generated_problem_count\":1,\"final_oj_candidate_count\":1,\"missing\":[],\"extra\":[],\"blocked_candidates\":[],\"chapter_stats\":[],\"high_risk_chapters\":[],\"unresolved_review_required\":[],\"chapter_memory_conflict_count\":2}",
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_problem_generation_log(
                    init_task_id, language_pack_id, kc_id, example_id,
                    candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                    candidate_samples_json, reference_solution, test_cases_json,
                    teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                    validation_status, create_time
                )
                VALUES (?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'passed', now())
                """,
                taskId,
                languagePackId,
                kcId,
                "输出数字",
                "请输出数字 1。",
                "无输入",
                "输出 1",
                "[{\"input\":\"\",\"output\":\"1\"}]",
                "print(1)\n",
                "[{\"input\":\"\",\"output\":\"1\"}]",
                "输出固定数字。",
                "[]",
                "[1]",
                "[]"
        );

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/publish")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("published"));

        String stage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        Integer mappingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM language_pack_problem_mapping WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        Integer problemCount = jdbcTemplate.queryForObject(
                "SELECT problem_count FROM language_pack WHERE id = ?",
                Integer.class,
                languagePackId
        );
        assertThat(stage).isEqualTo("published");
        assertThat(mappingCount).isEqualTo(1);
        assertThat(problemCount).isEqualTo(1);
    }

    @Test
    void publishShouldAllowBaselineGapWhenCurrentPackHasNoHighRiskChapters() throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('publish-baseline-gap-fixture', 2, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'problems_validated', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '输出', '输出', 'Output', '理解输出', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_task
                SET coverage_report_json = ?
                WHERE id = ?
                """,
                "{\"baseline_problem_count\":51,\"generated_problem_count\":48,\"final_oj_candidate_count\":48," +
                        "\"missing\":[{\"title\":\"4.14 自然数之和\",\"chapter\":\"第四章：流程自动化\"}]," +
                        "\"extra\":[],\"blocked_candidates\":[]," +
                        "\"chapter_stats\":[{\"chapter_title\":\"第二章：Python 语言基础\",\"chapter_index\":4,\"chapter_page_count\":73," +
                        "\"unit_count\":2,\"oj_candidate_count\":2,\"baseline_expected_count\":5,\"generated_problem_count\":2}]," +
                        "\"high_risk_chapters\":[{\"chapter_title\":\"第二章：Python 语言基础\",\"chapter_index\":4,\"chapter_page_count\":73," +
                        "\"unit_count\":2,\"oj_candidate_count\":2,\"baseline_expected_count\":5,\"generated_problem_count\":2}]," +
                        "\"unresolved_review_required\":[],\"chapter_memory_conflict_count\":0}",
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_problem_generation_log(
                    init_task_id, language_pack_id, kc_id, example_id,
                    candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                    candidate_samples_json, reference_solution, test_cases_json,
                    teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                    validation_status, create_time
                )
                VALUES (?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'passed', now())
                """,
                taskId,
                languagePackId,
                kcId,
                "输出数字",
                "请输出数字 1。",
                "无输入",
                "输出 1",
                "[{\"input\":\"\",\"output\":\"1\"}]",
                "print(1)\n",
                "[{\"input\":\"\",\"output\":\"1\"}]",
                "帮助学生理解最基础输出。",
                "[\"忘记换行\"]",
                "[1]",
                "[" + kcId + "]"
        );

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/publish")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("published"))
                .andExpect(jsonPath("$.data.language_pack.status").value("published"));
    }

    @Test
    void publishShouldAllowFrontendTestingWhenCoverageGateIsSkipped() throws Exception {
        alethicodeProperties.getLanguagePack().getPublish().setSkipCoverageGate(true);

        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES ('publish-skip-coverage-fixture', 1, 'Python 基础', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'problems_validated', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, '输入输出', '输入输出', 'IO', '理解输入输出', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_task
                SET coverage_report_json = ?
                WHERE id = ?
                """,
                "{\"baseline_problem_count\":0,\"generated_problem_count\":1,\"final_oj_candidate_count\":0," +
                        "\"missing\":[],\"extra\":[],\"blocked_candidates\":[]," +
                        "\"chapter_stats\":[{\"chapter_title\":\"第一章：联调\",\"chapter_index\":1,\"chapter_page_count\":12," +
                        "\"unit_count\":2,\"oj_candidate_count\":0,\"convertible_unit_count\":2,\"non_convertible_unit_count\":0," +
                        "\"chapter_has_task_signal\":true,\"blocked_by_reason\":{}}]," +
                        "\"high_risk_chapters\":[{\"chapter_title\":\"第一章：联调\",\"chapter_index\":1,\"chapter_page_count\":12}]," +
                        "\"unresolved_review_required\":[]}",
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_problem_generation_log(
                    init_task_id, language_pack_id, kc_id, example_id,
                    candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                    candidate_samples_json, reference_solution, test_cases_json,
                    teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                    validation_status, create_time
                )
                VALUES (?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'passed', now())
                """,
                taskId,
                languagePackId,
                kcId,
                "数字回显",
                "输入一个整数，输出该整数。",
                "输入一个整数 n",
                "输出整数 n",
                "[{\"input\":\"1\",\"output\":\"1\"}]",
                "n = int(input())\nprint(n)\n",
                "[{\"input\":\"1\",\"output\":\"1\"},{\"input\":\"5\",\"output\":\"5\"},{\"input\":\"9\",\"output\":\"9\"}]",
                "帮助学生理解最基础输入输出。",
                "[\"忘记读取输入\"]",
                "[1]",
                "[" + kcId + "]"
        );

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + taskId + "/publish")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("published"))
                .andExpect(jsonPath("$.data.language_pack.status").value("published"));
    }

    @Test
    void generateProblemsShouldOverwriteOutputsWithJudgeExecutionResults() throws Exception {
        ValidationFixture fixture = createValidationFixture("judge-overwrite-output");

        String ojCandidatesJson = objectMapper.writeValueAsString(Map.of(
                "oj_candidates", List.of(candidateArtifact(
                        fixture.exampleId(), fixture.documentId(), "星期输出", 74, fixture.kcId()
                ))
        ));
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'oj_candidates.json', 'oj_candidates_ready', ?, '', 'fixture-hash', now())
                """,
                fixture.taskId(),
                ojCandidatesJson
        );
        jdbcTemplate.update(
                "UPDATE language_pack_init_task SET stage = 'oj_candidates_ready' WHERE id = ?",
                fixture.taskId()
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString(), anyString()))
                .thenReturn(problemResponseWithOverrides(
                        "星期输出",
                        "输入一个 1 到 7 之间的整数，输出对应的星期字符串。",
                        Map.of(
                                "source_pages", List.of(74),
                                "source_example_ids", List.of(fixture.exampleId()),
                                "related_kc_ids", List.of(fixture.kcId()),
                                "samples", List.of(Map.of("input", "3", "output", "LLM_WRONG")),
                                "test_cases", List.of(
                                        Map.of("input", "3", "output", "LLM_WRONG"),
                                        Map.of("input", "1", "output", "LLM_WRONG"),
                                        Map.of("input", "7", "output", "LLM_WRONG")
                                )
                        )
                ));

        reset(judgeCheckService);
        when(judgeCheckService.executeReferenceSolution(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(buildAllPassedResult(
                        List.of("3", "1", "7"),
                        List.of("星期三", "星期一", "星期日")
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + fixture.taskId() + "/generate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stage").value("problem_packages_ready"));

        String testCasesJson = jdbcTemplate.queryForObject(
                "SELECT test_cases_json FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                String.class, fixture.taskId()
        );
        assertThat(testCasesJson).contains("星期三");
        assertThat(testCasesJson).doesNotContain("LLM_WRONG");
    }

    @Test
    void generateProblemsShouldFailFastWhenJudgeServerUnavailable() throws Exception {
        ValidationFixture fixture = createValidationFixture("judge-unavailable");

        String ojCandidatesJson = objectMapper.writeValueAsString(Map.of(
                "oj_candidates", List.of(candidateArtifact(
                        fixture.exampleId(), fixture.documentId(), "无服务测试", 74, fixture.kcId()
                ))
        ));
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'oj_candidates.json', 'oj_candidates_ready', ?, '', 'fixture-hash', now())
                """,
                fixture.taskId(),
                ojCandidatesJson
        );
        jdbcTemplate.update(
                "UPDATE language_pack_init_task SET stage = 'oj_candidates_ready' WHERE id = ?",
                fixture.taskId()
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString(), anyString()))
                .thenReturn(problemResponseWithOverrides(
                        "无服务测试",
                        "测试内容",
                        Map.of(
                                "source_pages", List.of(74),
                                "source_example_ids", List.of(fixture.exampleId()),
                                "related_kc_ids", List.of(fixture.kcId())
                        )
                ));

        reset(judgeCheckService);
        when(judgeCheckService.executeReferenceSolution(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService.JudgeUnavailableException("暂无可用的评测服务器"));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + fixture.taskId() + "/generate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isNotEmpty());

        String stage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class, fixture.taskId()
        );
        assertThat(stage).isEqualTo("failed");
    }

    @Test
    void validateProblemsShouldDetectOutputMismatchViaJudgeRecheck() throws Exception {
        ValidationFixture fixture = createValidationFixture("validate-judge-mismatch");
        Long candidateId = insertGeneratedProblem(
                fixture,
                problemPackageJson(Map.of(
                        "title", "输出星期字符串",
                        "description", "输入一个 1 到 7 之间的整数，输出对应的星期字符串。",
                        "input_description", "输入一个整数 n",
                        "output_description", "输出对应的星期字符串",
                        "samples", List.of(Map.of("input", "3", "output", "TAMPERED")),
                        "test_cases", List.of(
                                Map.of("input", "3", "output", "TAMPERED"),
                                Map.of("input", "1", "output", "星期一"),
                                Map.of("input", "7", "output", "星期日")
                        ),
                        "reference_solution_code", "n = int(input())\nweek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']\nprint(week[n - 1])\n",
                        "source_pages", List.of(74),
                        "source_example_ids", List.of(fixture.exampleId()),
                        "related_kc_ids", List.of(fixture.kcId())
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();

        reset(judgeCheckService);
        when(judgeCheckService.executeReferenceSolution(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(buildAllPassedResult(
                        List.of("3", "1", "7"),
                        List.of("星期三", "星期一", "星期日")
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + fixture.taskId() + "/validate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        String validationStatus = jdbcTemplate.queryForObject(
                "SELECT validation_status FROM language_pack_problem_generation_log WHERE id = ?",
                String.class, candidateId
        );
        String validationMessage = jdbcTemplate.queryForObject(
                "SELECT validation_message FROM language_pack_problem_generation_log WHERE id = ?",
                String.class, candidateId
        );
        assertThat(validationStatus).isEqualTo("failed");
        assertThat(validationMessage).contains("output mismatch");
    }

    @Test
    void validateProblemsShouldPassWhenJudgeOutputMatchesPersisted() throws Exception {
        ValidationFixture fixture = createValidationFixture("validate-judge-match");
        Long candidateId = insertGeneratedProblem(
                fixture,
                problemPackageJson(Map.of(
                        "title", "输出星期字符串",
                        "description", "输入一个 1 到 7 之间的整数，输出对应的星期字符串。",
                        "input_description", "输入一个整数 n",
                        "output_description", "输出对应的星期字符串",
                        "samples", List.of(Map.of("input", "3", "output", "星期三")),
                        "test_cases", List.of(
                                Map.of("input", "3", "output", "星期三"),
                                Map.of("input", "1", "output", "星期一"),
                                Map.of("input", "7", "output", "星期日")
                        ),
                        "reference_solution_code", "n = int(input())\nweek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']\nprint(week[n - 1])\n",
                        "source_pages", List.of(74),
                        "source_example_ids", List.of(fixture.exampleId()),
                        "related_kc_ids", List.of(fixture.kcId())
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();

        reset(judgeCheckService);
        when(judgeCheckService.executeReferenceSolution(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(buildAllPassedResult(
                        List.of("3", "1", "7"),
                        List.of("星期三", "星期一", "星期日")
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + fixture.taskId() + "/validate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        String validationStatus = jdbcTemplate.queryForObject(
                "SELECT validation_status FROM language_pack_problem_generation_log WHERE id = ?",
                String.class, candidateId
        );
        assertThat(validationStatus).isEqualTo("passed");
    }

    @Test
    void validateProblemsShouldStillUseStructuralRegenForStructuralErrors() throws Exception {
        ValidationFixture fixture = createValidationFixture("validate-structural-regen");
        Long candidateId = insertGeneratedProblem(
                fixture,
                problemPackageJson(Map.of(
                        "title", "输出星期字符串",
                        "description", "",
                        "input_description", "输入一个整数 n",
                        "output_description", "输出对应的星期字符串",
                        "samples", List.of(Map.of("input", "3", "output", "星期三")),
                        "test_cases", List.of(
                                Map.of("input", "3", "output", "星期三"),
                                Map.of("input", "1", "output", "星期一"),
                                Map.of("input", "7", "output", "星期日")
                        ),
                        "reference_solution_code", "n = int(input())\nweek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']\nprint(week[n - 1])\n",
                        "source_pages", List.of(74),
                        "source_example_ids", List.of(fixture.exampleId()),
                        "related_kc_ids", List.of(fixture.kcId())
                ))
        );

        reset(aiModelGateway);
        stubLlmDefaults();
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(problemResponseWithOverrides(
                        "输出星期字符串",
                        "输入一个 1 到 7 之间的整数，输出对应的星期字符串。",
                        Map.of(
                                "input_description", "输入一个整数 n",
                                "output_description", "输出对应的星期字符串",
                                "source_pages", List.of(74),
                                "source_example_ids", List.of(fixture.exampleId()),
                                "related_kc_ids", List.of(fixture.kcId())
                        )
                ));

        reset(judgeCheckService);
        when(judgeCheckService.executeReferenceSolution(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(buildAllPassedResult(
                        List.of("3", "1", "7"),
                        List.of("星期三", "星期一", "星期日")
                ));

        mockMvc.perform(post("/api/admin/language-packs/init-tasks/" + fixture.taskId() + "/validate-problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        String validationStatus = jdbcTemplate.queryForObject(
                "SELECT validation_status FROM language_pack_problem_generation_log WHERE id = ?",
                String.class, candidateId
        );
        assertThat(validationStatus).isEqualTo("passed");
        verify(aiModelGateway, atLeastOnce()).callForJson(anyString(), anyString());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ValidationFixture createValidationFixture(String slug) throws Exception {
        Long languagePackId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES (?, 1, 'Python 校验重试', 'Python3', 'draft', now(), now())
                RETURNING id
                """,
                Long.class,
                slug
        );
        Long taskId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'problem_packages_ready', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order, create_time, update_time
                )
                VALUES (?, ?, 'validate-retry.pptx', '/tmp/validate-retry.pptx', '/tmp/validate-retry.pptx',
                        '/tmp/validate-retry.pdf', 'validate-retry-hash', 1024, 1, 'normalized', 1, now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId
        );
        Long chapterId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_chapter(language_pack_id, init_task_id, chapter_index, title, description,
                                                  page_range_start, page_range_end, create_time)
                VALUES (?, ?, 1, '第一章：测试', '', 74, 74, now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId
        );
        Long kcId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_kc(language_pack_id, init_task_id, chapter_id, name, name_normalized, name_en, description, create_time)
                VALUES (?, ?, ?, '字符串', '字符串', 'String', '理解字符串', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId,
                chapterId
        );
        Long exampleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_example(
                    language_pack_id, init_task_id, document_id, raw_text, normalized_body,
                    input_description, output_description, evidence_excerpt,
                    page_range_start, page_range_end, source_title, unit_type,
                    oj_convertible, oj_block_reason, source_signature, create_time
                )
                VALUES (?, ?, ?, '输出星期字符串', '根据输入的数字输出对应星期字符串',
                        '输入一个 1-7 的整数', '输出对应的星期字符串', '输出星期字符串',
                        74, 74, '3.74 字符串操作符', 'exercise', true, '',
                        'chapter:1|title:3.74 字符串操作符|pages:74-74|type:exercise', now())
                RETURNING id
                """,
                Long.class,
                languagePackId,
                taskId,
                documentId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, page_embedding, search_tsv, create_time
                )
                VALUES (?, ?, 74, 0, '输出星期字符串', '输出星期字符串', 'hash-validate-retry', '/tmp/validate-retry.pdf',
                        '输出星期字符串', cast(? as vector), to_tsvector('simple', '输出星期字符串'), now())
                """,
                documentId,
                languagePackId,
                embeddingVector()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'kc_catalog.json', 'kc_ready', ?, '', 'kc-catalog-validate-retry-hash', now())
                """,
                taskId,
                objectMapper.writeValueAsString(Map.of(
                        "kcs", List.of(
                                Map.of("canonical_kc_id", kcId, "canonical_name", "字符串", "chapter_index", 1, "chapter_title", "第一章：测试")
                        )
                ))
        );
        Map<String, Object> candidate = candidateArtifact(exampleId, documentId, "3.74 字符串操作符", 74, kcId);
        candidate.put("normalized_body", "根据输入的数字输出对应星期字符串");
        candidate.put("input_description", "输入一个 1-7 的整数");
        candidate.put("output_description", "输出对应的星期字符串");
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time)
                VALUES (?, 'oj_candidates.json', 'oj_candidates_ready', ?, '', 'oj-candidates-validate-retry-hash', now())
                """,
                taskId,
                objectMapper.writeValueAsString(Map.of("oj_candidates", List.of(candidate)))
        );
        return new ValidationFixture(languagePackId, taskId, documentId, kcId, exampleId, stringValue(candidate.get("source_signature")));
    }

    private Long insertGeneratedProblem(ValidationFixture fixture, String problemPackageJson) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_problem_generation_log(
                    init_task_id, language_pack_id, kc_id, example_id,
                    candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                    candidate_samples_json, reference_solution, test_cases_json,
                    teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                    validation_status, create_time, source_example_ids_json, source_signature,
                    problem_package_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', now(), ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                fixture.taskId(),
                fixture.languagePackId(),
                fixture.kcId(),
                fixture.exampleId(),
                "输出星期字符串",
                "输入一个 1 到 7 之间的整数，输出对应的星期字符串。",
                "输入一个整数 n",
                "输出对应的星期字符串",
                "[{\"input\":\"3\",\"output\":\"星期三\"}]",
                "n = int(input())\nprint(n)\n",
                "[{\"input\":\"3\",\"output\":\"星期三\"},{\"input\":\"1\",\"output\":\"星期一\"},{\"input\":\"7\",\"output\":\"星期日\"}]",
                "帮助学生理解字符串切片。",
                "[\"索引偏移错误\"]",
                "[74]",
                "[" + fixture.kcId() + "]",
                "[" + fixture.exampleId() + "]",
                fixture.sourceSignature(),
                problemPackageJson
        );
    }

    private Map<String, Object> singleProblemResponse(String title, String description, int outputValue) {
        Map<String, Object> problem = new java.util.LinkedHashMap<>();
        problem.put("title", title);
        problem.put("description", description);
        problem.put("input_description", "无输入");
        problem.put("output_description", "输出指定数字");
        problem.put("test_cases", List.of(
                Map.of("input", "", "output", String.valueOf(outputValue)),
                Map.of("input", "", "output", String.valueOf(outputValue)),
                Map.of("input", "", "output", String.valueOf(outputValue))
        ));
        problem.put("reference_solution", "import sys\nsys.stdout.write('" + outputValue + "\\n')\n");
        problem.put("teaching_explanation", "帮助学生掌握基础输出。");
        problem.put("common_mistakes", List.of("忘记换行", "输出格式错误"));
        problem.put("source_pages", List.of(outputValue));
        problem.put("related_kc_ids", List.of(outputValue));
        problem.put("kc_id", outputValue);
        problem.put("example_id", outputValue);
        return Map.of("problems", List.of(problem));
    }

    private Map<String, Object> problemResponseWithOverrides(String title,
                                                             String description,
                                                             Map<String, Object> overrides) {
        Map<String, Object> problem = new java.util.LinkedHashMap<>();
        problem.put("title", title);
        problem.put("description", description);
        problem.put("input_description", "输入一个整数 n");
        problem.put("output_description", "输出对应的星期字符串");
        problem.put("samples", List.of(Map.of("input", "3", "output", "星期三")));
        problem.put("test_cases", List.of(
                Map.of("input", "3", "output", "星期三"),
                Map.of("input", "1", "output", "星期一"),
                Map.of("input", "7", "output", "星期日")
        ));
        problem.put("template", Map.of("Python3", "n = int(input())\n# TODO\nprint()\n"));
        problem.put("difficulty", "Low");
        problem.put("teaching_explanation", "帮助学生理解字符串切片。");
        problem.put("common_mistakes", List.of("索引偏移错误", "忘记读取输入"));
        problem.put("reference_solution_code", "n = int(input())\nweek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']\nprint(week[n - 1])\n");
        problem.putAll(overrides);
        return Map.of("problems", List.of(problem));
    }

    private String problemPackageJson(Map<String, Object> overrides) throws Exception {
        Map<String, Object> problem = new java.util.LinkedHashMap<>();
        problem.put("title", "输出星期字符串");
        problem.put("description", "输入一个 1 到 7 之间的整数，输出对应的星期字符串。");
        problem.put("input_description", "输入一个整数 n");
        problem.put("output_description", "输出对应的星期字符串");
        problem.put("samples", List.of(Map.of("input", "3", "output", "星期三")));
        problem.put("test_cases", List.of(
                Map.of("input", "3", "output", "星期三"),
                Map.of("input", "1", "output", "星期一"),
                Map.of("input", "7", "output", "星期日")
        ));
        problem.put("template", Map.of("Python3", "n = int(input())\n# TODO\nprint()\n"));
        problem.put("difficulty", "Low");
        problem.put("teaching_explanation", "帮助学生理解字符串切片。");
        problem.put("common_mistakes", List.of("索引偏移错误", "忘记读取输入"));
        problem.put("reference_solution_code", "n = int(input())\nweek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']\nprint(week[n - 1])\n");
        problem.put("source_pages", List.of(74));
        problem.put("source_example_ids", List.of(1800));
        problem.put("related_kc_ids", List.of(1700));
        problem.putAll(overrides);
        return objectMapper.writeValueAsString(problem);
    }

    private Map<String, Object> candidateArtifact(Long exampleId,
                                                  Long documentId,
                                                  String sourceTitle,
                                                  int pageNo,
                                                  Long kcId) {
        Map<String, Object> candidate = new java.util.LinkedHashMap<>();
        candidate.put("id", exampleId);
        candidate.put("document_id", documentId);
        candidate.put("document_title", "python-basic.pptx");
        candidate.put("chapter_title", "第一章：测试");
        candidate.put("chapter_index", 1);
        candidate.put("chapter_page_count", 5);
        candidate.put("raw_text", "示例" + pageNo);
        candidate.put("normalized_body", "print(" + pageNo + ")");
        candidate.put("input_description", "");
        candidate.put("output_description", "输出数字" + pageNo);
        candidate.put("evidence_excerpt", "示例" + pageNo);
        candidate.put("page_range_start", pageNo);
        candidate.put("page_range_end", pageNo);
        candidate.put("source_title", sourceTitle);
        candidate.put("unit_type", "exercise");
        candidate.put("source_pages", List.of(pageNo));
        candidate.put("kc_ids", List.of(kcId));
        candidate.put("oj_convertible", true);
        candidate.put("oj_block_reason", "");
        candidate.put("review_required", false);
        candidate.put("review_reason", "");
        candidate.put("source_signature", "chapter:1|title:" + sourceTitle + "|pages:" + pageNo + "-" + pageNo + "|type:exercise");
        return candidate;
    }

    private Path writePptxFixture(String prefix, String... slideTexts) throws IOException {
        try {
            Path file = Files.createTempFile(prefix, ".pptx");
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
            return file;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("python fixture generation interrupted", exception);
        }
    }

    private void runPython(String script, Path file, String... values) throws IOException, InterruptedException {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("python3");
        command.add("-c");
        command.add(script);
        command.add(file.toString());
        for (String value : values) {
            command.add(value == null ? "" : value);
        }
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("python fixture generation failed: " + output);
        }
    }

    private String embeddingVector() {
        return "[0.11,0.22,0.33,0.44,0.55,0.66,0.77,0.88,0.12,0.23,0.34,0.45,0.56,0.67,0.78,0.89]";
    }

    private void stubLlmDefaults() {
        when(aiModelGateway.readConfigOrDefault(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1, String.class));
        when(aiModelGateway.readConfigOrDefault(eq("LLM_MODEL"), eq("MiniMax-M2.7")))
                .thenReturn("MiniMax-M2.7");
    }

    private void stubJudgeCheckDefaults() {
        when(judgeCheckService.executeReferenceSolution(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    List<String> inputs = invocation.getArgument(2);
                    List<com.alethicode.service.languagepack.impl.JudgeCheckResult.CaseResult> results = new java.util.ArrayList<>();
                    for (int i = 0; i < inputs.size(); i++) {
                        results.add(new com.alethicode.service.languagepack.impl.JudgeCheckResult.CaseResult(
                                i, true, "judge_output_" + i, "", 0
                        ));
                    }
                    return new com.alethicode.service.languagepack.impl.JudgeCheckResult(true, results, "");
                });
    }

    private com.alethicode.service.languagepack.impl.JudgeCheckResult buildAllPassedResult(List<String> inputs, List<String> outputs) {
        List<com.alethicode.service.languagepack.impl.JudgeCheckResult.CaseResult> results = new java.util.ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            results.add(new com.alethicode.service.languagepack.impl.JudgeCheckResult.CaseResult(
                    i, true, outputs.get(i), "", 0
            ));
        }
        return new com.alethicode.service.languagepack.impl.JudgeCheckResult(true, results, "");
    }

    private com.alethicode.service.languagepack.impl.JudgeCheckResult buildPartialFailResult(int totalCases, List<Integer> failedIndices) {
        List<com.alethicode.service.languagepack.impl.JudgeCheckResult.CaseResult> results = new java.util.ArrayList<>();
        for (int i = 0; i < totalCases; i++) {
            if (failedIndices.contains(i)) {
                results.add(new com.alethicode.service.languagepack.impl.JudgeCheckResult.CaseResult(
                        i, false, "", "Runtime Error", 4
                ));
            } else {
                results.add(new com.alethicode.service.languagepack.impl.JudgeCheckResult.CaseResult(
                        i, true, "output_" + i, "", 0
                ));
            }
        }
        return new com.alethicode.service.languagepack.impl.JudgeCheckResult(false, results, "");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ValidationFixture(Long languagePackId,
                                     Long taskId,
                                     Long documentId,
                                     Long kcId,
                                     Long exampleId,
                                     String sourceSignature) {
    }
}

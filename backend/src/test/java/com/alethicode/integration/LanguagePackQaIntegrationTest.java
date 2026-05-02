package com.alethicode.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
class LanguagePackQaIntegrationTest extends AbstractJdbcIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JudgeServerService judgeServerService;
    @MockBean private SystemAdminService systemAdminService;
    @MockBean private ReleaseNotesService releaseNotesService;
    @MockBean private PlatformConfigService platformConfigService;
    @MockBean private SystemOptionService systemOptionService;
    @MockBean private AdminUploadService adminUploadService;
    @MockBean private AiModelGateway aiModelGateway;

    private Long rootUserId;
    private Long studentUserId;
    private Long visibleReadyPackId;
    private Long visibleMissingEmbeddingPackId;
    private Long hiddenReadyPackId;
    private Long readyDocumentId;

    @BeforeEach
    void setUp() {
        rootUserId = insertUser("root", "Admin");
        studentUserId = insertUser("student", "User");

        visibleReadyPackId = insertLanguagePack("python-basic", "Python 基础");
        visibleMissingEmbeddingPackId = insertLanguagePack("python-strings", "Python 字符串");
        hiddenReadyPackId = insertLanguagePack("python-hidden", "Python 隐藏包");

        String readyClassroomId = "class-qa-ready";
        String missingClassroomId = "class-qa-missing";
        jdbcTemplate.update(
                """
                INSERT INTO classroom(id, name, created_by_id, is_active, create_time, update_time)
                VALUES (?, ?, ?, true, now(), now()), (?, ?, ?, true, now(), now())
                """,
                readyClassroomId,
                "QA Ready Class",
                rootUserId,
                missingClassroomId,
                "QA Missing Class",
                rootUserId
        );
        jdbcTemplate.update(
                """
                INSERT INTO classroom_member(id, classroom_id, user_id, role, join_time, update_time)
                VALUES (?, ?, ?, 'student', now(), now()),
                       (?, ?, ?, 'student', now(), now())
                """,
                "member-qa-ready",
                readyClassroomId,
                studentUserId,
                "member-qa-missing",
                missingClassroomId,
                studentUserId
        );
        jdbcTemplate.update(
                """
                INSERT INTO classroom_language_pack(classroom_id, language_pack_id, create_time)
                VALUES (?, ?, now()), (?, ?, now())
                """,
                readyClassroomId,
                visibleReadyPackId,
                missingClassroomId,
                visibleMissingEmbeddingPackId
        );

        Long readyTaskId = insertInitTask(visibleReadyPackId);
        readyDocumentId = insertDocument(readyTaskId, visibleReadyPackId, "intro.pdf", "/tmp/lpqa-ready-preview.pdf");
        insertPage(readyDocumentId, visibleReadyPackId, 2, "变量", "变量是用来保存数据的名称。", "/tmp/lpqa-ready-preview.pdf", true);
        insertPage(readyDocumentId, visibleReadyPackId, 3, "列表", "列表可以保存多个元素。", "/tmp/lpqa-ready-preview.pdf", true);

        Long missingTaskId = insertInitTask(visibleMissingEmbeddingPackId);
        Long missingDocumentId = insertDocument(missingTaskId, visibleMissingEmbeddingPackId, "strings.pdf", "/tmp/lpqa-missing-preview.pdf");
        insertPage(missingDocumentId, visibleMissingEmbeddingPackId, 1, "字符串", "字符串可以保存文本。", "/tmp/lpqa-missing-preview.pdf", false);

        Long hiddenTaskId = insertInitTask(hiddenReadyPackId);
        Long hiddenDocumentId = insertDocument(hiddenTaskId, hiddenReadyPackId, "hidden.pdf", "/tmp/lpqa-hidden-preview.pdf");
        insertPage(hiddenDocumentId, hiddenReadyPackId, 1, "隐藏", "这个页面不应该被学生看到。", "/tmp/lpqa-hidden-preview.pdf", true);

        // Phase 3 切流：callForEmbedding 已从 AiModelGateway 删除，本 stub 一并清理。
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "answer_markdown", "变量是用于保存数据的名称。",
                "grounded", true,
                "insufficient_evidence", false,
                "citations", List.of(
                        Map.of(
                                "document_id", readyDocumentId,
                                "document_title", "intro.pdf",
                                "page_no", 2,
                                "excerpt", "变量是用来保存数据的名称。",
                                "confidence", 0.98
                        )
                )
        ));
    }

    @Test
    void packsEndpointShouldOnlyReturnVisibleQaReadyPacks() throws Exception {
        mockMvc.perform(get("/api/language-pack-qa/packs")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(visibleReadyPackId.intValue()))
                .andExpect(jsonPath("$.data[0].name").value("Python 基础"));
    }

    @Test
    void createSessionShouldRejectInvisibleOrNotReadyPack() throws Exception {
        mockMvc.perform(post("/api/language-pack-qa/sessions")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language_pack_id": %d
                                }
                                """.formatted(hiddenReadyPackId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("permission-denied"));

        mockMvc.perform(post("/api/language-pack-qa/sessions")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language_pack_id": %d
                                }
                                """.formatted(visibleMissingEmbeddingPackId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
    }

    @Test
    void sessionMessageAndCitationFlowShouldStayInsideCurrentPack() throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/language-pack-qa/sessions")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language_pack_id": %d
                                }
                                """.formatted(visibleReadyPackId)))
                .andExpect(status().isOk())
                .andReturn();
        long sessionId = readLong(sessionResult, "/data/id");

        mockMvc.perform(post("/api/language-pack-qa/sessions/" + sessionId + "/messages")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "变量是什么？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.answer_json.grounded").value(true))
                .andExpect(jsonPath("$.data.answer_json.insufficient_evidence").value(false))
                .andExpect(jsonPath("$.data.answer_json.citations[0].document_id").value(readyDocumentId.intValue()))
                .andExpect(jsonPath("$.data.answer_json.citations[0].page_no").value(2));

        mockMvc.perform(get("/api/language-pack-qa/sessions/" + sessionId + "/messages")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[1].answer_json.citations[0].page_no").value(2));

        mockMvc.perform(get("/api/language-pack-qa/packs/" + visibleReadyPackId + "/documents/" + readyDocumentId + "/pages/2")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.document_id").value(readyDocumentId.intValue()))
                .andExpect(jsonPath("$.data.page_no").value(2))
                .andExpect(jsonPath("$.data.preview_url").value("/api/language-pack-qa/packs/" + visibleReadyPackId + "/documents/" + readyDocumentId + "/preview"));
    }

    @Test
    void noEvidenceShouldReturnGroundedRefusal() throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/language-pack-qa/sessions")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language_pack_id": %d
                                }
                                """.formatted(visibleReadyPackId)))
                .andExpect(status().isOk())
                .andReturn();
        long sessionId = readLong(sessionResult, "/data/id");

        mockMvc.perform(post("/api/language-pack-qa/sessions/" + sessionId + "/messages")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "量子力学是什么？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.answer_json.grounded").value(false))
                .andExpect(jsonPath("$.data.answer_json.insufficient_evidence").value(true))
                .andExpect(jsonPath("$.data.answer_json.citations").isArray())
                .andExpect(jsonPath("$.data.answer_json.citations.length()").value(0));
    }

    @Test
    void sessionTitleShouldRefreshDuringEarlyDialogueTurns() throws Exception {
        when(aiModelGateway.callForJson(startsWith("你是一个对话标题生成器"), anyString()))
                .thenReturn(Map.of("title", "变量基础问答"))
                .thenReturn(Map.of("title", "变量与循环综合答疑"));

        MvcResult sessionResult = mockMvc.perform(post("/api/language-pack-qa/sessions")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language_pack_id": %d
                                }
                                """.formatted(visibleReadyPackId)))
                .andExpect(status().isOk())
                .andReturn();
        long sessionId = readLong(sessionResult, "/data/id");

        mockMvc.perform(post("/api/language-pack-qa/sessions/" + sessionId + "/messages")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "变量是什么？"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/language-pack-qa/sessions/" + sessionId + "/messages")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "for 循环里变量是怎么变化的？"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/language-pack-qa/sessions")
                        .param("language_pack_id", String.valueOf(visibleReadyPackId))
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(Math.toIntExact(sessionId)))
                .andExpect(jsonPath("$.data[0].title").value("变量与循环综合答疑"));
    }

    @Test
    void listMessagesShouldBackfillCitationsFromRetrievalLogWhenAssistantPayloadMissesThem() throws Exception {
        Long sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_chat_session(user_id, language_pack_id, status, create_time, update_time)
                VALUES (?, ?, 'active', now(), now())
                RETURNING id
                """,
                Long.class,
                studentUserId,
                visibleReadyPackId
        );

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_chat_message(session_id, role, content, create_time)
                VALUES (?, 'user', ?, now() - interval '2 seconds')
                """,
                sessionId,
                "for 循环的语法是什么？"
        );

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_chat_retrieval_log(session_id, query_text, page_hit_json, create_time)
                VALUES (?, ?, cast(? as jsonb), now() - interval '1 seconds')
                """,
                sessionId,
                "for 循环的语法是什么？",
                """
                [
                  {
                    "page_id": 10951,
                    "document_id": %d,
                    "document_title": "intro.pdf",
                    "page_no": 2,
                    "page_title": "变量",
                    "excerpt": "变量是用来保存数据的名称。",
                    "page_text": "变量是用来保存数据的名称。",
                    "preview_asset_path": "/tmp/lpqa-ready-preview.pdf",
                    "score": 0.98
                  }
                ]
                """.formatted(readyDocumentId)
        );

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_chat_message(session_id, role, content, answer_json, create_time)
                VALUES (?, 'assistant', ?, cast(? as jsonb), now())
                """,
                sessionId,
                "变量是用于保存数据的名称。",
                """
                {
                  "answer_markdown": "变量是用于保存数据的名称。",
                  "grounded": false,
                  "citations": [],
                  "insufficient_evidence": false
                }
                """
        );

        mockMvc.perform(get("/api/language-pack-qa/sessions/" + sessionId + "/messages")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].answer_json.citations.length()").value(1))
                .andExpect(jsonPath("$.data[1].answer_json.citations[0].document_id").value(readyDocumentId.intValue()))
                .andExpect(jsonPath("$.data[1].answer_json.citations[0].page_no").value(2))
                .andExpect(jsonPath("$.data[1].answer_json.citations[0].document_title").value("intro.pdf"));
    }

    @Test
    void feedbackShouldPersistForAssistantMessageOnly() throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/language-pack-qa/sessions")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language_pack_id": %d
                                }
                                """.formatted(visibleReadyPackId)))
                .andExpect(status().isOk())
                .andReturn();
        long sessionId = readLong(sessionResult, "/data/id");

        MvcResult answerResult = mockMvc.perform(post("/api/language-pack-qa/sessions/" + sessionId + "/messages")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "变量是什么？"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long messageId = readLong(answerResult, "/data/id");

        mockMvc.perform(post("/api/language-pack-qa/messages/" + messageId + "/feedback")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "feedback_label": "helpful",
                                  "comment": "这次解释很清楚"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        Integer feedbackCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_chat_feedback WHERE message_id = ? AND feedback_label = 'helpful'",
                Integer.class,
                messageId
        );
        org.assertj.core.api.Assertions.assertThat(feedbackCount).isEqualTo(1);
    }

    @Test
    void ojProblemSolvingQuestionShouldBeBlockedBeforeRetrieval() throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/language-pack-qa/sessions")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language_pack_id": %d
                                }
                                """.formatted(visibleReadyPackId)))
                .andExpect(status().isOk())
                .andReturn();
        long sessionId = readLong(sessionResult, "/data/id");

        mockMvc.perform(post("/api/language-pack-qa/sessions/" + sessionId + "/messages")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "这道 OJ 题怎么写？输入描述和输出描述都看不懂，直接给我完整代码。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.answer_json.grounded").value(false))
                .andExpect(jsonPath("$.data.answer_json.insufficient_evidence").value(true))
                .andExpect(jsonPath("$.data.answer_json.refusal_reason").value("oj_problem_question"))
                .andExpect(jsonPath("$.data.answer_json.citations.length()").value(0));

        Integer retrievalCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_chat_retrieval_log WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        org.assertj.core.api.Assertions.assertThat(retrievalCount).isEqualTo(0);
    }

    private long readLong(MvcResult result, String pointer) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.at(pointer).asLong();
    }

    private Long insertUser(String username, String adminType) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO "user"(username, admin_type, create_time)
                VALUES (?, ?, now())
                RETURNING id
                """,
                Long.class,
                username,
                adminType
        );
    }

    private Long insertLanguagePack(String slug, String name) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES (?, 1, ?, 'Python3', 'published', now(), now())
                RETURNING id
                """,
                Long.class,
                slug,
                name
        );
    }

    private Long insertInitTask(Long languagePackId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                VALUES (?, 'published', false, now(), now())
                RETURNING id
                """,
                Long.class,
                languagePackId
        );
    }

    private Long insertDocument(Long taskId, Long languagePackId, String originalFilename, String previewPdfPath) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_document(
                    init_task_id, language_pack_id, original_filename, original_path, canonical_path,
                    preview_pdf_path, file_hash, file_size_bytes, page_count, status, create_time, update_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 1024, 2, 'normalized', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                languagePackId,
                originalFilename,
                "/tmp/" + originalFilename,
                "/tmp/" + originalFilename,
                previewPdfPath,
                originalFilename + "-hash"
        );
    }

    private void insertPage(Long documentId,
                            Long languagePackId,
                            int pageNo,
                            String pageTitle,
                            String pageText,
                            String previewAssetPath,
                            boolean withEmbedding) {
        if (withEmbedding) {
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_page(
                        document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                        text_hash, preview_asset_path, excerpt, search_tsv, page_embedding, embedding_updated_at, create_time
                    )
                    VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, to_tsvector('simple', ?), cast(? as vector), now(), now())
                    """,
                    documentId,
                    languagePackId,
                    pageNo,
                    pageTitle,
                    pageText,
                    "hash-" + documentId + "-" + pageNo,
                    previewAssetPath,
                    pageText,
                    pageText,
                    "[0.11,0.12,0.13,0.14,0.15,0.16,0.17,0.18,0.19,0.20,0.21,0.22,0.23,0.24,0.25,0.26]"
            );
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_page(
                    document_id, language_pack_id, page_no, chunk_index, page_title, page_text,
                    text_hash, preview_asset_path, excerpt, search_tsv, create_time
                )
                VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, to_tsvector('simple', ?), now())
                """,
                documentId,
                languagePackId,
                pageNo,
                pageTitle,
                pageText,
                "hash-" + documentId + "-" + pageNo,
                previewAssetPath,
                pageText,
                pageText
        );
    }
}

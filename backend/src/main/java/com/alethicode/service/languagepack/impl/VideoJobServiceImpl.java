package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.exception.BadRequestException;
import com.alethicode.service.ai.AiCircuitBreaker;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.VideoJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Transactional(rollbackFor = Exception.class)
public class VideoJobServiceImpl implements VideoJobService {

    private static final Logger log = LoggerFactory.getLogger(VideoJobServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiModelGateway aiModelGateway;
    private final AlethicodeProperties properties;
    private final AiCircuitBreaker aiCircuitBreaker;
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public VideoJobServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                               AiModelGateway aiModelGateway, AlethicodeProperties properties,
                               AiCircuitBreaker aiCircuitBreaker) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiModelGateway = aiModelGateway;
        this.properties = properties;
        this.aiCircuitBreaker = aiCircuitBreaker;
    }

    @Override
    public Map<String, Object> createOrReuse(String username, Long messageId) {
        requireAdmin(username);
        Long userId = requireUserId(username);

        Map<String, Object> existing = findJobByMessageId(messageId);
        if (existing != null) {
            requireOwnership(existing, userId);
            return existing;
        }

        Map<String, Object> message = loadMessage(messageId, userId);
        String answerJson = (String) message.get("answer_json_text");
        if (answerJson == null || answerJson.isBlank()) {
            throw new BadRequestException("该消息没有回答内容，无法生成视频");
        }
        Map<String, Object> answer = parseJson(answerJson);
        boolean grounded = Boolean.TRUE.equals(answer.get("grounded"));
        boolean insufficient = Boolean.TRUE.equals(answer.get("insufficient_evidence"));
        if (!grounded || insufficient) {
            throw new BadRequestException("仅支持有证据的回答生成视频");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> citations = (List<Map<String, Object>>) answer.getOrDefault("citations", List.of());
        if (citations.isEmpty()) {
            throw new BadRequestException("该回答没有引用页，无法生成视频");
        }
        List<Map<String, Object>> topCitations = citations.size() > 3
                ? new ArrayList<>(citations.subList(0, 3)) : new ArrayList<>(citations);

        Long sessionId = ((Number) message.get("session_id")).longValue();
        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_chat_session WHERE id = ?",
                Long.class, sessionId);

        String questionText = findQuestionBeforeMessage(sessionId, messageId);
        String answerMarkdown = String.valueOf(answer.getOrDefault("answer_markdown", ""));

        Long jobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_video_job(
                    session_id, message_id, user_id, language_pack_id,
                    status, question_text, answer_markdown,
                    source_citations_json, create_time, update_time
                ) VALUES (?, ?, ?, ?, 'queued', ?, ?, CAST(? AS jsonb), now(), now())
                RETURNING id
                """,
                Long.class,
                sessionId, messageId, userId, languagePackId,
                questionText, answerMarkdown, toJson(topCitations)
        );

        Map<String, Object> job = findJobById(jobId);

        asyncExecutor.submit(() -> {
            try {
                executeVideoGeneration(jobId, languagePackId, questionText, answerMarkdown, topCitations);
            } catch (Exception e) {
                log.error("Video job {} failed: {}", jobId, e.getMessage(), e);
                failJob(jobId, e.getMessage());
            }
        });

        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getJob(String username, Long jobId) {
        Long userId = requireUserId(username);
        Map<String, Object> job = findJobById(jobId);
        if (job == null) {
            throw new BadRequestException("视频任务不存在");
        }
        requireOwnership(job, userId);
        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getJobByMessageId(Long messageId) {
        return findJobByMessageId(messageId);
    }

    private void executeVideoGeneration(Long jobId, Long languagePackId,
                                        String question, String answer,
                                        List<Map<String, Object>> citations) {
        updateJobStatus(jobId, "planning", 10);

        List<Map<String, Object>> citationPages = loadCitationPageDetails(languagePackId, citations);
        Map<String, Object> storyboard = generateStoryboard(question, answer, citationPages);
        updateJobStoryboard(jobId, storyboard, 30);

        updateJobStatus(jobId, "rendering", 50);

        String ttsProvider = aiModelGateway.readConfigOrDefault("VIDEO_TTS_PROVIDER", "stub");
        String renderProvider = aiModelGateway.readConfigOrDefault("VIDEO_RENDER_PROVIDER", "stub");

        Map<String, Object> renderResult;
        if ("stub".equals(renderProvider)) {
            renderResult = stubRender(jobId, storyboard);
        } else {
            renderResult = callExternalRender(jobId, storyboard, ttsProvider, renderProvider);
        }

        String videoPath = String.valueOf(renderResult.getOrDefault("video_path", ""));
        String posterPath = String.valueOf(renderResult.getOrDefault("poster_path", ""));
        int durationSeconds = ((Number) renderResult.getOrDefault("duration_seconds", 60)).intValue();

        jdbcTemplate.update(
                """
                UPDATE language_pack_video_job
                SET status = 'completed', progress_percent = 100,
                    video_path = ?, poster_path = ?, duration_seconds = ?,
                    provider_name = ?, update_time = now(), completed_time = now()
                WHERE id = ?
                """,
                videoPath, posterPath, durationSeconds, renderProvider, jobId
        );
        log.info("Video job {} completed: video={}, duration={}s", jobId, videoPath, durationSeconds);
    }

    private Map<String, Object> generateStoryboard(String question, String answer,
                                                    List<Map<String, Object>> citationPages) {
        String pagesContext = citationPages.stream()
                .map(p -> "【%s 第%s页】\n%s".formatted(
                        p.getOrDefault("document_title", ""),
                        p.getOrDefault("page_no", ""),
                        abbreviate(String.valueOf(p.getOrDefault("page_text", "")), 800)))
                .reduce("", (a, b) -> a + "\n\n" + b);

        return aiModelGateway.callForJson(
                """
                你是课件讲解视频分镜脚本生成器。
                根据学生问题、AI回答和引用的课件页内容，生成一份 4-7 个 scene 的分镜脚本。
                总时长 45-90 秒。每个 scene 必须绑定至少一个 citation page，不允许脱离证据自由发挥。
                
                输出 JSON：
                {
                  "total_duration_seconds": 60,
                  "scenes": [
                    {
                      "scene_index": 1,
                      "title": "场景标题",
                      "narration_text": "旁白文本（中文）",
                      "duration_seconds": 12,
                      "bullets": ["要点1", "要点2"],
                      "citation_page": {"document_id": 123, "page_no": 5},
                      "visual_focus": "页面中的重点区域描述"
                    }
                  ]
                }
                """,
                """
                【学生问题】
                %s

                【AI 回答】
                %s

                【引用课件页内容】
                %s
                """.formatted(question, abbreviate(answer, 1500), pagesContext)
        );
    }

    private List<Map<String, Object>> loadCitationPageDetails(Long languagePackId,
                                                              List<Map<String, Object>> citations) {
        List<Map<String, Object>> pages = new ArrayList<>();
        for (Map<String, Object> citation : citations) {
            Long documentId = toLong(citation.get("document_id"));
            Integer pageNo = toInt(citation.get("page_no"));
            if (documentId == null || pageNo == null) continue;
            try {
                Map<String, Object> page = jdbcTemplate.queryForMap(
                        """
                        SELECT p.page_text, p.excerpt, p.page_title, p.preview_asset_path,
                               d.original_filename AS document_title
                        FROM language_pack_page p
                        JOIN language_pack_document d ON d.id = p.document_id
                        WHERE p.document_id = ? AND p.page_no = ? AND p.language_pack_id = ?
                        """,
                        documentId, pageNo, languagePackId
                );
                page.put("document_id", documentId);
                page.put("page_no", pageNo);
                pages.add(page);
            } catch (EmptyResultDataAccessException ignored) {}
        }
        return pages;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stubRender(Long jobId, Map<String, Object> storyboard) {
        int totalDuration = ((Number) storyboard.getOrDefault("total_duration_seconds", 60)).intValue();
        Path videoDir = Path.of(properties.getSystem().getUploadDir(), "video_jobs");
        try {
            Files.createDirectories(videoDir);
            Path stubMarker = videoDir.resolve(jobId + "_stub.txt");
            Files.writeString(stubMarker, "stub video placeholder — no real render provider configured");
        } catch (Exception e) {
            log.warn("stubRender: failed to write stub marker for jobId={} under {}", jobId, videoDir, e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("video_path", "");
        result.put("poster_path", "");
        result.put("duration_seconds", totalDuration);
        result.put("stub", true);
        return result;
    }

    private Map<String, Object> callExternalRender(Long jobId, Map<String, Object> storyboard,
                                                    String ttsProvider, String renderProvider) {
        String renderApiUrl = aiModelGateway.readRequiredConfig("VIDEO_RENDER_API_URL");
        String renderApiKey = aiModelGateway.readRequiredConfig("VIDEO_RENDER_API_KEY");

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("storyboard", storyboard);
            requestBody.put("tts_provider", ttsProvider);
            requestBody.put("output_format", "mp4");
            requestBody.put("resolution", "1920x1080");

            HttpResponse<String> response = aiCircuitBreaker.executeWithInstance("videoProvider", "video render", () -> {
                HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(renderApiUrl))
                        .timeout(Duration.ofMinutes(10))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + renderApiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                        .build();
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            });
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Render API error: HTTP " + response.statusCode());
            }

            Map<String, Object> renderResponse = parseJson(response.body());
            String videoUrl = String.valueOf(renderResponse.get("video_url"));
            String posterUrl = String.valueOf(renderResponse.getOrDefault("poster_url", ""));

            String localVideoPath = downloadToLocal(videoUrl, jobId, "mp4");
            String localPosterPath = posterUrl.isBlank() ? "" : downloadToLocal(posterUrl, jobId, "jpg");
            int duration = ((Number) renderResponse.getOrDefault("duration_seconds", 60)).intValue();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("video_path", localVideoPath);
            result.put("poster_path", localPosterPath);
            result.put("duration_seconds", duration);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Video render failed: " + e.getMessage(), e);
        }
    }

    private String downloadToLocal(String url, Long jobId, String extension) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalStateException("Video download blocked: unsupported scheme '" + scheme + "'");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()
                || host.startsWith("127.") || "localhost".equalsIgnoreCase(host)
                || host.startsWith("10.") || host.startsWith("192.168.")
                || host.startsWith("169.254.") || host.startsWith("0.")) {
            throw new IllegalStateException("Video download blocked: private/local host '" + host + "'");
        }
        try {
            Path videoDir = Path.of(properties.getSystem().getUploadDir(), "video_jobs");
            Files.createDirectories(videoDir);
            Path localFile = videoDir.resolve(jobId + "." + extension);
            try (InputStream in = aiCircuitBreaker.executeWithInstance("videoProvider", "video asset download", () -> uri.toURL().openStream())) {
                Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/public/upload/video_jobs/" + localFile.getFileName();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to download video asset: " + e.getMessage(), e);
        }
    }

    private void updateJobStatus(Long jobId, String status, int progressPercent) {
        jdbcTemplate.update(
                "UPDATE language_pack_video_job SET status = ?, progress_percent = ?, update_time = now() WHERE id = ?",
                status, progressPercent, jobId);
    }

    private void updateJobStoryboard(Long jobId, Map<String, Object> storyboard, int progressPercent) {
        jdbcTemplate.update(
                "UPDATE language_pack_video_job SET storyboard_json = CAST(? AS jsonb), progress_percent = ?, update_time = now() WHERE id = ?",
                toJson(storyboard), progressPercent, jobId);
    }

    private void failJob(Long jobId, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE language_pack_video_job SET status = 'failed', error_message = ?, update_time = now() WHERE id = ?",
                abbreviate(errorMessage, 2000), jobId);
    }

    private Map<String, Object> findJobById(Long jobId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT id, session_id, message_id, user_id, language_pack_id,
                           status, progress_percent,
                           duration_seconds,
                           create_time, update_time, completed_time
                    FROM language_pack_video_job WHERE id = ?
                    """, jobId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Map<String, Object> findJobByMessageId(Long messageId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT id, session_id, message_id, user_id, language_pack_id,
                           status, progress_percent,
                           duration_seconds,
                           create_time, update_time, completed_time
                    FROM language_pack_video_job WHERE message_id = ?
                    """, messageId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Map<String, Object> loadMessage(Long messageId, Long userId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT m.id, m.session_id, m.role, m.content,
                           m.answer_json::text AS answer_json_text
                    FROM language_pack_chat_message m
                    JOIN language_pack_chat_session s ON s.id = m.session_id
                    WHERE m.id = ? AND s.user_id = ? AND m.role = 'assistant'
                    """, messageId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new BadRequestException("消息不存在或无权访问");
        }
    }

    private String findQuestionBeforeMessage(Long sessionId, Long messageId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT content FROM language_pack_chat_message
                    WHERE session_id = ? AND role = 'user' AND id < ?
                    ORDER BY id DESC LIMIT 1
                    """, String.class, sessionId, messageId);
        } catch (EmptyResultDataAccessException e) {
            return "";
        }
    }

    private Long requireUserId(String username) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("请先登录");
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM \"user\" WHERE username = ?", Long.class, username);
        } catch (EmptyResultDataAccessException e) {
            throw new BadRequestException("用户不存在");
        }
    }

    private void requireAdmin(String username) {
        try {
            String adminType = jdbcTemplate.queryForObject(
                    "SELECT admin_type FROM \"user\" WHERE username = ?",
                    String.class, username);
            if (!"Admin".equals(adminType) && !"Teacher".equals(adminType)) {
                throw new BadRequestException("仅管理员可使用视频生成功能（beta）");
            }
        } catch (EmptyResultDataAccessException e) {
            throw new BadRequestException("用户不存在");
        }
    }

    private void requireOwnership(Map<String, Object> job, Long userId) {
        Long jobUserId = ((Number) job.get("user_id")).longValue();
        if (!jobUserId.equals(userId)) {
            throw new BadRequestException("无权访问该视频任务");
        }
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("JSON serialize failed", e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { throw new IllegalStateException("JSON parse failed: " + e.getMessage(), e); }
    }

    private String abbreviate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return null;
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }
}

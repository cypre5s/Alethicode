package com.alethicode.service.classroom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClassroomLessonService {

    public record LessonFile(byte[] bytes, String contentType, String filename) {}

    private static final Logger log = LoggerFactory.getLogger(ClassroomLessonService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private final Path lessonRoot;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;

    public ClassroomLessonService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AlethicodeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.lessonRoot = Paths.get(properties.getSystem().getClassroomLessonDir());
    }

    // ── public API ──────────────────────────────────────────

    public ApiResponse<Object> lessonList(String classroomId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isMember(classroomId, user.userId())) {
            return ApiResponse.success(Map.of("results", List.of(), "total", 0));
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select cl.id, cl.title, cl.description, cl.lesson_type, cl.file_path, cl.file_size, cl.total_pages,
                       cl.table_of_contents::text as toc_json, cl.display_order, cl.create_time, cl.update_time,
                       (select count(*) from ai_generated_problem agp
                        where agp.lesson_id = cl.id and agp.is_published = true) as linked_problems_count
                from classroom_lesson cl
                where cl.classroom_id = ?
                order by cl.display_order asc, cl.create_time desc
                """,
                (rs, rowNum) -> mapLessonRow(rs, null),
                classroomId
        );
        return ApiResponse.success(Map.of("results", rows, "total", rows.size()));
    }

    public ApiResponse<Object> lessonRetrieve(String classroomId, String lessonId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Classroom not found");
        }
        Map<String, Object> row = lessonRow(classroomId, lessonId);
        if (row == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Lesson not found");
        }
        return ApiResponse.success(row);
    }

    public ApiResponse<Object> lessonCreate(String classroomId, MultipartFile file, String title, String notes, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可上传课件");
        }
        if (file == null || file.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "请上传文件");
        }

        String ext = extension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        String lessonType = switch (ext) {
            case ".pdf" -> "pdf";
            case ".ppt", ".pptx" -> "ppt";
            case ".doc", ".docx" -> "doc";
            case ".md", ".markdown" -> "markdown";
            default -> null;
        };
        if (lessonType == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "不支持的文件类型: " + ext);
        }

        String lessonId = randomId();
        String safeName = randomId() + ext;
        Path folder = lessonRoot.resolve(classroomId);
        try {
            Files.createDirectories(folder);
            Path filePath = folder.resolve(safeName);
            byte[] fileBytes = file.getBytes();
            Files.write(filePath, fileBytes);

            int totalPages = countFilePages(fileBytes, ext);

            int nextOrder = jdbcTemplate.queryForObject(
                    "select coalesce(max(display_order), -1) + 1 from classroom_lesson where classroom_id = ?",
                    Integer.class,
                    classroomId
            );

            jdbcTemplate.update(
                    """
                    insert into classroom_lesson(id, classroom_id, title, description, lesson_type,
                                                 file_path, file_size, total_pages, table_of_contents,
                                                 display_order, created_by_id, create_time, update_time)
                    values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, now(), now())
                    """,
                    lessonId,
                    classroomId,
                    trimToNull(title) == null ? trimToEmpty(file.getOriginalFilename()) : title,
                    trimToNull(notes),
                    lessonType,
                    classroomId + "/" + safeName,
                    file.getSize(),
                    totalPages,
                    "[]",
                    nextOrder,
                    user.userId()
            );
            jdbcTemplate.update("update classroom set lesson_count = lesson_count + 1, update_time = now() where id = ?", classroomId);
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "课件上传失败");
        }

        return ApiResponse.success(lessonRow(classroomId, lessonId));
    }

    public ApiResponse<Object> lessonDelete(String classroomId, String lessonId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可删除课件");
        }

        String filePath = jdbcTemplate.query(
                "select file_path from classroom_lesson where classroom_id = ? and id = ?",
                (rs, rowNum) -> rs.getString("file_path"),
                classroomId,
                lessonId
        ).stream().findFirst().orElse(null);

        int deleted = jdbcTemplate.update("delete from classroom_lesson where classroom_id = ? and id = ?", classroomId, lessonId);
        if (deleted == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Lesson not found");
        }
        jdbcTemplate.update("update classroom set lesson_count = greatest(lesson_count - 1, 0), update_time = now() where id = ?", classroomId);
        if (filePath != null) {
            Path full = lessonRoot.resolve(filePath).normalize();
            try {
                Files.deleteIfExists(full);
            } catch (IOException ignored) {
            }
        }
        return ApiResponse.success(Map.of("message", "删除成功"));
    }

    public LessonFile lessonDownload(String classroomId, String lessonId, Authentication authentication) {
        return lessonFile(classroomId, lessonId, authentication, true);
    }

    public LessonFile lessonView(String classroomId, String lessonId, Authentication authentication) {
        return lessonFile(classroomId, lessonId, authentication, false);
    }

    // ── private helpers ─────────────────────────────────────

    private LessonFile lessonFile(String classroomId, String lessonId, Authentication authentication, boolean download) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated() || !isMember(classroomId, user.userId())) {
            return null;
        }
        Map<String, Object> row = jdbcTemplate.query(
                "select title, lesson_type, file_path from classroom_lesson where classroom_id = ? and id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", rs.getString("title"));
                    item.put("lesson_type", rs.getString("lesson_type"));
                    item.put("file_path", rs.getString("file_path"));
                    return item;
                },
                classroomId,
                lessonId
        ).stream().findFirst().orElse(null);
        if (row == null) {
            return null;
        }
        Path path = lessonRoot.resolve(trimToEmpty(stringValue(row.get("file_path")))).normalize();
        try {
            if (!Files.exists(path)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            String lessonType = trimToEmpty(stringValue(row.get("lesson_type")));
            String contentType = "pdf".equals(lessonType)
                    ? "application/pdf"
                    : "application/octet-stream";
            String filename = trimToEmpty(stringValue(row.get("title")));
            if (download && !filename.contains(".")) {
                filename = filename + extensionByType(lessonType);
            }
            return new LessonFile(bytes, contentType, filename);
        } catch (IOException ignored) {
            return null;
        }
    }

    Map<String, Object> lessonRow(String classroomId, String lessonId) {
        return jdbcTemplate.query(
                """
                select cl.id, cl.title, cl.description, cl.lesson_type, cl.file_path, cl.file_size, cl.total_pages,
                       cl.table_of_contents::text as toc_json, cl.display_order, cl.create_time, cl.update_time,
                       (select count(*) from ai_generated_problem agp
                        where agp.lesson_id = cl.id and agp.is_published = true) as linked_problems_count
                from classroom_lesson cl
                where cl.classroom_id = ? and cl.id = ?
                """,
                (rs, rowNum) -> mapLessonRow(rs, classroomId),
                classroomId,
                lessonId
        ).stream().findFirst().orElse(null);
    }

    private Map<String, Object> mapLessonRow(java.sql.ResultSet rs, String classroomId) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getString("id"));
        if (classroomId != null) {
            item.put("classroom", classroomId);
        }
        item.put("title", rs.getString("title"));
        item.put("description", rs.getString("description"));
        item.put("lesson_type", rs.getString("lesson_type"));
        item.put("lesson_type_display", rs.getString("lesson_type"));
        item.put("file_type", rs.getString("lesson_type"));
        item.put("file_size", rs.getLong("file_size"));
        item.put("file_size_display", humanSize(rs.getLong("file_size")));
        item.put("total_pages", rs.getInt("total_pages"));
        item.put("table_of_contents", parseJsonList(rs.getString("toc_json")));
        item.put("display_order", rs.getInt("display_order"));
        item.put("linked_problems_count", rs.getInt("linked_problems_count"));
        item.put("vision_analyzed_pages", 0);
        item.put("create_time", formatTime(rs.getTimestamp("create_time")));
        item.put("uploaded_at", formatTime(rs.getTimestamp("create_time")));
        item.put("update_time", formatTime(rs.getTimestamp("update_time")));
        item.put("_file_path", rs.getString("file_path"));
        Map<String, Object> healed = healLegacyPptPageCount(item);
        healed.remove("_file_path");
        return healed;
    }

    private Map<String, Object> healLegacyPptPageCount(Map<String, Object> lesson) {
        String lessonType = trimToEmpty(stringValue(lesson.get("lesson_type")));
        int storedPages = parseIntObj(lesson.get("total_pages"), 0);
        if (!"ppt".equals(lessonType) || storedPages > 1) {
            return lesson;
        }

        String lessonId = trimToEmpty(stringValue(lesson.get("id")));
        String relativePath = trimToNull(stringValue(lesson.get("_file_path")));
        int actualPages = recountStoredLessonPages(relativePath);
        if (actualPages <= 1 || actualPages == storedPages) {
            return lesson;
        }

        lesson.put("total_pages", actualPages);
        if (!lessonId.isBlank()) {
            jdbcTemplate.update(
                    "update classroom_lesson set total_pages = ?, update_time = now() where id = ? and total_pages <> ?",
                    actualPages,
                    lessonId,
                    actualPages
            );
        }
        return lesson;
    }

    private int recountStoredLessonPages(String relativePath) {
        String normalizedPath = trimToNull(relativePath);
        if (normalizedPath == null) {
            return 0;
        }
        Path path = lessonRoot.resolve(normalizedPath).normalize();
        if (!path.startsWith(lessonRoot) || !Files.isRegularFile(path)) {
            return 0;
        }
        try {
            byte[] fileBytes = Files.readAllBytes(path);
            String ext = extension(path.getFileName().toString()).toLowerCase(Locale.ROOT);
            return countFilePages(fileBytes, ext);
        } catch (IOException ignored) {
            return 0;
        }
    }

    private int countFilePages(byte[] fileBytes, String ext) {
        if (".pptx".equals(ext) || ".ppt".equals(ext)) {
            return countPptxSlides(fileBytes);
        }
        if (".pdf".equals(ext)) {
            return countPdfPages(fileBytes);
        }
        return 1;
    }

    private int countPptxSlides(byte[] fileBytes) {
        int count = 0;
        try (var zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(fileBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().matches("ppt/slides/slide\\d+\\.xml")) {
                    count++;
                }
            }
        } catch (IOException ignored) {
        }
        return Math.max(count, 1);
    }

    private int countPdfPages(byte[] fileBytes) {
        String raw = new String(fileBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/Type\\s*/Page(?!s)").matcher(raw);
        int count = 0;
        while (m.find()) count++;
        return Math.max(count, 1);
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return filename.substring(idx);
    }

    private String extensionByType(String lessonType) {
        return switch (trimToEmpty(lessonType)) {
            case "pdf" -> ".pdf";
            case "ppt" -> ".pptx";
            case "doc" -> ".docx";
            case "markdown" -> ".md";
            default -> "";
        };
    }

    private String humanSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        double kb = size / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    // ── shared utilities ────────────────────────────────────

    private boolean isMember(String classroomId, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ?",
                Integer.class,
                classroomId,
                userId
        );
        return count != null && count > 0;
    }

    private boolean isStaff(String classroomId, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role in ('owner','ta')",
                Integer.class,
                classroomId,
                userId
        );
        return count != null && count > 0;
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null, false, false, null);
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> {
                        String adminType = trimToEmpty(rs.getString("admin_type"));
                        boolean admin = "Admin".equals(adminType) || "Teacher".equals(adminType);
                        boolean adminManager = "Admin".equals(adminType);
                        return new UserAuth(true, rs.getLong("id"), admin, adminManager, rs.getString("username"));
                    },
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, null, false, false, null);
        }
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private List<Object> parseJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("parseJsonList failed, returning empty list", e);
            return List.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int parseIntObj(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseIntObj failed for value={}, using fallback {}", value, fallback, e);
            return fallback;
        }
    }

    private record UserAuth(boolean authenticated, Long userId, boolean admin, boolean adminManager, String username) {
    }
}

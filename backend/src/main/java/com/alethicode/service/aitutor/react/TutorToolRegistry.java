package com.alethicode.service.aitutor.react;

import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
import com.alethicode.service.aitutor.retrieval.SimilarErrorRetrievalService;
import com.alethicode.service.languagepack.PageRetrievalHit;
import com.alethicode.service.languagepack.PageRetrievalService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory that builds ToolDefinition + ToolExecutor pairs for ReAct loops.
 * Each tool wraps an existing retrieval or query service.
 */
public final class TutorToolRegistry {

    private TutorToolRegistry() {}

    public static ToolDefinition searchCoursewareDefinition() {
        return new ToolDefinition(
                "search_courseware",
                "按知识点ID、章节名或关键词检索课件片段，返回最多5条匹配结果",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "kc_ids", Map.of("type", "array", "items", Map.of("type", "integer"), "description", "知识点ID列表"),
                                "chapter", Map.of("type", "string", "description", "章节名称"),
                                "limit", Map.of("type", "integer", "description", "返回上限，默认5")
                        ),
                        "required", List.of()
                ),
                ToolDomain.TUTOR,
                ctx -> true,
                "当学生遇到错误且需要课件知识辅助诊断时调用。不要在 AC_REVIEW 或 TRANSFER 阶段调用。"
        );
    }

    public static ToolExecutor searchCoursewareExecutor(
            CoursewareRetrievalService coursewareService,
            Long problemId,
            Long languagePackId
    ) {
        return args -> {
            List<Long> kcIds = parseLongList(args.get("kc_ids"));
            String chapter = stringVal(args.get("chapter"));
            int limit = intVal(args.get("limit"), 5);
            return coursewareService.retrieve(problemId, kcIds, chapter, limit, languagePackId);
        };
    }

    public static ToolDefinition searchSimilarErrorsDefinition() {
        return new ToolDefinition(
                "search_similar_errors",
                "向量检索当前用户历史中与给定错误描述相似的错误记录",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "错误描述文本，用于相似度检索"),
                                "error_taxonomy", Map.of("type", "string", "description", "错误分类标签，如 SyntaxError, WrongAnswer")
                        ),
                        "required", List.of("query")
                ),
                ToolDomain.TUTOR,
                ctx -> { ctx.requireUserId(); return true; },
                "仅在 ERROR_FEEDBACK 阶段调用，查找学生历史中类似的错误模式。需要 userId。"
        );
    }

    public static ToolExecutor searchSimilarErrorsExecutor(
            SimilarErrorRetrievalService errorService,
            Long userId,
            Long currentProblemId,
            String language
    ) {
        return args -> {
            String query = stringVal(args.get("query"));
            String taxonomy = stringVal(args.get("error_taxonomy"));
            return errorService.retrieve(userId, currentProblemId, language, taxonomy, query);
        };
    }

    public static ToolDefinition searchLanguagePackPagesDefinition() {
        return new ToolDefinition(
                "search_language_pack_pages",
                "从语言包课件中检索与查询相关的页面，返回最相关的页面内容和引用信息",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "检索查询文本"),
                                "context", Map.of("type", "string", "description", "最近对话上下文，用于增强检索")
                        ),
                        "required", List.of("query")
                ),
                ToolDomain.QA,
                ctx -> { ctx.requireLanguagePackId(); return true; },
                "QA 专用工具。仅在课件问答场景中调用，需要 languagePackId。不要在导学工作流中调用。"
        );
    }

    public static ToolExecutor searchLanguagePackPagesExecutor(
            PageRetrievalService pageService,
            Long languagePackId
    ) {
        return args -> {
            String query = stringVal(args.get("query"));
            String context = stringVal(args.get("context"));
            List<PageRetrievalHit> hits = pageService.retrieve(languagePackId, query, context);
            return hits.stream().map(PageRetrievalHit::toMap).toList();
        };
    }

    public static ToolDefinition getLearnerHistoryDefinition() {
        return new ToolDefinition(
                "get_learner_history",
                "获取学习者最近N次提交记录，包含结果、代码和错误信息",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "limit", Map.of("type", "integer", "description", "返回条数上限，默认5")
                        ),
                        "required", List.of()
                ),
                ToolDomain.TUTOR,
                ctx -> { ctx.requireUserId(); ctx.requireProblemId(); return true; },
                "获取学生最近提交历史，辅助错误诊断。需要 userId 和 problemId。"
        );
    }

    public static ToolExecutor getLearnerHistoryExecutor(JdbcTemplate jdbcTemplate, Long userId, Long problemId) {
        return args -> {
            int limit = intVal(args.get("limit"), 5);
            if (userId == null) {
                throw new IllegalStateException("get_learner_history requires userId");
            }
            if (problemId == null) {
                throw new IllegalStateException("get_learner_history requires problemId");
            }
            return jdbcTemplate.query(
                    """
                    SELECT id, result, code, language,
                           coalesce(statistic_info->>'err_info', '') AS err_info,
                           create_time
                    FROM submission
                    WHERE user_id = ? AND problem_id = ?
                    ORDER BY create_time DESC
                    LIMIT ?
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("submission_id", rs.getString("id"));
                        row.put("result", rs.getInt("result"));
                        row.put("result_label", resultLabel(rs.getInt("result")));
                        row.put("err_info", abbreviate(rs.getString("err_info"), 200));
                        row.put("code_excerpt", abbreviate(rs.getString("code"), 300));
                        row.put("language", rs.getString("language"));
                        row.put("create_time", rs.getTimestamp("create_time").toString());
                        return row;
                    },
                    userId, problemId, limit
            );
        };
    }

    public static Map<String, ToolDefinition> getToolsForDomain(ToolDomain domain, ToolContext context) {
        if (context != null && context.languagePackId() == null) {
            throw new IllegalStateException("TutorToolRegistry: languagePackId is required in ToolContext");
        }

        Map<String, ToolDefinition> allTools = Map.of(
                "search_courseware", searchCoursewareDefinition(),
                "search_similar_errors", searchSimilarErrorsDefinition(),
                "search_language_pack_pages", searchLanguagePackPagesDefinition(),
                "get_learner_history", getLearnerHistoryDefinition()
        );

        Map<String, ToolDefinition> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, ToolDefinition> entry : allTools.entrySet()) {
            if (entry.getValue().domain() == domain) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> parseLongList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> {
                        if (item instanceof Number n) return n.longValue();
                        return Long.parseLong(String.valueOf(item));
                    })
                    .toList();
        }
        return List.of();
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return fallback;
        try { return Integer.parseInt(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static String resultLabel(int result) {
        return switch (result) {
            case 0 -> "AC";
            case -1 -> "WA";
            case -2 -> "CE";
            case 1, 2 -> "TLE";
            case 3 -> "MLE";
            case 4 -> "RE";
            case 5 -> "SE";
            default -> "Unknown";
        };
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}

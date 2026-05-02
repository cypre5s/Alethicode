package com.alethicode.service.aitutor.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.aitutor.retrieval.SimilarErrorRetrievalService;
import com.alethicode.service.aitutor.profile.LearnerState;
import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;
import com.alethicode.service.aitutor.language.TutorLanguageSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EvidencePackAssembler {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CoursewareRetrievalService coursewareRetrievalService;
    private final SimilarErrorRetrievalService similarErrorRetrievalService;

    public EvidencePackAssembler(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CoursewareRetrievalService coursewareRetrievalService,
            SimilarErrorRetrievalService similarErrorRetrievalService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.coursewareRetrievalService = coursewareRetrievalService;
        this.similarErrorRetrievalService = similarErrorRetrievalService;
    }

    public EvidencePack assemble(
            Long userId,
            Long problemId,
            String sessionId,
            String currentPhase,
            String currentEvent,
            Map<String, Object> session,
            Map<String, Object> eventData,
            LearnerState learnerState
    ) {
        Map<String, Object> problem = loadProblemRecord(problemId);
        Map<String, Object> submission = loadSubmissionRecord(userId, stringValue(eventData.get("submission_id")));
        Map<String, Object> code = loadCodeContext(userId, problemId, eventData, submission);
        Long languagePackId = parseLong(problem.get("language_pack_id"));
        List<Long> kcIds = coursewareRetrievalService.loadProblemKcIds(problemId, languagePackId);
        String chapter = coursewareRetrievalService.loadPrimaryChapter(problemId, languagePackId);
        List<Map<String, Object>> coursewareHits = coursewareRetrievalService.retrieve(problemId, kcIds, chapter, 3, languagePackId);
        Map<String, List<Map<String, Object>>> similarHits = loadSimilarHits(userId, problemId, currentEvent, eventData, submission, code);
        List<Map<String, Object>> similarNotebookHits = similarHits.getOrDefault("similar_notebook_hits", List.of());
        List<Map<String, Object>> similarMemoryHits = similarHits.getOrDefault("similar_memory_hits", List.of());
        List<Map<String, Object>> retrievalHits = new java.util.ArrayList<>(coursewareHits);
        retrievalHits.addAll(similarNotebookHits);
        retrievalHits.addAll(similarMemoryHits);

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("session_id", sessionId == null ? "" : sessionId);
        workflow.put("phase", currentPhase == null ? "" : currentPhase);
        workflow.put("event", currentEvent == null ? "" : currentEvent);
        workflow.put("pending_human_action", stringValue(session.get("pending_human_action")));
        workflow.put("last_event", castMap(castMap(session.get("node_outputs")).get("last_event")));

        Map<String, Object> retrieval = new LinkedHashMap<>();
        retrieval.put("hit_count", retrievalHits.size());
        retrieval.put("hits", retrievalHits);
        retrieval.put("sources", retrievalHits.stream().map(hit -> hit.getOrDefault("match_type", hit.getOrDefault("source_type", ""))).distinct().toList());

        Map<String, Object> courseware = new LinkedHashMap<>();
        courseware.put("chapter", chapter);
        courseware.put("hits", coursewareHits);

        Map<String, Object> similarErrors = new LinkedHashMap<>();
        similarErrors.put("similar_notebook_hits", similarNotebookHits);
        similarErrors.put("similar_memory_hits", similarMemoryHits);
        similarErrors.put("notebook_hit_count", similarNotebookHits.size());
        similarErrors.put("memory_hit_count", similarMemoryHits.size());

        Map<String, Object> kc = new LinkedHashMap<>();
        kc.put("problem_kc_ids", kcIds);
        kc.put("chapter", chapter);

        Map<String, Object> risk = new LinkedHashMap<>();
        String message = stringValue(eventData.get("message"));
        String normalizedMessage = message == null ? "" : message.toLowerCase();
        risk.put("answer_leak_probe", normalizedMessage.contains("答案") || normalizedMessage.contains("answer"));
        risk.put("guardrail_flags", List.of());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("built_at", Instant.now().toString());
        meta.put("version", "v1");
        meta.put("strategy", "evidence_pack");

        Map<String, Object> orchestration = new LinkedHashMap<>();
        orchestration.put("current_phase", currentPhase == null ? "" : currentPhase);
        orchestration.put("current_event", currentEvent == null ? "" : currentEvent);
        orchestration.put("last_checkpoint", loadLatestCheckpointLabel(sessionId));
        orchestration.put("shared_context_keys", List.of(
                "problem",
                "code",
                "submission",
                "kc",
                "learner_state",
                "similar_errors"
        ));

        return new EvidencePack(
                problem,
                workflow,
                submission,
                code,
                new LinkedHashMap<>(castMap(eventData.get("behavior_metrics"))),
                learnerState.toMap(),
                courseware,
                similarErrors,
                kc,
                risk,
                retrieval,
                orchestration,
                meta
        );
    }

    public void persistRetrievalLogs(String sessionId, Long problemId, String phase, EvidencePack evidencePack) {
        List<Map<String, Object>> hits = normalizeMapList(evidencePack.retrieval().get("hits"));
        for (Map<String, Object> hit : hits) {
            jdbcTemplate.update(
                    """
                    insert into ai_retrieval_log(session_id, problem_id, phase, source_type, source_id, score, chunk_excerpt, metadata, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                    """,
                    sessionId,
                    problemId,
                    phase,
                    stringValue(hit.getOrDefault("match_type", hit.getOrDefault("source_type", ""))),
                    stringValue(hit.getOrDefault("chunk_id", hit.getOrDefault("source_id", ""))),
                    parseDouble(hit.get("score")),
                    stringValue(hit.getOrDefault("excerpt", hit.getOrDefault("summary", ""))),
                    toJson(hit)
            );
        }
    }

    public String buildProblemContext(EvidencePack evidencePack) {
        Map<String, Object> row = evidencePack.problem();
        LanguageAwareTutorContext context = LanguageAwareTutorContext.from(
                evidencePack.workflow(),
                evidencePack.code(),
                row
        );
        return """
                题目ID: %s
                标题: %s
                题目描述: %s
                输入描述: %s
                输出描述: %s
                样例: %s
                提示: %s
                来源: %s
                当前编程语言: %s
                题目支持语言: %s
                参考解语言: %s
                语言包ID: %s
                语言包主语言: %s
                目标受众: %s
                """.formatted(
                stringValue(row.get("id")),
                trimToEmpty(stringValue(row.get("title"))),
                trimToEmpty(stringValue(row.get("description"))),
                trimToEmpty(stringValue(row.get("input_description"))),
                trimToEmpty(stringValue(row.get("output_description"))),
                trimToEmpty(stringValue(row.get("samples"))),
                trimToEmpty(stringValue(row.get("hint"))),
                trimToEmpty(stringValue(row.get("source"))),
                context.currentLanguage(),
                context.problemSupportedLanguages(),
                context.problemReferenceSolutionLanguage(),
                context.languagePackId() == null ? "" : context.languagePackId(),
                context.languagePackPrimaryLanguage(),
                context.audience()
        );
    }

    private Map<String, Object> loadProblemRecord(Long problemId) {
        if (problemId == null) {
            throw new IllegalStateException("题目 ID 不能为空");
        }
        Map<String, Object> row = jdbcTemplate.query(
                """
                select p.id, p.title, p.description, p.input_description, p.output_description,
                       p.samples::text as samples_json, p.hint, p.source, p.reference_solution_code,
                       p.reference_solution_language, p.languages::text as languages_json, p.template::text as template_json,
                       lpm.language_pack_id, lp.primary_language as language_pack_primary_language
                from problem p
                left join language_pack_problem_mapping lpm on lpm.problem_id = p.id
                left join language_pack lp on lp.id = lpm.language_pack_id
                where p.id = ?
                limit 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("title", trimToEmpty(rs.getString("title")));
                    item.put("description", trimToEmpty(rs.getString("description")));
                    item.put("input_description", trimToEmpty(rs.getString("input_description")));
                    item.put("output_description", trimToEmpty(rs.getString("output_description")));
                    item.put("samples", trimToEmpty(rs.getString("samples_json")));
                    item.put("hint", trimToEmpty(rs.getString("hint")));
                    item.put("source", trimToEmpty(rs.getString("source")));
                    item.put("reference_solution_code", trimToEmpty(rs.getString("reference_solution_code")));
                    item.put("reference_solution_language", trimToEmpty(rs.getString("reference_solution_language")));
                    item.put("languages", TutorLanguageSupport.parseLanguageList(rs.getString("languages_json")));
                    item.put("template", trimToEmpty(rs.getString("template_json")));
                    item.put("language_pack_id", rs.getObject("language_pack_id"));
                    item.put("language_pack_primary_language", trimToEmpty(rs.getString("language_pack_primary_language")));
                    return item;
                },
                problemId
        );
        if (row == null) {
            throw new IllegalStateException("Problem not found: " + problemId);
        }
        return row;
    }

    private Map<String, Object> loadSubmissionRecord(Long userId, String submissionId) {
        if (submissionId == null || submissionId.isBlank() || userId == null) {
            return Map.of();
        }
        Map<String, Object> row = jdbcTemplate.query(
                """
                select id, code, language, result, info::text as info_json, statistic_info::text as statistic_info_json
                from submission
                where id = ? and user_id = ?
                limit 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getString("id"));
                    item.put("code", trimToEmpty(rs.getString("code")));
                    item.put("language", trimToEmpty(rs.getString("language")));
                    item.put("result", rs.getInt("result"));
                    item.put("info", parseJsonMap(rs.getString("info_json")));
                    item.put("statistic_info", parseJsonMap(rs.getString("statistic_info_json")));
                    return item;
                },
                submissionId,
                userId
        );
        return row == null ? Map.of() : row;
    }

    private Map<String, Object> loadCodeContext(Long userId, Long problemId, Map<String, Object> eventData, Map<String, Object> submission) {
        Map<String, Object> result = new LinkedHashMap<>();
        String currentCode = trimToEmpty(stringValue(eventData.get("code")));
        String currentLanguage = TutorLanguageSupport.normalizeLanguage(eventData.get("language"));
        if (currentCode.isBlank()) {
            currentCode = trimToEmpty(stringValue(submission.get("code")));
        }
        if (currentLanguage.isBlank()) {
            currentLanguage = TutorLanguageSupport.normalizeLanguage(submission.get("language"));
        }
        if (currentCode.isBlank() && userId != null && problemId != null) {
            currentCode = jdbcTemplate.query(
                    """
                    select code
                    from ai_code_snapshot
                    where user_id = ? and problem_id = ?
                    order by create_time desc
                    limit 1
                    """,
                    rs -> rs.next() ? trimToEmpty(rs.getString("code")) : "",
                    userId,
                    problemId
            );
        }
        result.put("current_code", currentCode);
        result.put("latest_snapshot", currentCode);
        result.put("language", currentLanguage);
        return result;
    }

    private Map<String, List<Map<String, Object>>> loadSimilarHits(
            Long userId,
            Long problemId,
            String currentEvent,
            Map<String, Object> eventData,
            Map<String, Object> submission,
            Map<String, Object> code
    ) {
        if (!"ERROR_FEEDBACK".equals(currentEvent)) {
            return Map.of("similar_notebook_hits", List.of(), "similar_memory_hits", List.of());
        }
        Map<String, Object> statisticInfo = castMap(submission.get("statistic_info"));
        String errorInfo = trimToEmpty(stringValue(statisticInfo.get("err_info")));
        if (errorInfo.isBlank()) {
            errorInfo = trimToEmpty(stringValue(castMap(submission.get("info")).get("err_info")));
        }
        String errorTaxonomy = deriveErrorTaxonomy(submission, errorInfo);
        String queryText = "错误类型：" + errorTaxonomy
                + "；错误信息：" + errorInfo
                + "；代码摘要：" + trimToEmpty(stringValue(code.get("current_code")));
        return similarErrorRetrievalService.retrieve(
                userId,
                problemId,
                trimToEmpty(stringValue(submission.get("language"))),
                errorTaxonomy,
                queryText
        );
    }

    private String deriveErrorTaxonomy(Map<String, Object> submission, String errorInfo) {
        int result = submission.get("result") instanceof Number number ? number.intValue() : 0;
        return com.alethicode.service.aitutor.contract.ErrorTaxonomy.normalize(
                deriveRawErrorHint(result, errorInfo)
        );
    }

    private String deriveRawErrorHint(int result, String errorInfo) {
        String normalized = trimToEmpty(errorInfo).toLowerCase();
        if (result == -2 || normalized.contains("compile") || normalized.contains("syntaxerror") || normalized.contains("indentationerror")) {
            return "syntax_error";
        }
        if (result == 1 || result == 2 || result == 3) {
            return "performance";
        }
        if (normalized.contains("exception") || normalized.contains("traceback") || normalized.contains("runtime") || result == 4) {
            return "runtime_error";
        }
        return "logic_error";
    }

    private String loadLatestCheckpointLabel(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }
        return jdbcTemplate.query(
                """
                select channel_values::text as channel_json
                from ai_workflow_checkpoint
                where session_id = ?
                order by created_at desc
                limit 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return "";
                    }
                    return String.valueOf(parseJsonMap(rs.getString("channel_json")).getOrDefault("label", ""));
                },
                sessionId
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> normalizeMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = castMap(item);
            if (!map.isEmpty()) {
                result.add(map);
            }
        }
        return result;
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

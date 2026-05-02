package com.alethicode.service.languagepack.impl;

import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.LanguagePackExportImportService;
import com.alethicode.service.languagepack.LanguagePackInitStageLabels;
import com.alethicode.service.languagepack.storage.LanguagePackStorageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class LanguagePackExportImportServiceImpl implements LanguagePackExportImportService {

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_CHAPTERS = 100;
    private static final int MAX_KCS = 500;
    private static final int MAX_EXAMPLES = 1000;
    private static final int MAX_CANDIDATES = 1000;

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*[a-z0-9]$");
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<\\s*script", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile("\\bon\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    private static final Set<String> VALID_UNIT_TYPES = Set.of(
            "exercise", "assignment", "worked_example", "code_snippet", "demo"
    );
    private static final Set<String> VALID_VALIDATION_STATUSES = Set.of(
            "pending", "validating", "passed", "failed"
    );
    private static final Set<String> VALID_LANGUAGES = Set.of("Python3", "C", "C++", "Java");

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackStorageService languagePackStorageService;

    public LanguagePackExportImportServiceImpl(JdbcTemplate jdbcTemplate,
                                               LanguagePackStorageService languagePackStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.languagePackStorageService = languagePackStorageService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportTask(Long taskId) {
        Map<String, Object> taskRow = jdbcTemplate.queryForMap(
                "SELECT language_pack_id, stage FROM language_pack_init_task WHERE id = ?", taskId
        );
        Long languagePackId = ((Number) taskRow.get("language_pack_id")).longValue();

        Map<String, Object> packRow = jdbcTemplate.queryForMap(
                "SELECT slug, name, primary_language FROM language_pack WHERE id = ?", languagePackId
        );

        List<Map<String, Object>> chapters = jdbcTemplate.queryForList(
                """
                SELECT id, chapter_index, title, description, page_range_start, page_range_end
                FROM language_pack_chapter
                WHERE language_pack_id = ?
                ORDER BY chapter_index
                """, languagePackId
        );

        Map<Long, Integer> chapterIdToRef = new HashMap<>();
        List<Map<String, Object>> exportChapters = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++) {
            Map<String, Object> ch = chapters.get(i);
            int refId = i + 1;
            chapterIdToRef.put(((Number) ch.get("id")).longValue(), refId);
            Map<String, Object> exportCh = new LinkedHashMap<>();
            exportCh.put("ref_id", refId);
            exportCh.put("chapter_index", ch.get("chapter_index"));
            exportCh.put("title", ch.get("title"));
            exportCh.put("description", ch.get("description"));
            exportCh.put("page_range_start", ch.get("page_range_start"));
            exportCh.put("page_range_end", ch.get("page_range_end"));
            exportChapters.add(exportCh);
        }

        List<Map<String, Object>> kcs = jdbcTemplate.queryForList(
                """
                SELECT id, chapter_id, name, name_en, description
                FROM language_pack_kc
                WHERE language_pack_id = ?
                ORDER BY id
                """, languagePackId
        );

        Map<Long, Integer> kcIdToRef = new HashMap<>();
        List<Map<String, Object>> exportKcs = new ArrayList<>();
        for (int i = 0; i < kcs.size(); i++) {
            Map<String, Object> kc = kcs.get(i);
            int refId = i + 1;
            kcIdToRef.put(((Number) kc.get("id")).longValue(), refId);
            Map<String, Object> exportKc = new LinkedHashMap<>();
            exportKc.put("ref_id", refId);
            Object chapterId = kc.get("chapter_id");
            exportKc.put("chapter_ref_id", chapterId != null ? chapterIdToRef.get(((Number) chapterId).longValue()) : null);
            exportKc.put("name", kc.get("name"));
            exportKc.put("name_en", kc.get("name_en"));
            exportKc.put("description", kc.get("description"));
            exportKcs.add(exportKc);
        }

        List<Map<String, Object>> examples = jdbcTemplate.queryForList(
                """
                SELECT id, raw_text, normalized_body, input_description, output_description,
                       evidence_excerpt, page_range_start, page_range_end,
                       unit_type, source_title, oj_convertible, oj_block_reason, source_signature
                FROM language_pack_example
                WHERE language_pack_id = ?
                ORDER BY id
                """, languagePackId
        );

        List<Long> exampleIds = examples.stream()
                .map(ex -> ((Number) ex.get("id")).longValue())
                .toList();
        Map<Long, List<Integer>> exampleKcRefMap = new HashMap<>();
        if (!exampleIds.isEmpty()) {
            List<Map<String, Object>> allMappings = jdbcTemplate.queryForList(
                    """
                    SELECT example_id, kc_id
                    FROM language_pack_example_kc_mapping
                    WHERE example_id IN (SELECT id FROM language_pack_example WHERE language_pack_id = ?)
                    ORDER BY example_id, kc_id
                    """, languagePackId
            );
            for (Map<String, Object> m : allMappings) {
                Long exId = ((Number) m.get("example_id")).longValue();
                Long kcDbId = ((Number) m.get("kc_id")).longValue();
                Integer kcRef = kcIdToRef.get(kcDbId);
                if (kcRef != null) {
                    exampleKcRefMap.computeIfAbsent(exId, k -> new ArrayList<>()).add(kcRef);
                }
            }
        }

        Map<Long, Integer> exampleIdToRef = new HashMap<>();
        List<Map<String, Object>> exportExamples = new ArrayList<>();
        for (int i = 0; i < examples.size(); i++) {
            Map<String, Object> ex = examples.get(i);
            int refId = i + 1;
            Long exampleId = ((Number) ex.get("id")).longValue();
            exampleIdToRef.put(exampleId, refId);

            Map<String, Object> exportEx = new LinkedHashMap<>();
            exportEx.put("ref_id", refId);
            exportEx.put("raw_text", ex.get("raw_text"));
            exportEx.put("normalized_body", ex.get("normalized_body"));
            exportEx.put("input_description", ex.get("input_description"));
            exportEx.put("output_description", ex.get("output_description"));
            exportEx.put("evidence_excerpt", ex.get("evidence_excerpt"));
            exportEx.put("page_range_start", ex.get("page_range_start"));
            exportEx.put("page_range_end", ex.get("page_range_end"));
            exportEx.put("unit_type", ex.get("unit_type"));
            exportEx.put("source_title", ex.get("source_title"));
            exportEx.put("oj_convertible", ex.get("oj_convertible"));
            exportEx.put("oj_block_reason", ex.get("oj_block_reason"));
            exportEx.put("source_signature", ex.get("source_signature"));
            exportEx.put("kc_ref_ids", exampleKcRefMap.getOrDefault(exampleId, List.of()));
            exportExamples.add(exportEx);
        }

        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                """
                SELECT kc_id, candidate_title, candidate_body,
                       candidate_input_description, candidate_output_description,
                       candidate_samples_json, reference_solution, test_cases_json,
                       teaching_explanation, common_mistakes_json,
                       source_pages_json, related_kc_ids_json,
                       source_signature, problem_package_json,
                       validation_status, validation_message
                FROM language_pack_problem_generation_log
                WHERE init_task_id = ?
                ORDER BY id
                """, taskId
        );

        List<Map<String, Object>> exportCandidates = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            Map<String, Object> exportC = new LinkedHashMap<>();
            Object kcId = c.get("kc_id");
            exportC.put("kc_ref_id", kcId != null ? kcIdToRef.get(((Number) kcId).longValue()) : null);
            exportC.put("candidate_title", c.get("candidate_title"));
            exportC.put("candidate_body", c.get("candidate_body"));
            exportC.put("candidate_input_description", c.get("candidate_input_description"));
            exportC.put("candidate_output_description", c.get("candidate_output_description"));
            exportC.put("candidate_samples_json", c.get("candidate_samples_json"));
            exportC.put("reference_solution", c.get("reference_solution"));
            exportC.put("test_cases_json", c.get("test_cases_json"));
            exportC.put("teaching_explanation", c.get("teaching_explanation"));
            exportC.put("common_mistakes_json", c.get("common_mistakes_json"));
            exportC.put("source_pages_json", c.get("source_pages_json"));
            exportC.put("related_kc_ids_json", c.get("related_kc_ids_json"));
            exportC.put("source_signature", c.get("source_signature"));
            exportC.put("problem_package_json", c.get("problem_package_json"));
            exportC.put("validation_status", c.get("validation_status"));
            exportC.put("validation_message", c.get("validation_message"));
            exportCandidates.add(exportC);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format_version", FORMAT_VERSION);
        result.put("export_time", Instant.now().toString());
        Map<String, Object> packMeta = new LinkedHashMap<>();
        packMeta.put("slug", packRow.get("slug"));
        packMeta.put("name", packRow.get("name"));
        packMeta.put("primary_language", packRow.get("primary_language"));
        result.put("language_pack", packMeta);
        result.put("chapters", exportChapters);
        result.put("knowledge_components", exportKcs);
        result.put("examples", exportExamples);
        result.put("candidates", exportCandidates);
        return result;
    }

    @Override
    public Long importTask(Map<String, Object> payload, Long creatorId) {
        validatePayload(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> packMeta = (Map<String, Object>) payload.get("language_pack");
        String slug = (String) packMeta.get("slug");
        String name = (String) packMeta.get("name");
        String primaryLanguage = (String) packMeta.get("primary_language");

        Integer existingVersion = jdbcTemplate.query(
                "SELECT MAX(version) FROM language_pack WHERE slug = ?",
                rs -> rs.next() ? rs.getObject(1, Integer.class) : null,
                slug
        );
        int nextVersion = existingVersion == null ? 1 : existingVersion + 1;

        KeyHolder packKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO language_pack(slug, version, name, primary_language, status, creator_id, create_time, update_time)
                    VALUES (?, ?, ?, ?, 'draft', ?, now(), now())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, slug);
            ps.setInt(2, nextVersion);
            ps.setString(3, name);
            ps.setString(4, primaryLanguage);
            if (creatorId != null) {
                ps.setLong(5, creatorId);
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }
            return ps;
        }, packKeyHolder);
        Long languagePackId = ((Number) packKeyHolder.getKeys().get("id")).longValue();

        KeyHolder taskKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO language_pack_init_task(language_pack_id, stage, enable_objective_questions, create_time, update_time)
                    VALUES (?, 'created', false, now(), now())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, languagePackId);
            return ps;
        }, taskKeyHolder);
        Long taskId = ((Number) taskKeyHolder.getKeys().get("id")).longValue();

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_stage_log(task_id, from_stage, to_stage, message, create_time)
                VALUES (?, '', 'created', ?, now())
                """,
                taskId, LanguagePackInitStageLabels.formatTaskCreatedViaImport()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) payload.getOrDefault("chapters", List.of());
        Map<Integer, Long> chapterRefToId = new HashMap<>();
        for (Map<String, Object> ch : chapters) {
            int refId = toInt(ch.get("ref_id"));
            Long chapterId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO language_pack_chapter(language_pack_id, init_task_id, chapter_index, title, description,
                                                     page_range_start, page_range_end, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, now())
                    RETURNING id
                    """,
                    Long.class,
                    languagePackId, taskId,
                    toInt(ch.get("chapter_index")),
                    toString(ch.get("title")),
                    toString(ch.get("description")),
                    toIntOrNull(ch.get("page_range_start")),
                    toIntOrNull(ch.get("page_range_end"))
            );
            chapterRefToId.put(refId, chapterId);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kcs = (List<Map<String, Object>>) payload.getOrDefault("knowledge_components", List.of());
        Map<Integer, Long> kcRefToId = new HashMap<>();
        for (Map<String, Object> kc : kcs) {
            int refId = toInt(kc.get("ref_id"));
            String kcName = toString(kc.get("name"));
            String nameNormalized = kcName.toLowerCase().strip();

            Integer chapterRefId = toIntOrNull(kc.get("chapter_ref_id"));
            Long chapterId = chapterRefId != null ? chapterRefToId.get(chapterRefId) : null;

            Long kcId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO language_pack_kc(language_pack_id, init_task_id, chapter_id, name, name_normalized,
                                                name_en, description, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, now())
                    RETURNING id
                    """,
                    Long.class,
                    languagePackId, taskId, chapterId,
                    kcName, nameNormalized,
                    toString(kc.get("name_en")),
                    toString(kc.get("description"))
            );
            kcRefToId.put(refId, kcId);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> examples = (List<Map<String, Object>>) payload.getOrDefault("examples", List.of());
        for (Map<String, Object> ex : examples) {
            Long exampleId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO language_pack_example(language_pack_id, init_task_id, raw_text, normalized_body,
                                                     input_description, output_description, evidence_excerpt,
                                                     page_range_start, page_range_end,
                                                     unit_type, source_title, oj_convertible, oj_block_reason,
                                                     source_signature, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    RETURNING id
                    """,
                    Long.class,
                    languagePackId, taskId,
                    toString(ex.get("raw_text")),
                    toString(ex.get("normalized_body")),
                    toString(ex.get("input_description")),
                    toString(ex.get("output_description")),
                    toString(ex.get("evidence_excerpt")),
                    toIntOrNull(ex.get("page_range_start")),
                    toIntOrNull(ex.get("page_range_end")),
                    toString(ex.get("unit_type")),
                    toString(ex.get("source_title")),
                    toBool(ex.get("oj_convertible")),
                    toString(ex.get("oj_block_reason")),
                    toString(ex.get("source_signature"))
            );

            @SuppressWarnings("unchecked")
            List<Number> kcRefIds = (List<Number>) ex.getOrDefault("kc_ref_ids", List.of());
            for (Number kcRefId : kcRefIds) {
                Long kcId = kcRefToId.get(kcRefId.intValue());
                if (kcId != null) {
                    jdbcTemplate.update(
                            "INSERT INTO language_pack_example_kc_mapping(example_id, kc_id) VALUES (?, ?)",
                            exampleId, kcId
                    );
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) payload.getOrDefault("candidates", List.of());
        for (Map<String, Object> c : candidates) {
            Integer kcRefId = toIntOrNull(c.get("kc_ref_id"));
            Long kcId = kcRefId != null ? kcRefToId.get(kcRefId) : null;
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_problem_generation_log(
                        init_task_id, language_pack_id, kc_id,
                        candidate_title, candidate_body,
                        candidate_input_description, candidate_output_description,
                        candidate_samples_json, reference_solution, test_cases_json,
                        teaching_explanation, common_mistakes_json,
                        source_pages_json, related_kc_ids_json,
                        source_signature, problem_package_json,
                        validation_status, validation_message,
                        create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    """,
                    taskId, languagePackId, kcId,
                    toString(c.get("candidate_title")),
                    toString(c.get("candidate_body")),
                    toString(c.get("candidate_input_description")),
                    toString(c.get("candidate_output_description")),
                    toString(c.get("candidate_samples_json")),
                    toString(c.get("reference_solution")),
                    toString(c.get("test_cases_json")),
                    toString(c.get("teaching_explanation")),
                    toString(c.get("common_mistakes_json")),
                    toString(c.get("source_pages_json")),
                    toString(c.get("related_kc_ids_json")),
                    toString(c.get("source_signature")),
                    toString(c.get("problem_package_json")),
                    toString(c.get("validation_status")),
                    toString(c.get("validation_message"))
            );
        }

        String resolvedStage = resolveImportedStage(chapters.size(), kcs.size(), examples.size(), candidates.size());
        jdbcTemplate.update(
                "UPDATE language_pack_init_task SET stage = ?, update_time = now() WHERE id = ?",
                resolvedStage, taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_stage_log(task_id, from_stage, to_stage, message, create_time)
                VALUES (?, 'created', ?, ?, now())
                """,
                taskId,
                resolvedStage,
                LanguagePackInitStageLabels.formatAdvance("created", resolvedStage)
        );

        int chapterCount = chapters.size();
        int kcCount = kcs.size();
        int exampleCount = examples.size();
        int candidateCount = candidates.size();
        jdbcTemplate.update(
                """
                UPDATE language_pack
                SET chapter_count = ?, kc_count = ?, example_count = ?, problem_count = ?, update_time = now()
                WHERE id = ?
                """,
                chapterCount, kcCount, exampleCount, candidateCount, languagePackId
        );

        return taskId;
    }

    @Override
    public Map<String, Object> deleteLanguagePack(Long languagePackId) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack WHERE id = ?",
                Integer.class,
                languagePackId
        );
        if (exists == null || exists == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Language pack not found");
        }

        List<Long> taskIds = jdbcTemplate.queryForList(
                "SELECT id FROM language_pack_init_task WHERE language_pack_id = ?",
                Long.class,
                languagePackId
        );

        List<Long> problemIds = jdbcTemplate.queryForList(
                "SELECT problem_id FROM language_pack_problem_mapping WHERE language_pack_id = ?",
                Long.class, languagePackId
        );

        int deletedProbKcMappings = 0;
        int deletedProblems = 0;
        if (!problemIds.isEmpty()) {
            String placeholders = problemIds.stream().map(id -> "?").collect(Collectors.joining(","));
            Object[] ids = problemIds.toArray();
            deletedProbKcMappings = jdbcTemplate.update(
                    "DELETE FROM ai_problem_kc_mapping WHERE problem_id IN (" + placeholders + ")",
                    ids
            );
            deletedProblems = jdbcTemplate.update(
                    "DELETE FROM problem WHERE id IN (" + placeholders + ")",
                    ids
            );
        }

        int deletedAiKcs = jdbcTemplate.update(
                "DELETE FROM ai_knowledge_component WHERE language_pack_id = ?", languagePackId
        );

        jdbcTemplate.update("DELETE FROM language_pack_video_job WHERE session_id IN (SELECT id FROM language_pack_chat_session WHERE language_pack_id = ?)", languagePackId);
        jdbcTemplate.update("DELETE FROM exam_sprint_task WHERE plan_id IN (SELECT id FROM exam_sprint_plan WHERE language_pack_id = ?)", languagePackId);
        jdbcTemplate.update("DELETE FROM exam_sprint_plan WHERE language_pack_id = ?", languagePackId);
        jdbcTemplate.update("DELETE FROM learner_kc_mastery WHERE language_pack_id = ?", languagePackId);
        jdbcTemplate.update("DELETE FROM learner_course_progress WHERE language_pack_id = ?", languagePackId);
        jdbcTemplate.update("DELETE FROM language_pack_kc_prerequisite WHERE language_pack_id = ?", languagePackId);
        jdbcTemplate.update("DELETE FROM language_pack_review_task WHERE language_pack_id = ?", languagePackId);

        int deletedTaskArtifacts = 0;
        for (Long taskId : taskIds) {
            jdbcTemplate.update("DELETE FROM language_pack_init_stage_log WHERE task_id = ?", taskId);
            jdbcTemplate.update("DELETE FROM language_pack_init_agent_run WHERE task_id = ?", taskId);
            jdbcTemplate.update("DELETE FROM language_pack_init_artifact WHERE task_id = ?", taskId);
            jdbcTemplate.update("DELETE FROM language_pack_problem_generation_log WHERE init_task_id = ?", taskId);
            jdbcTemplate.update("DELETE FROM language_pack_init_batch_run WHERE task_id = ?", taskId);
            languagePackStorageService.deleteTaskArtifacts(taskId);
            deletedTaskArtifacts++;
        }
        jdbcTemplate.update("DELETE FROM language_pack_init_task WHERE language_pack_id = ?", languagePackId);

        jdbcTemplate.update("DELETE FROM language_pack_problem_mapping WHERE language_pack_id = ?", languagePackId);
        jdbcTemplate.update("DELETE FROM language_pack_page WHERE language_pack_id = ?", languagePackId);
        jdbcTemplate.update("DELETE FROM language_pack_document WHERE language_pack_id = ?", languagePackId);
        jdbcTemplate.update("DELETE FROM language_pack_chat_session WHERE language_pack_id = ?", languagePackId);

        int deletedLanguagePack = jdbcTemplate.update("DELETE FROM language_pack WHERE id = ?", languagePackId);
        if (deletedLanguagePack == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Language pack not found");
        }

        return Map.of(
                "deleted_problems", deletedProblems,
                "deleted_ai_kcs", deletedAiKcs,
                "deleted_prob_kc_mappings", deletedProbKcMappings,
                "deleted_task_artifacts", deletedTaskArtifacts
        );
    }

    private String resolveImportedStage(int chapters, int kcs, int examples, int candidates) {
        if (candidates > 0) return "problems_validated";
        if (examples > 0) return "oj_candidates_ready";
        if (kcs > 0) return "kc_ready";
        if (chapters > 0) return "parsing";
        return "created";
    }

    private void validatePayload(Map<String, Object> payload) {
        Object fv = payload.get("format_version");
        if (fv == null || toInt(fv) != FORMAT_VERSION) {
            throw new BadRequestException("Unsupported format_version, expected " + FORMAT_VERSION);
        }

        Object packObj = payload.get("language_pack");
        if (!(packObj instanceof Map)) {
            throw new BadRequestException("Missing or invalid language_pack field");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> packMeta = (Map<String, Object>) packObj;

        String slug = toString(packMeta.get("slug"));
        if (slug.isBlank() || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new BadRequestException("Invalid slug: must match pattern [a-z0-9][a-z0-9-]*[a-z0-9]");
        }
        requireNonBlank(packMeta.get("name"), "language_pack.name");
        requireMaxLength(toString(packMeta.get("name")), 256, "language_pack.name");

        String language = toString(packMeta.get("primary_language"));
        if (!VALID_LANGUAGES.contains(language)) {
            throw new BadRequestException("Invalid primary_language: " + language);
        }

        validateList(payload, "chapters", MAX_CHAPTERS);
        validateList(payload, "knowledge_components", MAX_KCS);
        validateList(payload, "examples", MAX_EXAMPLES);
        validateList(payload, "candidates", MAX_CANDIDATES);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) payload.getOrDefault("chapters", List.of());
        Set<Integer> chapterRefIds = chapters.stream().map(ch -> toInt(ch.get("ref_id"))).collect(Collectors.toSet());
        for (Map<String, Object> ch : chapters) {
            requireNonBlank(ch.get("title"), "chapter.title");
            requireMaxLength(toString(ch.get("title")), 512, "chapter.title");
            assertSafeText(toString(ch.get("title")), "chapter.title");
            assertSafeText(toString(ch.get("description")), "chapter.description");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kcs = (List<Map<String, Object>>) payload.getOrDefault("knowledge_components", List.of());
        Set<Integer> kcRefIds = kcs.stream().map(kc -> toInt(kc.get("ref_id"))).collect(Collectors.toSet());
        for (Map<String, Object> kc : kcs) {
            requireNonBlank(kc.get("name"), "kc.name");
            requireMaxLength(toString(kc.get("name")), 256, "kc.name");
            assertSafeText(toString(kc.get("name")), "kc.name");
            assertSafeText(toString(kc.get("name_en")), "kc.name_en");
            assertSafeText(toString(kc.get("description")), "kc.description");
            Integer chapterRefId = toIntOrNull(kc.get("chapter_ref_id"));
            if (chapterRefId != null && !chapterRefIds.contains(chapterRefId)) {
                throw new BadRequestException("KC references non-existent chapter_ref_id: " + chapterRefId);
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> examples = (List<Map<String, Object>>) payload.getOrDefault("examples", List.of());
        for (Map<String, Object> ex : examples) {
            String unitType = toString(ex.get("unit_type"));
            if (!unitType.isBlank() && !VALID_UNIT_TYPES.contains(unitType)) {
                throw new BadRequestException("Invalid unit_type: " + unitType);
            }
            assertSafeText(toString(ex.get("raw_text")), "example.raw_text");
            assertSafeText(toString(ex.get("source_title")), "example.source_title");
            @SuppressWarnings("unchecked")
            List<Number> kcRefs = (List<Number>) ex.getOrDefault("kc_ref_ids", List.of());
            for (Number ref : kcRefs) {
                if (!kcRefIds.contains(ref.intValue())) {
                    throw new BadRequestException("Example references non-existent kc_ref_id: " + ref);
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) payload.getOrDefault("candidates", List.of());
        for (Map<String, Object> c : candidates) {
            String validationStatus = toString(c.get("validation_status"));
            if (!validationStatus.isBlank() && !VALID_VALIDATION_STATUSES.contains(validationStatus)) {
                throw new BadRequestException("Invalid validation_status: " + validationStatus);
            }
            assertSafeText(toString(c.get("candidate_title")), "candidate.candidate_title");
            assertSafeText(toString(c.get("candidate_body")), "candidate.candidate_body");
            assertSafeText(toString(c.get("reference_solution")), "candidate.reference_solution");
            Integer kcRefId = toIntOrNull(c.get("kc_ref_id"));
            if (kcRefId != null && !kcRefIds.contains(kcRefId)) {
                throw new BadRequestException("Candidate references non-existent kc_ref_id: " + kcRefId);
            }
        }
    }

    private void validateList(Map<String, Object> payload, String key, int maxSize) {
        Object raw = payload.get(key);
        if (raw == null) return;
        if (!(raw instanceof List)) {
            throw new BadRequestException(key + " must be an array");
        }
        List<?> list = (List<?>) raw;
        if (list.size() > maxSize) {
            throw new BadRequestException(key + " exceeds maximum size " + maxSize);
        }
    }

    private void assertSafeText(String text, String fieldName) {
        if (text == null || text.isBlank()) return;
        if (SCRIPT_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Field " + fieldName + " contains forbidden <script> content");
        }
        if (EVENT_HANDLER_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Field " + fieldName + " contains forbidden event handler attribute");
        }
    }

    private void requireNonBlank(Object value, String fieldName) {
        if (value == null || value.toString().isBlank()) {
            throw new BadRequestException("Field " + fieldName + " is required");
        }
    }

    private void requireMaxLength(String value, int max, String fieldName) {
        if (value != null && value.length() > max) {
            throw new BadRequestException("Field " + fieldName + " exceeds max length " + max);
        }
    }

    private String toString(Object value) {
        return value == null ? "" : value.toString();
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Expected integer but got: " + s);
            }
        }
        throw new BadRequestException("Expected integer but got: " + value);
    }

    private Integer toIntOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            if (s.isBlank()) return null;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Expected integer but got: " + s);
            }
        }
        return null;
    }

    private boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}

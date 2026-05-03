package com.alethicode.service.aitutor.parsons;

import com.alethicode.service.aitutor.review.ReviewProblemRatingService;
import com.alethicode.service.languagepack.impl.JudgeCheckResult;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService.JudgeUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Faded Parsons 能力主服务。
 *
 * <p>编排：
 * 题目元数据加载 → mastery 路由 → fading 决策 → token 切分 → distractor 生成
 * → 校验 schema → 写 {@code ai_parsons_session} → 写 {@code ai_learning_event}
 * → 返回 cardPayload（供 tutor_graph 透传给前端）。</p>
 *
 * <p>P0 阶段 grade 仅做 block-based 顺序比对；OJ Judge 接入留给后续增量。</p>
 */
@Service
public class ParsonsCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(ParsonsCapabilityService.class);
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ParsonsProperties properties;
    private final ParsonsTokenSegmenter segmenter;
    private final ParsonsDistractorGenerator distractorGenerator;
    private final AdaptiveFadingPolicy fadingPolicy;
    private final MasteryNfkProjectionService masteryNfkProjectionService;
    private final ParsonsWalkthroughEvaluator walkthroughEvaluator;
    private final LanguagePackProblemJudgeCheckService judgeService;
    private final ReviewProblemRatingService reviewProblemRatingService;

    public ParsonsCapabilityService(JdbcTemplate jdbcTemplate,
                                    ObjectMapper objectMapper,
                                    ParsonsProperties properties,
                                    ParsonsTokenSegmenter segmenter,
                                    ParsonsDistractorGenerator distractorGenerator,
                                    AdaptiveFadingPolicy fadingPolicy,
                                    MasteryNfkProjectionService masteryNfkProjectionService,
                                    ParsonsWalkthroughEvaluator walkthroughEvaluator,
                                    LanguagePackProblemJudgeCheckService judgeService,
                                    ReviewProblemRatingService reviewProblemRatingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.segmenter = segmenter;
        this.distractorGenerator = distractorGenerator;
        this.fadingPolicy = fadingPolicy;
        this.masteryNfkProjectionService = masteryNfkProjectionService;
        this.walkthroughEvaluator = walkthroughEvaluator;
        this.judgeService = judgeService;
        this.reviewProblemRatingService = reviewProblemRatingService;
    }

    @Transactional
    public DispatchResult dispatch(DispatchRequest req) {
        if (req.userId() == null || req.problemId() == null) {
            throw new IllegalArgumentException("userId 和 problemId 不能为空");
        }
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Parsons 能力当前关闭");
        }

        ProblemMeta meta = loadProblemMeta(req.problemId());
        if (meta.referenceCode() == null || meta.referenceCode().isBlank()) {
            throw new IllegalStateException("题目缺少 reference_solution_code，无法构造 Parsons 拼装题");
        }

        List<KcView> kcs = loadKcs(req.problemId());
        List<Long> kcIds = kcs.stream().map(KcView::id).toList();
        List<String> kcNames = kcs.stream().map(KcView::name).toList();

        Map<Long, MasteryWithSource> masteryByKc = masteryNfkProjectionService.getMasteryByKc(req.userId(), kcIds);

        FadingDecision decision;
        if (req.overrideFadingLevel() != null) {
            decision = fadingPolicy.decideForLevel(req.overrideFadingLevel());
        } else {
            decision = fadingPolicy.decide(masteryByKc);
        }

        List<ParsonsBlock> blocks = segmenter.segment(meta.referenceCode(), decision);
        List<ParsonsDistractor> distractors = decision.distractorCount() <= 0
                ? List.of()
                : distractorGenerator.generate(new ParsonsDistractorGenerator.GenerationContext(
                        req.userId(),
                        req.problemId(),
                        meta.title(),
                        meta.language(),
                        languageLabel(meta.language()),
                        kcIds,
                        kcNames,
                        blocks,
                        decision.distractorCount()));

        String sessionId = "ps-" + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();

        Map<String, Object> masterySnapshot = buildMasterySnapshot(masteryByKc, now);
        Map<String, Object> cardPayload = new LinkedHashMap<>();
        cardPayload.put("parsons_session_id", sessionId);
        cardPayload.put("fading_level", decision.fadingLevel());
        cardPayload.put("blocks", blocks.stream().map(this::blockToMap).toList());
        cardPayload.put("distractors", distractors.stream().map(this::distractorToMap).toList());
        cardPayload.put("mastery_snapshot", masterySnapshot);
        cardPayload.put("instructions", buildInstructions(decision, kcNames, meta.title()));
        cardPayload.put("language", meta.language());
        if (req.fsrsOrigin() != null && !req.fsrsOrigin().isBlank()) {
            cardPayload.put("fsrs_origin", req.fsrsOrigin());
        }
        if (req.previousSessionId() != null && !req.previousSessionId().isBlank()) {
            cardPayload.put("previous_session_id", req.previousSessionId());
        }

        persistSession(sessionId, req, meta, decision, blocks, distractors, masterySnapshot, now);
        recordEvent(req.userId(), req.problemId(), "parsons_dispatched", Map.of(
                "session_id", sessionId,
                "fading_level", decision.fadingLevel(),
                "kc_ids", kcIds,
                "source_card_id", req.sourceCardId() == null ? "" : req.sourceCardId(),
                "fsrs_origin", req.fsrsOrigin() == null ? "" : req.fsrsOrigin()
        ));

        return new DispatchResult(sessionId, cardPayload);
    }

    @Transactional
    public GradeResult grade(GradeRequest req) {
        if (req.parsonsSessionId() == null || req.parsonsSessionId().isBlank()) {
            throw new IllegalArgumentException("parsons_session_id 不能为空");
        }
        SessionRow row = loadSession(req.parsonsSessionId());
        List<Map<String, Object>> blockMaps = parseList(row.blocks());
        List<String> referenceOrder = blockMaps.stream()
                .map(m -> String.valueOf(m.get("id")))
                .toList();

        List<String> submitted = req.orderedBlockIds() == null ? List.of() : req.orderedBlockIds();
        boolean orderEqual = referenceOrder.equals(submitted);
        int newCount = row.submissionCount() + 1;

        // 失败 cascade 阈值
        int degradeAt = properties.getFailureCascade().getMaxAttemptsBeforeDegrade();
        int failfastAt = properties.getFailureCascade().getMaxAttemptsBeforeFailfast();

        String hint = null;
        String judgeStatus;
        boolean walkthroughRequired;
        boolean cascadeDegrade = false;
        boolean cascadeFailfast = false;
        boolean passed = false;
        boolean treatAsBlockFailForCascade = false;

        if (orderEqual) {
            // 设计稿 D4：block-based + execution-based 双重判分
            JudgeOutcome outcome = runJudgeForSubmission(row, blockMaps);
            judgeStatus = outcome.status;
            hint = outcome.hint;
            passed = "judge_ac".equals(outcome.status);
            walkthroughRequired = passed;
            if (!passed && !"judge_unavailable".equals(outcome.status)) {
                // judge_wa / judge_compile_error / judge_runtime_error 都算未通过，
                // 走和顺序错位一样的 cascade 计数
                treatAsBlockFailForCascade = true;
            }
        } else {
            judgeStatus = "block_fail";
            walkthroughRequired = false;
            int firstWrong = firstMismatchIndex(referenceOrder, submitted);
            hint = buildHint(blockMaps, firstWrong, newCount);
            treatAsBlockFailForCascade = true;
        }

        if (treatAsBlockFailForCascade) {
            if (newCount >= failfastAt) {
                cascadeFailfast = true;
            } else if (newCount >= degradeAt) {
                cascadeDegrade = true;
            }
        }

        jdbcTemplate.update("""
                update ai_parsons_session
                set submitted_order = cast(? as jsonb),
                    submission_count = ?,
                    judge_status = ?,
                    update_time = now(),
                    finalized_at = case when ? then now() else finalized_at end
                where id = ?
                """,
                toJson(submitted),
                newCount,
                judgeStatus,
                passed,
                req.parsonsSessionId());

        recordEvent(row.userId(), row.problemId(), "parsons_submitted", Map.of(
                "session_id", req.parsonsSessionId(),
                "result", judgeStatus,
                "attempts", newCount,
                "ordered_blocks", submitted
        ));
        if (cascadeFailfast) {
            recordEvent(row.userId(), row.problemId(), "parsons_failed_cascade", Map.of(
                    "session_id", req.parsonsSessionId(),
                    "attempts", newCount
            ));
            // Parsons + FSRS 闭环（设计稿 §11.1）：cascade_failfast 等价于学生对该错题
            // 再练一次仍未通过，按 FSRS 规则记 "again"，同时触发相似题生成。
            if (row.fsrsOrigin() != null && !row.fsrsOrigin().isBlank()) {
                reviewProblemRatingService.recordParsonsOutcome(
                        row.userId(), row.fsrsOrigin(), row.problemId(),
                        ReviewProblemRatingService.RATING_AGAIN);
            }
        }

        Integer nextFadingLevel = null;
        if (cascadeDegrade) {
            nextFadingLevel = Math.max(0, row.fadingLevel() - 1);
        }
        return new GradeResult(passed, hint, judgeStatus, walkthroughRequired,
                cascadeDegrade, cascadeFailfast, newCount,
                row.fadingLevel(), nextFadingLevel);
    }

    /**
     * 把已通过 block 顺序检查的拼装结果交给真实 OJ Judge 执行。
     *
     * <ul>
     *   <li>题目缺 {@code test_case_id} → {@code judge_unavailable}（设计稿要求 failfast，但
     *       不阻塞学生：返回 hint 让其重试，不计入 cascade）。</li>
     *   <li>Judge 服务暂不可用 → {@code judge_unavailable}，同上。</li>
     *   <li>编译失败 → {@code judge_compile_error}，hint 包含错误片段。</li>
     *   <li>有任意测试点未通过 → {@code judge_wa}，hint 指出第一个失败点的执行结果。</li>
     *   <li>全部测试点通过 → {@code judge_ac}，hint 为空。</li>
     * </ul>
     */
    private JudgeOutcome runJudgeForSubmission(SessionRow row, List<Map<String, Object>> blockMaps) {
        ProblemMeta meta = loadProblemMeta(row.problemId());
        if (meta.testCaseId() == null || meta.testCaseId().isBlank()) {
            return new JudgeOutcome("judge_unavailable", "题目尚未配置测试用例，暂时无法运行真实判题。");
        }
        String assembledCode = assembleCodeFromBlocks(blockMaps);
        if (assembledCode.isBlank()) {
            return new JudgeOutcome("judge_unavailable", "拼装结果为空，无法提交判题。");
        }
        String language = row.language() == null || row.language().isBlank()
                ? meta.language()
                : row.language();
        try {
            JudgeCheckResult result = judgeService.executeAgainstStoredTestCases(
                    assembledCode, language, meta.testCaseId(),
                    meta.timeLimitMs(), meta.memoryLimitMb());
            if (result.compileError() != null && !result.compileError().isBlank()) {
                return new JudgeOutcome("judge_compile_error",
                        "判题反馈：编译未通过 — " + shortenJudgeText(result.compileError()));
            }
            if (result.allPassed()) {
                return new JudgeOutcome("judge_ac", null);
            }
            JudgeCheckResult.CaseResult firstFailure = result.caseResults().stream()
                    .filter(c -> !c.passed())
                    .findFirst()
                    .orElse(null);
            String reason = firstFailure == null
                    ? "判题反馈：未通过全部测试用例。"
                    : "判题反馈：第 " + (firstFailure.index() + 1) + " 个测试点未通过 ("
                            + judgeStatusLabel(firstFailure.resultCode()) + ")"
                            + (firstFailure.error() == null || firstFailure.error().isBlank()
                                    ? ""
                                    : " — " + shortenJudgeText(firstFailure.error()));
            return new JudgeOutcome("judge_wa", reason);
        } catch (JudgeUnavailableException e) {
            log.warn("Parsons judge unavailable: {}", e.getMessage());
            return new JudgeOutcome("judge_unavailable", "判题服务暂时不可用，请稍后再提交一次。");
        } catch (RuntimeException e) {
            log.warn("Parsons judge run failed: {}", e.getMessage());
            return new JudgeOutcome("judge_unavailable", "判题执行失败：" + shortenJudgeText(e.getMessage()));
        }
    }

    private static String assembleCodeFromBlocks(List<Map<String, Object>> blockMaps) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> b : blockMaps) {
            int indent = b.get("indent") instanceof Number n ? n.intValue() : 0;
            String code = String.valueOf(b.getOrDefault("code", ""));
            sb.append("    ".repeat(Math.max(0, indent))).append(code).append("\n");
        }
        return sb.toString();
    }

    private static String judgeStatusLabel(int resultCode) {
        return switch (resultCode) {
            case -1 -> "WA";
            case -2 -> "compile_error";
            case 1 -> "cpu_time_limit";
            case 2 -> "real_time_limit";
            case 3 -> "memory_limit";
            case 4 -> "runtime_error";
            case 5 -> "system_error";
            default -> "code=" + resultCode;
        };
    }

    private static String shortenJudgeText(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        if (trimmed.length() <= 160) return trimmed;
        return trimmed.substring(0, 160) + "...";
    }

    private record JudgeOutcome(String status, String hint) {
    }

    @Transactional
    public WalkthroughResult submitWalkthrough(WalkthroughRequest req) {
        if (req.parsonsSessionId() == null || req.parsonsSessionId().isBlank()) {
            throw new IllegalArgumentException("parsons_session_id 不能为空");
        }
        SessionRow row = loadSession(req.parsonsSessionId());
        if (!"judge_ac".equalsIgnoreCase(row.judgeStatus())) {
            throw new IllegalStateException("当前会话尚未通过判题阶段，无法提交 walkthrough");
        }
        List<Map<String, Object>> blockMaps = parseList(row.blocks());
        List<ParsonsBlock> blocks = blockMaps.stream().map(this::mapToBlock).toList();
        ProblemMeta meta = loadProblemMeta(row.problemId());

        ParsonsWalkthroughEvaluator.Result evalResult =
                walkthroughEvaluator.evaluate(req.text(), blocks, meta.title());

        int attempts = row.walkthroughAttempts() + 1;
        boolean canRewrite = !evalResult.passed()
                && attempts <= properties.getWalkthrough().getMaxRewriteAttempts();
        String breakthroughNotebookId = null;
        if (evalResult.passed()) {
            breakthroughNotebookId = writeBreakthroughNotebook(row, req.text(), evalResult.score(), meta);
        }

        jdbcTemplate.update("""
                update ai_parsons_session
                set walkthrough_text = ?,
                    walkthrough_score = ?,
                    walkthrough_attempts = ?,
                    breakthrough_notebook_id = coalesce(?, breakthrough_notebook_id),
                    update_time = now(),
                    finalized_at = case when ? then now() else finalized_at end
                where id = ?
                """,
                req.text(),
                evalResult.score(),
                attempts,
                breakthroughNotebookId,
                evalResult.passed() || !canRewrite,
                req.parsonsSessionId());

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("session_id", req.parsonsSessionId());
        evt.put("walkthrough_score", evalResult.score());
        evt.put("walkthrough_text", req.text());
        evt.put("attempts", attempts);
        recordEvent(row.userId(), row.problemId(), "parsons_walkthrough_submitted", evt);
        if (evalResult.passed() && breakthroughNotebookId != null) {
            recordEvent(row.userId(), row.problemId(), "parsons_breakthrough", Map.of(
                    "session_id", req.parsonsSessionId(),
                    "notebook_id", breakthroughNotebookId
            ));
            // Parsons + FSRS 闭环（设计稿 §11.1）：walkthrough 通过 = 学生真的理解了，
            // 把对应错题包条目自动 rate 为 good 推进 FSRS。
            if (row.fsrsOrigin() != null && !row.fsrsOrigin().isBlank()) {
                reviewProblemRatingService.recordParsonsOutcome(
                        row.userId(), row.fsrsOrigin(), row.problemId(),
                        ReviewProblemRatingService.RATING_GOOD);
            }
        }

        return new WalkthroughResult(evalResult.score(), evalResult.feedback(),
                evalResult.passed(), canRewrite, breakthroughNotebookId);
    }

    /**
     * IDOR 防御：调用任意 user-facing parsons 端点（{@code loadCard} / {@code grade} /
     * {@code submitWalkthrough}）前必须先调本方法校验 sessionId 归属，避免学生 A 用学生 B 的
     * sessionId 越权读写。internal 路径（tutor-graph 调 {@code /internal/ai-tutor/parsons/*}）
     * 是 trusted source，不需要也不应该调本方法。
     *
     * <p>session 不存在 / 不属于该 user 都抛 {@link IllegalArgumentException} 且消息一致，
     * 防止攻击者通过响应区分"不存在"与"无权限"做枚举攻击。</p>
     */
    public void assertSessionOwnedBy(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            throw new IllegalArgumentException("Parsons 会话不存在: " + sessionId);
        }
        Long ownerId;
        try {
            ownerId = jdbcTemplate.queryForObject(
                    "select user_id from ai_parsons_session where id = ?",
                    Long.class, sessionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Parsons 会话不存在: " + sessionId);
        }
        if (!java.util.Objects.equals(ownerId, userId)) {
            throw new IllegalArgumentException("Parsons 会话不存在: " + sessionId);
        }
    }

    public Map<String, Object> loadCard(String sessionId) {
        SessionRow row = loadSession(sessionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parsons_session_id", row.id());
        payload.put("fading_level", row.fadingLevel());
        payload.put("blocks", parseList(row.blocks()));
        payload.put("distractors", parseList(row.distractors()));
        payload.put("mastery_snapshot", parseObject(row.masterySnapshot()));
        payload.put("instructions", row.instructions() == null ? "" : row.instructions());
        if (row.language() != null) payload.put("language", row.language());
        if (row.fsrsOrigin() != null && !row.fsrsOrigin().isBlank()) payload.put("fsrs_origin", row.fsrsOrigin());
        if (row.previousSessionId() != null && !row.previousSessionId().isBlank()) {
            payload.put("previous_session_id", row.previousSessionId());
        }
        return payload;
    }

    // ---- internal helpers ----

    private ProblemMeta loadProblemMeta(Long problemId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select p.id, p.title, p.reference_solution_code,
                           coalesce(nullif(p.reference_solution_language, ''), 'Python3') as language,
                           p.test_case_id,
                           coalesce(p.time_limit, 1000) as time_limit,
                           coalesce(p.memory_limit, 256) as memory_limit
                    from problem p
                    where p.id = ?
                    """, (rs, n) -> new ProblemMeta(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("reference_solution_code"),
                            rs.getString("language"),
                            rs.getString("test_case_id"),
                            rs.getInt("time_limit"),
                            rs.getInt("memory_limit")
                    ),
                    problemId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("题目不存在: " + problemId);
        }
    }

    private List<KcView> loadKcs(Long problemId) {
        return jdbcTemplate.query("""
                select kc.id, kc.name
                from ai_problem_kc_mapping m
                join ai_knowledge_component kc on kc.id = m.kc_id
                where m.problem_id = ?
                order by m.weight desc nulls last, kc.id asc
                """,
                (rs, n) -> new KcView(rs.getLong("id"), rs.getString("name")),
                problemId);
    }

    private void persistSession(String sessionId, DispatchRequest req, ProblemMeta meta,
                                FadingDecision decision, List<ParsonsBlock> blocks,
                                List<ParsonsDistractor> distractors, Map<String, Object> masterySnapshot,
                                Instant now) {
        jdbcTemplate.update("""
                insert into ai_parsons_session(
                    id, user_id, problem_id, workflow_session_id, source_card_id,
                    previous_session_id, fsrs_origin, language, fading_level,
                    mastery_snapshot, blocks, distractors,
                    submission_count, walkthrough_attempts, create_time, update_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), 0, 0, ?, ?)
                """,
                sessionId,
                req.userId(),
                req.problemId(),
                req.workflowSessionId(),
                req.sourceCardId(),
                req.previousSessionId(),
                req.fsrsOrigin(),
                meta.language(),
                decision.fadingLevel(),
                toJson(masterySnapshot),
                toJson(blocks.stream().map(this::blockToMap).toList()),
                toJson(distractors.stream().map(this::distractorToMap).toList()),
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private SessionRow loadSession(String sessionId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select id, user_id, problem_id, language, fading_level,
                           mastery_snapshot::text as mastery_snapshot,
                           blocks::text as blocks,
                           distractors::text as distractors,
                           submission_count, judge_status,
                           walkthrough_attempts,
                           previous_session_id, fsrs_origin,
                           '' as instructions
                    from ai_parsons_session
                    where id = ?
                    """, (rs, n) -> new SessionRow(
                            rs.getString("id"),
                            rs.getLong("user_id"),
                            rs.getLong("problem_id"),
                            rs.getString("language"),
                            rs.getInt("fading_level"),
                            rs.getString("mastery_snapshot"),
                            rs.getString("blocks"),
                            rs.getString("distractors"),
                            rs.getInt("submission_count"),
                            rs.getString("judge_status"),
                            rs.getInt("walkthrough_attempts"),
                            rs.getString("previous_session_id"),
                            rs.getString("fsrs_origin"),
                            rs.getString("instructions")
                    ),
                    sessionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Parsons 会话不存在: " + sessionId);
        }
    }

    private String writeBreakthroughNotebook(SessionRow row, String walkthroughText, double score, ProblemMeta meta) {
        String notebookId = "nb-" + UUID.randomUUID().toString().replace("-", "");
        List<Long> kcIds = parseKcIdsFromMasterySnapshot(row.masterySnapshot());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source", "parsons");
        evidence.put("parsons_session_id", row.id());
        evidence.put("score", score);
        jdbcTemplate.update("""
                insert into ai_learner_notebook(
                    id, user_id, problem_id, language, error_taxonomy,
                    root_cause, fix_outcome, student_reflection, tags, evidence_ptr,
                    is_deleted, entry_type, breakthrough_insight, kc_ids,
                    misconception_distribution, create_time, update_time
                ) values (?, ?, ?, ?, 'breakthrough',
                          ?, '', '', cast('[]' as jsonb), cast(? as jsonb),
                          false, 'breakthrough', ?, cast(? as jsonb),
                          cast('{}' as jsonb), now(), now())
                """,
                notebookId,
                row.userId(),
                row.problemId(),
                meta.language(),
                "Parsons 拼装顿悟笔记",
                toJson(evidence),
                walkthroughText,
                toJson(kcIds)
        );
        return notebookId;
    }

    private void recordEvent(Long userId, Long problemId, String eventType, Map<String, Object> extra) {
        String taxonomyRaw = extractString(extra, "error_taxonomy");
        if (taxonomyRaw == null) {
            taxonomyRaw = extractString(extra, "error_category");
        }
        String errorTaxonomy = taxonomyRaw == null
                ? null
                : com.alethicode.service.aitutor.contract.ErrorTaxonomy.normalize(taxonomyRaw);
        String rootCause = extractString(extra, "root_cause");
        String detectorName = extractString(extra, "detector_name");
        try {
            jdbcTemplate.update("""
                    insert into ai_learning_event(user_id, problem_id, event_type, extra_data,
                                                  error_taxonomy, root_cause, detector_name)
                    values (?, ?, ?, cast(? as jsonb), ?, ?, ?)
                    """,
                    userId, problemId, eventType, toJson(extra),
                    errorTaxonomy, rootCause, detectorName);
        } catch (RuntimeException e) {
            log.warn("Failed to record parsons learning event {}: {}", eventType, e.getMessage());
        }
    }

    private String extractString(Map<String, Object> source, String key) {
        if (source == null) return null;
        Object value = source.get(key);
        if (value == null) return null;
        String s = String.valueOf(value).strip();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> blockToMap(ParsonsBlock b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id());
        m.put("code", b.code());
        m.put("indent", b.indent());
        m.put("fading_state", b.fadingState().key());
        if (b.fadeHint() != null) m.put("fade_hint", b.fadeHint());
        return m;
    }

    private ParsonsBlock mapToBlock(Map<String, Object> m) {
        String state = String.valueOf(m.getOrDefault("fading_state", "visible")).toUpperCase();
        ParsonsBlock.FadingState fadingState;
        try {
            fadingState = ParsonsBlock.FadingState.valueOf(state);
        } catch (IllegalArgumentException e) {
            fadingState = ParsonsBlock.FadingState.VISIBLE;
        }
        return new ParsonsBlock(
                String.valueOf(m.get("id")),
                String.valueOf(m.getOrDefault("code", "")),
                m.get("indent") instanceof Number n ? n.intValue() : 0,
                fadingState,
                m.get("fade_hint") == null ? null : String.valueOf(m.get("fade_hint"))
        );
    }

    private Map<String, Object> distractorToMap(ParsonsDistractor d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id());
        m.put("code", d.code());
        m.put("indent", d.indent());
        m.put("source", d.source().key());
        if (d.kcHint() != null && !d.kcHint().isBlank()) m.put("kc_hint", d.kcHint());
        return m;
    }

    private Map<String, Object> buildMasterySnapshot(Map<Long, MasteryWithSource> by, Instant now) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("decision_at", now.toString());
        Map<String, Object> routing = new LinkedHashMap<>();
        for (Map.Entry<Long, MasteryWithSource> e : by.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("mastery", e.getValue().mastery());
            entry.put("source", e.getValue().source().key());
            if (e.getValue().nfkSequenceLength() != null) {
                entry.put("nfk_sequence_length", e.getValue().nfkSequenceLength());
            }
            if (e.getValue().fallbackReason() != null) {
                entry.put("fallback_reason", e.getValue().fallbackReason().key());
            }
            routing.put(String.valueOf(e.getKey()), entry);
        }
        snapshot.put("routing", routing);
        return snapshot;
    }

    private String buildInstructions(FadingDecision decision, List<String> kcNames, String title) {
        String kcLabel = kcNames.isEmpty() ? "本题" : String.join("、", kcNames);
        return switch (decision.fadingLevel()) {
            case 0 -> "把以下代码块按正确顺序拖拽到右侧，拼出可运行的代码（围绕 " + kcLabel + "）。";
            case 1 -> "拖拽代码块拼出正确顺序，注意有 1 个块需要根据提示补全（涉及 " + kcLabel + "）。";
            case 2 -> "拖拽并完成 2 个被遮蔽的关键步骤；当心 distractor 块（涉及 " + kcLabel + "）。";
            default -> "高难度模式：请补全所有遮蔽块并排好顺序。识别 distractor 是关键（涉及 " + kcLabel + "）。";
        };
    }

    private static String languageLabel(String language) {
        if (language == null || language.isBlank()) return "Python 3";
        return switch (language) {
            case "Python3", "Python" -> "Python 3";
            case "Cpp", "C++" -> "C++";
            default -> language;
        };
    }

    private List<Long> parseKcIdsFromMasterySnapshot(String json) {
        Map<String, Object> snap = parseObject(json);
        Object routing = snap.get("routing");
        if (!(routing instanceof Map<?, ?> map)) return List.of();
        List<Long> ids = new ArrayList<>();
        for (Object key : map.keySet()) {
            try {
                ids.add(Long.parseLong(String.valueOf(key)));
            } catch (NumberFormatException ignored) {
                // 非数字 key 忽略
            }
        }
        return ids;
    }

    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, LIST_OF_MAP);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static int firstMismatchIndex(List<String> ref, List<String> got) {
        int n = Math.min(ref.size(), got.size());
        for (int i = 0; i < n; i++) {
            if (!ref.get(i).equals(got.get(i))) return i;
        }
        return n;
    }

    private static String buildHint(List<Map<String, Object>> blockMaps, int firstWrong, int attempts) {
        if (firstWrong < 0 || firstWrong >= blockMaps.size()) {
            return "顺序未通过，请重新审视块的依赖关系。";
        }
        Map<String, Object> b = blockMaps.get(firstWrong);
        String code = String.valueOf(b.getOrDefault("code", ""));
        String trimmed = code.length() > 40 ? code.substring(0, 40) + "..." : code;
        if (attempts == 1) {
            return "第 " + (firstWrong + 1) + " 步开始出现错位，期待的逻辑是「" + trimmed + "」。";
        }
        if (attempts == 2) {
            return "再想想这一步的依赖关系，是不是缺了上一步的变量？「" + trimmed + "」。";
        }
        return "已多次错在第 " + (firstWrong + 1) + " 步，建议重新审题或回看示例。";
    }

    // ---- DTO records ----

    public record DispatchRequest(
            Long userId,
            Long problemId,
            String workflowSessionId,
            String sourceCardId,
            String previousSessionId,
            String fsrsOrigin,
            Integer overrideFadingLevel
    ) {
    }

    public record DispatchResult(String parsonsSessionId, Map<String, Object> cardPayload) {
    }

    public record GradeRequest(String parsonsSessionId, List<String> orderedBlockIds) {
    }

    public record GradeResult(
            boolean passed,
            String hint,
            String judgeStatus,
            boolean walkthroughRequired,
            boolean cascadeDegrade,
            boolean cascadeFailfast,
            int attempts,
            int currentFadingLevel,
            Integer nextFadingLevel
    ) {
    }

    public record WalkthroughRequest(String parsonsSessionId, String text) {
    }

    public record WalkthroughResult(double score, String feedback, boolean passed,
                                     boolean canRewrite, String breakthroughNotebookId) {
    }

    private record ProblemMeta(Long id, String title, String referenceCode, String language,
                                String testCaseId, int timeLimitMs, int memoryLimitMb) {
    }

    private record KcView(Long id, String name) {
    }

    private record SessionRow(
            String id,
            Long userId,
            Long problemId,
            String language,
            int fadingLevel,
            String masterySnapshot,
            String blocks,
            String distractors,
            int submissionCount,
            String judgeStatus,
            int walkthroughAttempts,
            String previousSessionId,
            String fsrsOrigin,
            String instructions
    ) {
    }
}

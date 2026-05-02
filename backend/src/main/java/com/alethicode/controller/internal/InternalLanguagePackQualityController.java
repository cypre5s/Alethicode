package com.alethicode.controller.internal;

import com.alethicode.config.InternalServiceKeyMatcher;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.quality.LanguagePackInitQualityReportService;
import com.alethicode.service.languagepack.quality.LintViolation;
import com.alethicode.service.languagepack.quality.ReferenceLintContext;
import com.alethicode.service.languagepack.quality.ReferenceLintReport;
import com.alethicode.service.languagepack.quality.ReferenceSolutionLinter;
import com.alethicode.service.languagepack.quality.ReferenceSolutionSelfValidator;
import com.alethicode.service.languagepack.quality.SamplesSynchronizer;
import com.alethicode.service.languagepack.quality.SelfValidationCaseResult;
import com.alethicode.service.languagepack.quality.SelfValidationReport;
import com.alethicode.service.languagepack.quality.SelfValidationSampleResult;
import com.alethicode.service.languagepack.quality.TitleDedupV2Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal API for tutor_graph init validation 节点：
 * 把 quality 闸门的 4 个核心服务暴露成 5 个 endpoint。
 *
 * <p>认证沿用 {@code X-Internal-Service-Key}，与 {@link InternalAITutorToolController} 一致。</p>
 */
@RestController
@RequestMapping("/internal/language-pack/quality")
public class InternalLanguagePackQualityController {

    private final ReferenceSolutionLinter linter;
    private final ReferenceSolutionSelfValidator selfValidator;
    private final SamplesSynchronizer samplesSynchronizer;
    private final TitleDedupV2Service titleDedupV2Service;
    private final LanguagePackInitQualityReportService qualityReportService;
    private final InternalServiceKeyMatcher internalServiceKeyMatcher;

    public InternalLanguagePackQualityController(
            ReferenceSolutionLinter linter,
            ReferenceSolutionSelfValidator selfValidator,
            SamplesSynchronizer samplesSynchronizer,
            TitleDedupV2Service titleDedupV2Service,
            LanguagePackInitQualityReportService qualityReportService,
            InternalServiceKeyMatcher internalServiceKeyMatcher
    ) {
        this.linter = linter;
        this.selfValidator = selfValidator;
        this.samplesSynchronizer = samplesSynchronizer;
        this.titleDedupV2Service = titleDedupV2Service;
        this.qualityReportService = qualityReportService;
        this.internalServiceKeyMatcher = internalServiceKeyMatcher;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }

    @PostMapping("/reference-lint")
    public ResponseEntity<Object> referenceLint(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        String code = readString(request, "reference_solution_code");
        String language = readOptionalString(request, "language", "Python3");
        ReferenceLintContext context = readLintContext(request);
        ReferenceLintReport report = linter.lint(code, language, context);
        return ResponseEntity.ok(toLintReportView(report));
    }

    @PostMapping("/self-validate")
    public ResponseEntity<Object> selfValidate(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        LanguagePackProblemPackage pkg = readProblemPackage(request);
        SelfValidationReport report = selfValidator.validate(pkg);
        return ResponseEntity.ok(toSelfValidationReportView(report));
    }

    @PostMapping("/samples-sync")
    public ResponseEntity<Object> samplesSync(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        LanguagePackProblemPackage pkg = readProblemPackage(request);
        SelfValidationReport report = selfValidator.validate(pkg);
        if (!report.allPassed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "self-validation 未通过，无法同步 samples",
                    "report", toSelfValidationReportView(report)
            ));
        }
        LanguagePackProblemPackage synced = samplesSynchronizer.synchronize(pkg, report);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("display_id", synced.displayId());
        body.put("samples", synced.samples().stream().map(s -> Map.of(
                "input", s.input(),
                "output", s.output()
        )).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/title-dedup-v2")
    public ResponseEntity<Object> titleDedupV2(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        Object raw = request.get("candidates");
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("candidates is required and must be a list");
        }
        List<TitleDedupV2Service.DedupCandidate> dedupCandidates = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw2)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw2;
            dedupCandidates.add(new TitleDedupV2Service.DedupCandidate(
                    readOptionalString(map, "display_id", ""),
                    readOptionalString(map, "title", ""),
                    readOptionalString(map, "description", ""),
                    readOptionalString(map, "source_title", ""),
                    readOptionalInt(map, "page_range_start"),
                    readOptionalInt(map, "page_range_end")
            ));
        }
        List<TitleDedupV2Service.DedupResult> results = titleDedupV2Service.dedup(dedupCandidates);
        return ResponseEntity.ok(Map.of(
                "results", results.stream().map(r -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("display_id", r.candidate().displayId());
                    view.put("original_title", r.candidate().title());
                    view.put("title", r.title());
                    view.put("action", r.action().name());
                    view.put("reason", r.reason());
                    view.put("description_md5", r.descriptionMd5());
                    view.put("signature_key", r.signatureKey());
                    return view;
                }).toList()
        ));
    }

    @GetMapping("/report/{taskId}")
    public ResponseEntity<Object> getReport(
            @PathVariable Long taskId,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return qualityReportService.findByTaskId(taskId)
                .<ResponseEntity<Object>>map(record -> ResponseEntity.ok(toQualityReportView(record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "quality report not found for task " + taskId)));
    }

    // ---- view builders ----

    private Map<String, Object> toLintReportView(ReferenceLintReport report) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("passable", report.passable());
        view.put("hard_violations", report.hardViolations().stream()
                .map(this::toViolationView).toList());
        view.put("soft_violations", report.softViolations().stream()
                .map(this::toViolationView).toList());
        return view;
    }

    private Map<String, Object> toViolationView(LintViolation v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rule_code", v.ruleCode());
        map.put("severity", v.severity());
        map.put("message", v.message());
        map.put("line", v.line());
        return map;
    }

    private Map<String, Object> toSelfValidationReportView(SelfValidationReport report) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("display_id", report.displayId());
        view.put("all_passed", report.allPassed());
        view.put("lint_blocked", report.lintBlocked());
        view.put("compile_failed", report.compileFailed());
        view.put("compile_error", report.compileError().orElse(""));
        view.put("failure_summary", report.failureSummary().orElse(""));
        view.put("duration_ms", report.duration() == null ? 0 : report.duration().toMillis());
        view.put("test_case_results", report.testCaseResults().stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("case_key", c.caseKey());
            map.put("status", c.status());
            map.put("expected_output", c.expectedOutput());
            map.put("actual_output", c.actualOutput());
            map.put("diff", c.diff());
            map.put("result_code", c.resultCode());
            return map;
        }).toList());
        view.put("sample_results", report.sampleResults().stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("index", s.index());
            map.put("status", s.status());
            map.put("expected_output", s.expectedOutput());
            map.put("actual_output", s.actualOutput());
            map.put("diff", s.diff());
            return map;
        }).toList());
        if (report.lintReport() != null) {
            view.put("lint_report", toLintReportView(report.lintReport()));
        }
        return view;
    }

    private Map<String, Object> toQualityReportView(LanguagePackInitQualityReportService.QualityReportRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("init_task_id", record.initTaskId());
        view.put("language_pack_id", record.languagePackId());
        view.put("total_packages", record.totalPackages());
        view.put("self_validated_count", record.selfValidatedCount());
        view.put("failed_count", record.failedCount());
        view.put("retried_count", record.retriedCount());
        view.put("escalated_count", record.escalatedCount());
        view.put("failure_breakdown", record.failureBreakdown());
        view.put("lint_summary", record.lintSummary());
        view.put("escalated_packages", record.escalatedPackages());
        view.put("duration_ms", record.duration() == null ? 0 : record.duration().toMillis());
        view.put("create_time", record.createTime() == null ? null : record.createTime().toString());
        return view;
    }

    // ---- request readers ----

    private ReferenceLintContext readLintContext(Map<String, Object> request) {
        return new ReferenceLintContext(
                readOptionalString(request, "description", ""),
                readOptionalString(request, "input_description", ""),
                readOptionalString(request, "output_description", "")
        );
    }

    @SuppressWarnings("unchecked")
    private LanguagePackProblemPackage readProblemPackage(Map<String, Object> request) {
        String code = readString(request, "reference_solution_code");
        String language = readOptionalString(request, "language",
                readOptionalString(request, "reference_solution_language", "Python3"));
        Object testCasesRaw = request.get("test_cases");
        if (!(testCasesRaw instanceof List<?> testCasesList) || testCasesList.isEmpty()) {
            throw new IllegalArgumentException("test_cases is required and must be a non-empty list");
        }
        List<LanguagePackProblemPackage.TestCase> testCases = new ArrayList<>();
        for (Object item : testCasesList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) rawMap;
            testCases.add(new LanguagePackProblemPackage.TestCase(
                    readOptionalString(map, "input", ""),
                    readOptionalString(map, "expected_output", readOptionalString(map, "output", ""))
            ));
        }

        Object samplesRaw = request.get("samples");
        List<LanguagePackProblemPackage.Sample> samples = new ArrayList<>();
        if (samplesRaw instanceof List<?> samplesList) {
            for (Object item : samplesList) {
                if (!(item instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) rawMap;
                samples.add(new LanguagePackProblemPackage.Sample(
                        readOptionalString(map, "input", ""),
                        readOptionalString(map, "output", "")
                ));
            }
        }

        Object judgeConfigRaw = request.get("judge_config");
        Integer timeLimit = 3000;
        Integer memoryLimit = 256;
        if (judgeConfigRaw instanceof Map<?, ?> rawConfig) {
            Map<String, Object> judgeConfig = (Map<String, Object>) rawConfig;
            timeLimit = readOptionalInt(judgeConfig, "time_limit_ms");
            memoryLimit = readOptionalInt(judgeConfig, "memory_limit_mb");
            if (timeLimit == null) timeLimit = 3000;
            if (memoryLimit == null) memoryLimit = 256;
        }

        return new LanguagePackProblemPackage(
                readOptionalString(request, "display_id", ""),
                readOptionalString(request, "title", ""),
                readOptionalString(request, "description", ""),
                readOptionalString(request, "input_description", ""),
                readOptionalString(request, "output_description", ""),
                samples,
                testCases,
                Map.of(language, code),
                timeLimit,
                memoryLimit,
                readOptionalString(request, "difficulty", "Low"),
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                null,
                language,
                code
        );
    }

    private String readString(Map<String, Object> body, String name) {
        Object value = body.get(name);
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.toString();
    }

    private String readOptionalString(Map<String, Object> body, String name, String defaultValue) {
        Object value = body.get(name);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private Integer readOptionalInt(Map<String, Object> body, String name) {
        Object value = body.get(name);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void validateServiceKey(String key) {
        if (!internalServiceKeyMatcher.isConfigured()) {
            throw new AccessDeniedException("Internal service key not configured");
        }
        if (key == null || key.isBlank()) {
            throw new AccessDeniedException("Missing X-Internal-Service-Key header");
        }
        if (!internalServiceKeyMatcher.matches(key)) {
            throw new AccessDeniedException("Invalid internal service key");
        }
    }
}

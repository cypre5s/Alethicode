package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.ProblemPackageWriteOptions;
import com.alethicode.service.languagepack.ProblemPackageWriteResult;
import com.alethicode.service.languagepack.ProblemPackageWriteService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProblemPackageWriteServiceImpl implements ProblemPackageWriteService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;

    public ProblemPackageWriteServiceImpl(JdbcTemplate jdbcTemplate,
                                          ObjectMapper objectMapper,
                                          AlethicodeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ProblemPackageWriteResult writeProblem(LanguagePackProblemPackage problemPackage, ProblemPackageWriteOptions options) {
        validatePackage(problemPackage);
        String displayId = trimToNull(problemPackage.displayId());
        if (displayId == null) {
            throw new IllegalStateException("problem package display_id is required");
        }
        if (!LanguagePackDisplayIdPolicy.isValid(displayId)) {
            throw new IllegalStateException("problem package display_id must match ^PPT\\d+-\\d+$");
        }
        String testCaseId = writeTestCasesToDisk(problemPackage.testCases(), options.spj());
        List<Map<String, Object>> score = buildTestCaseScore(
                problemPackage.testCases(),
                options.spj(),
                options.testCaseScore()
        );

        Long problemId = jdbcTemplate.queryForObject(
                """
                INSERT INTO problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    reference_solution_language, reference_solution_code,
                    visible, is_public, difficulty, source, statistic_info,
                    is_ai_generated, visibility_status, create_time, last_update_time
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, cast(? as jsonb), '',
                    cast(? as jsonb), cast(? as jsonb), ?, ?, ?,
                    ?, ?,
                    ?, ?, ?, ?, cast(? as jsonb),
                    ?, ?, now(), now()
                ) RETURNING id
                """,
                Long.class,
                displayId,
                problemPackage.title(),
                problemPackage.description(),
                problemPackage.inputDescription(),
                problemPackage.outputDescription(),
                writeJson(toSampleMaps(problemPackage.samples())),
                testCaseId,
                writeJson(score),
                writeJson(resolveLanguages(problemPackage)),
                writeJson(problemPackage.template()),
                options.createdById(),
                safeInt(problemPackage.timeLimit(), 1000),
                safeInt(problemPackage.memoryLimit(), 256),
                trimToEmpty(problemPackage.referenceSolutionLanguage()),
                trimToEmpty(problemPackage.referenceSolutionCode()),
                options.visible(),
                options.isPublic(),
                normalizeDifficulty(problemPackage.difficulty()),
                trimToEmpty(options.source()),
                writeJson(options.statisticInfo() == null ? Map.of() : options.statisticInfo()),
                options.aiGenerated(),
                trimToNull(options.visibilityStatus()) == null ? "class_private" : options.visibilityStatus()
        );
        if (problemId == null) {
            throw new IllegalStateException("Failed to insert problem");
        }

        for (String tag : options.tags() == null ? List.<String>of() : options.tags()) {
            String normalized = trimToNull(tag);
            if (normalized == null) {
                continue;
            }
            long tagId = findOrCreateTagId(normalized);
            jdbcTemplate.update(
                    "INSERT INTO problem_problem_tags(problem_id, problemtag_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    problemId,
                    tagId
            );
        }

        return new ProblemPackageWriteResult(problemId, displayId, testCaseId);
    }

    private void validatePackage(LanguagePackProblemPackage problemPackage) {
        if (problemPackage == null) {
            throw new IllegalStateException("problem package is required");
        }
        if (trimToNull(problemPackage.title()) == null) {
            throw new IllegalStateException("problem package title is required");
        }
        if (trimToNull(problemPackage.description()) == null) {
            throw new IllegalStateException("problem package description is required");
        }
        if (trimToNull(problemPackage.displayId()) == null) {
            throw new IllegalStateException("problem package display_id is required");
        }
        if (!LanguagePackDisplayIdPolicy.isValid(problemPackage.displayId())) {
            throw new IllegalStateException("problem package display_id must match ^PPT\\d+-\\d+$");
        }
        if (problemPackage.samples() == null || problemPackage.samples().isEmpty()) {
            throw new IllegalStateException("problem package samples are required");
        }
        if (problemPackage.testCases() == null || problemPackage.testCases().isEmpty()) {
            throw new IllegalStateException("problem package test cases are required");
        }
        if (problemPackage.template() == null || problemPackage.template().isEmpty()) {
            throw new IllegalStateException("problem package template is required");
        }
    }

    private List<String> resolveLanguages(LanguagePackProblemPackage problemPackage) {
        if (problemPackage.template() != null && !problemPackage.template().isEmpty()) {
            return List.copyOf(problemPackage.template().keySet());
        }
        String referenceLanguage = trimToNull(problemPackage.referenceSolutionLanguage());
        if (referenceLanguage != null) {
            return List.of(referenceLanguage);
        }
        return List.of("Python3");
    }

    private String writeTestCasesToDisk(List<LanguagePackProblemPackage.TestCase> testCases, boolean spj) {
        String testCaseId = UUID.randomUUID().toString().replace("-", "");
        Path testCaseDir = Path.of(properties.getSystem().getTestCaseDir(), testCaseId);
        try {
            Files.createDirectories(testCaseDir);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("spj", spj);
            Map<String, Object> testCaseMap = new LinkedHashMap<>();
            for (int i = 0; i < testCases.size(); i++) {
                LanguagePackProblemPackage.TestCase testCase = testCases.get(i);
                String inputName = (i + 1) + ".in";
                String input = trimToEmpty(testCase.input());
                Files.writeString(testCaseDir.resolve(inputName), input, StandardCharsets.UTF_8);

                Map<String, Object> caseInfo = new LinkedHashMap<>();
                caseInfo.put("input_name", inputName);
                caseInfo.put("input_size", input.getBytes(StandardCharsets.UTF_8).length);
                if (!spj) {
                    String outputName = (i + 1) + ".out";
                    String output = trimToEmpty(testCase.output());
                    Files.writeString(testCaseDir.resolve(outputName), output, StandardCharsets.UTF_8);
                    caseInfo.put("output_name", outputName);
                    caseInfo.put("output_size", output.getBytes(StandardCharsets.UTF_8).length);
                    caseInfo.put("stripped_output_md5", md5(output.strip()));
                }
                testCaseMap.put(String.valueOf(i + 1), caseInfo);
            }
            info.put("test_cases", testCaseMap);
            Files.writeString(testCaseDir.resolve("info"), writeJson(info), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write test cases to disk: " + exception.getMessage(), exception);
        }
        return testCaseId;
    }

    private List<Map<String, Object>> buildTestCaseScore(List<LanguagePackProblemPackage.TestCase> testCases,
                                                         boolean spj,
                                                         List<Map<String, Object>> overrideScore) {
        if (overrideScore != null && !overrideScore.isEmpty()) {
            return normalizeScoreOverride(overrideScore, spj);
        }
        List<Map<String, Object>> score = new ArrayList<>();
        int scorePerCase = testCases.isEmpty() ? 0 : 100 / testCases.size();
        int remainder = testCases.isEmpty() ? 0 : 100 % testCases.size();
        for (int i = 0; i < testCases.size(); i++) {
            LinkedHashMap<String, Object> scoreRow = new LinkedHashMap<>();
            scoreRow.put("input_name", (i + 1) + ".in");
            scoreRow.put("output_name", spj ? null : (i + 1) + ".out");
            scoreRow.put("score", i == 0 ? scorePerCase + remainder : scorePerCase);
            score.add(scoreRow);
        }
        return score;
    }

    private List<Map<String, Object>> normalizeScoreOverride(List<Map<String, Object>> overrideScore, boolean spj) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> scoreRow : overrideScore) {
            String inputName = trimToNull(stringVal(scoreRow.get("input_name")));
            if (inputName == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("input_name", inputName);
            item.put("output_name", spj ? null : trimToNull(stringVal(scoreRow.get("output_name"))));
            item.put("score", safeInt(parseInteger(scoreRow.get("score")), 0));
            normalized.add(item);
        }
        return normalized;
    }

    private List<Map<String, Object>> toSampleMaps(List<LanguagePackProblemPackage.Sample> samples) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LanguagePackProblemPackage.Sample sample : samples) {
            result.add(Map.of(
                    "input", trimToEmpty(sample.input()),
                    "output", trimToEmpty(sample.output())
            ));
        }
        return result;
    }

    private String normalizeDifficulty(String difficulty) {
        String normalized = trimToNull(difficulty);
        if (normalized == null) {
            return "Mid";
        }
        return switch (normalized) {
            case "Low", "Mid", "High" -> normalized;
            default -> "Mid";
        };
    }

    private int safeInt(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String normalized = trimToNull(stringVal(value));
        if (normalized == null) {
            return null;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private long findOrCreateTagId(String tagName) {
        Long existing = jdbcTemplate.query(
                "SELECT id FROM problem_tag WHERE name = ? ORDER BY id ASC LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null,
                tagName
        );
        if (existing != null) {
            return existing;
        }
        Long created = jdbcTemplate.queryForObject(
                "INSERT INTO problem_tag(name) VALUES (?) RETURNING id",
                Long.class,
                tagName
        );
        if (created == null) {
            throw new IllegalStateException("Failed to create tag: " + tagName);
        }
        return created;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return java.util.HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 not available", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}

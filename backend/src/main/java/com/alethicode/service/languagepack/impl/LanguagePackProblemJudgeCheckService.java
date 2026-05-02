package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.ai.AiCircuitBreaker;
import com.alethicode.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class LanguagePackProblemJudgeCheckService {

    private static final Logger log = LoggerFactory.getLogger(LanguagePackProblemJudgeCheckService.class);

    private static final Duration PING_TIMEOUT = Duration.ofMillis(1500);
    private static final Duration JUDGE_TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final AiCircuitBreaker aiCircuitBreaker;

    public LanguagePackProblemJudgeCheckService(JdbcTemplate jdbcTemplate,
                                               ObjectMapper objectMapper,
                                               AlethicodeProperties properties,
                                               AiCircuitBreaker aiCircuitBreaker) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.aiCircuitBreaker = aiCircuitBreaker;
    }

    public JudgeCheckResult executeReferenceSolution(String referenceSolutionCode,
                                                     String language,
                                                     List<String> inputs,
                                                     int timeLimitMs,
                                                     int memoryLimitMb) {
        Map<String, Object> languageConfig = resolveLanguageConfig(language);
        if (languageConfig == null) {
            throw new IllegalStateException("Language " + language + " is not supported for judge execution");
        }

        JudgeServerCandidate server = pickAvailableJudgeServer();
        if (server == null) {
            throw new JudgeUnavailableException("暂无可用的评测服务器");
        }

        Path tempDir = null;
        try {
            tempDir = prepareTempTestCaseDir(inputs);
            Map<String, Object> payload = buildPayload(
                    languageConfig, referenceSolutionCode, timeLimitMs, memoryLimitMb, tempDir.getFileName().toString()
            );
            Map<String, Object> response = requestJudge(server.serviceUrl(), payload);
            return parseJudgeResponse(response, inputs.size());
        } catch (JudgeUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Judge execution failed: " + e.getMessage(), e);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    /**
     * 直接复用题目已有的 {@code test_case_id} 跑判题，不申请临时目录。
     * Faded Parsons 拼装代码、可视化执行、其它需要"和真实题面对答案"的链路会调用这里。
     *
     * <p>判题结果与 {@link #executeReferenceSolution} 同源，因此 caller 拿到的
     * {@link JudgeCheckResult} 字段语义完全一致。</p>
     */
    public JudgeCheckResult executeAgainstStoredTestCases(String sourceCode,
                                                          String language,
                                                          String testCaseId,
                                                          int timeLimitMs,
                                                          int memoryLimitMb) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("sourceCode 不能为空");
        }
        if (testCaseId == null || testCaseId.isBlank()) {
            throw new IllegalArgumentException("testCaseId 不能为空");
        }
        Map<String, Object> languageConfig = resolveLanguageConfig(language);
        if (languageConfig == null) {
            throw new IllegalStateException("Language " + language + " is not supported for judge execution");
        }

        JudgeServerCandidate server = pickAvailableJudgeServer();
        if (server == null) {
            throw new JudgeUnavailableException("暂无可用的评测服务器");
        }

        try {
            Map<String, Object> payload = buildPayload(
                    languageConfig, sourceCode, timeLimitMs, memoryLimitMb, testCaseId
            );
            Map<String, Object> response = requestJudge(server.serviceUrl(), payload);
            return parseJudgeResponse(response, 0);
        } catch (JudgeUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Judge execution failed: " + e.getMessage(), e);
        }
    }

    // --- payload construction ---

    private Map<String, Object> buildPayload(Map<String, Object> languageConfig,
                                             String sourceCode,
                                             int timeLimitMs,
                                             int memoryLimitMb,
                                             String testCaseId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language_config", languageConfig);
        payload.put("src", sourceCode);
        payload.put("max_cpu_time", timeLimitMs);
        payload.put("max_memory", 1024L * 1024L * memoryLimitMb);
        payload.put("test_case_id", testCaseId);
        payload.put("output", true);
        return payload;
    }

    // --- response parsing ---

    private JudgeCheckResult parseJudgeResponse(Map<String, Object> response, int expectedCaseCount) {
        if (response == null || response.isEmpty()) {
            throw new IllegalStateException("Judge server returned empty response");
        }

        if (isTruthy(response.get("err"))) {
            String compileError = text(response.get("data"));
            return JudgeCheckResult.compileFailure(compileError);
        }

        List<Map<String, Object>> cases = extractResultData(response.get("data"));
        if (cases.isEmpty()) {
            throw new IllegalStateException("Judge server returned no result data");
        }

        List<JudgeCheckResult.CaseResult> caseResults = new ArrayList<>();
        boolean allPassed = true;
        for (int i = 0; i < cases.size(); i++) {
            Map<String, Object> item = cases.get(i);
            int resultCode = parseInt(item.get("result"));
            String actualOutput = text(item.get("output"));
            String error = text(item.get("error"));
            boolean passed = resultCode == 0;
            if (!passed) {
                allPassed = false;
            }
            caseResults.add(new JudgeCheckResult.CaseResult(i, passed, actualOutput, error, resultCode));
        }

        return new JudgeCheckResult(allPassed, caseResults, "");
    }

    // --- temp test case directory ---

    private Path prepareTempTestCaseDir(List<String> inputs) throws IOException {
        Path rootDir = Path.of(properties.getSystem().getTestCaseDir());
        Files.createDirectories(rootDir);
        Path tempDir = Files.createTempDirectory(rootDir, "langpack_check_");

        Map<String, Object> testCasesMap = new LinkedHashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            String seq = String.valueOf(i + 1);
            Files.writeString(tempDir.resolve(seq + ".in"), inputs.get(i));
            Files.writeString(tempDir.resolve(seq + ".out"), "");

            Map<String, Object> caseInfo = new LinkedHashMap<>();
            caseInfo.put("input_name", seq + ".in");
            caseInfo.put("output_name", seq + ".out");
            caseInfo.put("stripped_output_md5", null);
            testCasesMap.put(seq, caseInfo);
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("test_cases", testCasesMap);
        info.put("spj", null);
        Files.writeString(tempDir.resolve("info"), writeJson(info));
        return tempDir;
    }

    // --- judge server selection ---

    private JudgeServerCandidate pickAvailableJudgeServer() {
        List<JudgeServerCandidate> candidates = jdbcTemplate.query(
                """
                select hostname, service_url, ip, last_heartbeat
                from judge_server
                where is_disabled = false
                order by task_number asc, create_time asc
                """,
                (rs, rowNum) -> new JudgeServerCandidate(
                        rs.getString("hostname"),
                        rs.getString("service_url"),
                        rs.getString("ip"),
                        rs.getTimestamp("last_heartbeat")
                )
        );

        Instant threshold = Instant.now().minusSeconds(6);
        for (JudgeServerCandidate candidate : candidates) {
            if (candidate.lastHeartbeat() != null && candidate.lastHeartbeat().toInstant().isAfter(threshold)) {
                return candidate;
            }
        }
        for (JudgeServerCandidate candidate : candidates) {
            if (isServiceReachable(candidate.serviceUrl())) {
                return candidate;
            }
            String ip = text(candidate.ip());
            if (!ip.isBlank() && isServiceReachable("http://" + ip + ":8080")) {
                return new JudgeServerCandidate(candidate.hostname(), "http://" + ip + ":8080", ip, candidate.lastHeartbeat());
            }
        }
        return null;
    }

    private boolean isServiceReachable(String serviceUrl) {
        String normalized = text(serviceUrl);
        if (normalized.isBlank()) {
            return false;
        }
        try {
            HttpResponse<String> response = aiCircuitBreaker.executeWithInstance("languagePackJudge", "language pack judge ping", () -> {
                URI pingUri = normalizeBaseUri(normalized).resolve("ping");
                HttpRequest request = HttpRequest.newBuilder(pingUri).timeout(PING_TIMEOUT).GET().build();
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            });
            return response.statusCode() >= 200 && response.statusCode() < 500;
        } catch (Exception e) {
            log.debug("Judge server ping unreachable: {}", normalized, e);
            return false;
        }
    }

    // --- HTTP judge call ---

    private Map<String, Object> requestJudge(String serviceUrl, Map<String, Object> payload)
            throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = aiCircuitBreaker.executeWithInstance("languagePackJudge", "language pack judge execute", () -> {
                URI judgeUri = normalizeBaseUri(serviceUrl).resolve("judge");
                HttpRequest request = HttpRequest.newBuilder(judgeUri)
                        .timeout(JUDGE_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .header("X-Judge-Server-Token", HashUtils.sha256(properties.getJudgeServer().getToken()))
                        .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
                        .build();
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            });
        } catch (IOException | InterruptedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Language pack judge execution failed: " + exception.getMessage(), exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Judge server returned HTTP " + response.statusCode()
                    + ": " + (response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length()))));
        }
        String raw = response.body();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IOException("Judge server returned non-JSON response: " + raw.substring(0, Math.min(200, raw.length())), e);
        }
    }

    // --- language config resolution ---

    private Map<String, Object> resolveLanguageConfig(String language) {
        Map<String, Object> option = readMapOption("languages");
        if (option != null && option.get("languages") instanceof List<?> configured) {
            for (Object item : configured) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object name = map.get("name");
                if (name != null && language.equals(String.valueOf(name))) {
                    Object config = map.get("config");
                    if (config instanceof Map<?, ?> configMap) {
                        return castToStringObjectMap(configMap);
                    }
                }
            }
        }
        return defaultLanguageConfig(language);
    }

    private Map<String, Object> defaultLanguageConfig(String language) {
        return switch (language) {
            case "C" -> Map.of(
                    "compile", Map.of(
                            "src_name", "main.c", "exe_name", "main",
                            "max_cpu_time", 3000, "max_real_time", 10000, "max_memory", 268435456,
                            "compile_command", "/usr/bin/gcc -DONLINE_JUDGE -O2 -w -fmax-errors=3 -std=c17 {src_path} -lm -o {exe_path}"
                    ),
                    "run", Map.of(
                            "command", "{exe_path}", "seccomp_rule", "c_cpp",
                            "env", List.of("LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8")
                    )
            );
            case "C++" -> Map.of(
                    "compile", Map.of(
                            "src_name", "main.cpp", "exe_name", "main",
                            "max_cpu_time", 10000, "max_real_time", 20000, "max_memory", 1073741824,
                            "compile_command", "/usr/bin/g++ -DONLINE_JUDGE -O2 -w -fmax-errors=3 -std=c++20 {src_path} -lm -o {exe_path}"
                    ),
                    "run", Map.of(
                            "command", "{exe_path}", "seccomp_rule", "c_cpp",
                            "env", List.of("LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8")
                    )
            );
            case "Java" -> {
                Map<String, Object> compile = new LinkedHashMap<>();
                compile.put("src_name", "Main.java");
                compile.put("exe_name", "Main");
                compile.put("max_cpu_time", 5000);
                compile.put("max_real_time", 10000);
                compile.put("max_memory", -1);
                compile.put("compile_command", "/usr/bin/javac {src_path} -d {exe_dir}");
                Map<String, Object> run = new LinkedHashMap<>();
                run.put("command", "/usr/bin/java -cp {exe_dir} -XX:MaxRAM={max_memory}k Main");
                run.put("seccomp_rule", null);
                run.put("env", List.of("LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8"));
                run.put("memory_limit_check_only", 1);
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("compile", compile);
                config.put("run", run);
                yield config;
            }
            case "Python3" -> Map.of(
                    "compile", Map.of(
                            "src_name", "solution.py", "exe_name", "solution.py",
                            "max_cpu_time", 3000, "max_real_time", 10000, "max_memory", 134217728,
                            "compile_command", "/usr/bin/python3 -m py_compile {src_path}"
                    ),
                    "run", Map.of(
                            "command", "/usr/bin/python3 -BS {exe_path}", "seccomp_rule", "general",
                            // PYTHONHASHSEED=42 让 set/dict 默认遍历顺序在所有评测中保持 deterministic，
                            // 与 docs/plans/2026-04-28-language-pack-init-quality-design.md § 4 (D7) 对齐。
                            "env", List.of(
                                    "LANG=en_US.UTF-8",
                                    "LANGUAGE=en_US:en",
                                    "LC_ALL=en_US.UTF-8",
                                    "PYTHONHASHSEED=42"
                            )
                    )
            );
            default -> null;
        };
    }

    // --- utility methods ---

    private Map<String, Object> readMapOption(String key) {
        String rawJson;
        try {
            rawJson = jdbcTemplate.queryForObject("select value::text from sys_options where key = ?", String.class, key);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<Map<String, Object>> extractResultData(Object rawData) {
        if (!(rawData instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add(castToStringObjectMap(map));
            }
        }
        rows.sort(Comparator.comparingInt(one -> parseInt(one.get("test_case"))));
        return rows;
    }

    private URI normalizeBaseUri(String serviceUrl) {
        String normalized = text(serviceUrl);
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return URI.create(normalized);
    }

    private Map<String, Object> castToStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String t = value.toString().trim();
        return !t.isEmpty() && !"false".equalsIgnoreCase(t) && !"0".equals(t) && !"null".equalsIgnoreCase(t);
    }

    private int parseInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String t = text(raw);
        if (t.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize judge payload", e);
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    // --- inner types ---

    private record JudgeServerCandidate(String hostname, String serviceUrl, String ip, Timestamp lastHeartbeat) {}

    public static class JudgeUnavailableException extends RuntimeException {
        public JudgeUnavailableException(String message) {
            super(message);
        }
    }
}

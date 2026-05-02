package com.alethicode.service.aitutor.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.ai.AiCircuitBreaker;
import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;
import com.alethicode.service.aitutor.language.TutorLanguageSupport;
import com.alethicode.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

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

public class JudgeBackedExecutionTraceService implements ExecutionTraceService {

    private static final Logger log = LoggerFactory.getLogger(JudgeBackedExecutionTraceService.class);

    private static final Duration PING_TIMEOUT = Duration.ofMillis(1500);
    private static final Duration JUDGE_TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final AiCircuitBreaker aiCircuitBreaker;

    public JudgeBackedExecutionTraceService(JdbcTemplate jdbcTemplate,
                                            ObjectMapper objectMapper,
                                            AlethicodeProperties properties,
                                            AiCircuitBreaker aiCircuitBreaker) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.aiCircuitBreaker = aiCircuitBreaker;
    }

    @Override
    public Map<String, Object> explain(LanguageAwareTutorContext context,
                                       String code,
                                       String inputSample,
                                       String failureReason,
                                       Map<String, Object> submissionEvidence) {
        String language = context.currentLanguage();
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isBlank()) {
            return failed("当前还没有可执行的 " + TutorLanguageSupport.displayLanguage(language) + " 代码");
        }
        Map<String, Object> languageConfig = resolveLanguageConfig(language);
        if (languageConfig == null) {
            return failed("当前还不支持生成 " + TutorLanguageSupport.displayLanguage(language) + " 的运行轨迹");
        }
        JudgeServerCandidate server = pickAvailableJudgeServer();
        if (server == null) {
            return failed("当前没有可用的判题节点，暂时无法生成运行轨迹");
        }

        Path debugDir = null;
        try {
            debugDir = prepareDebugTestCaseDir(inputSample == null ? "" : inputSample);
            Map<String, Object> judgePayload = new LinkedHashMap<>();
            judgePayload.put("language_config", languageConfig);
            judgePayload.put("src", normalizedCode);
            judgePayload.put("max_cpu_time", 5000);
            judgePayload.put("max_memory", 1024L * 1024L * 256);
            judgePayload.put("test_case_id", debugDir.getFileName().toString());
            judgePayload.put("output", true);

            Map<String, Object> response = requestJudge(server.serviceUrl(), judgePayload, JUDGE_TIMEOUT);
            List<Map<String, Object>> cases = extractResultData(response.get("data"));
            Map<String, Object> first = cases.isEmpty() ? Map.of() : cases.get(0);
            int resultCode = parseInt(first.get("result"));
            String actualOutput = text(first.get("output"));
            String error = text(first.get("error"));
            String finalFailureReason = !error.isBlank() ? error : (failureReason == null ? "" : failureReason.trim());

            List<Map<String, Object>> steps = new ArrayList<>();
            steps.add(step(
                    0,
                    "使用输入样例编译并运行 " + TutorLanguageSupport.displayLanguage(language) + " 程序",
                    Map.of("language", language),
                    actualOutput,
                    "系统已经基于真实判题环境执行了当前代码。"
            ));
            if (!text(inputSample).isBlank()) {
                steps.add(step(
                        1,
                        "读取输入样例",
                        Map.of("input_sample", inputSample),
                        actualOutput,
                        "本次运行使用了题目样例输入，便于对照真实行为。"
                ));
            }
            if (resultCode != 0 || !finalFailureReason.isBlank()) {
                steps.add(step(
                        steps.size(),
                        "定位偏离证据",
                        Map.of("judge_result", judgeResultLabel(resultCode)),
                        actualOutput,
                        buildFailureExplanation(language, finalFailureReason, submissionEvidence)
                ));
            } else {
                steps.add(step(
                        steps.size(),
                        "检查输出行为",
                        Map.of("judge_result", "accepted"),
                        actualOutput,
                        "当前代码在真实运行中成功通过，本次运行轨迹用于帮助你理解程序如何从输入走到输出。"
                ));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ready");
            result.put("input_sample", inputSample == null ? "" : inputSample);
            result.put("steps", steps);
            result.put("divergence_step", steps.size() - 1);
            result.put("failure_reason", finalFailureReason);
            return result;
        } catch (Exception exception) {
            return failed("当前无法生成 " + TutorLanguageSupport.displayLanguage(language) + " 的运行轨迹：" + exception.getMessage());
        } finally {
            deleteDirectoryQuietly(debugDir);
        }
    }

    private Map<String, Object> step(int index,
                                     String code,
                                     Map<String, Object> variables,
                                     String output,
                                     String explanation) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("step_index", index);
        step.put("line_number", 0);
        step.put("code", code);
        step.put("variables", variables);
        step.put("output", output == null ? "" : output);
        step.put("branch_taken", "");
        step.put("teaching_explanation", explanation);
        return step;
    }

    private String buildFailureExplanation(String language, String failureReason, Map<String, Object> submissionEvidence) {
        String normalized = text(failureReason);
        if (!normalized.isBlank()) {
            return "真实运行证据表明当前 " + TutorLanguageSupport.displayLanguage(language) + " 程序在这里偏离预期："
                    + shorten(normalized, 220);
        }
        String statisticErr = text(TutorLanguageSupport.castMap(submissionEvidence.get("statistic_info")).get("err_info"));
        if (!statisticErr.isBlank()) {
            return "判题返回的失败证据显示程序在这里偏离预期：" + shorten(statisticErr, 220);
        }
        return "真实运行已经完成，但当前只拿到了有限证据。请先检查最近修改的几行以及输出格式是否与题目一致。";
    }

    private String judgeResultLabel(int resultCode) {
        return switch (resultCode) {
            case 0 -> "accepted";
            case -2 -> "compile_error";
            case 1 -> "cpu_time_limit";
            case 2 -> "real_time_limit";
            case 3 -> "memory_limit";
            case 4 -> "runtime_error";
            case 5 -> "system_error";
            default -> "unknown";
        };
    }

    private String shorten(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private Map<String, Object> failed(String reason) {
        return Map.of(
                "status", "failed",
                "input_sample", "",
                "steps", List.of(),
                "divergence_step", -1,
                "failure_reason", reason
        );
    }

    private Path prepareDebugTestCaseDir(String userInput) throws IOException {
        Path rootDir = Path.of(properties.getSystem().getTestCaseDir());
        Files.createDirectories(rootDir);
        Path tempDir = Files.createTempDirectory(rootDir, "trace_");
        Files.writeString(tempDir.resolve("1.in"), userInput);
        Files.writeString(tempDir.resolve("1.out"), "");
        Map<String, Object> caseInfo = new LinkedHashMap<>();
        caseInfo.put("input_name", "1.in");
        caseInfo.put("output_name", "1.out");
        caseInfo.put("stripped_output_md5", null);

        Map<String, Object> testCases = new LinkedHashMap<>();
        testCases.put("1", caseInfo);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("test_cases", testCases);
        info.put("spj", null);
        Files.writeString(tempDir.resolve("info"), writeJson(info));
        return tempDir;
    }

    private JudgeServerCandidate pickAvailableJudgeServer() {
        List<JudgeServerCandidate> candidates = jdbcTemplate.query(
                """
                select hostname, service_url, ip, last_heartbeat, task_number
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
            if (!text(candidate.ip()).isBlank() && isServiceReachable("http://" + candidate.ip() + ":8080")) {
                return new JudgeServerCandidate(candidate.hostname(), "http://" + candidate.ip() + ":8080", candidate.ip(), candidate.lastHeartbeat());
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
            HttpResponse<String> response = aiCircuitBreaker.executeWithInstance("judgeServer", "execution trace ping", () -> {
                URI pingUri = normalizeBaseUri(normalized).resolve("ping");
                HttpRequest request = HttpRequest.newBuilder(pingUri)
                        .timeout(PING_TIMEOUT)
                        .GET()
                        .build();
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            });
            return response.statusCode() >= 200 && response.statusCode() < 500;
        } catch (Exception e) {
            log.debug("judgeServiceReachable: ping failed for serviceUrl={}", normalized, e);
            return false;
        }
    }

    private Map<String, Object> requestJudge(String serviceUrl, Map<String, Object> payload, Duration timeout)
            throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = aiCircuitBreaker.executeWithInstance("judgeServer", "execution trace judge", () -> {
                URI judgeUri = normalizeBaseUri(serviceUrl).resolve("/judge");
                HttpRequest request = HttpRequest.newBuilder(judgeUri)
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("X-Judge-Server-Token", HashUtils.sha256(properties.getJudgeServer().getToken()))
                        .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
                        .build();
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            });
        } catch (IOException | InterruptedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Judge execution trace request failed: " + exception.getMessage(), exception);
        }
        String raw = response.body();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private URI normalizeBaseUri(String serviceUrl) {
        String normalized = text(serviceUrl);
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return URI.create(normalized);
    }

    private Map<String, Object> resolveLanguageConfig(String language) {
        Map<String, Object> option = readMapOption("languages");
        if (option != null && option.get("languages") instanceof List<?> configured) {
            for (Object item : configured) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object name = map.get("name");
                if (name != null && TutorLanguageSupport.normalizeLanguage(name).equals(TutorLanguageSupport.normalizeLanguage(language))) {
                    Object config = map.get("config");
                    if (config instanceof Map<?, ?> configMap) {
                        return castToStringObjectMap(configMap);
                    }
                }
            }
        }
        return defaultLanguageConfig(TutorLanguageSupport.normalizeLanguage(language));
    }

    private Map<String, Object> defaultLanguageConfig(String language) {
        return switch (language) {
            case "C" -> Map.of(
                    "compile", Map.of(
                            "src_name", "main.c",
                            "exe_name", "main",
                            "max_cpu_time", 3000,
                            "max_real_time", 10000,
                            "max_memory", 268435456,
                            "compile_command", "/usr/bin/gcc -DONLINE_JUDGE -O2 -w -fmax-errors=3 -std=c17 {src_path} -lm -o {exe_path}"
                    ),
                    "run", Map.of(
                            "command", "{exe_path}",
                            "seccomp_rule", "c_cpp",
                            "env", List.of("LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8")
                    )
            );
            case "C++" -> Map.of(
                    "compile", Map.of(
                            "src_name", "main.cpp",
                            "exe_name", "main",
                            "max_cpu_time", 10000,
                            "max_real_time", 20000,
                            "max_memory", 1073741824,
                            "compile_command", "/usr/bin/g++ -DONLINE_JUDGE -O2 -w -fmax-errors=3 -std=c++20 {src_path} -lm -o {exe_path}"
                    ),
                    "run", Map.of(
                            "command", "{exe_path}",
                            "seccomp_rule", "c_cpp",
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
                            "src_name", "solution.py",
                            "exe_name", "solution.py",
                            "max_cpu_time", 3000,
                            "max_real_time", 10000,
                            "max_memory", 134217728,
                            "compile_command", "/usr/bin/python3 -m py_compile {src_path}"
                    ),
                    "run", Map.of(
                            "command", "/usr/bin/python3 -BS {exe_path}",
                            "seccomp_rule", "general",
                            // PYTHONHASHSEED=42：与提交主链路与 reference self-validation 共享同一 deterministic env。
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
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private Map<String, Object> castToStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
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

    private int parseInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String text = text(raw);
        if (text.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
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
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record JudgeServerCandidate(
            String hostname,
            String serviceUrl,
            String ip,
            Timestamp lastHeartbeat
    ) {
    }
}

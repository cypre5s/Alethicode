package com.alethicode.service.submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SubmissionDataCollector {

    private static final Logger log = LoggerFactory.getLogger(SubmissionDataCollector.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern PYTHON_EXCEPTION_PATTERN =
            Pattern.compile("(\\w+(?:Error|Exception|Warning))\\s*:");

    private final AlethicodeProperties properties;
    private final ObjectMapper objectMapper;

    public SubmissionDataCollector(AlethicodeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * @param result   judge verdict: 0=AC, -1=WA, -2=CE, 1-4=TLE/MLE/RE, 5=SE
     * @param response raw judge HTTP response (null on system error)
     */
    public void collect(String submissionId,
                        long userId,
                        long problemId,
                        String problemDisplayId,
                        String problemTitle,
                        String language,
                        String code,
                        int result,
                        Map<String, Object> response,
                        Map<String, Object> statisticInfo,
                        Instant submittedAt) {
        String dataDir = properties.getSystem().getSubmissionDataDir();
        if (dataDir == null || dataDir.isBlank()) {
            return;
        }

        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("submission_id", submissionId);
            record.put("user_id", userId);
            record.put("problem_id", problemId);
            record.put("problem_display_id", problemDisplayId);
            record.put("problem_title", problemTitle);
            record.put("language", language);
            record.put("code", code);
            record.put("result", result);
            record.put("result_label", resultLabel(result));

            String errInfo = statisticInfo != null
                    ? String.valueOf(statisticInfo.getOrDefault("err_info", ""))
                    : "";
            record.put("error_info", errInfo);
            record.put("error_type", extractErrorType(errInfo, result));

            if (statisticInfo != null) {
                record.put("time_cost", statisticInfo.get("time_cost"));
                record.put("memory_cost", statisticInfo.get("memory_cost"));
                record.put("passed_test_case_count", statisticInfo.get("passed_test_case_count"));
                record.put("total_test_case_count", statisticInfo.get("total_test_case_count"));
                record.put("partial_score", statisticInfo.get("partial_score"));
            }

            record.put("submitted_at", submittedAt != null ? submittedAt.toString() : Instant.now().toString());
            record.put("collected_at", Instant.now().toString());

            String date = LocalDate.now(ZoneId.systemDefault()).format(DATE_FMT);
            Path dir = Path.of(dataDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(date + ".jsonl");

            String line = objectMapper.writeValueAsString(record) + "\n";
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            log.debug("Submission data collected: {} -> {}", submissionId, file);
        } catch (IOException e) {
            log.warn("Failed to collect submission data for {}: {}", submissionId, e.getMessage());
        }
    }

    private String extractErrorType(String errInfo, int result) {
        if (errInfo == null || errInfo.isEmpty()) {
            return resultLabel(result);
        }
        Matcher m = PYTHON_EXCEPTION_PATTERN.matcher(errInfo);
        if (m.find()) {
            return m.group(1);
        }
        return resultLabel(result);
    }

    private static String resultLabel(int result) {
        return switch (result) {
            case 0 -> "Accepted";
            case -1 -> "WrongAnswer";
            case -2 -> "CompileError";
            case 1, 2 -> "TimeLimitExceeded";
            case 3 -> "MemoryLimitExceeded";
            case 4 -> "RuntimeError";
            case 5 -> "SystemError";
            default -> "Unknown_" + result;
        };
    }
}

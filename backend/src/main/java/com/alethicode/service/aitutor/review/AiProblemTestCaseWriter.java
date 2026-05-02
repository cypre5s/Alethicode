package com.alethicode.service.aitutor.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 特化题测试用例落盘助手（Phase 3 抽离）。
 * 把 N 条 {input, output} 写入 {testCaseDir}/{testCaseId}/，并生成 OJ runtime 期望的 info 元数据。
 */
@Component
class AiProblemTestCaseWriter {

    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;

    AiProblemTestCaseWriter(ObjectMapper objectMapper, AlethicodeProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    void writeTestCases(String testCaseId, List<Map<String, Object>> testCases) {
        Path dir = Path.of(properties.getSystem().getTestCaseDir(), testCaseId);
        try {
            Files.createDirectories(dir);
            Map<String, Object> testCaseInfo = new LinkedHashMap<>();
            testCaseInfo.put("spj", false);
            Map<String, Object> infoCases = new LinkedHashMap<>();
            testCaseInfo.put("test_cases", infoCases);

            for (int i = 0; i < testCases.size(); i++) {
                Map<String, Object> tc = testCases.get(i);
                String input = ensureTrailingNewline(normalizeLineEnding(stringValue(tc.get("input"))));
                String output = ensureTrailingNewline(normalizeLineEnding(stringValue(tc.get("output"))));
                String inputName = (i + 1) + ".in";
                String outputName = (i + 1) + ".out";
                Files.writeString(dir.resolve(inputName), input, StandardCharsets.UTF_8);
                Files.writeString(dir.resolve(outputName), output, StandardCharsets.UTF_8);

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("stripped_output_md5", md5Hex(rstripWhitespace(output.getBytes(StandardCharsets.UTF_8))));
                info.put("input_size", input.getBytes(StandardCharsets.UTF_8).length);
                info.put("output_size", output.getBytes(StandardCharsets.UTF_8).length);
                info.put("input_name", inputName);
                info.put("output_name", outputName);
                infoCases.put(String.valueOf(i + 1), info);
            }

            Files.writeString(dir.resolve("info"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(testCaseInfo),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("AI特化题测试用例写入失败: " + e.getMessage(), e);
        }
    }

    String buildTestCaseScoreJson(int count) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (int i = 1; i <= count; i++) scores.put(String.valueOf(i), 100 / count);
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String normalizeLineEnding(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String ensureTrailingNewline(String content) {
        if (content.isEmpty() || content.endsWith("\n")) return content;
        return content + "\n";
    }

    private byte[] rstripWhitespace(byte[] content) {
        int end = content.length;
        while (end > 0 && (content[end - 1] == ' ' || content[end - 1] == '\t'
                || content[end - 1] == '\n' || content[end - 1] == '\r')) {
            end--;
        }
        byte[] trimmed = new byte[end];
        System.arraycopy(content, 0, trimmed, 0, end);
        return trimmed;
    }

    private String md5Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 failed", e);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

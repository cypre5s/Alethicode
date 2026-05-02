package com.alethicode.service.aitutor.assessment;

import com.alethicode.service.ai.AiModelGateway;

import java.util.LinkedHashMap;
import java.util.Map;

public class PythonCodeQualityAssessmentService implements CodeQualityAssessmentService {

    private final AiModelGateway aiModelGateway;

    public PythonCodeQualityAssessmentService(AiModelGateway aiModelGateway) {
        this.aiModelGateway = aiModelGateway;
    }

    @Override
    public Map<String, Object> assess(String code, String language, String problemDescription) {
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isBlank()) {
            throw new IllegalStateException("code is required for code quality assessment");
        }
        Map<String, Object> raw = aiModelGateway.callForJson(
                """
                你是 Python 代码质量评估助手。请严格评估代码质量并返回结构化 JSON。
                """,
                """
                【题目描述】
                %s

                【学生代码】
                ```python
                %s
                ```

                请输出 JSON：
                {
                  "readability": 1,
                  "readability_comment": "建议",
                  "efficiency": 1,
                  "efficiency_comment": "建议",
                  "style": 1,
                  "style_comment": "建议"
                }
                """.formatted(problemDescription == null ? "" : problemDescription.trim(), normalizedCode)
        );

        return normalize(raw);
    }

    static Map<String, Object> normalize(Map<String, Object> raw) {
        int readability = requireScore(raw, "readability");
        int efficiency = requireScore(raw, "efficiency");
        int style = requireScore(raw, "style");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readability", readability);
        result.put("readability_comment", requireText(raw, "readability_comment"));
        result.put("efficiency", efficiency);
        result.put("efficiency_comment", requireText(raw, "efficiency_comment"));
        result.put("style", style);
        result.put("style_comment", requireText(raw, "style_comment"));
        result.put("overall", roundOneDecimal((readability + efficiency + style) / 3.0));
        return result;
    }

    private static int requireScore(Map<String, Object> payload, String key) {
        Object raw = payload.get(key);
        int value;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else {
            try {
                value = Integer.parseInt(String.valueOf(raw).trim());
            } catch (Exception exception) {
                throw new IllegalStateException(key + " must be an integer", exception);
            }
        }
        if (value < 1 || value > 5) {
            throw new IllegalStateException(key + " must be in [1, 5]");
        }
        return value;
    }

    private static String requireText(Map<String, Object> payload, String key) {
        String value = payload == null ? "" : String.valueOf(payload.getOrDefault(key, "")).trim();
        if (value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
        return value;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

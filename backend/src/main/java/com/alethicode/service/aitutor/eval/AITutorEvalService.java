package com.alethicode.service.aitutor.eval;

import java.util.LinkedHashMap;
import java.util.Map;

public class AITutorEvalService {

    public Map<String, Object> summarize(Map<String, Object> traceGrade) {
        double schemaPass = toScore(traceGrade.get("schema_pass"));
        double pedagogyPass = toScore(traceGrade.get("pedagogy_pass"));
        double helpfulness = clamp(traceGrade.get("helpfulness"));
        double answerLeak = clamp(traceGrade.get("answer_leak"));
        double contextRecall = clamp(traceGrade.get("context_recall"));
        double contextPrecision = clamp(traceGrade.get("context_precision"));
        double latency = clamp(traceGrade.get("latency"));
        double stability = clamp(traceGrade.get("stability"));
        double reward = ((schemaPass + pedagogyPass + helpfulness + contextRecall + contextPrecision + latency + stability) / 7.0)
                * (1.0 - answerLeak);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schema_pass", schemaPass);
        summary.put("pedagogy_pass", pedagogyPass);
        summary.put("answer_leak", answerLeak);
        summary.put("helpfulness", helpfulness);
        summary.put("context_recall", contextRecall);
        summary.put("context_precision", contextPrecision);
        summary.put("latency", latency);
        summary.put("stability", stability);
        summary.put("reward", Math.round(reward * 1000.0) / 1000.0);
        return summary;
    }

    public Map<String, Object> buildDatasetInput(
            Map<String, Object> evidenceSummary,
            Map<String, Object> decision,
            Map<String, Object> guardrail
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidence", evidenceSummary);
        payload.put("decision", decision);
        payload.put("guardrail", guardrail);
        return payload;
    }

    public Map<String, Object> buildExpectation(Map<String, Object> traceGrade) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema_pass", traceGrade.getOrDefault("schema_pass", false));
        payload.put("pedagogy_pass", traceGrade.getOrDefault("pedagogy_pass", false));
        payload.put("helpfulness", traceGrade.getOrDefault("helpfulness", 0.0));
        payload.put("answer_leak", traceGrade.getOrDefault("answer_leak", 0.0));
        return payload;
    }

    private double toScore(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        return clamp(value);
    }

    private double clamp(Object value) {
        if (value instanceof Number number) {
            return Math.max(0.0, Math.min(1.0, number.doubleValue()));
        }
        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(String.valueOf(value))));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}

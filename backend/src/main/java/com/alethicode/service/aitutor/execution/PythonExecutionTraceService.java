package com.alethicode.service.aitutor.execution;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;
import com.alethicode.service.aitutor.language.TutorLanguageSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PythonExecutionTraceService implements ExecutionTraceService {

    private final AiModelGateway aiModelGateway;

    public PythonExecutionTraceService(AiModelGateway aiModelGateway) {
        this.aiModelGateway = aiModelGateway;
    }

    @Override
    public Map<String, Object> explain(LanguageAwareTutorContext context,
                                       String code,
                                       String inputSample,
                                       String failureReason,
                                       Map<String, Object> submissionEvidence) {
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isBlank()) {
            return failed("当前还没有可执行的 " + TutorLanguageSupport.displayLanguage(context.currentLanguage()) + " 代码");
        }
        if (containsUnsafePattern(normalizedCode)) {
            return failed("当前代码包含不受支持的语句，暂时无法生成安全运行轨迹");
        }
        List<Map<String, Object>> steps = SimplePythonTracer.trace(normalizedCode, inputSample == null ? "" : inputSample);
        if (steps.isEmpty()) {
            return failed("当前代码暂时无法生成稳定运行轨迹");
        }
        Map<String, Object> llmPayload = aiModelGateway.callForJson(
                """
                %s
                你只可以根据提供的结构化轨迹解释程序行为，不允许编造额外步骤。
                你必须返回 JSON 对象。
                """.formatted(TutorLanguageSupport.beginnerSystemRole("OJ 运行轨迹讲解助手", context)),
                """
                【输入样例】
                %s

                【执行轨迹】
                %s

                【失败原因】
                %s

                输出 JSON：
                {
                  "step_explanations": [
                    {"step_index":0,"explanation":"解释"}
                  ],
                  "divergence_step": 0
                }
                """.formatted(inputSample == null ? "" : inputSample, steps, failureReason == null ? "" : failureReason)
        );
        List<Map<String, Object>> explanations = normalizeExplanationList(llmPayload.get("step_explanations"));
        for (int index = 0; index < steps.size(); index++) {
            Map<String, Object> step = steps.get(index);
            String explanation = "";
            for (Map<String, Object> item : explanations) {
                if (((Number) item.getOrDefault("step_index", -1)).intValue() == index) {
                    explanation = String.valueOf(item.getOrDefault("explanation", ""));
                    break;
                }
            }
            step.put("teaching_explanation", explanation);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ready");
        result.put("input_sample", inputSample == null ? "" : inputSample);
        result.put("steps", steps);
        result.put("divergence_step", ((Number) llmPayload.getOrDefault("divergence_step", steps.size() - 1)).intValue());
        result.put("failure_reason", failureReason == null ? "" : failureReason);
        return result;
    }

    private boolean containsUnsafePattern(String code) {
        String normalized = code.toLowerCase();
        return normalized.contains("import os")
                || normalized.contains("import sys")
                || normalized.contains("open(")
                || normalized.contains("exec(")
                || normalized.contains("eval(")
                || normalized.contains("__");
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

    private List<Map<String, Object>> normalizeExplanationList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(normalized);
            }
        }
        return result;
    }
}

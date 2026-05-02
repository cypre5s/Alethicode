package com.alethicode.service.aitutor.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimplePythonTracer {

    private SimplePythonTracer() {
    }

    static List<Map<String, Object>> trace(String code, String inputSample) {
        List<Map<String, Object>> steps = new ArrayList<>();
        String[] lines = code.split("\\R");
        Map<String, Object> variables = new LinkedHashMap<>();
        List<String> outputs = new ArrayList<>();
        int stepIndex = 0;
        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber].trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step_index", stepIndex++);
            step.put("line_number", lineNumber + 1);
            step.put("code", line);
            if (line.startsWith("print(")) {
                outputs.add(line.substring(6, Math.max(6, line.length() - 1)).trim());
            } else if (line.contains("=") && !line.startsWith("if ") && !line.startsWith("while ") && !line.startsWith("for ")) {
                String[] parts = line.split("=", 2);
                variables.put(parts[0].trim(), parts[1].trim());
            }
            step.put("variables", new LinkedHashMap<>(variables));
            step.put("output", String.join("\n", outputs));
            step.put("branch_taken", line.startsWith("if ") || line.startsWith("elif ") ? line : "");
            steps.add(step);
        }
        if (steps.isEmpty() && inputSample != null && !inputSample.isBlank()) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step_index", 0);
            step.put("line_number", 1);
            step.put("code", "读取输入");
            step.put("variables", Map.of("input_sample", inputSample));
            step.put("output", "");
            step.put("branch_taken", "");
            steps.add(step);
        }
        return steps;
    }
}

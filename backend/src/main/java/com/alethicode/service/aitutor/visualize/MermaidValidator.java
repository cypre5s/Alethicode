package com.alethicode.service.aitutor.visualize;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class MermaidValidator {

    private static final int MAX_LINES = 50;
    private static final List<Pattern> ALLOWED_HEADERS = List.of(
            Pattern.compile("^\\s*flowchart\\s+(TD|LR|TB|BT|RL)\\b"),
            Pattern.compile("^\\s*graph\\s+(TD|LR|TB|BT|RL)\\b"),
            Pattern.compile("^\\s*sequenceDiagram\\b"),
            Pattern.compile("^\\s*stateDiagram-v2\\b"),
            Pattern.compile("^\\s*classDiagram\\b")
    );
    private static final Pattern FLOWCHART_HEADER =
            Pattern.compile("^\\s*(flowchart|graph)\\s+(TD|LR|TB|BT|RL)\\b");
    private static final Pattern NODE_LABEL = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*\\s*(\\[([^\\]]+)\\]|\\{([^}]+)})");
    private static final Pattern LABEL_REQUIRES_QUOTES = Pattern.compile("[():<>]");
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "subgraph", "click ", "style ", "linkStyle"
    );
    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "<script", "javascript:", "data:text/html"
    );

    public void validate(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new VisualizeValidationException("mermaid payload is empty");
        }
        String[] lines = payload.split("\\r?\\n");
        if (lines.length > MAX_LINES) {
            throw new VisualizeValidationException(
                    "mermaid line count " + lines.length + " exceeds limit " + MAX_LINES);
        }
        boolean headerOk = false;
        for (Pattern p : ALLOWED_HEADERS) {
            if (lines.length > 0 && p.matcher(lines[0]).find()) {
                headerOk = true;
                break;
            }
        }
        if (!headerOk) {
            throw new VisualizeValidationException(
                    "mermaid must start with flowchart|graph (TD/LR/TB/BT/RL) / sequenceDiagram / stateDiagram-v2 / classDiagram");
        }
        String lower = payload.toLowerCase();
        for (String forbidden : FORBIDDEN_KEYWORDS) {
            if (lower.contains(forbidden.toLowerCase())) {
                throw new VisualizeValidationException(
                        "mermaid contains forbidden keyword: " + forbidden.trim());
            }
        }
        for (String frag : FORBIDDEN_FRAGMENTS) {
            if (lower.contains(frag)) {
                throw new VisualizeValidationException(
                        "mermaid contains forbidden fragment: " + frag);
            }
        }
        if (FLOWCHART_HEADER.matcher(lines[0]).find()) {
            validateFlowchartLabels(lines);
        }
    }

    private void validateFlowchartLabels(String[] lines) {
        for (int i = 1; i < lines.length; i++) {
            var matcher = NODE_LABEL.matcher(lines[i]);
            while (matcher.find()) {
                String label = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                String normalized = label == null ? "" : label.trim();
                if (LABEL_REQUIRES_QUOTES.matcher(normalized).find() && !isQuoted(normalized)) {
                    throw new VisualizeValidationException(
                            "quote flowchart labels containing (), :, < or > with double quotes");
                }
            }
        }
    }

    private boolean isQuoted(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"");
    }
}

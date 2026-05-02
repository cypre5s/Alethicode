package com.alethicode.service.aitutor.visualize;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VisualizePromptCatalog {

    private static final String FOR_LOOP_TRACE = "You are an educational visualization generator. "
            + "Generate a Mermaid flowchart for the for-loop iteration described. "
            + "Use 'flowchart TD' or 'flowchart LR' subset only, no subgraph/click/style. "
            + "Show iterations in order: start -> i=0 -> i=1 -> ... -> end. "
            + "Each iteration node shows the iteration variable and a short action. "
            + "Every node label MUST be double-quoted, e.g. A[\"Start\"], B[\"i=0 print\"], D{\"Has next?\"}. "
            + "If a label contains parentheses, colon, < or >, it MUST still be inside double quotes. "
            + "Maximum 12 nodes. Output JSON: {\"format\":\"mermaid\",\"payload\":\"flowchart LR\\nA[\\\"Start\\\"] --> B[\\\"i=0 print\\\"]\",\"alt_text\":\"...\"}";

    private static final String RECURSION_STACK = "You are an educational visualization generator. "
            + "Generate a Mermaid flowchart depicting recursion call stack expansion and contraction. "
            + "Same Mermaid subset rules. Maximum 12 nodes. "
            + "Output JSON: {\"format\":\"mermaid\",\"payload\":\"...\",\"alt_text\":\"...\"}";

    private static final String DATA_STRUCTURE_STATE = "You are an educational visualization generator. "
            + "Generate inline SVG showing the current state of the requested data structure. "
            + "Allowed tags: svg g rect circle ellipse line path polyline polygon text tspan defs marker linearGradient stop title desc. "
            + "Forbidden: script foreignObject animate on* attributes href xlink:href inline-style javascript: data: URIs. "
            + "Maximum viewport 800x600 max 5 colors. "
            + "Output JSON: {\"format\":\"svg\",\"payload\":\"<svg ...>...</svg>\",\"alt_text\":\"...\"}";

    private static final String COMPLEXITY_COMPARE = "You are an educational visualization generator. "
            + "Generate a Chart.js v4 line/bar/radar configuration comparing 2-3 algorithms time complexity. "
            + "type must be line/bar/radar; x-axis n values like 1 10 100 1000; y-axis exact operation count; 2-3 datasets max. "
            + "Forbid options.plugins.tooltip.callbacks options.onClick options.onHover (function injection). "
            + "Output JSON: {\"format\":\"chart\",\"payload\":\"<json string>\",\"alt_text\":\"...\"}";

    private static final String KC_MASTERY_RADAR = "You are an educational visualization generator. "
            + "Generate Chart.js v4 radar configuration for KC mastery (0..1 scale). "
            + "type MUST be 'radar'; max 8 labels (KC names); exactly 1 dataset; each dataset must have 'label' (string, max 50 chars) and 'data' (array of numbers 0..1). "
            + "Forbid options.plugins.tooltip.callbacks options.onClick options.onHover (function injection). "
            + "Output JSON: {\"format\":\"chart\",\"payload\":{\"type\":\"radar\",\"data\":{\"labels\":[...],\"datasets\":[{\"label\":\"...\",\"data\":[...]}]}},\"alt_text\":\"...\"}";

    private static final String MEMORY_LAYOUT = "You are an educational visualization generator. "
            + "Generate inline SVG explaining variable/object/reference memory layout. "
            + "Same SVG whitelist rules as data_structure_state. Output JSON same shape.";

    private static final String DATA_FLOW = "You are an educational visualization generator. "
            + "Generate a Mermaid sequenceDiagram showing function call data flow and parameter passing. "
            + "Use sequenceDiagram subset only. FORBIDDEN directives: subgraph, click, style, linkStyle. "
            + "Do NOT use these words in participant names or messages either. Max 15 messages. "
            + "Output JSON: {\"format\":\"mermaid\",\"payload\":\"sequenceDiagram\\n...\",\"alt_text\":\"...\"}";

    private static final String FLOWCHART = "You are an educational visualization generator. "
            + "Generate a Mermaid flowchart summarizing the algorithm. "
            + "Use 'flowchart TD' or 'flowchart LR' subset only, no subgraph/click/style/linkStyle. "
            + "Maximum 15 nodes. Each node label max 20 chars. "
            + "Every node label MUST be double-quoted, e.g. A[\"Input\"], B[\"Loop i\"], D{\"Has next?\"}. "
            + "If a label contains parentheses, colon, < or >, it MUST still be inside double quotes. "
            + "Output JSON: {\"format\":\"mermaid\",\"payload\":\"flowchart TD\\nA[\\\"Input\\\"] --> B[\\\"Loop i\\\"]\",\"alt_text\":\"...\"}";

    private static final Map<VisualizeIntent, String> CATALOG = Map.ofEntries(
            Map.entry(VisualizeIntent.FOR_LOOP_TRACE, FOR_LOOP_TRACE),
            Map.entry(VisualizeIntent.RECURSION_STACK, RECURSION_STACK),
            Map.entry(VisualizeIntent.DATA_STRUCTURE_STATE, DATA_STRUCTURE_STATE),
            Map.entry(VisualizeIntent.COMPLEXITY_COMPARE, COMPLEXITY_COMPARE),
            Map.entry(VisualizeIntent.KC_MASTERY_RADAR, KC_MASTERY_RADAR),
            Map.entry(VisualizeIntent.MEMORY_LAYOUT, MEMORY_LAYOUT),
            Map.entry(VisualizeIntent.DATA_FLOW, DATA_FLOW),
            Map.entry(VisualizeIntent.FLOWCHART, FLOWCHART)
    );

    public String promptFor(VisualizeIntent intent) {
        String prompt = CATALOG.get(intent);
        if (prompt == null) {
            throw new IllegalStateException("no prompt registered for " + intent);
        }
        return prompt;
    }
}

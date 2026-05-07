package com.alethicode.service.aitutor.parsons;

import com.alethicode.service.ai.AiModelGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 错题驱动的 Parsons 干扰块生成器。
 *
 * <p>数据源优先级（设计稿创新点 2）：</p>
 * <ol>
 *   <li>该用户该 KC 最近 90 天的 {@code ai_learner_notebook} 错题（{@code root_cause} / {@code misconception_distribution} 中的关键代码片段）</li>
 *   <li>不足时由 LLM 受控生成，prompt 注入 reference_code + KC 名称 + 已抽取的真实错题模式</li>
 * </ol>
 *
 * <p>所有干扰块与参考块做字符级 LCS 相似度过滤：相似度 ≥ {@code lcs-similarity-threshold} 直接丢弃，
 * 防止 LLM 产出"接近正确答案"的伪干扰块导致漏题。</p>
 */
@Service
public class ParsonsDistractorGenerator {

    private static final Logger log = LoggerFactory.getLogger(ParsonsDistractorGenerator.class);

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final ParsonsProperties properties;

    public ParsonsDistractorGenerator(JdbcTemplate jdbcTemplate,
                                      AiModelGateway aiModelGateway,
                                      ObjectMapper objectMapper,
                                      ParsonsProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<ParsonsDistractor> generate(GenerationContext ctx) {
        if (ctx.targetCount() <= 0) {
            return List.of();
        }
        Set<String> referenceCodeSet = new LinkedHashSet<>();
        for (ParsonsBlock b : ctx.referenceBlocks()) {
            referenceCodeSet.add(b.code().trim());
        }

        List<ParsonsDistractor> notebookOnes = pickFromNotebook(ctx, referenceCodeSet);

        int notebookTarget = (int) Math.ceil(ctx.targetCount() * properties.getDistractor().getTargetNotebookRatio());
        List<ParsonsDistractor> picked = new ArrayList<>(notebookOnes.subList(0, Math.min(notebookOnes.size(), notebookTarget)));

        int remaining = ctx.targetCount() - picked.size();
        if (remaining > 0 && properties.getDistractor().isLlmFallbackEnabled()) {
            picked.addAll(generateByLlm(ctx, referenceCodeSet, picked, remaining));
        }
        if (picked.size() < ctx.targetCount()) {
            // 数据稀疏，告警但不阻塞
            log.warn("Parsons distractor undercount: target={}, actual={}, problemId={}, kcs={}",
                    ctx.targetCount(), picked.size(), ctx.problemId(), ctx.kcIds());
        }
        return picked;
    }

    private List<ParsonsDistractor> pickFromNotebook(GenerationContext ctx, Set<String> referenceCodeSet) {
        if (ctx.userId() == null || ctx.kcIds() == null || ctx.kcIds().isEmpty()) {
            return List.of();
        }
        List<Long> kcIds = ctx.kcIds();
        StringBuilder kcArr = new StringBuilder("[");
        for (int i = 0; i < kcIds.size(); i++) {
            if (i > 0) kcArr.append(",");
            kcArr.append(kcIds.get(i));
        }
        kcArr.append("]");

        // 注意：jsonb 操作符 `?|` 与 JDBC 的 `?` 占位符冲突，pgjdbc 会把
        // `kc_ids ?| ARRAY[...]` 中的 `?` 误识别为参数占位符。改用等价的
        // PostgreSQL 内置函数 jsonb_exists_any 规避，不引入驱动级 hack。
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, root_cause, misconception_distribution, evidence_ptr
                FROM ai_learner_notebook
                WHERE user_id = ?
                  AND is_deleted = false
                  AND update_time > now() - interval '90 day'
                  AND (jsonb_exists_any(kc_ids, ARRAY[%s]::text[])
                       OR (problem_id IS NOT NULL AND problem_id IN (
                           SELECT problem_id FROM ai_problem_kc_mapping WHERE kc_id = ANY(?)
                       )))
                ORDER BY update_time DESC
                LIMIT 20
                """.formatted(buildKcStrParams(kcIds)),
                buildArgs(ctx.userId(), kcIds));

        List<ParsonsDistractor> result = new ArrayList<>();
        int cursor = 0;
        for (Map<String, Object> row : rows) {
            String rootCause = nullToEmpty((String) row.get("root_cause"));
            String snippet = extractSnippetFromCause(rootCause);
            if (snippet.isBlank()) continue;
            if (referenceCodeSet.contains(snippet.trim())) continue;
            if (similarTooMuch(snippet, referenceCodeSet)) continue;
            result.add(new ParsonsDistractor(
                    "DN" + cursor++,
                    snippet,
                    inferIndent(snippet),
                    ParsonsDistractor.Source.NOTEBOOK,
                    abbreviateKc(rootCause)
            ));
        }
        return result;
    }

    private List<ParsonsDistractor> generateByLlm(GenerationContext ctx,
                                                   Set<String> referenceCodeSet,
                                                   List<ParsonsDistractor> already,
                                                   int needed) {
        String systemPrompt = """
                你是 %s 编程教学专家。请为以下 Parsons 拼装题生成 N 个干扰块。
                每个干扰块必须语义合理但**结果错误**（不能是无意义代码或可拼成正确答案的代码）。
                优先模仿"学生历史错题模式"，且与正确块明显不同。
                严格输出 JSON：{"distractors":[{"code":"...","indent":<int>,"kc_hint":"..."}]}
                """.formatted(ctx.languageLabel());

        Set<String> existingDistractorCodes = new LinkedHashSet<>();
        for (ParsonsDistractor d : already) existingDistractorCodes.add(d.code().trim());

        StringBuilder refCodeStr = new StringBuilder();
        for (ParsonsBlock b : ctx.referenceBlocks()) {
            refCodeStr.append("    ".repeat(b.indent())).append(b.code()).append("\n");
        }
        String prompt = """
                【题目】%s
                【正确代码（参考）】
                %s
                【相关 KC】%s
                【已收集的真实错题模式】
                %s
                【需要生成】N=%d
                """.formatted(
                ctx.problemTitle(),
                refCodeStr.toString().stripTrailing(),
                String.join(",", ctx.kcNames()),
                summarizeExistingDistractors(already),
                needed);

        int attempt = 0;
        int maxRetries = Math.max(0, properties.getDistractor().getMaxLlmRetries());
        List<ParsonsDistractor> picked = new ArrayList<>();
        while (attempt <= maxRetries && picked.size() < needed) {
            try {
                Map<String, Object> resp = aiModelGateway.callForJson(systemPrompt, prompt);
                List<Map<String, Object>> raw = extractDistractorList(resp);
                int idx = 0;
                for (Map<String, Object> entry : raw) {
                    if (picked.size() >= needed) break;
                    String code = nullToEmpty((String) entry.get("code")).trim();
                    if (code.isBlank()) continue;
                    if (referenceCodeSet.contains(code)) continue;
                    if (existingDistractorCodes.contains(code)) continue;
                    if (similarTooMuch(code, referenceCodeSet)) continue;
                    int indent = entry.get("indent") instanceof Number n ? n.intValue() : inferIndent(code);
                    String hint = nullToEmpty((String) entry.get("kc_hint"));
                    picked.add(new ParsonsDistractor(
                            "DL" + (already.size() + idx),
                            code,
                            Math.max(0, indent),
                            ParsonsDistractor.Source.LLM,
                            hint));
                    existingDistractorCodes.add(code);
                    idx++;
                }
            } catch (RuntimeException e) {
                log.warn("Parsons distractor LLM attempt {} failed: {}", attempt, e.getMessage());
            }
            attempt++;
        }
        return picked;
    }

    private List<Map<String, Object>> extractDistractorList(Map<String, Object> raw) {
        if (raw == null) return List.of();
        Object obj = raw.get("distractors");
        if (obj instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> typed = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        typed.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(typed);
                }
            }
            return result;
        }
        if (obj instanceof String s) {
            try {
                return objectMapper.readValue(s, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of();
    }

    private boolean similarTooMuch(String candidate, Set<String> referenceCodes) {
        double threshold = properties.getDistractor().getLcsSimilarityThreshold();
        String stripped = candidate.replaceAll("\\s+", "");
        if (stripped.isBlank()) return true;
        for (String ref : referenceCodes) {
            String refStripped = ref.replaceAll("\\s+", "");
            if (refStripped.isBlank()) continue;
            int lcs = lcsLength(stripped, refStripped);
            int maxLen = Math.max(stripped.length(), refStripped.length());
            if (maxLen == 0) continue;
            double ratio = (double) lcs / maxLen;
            if (ratio >= threshold) return true;
        }
        return false;
    }

    private static int lcsLength(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int m = a.length();
        int n = b.length();
        int[] prev = new int[n + 1];
        int[] cur = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    cur[j] = prev[j - 1] + 1;
                } else {
                    cur[j] = Math.max(prev[j], cur[j - 1]);
                }
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[n];
    }

    private static String extractSnippetFromCause(String rootCause) {
        if (rootCause == null) return "";
        int start = rootCause.indexOf("```");
        if (start >= 0) {
            int end = rootCause.indexOf("```", start + 3);
            if (end > start) {
                String fenced = rootCause.substring(start + 3, end).trim();
                int newline = fenced.indexOf('\n');
                if (newline > 0 && newline < 24) {
                    fenced = fenced.substring(newline + 1);
                }
                String[] codeLines = fenced.split("\\r?\\n");
                if (codeLines.length > 0) {
                    return codeLines[0].stripTrailing();
                }
            }
        }
        // 兜底：取首句
        String[] sentences = rootCause.split("[\\r\\n。.!?]");
        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.length() >= 4 && trimmed.length() <= 120 &&
                    (trimmed.contains("=") || trimmed.contains("(") || trimmed.contains("for")
                            || trimmed.contains("if") || trimmed.contains("return"))) {
                return trimmed;
            }
        }
        return "";
    }

    private static int inferIndent(String code) {
        int spaces = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == ' ') spaces++;
            else if (c == '\t') spaces += 4;
            else break;
        }
        return spaces / 4;
    }

    private static String abbreviateKc(String text) {
        if (text == null) return "";
        String norm = text.trim().replaceAll("\\s+", " ");
        if (norm.length() <= 30) return norm;
        return norm.substring(0, 30) + "...";
    }

    private static String summarizeExistingDistractors(List<ParsonsDistractor> existing) {
        if (existing.isEmpty()) return "(空)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < existing.size(); i++) {
            sb.append(i + 1).append(". ").append(existing.get(i).code()).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String buildKcStrParams(List<Long> kcIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kcIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("'").append(kcIds.get(i)).append("'");
        }
        return sb.toString();
    }

    private static Object[] buildArgs(Long userId, List<Long> kcIds) {
        // 第二参数是 ai_problem_kc_mapping.kc_id = ANY(?)
        Long[] kcArr = kcIds.toArray(new Long[0]);
        return new Object[]{userId, kcArr};
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Parsons distractor 生成上下文。
     *
     * @param userId          学生 ID
     * @param problemId       题目 ID
     * @param problemTitle    题目标题（喂给 LLM prompt）
     * @param language        语言 ID（Python3 / Java / ...）
     * @param languageLabel   人类可读语言名（"Python 3" / "Java" / ...）
     * @param kcIds           当前题目相关 KC ID
     * @param kcNames         KC 名称（用于 LLM prompt）
     * @param referenceBlocks 参考代码切分后的 block，用于 LCS 相似度过滤
     * @param targetCount     目标 distractor 数量
     */
    public record GenerationContext(
            Long userId,
            Long problemId,
            String problemTitle,
            String language,
            String languageLabel,
            List<Long> kcIds,
            List<String> kcNames,
            List<ParsonsBlock> referenceBlocks,
            int targetCount
    ) {
    }
}

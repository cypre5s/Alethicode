package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.AnswerSynthesisService;
import com.alethicode.service.languagepack.SynthesisTrace;
import com.alethicode.service.languagepack.GroundedAnswer;
import com.alethicode.service.languagepack.PageRetrievalHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AnswerSynthesisServiceImpl implements AnswerSynthesisService {

    private static final Logger log = LoggerFactory.getLogger(AnswerSynthesisServiceImpl.class);
    private static final double MIN_ACCEPT_SCORE = 0.2;

    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;

    public AnswerSynthesisServiceImpl(AiModelGateway aiModelGateway, ObjectMapper objectMapper) {
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public GroundedAnswer synthesizeAnswer(String question, List<PageRetrievalHit> hits) {
        return synthesizeAnswer(question, hits, null);
    }

    @Override
    public GroundedAnswer synthesizeAnswer(String question, List<PageRetrievalHit> hits, Long languagePackId) {
        return synthesizeAnswer(question, hits, languagePackId, null);
    }

    @Override
    public GroundedAnswer synthesizeAnswer(String question, List<PageRetrievalHit> hits, Long languagePackId, String primaryLanguage) {
        boolean hasUsableHits = hits != null && !hits.isEmpty() && hits.getFirst().score() >= MIN_ACCEPT_SCORE;
        List<PageRetrievalHit> effectiveHits = hasUsableHits ? hits : List.of();

        Map<String, Object> raw = aiModelGateway.callForJson(buildSystemPrompt(primaryLanguage), buildUserPrompt(question, effectiveHits));

        GroundedAnswer answer = validateAnswer(raw, effectiveHits);

        boolean groundingCriticEnabled = "true".equalsIgnoreCase(
                aiModelGateway.readConfigOrDefault("QA_GROUNDING_CRITIC_ENABLED", "false"));
        if (groundingCriticEnabled && answer.grounded()) {
            Map<String, Object> criticResult = runGroundingCritic(question, effectiveHits, answer);
            boolean criticGrounded = extractBoolean(criticResult, "grounded");
            if (!criticGrounded) {
                log.info("QA grounding critic rejected grounding; downgrading to general knowledge answer. verdict={}",
                        extractString(criticResult, "reason"));
                return new GroundedAnswer(answer.answerMarkdown(), List.of(), false, false, "");
            }
        }

        return answer;
    }

    @Override
    public SynthesisTrace synthesizeWithTrace(String question, List<PageRetrievalHit> hits, Long languagePackId) {
        return synthesizeWithTrace(question, hits, languagePackId, null);
    }

    @Override
    public SynthesisTrace synthesizeWithTrace(String question, List<PageRetrievalHit> hits, Long languagePackId, String primaryLanguage) {
        long synthesisStart = System.currentTimeMillis();
        GroundedAnswer answer = synthesizeAnswer(question, hits, languagePackId, primaryLanguage);
        long synthesisLatency = System.currentTimeMillis() - synthesisStart;

        boolean criticPassed = true;
        String criticVerdict = "";
        long criticLatency = 0;
        String failureBucket = null;

        boolean hasUsableHits = hits != null && !hits.isEmpty() && hits.getFirst().score() >= MIN_ACCEPT_SCORE;
        List<PageRetrievalHit> effectiveHits = hasUsableHits ? hits : List.of();

        boolean groundingCriticEnabled = "true".equalsIgnoreCase(
                aiModelGateway.readConfigOrDefault("QA_GROUNDING_CRITIC_ENABLED", "false"));
        if (groundingCriticEnabled && answer.grounded()) {
            long criticStart = System.currentTimeMillis();
            Map<String, Object> criticResult = runGroundingCritic(question, effectiveHits, answer);
            criticLatency = System.currentTimeMillis() - criticStart;
            criticPassed = extractBoolean(criticResult, "grounded");
            criticVerdict = extractString(criticResult, "reason");
            if (!criticPassed) {
                failureBucket = "grounding_critic_rejected";
            }
        }
        if (!answer.grounded() && answer.insufficientEvidence()) {
            failureBucket = "insufficient_evidence";
        }

        return new SynthesisTrace(answer, criticPassed, criticVerdict, synthesisLatency, criticLatency, failureBucket);
    }

    private Map<String, Object> runGroundingCritic(String question, List<PageRetrievalHit> hits, GroundedAnswer answer) {
        String evidenceJson;
        try {
            evidenceJson = objectMapper.writeValueAsString(
                    hits.stream().map(PageRetrievalHit::toMap).toList());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize evidence for grounding critic", e);
        }
        return aiModelGateway.callForJson(
                """
                你是一个答案事实核查员。
                你需要验证给出的答案是否真正基于引用的证据页内容。
                输出 JSON：{"grounded": true/false, "reason": "一句话说明"}
                """,
                """
                问题：%s
                
                答案：%s
                
                引用的证据页：%s
                """.formatted(question, answer.answerMarkdown(), evidenceJson)
        );
    }

    private boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String buildSystemPrompt(String primaryLanguage) {
        String lang = (primaryLanguage == null || primaryLanguage.isBlank()) ? "编程" : primaryLanguage;
        return """
                你是"课件问答助手"，专注于帮助 %s 编程初学者学习。
                你可以：
                1. 回答编程知识和 %s 相关问题，有课件证据时优先引用，无课件证据时用通用编程知识回答；
                2. 进行日常闲聊，并自然地引导回编程学习话题；
                3. 课件中有相关内容时，在回答中自然提及课件页的内容；
                4. 只拒答与 OJ 判题、题目提交、完整题解直接相关的请求。
                5. 回答末尾以一个启发性问题结尾，引导学生进一步思考（例如"你觉得……会怎样？"或"如果……该怎么做？"）。
                输出必须是 JSON 对象，包含：answer_markdown, cited_page_nos, insufficient_evidence。
                - answer_markdown：回答正文（Markdown 格式）。
                - cited_page_nos：你在 answer_markdown 中真正使用到内容的页码列表（数组形式，元素为 int）。
                  必须从给定「课件相关页」中已有的 page_no 里选；不引用任何课件页时返回空数组 []。
                  不要把所有给定的 page_no 都列出来，只列你确实在回答中用了内容的那几页；
                  这是给前端"已定位到课件页证据"卡片用的页码，列错或多列会让用户看到与回答无关的引用。
                - insufficient_evidence：布尔值。仅当问题是 OJ 判题相关请求且必须拒答时设为 true，其余一律 false。
                """.formatted(lang, lang);
    }

    private String buildUserPrompt(String question, List<PageRetrievalHit> hits) {
        try {
            String questionText = question == null ? "" : question.trim();
            if (hits.isEmpty()) {
                return "问题：\n" + questionText + "\n\n（课件中未检索到相关页面，请从通用编程知识回答）";
            }
            return """
                    问题：
                    %s

                    课件相关页（有引用价值时请标注页码）：
                    %s
                    """.formatted(questionText, objectMapper.writeValueAsString(
                    hits.stream().map(PageRetrievalHit::toMap).toList()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize retrieval hits", exception);
        }
    }

    private GroundedAnswer validateAnswer(Map<String, Object> raw, List<PageRetrievalHit> hits) {
        String answerMarkdown = requireText(raw.get("answer_markdown"), "answer_markdown is required");
        boolean insufficientEvidence = extractBoolean(raw, "insufficient_evidence");
        List<Integer> citedPageNos = extractCitedPageNos(raw.get("cited_page_nos"));
        if (citedPageNos.isEmpty()) {
            citedPageNos = extractLegacyCitationPageNos(raw.get("citations"));
        }

        if (insufficientEvidence) {
            boolean hasSubstantiveAnswer = answerMarkdown.length() > 80
                    && !answerMarkdown.contains("无法回答")
                    && !answerMarkdown.contains("抱歉");
            if (hasSubstantiveAnswer && !hits.isEmpty()) {
                log.info("QA synthesis: AI set insufficient_evidence=true but answer is substantive and hits exist; attaching cited citations");
                List<Map<String, Object>> citations = buildCitationsFromHits(hits, citedPageNos);
                return new GroundedAnswer(answerMarkdown, citations, !citations.isEmpty(), false, "");
            }
            if (hasSubstantiveAnswer) {
                return new GroundedAnswer(answerMarkdown, List.of(), false, false, "");
            }
            String refusalReason = trimToEmpty(raw.get("refusal_reason"));
            return refusal(answerMarkdown, refusalReason.isBlank() ? "insufficient_evidence" : refusalReason);
        }

        if (hits.isEmpty()) {
            return new GroundedAnswer(answerMarkdown, List.of(), false, false, "");
        }

        List<Map<String, Object>> citations = buildCitationsFromHits(hits, citedPageNos);
        return new GroundedAnswer(answerMarkdown, citations, !citations.isEmpty(), false, "");
    }

    /**
     * 从 LLM 输出的 {@code cited_page_nos} 字段抽取一组页码。
     *
     * <p>历史会话和老 LLM 没有这个字段，返回空列表；后续只在存在可展示 citations 时标记 grounded，
     * 避免前端出现「已定位到课件页证据」但没有引用按钮的状态。
     */
    private List<Integer> extractCitedPageNos(Object rawValue) {
        if (!(rawValue instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof Number number) {
                result.add(number.intValue());
            } else if (element instanceof String text) {
                try {
                    result.add(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    /**
     * 兼容老模型偶尔返回的 {@code citations: [{page_no: ...}]} 结构。
     *
     * <p>这里只提取模型明确给出的页码，仍然会与 RAG hits 求交集；不会把全量 hits 当作引用。
     */
    private List<Integer> extractLegacyCitationPageNos(Object rawValue) {
        if (!(rawValue instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> citation)) {
                continue;
            }
            Object pageNo = citation.get("page_no");
            if (pageNo instanceof Number number) {
                result.add(number.intValue());
            } else if (pageNo instanceof String text) {
                try {
                    result.add(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    /**
     * 用 LLM 标注的 {@code cited_page_nos} 与 {@code hits} 求交集生成 citations。
     *
     * <p>未标 / 标空数组 / 全部 hallucinate（不在 hits.page_no 集合内）时返回空列表，
     * 前端就不会显示无关的引用按钮；保留 hits 顺序，去重，避免一个页面在 hits 里多个 chunk
     * 命中导致 UI 出现重复引用。
     */
    private List<Map<String, Object>> buildCitationsFromHits(List<PageRetrievalHit> hits, List<Integer> citedPageNos) {
        if (citedPageNos == null || citedPageNos.isEmpty() || hits == null || hits.isEmpty()) {
            return List.of();
        }
        Set<Integer> citedSet = new HashSet<>(citedPageNos);
        Set<Integer> emittedPages = new HashSet<>();
        List<Map<String, Object>> citations = new ArrayList<>();
        for (PageRetrievalHit hit : hits) {
            int pageNo = hit.pageNo();
            if (!citedSet.contains(pageNo) || !emittedPages.add(pageNo)) {
                continue;
            }
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("document_id", hit.documentId());
            citation.put("document_title", hit.documentTitle());
            citation.put("page_no", pageNo);
            citation.put("excerpt", hit.excerpt());
            citation.put("confidence", Math.round(hit.score() * 1000.0) / 1000.0);
            citations.add(citation);
        }
        return citations;
    }

    private GroundedAnswer refusal(String answerMarkdown, String refusalReason) {
        return new GroundedAnswer(answerMarkdown, List.of(), false, true, refusalReason);
    }

    private String requireText(Object value, String message) {
        String text = trimToEmpty(value);
        if (text.isBlank()) {
            throw new IllegalStateException(message);
        }
        return text;
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

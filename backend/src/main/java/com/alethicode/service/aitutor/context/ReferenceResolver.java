package com.alethicode.service.aitutor.context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Unified Chat reference syntax in raw user text and event_data references arrays.
 *
 * <p>Recognised tokens:
 * <ul>
 *   <li>{@code @card:&lt;cardId&gt;} – explicit card anchor (e.g. <code>@card:C-V-001</code>)</li>
 *   <li>{@code @last_error} – the most recent ERROR_DIAGNOSIS card in the session</li>
 *   <li>{@code @last_visualize} – the most recent VISUALIZE card</li>
 *   <li>{@code @last_ideate} – the most recent IDEATE_ANALYSIS card</li>
 *   <li>{@code @last_guide} – the most recent PROBLEM_GUIDE card</li>
 *   <li>{@code @last_review} – the most recent KNOWLEDGE_REVIEW card</li>
 *   <li>{@code @last_post_ac} – the most recent POST_AC card</li>
 *   <li>{@code @courseware:&lt;lpId&gt;} – language pack reference; the AI tutor will pull RAG
 *       top-k chunks of that pack scoped to the current question. See
 *       <code>docs/plans/2026-05-03-courseware-reference-token-design.md</code>.</li>
 * </ul>
 *
 * <p>Design: <code>docs/plans/2026-04-25-unified-chat-context-design.md</code> 附录 B</p>
 */
public final class ReferenceResolver {

    /** {@code @card:C-V-001} – card_id alphabet/numbers/dashes. */
    public static final Pattern CARD_ID_REF = Pattern.compile("@card:([A-Za-z0-9_-]+)");

    /** {@code @last_xxx} shorthand. */
    public static final Pattern SHORTHAND_REF = Pattern.compile("@last_([a-z_]+)");

    /** {@code @courseware:42} – language pack id (positive integer). */
    public static final Pattern COURSEWARE_REF = Pattern.compile("@courseware:(\\d+)");

    /**
     * {@code @page:42:7} 或 {@code @page:7} – language pack page reference. Group 1 是
     * 可选 lpId（绝对引用，跨课件时显式指定），group 2 是 page number。课件问答里
     * 可省略 lpId，由调用方用当前会话的 lpId 补全。
     */
    public static final Pattern PAGE_REF = Pattern.compile("@page:(?:(\\d+):)?(\\d+)");

    /**
     * {@code @kc:python.recursion.def} – knowledge concept node id；允许字母数字、
     * 点、下划线、横线，与 KnowledgeStarMap 节点 id 风格一致。
     */
    public static final Pattern KC_REF = Pattern.compile("@kc:([A-Za-z0-9_.\\-]+)");

    /**
     * {@code @notebook:N-001} – LearnerNotebook 条目 id；允许字母数字、下划线、横线。
     */
    public static final Pattern NOTEBOOK_REF = Pattern.compile("@notebook:([A-Za-z0-9_\\-]+)");

    public enum ShorthandKind {
        ERROR("error", "error_diagnosis"),
        VISUALIZE("visualize", "visualize"),
        IDEATE("ideate", "ideate_analysis"),
        GUIDE("guide", "problem_guide"),
        REVIEW("review", "knowledge_review"),
        POST_AC("post_ac", "post_ac"),
        TRANSFER("transfer", "transfer_problem");

        private final String suffix;
        private final String cardType;

        ShorthandKind(String suffix, String cardType) {
            this.suffix = suffix;
            this.cardType = cardType;
        }

        public String suffix() { return suffix; }
        public String cardType() { return cardType; }

        public static ShorthandKind fromSuffix(String suffix) {
            if (suffix == null) return null;
            for (ShorthandKind kind : values()) {
                if (kind.suffix.equals(suffix)) return kind;
            }
            return null;
        }
    }

    private ReferenceResolver() {}

    /** True iff the raw token starts with {@code @card:} followed by a valid id. */
    public static boolean isExplicitCardRef(String raw) {
        if (raw == null) return false;
        Matcher m = CARD_ID_REF.matcher(raw.trim());
        return m.matches();
    }

    /** Extracts the card id from {@code @card:<id>}; returns null if not a valid explicit ref. */
    public static String extractCardId(String raw) {
        if (raw == null) return null;
        Matcher m = CARD_ID_REF.matcher(raw.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** Maps a shorthand token like {@code @last_error} to the card type. Returns null on miss. */
    public static ShorthandKind classifyShorthand(String raw) {
        if (raw == null) return null;
        Matcher m = SHORTHAND_REF.matcher(raw.trim());
        if (!m.matches()) return null;
        return ShorthandKind.fromSuffix(m.group(1));
    }

    /** True iff the raw token is a {@code @courseware:<digits>} reference. */
    public static boolean isCoursewareRef(String raw) {
        if (raw == null) return false;
        Matcher m = COURSEWARE_REF.matcher(raw.trim());
        return m.matches();
    }

    /**
     * Extracts the language pack id from {@code @courseware:<lpId>}; returns null if not a valid ref
     * or the digit string overflows {@code long}.
     */
    public static Long extractCoursewareId(String raw) {
        if (raw == null) return null;
        Matcher m = COURSEWARE_REF.matcher(raw.trim());
        if (!m.matches()) return null;
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 引用课件具体页：返回 {@link PageReference} 或 null（非 @page 形式 / 数字溢出）。
     * 课件问答页里 lpId 通常省略，由调用方用当前会话的 lpId 补全。
     */
    public static PageReference extractPageRef(String raw) {
        if (raw == null) return null;
        Matcher m = PAGE_REF.matcher(raw.trim());
        if (!m.matches()) return null;
        Long lpId = null;
        if (m.group(1) != null) {
            try {
                lpId = Long.parseLong(m.group(1));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        int pageNo;
        try {
            pageNo = Integer.parseInt(m.group(2));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (pageNo <= 0) return null;
        return new PageReference(lpId, pageNo);
    }

    /** Extracts the KC id from {@code @kc:<id>}; returns null if not a valid ref. */
    public static String extractKcId(String raw) {
        if (raw == null) return null;
        Matcher m = KC_REF.matcher(raw.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** Extracts the LearnerNotebook entry id from {@code @notebook:<id>}; returns null if invalid. */
    public static String extractNotebookEntryId(String raw) {
        if (raw == null) return null;
        Matcher m = NOTEBOOK_REF.matcher(raw.trim());
        return m.matches() ? m.group(1) : null;
    }

    /**
     * 课件页引用解析后的不可变 record。{@code lpId} 可空——课件问答页里 lpId 默认是当前
     * 会话的课件包；AI 导学助手里 lpId 必须显式指定，否则前端不应允许插入裸 {@code @page:<n>}。
     */
    public record PageReference(Long lpId, int pageNo) {}
}

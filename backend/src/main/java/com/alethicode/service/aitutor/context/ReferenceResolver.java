package com.alethicode.service.aitutor.context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Unified Chat 用户文本和 {@code event_data.references} 中的引用语法。
 *
 * <p>支持的引用：
 * <ul>
 *   <li>{@code @card:&lt;cardId&gt;}：显式卡片锚点。</li>
 *   <li>{@code @last_error}：当前会话最近的 ERROR_DIAGNOSIS 卡片。</li>
 *   <li>{@code @last_visualize}：当前会话最近的 VISUALIZE 卡片。</li>
 *   <li>{@code @last_ideate}：当前会话最近的 IDEATE_ANALYSIS 卡片。</li>
 *   <li>{@code @last_guide}：当前会话最近的 PROBLEM_GUIDE 卡片。</li>
 *   <li>{@code @last_review}：当前会话最近的 KNOWLEDGE_REVIEW 卡片。</li>
 *   <li>{@code @last_post_ac}：当前会话最近的 POST_AC 卡片。</li>
 *   <li>{@code @courseware:&lt;lpId&gt;}：语言包引用，AI Tutor 会按当前问题拉取 RAG 片段。</li>
 * </ul>
 *
 * <p>Design: <code>docs/plans/2026-04-25-unified-chat-context-design.md</code> 附录 B</p>
 */
public final class ReferenceResolver {

    /** 匹配 {@code @card:C-V-001} 形式的卡片 ID 引用。 */
    public static final Pattern CARD_ID_REF = Pattern.compile("@card:([A-Za-z0-9_-]+)");

    /** 匹配 {@code @last_xxx} 形式的快捷引用。 */
    public static final Pattern SHORTHAND_REF = Pattern.compile("@last_([a-z_]+)");

    /** 匹配 {@code @courseware:42} 形式的语言包引用。 */
    public static final Pattern COURSEWARE_REF = Pattern.compile("@courseware:(\\d+)");

    /**
     * 课件页引用支持两种语法：
     * <ul>
     *   <li>{@code @page:7} / {@code @page:42:7}：legacy 全局页号，group 2 是包内全局页码，
     *       多文档课件按 {@code (sort_order, document_id, page_no)} 排序连续编号。</li>
     *   <li>{@code @page:1.7} / {@code @page:42:1.7}：二级目录定位，group 2 是章号
     *       （按 sort_order 排序的 normalized 文档 1-based 序号），group 3 是该章内页号。</li>
     * </ul>
     * Group 1 是可选 lpId（跨课件包绝对引用），课件问答里通常省略，由调用方用当前会话 lpId 补全。
     */
    public static final Pattern PAGE_REF = Pattern.compile("@page:(?:(\\d+):)?(\\d+)(?:\\.(\\d+))?");

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

    /** 判断原始 token 是否为合法显式卡片引用。 */
    public static boolean isExplicitCardRef(String raw) {
        if (raw == null) return false;
        Matcher m = CARD_ID_REF.matcher(raw.trim());
        return m.matches();
    }

    /** 从 {@code @card:<id>} 中提取卡片 ID，非法时返回 null。 */
    public static String extractCardId(String raw) {
        if (raw == null) return null;
        Matcher m = CARD_ID_REF.matcher(raw.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** 将 {@code @last_error} 等快捷引用映射为卡片类型，未命中时返回 null。 */
    public static ShorthandKind classifyShorthand(String raw) {
        if (raw == null) return null;
        Matcher m = SHORTHAND_REF.matcher(raw.trim());
        if (!m.matches()) return null;
        return ShorthandKind.fromSuffix(m.group(1));
    }

    /** 判断原始 token 是否为合法课件引用。 */
    public static boolean isCoursewareRef(String raw) {
        if (raw == null) return false;
        Matcher m = COURSEWARE_REF.matcher(raw.trim());
        return m.matches();
    }

    /**
     * 从 {@code @courseware:<lpId>} 中提取语言包 ID，非法或溢出时返回 null。
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
     * 引用课件具体页：返回 {@link PageReference} 或 null（非 @page 形式 / 数字溢出 / 0 值）。
     * 课件问答页里 lpId 通常省略，由调用方用当前会话的 lpId 补全。
     *
     * <p>解析规则与 {@link #PAGE_REF} 对应：
     * <ul>
     *   <li>group 3 缺省 → 全局页号语法，{@code chapter} = {@code null}，{@code pageNo} 为包内全局页码。</li>
     *   <li>group 3 存在 → 二级目录语法，{@code chapter} 取 group 2，{@code pageNo} 取 group 3。</li>
     * </ul>
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
        int firstNumber;
        try {
            firstNumber = Integer.parseInt(m.group(2));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (firstNumber <= 0) return null;

        if (m.group(3) == null) {
            return new PageReference(lpId, null, firstNumber);
        }
        int chapterPageNo;
        try {
            chapterPageNo = Integer.parseInt(m.group(3));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (chapterPageNo <= 0) return null;
        return new PageReference(lpId, firstNumber, chapterPageNo);
    }

    /** 从 {@code @kc:<id>} 中提取 KC ID，非法时返回 null。 */
    public static String extractKcId(String raw) {
        if (raw == null) return null;
        Matcher m = KC_REF.matcher(raw.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** 从 {@code @notebook:<id>} 中提取 LearnerNotebook 条目 ID，非法时返回 null。 */
    public static String extractNotebookEntryId(String raw) {
        if (raw == null) return null;
        Matcher m = NOTEBOOK_REF.matcher(raw.trim());
        return m.matches() ? m.group(1) : null;
    }

    /**
     * 课件页引用解析后的不可变 record。
     *
     * <ul>
     *   <li>{@code lpId} 可空——课件问答页默认用当前会话的课件包；AI 导学助手里 lpId
     *       必须显式指定，否则前端不应允许插入裸 {@code @page:<n>}。</li>
     *   <li>{@code chapter} == null：{@code pageNo} 是课件包内全局页码（legacy 语法）。</li>
     *   <li>{@code chapter} != null：{@code pageNo} 是该章（按 sort_order 排序的 normalized
     *       文档 1-based 序号）内的页号。</li>
     * </ul>
     */
    public record PageReference(Long lpId, Integer chapter, int pageNo) {}
}

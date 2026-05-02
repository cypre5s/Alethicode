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
 * </ul>
 *
 * <p>Design: <code>docs/plans/2026-04-25-unified-chat-context-design.md</code> 附录 B</p>
 */
public final class ReferenceResolver {

    /** {@code @card:C-V-001} – card_id alphabet/numbers/dashes. */
    public static final Pattern CARD_ID_REF = Pattern.compile("@card:([A-Za-z0-9_-]+)");

    /** {@code @last_xxx} shorthand. */
    public static final Pattern SHORTHAND_REF = Pattern.compile("@last_([a-z_]+)");

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
}

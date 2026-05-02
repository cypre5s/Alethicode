package com.alethicode.service.aitutor.context;

import com.alethicode.service.aitutor.contract.Phase;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

/**
 * Unified Chat Mode (P3) – the user-facing view of what the student wants right now.
 *
 * <p>Mode is decoupled from the system-managed {@link Phase}: switching Mode does NOT
 * change the workflow phase. However, certain Modes are not meaningful in some Phases;
 * the {@link #ALLOWED_BY_PHASE} matrix below mirrors the design appendix.</p>
 *
 * <p>Design: <code>docs/plans/2026-04-25-unified-chat-context-design.md</code> §5 / 附录 A</p>
 */
public enum ConversationMode {

    READING("reading"),
    IDEATE("ideate"),
    CODING("coding"),
    ERROR_DIAG("error_diag"),
    VISUALIZE("visualize"),
    AC_REVIEW("ac_review"),
    TRANSFER("transfer"),
    KNOWLEDGE_REVIEW("knowledge_review"),
    CHAT("chat");

    private final String key;

    ConversationMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<ConversationMode> fromKey(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim().toLowerCase();
        if (trimmed.isEmpty()) return Optional.empty();
        return Arrays.stream(values())
                .filter(mode -> mode.key.equals(trimmed))
                .findFirst();
    }

    public static ConversationMode defaultMode() {
        return READING;
    }

    /**
     * Phase × Mode allow matrix. Modes not listed here for a given Phase return 422 on
     * {@link ConversationContextService#switchMode(String, ConversationMode, Phase)}.
     */
    public static final Map<Phase, EnumSet<ConversationMode>> ALLOWED_BY_PHASE;

    static {
        EnumMap<Phase, EnumSet<ConversationMode>> matrix = new EnumMap<>(Phase.class);
        matrix.put(Phase.READING, EnumSet.of(
                READING, IDEATE, VISUALIZE, KNOWLEDGE_REVIEW, CHAT
        ));
        matrix.put(Phase.IDEATING, EnumSet.of(
                READING, IDEATE, CODING, VISUALIZE, KNOWLEDGE_REVIEW, CHAT
        ));
        matrix.put(Phase.CODING, EnumSet.of(
                IDEATE, CODING, ERROR_DIAG, VISUALIZE, KNOWLEDGE_REVIEW, CHAT
        ));
        matrix.put(Phase.ERROR_FEEDBACK, EnumSet.of(
                READING, IDEATE, CODING, ERROR_DIAG, VISUALIZE, KNOWLEDGE_REVIEW, CHAT
        ));
        matrix.put(Phase.AC_REVIEW, EnumSet.of(
                AC_REVIEW, VISUALIZE, TRANSFER, KNOWLEDGE_REVIEW, CHAT
        ));
        matrix.put(Phase.TRANSFER, EnumSet.of(
                TRANSFER, CODING, VISUALIZE, KNOWLEDGE_REVIEW, CHAT
        ));
        ALLOWED_BY_PHASE = Map.copyOf(matrix);
    }

    public boolean allowedIn(Phase phase) {
        EnumSet<ConversationMode> allowed = ALLOWED_BY_PHASE.get(phase);
        return allowed != null && allowed.contains(this);
    }
}

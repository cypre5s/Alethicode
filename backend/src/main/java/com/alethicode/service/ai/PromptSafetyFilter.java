package com.alethicode.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Input sanitizer for anything that gets concatenated into an LLM system / user
 * prompt. Goals:
 *
 * <ol>
 *   <li><b>Prompt injection</b>: strip common jailbreak phrases that try to
 *       override our system prompt (ignore previous instructions / disregard
 *       rules / you are now...).</li>
 *   <li><b>Delimiter confusion</b>: escape backticks and XML-like tags that
 *       might close our own delimiters.</li>
 *   <li><b>Hard length cap</b>: reject inputs that dwarf a normal tutor turn —
 *       those are usually prompt-stuffing or copy-pasted logs.</li>
 * </ol>
 *
 * The filter is intentionally conservative: it does NOT try to be a full
 * jailbreak detector. Real prompt safety needs a separate content-scan provider
 * (see {@code AigcComplianceService.scanForSensitiveContent}). This is the
 * cheap first gate that catches the 80% of automated attacks.
 */
@Service
public class PromptSafetyFilter {

    private static final Logger log = LoggerFactory.getLogger(PromptSafetyFilter.class);

    /** Hard cap for any single user-supplied field. */
    private static final int MAX_FIELD_LENGTH = 8_000;

    /** Substrings (case-insensitive) that, when found verbatim, are rewritten. */
    private static final List<String> JAILBREAK_MARKERS = List.of(
            "ignore previous instructions",
            "ignore the above",
            "disregard the above",
            "you are now",
            "system prompt:",
            "### system",
            "<|system|>",
            "developer mode",
            "忽略以上",
            "忽略之前",
            "忽略上面",
            "无视之前",
            "你现在是",
            "你现在扮演",
            "开发者模式"
    );

    /** Pattern that matches 3+ consecutive backticks or XML-like role tags. */
    private static final Pattern DELIMITER_PATTERN = Pattern.compile(
            "(```+|<\\|(system|user|assistant|tool)\\|>)", Pattern.CASE_INSENSITIVE);

    /**
     * Sanitize a single user-provided field before it's concatenated into a
     * prompt. Returns a cleaned string plus a diagnostics record that the
     * caller can log / increment a metric on.
     */
    public Result sanitize(String raw) {
        if (raw == null) return Result.clean("");
        String original = raw;
        boolean modified = false;

        // 1. Hard length cap.
        String trimmed = raw;
        if (trimmed.length() > MAX_FIELD_LENGTH) {
            trimmed = trimmed.substring(0, MAX_FIELD_LENGTH);
            modified = true;
        }

        // 2. Strip jailbreak markers (replace with a neutral placeholder).
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean hitJailbreak = false;
        for (String marker : JAILBREAK_MARKERS) {
            if (lower.contains(marker)) {
                // Replace the exact case-insensitive span with [REDACTED].
                trimmed = replaceIgnoreCase(trimmed, marker, "[redacted]");
                lower = trimmed.toLowerCase(Locale.ROOT);
                hitJailbreak = true;
                modified = true;
            }
        }

        // 3. Escape code fences / role tags.
        String escaped = DELIMITER_PATTERN.matcher(trimmed).replaceAll(match -> {
            String token = match.group();
            return token.replace("`", "'").replace("<", "〈").replace(">", "〉");
        });
        if (!escaped.equals(trimmed)) {
            trimmed = escaped;
            modified = true;
        }

        if (modified) {
            log.debug("PromptSafetyFilter rewrote input: jailbreak={} originalLen={} resultLen={}",
                    hitJailbreak, original.length(), trimmed.length());
        }
        return new Result(trimmed, hitJailbreak, modified);
    }

    /** Convenience overload for the majority of callers that just want the text. */
    public String sanitizeText(String raw) {
        return sanitize(raw).sanitized();
    }

    private static String replaceIgnoreCase(String input, String target, String replacement) {
        StringBuilder out = new StringBuilder(input.length());
        int cursor = 0;
        String lower = input.toLowerCase(Locale.ROOT);
        int idx;
        while ((idx = lower.indexOf(target, cursor)) >= 0) {
            out.append(input, cursor, idx);
            out.append(replacement);
            cursor = idx + target.length();
        }
        out.append(input, cursor, input.length());
        return out.toString();
    }

    public record Result(String sanitized, boolean jailbreakDetected, boolean modified) {
        private static Result clean(String sanitized) {
            return new Result(sanitized, false, false);
        }
    }
}

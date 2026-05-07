package com.alethicode.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 清理将被拼接进 LLM prompt 的用户输入。
 *
 * <ol>
 *   <li>改写常见越狱短语，避免覆盖系统提示。</li>
 *   <li>转义代码围栏和类 XML 角色标签，避免混淆提示分隔符。</li>
 *   <li>截断异常长字段，拦截 prompt stuffing 和误粘贴日志。</li>
 * </ol>
 *
 * 该过滤器只是低成本第一道门禁，完整安全判断仍依赖合规扫描服务。
 */
@Service
public class PromptSafetyFilter {

    private static final Logger log = LoggerFactory.getLogger(PromptSafetyFilter.class);

    /** 单个用户字段的硬长度上限。 */
    private static final int MAX_FIELD_LENGTH = 8_000;

    /** 需要忽略大小写改写的常见越狱片段。 */
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

    /** 匹配连续反引号或类 XML 角色标签。 */
    private static final Pattern DELIMITER_PATTERN = Pattern.compile(
            "(```+|<\\|(system|user|assistant|tool)\\|>)", Pattern.CASE_INSENSITIVE);

    /**
     * 清理单个用户字段，并返回可用于日志和指标的诊断结果。
     */
    public Result sanitize(String raw) {
        if (raw == null) return Result.clean("");
        String original = raw;
        boolean modified = false;

        String trimmed = raw;
        if (trimmed.length() > MAX_FIELD_LENGTH) {
            trimmed = trimmed.substring(0, MAX_FIELD_LENGTH);
            modified = true;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean hitJailbreak = false;
        for (String marker : JAILBREAK_MARKERS) {
            if (lower.contains(marker)) {
                trimmed = replaceIgnoreCase(trimmed, marker, "[redacted]");
                lower = trimmed.toLowerCase(Locale.ROOT);
                hitJailbreak = true;
                modified = true;
            }
        }

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

    /** 返回清理后的文本。 */
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

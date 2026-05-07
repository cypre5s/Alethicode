package com.alethicode.service.languagepack.impl;

import com.alethicode.service.languagepack.ConversationContextService;
import com.alethicode.service.languagepack.SessionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ConversationContextServiceImpl implements ConversationContextService {

    private static final int CONTEXT_LIMIT = 14;
    private static final int MAX_CONTEXT_CHARS = 3000;

    private final JdbcTemplate jdbcTemplate;

    public ConversationContextServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String buildRecentContext(Long sessionId) {
        if (sessionId == null) {
            return "";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT role,
                       content,
                       CASE
                           WHEN answer_json IS NULL THEN ''
                           ELSE coalesce(answer_json ->> 'answer_markdown', '')
                       END AS answer_markdown
                FROM language_pack_chat_message
                WHERE session_id = ?
                ORDER BY id DESC
                LIMIT ?
                """,
                sessionId,
                CONTEXT_LIMIT
        );
        if (rows.isEmpty()) {
            return "";
        }
        Collections.reverse(rows);
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String role = String.valueOf(row.getOrDefault("role", ""));
            String rawContent = "assistant".equals(role)
                    ? trimToEmpty(row.get("answer_markdown"))
                    : trimToEmpty(row.get("content"));
            if (rawContent.isBlank()) {
                continue;
            }
            String prefix = "assistant".equals(role) ? "A: " : "Q: ";
            String normalized = rawContent.replaceAll("\\s+", " ").trim();
            lines.add(prefix + normalized);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (builder.length() + line.length() + 1 > MAX_CONTEXT_CHARS) {
                break;
            }
            builder.insert(0, line + "\n");
        }
        return builder.toString().trim();
    }

    @Override
    public SessionContext buildSessionContext(Long sessionId) {
        if (sessionId == null) {
            return new SessionContext(null, "", "", List.of());
        }
        String recentDialogue = buildRecentContext(sessionId);
        String sessionSummary = buildSessionSummary(sessionId);
        List<Long> recentCitedPageIds = loadRecentCitedPageIds(sessionId);
        return new SessionContext(sessionId, recentDialogue, sessionSummary, recentCitedPageIds);
    }

    private String buildSessionSummary(Long sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT content
                FROM language_pack_chat_message
                WHERE session_id = ? AND role = 'user'
                ORDER BY id DESC
                LIMIT 5
                """,
                sessionId
        );
        if (rows.isEmpty()) {
            return "";
        }
        Collections.reverse(rows);
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            String content = trimToEmpty(row.get("content"));
            if (!content.isBlank()) {
                sb.append(compact(content)).append("; ");
            }
        }
        return sb.toString().trim();
    }

    private List<Long> loadRecentCitedPageIds(Long sessionId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT (elem ->> 'page_id')::bigint AS page_id
                FROM language_pack_chat_retrieval_log r,
                     jsonb_array_elements(r.page_hit_json) AS elem
                WHERE r.session_id = ?
                  AND r.page_hit_json IS NOT NULL
                  AND r.page_hit_json != '[]'::jsonb
                ORDER BY page_id
                LIMIT 10
                """,
                (rs, rowNum) -> rs.getLong("page_id"),
                sessionId
        );
    }

    /**
     * 按距离当前消息的远近压缩文本。
     *
     * 近距离保留全文，中距离压缩回答，远距离只保留主题或首句。
     */
    private String compressByDistance(String text, int distance, boolean isAssistant) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (distance <= 1) {
            return normalized;
        }
        if (distance <= 5) {
            if (!isAssistant) {
                return normalized;
            }
            return extractFirstAndLastSentence(normalized);
        }
        if (!isAssistant) {
            return extractFirstSentence(normalized);
        }
        return extractFirstSentence(normalized);
    }

    private String extractFirstSentence(String text) {
        int end = findSentenceEnd(text, 0);
        if (end < 0 || end >= text.length() - 1) {
            return text.length() > 150 ? text.substring(0, 150) + "…" : text;
        }
        return text.substring(0, end + 1).trim();
    }

    private String extractFirstAndLastSentence(String text) {
        int firstEnd = findSentenceEnd(text, 0);
        if (firstEnd < 0 || firstEnd >= text.length() - 5) {
            return text.length() > 300 ? text.substring(0, 300) + "…" : text;
        }
        String first = text.substring(0, firstEnd + 1).trim();
        int lastStart = text.lastIndexOf('。');
        if (lastStart < 0) lastStart = text.lastIndexOf('.');
        if (lastStart <= firstEnd) {
            return first;
        }
        int secondLastStart = text.lastIndexOf('。', lastStart - 1);
        if (secondLastStart < 0) secondLastStart = text.lastIndexOf('.', lastStart - 1);
        String last = text.substring(Math.max(firstEnd + 1, secondLastStart + 1)).trim();
        return first + " … " + last;
    }

    private int findSentenceEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '.' || c == '！' || c == '?' || c == '？') {
                if (i > from + 5) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String compact(Object value) {
        return compact(value, 240);
    }

    private String compact(Object value, int maxLen) {
        String text = trimToEmpty(value).replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

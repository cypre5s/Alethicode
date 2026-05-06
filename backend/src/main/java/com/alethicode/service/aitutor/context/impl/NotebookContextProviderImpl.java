package com.alethicode.service.aitutor.context.impl;

import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.context.NotebookContextProvider;
import com.alethicode.service.aitutor.context.NotebookSummary;
import com.alethicode.service.aitutor.context.ReferenceResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotebookContextProviderImpl implements NotebookContextProvider {

    private final JdbcTemplate jdbcTemplate;

    public NotebookContextProviderImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<NotebookSummary> resolveNotebookReferences(long userId, List<String> rawTokens) {
        if (rawTokens == null || rawTokens.isEmpty()) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        for (String raw : rawTokens) {
            String id = ReferenceResolver.extractNotebookEntryId(raw);
            if (id != null && !id.isBlank()) ids.add(id);
        }
        if (ids.isEmpty()) return List.of();
        List<NotebookSummary> result = new ArrayList<>();
        for (String id : ids) {
            List<NotebookSummary> rows = loadNotebook(userId, id);
            if (rows.isEmpty()) {
                throw BusinessExceptions.fromLegacy("permission-denied", "Notebook reference not accessible: " + id);
            }
            result.add(rows.get(0));
        }
        return result;
    }

    private List<NotebookSummary> loadNotebook(long userId, String entryId) {
        return jdbcTemplate.query("""
                SELECT id, root_cause, fix_outcome, student_reflection, entry_type, create_time
                FROM ai_learner_notebook
                WHERE id = ? AND user_id = ? AND is_deleted = FALSE
                LIMIT 1
                """, (rs, rowNum) -> {
            String title = rs.getString("root_cause");
            if (title == null || title.isBlank()) {
                title = rs.getString("entry_type");
            }
            String content = String.join("\n",
                    safe(rs.getString("root_cause")),
                    safe(rs.getString("fix_outcome")),
                    safe(rs.getString("student_reflection"))
            ).trim();
            Timestamp created = rs.getTimestamp("create_time");
            return new NotebookSummary(
                    rs.getString("id"),
                    title,
                    content,
                    created == null ? null : created.toInstant(),
                    Instant.now()
            );
        }, entryId, userId);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

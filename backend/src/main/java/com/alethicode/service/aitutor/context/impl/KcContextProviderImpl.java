package com.alethicode.service.aitutor.context.impl;

import com.alethicode.service.aitutor.context.KcContextProvider;
import com.alethicode.service.aitutor.context.KcSummary;
import com.alethicode.service.aitutor.context.ReferenceResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KcContextProviderImpl implements KcContextProvider {

    private final JdbcTemplate jdbcTemplate;

    public KcContextProviderImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KcSummary> resolveKcReferences(long userId, List<String> rawTokens) {
        if (rawTokens == null || rawTokens.isEmpty()) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        for (String raw : rawTokens) {
            String id = ReferenceResolver.extractKcId(raw);
            if (id != null && !id.isBlank()) ids.add(id);
        }
        if (ids.isEmpty()) return List.of();
        List<KcSummary> result = new ArrayList<>();
        for (String id : ids) {
            result.addAll(loadKc(userId, id));
        }
        return result;
    }

    private List<KcSummary> loadKc(long userId, String kcId) {
        boolean numeric = kcId.matches("\\d+");
        String where = numeric
                ? "kc.id = ?"
                : "(kc.name_normalized = ? OR kc.name_en = ?)";
        Object[] args = numeric
                ? new Object[]{Long.parseLong(kcId), userId}
                : new Object[]{kcId, kcId, userId};
        return jdbcTemplate.query("""
                SELECT kc.id, kc.name, kc.description, ch.title AS chapter_title,
                       COALESCE(m.mastery, 0) AS mastery
                FROM language_pack_kc kc
                LEFT JOIN language_pack_chapter ch ON ch.id = kc.chapter_id
                LEFT JOIN learner_kc_mastery m ON m.kc_id = kc.id AND m.user_id = ?
                WHERE %s
                ORDER BY kc.id
                LIMIT 1
                """.formatted(where), (rs, rowNum) -> new KcSummary(
                String.valueOf(rs.getLong("id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("chapter_title") == null ? List.of() : List.of(rs.getString("chapter_title")),
                rs.getDouble("mastery"),
                Instant.now()
        ), reorderArgs(args, numeric));
    }

    private Object[] reorderArgs(Object[] args, boolean numeric) {
        if (numeric) return new Object[]{args[1], args[0]};
        return new Object[]{args[2], args[0], args[1]};
    }
}

package com.alethicode.service.twin.kc;

import com.alethicode.dto.response.twin.KcGalaxyResponse;
import com.alethicode.dto.response.twin.KcGalaxyResponse.KcGalaxyEdge;
import com.alethicode.dto.response.twin.KcGalaxyResponse.KcGalaxyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class KcGalaxyProjector {

    private static final Logger log = LoggerFactory.getLogger(KcGalaxyProjector.class);
    private static final Set<String> VALID_RELATION_TYPES = Set.of("prerequisite", "related", "applies_to");

    private final JdbcTemplate jdbcTemplate;

    public KcGalaxyProjector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KcGalaxyResponse project(Long userId, Long languagePackId) {
        List<KcGalaxyNode> nodes = queryNodes(userId, languagePackId);
        if (nodes.isEmpty()) {
            return new KcGalaxyResponse(List.of(), List.of());
        }
        List<KcGalaxyEdge> edges = queryEdges(languagePackId);
        return new KcGalaxyResponse(nodes, edges);
    }

    private List<KcGalaxyNode> queryNodes(Long userId, Long languagePackId) {
        String sql;
        Object[] params;

        if (languagePackId != null) {
            sql = """
                SELECT
                  kc.id AS kc_id,
                  kc.name,
                  COALESCE(m.mastery, 0) AS mastery,
                  m.last_attempt_at AS last_touched_at,
                  COALESCE(ev.recent_count, 0) AS recent_event_count,
                  COALESCE(ch.title, '') AS category
                FROM language_pack_kc kc
                LEFT JOIN learner_kc_mastery m
                  ON m.kc_id = kc.id AND m.user_id = ? AND m.language_pack_id = kc.language_pack_id
                LEFT JOIN language_pack_chapter ch ON ch.id = kc.chapter_id
                LEFT JOIN LATERAL (
                  SELECT COUNT(*) AS recent_count
                  FROM ai_learning_event e
                  WHERE e.user_id = ? AND e.created_at >= NOW() - INTERVAL '7 days'
                    AND e.extra_data->>'kc_id' = kc.id::TEXT
                ) ev ON TRUE
                WHERE kc.language_pack_id = ?
                ORDER BY kc.id
                """;
            params = new Object[]{ userId, userId, languagePackId };
        } else {
            sql = """
                SELECT
                  kc.id AS kc_id,
                  kc.name,
                  COALESCE(m.mastery, 0) AS mastery,
                  m.last_attempt_at AS last_touched_at,
                  0 AS recent_event_count,
                  COALESCE(ch.title, '') AS category
                FROM language_pack_kc kc
                INNER JOIN learner_kc_mastery m
                  ON m.kc_id = kc.id AND m.user_id = ?
                LEFT JOIN language_pack_chapter ch ON ch.id = kc.chapter_id
                ORDER BY kc.id
                """;
            params = new Object[]{ userId };
        }

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("last_touched_at");
            return new KcGalaxyNode(
                    rs.getLong("kc_id"),
                    rs.getString("name"),
                    rs.getDouble("mastery"),
                    ts != null ? ts.toInstant() : null,
                    rs.getInt("recent_event_count"),
                    rs.getString("category")
            );
        }, params);
    }

    private List<KcGalaxyEdge> queryEdges(Long languagePackId) {
        if (languagePackId != null) {
            return jdbcTemplate.query("""
                SELECT kc_id AS from_kc_id, prerequisite_kc_id AS to_kc_id,
                       'prerequisite' AS relation_type, 1.0 AS weight
                FROM language_pack_kc_prerequisite
                WHERE language_pack_id = ?
                """, (rs, rowNum) -> new KcGalaxyEdge(
                    rs.getLong("from_kc_id"),
                    rs.getLong("to_kc_id"),
                    rs.getString("relation_type"),
                    rs.getDouble("weight")
            ), languagePackId);
        }

        return jdbcTemplate.query("""
            SELECT from_kc_id, to_kc_id, relation_type, weight
            FROM ai_kc_relation
            WHERE relation_type IN ('prerequisite', 'related', 'applies_to')
            """, (rs, rowNum) -> {
            String relType = rs.getString("relation_type");
            if (!VALID_RELATION_TYPES.contains(relType)) {
                relType = "related";
            }
            return new KcGalaxyEdge(
                    rs.getLong("from_kc_id"),
                    rs.getLong("to_kc_id"),
                    relType,
                    rs.getDouble("weight")
            );
        });
    }
}

package com.alethicode.service.problem;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 查询某道题对应的相关课件例题。
 *
 * 从 Controller 抽出，避免 Controller 直连 JdbcTemplate（见 BUG #37）。
 */
@Service
public class RelatedExampleQueryService {

    private static final int MAX_EXAMPLES = 5;

    private final JdbcTemplate jdbcTemplate;

    public RelatedExampleQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findByProblemId(Long problemId) {
        if (problemId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT e.id, e.source_title, e.normalized_body, e.evidence_excerpt,
                       e.unit_type, k.name AS kc_name, c.title AS chapter_title
                FROM language_pack_problem_mapping lpm
                JOIN language_pack_problem_generation_log g ON g.id = lpm.generation_log_id
                JOIN language_pack_example e ON e.language_pack_id = lpm.language_pack_id
                JOIN language_pack_example_kc_mapping ekm ON ekm.example_id = e.id AND ekm.kc_id = g.kc_id
                JOIN language_pack_kc k ON k.id = g.kc_id
                JOIN language_pack_chapter c ON c.id = k.chapter_id
                WHERE lpm.problem_id = ?
                ORDER BY e.id
                LIMIT ?
                """, problemId, MAX_EXAMPLES);
    }
}

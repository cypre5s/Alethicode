package com.alethicode.service.languagepack.impl;

import com.alethicode.service.languagepack.CourseStructureService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseStructureServiceImpl implements CourseStructureService {

    private final JdbcTemplate jdbcTemplate;

    public CourseStructureServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> getCourseStructure(Long languagePackId) {
        Map<String, Object> pack = jdbcTemplate.queryForMap(
                """
                SELECT id, name, primary_language, status, course_objective, target_audience, total_hours
                FROM language_pack WHERE id = ?
                """, languagePackId);

        List<Map<String, Object>> chapters = jdbcTemplate.queryForList(
                """
                SELECT id, chapter_index, title, learning_objective, estimated_hours
                FROM language_pack_chapter
                WHERE language_pack_id = ?
                ORDER BY chapter_index
                """, languagePackId);

        for (Map<String, Object> chapter : chapters) {
            Long chapterId = ((Number) chapter.get("id")).longValue();

            List<Map<String, Object>> kcs = jdbcTemplate.queryForList(
                    """
                    SELECT k.id, k.name, k.description,
                           COALESCE(
                               (SELECT json_agg(p.prerequisite_kc_id)
                                FROM language_pack_kc_prerequisite p
                                WHERE p.kc_id = k.id), '[]'
                           ) AS prerequisite_ids
                    FROM language_pack_kc k
                    WHERE k.chapter_id = ?
                    ORDER BY k.id
                    """, chapterId);

            for (Map<String, Object> kc : kcs) {
                Long kcId = ((Number) kc.get("id")).longValue();

                List<Map<String, Object>> examples = jdbcTemplate.queryForList(
                        """
                        SELECT e.id, e.source_title, e.normalized_body
                        FROM language_pack_example_kc_mapping ekm
                        JOIN language_pack_example e ON e.id = ekm.example_id
                        WHERE ekm.kc_id = ?
                        ORDER BY e.id
                        """, kcId);
                kc.put("examples", examples);

                List<Map<String, Object>> problems = jdbcTemplate.queryForList(
                        """
                        SELECT pm.problem_id, p.title
                        FROM language_pack_problem_mapping pm
                        JOIN language_pack_problem_generation_log pgl ON pgl.id = pm.generation_log_id
                        JOIN problem p ON p.id = pm.problem_id
                        WHERE pgl.kc_id = ?
                        ORDER BY pm.problem_id
                        """, kcId);
                kc.put("problems", problems);
            }
            chapter.put("kcs", kcs);

            List<Map<String, Object>> reviewTasks = jdbcTemplate.queryForList(
                    """
                    SELECT id, task_type, title, description, problem_count, sort_order
                    FROM language_pack_review_task
                    WHERE chapter_id = ?
                    ORDER BY sort_order
                    """, chapterId);
            chapter.put("review_tasks", reviewTasks);
        }

        Map<String, Object> result = new LinkedHashMap<>(pack);
        result.put("chapters", chapters);
        return result;
    }

    @Override
    public Map<String, Object> getKcGraph(Long languagePackId) {
        List<Map<String, Object>> nodes = jdbcTemplate.queryForList(
                """
                SELECT k.id, k.name, k.description, c.chapter_index, c.title AS chapter_title
                FROM language_pack_kc k
                JOIN language_pack_chapter c ON c.id = k.chapter_id
                WHERE k.language_pack_id = ?
                ORDER BY c.chapter_index, k.id
                """, languagePackId);

        List<Map<String, Object>> edges = jdbcTemplate.queryForList(
                """
                SELECT kc_id AS target, prerequisite_kc_id AS source
                FROM language_pack_kc_prerequisite
                WHERE language_pack_id = ?
                """, languagePackId);

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("language_pack_id", languagePackId);
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }
}

package com.alethicode.service.aitutor.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CrossCourseProfileService {

    private final JdbcTemplate jdbcTemplate;

    public CrossCourseProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> loadActionBias(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        List<String> weakKcs = jdbcTemplate.query(
                """
                select jsonb_array_elements_text(weak_kcs)::text as weak_kc
                from ai_learner_profile_snapshot
                where user_id = ?
                order by created_at desc
                limit 10
                """,
                (rs, rowNum) -> rs.getString("weak_kc"),
                userId
        );
        String dominantFrustration = jdbcTemplate.query(
                """
                select frustration_level
                from ai_learner_profile_snapshot
                where user_id = ?
                group by frustration_level
                order by count(*) desc, frustration_level asc
                limit 1
                """,
                rs -> rs.next() ? rs.getString("frustration_level") : null,
                userId
        );
        Integer snapshotCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_learner_profile_snapshot where user_id = ?",
                Integer.class,
                userId
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cross_course_weak_kcs", weakKcs.stream().distinct().limit(5).toList());
        result.put("source", weakKcs.isEmpty() ? "cold_start" : "profile_snapshot");
        result.put("dominant_frustration_level", dominantFrustration == null ? "low" : dominantFrustration);
        result.put("prior_snapshot_count", snapshotCount == null ? 0 : snapshotCount);
        result.put("preferred_action", weakKcs.isEmpty() ? "transfer" : "ac_review");
        return result;
    }
}

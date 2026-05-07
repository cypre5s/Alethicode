package com.alethicode.mcp;

import com.alethicode.service.aitutor.path.LearningPathOptimizerService;
import com.alethicode.service.aitutor.profile.LearnerProfileProjector;
import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
import com.alethicode.service.aitutor.supplement.BeginnerSupplementPlannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具背后的业务逻辑提供者。
 *
 * 本服务不直接依赖 Spring AI MCP 注解；实际工具注册由启用 profile 后的 Registrar 完成。
 */
@Service
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
public class AlethicodeMcpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(AlethicodeMcpToolProvider.class);

    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private LearnerProfileProjector profileProjector;
    @Autowired(required = false)
    private CoursewareRetrievalService coursewareService;
    @Autowired(required = false)
    private BeginnerSupplementPlannerService supplementPlannerService;
    @Autowired(required = false)
    private LearningPathOptimizerService learningPathService;

    public AlethicodeMcpToolProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getProblem(Long problemId) {
        return jdbcTemplate.queryForMap("""
            SELECT id, title, description, input_description, output_description,
                   hint, difficulty, time_limit, memory_limit
            FROM problem WHERE id = ?
            """, problemId);
    }

    public Map<String, Object> getSubmission(Long submissionId) {
        return jdbcTemplate.queryForMap("""
            SELECT s.id, s.problem_id, s.user_id, s.result, s.language,
                   s.create_time, s.statistic_info
            FROM submission s WHERE s.id = ?
            """, submissionId);
    }

    public Map<String, Object> getLearnerMastery(Long userId, Long languagePackId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name, km.mastery
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            WHERE km.user_id = ? AND km.language_pack_id = ?
            """, userId, languagePackId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", userId);
        result.put("language_pack_id", languagePackId);
        result.put("masteries", rows);
        return result;
    }

    public Map<String, Object> getLearnerProfile(Long userId) {
        if (profileProjector == null) {
            return Map.of("error", "LearnerProfileProjector not available");
        }
        var state = profileProjector.project(userId, null, Map.of(), null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", userId);
        result.put("calibrated", state.calibrated());
        result.put("mastery_by_kc", state.masteryByKc());
        result.put("weak_kcs", state.weakKcs());
        result.put("frustration_level", state.frustrationLevel());
        result.put("confidence_proxy", state.confidenceProxy());
        return result;
    }

    public List<Map<String, Object>> searchCourseware(String query, Long languagePackId) {
        if (coursewareService == null) {
            return List.of();
        }
        return coursewareService.retrieve(null, List.of(), query, 5, languagePackId);
    }

    public Map<String, Object> recommendProblem(Long userId, Long languagePackId) {
        if (supplementPlannerService == null) {
            return Map.of("error", "SupplementPlannerService not available");
        }
        return supplementPlannerService.buildPlan(userId, "daily_review", languagePackId, null, null, null, null);
    }

    public Map<String, Object> getLearningPath(Long userId, Long languagePackId) {
        if (learningPathService == null) {
            return Map.of("error", "LearningPathOptimizerService not available");
        }
        return learningPathService.computePath(userId, languagePackId);
    }
}

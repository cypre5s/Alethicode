package com.alethicode.service.career.path;

import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.rollout.RolloutDecision;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import com.alethicode.service.career.bridging.CareerBridgingService;
import com.alethicode.service.career.bridging.MilestoneType;
import com.alethicode.service.career.preference.CareerPreferenceService;
import com.alethicode.service.career.preference.CareerPreferenceServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CareerPathServiceImpl implements CareerPathService {

    private static final Logger log = LoggerFactory.getLogger(CareerPathServiceImpl.class);
    private static final double UNLOCK_PARENT_THRESHOLD = 0.7;
    private static final double UNLOCK_SELF_THRESHOLD = 0.5;
    private static final double IN_PROGRESS_THRESHOLD = 0.3;
    /** plan 9 节灰度 4 个 experiment_id 之一：Path buildView 走 evaluate，rollback 直接返回空节点。 */
    private static final String EXPERIMENT_ID = "career_path";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MasteryService masteryService;
    private final CareerBridgingService careerBridgingService;
    private final RolloutPolicyService rolloutPolicyService;
    private final CareerPreferenceService preferenceService;

    public CareerPathServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MasteryService masteryService,
            CareerBridgingService careerBridgingService,
            RolloutPolicyService rolloutPolicyService,
            CareerPreferenceService preferenceService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.masteryService = masteryService;
        this.careerBridgingService = careerBridgingService;
        this.rolloutPolicyService = rolloutPolicyService;
        this.preferenceService = preferenceService;
    }

    @Override
    public CareerPathView buildView(long userId, String majorCode) {
        String majorNameZh = loadMajorNameZh(majorCode);
        if (preferenceService.isModuleDisabled(userId, CareerPreferenceServiceImpl.MODULE_CAREER_PATH)) {
            log.info("career path skipped (user-level disabled): user={}", userId);
            return new CareerPathView(majorCode, majorNameZh, List.of());
        }
        RolloutDecision decision = rolloutPolicyService.evaluate(
                EXPERIMENT_ID, "user:" + userId, Map.of());
        if ("rollback".equals(decision.rolloutMode())) {
            log.info("career path rolled back for user={}, major={}, reason={}",
                    userId, majorCode, decision.reason());
            return new CareerPathView(majorCode, majorNameZh, List.of());
        }
        List<PathNodeRow> rows = loadPathNodes(majorCode);
        if (rows.isEmpty()) {
            return new CareerPathView(majorCode, majorNameZh, List.of());
        }

        Map<String, Double> masteryByKc = masteryService.projectMasteryByLanguagePack(userId, null);

        Map<String, Double> kcMasteryMap = new LinkedHashMap<>();
        for (PathNodeRow row : rows) {
            kcMasteryMap.put(row.kcCode(), masteryByKc.get(row.kcCode()));
        }

        List<CareerPathNodeView> nodes = new ArrayList<>();
        for (PathNodeRow row : rows) {
            Double selfMastery = kcMasteryMap.get(row.kcCode());
            String status = determineStatus(row.parentKcCode(), selfMastery, kcMasteryMap);
            List<String> useCases = parseJsonStringList(row.typicalUseCasesJson());
            nodes.add(new CareerPathNodeView(
                    row.kcCode(), row.parentKcCode(), row.whyMd(),
                    useCases, selfMastery, status, row.sortOrder()
            ));
        }

        return new CareerPathView(majorCode, majorNameZh, nodes);
    }

    @Override
    public void markNodeUnlocked(long userId, String majorCode, String kcCode) {
        ensurePathNodeExists(majorCode, kcCode);
        careerBridgingService.recordMilestone(
                userId, MilestoneType.PATH_NODE_UNLOCKED,
                majorCode + ":" + kcCode
        );
        log.info("career path node unlocked: user={}, major={}, kc={}", userId, majorCode, kcCode);
    }

    /**
     * 校验 (majorCode, kcCode) 二元组真实存在于 {@code career_path_node}，
     * 避免任意字符串污染 {@code career_bridging_milestone.milestone_ref}。
     */
    private void ensurePathNodeExists(String majorCode, String kcCode) {
        if (majorCode == null || majorCode.isBlank()
                || kcCode == null || kcCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "major_code / kc_code required");
        }
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from career_path_node where major_code = ? and kc_code = ?)",
                Boolean.class, majorCode, kcCode);
        if (exists == null || !exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "career_path_node not found: major=" + majorCode + ", kc=" + kcCode);
        }
    }

    private String determineStatus(String parentKcCode, Double selfMastery,
                                   Map<String, Double> masteryMap) {
        if (parentKcCode != null) {
            Double parentMastery = masteryMap.get(parentKcCode);
            if (parentMastery == null || parentMastery < UNLOCK_PARENT_THRESHOLD) {
                return "locked";
            }
        }
        if (selfMastery == null) {
            return parentKcCode == null ? "in_progress" : "locked";
        }
        if (selfMastery >= UNLOCK_SELF_THRESHOLD) {
            return "unlocked";
        }
        if (selfMastery >= IN_PROGRESS_THRESHOLD) {
            return "in_progress";
        }
        return "locked";
    }

    private String loadMajorNameZh(String majorCode) {
        try {
            return jdbcTemplate.queryForObject(
                    "select name_zh from career_major_dictionary where code = ? and enabled = true",
                    String.class, majorCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "major not found: " + majorCode);
        }
    }

    private List<PathNodeRow> loadPathNodes(String majorCode) {
        return jdbcTemplate.query("""
                select kc_code, parent_kc_code, why_md,
                       typical_use_cases::text as typical_use_cases_json, sort_order
                from career_path_node
                where major_code = ?
                order by sort_order asc, kc_code asc
                """,
                (rs, rowNum) -> new PathNodeRow(
                        rs.getString("kc_code"), rs.getString("parent_kc_code"),
                        rs.getString("why_md"), rs.getString("typical_use_cases_json"),
                        rs.getInt("sort_order")
                ), majorCode
        );
    }

    private List<String> parseJsonStringList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private record PathNodeRow(String kcCode, String parentKcCode, String whyMd,
                               String typicalUseCasesJson, int sortOrder) {}
}

package com.alethicode.service.career.bridging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Career Bridging 里程碑触发器（plan 3.1 节）。
 *
 * <p>提供静态式入口给 MasteryService / LanguagePackQaService 等已有服务
 * 在关键时刻回调，登记 KC 毕业 / 章节进入里程碑。不用 Spring Event
 * 机制（避免引入事件总线复杂度，保持 fail fast 直调）。
 */
@Component
public class CareerMilestoneEventListener {

    private static final Logger log = LoggerFactory.getLogger(CareerMilestoneEventListener.class);
    private static final double KC_GRADUATED_THRESHOLD = 0.7;

    private final CareerBridgingService careerBridgingService;
    private final JdbcTemplate jdbcTemplate;

    public CareerMilestoneEventListener(
            CareerBridgingService careerBridgingService,
            JdbcTemplate jdbcTemplate
    ) {
        this.careerBridgingService = careerBridgingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 当 MasteryService 更新后调用：检查该用户是否有 KC 簇 mastery 均值
     * 从 < 0.7 升至 >= 0.7，若是则插入 kc_cluster_graduated 里程碑。
     *
     * @param userId     学生 id
     * @param masteryByKc 当前最新 mastery 快照
     */
    public void onMasteryUpdated(long userId, Map<String, Double> masteryByKc) {
        if (masteryByKc == null || masteryByKc.isEmpty()) {
            return;
        }
        if (!hasCareerProfile(userId)) {
            return;
        }

        List<String> graduatedKcs = masteryByKc.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() >= KC_GRADUATED_THRESHOLD)
                .map(Map.Entry::getKey)
                .toList();

        if (graduatedKcs.isEmpty()) {
            return;
        }

        String clusterRef = String.join(",", graduatedKcs);
        try {
            careerBridgingService.recordMilestone(
                    userId, MilestoneType.KC_CLUSTER_GRADUATED, clusterRef);
            log.debug("career milestone kc_cluster_graduated: user={}, kcs={}", userId, clusterRef);
        } catch (Exception e) {
            log.debug("career milestone kc_cluster_graduated skipped (likely duplicate): user={}", userId);
        }
    }

    /**
     * 当学生进入新章节 / 课件包时调用。
     *
     * @param userId        学生 id
     * @param chapterRef    章节标识（如 language_pack_id 或章节名）
     */
    public void onChapterEntered(long userId, String chapterRef) {
        if (!hasCareerProfile(userId)) {
            return;
        }
        try {
            careerBridgingService.recordMilestone(
                    userId, MilestoneType.CHAPTER_ENTERED, chapterRef);
            log.debug("career milestone chapter_entered: user={}, ref={}", userId, chapterRef);
        } catch (Exception e) {
            log.debug("career milestone chapter_entered skipped: user={}", userId);
        }
    }

    private boolean hasCareerProfile(long userId) {
        try {
            String majorCode = jdbcTemplate.queryForObject(
                    "select major_code from user_profile where user_id = ?",
                    String.class, userId);
            return majorCode != null && !majorCode.isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}

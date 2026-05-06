package com.alethicode.service.career.bridging;

import com.alethicode.config.AlethicodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Career Bridging 里程碑触发器（plan 3.1 节 + todo 10）。
 *
 * <p>提供「直调式」入口给已有领域服务在关键时刻回调：
 * <ul>
 *   <li>{@link #onMasteryUpdated} 由
 *   {@link com.alethicode.service.submission.JudgeCompletedEventListener#handleMasteryUpdate}
 *   在 {@code learner_kc_mastery} 写入后调用，KC 跨过 0.7 视为毕业；</li>
 *   <li>{@link #onLanguagePackEntered} 由
 *   {@link com.alethicode.service.classroom.LearnerCourseProgressService#getOrCreateProgress}
 *   首次 INSERT 时调用，登记 chapter_entered。</li>
 * </ul>
 *
 * <p>边界保护：
 * <ol>
 *   <li>career.bridging.enabled=false 时静默跳过（不阻塞主链路）；</li>
 *   <li>学生 user_profile.major_code 为空时静默跳过（非 career 学生不应被打扰）；</li>
 *   <li>{@link CareerBridgingService#recordMilestone} 三元组 (user, type, ref) 幂等，
 *       重复触发不会重复插入。</li>
 * </ol>
 *
 * <p>不接管异常：listener 只做边界判断；DB 异常上抛，由调用方
 * （{@code JudgeCompletedEventListener.handleMasteryUpdate} /
 * {@code LearnerCourseProgressService.getOrCreateProgress}）现有 try/catch 兜底，
 * 保持 AGENTS.md 「fail fast，不写防御性掩盖问题逻辑」前提下不污染现有主链路。
 */
@Component
public class CareerMilestoneEventListener {

    private static final Logger log = LoggerFactory.getLogger(CareerMilestoneEventListener.class);
    /** mastery >= 该阈值视为「该 KC 已毕业」，与 CareerPathServiceImpl unlocked 阈值一致。 */
    private static final double KC_GRADUATED_THRESHOLD = 0.7;

    private final CareerBridgingService careerBridgingService;
    private final AlethicodeProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public CareerMilestoneEventListener(
            CareerBridgingService careerBridgingService,
            AlethicodeProperties properties,
            JdbcTemplate jdbcTemplate
    ) {
        this.careerBridgingService = careerBridgingService;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 一条 (user, language_pack, kc) mastery 行写入后调用：mastery 跨过 0.7
     * 阈值即登记一条 {@code kc_cluster_graduated} 里程碑。
     *
     * @param userId         学生 id
     * @param languagePackId 课件包 id（与 mastery 行一致）
     * @param kcId           KC id（与 mastery 行一致）
     */
    public void onMasteryUpdated(long userId, long languagePackId, long kcId) {
        if (!isCareerEnabled() || !hasCareerProfile(userId)) {
            return;
        }
        Double mastery = loadMastery(userId, languagePackId, kcId);
        if (mastery == null || mastery < KC_GRADUATED_THRESHOLD) {
            return;
        }
        String ref = "lp:" + languagePackId + ":kc:" + kcId;
        careerBridgingService.recordMilestone(userId, MilestoneType.KC_CLUSTER_GRADUATED, ref);
        log.debug("career milestone kc_cluster_graduated: user={}, ref={}, mastery={}",
                userId, ref, mastery);
    }

    /**
     * 学生首次进入某课件包时调用，登记 {@code chapter_entered} 里程碑。
     *
     * @param userId         学生 id
     * @param languagePackId 课件包 id
     */
    public void onLanguagePackEntered(long userId, long languagePackId) {
        if (!isCareerEnabled() || !hasCareerProfile(userId)) {
            return;
        }
        String ref = "lp:" + languagePackId;
        careerBridgingService.recordMilestone(userId, MilestoneType.CHAPTER_ENTERED, ref);
        log.debug("career milestone chapter_entered: user={}, ref={}", userId, ref);
    }

    private boolean isCareerEnabled() {
        return properties.getCareer().getBridging().isEnabled();
    }

    /**
     * user_profile 行不存在或 major_code 为空 ⇒ 非 career 学生，静默跳过。
     * 注：CareerBridgingServiceImpl.ensureProfile 必然先于本入口写入 major_code。
     */
    private boolean hasCareerProfile(long userId) {
        List<String> rows = jdbcTemplate.queryForList(
                "select major_code from user_profile where user_id = ?",
                String.class, userId);
        if (rows.isEmpty()) {
            return false;
        }
        String majorCode = rows.get(0);
        return majorCode != null && !majorCode.isBlank();
    }

    private Double loadMastery(long userId, long languagePackId, long kcId) {
        List<Double> rows = jdbcTemplate.queryForList(
                "select mastery from learner_kc_mastery where user_id = ? and language_pack_id = ? and kc_id = ?",
                Double.class, userId, languagePackId, kcId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}

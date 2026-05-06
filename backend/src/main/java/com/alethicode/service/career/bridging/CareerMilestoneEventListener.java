package com.alethicode.service.career.bridging;

import com.alethicode.config.AlethicodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Career Bridging 里程碑触发器（plan 3.1 节 + todo 10）。
 *
 * <p>提供「直调式」入口给已有领域服务在关键时刻回调：
 * <ul>
 *   <li>{@link #onMasteryUpdated}：单 KC mastery 写入后聚合到所在 chapter，
 *       <strong>chapter 内所有 KC mastery 均值 ≥ 0.7</strong> 视为「KC 簇毕业」
 *       （plan 3.1 节强约束：「某 KC 簇的均值由 &lt; 0.7 升至 ≥ 0.7」）；</li>
 *   <li>{@link #onLanguagePackEntered} 由
 *       {@link com.alethicode.service.classroom.LearnerCourseProgressService#getOrCreateProgress}
 *       首次 INSERT 时调用，登记 chapter_entered。</li>
 * </ul>
 *
 * <p>边界保护：
 * <ol>
 *   <li>career.bridging.enabled=false 时静默跳过（不阻塞主链路）；</li>
 *   <li>学生 user_profile.major_code 为空时静默跳过（非 career 学生不应被打扰）；</li>
 *   <li>{@link CareerBridgingService#recordMilestone} 三元组 (user, type, ref) 幂等，
 *       重复触发不会重复插入；ref 用 {@code lp:&lt;id&gt;:chapter:&lt;id&gt;}
 *       让同一 chapter 仅触发一次（即使后续 KC mastery 还在波动）。</li>
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
    /** chapter 内所有 KC mastery 均值 ≥ 该阈值视为「该 chapter KC 簇毕业」（plan 3.1 节）。 */
    private static final double KC_CLUSTER_GRADUATED_THRESHOLD = 0.7;

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
     * 一条 (user, language_pack, kc) mastery 行写入后调用：聚合到该 KC 所在
     * chapter 的所有 KC mastery 均值，跨过 0.7 阈值即登记
     * {@code kc_cluster_graduated} 里程碑（plan 3.1 节）。
     *
     * <p>kc 没归属 chapter（chapter_id NULL）时不触发——「KC 簇」语义依附于
     * chapter，散落 KC 不计入。
     *
     * @param userId         学生 id
     * @param languagePackId 课件包 id（与 mastery 行一致）
     * @param kcId           KC id（与 mastery 行一致）
     */
    public void onMasteryUpdated(long userId, long languagePackId, long kcId) {
        if (!isCareerEnabled() || !hasCareerProfile(userId)) {
            return;
        }
        Long chapterId = loadChapterIdOf(kcId);
        if (chapterId == null) {
            return;
        }
        Double avgMastery = loadChapterAverageMastery(userId, languagePackId, chapterId);
        if (avgMastery == null || avgMastery < KC_CLUSTER_GRADUATED_THRESHOLD) {
            return;
        }
        String ref = "lp:" + languagePackId + ":chapter:" + chapterId;
        careerBridgingService.recordMilestone(userId, MilestoneType.KC_CLUSTER_GRADUATED, ref);
        log.debug("career milestone kc_cluster_graduated: user={}, ref={}, avgMastery={}",
                userId, ref, avgMastery);
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

    /**
     * 查 KC 所在 chapter；KC 不存在或 chapter_id NULL 时返回 null（散落 KC 不参与簇聚合）。
     */
    private Long loadChapterIdOf(long kcId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select chapter_id from language_pack_kc where id = ?",
                kcId);
        if (rows.isEmpty()) {
            return null;
        }
        Object raw = rows.get(0).get("chapter_id");
        return raw == null ? null : ((Number) raw).longValue();
    }

    /**
     * 计算 (user, language_pack, chapter) 三元组下所有 KC 的 mastery 均值。
     * 该 chapter 没有学生 mastery 记录时返回 null（不触发）。
     */
    private Double loadChapterAverageMastery(long userId, long languagePackId, long chapterId) {
        return jdbcTemplate.queryForObject("""
                select avg(km.mastery)::double precision
                from learner_kc_mastery km
                join language_pack_kc kc on kc.id = km.kc_id
                where km.user_id = ? and km.language_pack_id = ? and kc.chapter_id = ?
                """, Double.class, userId, languagePackId, chapterId);
    }
}

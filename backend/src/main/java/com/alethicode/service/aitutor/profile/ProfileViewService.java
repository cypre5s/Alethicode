package com.alethicode.service.aitutor.profile;

import com.alethicode.dto.response.StudentProfileView;
import com.alethicode.service.aitutor.contract.ErrorTaxonomy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProfileViewService {

    private static final int TOP_WEAK_KCS = 5;
    private static final int TOP_STRONG_KCS = 3;
    private static final int TOP_ERRORS = 5;
    private static final double MASTERY_DISPLAY_FLOOR = 0.4;

    private static final Map<String, String> LEARNING_STYLE_LABELS = Map.of(
            "step_by_step", "step-by-step",
            "exploratory", "exploratory",
            "visual", "visual",
            "analytical", "analytical"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LearnerNarrativeSummaryService narrativeSummaryService;
    private final LearnerMemoryService learnerMemoryService;

    public ProfileViewService(JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              LearnerNarrativeSummaryService narrativeSummaryService,
                              LearnerMemoryService learnerMemoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.narrativeSummaryService = narrativeSummaryService;
        this.learnerMemoryService = learnerMemoryService;
    }

    public StudentProfileView getMyProfile(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        LearnerNarrativeSummaryService.NarrativeSummary narrative = narrativeSummaryService.loadOrGenerate(userId);
        LearningStyle style = learnerMemoryService.inferLearningStyle(userId);
        if (style == null) {
            style = LearningStyle.STEP_BY_STEP;
        }
        boolean personalizationEnabled = !narrative.userDisabled();

        return new StudentProfileView(
                userId,
                personalizationEnabled,
                narrative.userOverridden(),
                buildSummaryView(narrative),
                buildStyleView(style),
                loadTopWeakKcs(userId),
                loadTopStrongKcs(userId),
                loadTopErrors(userId),
                loadStats30d(userId, narrative)
        );
    }

    public void refreshSummary(Long userId) {
        narrativeSummaryService.refreshIfStale(userId);
    }

    public void overrideSummary(Long userId, String summaryText) {
        narrativeSummaryService.overrideSummary(userId, summaryText);
    }

    public void updatePreferences(Long userId, Boolean personalizationEnabled) {
        if (userId == null || personalizationEnabled == null) {
            return;
        }
        if (personalizationEnabled) {
            narrativeSummaryService.enablePersonalization(userId);
        } else {
            narrativeSummaryService.disablePersonalization(userId);
        }
    }

    private StudentProfileView.NarrativeSummaryView buildSummaryView(
            LearnerNarrativeSummaryService.NarrativeSummary narrative) {
        return new StudentProfileView.NarrativeSummaryView(
                narrative.version(),
                narrative.summaryText(),
                narrative.updatedAt(),
                !narrative.userOverridden()
        );
    }

    private StudentProfileView.LearningStyleView buildStyleView(LearningStyle style) {
        String key = style.key();
        return new StudentProfileView.LearningStyleView(
                key,
                LEARNING_STYLE_LABELS.getOrDefault(key, "default")
        );
    }

    private List<StudentProfileView.KcView> loadTopWeakKcs(Long userId) {
        return jdbcTemplate.query(
                "select k.name as kc_name, km.mastery from learner_kc_mastery km "
                        + "join language_pack_kc k on k.id = km.kc_id "
                        + "where km.user_id = ? and km.mastery < 0.6 "
                        + "order by km.mastery asc limit ?",
                (rs, rowNum) -> new StudentProfileView.KcView(
                        rs.getString("kc_name"),
                        round(Math.max(rs.getDouble("mastery"), MASTERY_DISPLAY_FLOOR))
                ),
                userId, TOP_WEAK_KCS
        );
    }

    private List<StudentProfileView.KcView> loadTopStrongKcs(Long userId) {
        return jdbcTemplate.query(
                "select k.name as kc_name, km.mastery from learner_kc_mastery km "
                        + "join language_pack_kc k on k.id = km.kc_id "
                        + "where km.user_id = ? and km.mastery >= 0.7 "
                        + "order by km.mastery desc limit ?",
                (rs, rowNum) -> new StudentProfileView.KcView(
                        rs.getString("kc_name"),
                        round(rs.getDouble("mastery"))
                ),
                userId, TOP_STRONG_KCS
        );
    }

    private List<StudentProfileView.ErrorPatternView> loadTopErrors(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select error_taxonomy, count(*) as cnt, max(update_time) as last_seen "
                        + "from ai_learner_notebook "
                        + "where user_id = ? and is_deleted = false "
                        + "and update_time > now() - interval '30 day' "
                        + "and error_taxonomy is not null "
                        + "group by error_taxonomy order by cnt desc limit ?",
                userId, TOP_ERRORS
        );
        List<StudentProfileView.ErrorPatternView> views = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String raw = String.valueOf(row.getOrDefault("error_taxonomy", ""));
            String normalized = ErrorTaxonomy.normalize(raw);
            if (ErrorTaxonomy.UNKNOWN.equals(normalized)) {
                continue;
            }
            String label = ErrorTaxonomy.label(normalized);
            int count = ((Number) row.getOrDefault("cnt", 0)).intValue();
            Object lastSeenRaw = row.get("last_seen");
            String lastSeen = lastSeenRaw instanceof Timestamp ts ? ts.toInstant().toString() : "";
            views.add(new StudentProfileView.ErrorPatternView(label, count, lastSeen));
        }
        return views;
    }

    private Map<String, Object> loadStats30d(Long userId,
                                              LearnerNarrativeSummaryService.NarrativeSummary narrative) {
        Map<String, Object> stats = new LinkedHashMap<>();
        Object existing = narrative.payload().get("stats_30d");
        if (existing instanceof Map<?, ?> existingStats) {
            for (Map.Entry<?, ?> entry : existingStats.entrySet()) {
                stats.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return stats;
        }
        Long attempted = jdbcTemplate.queryForObject(
                "select count(distinct problem_id) from submission "
                        + "where user_id = ? and create_time > now() - interval '30 day'",
                Long.class, userId
        );
        Long ac = jdbcTemplate.queryForObject(
                "select count(distinct problem_id) from submission "
                        + "where user_id = ? and result = 0 and create_time > now() - interval '30 day'",
                Long.class, userId
        );
        long attemptedSafe = attempted == null ? 0L : attempted;
        long acSafe = ac == null ? 0L : ac;
        stats.put("problems_attempted_30d", attemptedSafe);
        stats.put("problems_ac_30d", acSafe);
        stats.put("ac_rate_30d", attemptedSafe == 0 ? 0.0 : round((double) acSafe / attemptedSafe));
        if (objectMapper == null) {
            stats.put("debug", "objectMapper unused");
        }
        return stats;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}

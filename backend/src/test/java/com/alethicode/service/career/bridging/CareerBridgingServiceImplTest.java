package com.alethicode.service.career.bridging;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.profile.LearnerProfileProjector;
import com.alethicode.service.aitutor.profile.LearnerState;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService.AbTestAssignment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CareerBridgingServiceImpl} 单测 —— 覆盖 plan 3 节核心契约。
 *
 * <p>覆盖场景：(1) ensureProfile 首次填触发新 milestone；(2) ensureProfile 重复填
 * reuse 已有 milestone；(3) major 不在字典 → 抛 422；(4) user_profile 行不存在 → 抛 404；
 * (5) recordMilestone 未知 type → 抛 422；(6) generateForMilestone control 组消费
 * 但不调 LLM；(7) generateForMilestone treatment 组完整链路（A/B + LLM + Reflection +
 * persist + consume）。
 *
 * <p>所有 JdbcTemplate 调用通过 SQL 关键字 ArgumentMatcher 分发到 stub 返回；
 * RolloutPolicyService 用真实实例（其内部 stableHash 是稳定的，便于挑选 control / treatment
 * 落点 user id）。
 */
@ExtendWith(MockitoExtension.class)
class CareerBridgingServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AiModelGateway aiModelGateway;
    @Mock
    private ReflectionService reflectionService;
    @Mock
    private LearnerProfileProjector learnerProfileProjector;
    @Mock
    private RolloutPolicyService rolloutPolicyService;
    @Mock
    private com.alethicode.service.career.preference.CareerPreferenceService preferenceService;

    private ObjectMapper objectMapper;
    private AlethicodeProperties properties;
    private CareerBridgingServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new AlethicodeProperties();
        properties.getCareer().getBridging().setEnabled(true);
        properties.getCareer().getBridging().setTreatmentRate(0.5);
        // 默认未关闭模块；个别测试可覆盖（todo 15）
        org.mockito.Mockito.lenient().when(
                preferenceService.isModuleDisabled(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(false);
        service = new CareerBridgingServiceImpl(
                jdbcTemplate, objectMapper, properties,
                aiModelGateway, reflectionService, rolloutPolicyService, learnerProfileProjector,
                preferenceService);
    }

    // ---------- ensureProfile ----------

    @Test
    void ensureProfileFirstTimeInsertsEnrollmentMilestone() {
        long userId = 100L;
        String major = "biology";
        stubMajorExists(major, true);
        stubUserProfileUpdate(major, "I want bioinfo", userId, 1);
        stubFindMilestoneIdEmpty(userId, "enrollment", major);
        stubInsertMilestoneReturning(555L);

        var result = service.ensureProfile(userId, major, "I want bioinfo");
        assertThat(result.newlyEnrolled()).isTrue();
        assertThat(result.milestoneId()).isEqualTo(555L);
        assertThat(result.majorCode()).isEqualTo("biology");
    }

    @Test
    void ensureProfileSecondTimeReusesExistingMilestone() {
        long userId = 101L;
        String major = "chemistry";
        stubMajorExists(major, true);
        stubUserProfileUpdate(major, null, userId, 1);
        stubFindMilestoneIdReturning(userId, "enrollment", major, 999L);

        var result = service.ensureProfile(userId, major, "  ");
        assertThat(result.newlyEnrolled()).isFalse();
        assertThat(result.milestoneId()).isEqualTo(999L);
        assertThat(result.majorCode()).isEqualTo("chemistry");

        verify(jdbcTemplate, never())
                .update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void ensureProfileRejectsUnknownMajorWith422() {
        stubMajorExists("unknown", false);
        assertThatThrownBy(() -> service.ensureProfile(1L, "unknown", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void ensureProfileRejectsMissingUserProfileWith404() {
        long userId = 102L;
        stubMajorExists("biology", true);
        stubUserProfileUpdate("biology", null, userId, 0);   // 0 行 = 不存在

        assertThatThrownBy(() -> service.ensureProfile(userId, "biology", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------- recordMilestone ----------

    @Test
    void recordMilestoneRejectsNullTypeWith422() {
        assertThatThrownBy(() -> service.recordMilestone(1L, (MilestoneType) null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void recordMilestoneReturnsExistingIdWithoutInsertingDuplicate() {
        long userId = 200L;
        stubFindMilestoneIdReturning(userId, "kc_cluster_graduated", "loop", 777L);
        long id = service.recordMilestone(userId, MilestoneType.KC_CLUSTER_GRADUATED, "loop");
        assertThat(id).isEqualTo(777L);
        verify(jdbcTemplate, never())
                .update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    // ---------- generateForMilestone ----------

    @Test
    void generateForMilestoneControlGroupConsumesWithoutCallingLlm() {
        long userId = 100L;
        long milestoneId = 1L;

        stubLoadMilestone(userId, milestoneId, "enrollment", null);
        stubLoadUserMajor(userId, "biology");
        stubAbTestAssignment(userId, "control");

        Optional<CareerBridgingReport> report = service.generateForMilestone(userId, milestoneId);

        assertThat(report).isEmpty();
        verify(jdbcTemplate)
                .update(argThat(sqlContains("update career_bridging_milestone")), eq(milestoneId));
        verify(aiModelGateway, never()).callForJson(anyString(), anyString(), anyString());
        verify(reflectionService, never()).reflectAndRefine(any(), any(), any(), anyInt());
    }

    @Test
    void generateForMilestoneTreatmentGroupCallsLlmAndPersistsReport() {
        long userId = 200L;
        long milestoneId = 2L;

        stubLoadMilestone(userId, milestoneId, "enrollment", "biology");
        stubLoadUserMajor(userId, "biology");
        stubLoadMajorDictionaryRow("biology");
        stubLoadRecentPackTitles(userId, List.of("Python 入门"));
        stubAbTestAssignment(userId, "treatment");
        when(learnerProfileProjector.project(eq(userId), eq(null), any(), eq(null)))
                .thenReturn(emptyLearnerState());

        Map<String, Object> initial = Map.of(
                "title", "你专业的 Python 起点",
                "intro_md", "intro",
                "use_cases", List.of(Map.of("name", "DNA GC", "why_for_major", "频次")),
                "next_step_md", "继续学列表",
                "citations", List.of(Map.of("source", "major_dictionary", "ref", "biology[0]")));
        when(aiModelGateway.callForJson(anyString(), anyString(), eq("career-bridging")))
                .thenReturn(initial);
        when(reflectionService.reflectAndRefine(eq(CardType.CAREER_BRIDGING), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(initial, true, 1, "ok"));

        stubInsertReportReturning(userId, milestoneId, 321L);
        stubLoadReportById(321L, userId, milestoneId);

        Optional<CareerBridgingReport> report = service.generateForMilestone(userId, milestoneId);

        assertThat(report).isPresent();
        assertThat(report.get().id()).isEqualTo(321L);
        assertThat(report.get().reflectionPassed()).isTrue();
        assertThat(report.get().rolloutMode()).isEqualTo("treatment");
        verify(aiModelGateway, times(1)).callForJson(anyString(), anyString(), eq("career-bridging"));
        verify(reflectionService, times(1)).reflectAndRefine(eq(CardType.CAREER_BRIDGING), any(), any(), eq(1));
        verify(jdbcTemplate)
                .update(argThat(sqlContains("update career_bridging_milestone")), eq(milestoneId));
    }

    // ---------- helpers ----------

    private void stubAbTestAssignment(long userId, String group) {
        lenient().when(rolloutPolicyService.assignAbTest(eq("career_bridging_v1"), eq(userId), eq(0.5)))
                .thenReturn(new AbTestAssignment("career_bridging_v1", userId, group, 0.0));
    }

    private void stubMajorExists(String code, boolean exists) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from career_major_dictionary")),
                        eq(Boolean.class), eq(code)))
                .thenReturn(exists);
    }

    private void stubUserProfileUpdate(String major, String intent, long userId, int rows) {
        String trimmedIntent = (intent == null || intent.isBlank()) ? null : intent.trim();
        lenient().when(jdbcTemplate.update(
                        argThat(sqlContains("update user_profile")),
                        eq(major), eq(trimmedIntent), eq(userId)))
                .thenReturn(rows);
    }

    private void stubFindMilestoneIdEmpty(long userId, String type, String ref) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("select id from career_bridging_milestone")),
                        eq(Long.class), eq(userId), eq(type), eq(ref)))
                .thenThrow(new EmptyResultDataAccessException(1));
    }

    private void stubFindMilestoneIdReturning(long userId, String type, String ref, long id) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("select id from career_bridging_milestone")),
                        eq(Long.class), eq(userId), eq(type), eq(ref)))
                .thenReturn(id);
    }

    private void stubInsertMilestoneReturning(long generatedId) {
        lenient().when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder kh = invocation.getArgument(1);
                    Map<String, Object> keys = new LinkedHashMap<>();
                    keys.put("id", generatedId);
                    kh.getKeyList().clear();
                    kh.getKeyList().add(keys);
                    return 1;
                });
    }

    private void stubLoadMilestone(long userId, long milestoneId, String type, String ref) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from career_bridging_milestone")
                                .and(sqlContains("where id = ?"))),
                        any(RowMapper.class), eq(milestoneId), eq(userId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    Map<String, Object> cols = new LinkedHashMap<>();
                    cols.put("id", milestoneId);
                    cols.put("user_id", userId);
                    cols.put("milestone_type", type);
                    cols.put("milestone_ref", ref);
                    cols.put("triggered_at", new java.sql.Timestamp(System.currentTimeMillis()));
                    cols.put("consumed_at", null);
                    java.sql.ResultSet rs = mockResultSet(cols);
                    return mapper.mapRow(rs, 1);
                });
    }

    private void stubLoadUserMajor(long userId, String major) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("select major_code from user_profile")),
                        eq(String.class), eq(userId)))
                .thenReturn(major);
    }

    private void stubLoadMajorDictionaryRow(String major) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from career_major_dictionary")
                                .and(sqlContains("seed_keywords"))),
                        any(RowMapper.class), eq(major)))
                .thenAnswer(invocation -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", major);
                    row.put("name_zh", "生物科学");
                    row.put("name_en", "Biology");
                    row.put("discipline", "natural-science");
                    row.put("seed_keywords", List.of("DNA"));
                    row.put("seed_use_cases", List.of(Map.of("name", "DNA GC")));
                    row.put("seed_kcs", List.of());
                    return row;
                });
    }

    private void stubLoadRecentPackTitles(long userId, List<String> titles) {
        lenient().when(jdbcTemplate.queryForList(
                        argThat(sqlContains("language_pack")),
                        eq(String.class), eq(userId)))
                .thenReturn(titles);
    }

    private void stubInsertReportReturning(long userId, long milestoneId, long generatedId) {
        lenient().when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder kh = invocation.getArgument(1);
                    Map<String, Object> keys = new LinkedHashMap<>();
                    keys.put("id", generatedId);
                    kh.getKeyList().clear();
                    kh.getKeyList().add(keys);
                    return 1;
                });
    }

    private void stubLoadReportById(long reportId, long userId, long milestoneId) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from career_bridging_report")
                                .and(sqlContains("where id = ?"))),
                        any(RowMapper.class), eq(reportId)))
                .thenAnswer(invocation -> new CareerBridgingReport(
                        reportId, userId, milestoneId, "biology", "milestone",
                        "你专业的 Python 起点",
                        "intro\n\n## 典型应用场景\n\n1. **DNA GC** — 频次\n",
                        List.of(Map.of("source", "major_dictionary", "ref", "biology[0]")),
                        "treatment", true, "trace", java.time.Instant.now()));
    }

    private LearnerState emptyLearnerState() {
        return new LearnerState(
                false,
                Map.of(),
                List.of(),
                Map.of(),
                Map.of(),
                "low",
                "medium",
                Map.of(),
                List.of(),
                "新生",
                false);
    }

    private static SqlMatcher sqlContains(String fragment) {
        return new SqlMatcher(fragment);
    }

    /**
     * Lightweight ResultSet mock used only for {@link #stubLoadMilestone} —— 仅支持 getString /
     * getLong / getTimestamp / getObject 几个本测试涉及的字段；遇到未覆盖的方法直接抛
     * UnsupportedOperationException 让测试失败给出明确信号。
     */
    @SuppressWarnings("unchecked")
    private java.sql.ResultSet mockResultSet(Map<String, Object> values) {
        java.sql.ResultSet mock = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        try {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String column = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Long l) {
                    lenient().when(mock.getLong(column)).thenReturn(l);
                } else if (value instanceof Number n) {
                    lenient().when(mock.getLong(column)).thenReturn(n.longValue());
                }
                if (value instanceof String s) {
                    lenient().when(mock.getString(column)).thenReturn(s);
                } else if (value == null) {
                    lenient().when(mock.getString(column)).thenReturn(null);
                }
                if (value instanceof java.sql.Timestamp ts) {
                    lenient().when(mock.getTimestamp(column)).thenReturn(ts);
                } else if (value == null) {
                    lenient().when(mock.getTimestamp(column)).thenReturn(null);
                }
                lenient().when(mock.getObject(column)).thenReturn(value);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        return mock;
    }

    private static class SqlMatcher implements org.mockito.ArgumentMatcher<String> {
        private final String fragment;

        SqlMatcher(String fragment) {
            this.fragment = fragment;
        }

        @Override
        public boolean matches(String argument) {
            return argument != null && argument.toLowerCase().contains(fragment.toLowerCase());
        }

        SqlMatcher and(SqlMatcher other) {
            String thisFrag = this.fragment;
            return new SqlMatcher(thisFrag) {
                @Override
                public boolean matches(String argument) {
                    return argument != null
                            && argument.toLowerCase().contains(thisFrag.toLowerCase())
                            && argument.toLowerCase().contains(other.fragment.toLowerCase());
                }
            };
        }

        @Override
        public String toString() {
            return "sqlContains(" + fragment + ")";
        }
    }
}

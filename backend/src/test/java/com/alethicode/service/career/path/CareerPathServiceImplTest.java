package com.alethicode.service.career.path;

import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.rollout.RolloutDecision;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import com.alethicode.service.career.bridging.CareerBridgingService;
import com.alethicode.service.career.bridging.MilestoneType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CareerPathServiceImpl} 单测——覆盖 plan 6.1 节核心契约：
 * 拓扑 + mastery 三态判断 / 不存在专业抛 404 / markNodeUnlocked 严校验。
 */
@ExtendWith(MockitoExtension.class)
class CareerPathServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private MasteryService masteryService;
    @Mock
    private CareerBridgingService careerBridgingService;
    @Mock
    private RolloutPolicyService rolloutPolicyService;

    private ObjectMapper objectMapper;
    private CareerPathServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 默认 baseline 决策；个别测试可覆盖为 rollback
        lenient().when(rolloutPolicyService.evaluate(eq("career_path"), anyString(), any()))
                .thenReturn(new RolloutDecision("baseline", "default", java.util.Map.of()));
        service = new CareerPathServiceImpl(
                jdbcTemplate, objectMapper, masteryService, careerBridgingService, rolloutPolicyService);
    }

    @Test
    void buildViewReturnsEmptyNodesWhenNoPathConfigured() {
        String major = "biology";
        stubMajorName(major, "生物");
        stubLoadPathNodesEmpty(major);

        CareerPathView view = service.buildView(1L, major);

        assertThat(view.majorCode()).isEqualTo(major);
        assertThat(view.majorNameZh()).isEqualTo("生物");
        assertThat(view.nodes()).isEmpty();
        verify(masteryService, never()).projectMasteryByLanguagePack(any(), any());
    }

    @Test
    void buildViewReturnsNotFoundWhenMajorMissing() {
        when(jdbcTemplate.queryForObject(
                argThat(sqlContains("from career_major_dictionary")),
                eq(String.class), eq("ghost")))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> service.buildView(1L, "ghost"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void buildViewReturnsEmptyNodesWhenRolloutRollback() {
        String major = "biology";
        stubMajorName(major, "生物");
        when(rolloutPolicyService.evaluate(eq("career_path"), eq("user:7"), any()))
                .thenReturn(new RolloutDecision("rollback", "policy off", java.util.Map.of()));

        CareerPathView view = service.buildView(7L, major);

        assertThat(view.majorCode()).isEqualTo(major);
        assertThat(view.nodes()).isEmpty();
        verify(jdbcTemplate, never()).query(argThat(sqlContains("from career_path_node")),
                any(RowMapper.class), any());
    }

    @Test
    void buildViewClassifiesNodesByMasteryThreshold() {
        String major = "biology";
        stubMajorName(major, "生物");
        stubLoadPathNodes(major, List.of(
                row("variables", null, "why1", "[\"v1\"]", 1),
                row("data_types", "variables", "why2", "[\"v2\"]", 2),
                row("collections", "data_types", "why3", "[\"v3\"]", 3),
                row("control_flow", "collections", "why4", "[]", 4),
                row("functions", "control_flow", "why5", "[]", 5)
        ));

        Map<String, Double> mastery = new HashMap<>();
        mastery.put("variables", 0.85);     // 根节点 + self >= 0.5 = unlocked
        mastery.put("data_types", 0.75);    // parent=0.85>=0.7 + self>=0.5 = unlocked
        mastery.put("collections", 0.4);    // parent=0.75>=0.7 + 0.3<=self<0.5 = in_progress
        mastery.put("control_flow", null);  // parent collections=0.4<0.7 = locked
        mastery.put("functions", null);     // 链上 parent 已 locked = locked
        when(masteryService.projectMasteryByLanguagePack(eq(7L), eq(null)))
                .thenReturn(mastery);

        CareerPathView view = service.buildView(7L, major);

        assertThat(view.nodes()).hasSize(5);
        Map<String, String> statusByKc = new HashMap<>();
        for (CareerPathNodeView n : view.nodes()) {
            statusByKc.put(n.kcCode(), n.status());
        }
        assertThat(statusByKc.get("variables")).isEqualTo("unlocked");
        assertThat(statusByKc.get("data_types")).isEqualTo("unlocked");
        assertThat(statusByKc.get("collections")).isEqualTo("in_progress");
        assertThat(statusByKc.get("control_flow")).isEqualTo("locked");
        assertThat(statusByKc.get("functions")).isEqualTo("locked");
    }

    @Test
    void markNodeUnlockedRejectsBlankInputsWith422() {
        assertThatThrownBy(() -> service.markNodeUnlocked(1L, "", "kc"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThatThrownBy(() -> service.markNodeUnlocked(1L, "biology", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void markNodeUnlockedRejectsUnknownPathNodeWith404() {
        when(jdbcTemplate.queryForObject(
                argThat(sqlContains("select exists(select 1 from career_path_node")),
                eq(Boolean.class), eq("biology"), eq("ghost-kc")))
                .thenReturn(false);

        assertThatThrownBy(() -> service.markNodeUnlocked(1L, "biology", "ghost-kc"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void markNodeUnlockedRecordsMilestoneOnValidPathNode() {
        when(jdbcTemplate.queryForObject(
                argThat(sqlContains("select exists(select 1 from career_path_node")),
                eq(Boolean.class), eq("biology"), eq("variables")))
                .thenReturn(true);

        service.markNodeUnlocked(7L, "biology", "variables");

        verify(careerBridgingService, times(1)).recordMilestone(
                eq(7L), eq(MilestoneType.PATH_NODE_UNLOCKED), eq("biology:variables"));
    }

    // ---------- helpers ----------

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    private void stubMajorName(String code, String nameZh) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from career_major_dictionary")),
                        eq(String.class), eq(code)))
                .thenReturn(nameZh);
    }

    private void stubLoadPathNodesEmpty(String major) {
        lenient().when(jdbcTemplate.query(
                        argThat(sqlContains("from career_path_node")),
                        any(RowMapper.class), eq(major)))
                .thenReturn(List.of());
    }

    private void stubLoadPathNodes(String major, List<Map<String, Object>> rows) {
        lenient().when(jdbcTemplate.query(
                        argThat(sqlContains("from career_path_node")),
                        any(RowMapper.class), eq(major)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    java.util.List<Object> mapped = new java.util.ArrayList<>();
                    int idx = 0;
                    for (Map<String, Object> row : rows) {
                        mapped.add(mapper.mapRow(toResultSet(row), idx++));
                    }
                    return mapped;
                });
    }

    private static Map<String, Object> row(String kc, String parent, String why, String useCases, int sortOrder) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kc_code", kc);
        r.put("parent_kc_code", parent);
        r.put("why_md", why);
        r.put("typical_use_cases_json", useCases);
        r.put("sort_order", sortOrder);
        return r;
    }

    @SuppressWarnings("unchecked")
    private java.sql.ResultSet toResultSet(Map<String, Object> values) {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        try {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String column = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Integer i) {
                    lenient().when(rs.getInt(column)).thenReturn(i);
                }
                lenient().when(rs.getString(column))
                        .thenReturn(value == null ? null : value.toString());
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        return rs;
    }

    private static SqlMatcher sqlContains(String fragment) {
        return new SqlMatcher(fragment);
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

        @Override
        public String toString() {
            return "sqlContains(" + fragment + ")";
        }
    }
}

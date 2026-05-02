package com.alethicode.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "logging.level.root=WARN",
        "logging.level.org.springframework.jdbc.core.JdbcTemplate=ERROR",
        "logging.level.org.springframework.web=ERROR",
        "logging.level.org.springframework.security=ERROR"
})
class ClassroomMonitorScaleIntegrationTest extends AbstractJdbcIntegrationTest {

    private static final String CLASSROOM_ID = "cls_scale_200";
    private static final String ROOT_USERNAME = "root";
    private static final int STUDENT_COUNT = 200;
    private static final int PROBLEM_COUNT = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private JudgeServerService judgeServerService;
    @MockBean private SystemAdminService systemAdminService;
    @MockBean private ReleaseNotesService releaseNotesService;
    @MockBean private PlatformConfigService platformConfigService;
    @MockBean private SystemOptionService systemOptionService;
    @MockBean private AdminUploadService adminUploadService;

    @BeforeEach
    void setUp() {
        seedMonitorDataset();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void monitorEndpointsShouldKeepContractFor200Students() throws Exception {
        mockMvc.perform(get("/api/classroom/" + CLASSROOM_ID + "/monitor/stats")
                        .with(SecurityMockMvcRequestPostProcessors.user(ROOT_USERNAME).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total_members").value(STUDENT_COUNT))
                .andExpect(jsonPath("$.data.online_count").isNumber())
                .andExpect(jsonPath("$.data.coding_count").isNumber())
                .andExpect(jsonPath("$.data.active_coding").isNumber())
                .andExpect(jsonPath("$.data.avg_progress").isNumber());

        mockMvc.perform(get("/api/classroom/" + CLASSROOM_ID + "/monitor/snapshots")
                        .with(SecurityMockMvcRequestPostProcessors.user(ROOT_USERNAME).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(STUDENT_COUNT))
                .andExpect(jsonPath("$.data.results[0].user_id").isNumber())
                .andExpect(jsonPath("$.data.results[0].activity_status").isString())
                .andExpect(jsonPath("$.data.results[0].code_length").isNumber())
                .andExpect(jsonPath("$.data.results[0].submission_count").isNumber())
                .andExpect(jsonPath("$.data.results[0].ac_count").isNumber())
                .andExpect(jsonPath("$.data.results[0].progress").isNumber());

        mockMvc.perform(get("/api/classroom/" + CLASSROOM_ID + "/monitor/error-clusters")
                        .with(SecurityMockMvcRequestPostProcessors.user(ROOT_USERNAME).roles("ADMIN"))
                        .param("time_window", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.clusters").isArray());
    }

    @Test
    void monitorEndpointsPerformanceShouldMeetGateFor200Students() throws Exception {
        EndpointMetric stats = benchmarkEndpoint("/api/classroom/" + CLASSROOM_ID + "/monitor/stats", 12, 80);
        EndpointMetric errorClusters = benchmarkEndpoint("/api/classroom/" + CLASSROOM_ID + "/monitor/error-clusters?time_window=60", 12, 80);
        EndpointMetric snapshots = benchmarkEndpoint("/api/classroom/" + CLASSROOM_ID + "/monitor/snapshots", 12, 80);

        assertThat(stats.errorRate()).isZero();
        assertThat(errorClusters.errorRate()).isZero();
        assertThat(snapshots.errorRate()).isZero();

        assertThat(stats.p95Millis()).isLessThan(250L);
        assertThat(errorClusters.p95Millis()).isLessThan(250L);
        assertThat(snapshots.p95Millis()).isLessThan(450L);

        writeBaseline(stats, errorClusters, snapshots);
    }

    private EndpointMetric benchmarkEndpoint(String path, int warmupIterations, int measuredIterations) throws Exception {
        List<Long> latencies = new ArrayList<>();
        int errors = 0;
        int total = warmupIterations + measuredIterations;
        for (int i = 0; i < total; i++) {
            long startNs = System.nanoTime();
            MvcResult result = mockMvc.perform(get(path)
                            .with(SecurityMockMvcRequestPostProcessors.user(ROOT_USERNAME).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn();
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
            boolean ok = payload.has("error") && payload.get("error").isNull();
            if (i >= warmupIterations) {
                latencies.add(elapsedMs);
                if (!ok) {
                    errors++;
                }
            }
        }
        long p50 = percentileMillis(latencies, 0.50d);
        long p95 = percentileMillis(latencies, 0.95d);
        double errorRate = latencies.isEmpty() ? 1.0d : ((double) errors / (double) latencies.size());
        return new EndpointMetric(p50, p95, errorRate);
    }

    private long percentileMillis(List<Long> values, double quantile) {
        if (values.isEmpty()) {
            return Long.MAX_VALUE;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(quantile * sorted.size()) - 1;
        int bounded = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(bounded);
    }

    private void writeBaseline(EndpointMetric stats, EndpointMetric errorClusters, EndpointMetric snapshots) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", Instant.now().toString());
        report.put("classroom_id", CLASSROOM_ID);
        report.put("students", STUDENT_COUNT);
        report.put("threshold_ms", Map.of(
                "monitor_stats_p95", 250,
                "monitor_error_clusters_p95", 250,
                "monitor_snapshots_p95", 450
        ));
        report.put("results", Map.of(
                "monitor_stats", metricMap(stats),
                "monitor_error_clusters", metricMap(errorClusters),
                "monitor_snapshots", metricMap(snapshots)
        ));

        Path output = Path.of("target", "classroom-monitor-200-baseline.json");
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    private Map<String, Object> metricMap(EndpointMetric metric) {
        return Map.of(
                "p50_ms", metric.p50Millis(),
                "p95_ms", metric.p95Millis(),
                "error_rate", metric.errorRate()
        );
    }

    private void seedMonitorDataset() {
        insertUser(ROOT_USERNAME, "Admin");
        Long rootUserId = jdbcTemplate.queryForObject("select id from \"user\" where username = ?", Long.class, ROOT_USERNAME);
        assertThat(rootUserId).isNotNull();

        jdbcTemplate.update(
                """
                insert into classroom(id, name, created_by_id, create_time, update_time)
                values (?, 'Scale Class', ?, now(), now())
                """,
                CLASSROOM_ID,
                rootUserId
        );
        jdbcTemplate.update(
                """
                insert into classroom_member(id, classroom_id, user_id, role, join_time, update_time)
                values ('cm-root', ?, ?, 'owner', now(), now())
                """,
                CLASSROOM_ID,
                rootUserId
        );

        for (int i = 1; i <= PROBLEM_COUNT; i++) {
            long problemId = 3000L + i;
            jdbcTemplate.update(
                    """
                    insert into problem(id, _id, title, description, visible, is_public, difficulty,
                                        statistic_info, source, submission_number, accepted_number,
                                        created_by_id, create_time, last_update_time)
                    values (?, ?, ?, 'desc', true, true, 'Low', cast('{}' as jsonb), 'scale-test', 0, 0, ?, now(), now())
                    """,
                    problemId,
                    "P" + problemId,
                    "Scale Problem " + i,
                    rootUserId
            );
            jdbcTemplate.update(
                    """
                    insert into classroom_problem(id, classroom_id, problem_id, display_order, is_visible, update_time)
                    values (?, ?, ?, ?, true, now())
                    """,
                    "cp-" + i,
                    CLASSROOM_ID,
                    problemId,
                    i
            );
        }

        for (int i = 1; i <= STUDENT_COUNT; i++) {
            String username = String.format("stu_%03d", i);
            insertUser(username, "Regular User");
            Long userId = jdbcTemplate.queryForObject("select id from \"user\" where username = ?", Long.class, username);
            assertThat(userId).isNotNull();

            jdbcTemplate.update(
                    """
                    insert into classroom_member(id, classroom_id, user_id, role, join_time, update_time)
                    values (?, ?, ?, 'student', now(), now())
                    """,
                    "cm-" + i,
                    CLASSROOM_ID,
                    userId
            );

            for (int s = 1; s <= 3; s++) {
                long problemId = 3000L + ((i + s - 1) % PROBLEM_COUNT + 1);
                int result = s == 1 ? 0 : 1;
                jdbcTemplate.update(
                        """
                        insert into submission(id, problem_id, user_id, username, result, language, code, statistic_info, create_time)
                        values (?, ?, ?, ?, ?, 'Python3', ?, cast('{}' as jsonb), now())
                        """,
                        "sub-" + i + "-" + s,
                        problemId,
                        userId,
                        username,
                        result,
                        "print(" + i + ")"
                );
            }

            String activityStatus;
            String errorTaxonomy;
            switch (i % 6) {
                case 0 -> {
                    activityStatus = "typing";
                    errorTaxonomy = null;
                }
                case 1 -> {
                    activityStatus = "running";
                    errorTaxonomy = null;
                }
                case 2 -> {
                    activityStatus = "abnormal";
                    errorTaxonomy = "syntax_error";
                }
                case 3 -> {
                    activityStatus = "abnormal";
                    errorTaxonomy = "runtime_error";
                }
                case 4 -> {
                    activityStatus = "idle";
                    errorTaxonomy = null;
                }
                default -> {
                    activityStatus = "submitted";
                    errorTaxonomy = null;
                }
            }
            int staleSeconds = i % 10 == 0 ? 50 : 8;
            jdbcTemplate.update(
                    """
                    insert into student_monitoring_snapshot(id, classroom_id, user_id, classroom_problem_id, activity_status, error_taxonomy,
                                                            code_snapshot, code_hash, edit_distance, submission_count,
                                                            ac_count, elapsed_time_seconds, snapshot_time)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    "snap-" + i,
                    CLASSROOM_ID,
                    userId,
                    "cp-" + ((i % PROBLEM_COUNT) + 1),
                    activityStatus,
                    errorTaxonomy,
                    "print(" + i + ")",
                    "hash-" + i,
                    3,
                    3,
                    1,
                    300 + i,
                    Timestamp.from(Instant.now().minusSeconds(staleSeconds))
            );
        }
    }

    private void insertUser(String username, String adminType) {
        jdbcTemplate.update(
                "insert into \"user\"(username, admin_type, create_time) values (?, ?, now())",
                username,
                adminType
        );
    }

    private record EndpointMetric(long p50Millis, long p95Millis, double errorRate) {
    }
}

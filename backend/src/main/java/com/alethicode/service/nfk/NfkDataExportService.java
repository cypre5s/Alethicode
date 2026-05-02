package com.alethicode.service.nfk;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 为 NFK 离线训练输出训练集 + 计算课程包的"数据就绪度"。
 *
 * <p>字段约定与 {@code research/nfk/} 侧训练代码严格对齐：
 * <pre>
 *   user_id, question_id, skill_id, response, timestamp
 * </pre>
 * 其中：
 * <ul>
 *   <li>{@code user_id} / {@code question_id}：均为平台原生数字 ID，直接导出整数；</li>
 *   <li>{@code skill_id}：同一 {@code problem_id} 的多 KC 按 {@code weight DESC} 取最大作为主 KC
 *       （与 {@code docs/reports/nfk-data-quality-report.md} 对 A4 的假设一致）；</li>
 *   <li>{@code response}：{@code submission.result = 0} → 1，否则 0；</li>
 *   <li>{@code timestamp}：{@code submission.create_time} 的 ISO-8601 字符串。</li>
 * </ul>
 *
 * <p>{@code readiness_level} 阈值（故意保守，宁可 WARM 不过度乐观）：
 * <ul>
 *   <li>HOT：students ≥ 30, problems ≥ 30, kc_coverage ≥ 0.7, interactions ≥ 800；</li>
 *   <li>WARM：students ≥ 10, problems ≥ 10, kc_coverage ≥ 0.4, interactions ≥ 200；</li>
 *   <li>其它 → COLD，不建议训 NFK。</li>
 * </ul>
 */
@Service
public class NfkDataExportService {

    /** CSV 表头，必须与 research/nfk/ 侧训练 dataloader 一致。 */
    public static final String CSV_HEADER = "user_id,question_id,skill_id,response,timestamp";

    private static final int HOT_MIN_STUDENTS = 30;
    private static final int HOT_MIN_PROBLEMS = 30;
    private static final double HOT_MIN_COVERAGE = 0.7;
    private static final int HOT_MIN_INTERACTIONS = 800;

    private static final int WARM_MIN_STUDENTS = 10;
    private static final int WARM_MIN_PROBLEMS = 10;
    private static final double WARM_MIN_COVERAGE = 0.4;
    private static final int WARM_MIN_INTERACTIONS = 200;

    private final JdbcTemplate jdbcTemplate;
    private final NfkTrainingRowValidator rowValidator;

    public NfkDataExportService(JdbcTemplate jdbcTemplate, NfkTrainingRowValidator rowValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowValidator = rowValidator;
    }

    /**
     * 流式输出指定 language pack 的 NFK 训练 CSV。
     *
     * <p>返回 {@link StreamingResponseBody}，上层 controller 负责设置
     * {@code Content-Type / Content-Disposition}。
     */
    public StreamingResponseBody exportTrainingData(Long languagePackId) {
        if (languagePackId == null || languagePackId <= 0) {
            throw new IllegalArgumentException("languagePackId is required");
        }
        return os -> writeTrainingDataCsv(languagePackId, os);
    }

    /**
     * 直接写 CSV 到输出流；单独抽出便于测试直接传 {@link java.io.ByteArrayOutputStream}。
     */
    public void writeTrainingDataCsv(Long languagePackId, OutputStream outputStream) {
        if (languagePackId == null || languagePackId <= 0) {
            throw new IllegalArgumentException("languagePackId is required");
        }
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            writer.write('\n');

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                WITH primary_kc AS (
                    SELECT DISTINCT ON (m.problem_id)
                           m.problem_id,
                           m.kc_id
                    FROM ai_problem_kc_mapping m
                    WHERE m.language_pack_id = ?
                      AND m.weight > 0
                      AND m.kc_id IS NOT NULL
                    ORDER BY m.problem_id, m.weight DESC, m.kc_id ASC
                )
                SELECT s.user_id      AS user_id,
                       s.problem_id   AS question_id,
                       pk.kc_id       AS skill_id,
                       CASE WHEN s.result = 0 THEN 1 ELSE 0 END AS response,
                       s.create_time  AS ts
                FROM submission s
                JOIN language_pack_problem_mapping lpm
                  ON lpm.problem_id = s.problem_id
                 AND lpm.language_pack_id = ?
                JOIN primary_kc pk
                  ON pk.problem_id = s.problem_id
                WHERE s.user_id > 0
                ORDER BY s.user_id ASC, s.create_time ASC, s.id ASC
                """,
                    languagePackId, languagePackId);

            long rowNumber = 0;
            for (Map<String, Object> row : rows) {
                rowNumber++;
                Map<String, Object> canonical = canonicalize(rowNumber, row);
                rowValidator.validateRow(rowNumber, canonical);
                writer.write(String.valueOf(canonical.get("user_id")));
                writer.write(',');
                writer.write(String.valueOf(canonical.get("question_id")));
                writer.write(',');
                writer.write(String.valueOf(canonical.get("skill_id")));
                writer.write(',');
                writer.write(String.valueOf(canonical.get("response")));
                writer.write(',');
                writer.write(String.valueOf(canonical.get("timestamp")));
                writer.write('\n');
            }

            writer.flush();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to write NFK training CSV for pack " + languagePackId, e);
        }
    }

    /**
     * 计算就绪度：学生数 / 题目数 / KC 数 / 交互数 / KC 覆盖率 + 等级。
     */
    public Map<String, Object> computeReadiness(Long languagePackId) {
        if (languagePackId == null || languagePackId <= 0) {
            throw new IllegalArgumentException("languagePackId is required");
        }

        Map<String, Object> counts = jdbcTemplate.queryForMap("""
            WITH pack_problems AS (
                SELECT problem_id
                FROM language_pack_problem_mapping
                WHERE language_pack_id = ?
            ),
            covered_problems AS (
                SELECT DISTINCT pp.problem_id
                FROM pack_problems pp
                JOIN ai_problem_kc_mapping m
                  ON m.problem_id = pp.problem_id
                 AND m.weight > 0
                 AND m.kc_id IS NOT NULL
            ),
            pack_kc AS (
                SELECT DISTINCT m.kc_id
                FROM ai_problem_kc_mapping m
                JOIN pack_problems pp ON pp.problem_id = m.problem_id
                WHERE m.weight > 0 AND m.kc_id IS NOT NULL
            ),
            pack_submissions AS (
                SELECT s.user_id, s.id
                FROM submission s
                JOIN pack_problems pp ON pp.problem_id = s.problem_id
                WHERE s.user_id > 0
            )
            SELECT
                (SELECT COUNT(*) FROM pack_problems)                     AS problem_count,
                (SELECT COUNT(*) FROM covered_problems)                  AS covered_count,
                (SELECT COUNT(*) FROM pack_kc)                            AS kc_count,
                (SELECT COUNT(DISTINCT user_id) FROM pack_submissions)    AS student_count,
                (SELECT COUNT(*) FROM pack_submissions)                   AS interaction_count
            """, languagePackId);

        long problems = asLong(counts.get("problem_count"));
        long covered = asLong(counts.get("covered_count"));
        long kcs = asLong(counts.get("kc_count"));
        long students = asLong(counts.get("student_count"));
        long interactions = asLong(counts.get("interaction_count"));
        double coverage = problems == 0 ? 0.0 : (double) covered / problems;
        String level = resolveLevel(students, covered, coverage, interactions);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("language_pack_id", languagePackId);
        result.put("student_count", students);
        result.put("problem_count", problems);
        result.put("covered_problem_count", covered);
        result.put("kc_count", kcs);
        result.put("interaction_count", interactions);
        result.put("kc_coverage", round4(coverage));
        result.put("readiness_level", level);
        result.put("next_action", describeNextAction(level));
        return result;
    }

    private String resolveLevel(long students, long coveredProblems, double coverage, long interactions) {
        if (students >= HOT_MIN_STUDENTS
                && coveredProblems >= HOT_MIN_PROBLEMS
                && coverage >= HOT_MIN_COVERAGE
                && interactions >= HOT_MIN_INTERACTIONS) {
            return "HOT";
        }
        if (students >= WARM_MIN_STUDENTS
                && coveredProblems >= WARM_MIN_PROBLEMS
                && coverage >= WARM_MIN_COVERAGE
                && interactions >= WARM_MIN_INTERACTIONS) {
            return "WARM";
        }
        return "COLD";
    }

    private String describeNextAction(String level) {
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "HOT" -> "满足 NFK 训练阈值，可导出 CSV 并在 AutoDL 上训练。";
            case "WARM" -> "数据量接近训练阈值，可尝试训练但关注过拟合；建议先跑一次 baseline。";
            default -> "数据量不足，建议继续收集真实做题数据或补全 KC 映射后再评估。";
        };
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /**
     * 把 JDBC 原生行映射规范化为契约定义的 5 字段 dict（timestamp 强制 ISO-8601 UTC）。
     *
     * <p>关键改动：原实现直接调用 {@code Object#toString()} 输出时间戳，对
     * {@link Timestamp} 会得到 {@code "yyyy-MM-dd HH:mm:ss.fff"} 形式，受 JVM 时区影响
     * 且不带时区后缀；本方法统一走 {@link Instant#toString()}，输出始终是 UTC 形式
     * （{@code "YYYY-MM-DDTHH:MM:SS[.fff]Z"}），与 {@code research/nfk/} 训练侧约定一致。
     */
    static Map<String, Object> canonicalize(long rowNumber, Map<String, Object> row) {
        if (row == null) {
            throw new NfkTrainingRowValidationException(rowNumber, "row payload is null");
        }
        Map<String, Object> canonical = new LinkedHashMap<>(5);
        canonical.put("user_id", asLong(row.get("user_id")));
        canonical.put("question_id", asLong(row.get("question_id")));
        canonical.put("skill_id", asLong(row.get("skill_id")));
        canonical.put("response", (int) asLong(row.get("response")));
        canonical.put("timestamp", asIsoUtc(rowNumber, row.get("ts")));
        return canonical;
    }

    private static String asIsoUtc(long rowNumber, Object value) {
        if (value == null) {
            throw new NfkTrainingRowValidationException(rowNumber, "timestamp is null");
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toString();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant().toString();
        }
        throw new NfkTrainingRowValidationException(rowNumber,
                "timestamp must be Instant / Timestamp / OffsetDateTime / ZonedDateTime, got "
                        + value.getClass().getName());
    }
}

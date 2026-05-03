package com.alethicode.service.aitutor.parsons;

import com.alethicode.service.ai.AiModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * SQL 编译烟雾测试 — 直连本地 dev PostgreSQL（5436/alethicode），用 read-only
 * 路径触发 {@link ParsonsDistractorGenerator#generate} 让 pgjdbc 真正 PREPARE
 * 内部 SQL，捕获 SQL 占位符冲突 / 列不存在 / 函数不存在 / type mismatch 等
 * <b>编译期</b> bug。
 *
 * <p><b>核心保障</b>：5/3 修复的 `kc_ids ?|` → `jsonb_exists_any` 这条改动如果
 * 未来被回退或者再次踩到 pgjdbc 的占位符坑，本测试会立即在真实 PG 上抛错。
 *
 * <p><b>不动数据</b>：只用 user_id=-1（必不存在）查询，不 INSERT / UPDATE /
 * DELETE 任何表；不依赖 {@link com.alethicode.integration.AbstractJdbcIntegrationTest}
 * 的 truncate cleanup，因此可以安全地连 5436 dev 数据库。
 *
 * <p><b>可选执行</b>：本地无 PostgreSQL 时（curl 5436 不通 / 没设
 * {@code DB_PASSWORD}）静默 skip，不阻塞 `mvn test` 全量。CI 环境配齐
 * {@code DB_PASSWORD} 即自动跑。
 */
class ParsonsDistractorGeneratorSqlSmokeTest {

    private static final String DEFAULT_URL = "jdbc:postgresql://127.0.0.1:5436/alethicode";
    private static final String DEFAULT_USER = "onlinejudge";

    @Test
    void pickFromNotebookSqlMustParseAndExecuteAgainstRealPostgres() {
        String url = System.getenv().getOrDefault("ALETHICODE_SQL_SMOKE_DB_URL", DEFAULT_URL);
        String user = System.getenv().getOrDefault("ALETHICODE_SQL_SMOKE_DB_USERNAME", DEFAULT_USER);
        String password = System.getenv("DB_PASSWORD");
        assumeTrue(password != null && !password.isBlank(),
                "DB_PASSWORD env not set; skipping (set DB_PASSWORD or run via start.sh-aware shell)");

        DataSource ds = singleConnection(url, user, password);
        if (ds == null) {
            return; // assumeTrue inside singleConnection already skipped
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

        ParsonsProperties properties = new ParsonsProperties();
        properties.getDistractor().setLlmFallbackEnabled(false); // 防止意外触发 LLM mock 缺失
        ParsonsDistractorGenerator generator = new ParsonsDistractorGenerator(
                jdbcTemplate, mock(AiModelGateway.class), new ObjectMapper(), properties);

        // 用 user_id=-1（必不存在）保证查询返回空，避免读到任何真实学生数据；
        // 关键是让 pgjdbc 真正 PREPARE 那条 SQL —— 占位符冲突 / 列缺失 /
        // jsonb_exists_any 不存在等 SQL 编译期问题会在此处抛出。
        assertThatCode(() -> {
            List<ParsonsDistractor> result = generator.generate(
                    new ParsonsDistractorGenerator.GenerationContext(
                            -1L, 999L, "smoke", "Python3", "Python 3",
                            List.of(919L, 920L), List.of("KC#919", "KC#920"),
                            List.of(new ParsonsBlock("B0", "pass", 0, ParsonsBlock.FadingState.VISIBLE, null)),
                            1));
            assertThat(result).isEmpty();
        }).as("pickFromNotebook SQL 必须能在真实 PostgreSQL 上 PREPARE 并执行").doesNotThrowAnyException();
    }

    private static DataSource singleConnection(String url, String user, String password) {
        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            // 强制 read-only，防止误写
            conn.setReadOnly(true);
            SingleConnectionDataSource ds = new SingleConnectionDataSource(conn, true);
            ds.setSuppressClose(false);
            return ds;
        } catch (SQLException e) {
            assumeTrue(false, "Cannot connect to PostgreSQL at " + url
                    + " (" + e.getMessage() + "); skipping smoke test. Run start.sh first.");
            return null;
        }
    }
}

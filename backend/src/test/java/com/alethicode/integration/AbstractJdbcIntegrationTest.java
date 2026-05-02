package com.alethicode.integration;

import com.alethicode.service.submission.SubmissionThrottleService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

abstract class AbstractJdbcIntegrationTest {

    @Autowired
    private JdbcTemplate cleanupJdbcTemplate;

    @Autowired(required = false)
    private SubmissionThrottleService submissionThrottleService;

    @BeforeEach
    void cleanDatabaseBeforeEachTest() {
        if (submissionThrottleService != null) {
            submissionThrottleService.resetBucketsForTesting();
        }
        cleanDatabase();
    }

    private void cleanDatabase() {
        IntegrationTestDatabaseGuard.assertSafe(cleanupJdbcTemplate);
        List<String> quotedTableNames = cleanupJdbcTemplate.queryForList(
                """
                select quote_ident(tablename)
                from pg_tables
                where schemaname = 'public'
                  and tablename <> 'flyway_schema_history'
                order by tablename
                """,
                String.class
        );
        if (quotedTableNames.isEmpty()) {
            return;
        }
        cleanupJdbcTemplate.execute("truncate table " + String.join(", ", quotedTableNames) + " restart identity cascade");
    }
}

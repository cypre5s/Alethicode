package com.alethicode.integration;

import org.springframework.jdbc.core.JdbcTemplate;

final class IntegrationTestDatabaseGuard {

    private static final String SHARED_DATABASE_NAME = "alethicode";
    private static final String OVERRIDE_FLAG = "alethicode.integration.allowSharedDb";

    private IntegrationTestDatabaseGuard() {
    }

    static void assertSafe(JdbcTemplate jdbcTemplate) {
        String currentDatabase = jdbcTemplate.queryForObject("select current_database()", String.class);
        String db = currentDatabase == null ? "" : currentDatabase.trim();
        if (!SHARED_DATABASE_NAME.equalsIgnoreCase(db)) {
            return;
        }
        if (Boolean.getBoolean(OVERRIDE_FLAG)) {
            return;
        }
        throw new IllegalStateException(
                "Refusing destructive integration cleanTables on shared database '" + SHARED_DATABASE_NAME + "'. "
                        + "Use a dedicated test database, or pass -D" + OVERRIDE_FLAG + "=true explicitly."
        );
    }
}

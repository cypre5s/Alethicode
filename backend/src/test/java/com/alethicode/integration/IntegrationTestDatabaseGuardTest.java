package com.alethicode.integration;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationTestDatabaseGuardTest {

    @Test
    void shouldAllowDedicatedTestDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select current_database()", String.class)).thenReturn("test_aethicode");

        assertThatCode(() -> IntegrationTestDatabaseGuard.assertSafe(jdbcTemplate))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectSharedDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select current_database()", String.class)).thenReturn("alethicode");

        assertThatThrownBy(() -> IntegrationTestDatabaseGuard.assertSafe(jdbcTemplate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alethicode")
                .hasMessageContaining("dedicated test database");
    }
}

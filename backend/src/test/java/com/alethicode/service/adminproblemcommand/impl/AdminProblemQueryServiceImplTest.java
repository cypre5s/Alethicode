package com.alethicode.service.adminproblemcommand.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.exception.LegacyBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AdminProblemQueryServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectAnonymousAccess() {
        AdminProblemQueryServiceImpl service = new AdminProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());

        assertThatThrownBy(() -> service.getAdminProblems(Map.of(), null))
                .isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
                    assertThat(exception.legacyCode()).isEqualTo("permission-denied");
                    assertThat(exception.getMessage()).isEqualTo("请先登录");
                });
    }

}

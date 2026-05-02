package com.alethicode.service.adminproblemcommand.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProblemTeacherPermissionTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void teacherShouldListProblemsWithRequestedLanguagePackFilter() throws Exception {
        AdminProblemQueryServiceImpl service = new AdminProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("teacher_user");

        String permissionSql = "select id, admin_type, problem_permission, is_disabled from \"user\" where username = ?";
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = (RowMapper<Object>) invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(7L);
            when(resultSet.getString("admin_type")).thenReturn("Teacher");
            when(resultSet.getString("problem_permission")).thenReturn("None");
            when(resultSet.getBoolean("is_disabled")).thenReturn(false);
            return mapper.mapRow(resultSet, 0);
        }).when(jdbcTemplate).queryForObject(eq(permissionSql), ArgumentMatchers.<RowMapper<Object>>any(), eq("teacher_user"));

        when(jdbcTemplate.queryForList(
                startsWith("select distinct clp.language_pack_id"),
                eq(Long.class),
                eq(7L)
        )).thenReturn(List.of());

        when(jdbcTemplate.queryForObject(
                startsWith("select count(*) from problem p left join \"user\" u on u.id = p.created_by_id"),
                eq(Long.class),
                eq(999L)
        )).thenReturn(0L);

        when(jdbcTemplate.query(
                startsWith("select p.id from problem p"),
                ArgumentMatchers.<RowMapper<Long>>any(),
                eq(999L),
                eq(10),
                eq(0)
        )).thenReturn(List.of());

        ApiResponse<Object> response = service.getAdminProblems(Map.of("language_pack_id", "999"), authentication);

        assertThat(response.error()).isNull();
        assertThat(response.data()).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) response.data();
        assertThat(payload.get("results")).isEqualTo(List.of());
        assertThat(payload.get("total")).isEqualTo(0L);
    }
}

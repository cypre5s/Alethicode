package com.alethicode.service.problem.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.exception.LegacyBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemQueryServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void getProblemsShouldReturnErrorWhenLimitIsMissing() {
        ProblemQueryServiceImpl service = new ProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());

        assertThatThrownBy(() -> service.getProblems(Map.of(), null))
                .isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
                    assertThat(exception.legacyCode()).isEqualTo("error");
                    assertThat(exception.getMessage()).isEqualTo("Limit is needed");
                });
    }

    @Test
    void getTagProgressShouldRejectAnonymousRequest() {
        ProblemQueryServiceImpl service = new ProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());

        assertThatThrownBy(() -> service.getTagProgress(null, null, null))
                .isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
                    assertThat(exception.legacyCode()).isEqualTo("permission-denied");
                    assertThat(exception.getMessage()).isEqualTo("请先登录");
                });
    }

    @Test
    void getTagProgressShouldRejectInvalidUserId() {
        ProblemQueryServiceImpl service = new ProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "alice",
                "pwd",
                List.of()
        );

        when(jdbcTemplate.queryForObject(
                "select id from \"user\" where username = ?",
                Long.class,
                "alice"
        )).thenReturn(1L);

        assertThatThrownBy(() -> service.getTagProgress("abc", null, authentication))
                .isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
                    assertThat(exception.legacyCode()).isEqualTo("error");
                    assertThat(exception.getMessage()).isEqualTo("Invalid user_id");
                });
    }

    @SuppressWarnings("unchecked")
    @Test
    void getProblemTagsShouldApplyLanguagePackFilterAndKcBinding() {
        ProblemQueryServiceImpl service = new ProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "alice",
                "pwd",
                List.of()
        );
        List<String> executedSql = new ArrayList<>();
        List<Object[]> executedArgs = new ArrayList<>();

        when(jdbcTemplate.queryForObject(
                "select id from \"user\" where username = ?",
                Long.class,
                "alice"
        )).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                "select admin_type from \"user\" where username = ?",
                String.class,
                "alice"
        )).thenReturn("Regular User");
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1L)))
                .thenReturn(List.of(5L));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    executedSql.add(invocation.getArgument(0));
                    executedArgs.add(Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length));
                    return List.of();
                });

        ApiResponse<Object> response = service.getProblemTags(null, "5", authentication);

        assertThat(response.error()).isNull();
        assertThat(executedSql).singleElement().satisfies(sql -> {
            assertThat(sql).contains("join language_pack_problem_mapping lpm");
            assertThat(sql).contains("lpm.language_pack_id = ?");
            assertThat(sql).contains("t.name not like 'kc:%'");
            assertThat(sql).contains("from language_pack_kc k");
        });
        assertThat(executedArgs).singleElement().satisfies(args ->
                assertThat(args).containsSequence(5L, 1L, 5L));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getTagProgressShouldApplyLanguagePackFilterWhenProvided() {
        ProblemQueryServiceImpl service = new ProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "alice",
                "pwd",
                List.of()
        );
        List<String> executedSql = new ArrayList<>();
        List<Object[]> executedArgs = new ArrayList<>();

        when(jdbcTemplate.queryForObject(
                "select id from \"user\" where username = ?",
                Long.class,
                "alice"
        )).thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                "select admin_type from \"user\" where username = ?",
                String.class,
                "alice"
        )).thenReturn("Regular User");
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1L)))
                .thenReturn(List.of(5L));
        when(jdbcTemplate.queryForObject(
                "select acm_problems_status::text from user_profile where user_id = ?",
                String.class,
                1L
        )).thenReturn("{\"problems\":{}}");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    executedSql.add(invocation.getArgument(0));
                    executedArgs.add(Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length));
                    return List.of();
                });

        ApiResponse<Object> response = service.getTagProgress(null, "5", authentication);

        assertThat(response.error()).isNull();
        assertThat(((Map<?, ?>) response.data()).get("language_pack_id")).isEqualTo(5L);
        assertThat(executedSql).singleElement().satisfies(sql -> {
            assertThat(sql).contains("join language_pack_problem_mapping lpm");
            assertThat(sql).contains("lpm.language_pack_id = ?");
            assertThat(sql).contains("t.name not like 'kc:%'");
            assertThat(sql).contains("from language_pack_kc k");
        });
        assertThat(executedArgs).singleElement().satisfies(args ->
                assertThat(args).containsSequence(5L, 5L));
    }

    @SuppressWarnings("unchecked")
    @Test
    void pickOneShouldReturnErrorWhenNoVisibleProblem() {
        ProblemQueryServiceImpl service = new ProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class))).thenReturn(null);

        assertThatThrownBy(service::pickOne)
                .isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
                    assertThat(exception.legacyCode()).isEqualTo("error");
                    assertThat(exception.getMessage()).isEqualTo("No problem to pick");
                });
    }

    @SuppressWarnings("unchecked")
    @Test
    void pickOneShouldReturnProblemIdWhenVisibleProblemExists() {
        ProblemQueryServiceImpl service = new ProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class))).thenReturn("PPT2-001");

        ApiResponse<Object> response = service.pickOne();

        assertThat(response.error()).isNull();
        assertThat(response.data()).isEqualTo("PPT2-001");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getProblemsShouldIncludeStudentPrivateAiProblemsForCreatorInListQuery() {
        ProblemQueryServiceImpl service = new ProblemQueryServiceImpl(jdbcTemplate, new ObjectMapper());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "alice",
                "pwd",
                List.of()
        );
        List<String> executedSql = new ArrayList<>();
        List<Object[]> executedArgs = new ArrayList<>();

        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    Object[] args = invocation.getArguments().length >= 3
                            ? Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length)
                            : new Object[0];
                    executedSql.add(sql);
                    executedArgs.add(args);
                    if ("select id from \"user\" where username = ?".equals(sql)) {
                        return 7L;
                    }
                    if (sql.startsWith("select count(*) from problem p")) {
                        return 0L;
                    }
                    return null;
                });

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    executedSql.add(invocation.getArgument(0));
                    executedArgs.add(Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length));
                    return List.of();
                });

        ApiResponse<Object> response = service.getProblems(Map.of("limit", "10", "offset", "0"), authentication);

        assertThat(response.error()).isNull();
        assertThat(executedSql).anySatisfy(sql -> {
            assertThat(sql).contains("p.is_ai_generated = true");
            assertThat(sql).contains("p.visibility_status = 'student_private'");
            assertThat(sql).contains("p.created_by_id = ?");
            assertThat(sql).contains("p.visible = true");
        });
        assertThat(executedArgs).anySatisfy(args -> assertThat(args).contains(7L));
    }
}

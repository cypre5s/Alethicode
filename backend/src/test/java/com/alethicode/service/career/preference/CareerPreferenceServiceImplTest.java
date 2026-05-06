package com.alethicode.service.career.preference;

import com.alethicode.service.career.preference.CareerPreferenceService.CareerPreferences;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link CareerPreferenceServiceImpl} 单测——覆盖 plan 9.3 节 + todo 15 核心契约：
 * <ul>
 *   <li>findPreferences 行不存在 ⇒ 全 false（默认启用，不抛错）；</li>
 *   <li>findPreferences 行存在 ⇒ 4 列 boolean 正确投影；</li>
 *   <li>updatePreferences 行不存在 ⇒ 抛 404；</li>
 *   <li>updatePreferences null body ⇒ 抛 422；</li>
 *   <li>updatePreferences 正常 ⇒ UPDATE 4 列；</li>
 *   <li>isModuleDisabled 4 个模块名 + 未知模块名 5 路径。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CareerPreferenceServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CareerPreferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CareerPreferenceServiceImpl(jdbcTemplate);
    }

    @Test
    void findPreferencesReturnsAllEnabledWhenRowMissing() {
        when(jdbcTemplate.queryForObject(
                argThat(sqlContains("from user_profile")),
                any(RowMapper.class), eq(7L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        CareerPreferences prefs = service.findPreferences(7L);

        assertThat(prefs).isEqualTo(CareerPreferences.allEnabled());
    }

    @Test
    void findPreferencesProjectsAllFourColumns() {
        // 用 stub 行让 RowMapper 返回 (true, false, true, false)
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from user_profile")),
                        any(RowMapper.class), eq(42L)))
                .thenReturn(new CareerPreferences(true, false, true, false));

        CareerPreferences prefs = service.findPreferences(42L);

        assertThat(prefs.careerBridgingDisabled()).isTrue();
        assertThat(prefs.codingLensDisabled()).isFalse();
        assertThat(prefs.careerStudioDisabled()).isTrue();
        assertThat(prefs.careerPathDisabled()).isFalse();
    }

    @Test
    void updatePreferencesThrows422WhenBodyNull() {
        assertThatThrownBy(() -> service.updatePreferences(7L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void updatePreferencesThrows404WhenUserProfileMissing() {
        when(jdbcTemplate.update(argThat(sqlContains("update user_profile")),
                any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.updatePreferences(7L,
                new CareerPreferences(true, true, true, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updatePreferencesUpdatesAllFourColumnsOnSuccess() {
        when(jdbcTemplate.update(argThat(sqlContains("update user_profile")),
                eq(true), eq(false), eq(true), eq(false), eq(7L)))
                .thenReturn(1);

        service.updatePreferences(7L, new CareerPreferences(true, false, true, false));
    }

    @Test
    void isModuleDisabledMatchesAllFourModuleNames() {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from user_profile")),
                        any(RowMapper.class), eq(7L)))
                .thenReturn(new CareerPreferences(true, false, true, false));

        assertThat(service.isModuleDisabled(7L, CareerPreferenceServiceImpl.MODULE_CAREER_BRIDGING)).isTrue();
        assertThat(service.isModuleDisabled(7L, CareerPreferenceServiceImpl.MODULE_CODING_LENS)).isFalse();
        assertThat(service.isModuleDisabled(7L, CareerPreferenceServiceImpl.MODULE_CAREER_STUDIO)).isTrue();
        assertThat(service.isModuleDisabled(7L, CareerPreferenceServiceImpl.MODULE_CAREER_PATH)).isFalse();
        // 未知模块名 ⇒ 默认启用
        assertThat(service.isModuleDisabled(7L, "unknown")).isFalse();
        assertThat(service.isModuleDisabled(7L, null)).isFalse();
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

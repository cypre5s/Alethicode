package com.alethicode.service.classroom.ai;

import com.alethicode.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassroomKcResolverTest {

    private static final String CLASSROOM_ID = "classroom-1";
    private static final Long LP_ID = 11L;

    @Test
    void resolveLanguagePackIdShouldReturnBoundLp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(CLASSROOM_ID))).thenReturn(LP_ID);

        ClassroomKcResolver resolver = new ClassroomKcResolver(jdbc);
        assertThat(resolver.resolveLanguagePackId(CLASSROOM_ID)).isEqualTo(LP_ID);
    }

    @Test
    void resolveLanguagePackIdShouldFailfastWhenUnbound() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(CLASSROOM_ID)))
                .thenThrow(new EmptyResultDataAccessException(1));

        ClassroomKcResolver resolver = new ClassroomKcResolver(jdbc);
        assertThatThrownBy(() -> resolver.resolveLanguagePackId(CLASSROOM_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级未绑定语言包");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void listKcOptionsTreeShouldGroupByChapter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(CLASSROOM_ID))).thenReturn(LP_ID);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(LP_ID))).thenAnswer(invocation -> {
            RowMapper<Map<String, Object>> rowMapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            // chapter A → kc 1
            when(rs.getLong("kc_id")).thenReturn(1L, 2L, 99L);
            when(rs.getString("kc_name")).thenReturn("循环", "条件", "孤儿KC");
            when(rs.getString("kc_description")).thenReturn("desc1", "desc2", "");
            when(rs.getObject("chapter_id")).thenReturn(10L, 10L, null);
            when(rs.getObject("chapter_index")).thenReturn(1, 1, null);
            when(rs.getString("chapter_title")).thenReturn("第一章", "第一章", null);
            Map<String, Object> r1 = rowMapper.mapRow(rs, 0);
            Map<String, Object> r2 = rowMapper.mapRow(rs, 1);
            Map<String, Object> r3 = rowMapper.mapRow(rs, 2);
            return List.of(r1, r2, r3);
        });

        ClassroomKcResolver resolver = new ClassroomKcResolver(jdbc);
        List<Map<String, Object>> tree = resolver.listKcOptionsTree(CLASSROOM_ID);
        assertThat(tree).hasSize(2);
        Map<String, Object> chapter1 = tree.get(0);
        assertThat(chapter1.get("chapter_title")).isEqualTo("第一章");
        List<?> kcs = (List<?>) chapter1.get("kcs");
        assertThat(kcs).hasSize(2);
        Map<String, Object> orphan = tree.get(1);
        assertThat(orphan.get("chapter_title")).isEqualTo("未分组");
        assertThat(((List<?>) orphan.get("kcs"))).hasSize(1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void expandKcIdsShouldRejectIdsOutsideThePack() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(CLASSROOM_ID))).thenReturn(LP_ID);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(1L)); // 只有 1 通过校验，2 不属于该 LP

        ClassroomKcResolver resolver = new ClassroomKcResolver(jdbc);
        assertThatThrownBy(() -> resolver.expandKcIds(CLASSROOM_ID, List.of(1, 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该班级语言包的 KC");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void expandKcIdsShouldDedupeAndPreserveOrder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(CLASSROOM_ID))).thenReturn(LP_ID);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(3L, 5L));

        ClassroomKcResolver resolver = new ClassroomKcResolver(jdbc);
        List<Long> ids = resolver.expandKcIds(CLASSROOM_ID, List.of(3, 5, 3));
        assertThat(ids).containsExactly(3L, 5L);
    }

    @Test
    void expandKcIdsShouldReturnEmptyForBlankInput() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(CLASSROOM_ID))).thenReturn(LP_ID);

        ClassroomKcResolver resolver = new ClassroomKcResolver(jdbc);
        assertThat(resolver.expandKcIds(CLASSROOM_ID, List.of())).isEmpty();
        assertThat(resolver.expandKcIds(CLASSROOM_ID, null)).isEmpty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void loadKcNameMapShouldReturnIdToNameMapping() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Map<Long, String>> extractor = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(true, true, false);
                    when(rs.getLong("id")).thenReturn(1L, 2L);
                    when(rs.getString("name")).thenReturn("循环", "条件");
                    return extractor.extractData(rs);
                });

        ClassroomKcResolver resolver = new ClassroomKcResolver(jdbc);
        Map<Long, String> map = resolver.loadKcNameMap(LP_ID, List.of(1L, 2L));
        assertThat(map).containsEntry(1L, "循环").containsEntry(2L, "条件");
    }

    @Test
    void loadKcNameMapShouldShortCircuitForEmptyInput() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomKcResolver resolver = new ClassroomKcResolver(jdbc);
        assertThat(resolver.loadKcNameMap(null, List.of(1L, 2L))).isEmpty();
        assertThat(resolver.loadKcNameMap(LP_ID, List.of())).isEmpty();
    }
}

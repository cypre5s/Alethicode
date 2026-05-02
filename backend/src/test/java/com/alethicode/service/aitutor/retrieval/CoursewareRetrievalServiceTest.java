package com.alethicode.service.aitutor.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursewareRetrievalServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CoursewareRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new CoursewareRetrievalService(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void retrieveShouldIncludePreviewFieldsForLanguagePackHits() throws Exception {
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from language_pack_kc_page_mapping")),
                any(RowMapper.class),
                eq(8L),
                eq(12L)
        )).thenAnswer(invocation -> {
            RowMapper<Map<String, Object>> rowMapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("chunk_id")).thenReturn(501L);
            when(rs.getString("title")).thenReturn("第 3 章课件");
            when(rs.getString("document_title")).thenReturn("chapter-3.pptx");
            when(rs.getString("content")).thenReturn("变量拆位可以先转字符串，也可以用整除与取模。");
            when(rs.getString("chapter")).thenReturn("3");
            when(rs.getObject(anyString())).thenAnswer(getObjectInvocation -> switch (getObjectInvocation.getArgument(0, String.class)) {
                case "document_id" -> 21L;
                case "kc_id" -> 12L;
                case "slide_number" -> 7;
                default -> null;
            });
            return List.of(rowMapper.mapRow(rs, 0));
        });

        List<Map<String, Object>> hits = service.retrieve(null, List.of(12L), "", 5, 8L);

        assertThat(hits).hasSize(1);
        Map<String, Object> first = hits.getFirst();
        assertThat(first.get("document_id")).isEqualTo(21L);
        assertThat(first.get("document_title")).isEqualTo("chapter-3.pptx");
        assertThat(first.get("slide_number")).isEqualTo(7);
        assertThat(first.get("chapter")).isEqualTo("3");
        assertThat(first.get("preview")).isEqualTo("变量拆位可以先转字符串，也可以用整除与取模。");
    }

    @SuppressWarnings("unchecked")
    @Test
    void retrieveShouldNotInventLanguagePackPreviewFieldsForClassicCoursewareHits() throws Exception {
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from ai_courseware_chunk") && sql.contains("where problem_id = ?")),
                any(RowMapper.class),
                eq(1001L)
        )).thenAnswer(invocation -> {
            RowMapper<Map<String, Object>> rowMapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(301L);
            when(rs.getString("title")).thenReturn("整数拆位");
            when(rs.getString("content")).thenReturn("把四位数拆成千百十个四位。");
            when(rs.getString("chapter")).thenReturn("3");
            when(rs.getObject(anyString())).thenAnswer(getObjectInvocation -> "kc_id".equals(getObjectInvocation.getArgument(0, String.class)) ? 9L : null);
            return List.of(rowMapper.mapRow(rs, 0));
        });

        List<Map<String, Object>> hits = service.retrieve(1001L, List.of(), "", 5, null);

        assertThat(hits).hasSize(1);
        Map<String, Object> first = hits.getFirst();
        assertThat(first).doesNotContainKeys("document_id", "document_title");
        assertThat(first.get("chapter")).isEqualTo("3");
        assertThat(first.get("preview")).isEqualTo("把四位数拆成千百十个四位。");
    }
}

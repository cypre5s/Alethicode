package com.alethicode.service.aitutor.context.impl;

import com.alethicode.service.aitutor.context.PageContextProvider;
import com.alethicode.service.aitutor.context.PageSummary;
import com.alethicode.service.languagepack.LanguagePackQaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageContextProviderImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private LanguagePackQaService languagePackQaService;

    @Test
    void resolvesPageTokenAsPackageGlobalPageNumberAcrossDocuments() {
        when(languagePackQaService.listQaPacks("alice")).thenReturn(List.of(packMap(42L, "Python 入门")));
        PageSummary secondChapterFirstPage = new PageSummary(
                42L,
                "Python 入门",
                202L,
                "第二章：分支结构.pptx",
                1,
                "第二章第一页正文",
                Instant.parse("2026-05-07T00:00:00Z")
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L), eq(7)))
                .thenReturn(List.of(secondChapterFirstPage));

        PageContextProvider provider = new PageContextProviderImpl(jdbcTemplate, languagePackQaService);

        List<PageSummary> result = provider.resolvePageReferences("alice", 42L, List.of("@page:7"));

        assertThat(result).containsExactly(secondChapterFirstPage);
        assertThat(result.getFirst().pageNumber()).isEqualTo(1);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(42L), eq(7));
        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("dense_rank() OVER");
        assertThat(sql).contains("ORDER BY d.sort_order, d.id, p.page_no");
        assertThat(sql).contains("global_page_no = ?");
    }

    @Test
    void resolvesChapterPageTokenAsDocumentSortOrderIndex() {
        when(languagePackQaService.listQaPacks("alice")).thenReturn(List.of(packMap(42L, "Python 入门")));
        PageSummary firstChapterSeventhPage = new PageSummary(
                42L,
                "Python 入门",
                101L,
                "第一章：变量与赋值.pptx",
                7,
                "第一章第七页正文",
                Instant.parse("2026-05-07T00:00:00Z")
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L), eq(42L), eq(1), eq(7)))
                .thenReturn(List.of(firstChapterSeventhPage));

        PageContextProvider provider = new PageContextProviderImpl(jdbcTemplate, languagePackQaService);

        List<PageSummary> result = provider.resolvePageReferences("alice", 42L, List.of("@page:1.7"));

        assertThat(result).containsExactly(firstChapterSeventhPage);
        assertThat(result.getFirst().pageNumber()).isEqualTo(7);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(42L), eq(42L), eq(1), eq(7));
        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("dense_rank() OVER (ORDER BY sort_order, id)");
        assertThat(sql).contains("od.chapter_index = ?");
        assertThat(sql).contains("p.page_no = ?");
    }

    private Map<String, Object> packMap(long id, String name) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("id", id);
        pack.put("name", name);
        return pack;
    }
}

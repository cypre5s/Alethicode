package com.alethicode.service.languagepack.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguagePackDisplayIdAllocatorTest {

    @Test
    void shouldAssignDisplayIdsByChapterAndStableOrder() {
        Map<String, Object> chapterTwoLaterPage = unit(2002L, 2, List.of(12), "B题", "sig-b");
        Map<String, Object> chapterOneFirst = unit(1001L, 1, List.of(3), "A题", "sig-a");
        Map<String, Object> chapterTwoFirstPage = unit(2001L, 2, List.of(11), "A题", "sig-c");
        Map<String, Object> chapterOneSecond = unit(1002L, 1, List.of(4), "B题", "sig-d");

        List<Map<String, Object>> units = List.of(
                chapterTwoLaterPage,
                chapterOneFirst,
                chapterTwoFirstPage,
                chapterOneSecond
        );

        LanguagePackDisplayIdAllocator.assignDeterministicDisplayIds(units);

        assertThat(chapterOneFirst.get("display_id")).isEqualTo("PPT1-1");
        assertThat(chapterOneSecond.get("display_id")).isEqualTo("PPT1-2");
        assertThat(chapterTwoFirstPage.get("display_id")).isEqualTo("PPT2-1");
        assertThat(chapterTwoLaterPage.get("display_id")).isEqualTo("PPT2-2");
    }

    @Test
    void shouldFailFastWhenChapterIndexIsMissing() {
        Map<String, Object> invalidUnit = new LinkedHashMap<>();
        invalidUnit.put("id", 3001L);
        invalidUnit.put("source_pages", List.of(1));
        invalidUnit.put("source_title", "无章节");
        invalidUnit.put("source_signature", "sig-x");

        assertThatThrownBy(() -> LanguagePackDisplayIdAllocator.assignDeterministicDisplayIds(List.of(invalidUnit)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chapter_index is required");
    }

    private Map<String, Object> unit(Long id,
                                     Integer chapterIndex,
                                     List<Integer> sourcePages,
                                     String sourceTitle,
                                     String sourceSignature) {
        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("id", id);
        unit.put("chapter_index", chapterIndex);
        unit.put("source_pages", sourcePages);
        unit.put("source_title", sourceTitle);
        unit.put("source_signature", sourceSignature);
        return unit;
    }
}

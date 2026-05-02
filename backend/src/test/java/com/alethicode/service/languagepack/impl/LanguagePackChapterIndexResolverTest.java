package com.alethicode.service.languagepack.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguagePackChapterIndexResolverTest {

    @Test
    void shouldResolveChapterIndexFromChineseChapterPrefix() {
        assertThat(LanguagePackChapterIndexResolver.resolveForPptFilename("第一章 变量和数据类型.pptx"))
                .isEqualTo(1);
        assertThat(LanguagePackChapterIndexResolver.resolveForPptFilename("第七章 综合实战.ppt"))
                .isEqualTo(7);
    }

    @Test
    void shouldResolveChapterIndexFromPptPrefix() {
        assertThat(LanguagePackChapterIndexResolver.resolveForPptFilename("PPT2-循环语句.pptx"))
                .isEqualTo(2);
        assertThat(LanguagePackChapterIndexResolver.resolveForPptFilename("python_basic_ppt12_作业讲解.pptx"))
                .isEqualTo(12);
    }

    @Test
    void shouldReturnNullWhenChapterIndexCannotBeResolved() {
        assertThat(LanguagePackChapterIndexResolver.resolveForPptFilename("变量命名规范.pptx"))
                .isNull();
        assertThat(LanguagePackChapterIndexResolver.resolveForPptFilename("README.md"))
                .isNull();
    }
}

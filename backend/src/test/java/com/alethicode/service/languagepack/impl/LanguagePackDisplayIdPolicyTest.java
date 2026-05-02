package com.alethicode.service.languagepack.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguagePackDisplayIdPolicyTest {

    @Test
    void shouldBuildAndParseDisplayId() {
        String displayId = LanguagePackDisplayIdPolicy.build(2, 3);

        assertThat(displayId).isEqualTo("PPT2-3");
        assertThat(LanguagePackDisplayIdPolicy.isValid(displayId)).isTrue();
        assertThat(LanguagePackDisplayIdPolicy.parseChapterIndex(displayId)).isEqualTo(2);
    }

    @Test
    void shouldRejectInvalidDisplayIdFormat() {
        assertThat(LanguagePackDisplayIdPolicy.isValid("LP-ABC123")).isFalse();
        assertThat(LanguagePackDisplayIdPolicy.isValid("PPT2_1")).isFalse();
        assertThat(LanguagePackDisplayIdPolicy.parseChapterIndex("PPT-2-1")).isNull();
    }
}

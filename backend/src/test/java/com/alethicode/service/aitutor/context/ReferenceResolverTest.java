package com.alethicode.service.aitutor.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceResolverTest {

    @Test
    void parsesExplicitCardReference() {
        assertThat(ReferenceResolver.isExplicitCardRef("@card:C-V-001")).isTrue();
        assertThat(ReferenceResolver.extractCardId("@card:C-V-001")).isEqualTo("C-V-001");
        assertThat(ReferenceResolver.extractCardId("@card:G-AB12CDEF")).isEqualTo("G-AB12CDEF");
    }

    @Test
    void rejectsMalformedExplicitReferences() {
        assertThat(ReferenceResolver.isExplicitCardRef(null)).isFalse();
        assertThat(ReferenceResolver.isExplicitCardRef("")).isFalse();
        assertThat(ReferenceResolver.isExplicitCardRef("@card:")).isFalse();
        assertThat(ReferenceResolver.isExplicitCardRef("@card:abc def")).isFalse();
        assertThat(ReferenceResolver.extractCardId("@card:")).isNull();
        assertThat(ReferenceResolver.extractCardId("not a ref")).isNull();
    }

    @Test
    void recognisesAllShorthandKinds() {
        assertThat(ReferenceResolver.classifyShorthand("@last_error"))
                .isEqualTo(ReferenceResolver.ShorthandKind.ERROR);
        assertThat(ReferenceResolver.classifyShorthand("@last_visualize"))
                .isEqualTo(ReferenceResolver.ShorthandKind.VISUALIZE);
        assertThat(ReferenceResolver.classifyShorthand("@last_ideate"))
                .isEqualTo(ReferenceResolver.ShorthandKind.IDEATE);
        assertThat(ReferenceResolver.classifyShorthand("@last_guide"))
                .isEqualTo(ReferenceResolver.ShorthandKind.GUIDE);
        assertThat(ReferenceResolver.classifyShorthand("@last_review"))
                .isEqualTo(ReferenceResolver.ShorthandKind.REVIEW);
        assertThat(ReferenceResolver.classifyShorthand("@last_post_ac"))
                .isEqualTo(ReferenceResolver.ShorthandKind.POST_AC);
        assertThat(ReferenceResolver.classifyShorthand("@last_transfer"))
                .isEqualTo(ReferenceResolver.ShorthandKind.TRANSFER);
    }

    @Test
    void rejectsUnknownShorthand() {
        assertThat(ReferenceResolver.classifyShorthand("@last_unknown")).isNull();
        assertThat(ReferenceResolver.classifyShorthand(null)).isNull();
        assertThat(ReferenceResolver.classifyShorthand("@card:X")).isNull();
        assertThat(ReferenceResolver.classifyShorthand("not a ref")).isNull();
    }

    @Test
    void shorthandKindMapsToCardType() {
        assertThat(ReferenceResolver.ShorthandKind.ERROR.cardType()).isEqualTo("error_diagnosis");
        assertThat(ReferenceResolver.ShorthandKind.VISUALIZE.cardType()).isEqualTo("visualize");
        assertThat(ReferenceResolver.ShorthandKind.IDEATE.cardType()).isEqualTo("ideate_analysis");
        assertThat(ReferenceResolver.ShorthandKind.GUIDE.cardType()).isEqualTo("problem_guide");
        assertThat(ReferenceResolver.ShorthandKind.REVIEW.cardType()).isEqualTo("knowledge_review");
        assertThat(ReferenceResolver.ShorthandKind.POST_AC.cardType()).isEqualTo("post_ac");
        assertThat(ReferenceResolver.ShorthandKind.TRANSFER.cardType()).isEqualTo("transfer_problem");
    }

    @Test
    void parsesCoursewareReference() {
        assertThat(ReferenceResolver.isCoursewareRef("@courseware:42")).isTrue();
        assertThat(ReferenceResolver.isCoursewareRef("@courseware:1")).isTrue();
        assertThat(ReferenceResolver.isCoursewareRef("  @courseware:9999999  ")).isTrue();
        assertThat(ReferenceResolver.extractCoursewareId("@courseware:42")).isEqualTo(42L);
        assertThat(ReferenceResolver.extractCoursewareId("@courseware:1")).isEqualTo(1L);
        assertThat(ReferenceResolver.extractCoursewareId("@courseware:9999999")).isEqualTo(9_999_999L);
    }

    @Test
    void rejectsMalformedCoursewareReferences() {
        assertThat(ReferenceResolver.isCoursewareRef(null)).isFalse();
        assertThat(ReferenceResolver.isCoursewareRef("")).isFalse();
        assertThat(ReferenceResolver.isCoursewareRef("@courseware:")).isFalse();
        assertThat(ReferenceResolver.isCoursewareRef("@courseware:abc")).isFalse();
        assertThat(ReferenceResolver.isCoursewareRef("@courseware:12abc")).isFalse();
        assertThat(ReferenceResolver.isCoursewareRef("@courseware:-5")).isFalse();
        assertThat(ReferenceResolver.extractCoursewareId("@courseware:")).isNull();
        assertThat(ReferenceResolver.extractCoursewareId("@courseware:abc")).isNull();
        assertThat(ReferenceResolver.extractCoursewareId("@card:42")).isNull();
        assertThat(ReferenceResolver.extractCoursewareId(null)).isNull();
    }

    @Test
    void coursewareAndCardPatternsDoNotCollide() {
        // 互不识别：@card:* 不被 courseware 识别，@courseware:* 不被 card / shorthand 识别
        assertThat(ReferenceResolver.isCoursewareRef("@card:C-V-001")).isFalse();
        assertThat(ReferenceResolver.isExplicitCardRef("@courseware:42")).isFalse();
        assertThat(ReferenceResolver.classifyShorthand("@courseware:42")).isNull();
    }
}

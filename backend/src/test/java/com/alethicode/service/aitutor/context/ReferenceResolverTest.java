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
}

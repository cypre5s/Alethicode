package com.alethicode.service.twin.arena;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiAdversaryServiceTest {

    private final AiAdversaryService service = new AiAdversaryService(null);

    @Test
    void selectDifficultyLevelForHighMastery() {
        assertThat(service.selectDifficultyLevel(0.85)).isEqualTo("expert_with_subtle_bug");
    }

    @Test
    void selectDifficultyLevelForMidMastery() {
        assertThat(service.selectDifficultyLevel(0.6)).isEqualTo("competent");
    }

    @Test
    void selectDifficultyLevelForLowMastery() {
        assertThat(service.selectDifficultyLevel(0.2)).isEqualTo("verbose_but_correct");
    }

    @Test
    void selectDifficultyLevelBoundary08() {
        assertThat(service.selectDifficultyLevel(0.8)).isEqualTo("expert_with_subtle_bug");
    }

    @Test
    void selectDifficultyLevelBoundary05() {
        assertThat(service.selectDifficultyLevel(0.5)).isEqualTo("competent");
    }
}

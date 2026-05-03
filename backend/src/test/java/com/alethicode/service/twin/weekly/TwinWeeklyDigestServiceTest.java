package com.alethicode.service.twin.weekly;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TwinWeeklyDigestServiceTest {

    private final TwinWeeklyDigestService service = new TwinWeeklyDigestService(null);

    @Test
    void generateDigestTextForZeroSubmits() {
        Map<String, Object> metrics = Map.of("submits", 0, "acs", 0, "new_kcs", 0);
        String text = service.generateDigestText(metrics);
        assertThat(text).contains("安静");
    }

    @Test
    void generateDigestTextForHighAcRate() {
        Map<String, Object> metrics = Map.of("submits", 10, "acs", 8, "new_kcs", 2);
        String text = service.generateDigestText(metrics);
        assertThat(text).contains("10 次代码");
        assertThat(text).contains("8 次通过");
        assertThat(text).contains("保持节奏");
    }

    @Test
    void generateDigestTextForLowAcRate() {
        Map<String, Object> metrics = Map.of("submits", 10, "acs", 2, "new_kcs", 0);
        String text = service.generateDigestText(metrics);
        assertThat(text).contains("挑战");
    }

    @Test
    void generateDigestTextWithNewKcs() {
        Map<String, Object> metrics = Map.of("submits", 5, "acs", 3, "new_kcs", 3);
        String text = service.generateDigestText(metrics);
        assertThat(text).contains("3 个新知识点");
        assertThat(text).contains("扩展");
    }

    @Test
    void generateDigestTextForOnlySubmitsNoAcs() {
        Map<String, Object> metrics = Map.of("submits", 3, "acs", 0, "new_kcs", 1);
        String text = service.generateDigestText(metrics);
        assertThat(text).contains("3 次代码");
        assertThat(text).doesNotContain("通过");
    }

    @Test
    void computeWeeklyMetricsDefaultsOnNullJdbc() {
        Map<String, Object> metrics = service.computeWeeklyMetrics(1L, java.time.LocalDate.now());
        assertThat(metrics).containsKey("submits");
        assertThat(metrics.get("submits")).isEqualTo(0);
    }
}

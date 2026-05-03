package com.alethicode.service.twin.weekly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TwinWeeklyDigestServiceTest {

    private final TwinWeeklyDigestService service = new TwinWeeklyDigestService(null);

    @Test void digestZeroSubmits() { assertThat(service.generateDigestText(Map.of("submits", 0, "acs", 0, "new_kcs", 0))).contains("安静"); }
    @Test void digestHighAcRate() { String t = service.generateDigestText(Map.of("submits", 10, "acs", 8, "new_kcs", 2)); assertThat(t).contains("保持节奏"); }
    @Test void digestLowAcRate() { String t = service.generateDigestText(Map.of("submits", 10, "acs", 2, "new_kcs", 0)); assertThat(t).contains("挑战"); }
    @Test void digestWithNewKcs() { String t = service.generateDigestText(Map.of("submits", 5, "acs", 3, "new_kcs", 3)); assertThat(t).contains("新知识点"); }
    @Test void digestNoAcs() { String t = service.generateDigestText(Map.of("submits", 3, "acs", 0, "new_kcs", 1)); assertThat(t).doesNotContain("通过"); }
    @Test void digestAllAc() { String t = service.generateDigestText(Map.of("submits", 5, "acs", 5, "new_kcs", 0)); assertThat(t).contains("保持节奏"); }
    @Test void digestSingleSubmit() { String t = service.generateDigestText(Map.of("submits", 1, "acs", 0, "new_kcs", 0)); assertThat(t).contains("1 次代码"); }
    @Test void digestLargeNumbers() { String t = service.generateDigestText(Map.of("submits", 100, "acs", 50, "new_kcs", 10)); assertThat(t).contains("100"); }
    @Test void digestExactThreshold() { String t = service.generateDigestText(Map.of("submits", 10, "acs", 7, "new_kcs", 0)); assertThat(t).contains("保持节奏"); }
    @Test void digestBelowThreshold() { String t = service.generateDigestText(Map.of("submits", 10, "acs", 3, "new_kcs", 0)); assertThat(t).contains("挑战"); }
    @Test void digestContainsSubmitCount() { String t = service.generateDigestText(Map.of("submits", 7, "acs", 3, "new_kcs", 0)); assertThat(t).contains("7"); }
    @Test void digestContainsAcCount() { String t = service.generateDigestText(Map.of("submits", 7, "acs", 3, "new_kcs", 0)); assertThat(t).contains("3"); }
    @Test void metricsDefaultsOnNullJdbc() { Map<String, Object> m = service.computeWeeklyMetrics(1L, java.time.LocalDate.now()); assertThat(m.get("submits")).isEqualTo(0); }
    @Test void metricsHasAllKeys() { Map<String, Object> m = service.computeWeeklyMetrics(1L, java.time.LocalDate.now()); assertThat(m).containsKeys("submits", "acs", "active_days", "new_kcs", "frustration_moments"); }
    @Test void metricsDefaultAcs() { assertThat(service.computeWeeklyMetrics(1L, java.time.LocalDate.now()).get("acs")).isEqualTo(0); }
}

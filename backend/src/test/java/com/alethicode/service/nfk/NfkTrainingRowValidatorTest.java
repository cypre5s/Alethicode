package com.alethicode.service.nfk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfkTrainingRowValidatorTest {

    private NfkTrainingRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NfkTrainingRowValidator(new ObjectMapper());
        validator.initSchema();
    }

    @Test
    void validRowPasses() {
        assertThatCode(() -> validator.validateRow(1L, validRow())).doesNotThrowAnyException();
    }

    @Test
    void validRowWithFractionalSecondsPasses() {
        Map<String, Object> row = validRow();
        row.put("timestamp", "2026-04-10T10:00:00.123456789Z");
        assertThatCode(() -> validator.validateRow(1L, row)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingTimestamp() {
        Map<String, Object> row = validRow();
        row.remove("timestamp");

        assertThatThrownBy(() -> validator.validateRow(7L, row))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("row 7")
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsLocalTimeFormatWithSpaceSeparator() {
        Map<String, Object> row = validRow();
        row.put("timestamp", "2026-04-10 10:00:00.0");

        assertThatThrownBy(() -> validator.validateRow(2L, row))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsTimestampWithLocalOffsetInsteadOfZ() {
        Map<String, Object> row = validRow();
        row.put("timestamp", "2026-04-10T10:00:00+08:00");

        assertThatThrownBy(() -> validator.validateRow(2L, row))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsResponseEqualsTwo() {
        Map<String, Object> row = validRow();
        row.put("response", 2);

        assertThatThrownBy(() -> validator.validateRow(3L, row))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("response");
    }

    @Test
    void rejectsZeroUserId() {
        Map<String, Object> row = validRow();
        row.put("user_id", 0L);

        assertThatThrownBy(() -> validator.validateRow(4L, row))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("user_id");
    }

    @Test
    void rejectsAdditionalProperties() {
        Map<String, Object> row = validRow();
        row.put("extra", "should-not-be-here");

        assertThatThrownBy(() -> validator.validateRow(5L, row))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("row 5");
    }

    @Test
    void rejectsNullRow() {
        assertThatThrownBy(() -> validator.validateRow(6L, null))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("row 6")
                .hasMessageContaining("null");
    }

    private static Map<String, Object> validRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", 1L);
        row.put("question_id", 100L);
        row.put("skill_id", 7L);
        row.put("response", 1);
        row.put("timestamp", "2026-04-10T10:00:00Z");
        return row;
    }
}

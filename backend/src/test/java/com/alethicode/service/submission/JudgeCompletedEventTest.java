package com.alethicode.service.submission;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeCompletedEventTest {

    @Test
    void recordShouldHoldAllFields() {
        JudgeCompletedEvent event = new JudgeCompletedEvent(
                "sub-123", 1L, 42L, "P001",
                0, "", "print(1)", "Python3",
                "Test Problem", "Description", "Input", "Output",
                Map.of("data", "response"), Map.of("time_cost", 10)
        );

        assertThat(event.submissionId()).isEqualTo("sub-123");
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.problemId()).isEqualTo(42L);
        assertThat(event.problemDisplayId()).isEqualTo("P001");
        assertThat(event.finalResult()).isEqualTo(0);
        assertThat(event.code()).isEqualTo("print(1)");
        assertThat(event.language()).isEqualTo("Python3");
    }

    @Test
    void acResultShouldBeZero() {
        JudgeCompletedEvent event = new JudgeCompletedEvent(
                "sub-1", 1L, 1L, "P1", 0, "", "", "Python3",
                "", "", "", "", null, null
        );

        assertThat(event.finalResult()).isEqualTo(0);
    }

    @Test
    void waResultShouldBeNegativeOne() {
        JudgeCompletedEvent event = new JudgeCompletedEvent(
                "sub-2", 1L, 1L, "P1", -1, "Wrong Answer", "", "Python3",
                "", "", "", "", null, null
        );

        assertThat(event.finalResult()).isEqualTo(-1);
        assertThat(event.errInfo()).isEqualTo("Wrong Answer");
    }
}

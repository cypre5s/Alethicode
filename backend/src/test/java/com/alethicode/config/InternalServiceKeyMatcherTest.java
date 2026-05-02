package com.alethicode.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceKeyMatcherTest {

    @Test
    void matchesCurrentKey() {
        InternalServiceKeyMatcher matcher = new InternalServiceKeyMatcher("current-key-123", "previous-key-456");

        assertThat(matcher.isConfigured()).isTrue();
        assertThat(matcher.matches("current-key-123")).isTrue();
    }

    @Test
    void matchesPreviousKeyDuringRollingWindow() {
        InternalServiceKeyMatcher matcher = new InternalServiceKeyMatcher("current-key-123", "previous-key-456");

        assertThat(matcher.matches("previous-key-456")).isTrue();
    }

    @Test
    void rejectsWrongMissingAndBlankKeys() {
        InternalServiceKeyMatcher matcher = new InternalServiceKeyMatcher("current-key-123", "previous-key-456");

        assertThat(matcher.matches("wrong-key")).isFalse();
        assertThat(matcher.matches("")).isFalse();
        assertThat(matcher.matches(null)).isFalse();
    }

    @Test
    void previousKeyIsOptional() {
        InternalServiceKeyMatcher matcher = new InternalServiceKeyMatcher("current-key-123", "");

        assertThat(matcher.matches("current-key-123")).isTrue();
        assertThat(matcher.matches("previous-key-456")).isFalse();
    }

    @Test
    void currentKeyMustBeConfiguredForMatcherToAcceptAnything() {
        InternalServiceKeyMatcher matcher = new InternalServiceKeyMatcher("", "previous-key-456");

        assertThat(matcher.isConfigured()).isFalse();
        assertThat(matcher.matches("previous-key-456")).isFalse();
    }
}

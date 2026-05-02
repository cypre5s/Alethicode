package com.alethicode.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdeateAnalyzeRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validRequestShouldPassValidation() {
        IdeateAnalyzeRequest request = new IdeateAnalyzeRequest(1L, "session-1", "I think I should use a loop");
        Set<ConstraintViolation<IdeateAnalyzeRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void nullProblemIdShouldFail() {
        IdeateAnalyzeRequest request = new IdeateAnalyzeRequest(null, "session-1", "Some thought");
        Set<ConstraintViolation<IdeateAnalyzeRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("problemId");
    }

    @Test
    void blankThoughtTextShouldFail() {
        IdeateAnalyzeRequest request = new IdeateAnalyzeRequest(1L, "session-1", "");
        Set<ConstraintViolation<IdeateAnalyzeRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("thoughtText");
    }

    @Test
    void nullSessionIdShouldPass() {
        IdeateAnalyzeRequest request = new IdeateAnalyzeRequest(1L, null, "A valid thought");
        Set<ConstraintViolation<IdeateAnalyzeRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}

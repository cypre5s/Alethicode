package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record AdminProblemUpsertRequest(
        Long id,

        @JsonProperty("_id")
        @Size(max = 32)
        String displayId,

        @JsonProperty("language_pack_id")
        @NotNull
        @Min(1)
        Long languagePackId,

        @NotBlank
        @Size(max = 1024)
        String title,

        @NotBlank
        String description,

        @NotBlank
        String inputDescription,

        @NotBlank
        String outputDescription,

        @NotNull
        @NotEmpty
        List<@Valid ProblemSampleRequest> samples,

        @NotBlank
        @Size(max = 64)
        String testCaseId,

        @NotNull
        List<@Valid ProblemTestCaseScoreRequest> testCaseScore,

        @NotNull
        @Min(1)
        @Max(60000)
        Integer timeLimit,

        @NotNull
        @Min(1)
        @Max(1024)
        Integer memoryLimit,

        @NotNull
        @NotEmpty
        List<@NotBlank String> languages,

        @NotNull
        Map<String, String> template,

        String referenceSolutionLanguage,
        String referenceSolutionCode,

        @NotNull
        Boolean visible,

        @NotBlank
        String difficulty,

        @NotNull
        @NotEmpty
        List<@NotBlank String> tags,

        String hint,
        String source,
        Map<String, Object> statisticInfo
) {
}

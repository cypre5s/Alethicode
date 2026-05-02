package com.alethicode.service.aitutor.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.ai.AiModelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecializedProblemGeneratorTest {

    private static final Long USER_ID = 11L;
    private static final String TAXONOMY = "logic_error";

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AiModelGateway aiModelGateway;
    @Mock private AlethicodeProperties properties;
    @Mock private AlethicodeProperties.System systemProperties;

    private SpecializedProblemGenerator generator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        lenient().when(properties.getSystem()).thenReturn(systemProperties);
        lenient().when(systemProperties.getTestCaseDir()).thenReturn(tempDir.toString());
        objectMapper = new ObjectMapper();
        AiProblemTestCaseWriter writer = new AiProblemTestCaseWriter(objectMapper, properties);
        generator = new SpecializedProblemGenerator(jdbcTemplate, objectMapper, aiModelGateway, writer);
    }

    @Test
    void generateOneShouldInsertProblemAndReturnId() {
        Map<String, Object> llmResult = sampleLlmResult();
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(llmResult);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("insert into problem(")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(7777L);

        Long id = generator.generateOne(USER_ID, TAXONOMY, List.of("loop never updates i"), List.of());

        assertThat(id).isEqualTo(7777L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateOneShouldFailFastWhenLlmReturnsNoTestCases() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "T");
        result.put("description", "D");
        result.put("samples", List.of());
        result.put("test_cases", List.of());
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(result);

        assertThatThrownBy(() -> generator.generateOne(USER_ID, TAXONOMY, List.of("err"), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM 未返回有效 test_cases");
    }

    @SuppressWarnings("unchecked")
    @Test
    void appendOneToPackageShouldFlipProblemSourceAndInsertReviewRow() {
        generator.appendOneToPackage("pkg-1", 99L, 4);
        verify(jdbcTemplate).update(eq("update problem set ai_source_review_package_id = ? where id = ?"), eq("pkg-1"), eq(99L));
        verify(jdbcTemplate).update(
                argThat(sql -> sql != null && sql.contains("insert into ai_error_review_problem")),
                anyString(), eq("pkg-1"), eq(99L), eq(4)
        );
    }

    @Test
    void generateSpecializedProblemsShouldSwallowFailuresAndKeepBatch() {
        Map<String, Object> ok = sampleLlmResult();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("title", "X");
        bad.put("test_cases", List.of());
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(ok)
                .thenReturn(bad)
                .thenReturn(ok);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("insert into problem(")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(101L, 103L);

        List<Long> ids = generator.generateSpecializedProblems(USER_ID, TAXONOMY, List.of("rc"), List.of(), 3);

        assertThat(ids).containsExactly(101L, 103L);
    }

    private Map<String, Object> sampleLlmResult() {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("title", "Sum two ints");
        sample.put("description", "Read two ints, print sum");
        sample.put("input_description", "Two ints separated by spaces");
        sample.put("output_description", "Single int");
        sample.put("reference_solution_code", "a, b = map(int, input().split())\nprint(a + b)\n");
        sample.put("difficulty", "Low");
        sample.put("samples", List.of(Map.of("input", "1 2", "output", "3")));
        sample.put("test_cases", List.of(Map.of("input", "1 2", "output", "3"), Map.of("input", "4 5", "output", "9")));
        return sample;
    }

    @SuppressWarnings("unused")
    private RowMapper<?> markRowMapperUsed() { return null; }
}

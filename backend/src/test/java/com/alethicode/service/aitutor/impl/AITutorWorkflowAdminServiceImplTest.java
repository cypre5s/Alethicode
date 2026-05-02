package com.alethicode.service.aitutor.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.config.BetaFeatureRegistry;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.exception.LegacyBusinessException;
import com.alethicode.service.ai.AiCircuitBreaker;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;
import com.alethicode.service.aitutor.policy.TransitionPolicy;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.profile.LearnerMemoryService;
import com.alethicode.service.aitutor.profile.LearnerProfileProjector;
import com.alethicode.service.aitutor.profile.LearnerState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.ArgumentCaptor;

class AITutorWorkflowAdminServiceImplTest {

    @TempDir
    Path tempDir;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AiModelGateway aiModelGateway = mock(AiModelGateway.class);
    private final com.alethicode.service.rag.RagServiceClient ragServiceClient =
            mock(com.alethicode.service.rag.RagServiceClient.class);
    private final AiCircuitBreaker aiCircuitBreaker = mock(AiCircuitBreaker.class);
    private final ReflectionService reflectionService = mock(ReflectionService.class);
    private final AITutorWorkflowAdminServiceImpl service = new AITutorWorkflowAdminServiceImpl(
            jdbcTemplate,
            new ObjectMapper(),
            aiModelGateway,
            ragServiceClient,
            new AlethicodeProperties(),
            reflectionService,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            aiCircuitBreaker
    );

    AITutorWorkflowAdminServiceImplTest() {
        when(reflectionService.reflectAndRefine(any(), any(), any(), eq(1)))
                .thenAnswer(invocation -> new ReflectionResult(
                        invocation.getArgument(2),
                        true,
                        0,
                        "pass"
                ));
    }

    @Test
    void workflowSessionGetShouldRequireLogin() {
        assertThatThrownBy(() -> service.workflowSessionGet(Map.of("problem_id", "1001"), null))
                .isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
                    assertThat(exception.legacyCode()).isEqualTo("permission-denied");
                    assertThat(exception.getMessage()).isEqualTo("请先登录");
                });
    }

    @Test
    void workflowEventShouldRequireLogin() {
        assertThatThrownBy(() -> service.workflowEvent(
                Map.of("problem_id", 1001, "event", "READING", "event_data", Map.of()),
                null
        )).isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
            assertThat(exception.legacyCode()).isEqualTo("permission-denied");
            assertThat(exception.getMessage()).isEqualTo("请先登录");
        });
    }

    @Test
    void ideateSkeletonShouldReturnDedicatedSkeletonPayloadWithoutWorkflowMutation() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("root");
        when(jdbcTemplate.queryForObject(
                eq("select id, admin_type from \"user\" where lower(username) = ?"),
                any(RowMapper.class),
                eq("root")
        )).thenReturn(invokeUserAuth(true, 1L, true, true));
        when(jdbcTemplate.query(
                anyString(),
                any(ResultSetExtractor.class),
                eq(110L)
        )).thenAnswer(invocation -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>(Map.ofEntries(
                    Map.entry("id", 110L),
                    Map.entry("title", "平均成绩计算"),
                    Map.entry("description", "输入三个成绩，输出平均值"),
                    Map.entry("input_description", "一行三个数字"),
                    Map.entry("output_description", "保留一位小数"),
                    Map.entry("samples", "[{\"input\":\"86 92 78\",\"output\":\"85.3\"}]"),
                    Map.entry("hint", ""),
                    Map.entry("source", "流程自动化"),
                    Map.entry("reference_solution_code", ""),
                    Map.entry("reference_solution_language", ""),
                    Map.entry("languages", List.of("Python3")),
                    Map.entry("template", "")
            ));
            row.put("language_pack_id", null);
            row.put("language_pack_primary_language", "");
            return row;
        });
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "description", "先补全输入和平均值变量",
                "skeleton", "scores = list(map(float, input().split()))\n# TODO: 计算平均值"
        ));

        ApiResponse<Object> response = service.ideateSkeleton(
                Map.of("problem_id", 110L, "session_id", "s1"),
                authentication
        );

        assertThat(response.error()).isNull();
        assertThat(response.data()).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) response.data();
        assertThat(payload.get("description")).isEqualTo("先补全输入和平均值变量");
        assertThat(payload.get("skeleton")).isEqualTo("scores = list(map(float, input().split()))\n# TODO: 计算平均值");
        assertThat(payload.get("session_id")).isEqualTo("s1");
    }

    @Test
    void ideateSkeletonShouldAcceptSkeletonAliasAndOptionalDescription() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("root");
        when(jdbcTemplate.queryForObject(
                eq("select id, admin_type from \"user\" where lower(username) = ?"),
                any(RowMapper.class),
                eq("root")
        )).thenReturn(invokeUserAuth(true, 1L, true, true));
        when(jdbcTemplate.query(
                anyString(),
                any(ResultSetExtractor.class),
                eq(110L)
        )).thenAnswer(invocation -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>(Map.ofEntries(
                    Map.entry("id", 110L),
                    Map.entry("title", "平均成绩计算"),
                    Map.entry("description", "输入三个成绩，输出平均值"),
                    Map.entry("input_description", "一行三个数字"),
                    Map.entry("output_description", "保留一位小数"),
                    Map.entry("samples", "[{\"input\":\"86 92 78\",\"output\":\"85.3\"}]"),
                    Map.entry("hint", ""),
                    Map.entry("source", "流程自动化"),
                    Map.entry("reference_solution_code", ""),
                    Map.entry("reference_solution_language", ""),
                    Map.entry("languages", List.of("Python3")),
                    Map.entry("template", "")
            ));
            row.put("language_pack_id", null);
            row.put("language_pack_primary_language", "");
            return row;
        });
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "code", "scores = list(map(float, input().split()))\n# TODO: 计算平均值"
        ));

        ApiResponse<Object> response = service.ideateSkeleton(
                Map.of("problem_id", 110L, "session_id", "s1"),
                authentication
        );

        assertThat(response.error()).isNull();
        assertThat(response.data()).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) response.data();
        assertThat(payload.get("description")).isEqualTo("");
        assertThat(payload.get("skeleton")).isEqualTo("scores = list(map(float, input().split()))\n# TODO: 计算平均值");
        assertThat(payload.get("session_id")).isEqualTo("s1");
    }

    @Test
    void ideateSkeletonShouldMergeNestedDescriptionWithTopLevelSkeletonAlias() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("root");
        when(jdbcTemplate.queryForObject(
                eq("select id, admin_type from \"user\" where lower(username) = ?"),
                any(RowMapper.class),
                eq("root")
        )).thenReturn(invokeUserAuth(true, 1L, true, true));
        when(jdbcTemplate.query(
                anyString(),
                any(ResultSetExtractor.class),
                eq(110L)
        )).thenAnswer(invocation -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>(Map.ofEntries(
                    Map.entry("id", 110L),
                    Map.entry("title", "平均成绩计算"),
                    Map.entry("description", "输入三个成绩，输出平均值"),
                    Map.entry("input_description", "一行三个数字"),
                    Map.entry("output_description", "保留一位小数"),
                    Map.entry("samples", "[{\"input\":\"86 92 78\",\"output\":\"85.3\"}]"),
                    Map.entry("hint", ""),
                    Map.entry("source", "流程自动化"),
                    Map.entry("reference_solution_code", ""),
                    Map.entry("reference_solution_language", ""),
                    Map.entry("languages", List.of("Python3")),
                    Map.entry("template", "")
            ));
            row.put("language_pack_id", null);
            row.put("language_pack_primary_language", "");
            return row;
        });
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "data", Map.of("description", "先补全输入和平均值变量"),
                "code", "scores = list(map(float, input().split()))\n# TODO: 计算平均值"
        ));

        ApiResponse<Object> response = service.ideateSkeleton(
                Map.of("problem_id", 110L, "session_id", "s1"),
                authentication
        );

        assertThat(response.error()).isNull();
        assertThat(response.data()).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) response.data();
        assertThat(payload.get("description")).isEqualTo("先补全输入和平均值变量");
        assertThat(payload.get("skeleton")).isEqualTo("scores = list(map(float, input().split()))\n# TODO: 计算平均值");
        assertThat(payload.get("session_id")).isEqualTo("s1");
    }

    @Test
    void ideateSkeletonShouldAskLlmToAvoidUnnecessaryMainAndImports() throws Exception {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "description", "先补全输入和平均值变量",
                "skeleton", "scores = list(map(float, input().split()))\n# TODO: 计算平均值"
        ));

        invokeGenerateSkeletonByLlm(
                "输入三个成绩，输出平均值",
                new LanguageAwareTutorContext("Python3", List.of("Python3"), "", null, "", null)
        );

        ArgumentCaptor<String> promptCaptor = forClass(String.class);
        verify(aiModelGateway).callForJson(anyString(), promptCaptor.capture());
        String userPrompt = promptCaptor.getValue();
        assertThat(userPrompt).contains("不要为了凑模板额外包一层 main、def main()");
        assertThat(userPrompt).contains("不要添加不会实际用到的 import、include、using、package");
        assertThat(userPrompt).contains("默认使用简体中文");
        assertThat(userPrompt).contains("变量名、函数名、类名等标识符不要翻译成拼音或中文");
        assertThat(userPrompt).contains("优先使用符合该语言习惯的清晰英文命名");
        assertThat(userPrompt).contains("一句中文简短说明如何使用骨架");
        assertThat(userPrompt).contains("包含中文 TODO 注释");
    }

    @Test
    void adminVariantReviewDelegatesToAdminVariantReviewService() {
        assertThatThrownBy(() -> service.adminVariantReview(Map.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nextTransferDisplayIdShouldSupportChapterBasedId() throws Exception {
        when(jdbcTemplate.queryForList(
                "select _id from problem where _id like ?",
                String.class,
                "2.1.%"
        )).thenReturn(List.of("2.1.001", "2.1.009"));
        when(jdbcTemplate.queryForObject(
                "select count(*) from problem where _id = ?",
                Integer.class,
                "2.1.010"
        )).thenReturn(0);

        String displayId = invokeNextTransferDisplayId("2.1.001", "2.1 圆面积计算");

        assertThat(displayId).isEqualTo("2.1.010");
    }

    @Test
    void nextTransferDisplayIdShouldDeriveChapterPrefixFromTitle() throws Exception {
        when(jdbcTemplate.queryForList(
                "select _id from problem where _id like ?",
                String.class,
                "2.1.%"
        )).thenReturn(List.of("2.1.001", "2.1.002"));
        when(jdbcTemplate.queryForObject(
                "select count(*) from problem where _id = ?",
                Integer.class,
                "2.1.003"
        )).thenReturn(0);

        String displayId = invokeNextTransferDisplayId("PPT2-1", "2.1 圆面积计算");

        assertThat(displayId).isEqualTo("2.1.003");
    }

    @Test
    void nextTransferDisplayIdShouldRejectWhenDisplayIdBlankAndTitleHasNoChapterPrefix() {
        assertThatThrownBy(() -> invokeNextTransferDisplayId("", "圆面积计算"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage(
                        "source display_id must map to chapter prefix (from display_id or title), got display_id: , title: 圆面积计算"
                );
    }

    @Test
    void generateTransferByLlmWithRetryShouldRetryWhenHintMissing() throws Exception {
        Map<String, Object> missingHint = Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "reference_solution_code", "print('ok')",
                "samples", List.of(Map.of("input", "1", "output", "1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        );
        Map<String, Object> valid = Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "hint", "提示",
                "reference_solution_code", "print('ok')",
                "samples", List.of(Map.of("input", "1", "output", "1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        );
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(missingHint)
                .thenReturn(valid);

        Map<String, Object> generated = invokeGenerateTransferByLlmWithRetry();

        assertThat(generated.get("hint")).isEqualTo("提示");
        verify(aiModelGateway, times(2)).callForJson(anyString(), anyString());
    }

    @Test
    void generateTransferByLlmWithRetryShouldFailAfterMaxAttempts() {
        Map<String, Object> missingHint = Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "reference_solution_code", "print('ok')",
                "samples", List.of(Map.of("input", "1", "output", "1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        );
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(missingHint)
                .thenReturn(missingHint)
                .thenReturn(missingHint);

        assertThatThrownBy(this::invokeGenerateTransferByLlmWithRetry)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(LegacyBusinessException.class)
                .hasRootCauseMessage("类似题生成失败，请重试（transfer.hint is required）");
        verify(aiModelGateway, times(3)).callForJson(anyString(), anyString());
    }

    @Test
    void transferSystemPromptShouldExplicitlyRequireNonEmptyHint() throws Exception {
        String prompt = invokeTransferSystemPrompt();

        assertThat(prompt)
                .contains("hint 必须为非空字符串")
                .contains("即使源题 hint 为空，也必须自行生成新的 hint")
                .contains("reference_solution_code")
                .contains("test_cases");
    }

    @Test
    void transferSystemPromptShouldRequireOneLevelHarderWithinSyllabus() throws Exception {
        String prompt = invokeTransferSystemPrompt();

        assertThat(prompt)
                .contains("难度比源题提升一个层级")
                .contains("仍严格限定在源题所属考纲知识范围内")
                .contains("至少包含 1 个需要学生主动思考")
                .contains("不能变成纯模板套用");
    }

    @Test
    void transferSourcePayloadShouldMarkBlankHintAsUnavailable() throws Exception {
        Map<String, Object> payload = invokeBuildTransferSourcePayload(
                Map.of(
                        "id", 101L,
                        "_id", "PPT2-1",
                        "title", "2.1 圆面积计算",
                        "languages_json", "[\"Python3\"]"
                ),
                "描述",
                "输入",
                "输出",
                "   ",
                List.of(Map.of("input", "5", "output", "78.5375")),
                "Python3"
        );

        assertThat(payload.get("source_hint")).isEqualTo("");
        assertThat(payload.get("source_hint_available")).isEqualTo(false);
    }

    @Test
    void transferUserPromptShouldExplicitlyRequireFreshHintWhenSourceHintBlank() throws Exception {
        Map<String, Object> sourcePayload = invokeBuildTransferSourcePayload(
                Map.of(
                        "id", 101L,
                        "_id", "PPT2-1",
                        "title", "2.1 圆面积计算",
                        "languages_json", "[\"Python3\"]"
                ),
                "描述",
                "输入",
                "输出",
                "   ",
                List.of(Map.of("input", "5", "output", "78.5375")),
                "Python3"
        );

        String prompt = invokeBuildTransferUserPrompt(sourcePayload, 1, null, "");

        assertThat(prompt)
                .contains("源题没有可复用的 hint")
                .contains("你必须自行补出新的非空 hint");
    }

    @Test
    void generateTransferByLlmWithRetryShouldEscalateHintRequirementOnSecondAttempt() throws Exception {
        Map<String, Object> missingHint = Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "reference_solution_code", "print('ok')",
                "samples", List.of(Map.of("input", "1", "output", "1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        );
        Map<String, Object> valid = Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "hint", "提示",
                "reference_solution_code", "print('ok')",
                "samples", List.of(Map.of("input", "1", "output", "1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        );
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(missingHint)
                .thenReturn(valid);

        invokeGenerateTransferByLlmWithRetryWithHint("   ");

        ArgumentCaptor<String> promptCaptor = forClass(String.class);
        verify(aiModelGateway, times(2)).callForJson(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1))
                .contains("上一次返回缺少字段：hint")
                .contains("本次必须补齐非空 hint")
                .contains("源题没有可复用的 hint");
    }

    @Test
    void validateGeneratedTransferPayloadShouldRequireJudgeAssets() {
        Map<String, Object> withoutReferenceSolution = Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "hint", "提示",
                "samples", List.of(Map.of("input", "1", "output", "1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        );
        Map<String, Object> withoutTestCases = Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "hint", "提示",
                "reference_solution_code", "print('ok')",
                "samples", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        );

        assertThatThrownBy(() -> invokeValidateGeneratedTransferPayload(withoutReferenceSolution))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseMessage("transfer.reference_solution_code is required");
        assertThatThrownBy(() -> invokeValidateGeneratedTransferPayload(withoutTestCases))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseMessage("transfer.test_cases must be an array");
    }

    @Test
    void validateGeneratedTransferPayloadShouldRejectLoneMinusAfterDigitPlaceInSampleOutput() throws Exception {
        java.util.Map<String, Object> generated = new java.util.LinkedHashMap<>(Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "hint", "提示",
                "reference_solution_code", "print('ok')",
                "samples", List.of(Map.of("input", "1", "output", "千位数是-，百位数是1\n1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        ));
        assertThatThrownBy(() -> invokeValidateGeneratedTransferPayload(generated))
                .isInstanceOf(InvocationTargetException.class)
                .satisfies(ex -> {
                    assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
                    String msg = ex.getCause().getMessage();
                    assertThat(msg).contains("transfer.samples[0].output invalid").contains("千位数是-");
                });
    }

    @Test
    void validateGeneratedTransferPayloadShouldRejectDupWeiInDigitPlaceOutput() throws Exception {
        java.util.Map<String, Object> generated = new java.util.LinkedHashMap<>(Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "hint", "提示",
                "reference_solution_code", "print('ok')",
                "samples", List.of(Map.of("input", "1", "output", "百位位数是1\n1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        ));
        assertThatThrownBy(() -> invokeValidateGeneratedTransferPayload(generated))
                .isInstanceOf(InvocationTargetException.class)
                .satisfies(ex -> {
                    assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
                    String msg = ex.getCause().getMessage();
                    assertThat(msg).contains("transfer.samples[0].output invalid").contains("位位");
                });
    }

    @Test
    void writeTransferTestCasesShouldPersistJudgeFilesToConfiguredDir() throws Exception {
        AlethicodeProperties properties = new AlethicodeProperties();
        properties.getSystem().setTestCaseDir(tempDir.toString());
        AITutorWorkflowAdminServiceImpl localService = new AITutorWorkflowAdminServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                aiModelGateway,
                ragServiceClient,
                properties,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                aiCircuitBreaker
        );

        invokeWriteTransferTestCases(
                localService,
                "transfer_case_001",
                List.of(
                        Map.of("input", "3.5 4.2", "output", "14.70"),
                        Map.of("input", "2 5", "output", "10.00")
                )
        );

        Path testCaseDir = tempDir.resolve("transfer_case_001");
        assertThat(Files.readString(testCaseDir.resolve("1.in"), StandardCharsets.UTF_8)).isEqualTo("3.5 4.2\n");
        assertThat(Files.readString(testCaseDir.resolve("1.out"), StandardCharsets.UTF_8)).isEqualTo("14.70\n");
        assertThat(Files.readString(testCaseDir.resolve("2.in"), StandardCharsets.UTF_8)).isEqualTo("2 5\n");
        assertThat(Files.readString(testCaseDir.resolve("2.out"), StandardCharsets.UTF_8)).isEqualTo("10.00\n");
        assertThat(Files.readString(testCaseDir.resolve("info"), StandardCharsets.UTF_8))
                .contains("\"spj\" : false")
                .contains("\"input_name\" : \"1.in\"")
                .contains("\"output_name\" : \"2.out\"");
    }

    @Test
    void mergeBehaviorMetricsShouldPreserveTrackedFieldsAndLastEvent() throws Exception {
        Map<String, Object> merged = invokeMergeBehaviorMetrics(
                Map.of(
                        "consecutiveErrors", 1,
                        "submissionCount", 2,
                        "editFrequency", 3,
                        "dwellTime", 4,
                        "deleteRatio", 0.1,
                        "latency_ms", 9
                ),
                "CHAT",
                Map.of(
                        "behavior_metrics", Map.of(
                                "consecutiveErrors", 5,
                                "submissionCount", 6,
                                "editFrequency", 7,
                                "dwellTime", 8,
                                "deleteRatio", 0.25
                        )
                ),
                12
        );

        assertThat(merged.get("consecutiveErrors")).isEqualTo(5);
        assertThat(merged.get("submissionCount")).isEqualTo(6);
        assertThat(merged.get("editFrequency")).isEqualTo(7);
        assertThat(merged.get("dwellTime")).isEqualTo(8);
        assertThat(merged.get("deleteRatio")).isEqualTo(0.25);
        assertThat(merged.get("latency_ms")).isEqualTo(12);
        assertThat(merged.get("last_event")).isEqualTo("CHAT");
    }

    @Test
    void normalizePostAcPayloadShouldPopulateLevelTwoAndLevelThreeForGuidanceLevelThree() throws Exception {
        Map<String, Object> payload = invokeNormalizePostAcPayload(
                Map.of(
                        "celebration", "通过了",
                        "what_you_learned", List.of("输入输出"),
                        "key_success_point", "主流程正确",
                        "transfer_tip", "迁移总结",
                        "one_improvement", "命名更清晰",
                        "recommended_review", "字符串",
                        "next_practice_direction", "继续练习",
                        "peer_comparison", Map.of(
                                "algorithm_diff", "优秀解法更先抽象输入",
                                "structure_diff", "结构更紧凑",
                                "organization_diff", "输出更集中"
                        ),
                        "progressive_hints", List.of(
                                Map.of("title", "第一步", "question", "先做什么？", "code_snippet", "a = input()")
                        )
                ),
                3
        );

        assertThat(payload).containsKey("level_2");
        assertThat(payload).containsKey("level_3");
        assertThat(castMap(payload.get("level_2")).get("algorithm_diff")).isEqualTo("优秀解法更先抽象输入");
        assertThat(castMap(payload.get("level_3")).get("steps")).isEqualTo(List.of(
                Map.of("title", "第一步", "question", "先做什么？", "code_snippet", "a = input()")
        ));
    }

    @Test
    void buildChatPayloadShouldCallLlmAndAppendAssistantHistory() throws Exception {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "reply", "先把输入和输出链路跑通，再看计算细节。",
                "focus_point", "输入解析",
                "next_question", "当输入是 1 2 时，你希望输出多少？"
        ));

        Map<String, Object> payload = invokeBuildChatPayload(
                "CODING",
                "题目上下文",
                "我卡住了，不知道下一步",
                "a, b = map(int, input().split())",
                Map.of("consecutiveErrors", 1),
                Map.of("history", List.of(
                        Map.of("role", "assistant", "content", "先确认题意", "phase", "READING", "ts", "2026-03-29T00:00:00Z")
                )),
                null
        );

        List<Map<String, Object>> history = castMapList(payload.get("history"));
        assertThat(history).hasSize(3);
        assertThat(history.get(1).get("role")).isEqualTo("user");
        assertThat(history.get(2).get("role")).isEqualTo("assistant");
        assertThat(String.valueOf(history.get(2).get("content"))).contains("聚焦点：输入解析");
        assertThat(String.valueOf(history.get(2).get("content"))).contains("思考题：当输入是 1 2 时，你希望输出多少？");
        verify(aiModelGateway, times(1)).callForJson(anyString(), anyString());
    }

    @Test
    void buildChatPayloadShouldBlockDirectAnswerStyleContent() throws Exception {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "reply", "```python\nprint(a + b)\n```",
                "focus_point", "直接输出",
                "next_question", "还需要我给完整代码吗？"
        ));

        Map<String, Object> payload = invokeBuildChatPayload(
                "READING",
                "题目上下文",
                "直接给我答案",
                "",
                Map.of("consecutiveErrors", 0),
                Map.of(),
                null
        );

        List<Map<String, Object>> history = castMapList(payload.get("history"));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("role")).isEqualTo("user");
        assertThat(history.get(1).get("role")).isEqualTo("assistant");
        String content = String.valueOf(history.get(1).get("content"));
        assertThat(content).contains("我先不直接给最终答案");
        assertThat(content).doesNotContain("```");
    }

    @Test
    void buildErrorDiagnosisPayloadShouldExposeFirstFailedTestCaseEvidence() throws Exception {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "root_cause", "错误样例说明了加法结果不对",
                "what_program_is_doing", "程序输出了错误的和",
                "expected_behavior", "程序应该输出正确结果",
                "fix_direction", "先检查加法逻辑",
                "related_kcs", List.of("输入输出"),
                "encouragement", "再试一次"
        ));

        Map<String, Object> diagnosis = invokeBuildErrorDiagnosisPayload(
                Map.of(
                        "result", -1,
                        "code", "print(4)",
                        "language", "Python3",
                        "info", Map.of(
                                "data", List.of(
                                        Map.of("test_case", "1", "result", -1, "input", "1 2", "expected_output", "3", "actual_output", "4"),
                                        Map.of("test_case", "2", "result", 0, "input", "2 2", "expected_output", "4", "actual_output", "4")
                                )
                        ),
                        "statistic_info", Map.of()
                ),
                "题目上下文",
                Map.of("consecutiveErrors", 1),
                null,
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(castMap(diagnosis.get("first_failed_test_case"))).isEqualTo(Map.of(
                "input", "1 2",
                "expected_output", "3",
                "actual_output", "4"
        ));
    }

    private String invokeNextTransferDisplayId(String sourceDisplayId, String sourceTitle) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod("nextTransferDisplayId", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, sourceDisplayId, sourceTitle);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeBuildErrorDiagnosisPayload(
            Map<String, Object> submission,
            String problemContext,
            Map<String, Object> behaviorMetrics,
            Object learnerState,
            List<Map<String, Object>> coursewareRefs,
            List<Map<String, Object>> similarNotebookHits,
            List<Map<String, Object>> similarMemoryHits
    ) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "buildErrorDiagnosisPayload",
                LanguageAwareTutorContext.class,
                Map.class,
                String.class,
                Map.class,
                Class.forName("com.alethicode.service.aitutor.profile.LearnerState"),
                List.class,
                List.class,
                List.class,
                Long.class,
                Long.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                service,
                new LanguageAwareTutorContext("Python3", List.of("Python3"), "", null, "", null),
                submission,
                problemContext,
                behaviorMetrics,
                learnerState,
                coursewareRefs,
                similarNotebookHits,
                similarMemoryHits,
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeMergeBehaviorMetrics(
            Map<String, Object> existing,
            String event,
            Map<String, Object> eventData,
            int latencyMs
    ) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "mergeBehaviorMetrics",
                Map.class,
                String.class,
                Map.class,
                int.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(service, existing, event, eventData, latencyMs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeNormalizePostAcPayload(
            Map<String, Object> raw,
            int guidanceLevel
    ) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "normalizePostAcPayload",
                Map.class,
                int.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(service, raw, guidanceLevel);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeBuildChatPayload(
            String currentPhase,
            String problemContext,
            String message,
            String code,
            Map<String, Object> behaviorMetrics,
            Map<String, Object> existingChatPayload,
            Object learnerState
    ) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "buildChatPayload",
                LanguageAwareTutorContext.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Map.class,
                Map.class,
                Map.class,
                Class.forName("com.alethicode.service.aitutor.profile.LearnerState")
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                service,
                new LanguageAwareTutorContext("Python3", List.of("Python3"), "", null, "", null),
                currentPhase,
                problemContext,
                message,
                code,
                behaviorMetrics,
                Map.of(),
                existingChatPayload,
                learnerState
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeGenerateSkeletonByLlm(
            String problemContext,
            LanguageAwareTutorContext tutorContext
    ) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "generateSkeletonByLlm",
                String.class,
                LanguageAwareTutorContext.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(service, problemContext, tutorContext);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castMapList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeGenerateTransferByLlmWithRetry() throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "generateTransferByLlmWithRetry",
                Map.class,
                String.class,
                String.class,
                String.class,
                String.class,
                List.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                service,
                Map.of(
                        "id", 1001L,
                        "_id", "PPT2-1",
                        "title", "2.1 圆面积计算",
                        "languages_json", "[\"Python3\"]"
                ),
                "描述",
                "输入",
                "输出",
                "提示",
                List.of(Map.of("input", "1", "output", "1")),
                "Python3",
                ""
        );
    }

    private String invokeTransferSystemPrompt() throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod("buildTransferSystemPrompt");
        method.setAccessible(true);
        return (String) method.invoke(service);
    }

    private String invokeBuildTransferUserPrompt(
            Map<String, Object> sourcePayload,
            int attempt,
            String previousError,
            String nextPracticeDirection
    ) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "buildTransferUserPrompt",
                Map.class,
                int.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, sourcePayload, attempt, previousError, nextPracticeDirection);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeBuildTransferSourcePayload(
            Map<String, Object> source,
            String description,
            String inputDescription,
            String outputDescription,
            String hint,
            List<Map<String, Object>> sourceSamples,
            String preferredReferenceSolutionLanguage
    ) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "buildTransferSourcePayload",
                Map.class,
                String.class,
                String.class,
                String.class,
                String.class,
                List.class,
                String.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                service,
                source,
                description,
                inputDescription,
                outputDescription,
                hint,
                sourceSamples,
                preferredReferenceSolutionLanguage
        );
    }

    private void invokeValidateGeneratedTransferPayload(Map<String, Object> generated) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "validateGeneratedTransferPayload",
                Map.class
        );
        method.setAccessible(true);
        method.invoke(service, generated);
    }

    private void invokeWriteTransferTestCases(
            AITutorWorkflowAdminServiceImpl localService,
            String testCaseId,
            List<Map<String, Object>> testCases
    ) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "writeTransferTestCases",
                String.class,
                List.class
        );
        method.setAccessible(true);
        method.invoke(localService, testCaseId, testCases);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeGenerateTransferByLlmWithRetryWithHint(String hint) throws Exception {
        Method method = AITutorWorkflowAdminServiceImpl.class.getDeclaredMethod(
                "generateTransferByLlmWithRetry",
                Map.class,
                String.class,
                String.class,
                String.class,
                String.class,
                List.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                service,
                Map.of(
                        "id", 1001L,
                        "_id", "PPT2-1",
                        "title", "2.1 圆面积计算",
                        "languages_json", "[\"Python3\"]"
                ),
                "描述",
                "输入",
                "输出",
                hint,
                List.of(Map.of("input", "1", "output", "1")),
                "Python3",
                ""
        );
    }

    private Object invokeUserAuth(boolean authenticated, Long userId, boolean admin, boolean adminManager) {
        try {
            Class<?> clazz = Class.forName("com.alethicode.service.aitutor.impl.AITutorWorkflowAdminServiceImpl$UserAuth");
            var constructor = clazz.getDeclaredConstructor(boolean.class, Long.class, boolean.class, boolean.class, boolean.class, Set.class);
            constructor.setAccessible(true);
            return constructor.newInstance(authenticated, userId, admin, adminManager, false, Set.of());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private LearnerState learnerStateFromMetrics(Map<String, Object> metrics) {
        int consecutiveErrors = ((Number) metrics.getOrDefault("consecutiveErrors", 0)).intValue();
        String frustration = consecutiveErrors >= 5 ? "severe" : "low";
        return new LearnerState(true, Map.of(), List.of(), Map.of(),
                metrics, frustration, "high", Map.of(), List.of(), "", true);
    }
}

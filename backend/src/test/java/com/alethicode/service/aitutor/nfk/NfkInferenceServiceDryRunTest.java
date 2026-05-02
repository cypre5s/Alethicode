package com.alethicode.service.aitutor.nfk;

import com.alethicode.config.AlethicodeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 ONNX 模型的 dry-run 集成测试。
 *
 * <p>该测试仅在 {@code combined_outputs/nfk_outputs/onnx/alethicode_nfk.onnx} 真实存在时启用
 * ——CI 若无模型文件会自动跳过；本地执行可以校验 Java onnxruntime 侧的输入/输出 shape、
 * dtype 与 Python 训练侧完全一致，避免阶段 3.3 接入后盲区崩溃。
 */
class NfkInferenceServiceDryRunTest {

    private static final Path DEFAULT_MODEL_PATH = Path.of(
            System.getProperty("user.dir"),
            "../combined_outputs/nfk_outputs/onnx/alethicode_nfk.onnx"
    ).normalize();

    private AlethicodeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AlethicodeProperties();
        properties.getNfk().setEnabled(true);
        properties.getNfk().setModelPath(DEFAULT_MODEL_PATH.toString());
    }

    static boolean modelExists() {
        return Files.isRegularFile(DEFAULT_MODEL_PATH);
    }

    @Test
    @EnabledIf("modelExists")
    void loadsModelAndExposesExpectedInputSchema() {
        NfkInferenceService service = new NfkInferenceService(properties);
        service.init();
        try {
            assertThat(service.isAvailable()).isTrue();
            assertThat(service.inputNames())
                    .containsExactlyInAnyOrder("question_ids", "skill_ids", "responses", "delta_t", "pad_mask");
            assertThat(service.outputNames()).first().asString().isEqualTo("kt_pred");
        } finally {
            service.shutdown();
        }
    }

    @Test
    @EnabledIf("modelExists")
    void predictPerSkillReturnsMasteryInZeroOneRange() {
        NfkInferenceService service = new NfkInferenceService(properties);
        service.init();
        try {
            List<NfkInferenceService.NfkInteraction> sequence = List.of(
                    new NfkInferenceService.NfkInteraction(101L, 7L, 1, 1_712_000_000.0),
                    new NfkInferenceService.NfkInteraction(102L, 7L, 0, 1_712_001_000.0),
                    new NfkInferenceService.NfkInteraction(103L, 9L, 1, 1_712_002_000.0),
                    new NfkInferenceService.NfkInteraction(104L, 9L, 1, 1_712_003_000.0)
            );

            Map<Long, Double> result = service.predictPerSkill(sequence);

            assertThat(result).containsKeys(7L, 9L);
            for (Map.Entry<Long, Double> entry : result.entrySet()) {
                assertThat(entry.getValue())
                        .as("mastery of skill %s must fall in [0,1]", entry.getKey())
                        .isBetween(0.0, 1.0);
            }
        } finally {
            service.shutdown();
        }
    }

    @Test
    @EnabledIf("modelExists")
    void predictForSkillsFiltersToRequestedSubset() {
        NfkInferenceService service = new NfkInferenceService(properties);
        service.init();
        try {
            List<NfkInferenceService.NfkInteraction> sequence = List.of(
                    new NfkInferenceService.NfkInteraction(101L, 7L, 1, 1_712_000_000.0),
                    new NfkInferenceService.NfkInteraction(102L, 9L, 0, 1_712_001_000.0)
            );

            Map<Long, Double> filtered = service.predictForSkills(sequence, List.of(7L));

            assertThat(filtered).containsOnlyKeys(7L);
        } finally {
            service.shutdown();
        }
    }
}

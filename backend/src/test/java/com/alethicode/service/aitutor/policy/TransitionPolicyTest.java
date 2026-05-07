package com.alethicode.service.aitutor.policy;

import com.alethicode.exception.LegacyBusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * 工作流阶段转换校验规则测试。
 */

class TransitionPolicyTest {

    private final TransitionPolicy policy = new TransitionPolicy();

    @Test
    void shouldAllowReadingToCodingTransition() {
        assertThatCode(() -> policy.validateOrThrow("READING", "CODING", "", Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowIdeatingToCodingTransition() {
        assertThatCode(() -> policy.validateOrThrow(
                "IDEATING",
                "CODING",
                "",
                Map.of()
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInvalidCheckpointRestorePhase() {
        assertThatThrownBy(() -> policy.validateCheckpointRestoreOrThrow("READING", "HACKED", ""))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessage("Illegal workflow checkpoint restore: READING -> HACKED");
    }
}

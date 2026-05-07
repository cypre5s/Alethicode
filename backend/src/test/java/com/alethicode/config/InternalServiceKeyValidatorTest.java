package com.alethicode.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link InternalServiceKeyValidator} 的启动护栏测试。
 *
 * <ul>
 *   <li>dev profile 使用弱默认密钥时只告警，不阻断启动。</li>
 *   <li>prod profile 使用空值、默认值或短密钥时必须抛错。</li>
 *   <li>prod profile 使用强密钥时允许启动。</li>
 * </ul>
 */
class InternalServiceKeyValidatorTest {

    private final InternalServiceKeyValidator validator = new InternalServiceKeyValidator();

    @Test
    void devProfile_withDefaultKey_allowsStartup() throws Exception {
        ApplicationRunner runner = validator.validateInternalServiceKey("dev-internal-key", "", env("dev"));
        runner.run(mockArgs());
    }

    @Test
    void devProfile_withBlankKey_allowsStartup() throws Exception {
        ApplicationRunner runner = validator.validateInternalServiceKey("", "", env("default"));
        runner.run(mockArgs());
    }

    @Test
    void prodProfile_withBlankKey_fails() {
        ApplicationRunner runner = validator.validateInternalServiceKey("", "", env("prod"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> runner.run(mockArgs()))
                .withMessageContaining("non-empty");
    }

    @Test
    void prodProfile_withDevDefault_fails() {
        ApplicationRunner runner = validator.validateInternalServiceKey("dev-internal-key", "", env("prod"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> runner.run(mockArgs()))
                .withMessageContaining("dev default");
    }

    @Test
    void prodProfile_withShortKey_fails() {
        ApplicationRunner runner = validator.validateInternalServiceKey("short-key", "", env("prod"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> runner.run(mockArgs()))
                .withMessageContaining("24 characters");
    }

    @Test
    void prodProfile_withStrongKey_allowsStartup() throws Exception {
        ApplicationRunner runner = validator.validateInternalServiceKey(
                "some-very-strong-random-key-of-substantial-length-xyz", "", env("production"));
        runner.run(mockArgs());
    }

    @Test
    void prodProfile_withStrongPreviousKey_allowsRollingWindow() throws Exception {
        ApplicationRunner runner = validator.validateInternalServiceKey(
                "current-very-strong-random-key-abcdef",
                "previous-very-strong-random-key-ghijk",
                env("prod"));
        runner.run(mockArgs());
    }

    @Test
    void prodProfile_withWeakPreviousKey_failsBecauseItIsAcceptedInbound() {
        ApplicationRunner runner = validator.validateInternalServiceKey(
                "current-very-strong-random-key-abcdef",
                "short-previous",
                env("prod"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> runner.run(mockArgs()))
                .withMessageContaining("previous-service-key")
                .withMessageContaining("24 characters");
    }

    @Test
    void prodProfile_withPreviousEqualCurrent_fails() {
        String key = "same-very-strong-random-key-abcdef";
        ApplicationRunner runner = validator.validateInternalServiceKey(key, key, env("prod"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> runner.run(mockArgs()))
                .withMessageContaining("must differ");
    }

    @Test
    void releaseProfile_isAlsoProdLike() {
        ApplicationRunner runner = validator.validateInternalServiceKey("dev-internal-key", "", env("release"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> runner.run(mockArgs()));
    }

    private static Environment env(String activeProfile) {
        Environment e = mock(Environment.class);
        when(e.getActiveProfiles()).thenReturn(new String[]{activeProfile});
        return e;
    }

    private static ApplicationArguments mockArgs() {
        return mock(ApplicationArguments.class);
    }

    /** 只验证 bean 定义存在。 */
    @Test
    void beanDefinition_returnsApplicationRunner() {
        ApplicationRunner runner = validator.validateInternalServiceKey(
                "key-that-is-long-enough-abcdef", "", env("prod"));
        assertThat(runner).isNotNull();
    }
}

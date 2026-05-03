package com.alethicode.service.twin.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldSettingServiceTest {

    private final WorldSettingService service = new WorldSettingService(null);

    @Test
    void isValidThemeAcceptsAllSixThemes() {
        assertThat(service.isValidTheme("academy")).isTrue();
        assertThat(service.isValidTheme("forest")).isTrue();
        assertThat(service.isValidTheme("sunset")).isTrue();
        assertThat(service.isValidTheme("galaxy")).isTrue();
        assertThat(service.isValidTheme("ocean")).isTrue();
        assertThat(service.isValidTheme("sakura")).isTrue();
    }

    @Test
    void isValidThemeRejectsInvalid() {
        assertThat(service.isValidTheme("rainbow")).isFalse();
        assertThat(service.isValidTheme("")).isFalse();
        assertThat(service.isValidTheme(null)).isFalse();
    }

    @Test
    void getWorldSettingDefaultsOnNullJdbc() {
        var defaults = service.getWorldSetting(1L);
        assertThat(defaults.get("world_name")).isEqualTo("编程学院");
        assertThat(defaults.get("theme_id")).isEqualTo("academy");
    }
}

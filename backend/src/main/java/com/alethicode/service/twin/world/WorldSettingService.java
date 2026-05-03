package com.alethicode.service.twin.world;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class WorldSettingService {

    private static final Set<String> VALID_THEMES = Set.of(
            "academy", "forest", "sunset", "galaxy", "ocean", "sakura"
    );

    private final JdbcTemplate jdbcTemplate;

    public WorldSettingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getWorldSetting(Long userId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT world_name, world_narrative, theme_id, custom_palette FROM twin_world_setting WHERE user_id = ?",
                    userId);
        } catch (Exception e) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("world_name", "编程学院");
            defaults.put("world_narrative", null);
            defaults.put("theme_id", "academy");
            defaults.put("custom_palette", null);
            return defaults;
        }
    }

    public void updateWorldSetting(Long userId, String worldName, String narrative, String themeId) {
        String safeTheme = VALID_THEMES.contains(themeId) ? themeId : "academy";
        jdbcTemplate.update("""
            INSERT INTO twin_world_setting (user_id, world_name, world_narrative, theme_id)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id)
            DO UPDATE SET world_name = EXCLUDED.world_name,
                world_narrative = EXCLUDED.world_narrative,
                theme_id = EXCLUDED.theme_id,
                updated_at = NOW()
            """, userId, worldName, narrative, safeTheme);
    }

    public boolean isValidTheme(String themeId) {
        return VALID_THEMES.contains(themeId);
    }
}

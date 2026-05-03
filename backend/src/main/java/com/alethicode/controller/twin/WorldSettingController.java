package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/twin/world-setting")
public class WorldSettingController {

    private final JdbcTemplate jdbcTemplate;

    public WorldSettingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getWorldSetting(Authentication authentication) {
        Long userId = requireUserId(authentication);
        try {
            Map<String, Object> setting = jdbcTemplate.queryForMap(
                    "SELECT world_name, world_narrative, theme_id, custom_palette FROM twin_world_setting WHERE user_id = ?",
                    userId);
            return ApiResponse.success(setting);
        } catch (Exception e) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("world_name", "编程学院");
            defaults.put("world_narrative", null);
            defaults.put("theme_id", "academy");
            defaults.put("custom_palette", null);
            return ApiResponse.success(defaults);
        }
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> updateWorldSetting(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String worldName = body.getOrDefault("world_name", "编程学院");
        String narrative = body.get("world_narrative");
        String themeId = body.getOrDefault("theme_id", "academy");

        jdbcTemplate.update("""
            INSERT INTO twin_world_setting (user_id, world_name, world_narrative, theme_id)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id)
            DO UPDATE SET world_name = EXCLUDED.world_name,
                world_narrative = EXCLUDED.world_narrative,
                theme_id = EXCLUDED.theme_id,
                updated_at = NOW()
            """, userId, worldName, narrative, themeId);

        return ApiResponse.success(Map.of("ok", true));
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}

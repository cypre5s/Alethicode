package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.career.preference.CareerPreferenceService;
import com.alethicode.service.career.preference.CareerPreferenceService.CareerPreferences;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 学生「我的」面板下的 Career 模块开关入口（plan 9.3 节 + todo 15）。
 *
 * <ul>
 *   <li>{@code GET /api/career/preferences} —— 当前学生 4 个模块开关读取；
 *       未登录抛 401。</li>
 *   <li>{@code PUT /api/career/preferences} —— 4 个模块开关更新；body 接受
 *       camelCase 与 snake_case 两种字段名（与全局 Jackson 策略对齐）。</li>
 * </ul>
 */
@RestController
@RequestMapping
public class CareerPreferenceController {

    private final CareerPreferenceService preferenceService;

    public CareerPreferenceController(CareerPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping({"/api/career/preferences", "/api/career/preferences/"})
    public ApiResponse<CareerPreferences> getPreferences(Authentication auth) {
        long userId = requireUserId(auth);
        return ApiResponse.success(preferenceService.findPreferences(userId));
    }

    @PutMapping({"/api/career/preferences", "/api/career/preferences/"})
    public ApiResponse<CareerPreferences> updatePreferences(
            @RequestBody Map<String, Object> body,
            Authentication auth
    ) {
        long userId = requireUserId(auth);
        CareerPreferences prefs = parsePreferences(body);
        preferenceService.updatePreferences(userId, prefs);
        return ApiResponse.success(prefs);
    }

    private static long requireUserId(Authentication auth) {
        Long userId = AuthUserResolver.currentUserIdOrNull(auth);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }

    /**
     * 同时接受 camelCase 与 snake_case 字段名：例如
     * {@code career_bridging_disabled} 与 {@code careerBridgingDisabled}
     * 都映射到同一字段。未提供的字段默认 false（启用）。
     */
    private static CareerPreferences parsePreferences(Map<String, Object> body) {
        if (body == null) {
            return CareerPreferences.allEnabled();
        }
        return new CareerPreferences(
                readBool(body, "career_bridging_disabled", "careerBridgingDisabled"),
                readBool(body, "coding_lens_disabled", "codingLensDisabled"),
                readBool(body, "career_studio_disabled", "careerStudioDisabled"),
                readBool(body, "career_path_disabled", "careerPathDisabled")
        );
    }

    private static boolean readBool(Map<String, Object> body, String snakeKey, String camelKey) {
        Object value = body.get(snakeKey);
        if (value == null) {
            value = body.get(camelKey);
        }
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

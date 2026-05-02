package com.alethicode.dto.request;

/**
 * 学生侧画像偏好更新请求。
 *
 * personalizationEnabled=null 表示不修改该字段；非 null 时按布尔值切换学生侧个性化开关。
 */
public record UpdateProfilePreferencesRequest(
        Boolean personalizationEnabled
) {
}

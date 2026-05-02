package com.alethicode.dto.request;

public record EditUserProfileRequest(
        String realName,
        String avatar,
        String blog,
        String mood,
        String github,
        String school,
        String major,
        String language
) {
}

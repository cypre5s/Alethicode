package com.alethicode.dto.request;

public record AnnouncementCreateRequest(
        String title,
        String content,
        Boolean visible
) {
}

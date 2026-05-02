package com.alethicode.dto.request;

public record AnnouncementEditRequest(
        Long id,
        String title,
        String content,
        Boolean visible
) {
}

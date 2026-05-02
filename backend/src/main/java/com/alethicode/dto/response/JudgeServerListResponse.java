package com.alethicode.dto.response;

import java.util.List;

public record JudgeServerListResponse(
        String token,
        List<JudgeServerItemResponse> servers
) {
}

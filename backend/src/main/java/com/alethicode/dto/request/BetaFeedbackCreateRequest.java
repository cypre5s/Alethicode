package com.alethicode.dto.request;

import java.util.List;
import java.util.Map;

/**
 * 学生公测反馈提交请求体。除了 type/severity 是枚举白名单外，其余字段均可缺省，
 * description 上限 2000 字（在 service 层校验）。
 */
public record BetaFeedbackCreateRequest(
        String type,
        String severity,
        String description,
        String route,
        Long problemId,
        Long submissionId,
        String workflowSessionId,
        Map<String, Object> browserMeta,
        List<Map<String, Object>> recentActions,
        Boolean wjxFollowupOpened,
        String privacyNoticeVersion
) {
}

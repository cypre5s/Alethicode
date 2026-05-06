package com.alethicode.dto.response;

import com.alethicode.service.career.bridging.CareerBridgingReport;

/**
 * POST /api/career/profile 的响应体。
 *
 * <p>{@code report} 在以下情况为 null：(a) 调用方设置 {@code auto_generate=false}；
 * (b) A/B 分组分到 control 组；(c) 当前里程碑早已被消费过。
 * 调用方需根据 {@code newly_enrolled} + {@code milestone_id} 判断后续是否
 * 还需要主动调 {@code POST /api/career/milestones/{milestoneId}/reports}。
 */
public record CareerEnrollmentResponse(
        boolean newlyEnrolled,
        long milestoneId,
        String majorCode,
        CareerBridgingReport report
) {
}

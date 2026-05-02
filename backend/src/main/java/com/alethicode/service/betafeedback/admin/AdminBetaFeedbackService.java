package com.alethicode.service.betafeedback.admin;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

/**
 * 公测反馈管理员侧服务。所有方法都假定调用方已通过 {@code @PreAuthorize("hasRole('ADMIN')")}。
 */
public interface AdminBetaFeedbackService {

    Set<String> ALLOWED_STATUSES = Set.of("pending", "triaging", "fixing", "resolved", "wontfix");

    /**
     * 分页 + 状态/严重度/类型筛选反馈列表。
     */
    Map<String, Object> listReports(int offset, int limit, String status, String severity, String type);

    /**
     * 单条反馈详情，含附件元信息。
     */
    Map<String, Object> getReport(long id);

    /**
     * 修改反馈状态；transition 到 resolved/wontfix 时同时写 resolved_at。
     */
    void updateStatus(long id, String newStatus);

    /**
     * 流式返回截图二进制。Content-Type 跟随附件本身的 mime；找不到 reportId 或 attachmentId 抛 BadRequestException。
     */
    ResponseEntity<byte[]> streamScreenshot(long reportId, long attachmentId);
}

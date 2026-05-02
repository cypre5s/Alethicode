package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.betafeedback.admin.AdminBetaFeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公测反馈管理后台 API。所有路径都受 {@code /api/admin/**} 的 {@code hasRole('ADMIN')} 保护，
 * 此处再叠一层 {@code @PreAuthorize} 保险，禁止 Teacher 角色访问。
 */
@RestController
@RequestMapping("/api/admin/beta")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBetaFeedbackController {

    private final AdminBetaFeedbackService adminService;

    public AdminBetaFeedbackController(AdminBetaFeedbackService adminService) {
        this.adminService = adminService;
    }

    @GetMapping({"/feedback-reports", "/feedback-reports/"})
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String type
    ) {
        return ApiResponse.success(adminService.listReports(offset, limit, status, severity, type));
    }

    @GetMapping("/feedback-reports/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        return ApiResponse.success(adminService.getReport(id));
    }

    @PatchMapping("/feedback-reports/{id}")
    public ApiResponse<Void> updateStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Object statusValue = body == null ? null : body.get("status");
        String newStatus = statusValue == null ? null : String.valueOf(statusValue);
        adminService.updateStatus(id, newStatus);
        return ApiResponse.success(null);
    }

    @GetMapping("/feedback-reports/{reportId}/screenshots/{attachmentId}")
    public ResponseEntity<byte[]> getScreenshot(
            @PathVariable long reportId,
            @PathVariable long attachmentId
    ) {
        return adminService.streamScreenshot(reportId, attachmentId);
    }
}

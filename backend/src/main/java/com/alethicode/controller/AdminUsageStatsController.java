package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.usagestats.UsageStatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公测期使用统计后台 API。所有路径受 {@code /api/admin/**} 的 {@code hasRole('ADMIN')} 保护，
 * 此处再叠一层 {@code @PreAuthorize} 保险，禁止 Teacher 角色访问。
 */
@RestController
@RequestMapping("/api/admin/usage-stats")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsageStatsController {

    private final UsageStatsService service;

    public AdminUsageStatsController(UsageStatsService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam(defaultValue = "7d") String range
    ) {
        return ApiResponse.success(service.getStats(range));
    }
}

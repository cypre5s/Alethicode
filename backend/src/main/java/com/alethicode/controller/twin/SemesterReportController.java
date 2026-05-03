package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.report.SemesterReportService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/twin/semester-report")
public class SemesterReportController {

    private final SemesterReportService reportService;

    public SemesterReportController(SemesterReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> generateReport(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String semesterLabel = body.get("semester_label");
        if (semesterLabel == null || semesterLabel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semester_label required");
        }
        String themeSkin = body.getOrDefault("theme_skin", "default");
        return ApiResponse.success(reportService.generateReport(userId, semesterLabel, themeSkin));
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}

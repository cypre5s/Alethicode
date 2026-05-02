package com.alethicode.controller.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomAnalyticsService;
import com.alethicode.service.monitor.StudentRiskDetectionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classroom/{classroomId}/analytics")
public class ClassroomAnalyticsController {

    private final ClassroomAnalyticsService classroomAnalyticsService;
    private final StudentRiskDetectionService studentRiskDetectionService;

    public ClassroomAnalyticsController(ClassroomAnalyticsService classroomAnalyticsService,
                                         StudentRiskDetectionService studentRiskDetectionService) {
        this.classroomAnalyticsService = classroomAnalyticsService;
        this.studentRiskDetectionService = studentRiskDetectionService;
    }

    @GetMapping({"weekly-pulse", "weekly-pulse/"})
    public ApiResponse<Map<String, Object>> weeklyPulse(
            @PathVariable String classroomId,
            @RequestParam(defaultValue = "week") String range,
            Authentication authentication) {
        return ApiResponse.success(classroomAnalyticsService.getWeeklyPulse(classroomId, range, authentication));
    }

    @GetMapping({"kc-heatmap", "kc-heatmap/"})
    public ApiResponse<Map<String, Object>> kcHeatmap(
            @PathVariable String classroomId, Authentication authentication) {
        return ApiResponse.success(classroomAnalyticsService.getKcMasteryHeatmap(classroomId, authentication));
    }

    @GetMapping({"weak-kc-suggestions", "weak-kc-suggestions/"})
    public ApiResponse<Map<String, Object>> weakKcSuggestions(
            @PathVariable String classroomId, Authentication authentication) {
        return ApiResponse.success(classroomAnalyticsService.getWeakKcSuggestions(classroomId, authentication));
    }

    @GetMapping({"courseware-usage", "courseware-usage/"})
    public ApiResponse<Map<String, Object>> coursewareUsage(
            @PathVariable String classroomId, Authentication authentication) {
        return ApiResponse.success(classroomAnalyticsService.getCoursewareUsage(classroomId, authentication));
    }

    @GetMapping({"risk-students", "risk-students/"})
    public ApiResponse<List<Map<String, Object>>> riskStudents(
            @PathVariable String classroomId, Authentication authentication) {
        return ApiResponse.success(studentRiskDetectionService.getEnhancedRiskList(classroomId, authentication));
    }

    @GetMapping({"weekly-report", "weekly-report/"})
    public ApiResponse<Map<String, Object>> weeklyReport(
            @PathVariable String classroomId, Authentication authentication) {
        return ApiResponse.success(classroomAnalyticsService.generateWeeklyReport(classroomId, authentication));
    }

    @GetMapping({"risk-students/{userId}/advice", "risk-students/{userId}/advice/"})
    public ApiResponse<Map<String, Object>> riskStudentAdvice(
            @PathVariable String classroomId, @PathVariable Long userId, Authentication authentication) {
        return ApiResponse.success(studentRiskDetectionService.generateInterventionAdvice(classroomId, userId, authentication));
    }

    @GetMapping({"student/{userId}/profile", "student/{userId}/profile/"})
    public ApiResponse<Map<String, Object>> studentProfile(
            @PathVariable String classroomId, @PathVariable Long userId, Authentication authentication) {
        return ApiResponse.success(classroomAnalyticsService.getStudentProfile(classroomId, userId, authentication));
    }
}

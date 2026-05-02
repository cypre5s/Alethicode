package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.languagepack.ContentImprovementService;
import com.alethicode.service.classroom.CourseInsightService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminCourseInsightController {

    private final CourseInsightService courseInsightService;
    private final ContentImprovementService contentImprovementService;
    private final JdbcTemplate jdbcTemplate;

    public AdminCourseInsightController(CourseInsightService courseInsightService,
                                         ContentImprovementService contentImprovementService,
                                         JdbcTemplate jdbcTemplate) {
        this.courseInsightService = courseInsightService;
        this.contentImprovementService = contentImprovementService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping({"/api/admin/insight/classroom/{classroomId}/mastery", "/api/admin/insight/classroom/{classroomId}/mastery/"})
    public ApiResponse<List<Map<String, Object>>> getClassMastery(
            Authentication auth, @PathVariable String classroomId) {
        return ApiResponse.success(courseInsightService.getClassMasteryDistribution(classroomId));
    }

    @GetMapping({"/api/admin/insight/classroom/{classroomId}/weak-points", "/api/admin/insight/classroom/{classroomId}/weak-points/"})
    public ApiResponse<List<Map<String, Object>>> getCommonWeakPoints(
            Authentication auth, @PathVariable String classroomId) {
        return ApiResponse.success(courseInsightService.getCommonWeakPoints(classroomId));
    }

    @GetMapping({"/api/admin/insight/classroom/{classroomId}/risk-students", "/api/admin/insight/classroom/{classroomId}/risk-students/"})
    public ApiResponse<List<Map<String, Object>>> getStudentRiskList(
            Authentication auth, @PathVariable String classroomId) {
        return ApiResponse.success(courseInsightService.getStudentRiskList(classroomId));
    }

    @GetMapping({"/api/admin/insight/mastery-heatmap", "/api/admin/insight/mastery-heatmap/"})
    public ApiResponse<List<Map<String, Object>>> getMasteryHeatmap(
            Authentication auth,
            @RequestParam("classroom_id") String classroomId,
            @RequestParam("language_pack_id") Long languagePackId) {
        return ApiResponse.success(courseInsightService.getStudentKcMasteryMatrix(classroomId, languagePackId));
    }

    @GetMapping({"/api/admin/insight/error-ranking", "/api/admin/insight/error-ranking/"})
    public ApiResponse<List<Map<String, Object>>> getErrorRanking(
            Authentication auth,
            @RequestParam("classroom_id") String classroomId,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        return ApiResponse.success(courseInsightService.getErrorPatternRanking(classroomId, days));
    }

    @GetMapping({"/api/admin/insight/intervention-effect", "/api/admin/insight/intervention-effect/"})
    public ApiResponse<Map<String, Object>> getInterventionEffect(
            Authentication auth,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        return ApiResponse.success(courseInsightService.getInterventionEffect(days));
    }

    @GetMapping({"/api/admin/insight/classrooms", "/api/admin/insight/classrooms/"})
    public ApiResponse<List<Map<String, Object>>> listClassrooms(Authentication auth) {
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT id, name FROM classroom ORDER BY name"));
    }

    @GetMapping({"/api/admin/insight/content/{languagePackId}/effectiveness", "/api/admin/insight/content/{languagePackId}/effectiveness/"})
    public ApiResponse<Map<String, Object>> getContentEffectiveness(
            Authentication auth, @PathVariable Long languagePackId) {
        return ApiResponse.success(courseInsightService.getContentEffectiveness(languagePackId));
    }

    @GetMapping({"/api/admin/insight/content/{languagePackId}/improvements", "/api/admin/insight/content/{languagePackId}/improvements/"})
    public ApiResponse<Map<String, Object>> getImprovementSuggestions(
            Authentication auth, @PathVariable Long languagePackId) {
        return ApiResponse.success(contentImprovementService.getImprovementSuggestions(languagePackId));
    }
}

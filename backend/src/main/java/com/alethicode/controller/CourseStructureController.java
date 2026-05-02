package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.languagepack.CourseStructureService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class CourseStructureController {

    private final CourseStructureService courseStructureService;

    public CourseStructureController(CourseStructureService courseStructureService) {
        this.courseStructureService = courseStructureService;
    }

    @GetMapping({"/api/language-pack/{id}/course-structure", "/api/language-pack/{id}/course-structure/"})
    public ApiResponse<Map<String, Object>> getCourseStructure(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ApiResponse.success(courseStructureService.getCourseStructure(id));
    }

    @GetMapping({"/api/language-pack/{id}/kc-graph", "/api/language-pack/{id}/kc-graph/"})
    public ApiResponse<Map<String, Object>> getKcGraph(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ApiResponse.success(courseStructureService.getKcGraph(id));
    }
}

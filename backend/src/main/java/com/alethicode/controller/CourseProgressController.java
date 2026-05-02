package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.path.DifficultyCalibrationService;
import com.alethicode.service.aitutor.path.LearningPathOptimizerService;
import com.alethicode.service.aitutor.path.MasteryAdaptiveProblemSelector;
import com.alethicode.service.classroom.LearnerCourseProgressService;
import com.alethicode.service.problem.RelatedExampleQueryService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class CourseProgressController {

    private final LearnerCourseProgressService progressService;
    private final RelatedExampleQueryService relatedExampleQueryService;
    private final LearningPathOptimizerService learningPathOptimizerService;
    private final MasteryAdaptiveProblemSelector masteryAdaptiveProblemSelector;
    private final DifficultyCalibrationService difficultyCalibrationService;

    public CourseProgressController(LearnerCourseProgressService progressService,
                                    RelatedExampleQueryService relatedExampleQueryService,
                                    LearningPathOptimizerService learningPathOptimizerService,
                                    MasteryAdaptiveProblemSelector masteryAdaptiveProblemSelector,
                                    DifficultyCalibrationService difficultyCalibrationService) {
        this.progressService = progressService;
        this.relatedExampleQueryService = relatedExampleQueryService;
        this.learningPathOptimizerService = learningPathOptimizerService;
        this.masteryAdaptiveProblemSelector = masteryAdaptiveProblemSelector;
        this.difficultyCalibrationService = difficultyCalibrationService;
    }

    @GetMapping({"/api/course-progress/{languagePackId}", "/api/course-progress/{languagePackId}/"})
    public ApiResponse<Map<String, Object>> getCourseProgress(
            Authentication auth, @PathVariable Long languagePackId) {
        Long userId = AuthUserResolver.currentUserIdOrNull(auth);
        if (userId == null) {
            throw BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        return ApiResponse.success(progressService.getOrCreateProgress(userId, languagePackId));
    }

    @GetMapping({"/api/problems/{problemId}/related-examples", "/api/problems/{problemId}/related-examples/"})
    public ApiResponse<List<Map<String, Object>>> getRelatedExamples(@PathVariable Long problemId) {
        return ApiResponse.success(relatedExampleQueryService.findByProblemId(problemId));
    }

    @GetMapping({"/api/learning-path", "/api/learning-path/"})
    public ApiResponse<Map<String, Object>> getLearningPath(
            Authentication auth,
            @RequestParam("language_pack_id") Long languagePackId) {
        Long userId = AuthUserResolver.currentUserIdOrNull(auth);
        return ApiResponse.success(learningPathOptimizerService.computePath(userId, languagePackId));
    }

    @GetMapping({"/api/recommend/next-problem", "/api/recommend/next-problem/"})
    public ApiResponse<Map<String, Object>> recommendNextProblem(
            Authentication auth,
            @RequestParam("language_pack_id") Long languagePackId) {
        Long userId = AuthUserResolver.currentUserIdOrNull(auth);
        if (userId == null) {
            throw BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        return ApiResponse.success(masteryAdaptiveProblemSelector.selectNextProblem(userId, languagePackId));
    }

    @PostMapping({"/api/admin/calibrate-difficulty/{languagePackId}", "/api/admin/calibrate-difficulty/{languagePackId}/"})
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> calibrateDifficulty(@PathVariable Long languagePackId) {
        return ApiResponse.success(difficultyCalibrationService.calibrateByLanguagePack(languagePackId));
    }
}

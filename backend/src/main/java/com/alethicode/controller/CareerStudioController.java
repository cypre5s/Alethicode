package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.career.studio.MicroProjectStudioService;
import com.alethicode.service.career.studio.MicroProjectStudioService.CareerMicroProject;
import com.alethicode.service.career.studio.MicroProjectStudioService.MicroProjectRecommendation;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Project Studio REST 入口（plan 5.3 节）。
 *
 * <p>暴露 5 个端点：
 * <ul>
 *   <li>{@code GET /api/career/studio/recommendations} —— 推荐 KC 簇候选。</li>
 *   <li>{@code POST /api/career/studio/projects} —— 生成微项目（含真判题自验证）。</li>
 *   <li>{@code GET /api/career/studio/projects?limit=5} —— 学生最近 N 个微项目。</li>
 *   <li>{@code GET /api/career/studio/projects/{id}} —— 单个微项目详情。</li>
 *   <li>{@code POST /api/career/studio/projects/{id}/portfolio-card} —— 渲染并写回
 *       作品集卡片 Markdown（plan 5.3 节，写盘到 {@code data/exports/career-portfolio/}
 *       并把 file URI 写回 {@code career_micro_project.portfolio_card_uri} 列）。</li>
 * </ul>
 *
 * <p>所有端点未登录抛 401；资源属主校验下沉到 service 层（user_id 双键 SQL）。
 */
@RestController
@RequestMapping("/api/career/studio")
public class CareerStudioController {

    private final MicroProjectStudioService studioService;

    public CareerStudioController(MicroProjectStudioService studioService) {
        this.studioService = studioService;
    }

    @GetMapping({"/recommendations", "/recommendations/"})
    public ApiResponse<List<MicroProjectRecommendation>> recommendations(Authentication auth) {
        long userId = requireUserId(auth);
        return ApiResponse.success(studioService.recommendForUser(userId));
    }

    @SuppressWarnings("unchecked")
    @PostMapping({"/projects", "/projects/"})
    public ApiResponse<CareerMicroProject> generate(
            @RequestBody Map<String, Object> body,
            Authentication auth
    ) {
        long userId = requireUserId(auth);
        String majorCode = String.valueOf(body.getOrDefault("major_code", ""));
        List<String> kcCodes = (List<String>) body.getOrDefault("kc_codes", List.of());
        Optional<CareerMicroProject> result = studioService.generate(userId, majorCode, kcCodes);
        return result.<ApiResponse<CareerMicroProject>>map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("generation_failed", null));
    }

    @GetMapping({"/projects", "/projects/"})
    public ApiResponse<List<CareerMicroProject>> listProjects(
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            Authentication auth
    ) {
        long userId = requireUserId(auth);
        return ApiResponse.success(studioService.listForUser(userId, limit));
    }

    @GetMapping({"/projects/{projectId}", "/projects/{projectId}/"})
    public ApiResponse<CareerMicroProject> getProject(
            @PathVariable long projectId,
            Authentication auth
    ) {
        long userId = requireUserId(auth);
        return studioService.findById(userId, projectId)
                .<ApiResponse<CareerMicroProject>>map(ApiResponse::success)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "micro project not found or not owned by user: id=" + projectId));
    }

    @PostMapping({"/projects/{projectId}/portfolio-card", "/projects/{projectId}/portfolio-card/"})
    public ApiResponse<CareerMicroProject> exportPortfolioCard(
            @PathVariable long projectId,
            Authentication auth
    ) {
        long userId = requireUserId(auth);
        return ApiResponse.success(studioService.exportPortfolioCard(userId, projectId));
    }

    private static long requireUserId(Authentication auth) {
        Long userId = AuthUserResolver.currentUserIdOrNull(auth);
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        return userId;
    }
}

package com.alethicode.controller;

import com.alethicode.dto.request.CareerProfileRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.CareerEnrollmentResponse;
import com.alethicode.dto.response.CareerMajorOption;
import com.alethicode.dto.response.CareerProfileView;
import com.alethicode.service.career.bridging.CareerBridgingReport;
import com.alethicode.service.career.bridging.CareerBridgingService;
import com.alethicode.service.career.bridging.CareerBridgingService.EnrollmentResult;
import com.alethicode.util.AuthUserResolver;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Career Bridging 模块 REST 入口（plan 3.5 节）。
 *
 * <p>暴露 5 个端点：
 * <ul>
 *   <li>{@code GET /api/career/majors} —— 专业字典下拉，注册流前置依赖；</li>
 *   <li>{@code GET /api/career/profile} —— 当前学生 Career 档案视图；</li>
 *   <li>{@code PUT /api/career/profile} —— 写专业 + 学习目标；首次填触发
 *       enrollment 里程碑，{@code auto_generate=true}（默认）时立即触发 LLM
 *       生成 Why 报告（A/B treatment 组才真生成，control 组只消费里程碑）；</li>
 *   <li>{@code POST /api/career/milestones/{milestoneId}/reports} —— 拆开两步走时手动触发；</li>
 *   <li>{@code GET /api/career/reports?limit=5} —— 主页 CareerProgressCard 拉报告列表。</li>
 * </ul>
 *
 * <p>所有端点未登录返回 {@code error-permission-denied}；资源属主校验在 service
 * 层（loadMilestone / mapReportRow 都基于 user_id 双键）；service 层抛
 * {@code ResponseStatusException} 时由 Spring 默认 ResponseStatus 处理器映射成 4xx/5xx。
 */
@RestController
@RequestMapping
public class CareerController {

    private static final Logger log = LoggerFactory.getLogger(CareerController.class);
    private static final CareerProfileView EMPTY_PROFILE_VIEW =
            new CareerProfileView(null, "", null, null);

    private final CareerBridgingService careerBridgingService;

    public CareerController(CareerBridgingService careerBridgingService) {
        this.careerBridgingService = careerBridgingService;
    }

    @GetMapping({"/api/career/majors", "/api/career/majors/"})
    public ApiResponse<List<CareerMajorOption>> listMajors() {
        return ApiResponse.success(careerBridgingService.listMajors());
    }

    @GetMapping({"/api/career/profile", "/api/career/profile/"})
    public ApiResponse<CareerProfileView> getProfile(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ApiResponse.error("error-permission-denied");
        }
        return ApiResponse.success(
                careerBridgingService.findProfile(userId).orElse(EMPTY_PROFILE_VIEW));
    }

    @PutMapping({"/api/career/profile", "/api/career/profile/"})
    public ApiResponse<CareerEnrollmentResponse> upsertProfile(
            @Valid @RequestBody CareerProfileRequest request,
            Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ApiResponse.error("error-permission-denied");
        }
        EnrollmentResult result = careerBridgingService.ensureProfile(
                userId, request.majorCode(), request.careerIntent());
        CareerBridgingReport autoReport = null;
        if (request.shouldAutoGenerate()) {
            try {
                autoReport = careerBridgingService.generateForMilestone(
                        userId, result.milestoneId()).orElse(null);
            } catch (ResponseStatusException e) {
                // service 用 ResponseStatusException 表达明确业务错误（如 503 关闭、
                // 404 milestone 已不存在）；profile 已写入，但不能掩盖语义错误
                throw e;
            } catch (Exception e) {
                // 仅吞 LLM/Reflection/IO 层的不确定性失败，让前端通过手动 POST
                // /api/career/milestones/{id}/reports 重试，profile 写入不被阻塞
                log.warn("career bridging auto-generate failed: user={}, milestone={}, reason={}",
                        userId, result.milestoneId(), e.toString());
            }
        }
        return ApiResponse.success(new CareerEnrollmentResponse(
                result.newlyEnrolled(),
                result.milestoneId(),
                result.majorCode(),
                autoReport));
    }

    @PostMapping({
            "/api/career/milestones/{milestoneId}/reports",
            "/api/career/milestones/{milestoneId}/reports/"
    })
    public ApiResponse<CareerBridgingReport> generateReport(@PathVariable long milestoneId,
                                                            Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ApiResponse.error("error-permission-denied");
        }
        Optional<CareerBridgingReport> report =
                careerBridgingService.generateForMilestone(userId, milestoneId);
        return ApiResponse.success(report.orElse(null));
    }

    @GetMapping({"/api/career/reports", "/api/career/reports/"})
    public ApiResponse<List<CareerBridgingReport>> listReports(
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ApiResponse.error("error-permission-denied");
        }
        return ApiResponse.success(careerBridgingService.recentReports(userId, limit));
    }
}

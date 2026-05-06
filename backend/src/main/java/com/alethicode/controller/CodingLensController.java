package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.career.lens.DomainLensService;
import com.alethicode.service.career.lens.ProblemDomainVariant;
import com.alethicode.service.career.preference.CareerPreferenceService;
import com.alethicode.service.career.preference.CareerPreferenceServiceImpl;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Coding Lens REST 入口（plan 4.3 节）。
 *
 * <ul>
 *   <li>{@code GET /api/coding-lens/problems/{problemId}?major={code}} 获取专业化题面变体</li>
 *   <li>{@code POST /api/coding-lens/variants/{variantId}/lock} 教师锁定（考试模式）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/coding-lens")
public class CodingLensController {

    private final DomainLensService domainLensService;
    private final CareerPreferenceService preferenceService;

    public CodingLensController(DomainLensService domainLensService,
                                CareerPreferenceService preferenceService) {
        this.domainLensService = domainLensService;
        this.preferenceService = preferenceService;
    }

    @GetMapping("/problems/{problemId}")
    public ApiResponse<ProblemDomainVariant> getVariant(
            @PathVariable long problemId,
            @RequestParam(name = "major") String majorCode,
            Authentication authentication
    ) {
        long userId = requireUserId(authentication);
        // 用户级关闭面板：学生在「我的」面板里关闭 coding_lens 后，本端点
        // 不再触发 LLM 生成，直接返回 variant_not_available（前端展示原版题面）
        if (preferenceService.isModuleDisabled(userId, CareerPreferenceServiceImpl.MODULE_CODING_LENS)) {
            return ApiResponse.error("variant_not_available", null);
        }
        Optional<ProblemDomainVariant> variant = domainLensService.findOrGenerate(problemId, majorCode, userId);
        return variant.<ApiResponse<ProblemDomainVariant>>map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("variant_not_available", null));
    }

    /**
     * 锁定题面变体（考试模式下不允许 LLM 重新生成）。
     * 必须为 Admin 角色（教师 / 超管）才能调用——普通学生不应触达此端点。
     * 与 {@code AdminLanguagePackController} 等管理端遵循同一 RBAC 范式。
     */
    @PostMapping("/variants/{variantId}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> lockForExam(
            @PathVariable long variantId,
            Authentication authentication
    ) {
        long userId = requireUserId(authentication);
        domainLensService.lockForExam(variantId, userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ProblemDomainVariant>> listVariants(
            @RequestParam(name = "major", required = false) String majorCode,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        return ApiResponse.success(domainLensService.listVariants(majorCode, limit));
    }

    private static long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}

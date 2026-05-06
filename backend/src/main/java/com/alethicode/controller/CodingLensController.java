package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.career.lens.DomainLensService;
import com.alethicode.service.career.lens.ProblemDomainVariant;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    public CodingLensController(DomainLensService domainLensService) {
        this.domainLensService = domainLensService;
    }

    @GetMapping("/problems/{problemId}")
    public ApiResponse<ProblemDomainVariant> getVariant(
            @PathVariable long problemId,
            @RequestParam(name = "major") String majorCode,
            Authentication authentication
    ) {
        requireUserId(authentication);
        Optional<ProblemDomainVariant> variant = domainLensService.findOrGenerate(problemId, majorCode);
        return variant.<ApiResponse<ProblemDomainVariant>>map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("variant_not_available", null));
    }

    @PostMapping("/variants/{variantId}/lock")
    public ApiResponse<Void> lockForExam(
            @PathVariable long variantId,
            Authentication authentication
    ) {
        long userId = requireUserId(authentication);
        domainLensService.lockForExam(variantId, userId);
        return ApiResponse.success(null);
    }

    private static long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}

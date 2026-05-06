package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.career.path.CareerPathService;
import com.alethicode.service.career.path.CareerPathView;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Career Path Map REST 入口（plan 6.2 节）。
 */
@RestController
@RequestMapping("/api/career/path")
public class CareerPathController {

    private final CareerPathService careerPathService;

    public CareerPathController(CareerPathService careerPathService) {
        this.careerPathService = careerPathService;
    }

    @GetMapping
    public ApiResponse<CareerPathView> getPath(
            @RequestParam(name = "major") String majorCode,
            Authentication authentication
    ) {
        long userId = requireUserId(authentication);
        return ApiResponse.success(careerPathService.buildView(userId, majorCode));
    }

    private static long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}

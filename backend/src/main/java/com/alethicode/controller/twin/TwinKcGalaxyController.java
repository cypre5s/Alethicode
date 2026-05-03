package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.twin.KcGalaxyResponse;
import com.alethicode.service.twin.kc.KcGalaxyProjector;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/twin/kc-galaxy")
public class TwinKcGalaxyController {

    private final KcGalaxyProjector projector;

    public TwinKcGalaxyController(KcGalaxyProjector projector) {
        this.projector = projector;
    }

    @GetMapping
    public ApiResponse<KcGalaxyResponse> getKcGalaxy(
            @RequestParam(name = "language_pack_id", required = false) Long languagePackId,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        KcGalaxyResponse response = projector.project(userId, languagePackId);
        return ApiResponse.success(response);
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}

package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.museum.ErrorMuseumService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/twin/museum/pins")
public class ErrorMuseumController {

    private final ErrorMuseumService museumService;

    public ErrorMuseumController(ErrorMuseumService museumService) {
        this.museumService = museumService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listPins(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(museumService.listPins(userId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> pinMemory(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        Number memoryIdNum = (Number) body.get("memory_id");
        if (memoryIdNum == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memory_id required");
        }
        String annotation = body.get("annotation") != null ? body.get("annotation").toString() : null;
        try {
            long pinId = museumService.pinMemory(userId, memoryIdNum.longValue(), annotation);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pin_id", pinId);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PatchMapping("/{pinId}")
    public ApiResponse<Map<String, Object>> updatePin(
            @PathVariable Long pinId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String annotation = body.get("annotation") != null ? body.get("annotation").toString() : null;
        Integer pinOrder = body.get("pin_order") != null ? ((Number) body.get("pin_order")).intValue() : null;
        try {
            museumService.updatePin(userId, pinId, annotation, pinOrder);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{pinId}")
    public ApiResponse<Map<String, Object>> unpinMemory(
            @PathVariable Long pinId,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        try {
            museumService.unpinMemory(userId, pinId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}

package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.chat.TwinChatService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/twin/chat")
public class TwinChatController {

    private final TwinChatService chatService;

    public TwinChatController(TwinChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> askTwin(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question required");
        }
        return ApiResponse.success(chatService.askTwin(userId, question));
    }

    @GetMapping("/quick-questions")
    public ApiResponse<List<Map<String, Object>>> getQuickQuestions() {
        return ApiResponse.success(chatService.getQuickQuestions());
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}

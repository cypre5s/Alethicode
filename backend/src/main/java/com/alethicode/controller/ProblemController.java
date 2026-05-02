package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.problem.ProblemQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class ProblemController {

    private final ProblemQueryService problemQueryService;

    public ProblemController(ProblemQueryService problemQueryService) {
        this.problemQueryService = problemQueryService;
    }

    @GetMapping({
            "/api/problems",
            "/api/problems/"
    })
    public ApiResponse<Object> problems(
            @RequestParam Map<String, String> params,
            Authentication authentication
    ) {
        return problemQueryService.getProblems(params, authentication);
    }

    @GetMapping({
            "/api/problems/tags",
            "/api/problems/tags/"
    })
    public ApiResponse<Object> tags(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "language_pack_id", required = false) String languagePackId,
            Authentication authentication
    ) {
        return problemQueryService.getProblemTags(keyword, languagePackId, authentication);
    }

    @GetMapping({
            "/api/problems/tag-progress",
            "/api/problems/tag-progress/"
    })
    public ApiResponse<Object> tagProgress(
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(name = "language_pack_id", required = false) String languagePackId,
            Authentication authentication
    ) {
        return problemQueryService.getTagProgress(userId, languagePackId, authentication);
    }

    @GetMapping({
            "/api/problems/random",
            "/api/problems/random/"
    })
    public ApiResponse<Object> randomProblem() {
        return problemQueryService.pickOne();
    }
}

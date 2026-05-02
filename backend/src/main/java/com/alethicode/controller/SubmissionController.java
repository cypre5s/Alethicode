package com.alethicode.controller;

import com.alethicode.dto.request.CreateSubmissionRequest;
import com.alethicode.dto.request.DebugSubmissionRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.submission.SubmissionCommandDomainService;
import com.alethicode.service.submission.SubmissionJudgeDispatchDomainService;
import com.alethicode.service.submission.SubmissionQueryDomainService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class SubmissionController {

    private final SubmissionCommandDomainService submissionCommandDomainService;
    private final SubmissionQueryDomainService submissionQueryDomainService;
    private final SubmissionJudgeDispatchDomainService submissionJudgeDispatchDomainService;

    public SubmissionController(SubmissionCommandDomainService submissionCommandDomainService,
                                SubmissionQueryDomainService submissionQueryDomainService,
                                SubmissionJudgeDispatchDomainService submissionJudgeDispatchDomainService) {
        this.submissionCommandDomainService = submissionCommandDomainService;
        this.submissionQueryDomainService = submissionQueryDomainService;
        this.submissionJudgeDispatchDomainService = submissionJudgeDispatchDomainService;
    }

    @PostMapping({
            "/api/submission",
            "/api/submission/"
    })
    public ApiResponse<Object> createSubmission(
            @RequestBody CreateSubmissionRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        return submissionCommandDomainService.createSubmission(
                request,
                authentication,
                httpServletRequest.getRemoteAddr(),
                hasApiKeyAuth(httpServletRequest)
        );
    }

    @GetMapping({
            "/api/submission",
            "/api/submission/"
    })
    public ApiResponse<Object> getSubmission(
            @RequestParam(name = "id", required = false) String id,
            Authentication authentication
    ) {
        return submissionQueryDomainService.getSubmission(id, authentication);
    }

    @GetMapping({
            "/api/submissions",
            "/api/submissions/"
    })
    public ApiResponse<Object> listSubmissions(
            @RequestParam(name = "problem_id", required = false) String problemId,
            @RequestParam(name = "myself", required = false) String myself,
            @RequestParam(name = "result", required = false) String result,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "limit", required = false) String limit,
            @RequestParam(name = "offset", required = false) String offset,
            Authentication authentication
    ) {
        return submissionQueryDomainService.listSubmissions(problemId, myself, result, username, limit, offset, authentication);
    }

    @GetMapping({
            "/api/submissions/recent-wrong",
            "/api/submissions/recent-wrong/"
    })
    public ApiResponse<Object> recentWrong(
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(name = "limit", required = false) String limit,
            Authentication authentication
    ) {
        return submissionQueryDomainService.recentWrong(userId, limit, authentication);
    }

    @GetMapping({
            "/api/submission-exists",
            "/api/submission-exists/"
    })
    public ApiResponse<Object> submissionExists(
            @RequestParam(name = "problem_id", required = false) String problemId,
            Authentication authentication
    ) {
        return submissionQueryDomainService.submissionExists(problemId, authentication);
    }

    @PostMapping({
            "/api/debug",
            "/api/debug/"
    })
    public ApiResponse<Object> debugSubmission(
            @RequestBody DebugSubmissionRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        return submissionJudgeDispatchDomainService.debugSubmission(
                request,
                authentication,
                httpServletRequest.getRemoteAddr(),
                hasApiKeyAuth(httpServletRequest)
        );
    }

    private boolean hasApiKeyAuth(HttpServletRequest request) {
        // "HTTP_APPKEY" 是 CGI/PHP 环境变量命名，HTTP 规范下 header 名应为 "AppKey"；
        // 同时兼容 Django 时代的大写形态以避免破坏旧客户端。
        String appKey = request.getHeader("Appkey");
        if (appKey == null || appKey.isBlank()) {
            appKey = request.getHeader("App-Key");
        }
        if (appKey == null || appKey.isBlank()) {
            appKey = request.getHeader("HTTP_APPKEY");
        }
        return appKey != null && !appKey.trim().isEmpty();
    }

    @GetMapping({
            "/api/problems/statistics",
            "/api/problems/statistics/"
    })
    public ApiResponse<Object> problemStatistics(
            @RequestParam(name = "problem_id", required = false) String problemId,
            @RequestParam(name = "language", required = false) String language
    ) {
        return submissionQueryDomainService.problemStatistics(problemId, language);
    }
}

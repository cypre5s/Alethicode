package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.submission.SubmissionJudgeDispatchDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubmissionController {

    private final SubmissionJudgeDispatchDomainService submissionJudgeDispatchDomainService;

    public AdminSubmissionController(SubmissionJudgeDispatchDomainService submissionJudgeDispatchDomainService) {
        this.submissionJudgeDispatchDomainService = submissionJudgeDispatchDomainService;
    }

    @GetMapping({
            "/api/submission/rejudge",
            "/api/submission/rejudge/",
            "/api/admin/submission/rejudge",
            "/api/admin/submission/rejudge/"
    })
    public ApiResponse<Object> rejudge(
            @RequestParam(name = "id", required = false) String id,
            Authentication authentication
    ) {
        return submissionJudgeDispatchDomainService.rejudgeSubmission(id, authentication);
    }
}

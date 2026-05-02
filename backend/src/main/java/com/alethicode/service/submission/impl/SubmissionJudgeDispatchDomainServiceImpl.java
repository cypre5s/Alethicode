package com.alethicode.service.submission.impl;

import com.alethicode.dto.request.DebugSubmissionRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.submission.impl.SubmissionServiceImpl;
import com.alethicode.service.submission.SubmissionJudgeDispatchDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SubmissionJudgeDispatchDomainServiceImpl implements SubmissionJudgeDispatchDomainService {

    private final SubmissionServiceImpl submissionService;

    public SubmissionJudgeDispatchDomainServiceImpl(SubmissionServiceImpl submissionService) {
        this.submissionService = submissionService;
    }

    @Override
    public ApiResponse<Object> debugSubmission(DebugSubmissionRequest request,
                                               Authentication authentication,
                                               String clientIp,
                                               boolean apiKeyAuth) {
        return submissionService.debugSubmission(request, authentication, clientIp, apiKeyAuth);
    }

    @Override
    public ApiResponse<Object> rejudgeSubmission(String submissionId, Authentication authentication) {
        return submissionService.rejudgeSubmission(submissionId, authentication);
    }
}

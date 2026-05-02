package com.alethicode.service.submission.impl;

import com.alethicode.dto.request.CreateSubmissionRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.submission.impl.SubmissionServiceImpl;
import com.alethicode.service.submission.SubmissionCommandDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SubmissionCommandDomainServiceImpl implements SubmissionCommandDomainService {

    private final SubmissionServiceImpl submissionService;

    public SubmissionCommandDomainServiceImpl(SubmissionServiceImpl submissionService) {
        this.submissionService = submissionService;
    }

    @Override
    public ApiResponse<Object> createSubmission(CreateSubmissionRequest request,
                                                Authentication authentication,
                                                String clientIp,
                                                boolean apiKeyAuth) {
        return submissionService.createSubmission(request, authentication, clientIp, apiKeyAuth);
    }
}

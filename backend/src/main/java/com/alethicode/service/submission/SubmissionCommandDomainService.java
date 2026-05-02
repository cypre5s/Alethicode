package com.alethicode.service.submission;

import com.alethicode.dto.request.CreateSubmissionRequest;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

public interface SubmissionCommandDomainService {

    ApiResponse<Object> createSubmission(
            CreateSubmissionRequest request,
            Authentication authentication,
            String clientIp,
            boolean apiKeyAuth
    );

}

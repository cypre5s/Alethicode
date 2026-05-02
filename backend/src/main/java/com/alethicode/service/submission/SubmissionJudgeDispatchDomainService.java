package com.alethicode.service.submission;

import com.alethicode.dto.request.DebugSubmissionRequest;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

public interface SubmissionJudgeDispatchDomainService {

    ApiResponse<Object> debugSubmission(
            DebugSubmissionRequest request,
            Authentication authentication,
            String clientIp,
            boolean apiKeyAuth
    );

    ApiResponse<Object> rejudgeSubmission(String submissionId, Authentication authentication);
}

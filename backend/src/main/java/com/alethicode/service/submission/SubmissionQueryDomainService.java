package com.alethicode.service.submission;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

public interface SubmissionQueryDomainService {

    ApiResponse<Object> getSubmission(String submissionId, Authentication authentication);

    ApiResponse<Object> listSubmissions(String problemId, String myself, String result, String username,
                                        String limit, String offset, Authentication authentication);

    ApiResponse<Object> recentWrong(String userIdParam, String limitParam, Authentication authentication);

    ApiResponse<Object> submissionExists(String problemIdParam, Authentication authentication);

    ApiResponse<Object> problemStatistics(String problemIdParam, String language);
}

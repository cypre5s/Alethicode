package com.alethicode.service.aitutor;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface AITutorAdminReviewDomainService {

    ApiResponse<Object> adminVariantReview(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> adminVariantApprove(String problemId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminVariantReject(String problemId, Authentication authentication);

    ApiResponse<Object> adminKcList(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> adminKcDetailUpdate(String kcId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminKcProblems(String kcId, Authentication authentication);

    ApiResponse<Object> adminClassroomChapters(Authentication authentication);

    ApiResponse<Object> adminPreflightStats(Authentication authentication);

    ApiResponse<Object> adminPreflightDiagnose(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminMcMiningPending(Authentication authentication);

    ApiResponse<Object> adminMcMiningApprove(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminMcMiningReject(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminMcMiningMerge(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminMcMiningDiscover(Authentication authentication);
}

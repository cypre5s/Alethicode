package com.alethicode.service.aitutor;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface AITutorKnowledgeDomainService {

    ApiResponse<Object> notebookList(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> notebookCreate(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> notebookUpdate(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> notebookDelete(String id, Authentication authentication);

    ApiResponse<Object> notebookExport(Authentication authentication);

    ApiResponse<Object> notebookClassFrequency(Authentication authentication);

    ApiResponse<Object> notebookGenerateReflection(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> notebookWeeklySummary(Authentication authentication);

    ApiResponse<Object> supplementPlan(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> misconceptionsMine(Authentication authentication);

    ApiResponse<Object> reviewDue(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> knowledgeGraph(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> knowledgeGraphSnapshot(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> kcDetail(String kcId, Map<String, String> params, Authentication authentication);
}

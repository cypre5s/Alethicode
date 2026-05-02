package com.alethicode.service.aitutor.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.impl.AITutorServiceImpl;
import com.alethicode.service.aitutor.AITutorKnowledgeDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AITutorKnowledgeDomainServiceImpl implements AITutorKnowledgeDomainService {

    private final AITutorServiceImpl aiTutorService;

    public AITutorKnowledgeDomainServiceImpl(AITutorServiceImpl aiTutorService) {
        this.aiTutorService = aiTutorService;
    }

    @Override
    public ApiResponse<Object> notebookList(Map<String, String> params, Authentication authentication) {
        return aiTutorService.notebookList(params, authentication);
    }

    @Override
    public ApiResponse<Object> notebookCreate(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.notebookCreate(request, authentication);
    }

    @Override
    public ApiResponse<Object> notebookUpdate(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.notebookUpdate(request, authentication);
    }

    @Override
    public ApiResponse<Object> notebookDelete(String id, Authentication authentication) {
        return aiTutorService.notebookDelete(id, authentication);
    }

    @Override
    public ApiResponse<Object> notebookExport(Authentication authentication) {
        return aiTutorService.notebookExport(authentication);
    }

    @Override
    public ApiResponse<Object> notebookClassFrequency(Authentication authentication) {
        return aiTutorService.notebookClassFrequency(authentication);
    }

    @Override
    public ApiResponse<Object> notebookGenerateReflection(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.notebookGenerateReflection(request, authentication);
    }

    @Override
    public ApiResponse<Object> notebookWeeklySummary(Authentication authentication) {
        return aiTutorService.notebookWeeklySummary(authentication);
    }

    @Override
    public ApiResponse<Object> supplementPlan(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.supplementPlan(request, authentication);
    }

    @Override
    public ApiResponse<Object> misconceptionsMine(Authentication authentication) {
        return aiTutorService.misconceptionsMine(authentication);
    }

    @Override
    public ApiResponse<Object> reviewDue(Map<String, String> params, Authentication authentication) {
        return aiTutorService.reviewDue(params, authentication);
    }

    @Override
    public ApiResponse<Object> knowledgeGraph(Map<String, String> params, Authentication authentication) {
        return aiTutorService.knowledgeGraph(params, authentication);
    }

    @Override
    public ApiResponse<Object> knowledgeGraphSnapshot(Map<String, String> params, Authentication authentication) {
        return aiTutorService.knowledgeGraphSnapshot(params, authentication);
    }

    @Override
    public ApiResponse<Object> kcDetail(String kcId, Map<String, String> params, Authentication authentication) {
        return aiTutorService.kcDetail(kcId, params, authentication);
    }
}

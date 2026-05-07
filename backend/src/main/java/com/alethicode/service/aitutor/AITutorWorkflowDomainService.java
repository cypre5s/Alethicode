package com.alethicode.service.aitutor;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

/**
 * 原领域服务中保留的非工作流方法。
 *
 * 会话、事件、checkpoint 和中断执行已迁移到 LangGraph tutor-graph 与
 * {@link com.alethicode.controller.TutorWorkflowController}。
 */
public interface AITutorWorkflowDomainService {

    ApiResponse<Object> ideateSkeleton(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> codeSnapshot(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> learningEventsBatch(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> calibrationStatus(Authentication authentication);

    ApiResponse<Object> calibrationAnswer(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> calibrationSkip(Authentication authentication);

}

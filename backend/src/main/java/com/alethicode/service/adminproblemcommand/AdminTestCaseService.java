package com.alethicode.service.adminproblemcommand;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AdminTestCaseService {

    ApiResponse<Object> uploadTestCases(String spjParam, MultipartFile file, Authentication authentication);

    ResponseEntity<Resource> downloadTestCases(String problemIdParam, Authentication authentication);

    ApiResponse<Object> getInlineTestCases(String problemIdParam, Authentication authentication);

    ApiResponse<Object> uploadInlineTestCases(Map<String, Object> request, Authentication authentication);
}

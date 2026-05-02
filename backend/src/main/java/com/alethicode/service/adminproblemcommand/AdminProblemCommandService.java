package com.alethicode.service.adminproblemcommand;

import com.alethicode.dto.request.AdminProblemUpsertRequest;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AdminProblemCommandService {

    ApiResponse<Object> createProblem(AdminProblemUpsertRequest request, Authentication authentication);

    ApiResponse<Object> updateProblem(AdminProblemUpsertRequest request, Authentication authentication);

    ApiResponse<Object> deleteProblem(String idParam, Authentication authentication);

    ResponseEntity<Resource> exportProblems(List<String> problemIdParams, Authentication authentication);

    ApiResponse<Object> importProblems(MultipartFile file, String autoKc, String languagePackId, Authentication authentication);

    ApiResponse<Object> importFps(MultipartFile file, Authentication authentication);
}

package com.alethicode.service.adminproblemcommand;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface AdminProblemImportDomainService {

    ApiResponse<Object> importProblems(MultipartFile file, String autoKc, String languagePackId, Authentication authentication);
}

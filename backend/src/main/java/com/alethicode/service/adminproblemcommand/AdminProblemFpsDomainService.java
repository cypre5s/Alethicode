package com.alethicode.service.adminproblemcommand;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface AdminProblemFpsDomainService {

    ApiResponse<Object> importFps(MultipartFile file, Authentication authentication);
}

package com.alethicode.service.adminproblemcommand.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminProblemCommandService;
import com.alethicode.service.adminproblemcommand.AdminProblemFpsDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminProblemFpsDomainServiceImpl implements AdminProblemFpsDomainService {

    private final AdminProblemCommandService adminProblemCommandService;

    public AdminProblemFpsDomainServiceImpl(AdminProblemCommandService adminProblemCommandService) {
        this.adminProblemCommandService = adminProblemCommandService;
    }

    @Override
    public ApiResponse<Object> importFps(MultipartFile file, Authentication authentication) {
        return adminProblemCommandService.importFps(file, authentication);
    }
}

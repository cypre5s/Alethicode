package com.alethicode.service.adminproblemcommand.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminProblemCommandService;
import com.alethicode.service.adminproblemcommand.AdminProblemImportDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminProblemImportDomainServiceImpl implements AdminProblemImportDomainService {

    private final AdminProblemCommandService adminProblemCommandService;

    public AdminProblemImportDomainServiceImpl(AdminProblemCommandService adminProblemCommandService) {
        this.adminProblemCommandService = adminProblemCommandService;
    }

    @Override
    public ApiResponse<Object> importProblems(MultipartFile file, String autoKc, String languagePackId, Authentication authentication) {
        return adminProblemCommandService.importProblems(file, autoKc, languagePackId, authentication);
    }
}

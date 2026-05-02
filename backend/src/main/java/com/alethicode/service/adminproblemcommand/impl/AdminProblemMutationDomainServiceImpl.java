package com.alethicode.service.adminproblemcommand.impl;

import com.alethicode.dto.request.AdminProblemUpsertRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminProblemCommandService;
import com.alethicode.service.adminproblemcommand.AdminProblemMutationDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AdminProblemMutationDomainServiceImpl implements AdminProblemMutationDomainService {

    private final AdminProblemCommandService adminProblemCommandService;

    public AdminProblemMutationDomainServiceImpl(AdminProblemCommandService adminProblemCommandService) {
        this.adminProblemCommandService = adminProblemCommandService;
    }

    @Override
    public ApiResponse<Object> createProblem(AdminProblemUpsertRequest request, Authentication authentication) {
        return adminProblemCommandService.createProblem(request, authentication);
    }

    @Override
    public ApiResponse<Object> updateProblem(AdminProblemUpsertRequest request, Authentication authentication) {
        return adminProblemCommandService.updateProblem(request, authentication);
    }

    @Override
    public ApiResponse<Object> deleteProblem(String idParam, Authentication authentication) {
        return adminProblemCommandService.deleteProblem(idParam, authentication);
    }
}

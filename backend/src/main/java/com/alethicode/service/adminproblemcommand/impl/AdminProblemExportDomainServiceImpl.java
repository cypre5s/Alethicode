package com.alethicode.service.adminproblemcommand.impl;

import com.alethicode.service.adminproblemcommand.AdminProblemCommandService;
import com.alethicode.service.adminproblemcommand.AdminProblemExportDomainService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminProblemExportDomainServiceImpl implements AdminProblemExportDomainService {

    private final AdminProblemCommandService adminProblemCommandService;

    public AdminProblemExportDomainServiceImpl(AdminProblemCommandService adminProblemCommandService) {
        this.adminProblemCommandService = adminProblemCommandService;
    }

    @Override
    public ResponseEntity<Resource> exportProblems(List<String> problemIdParams, Authentication authentication) {
        return adminProblemCommandService.exportProblems(problemIdParams, authentication);
    }
}

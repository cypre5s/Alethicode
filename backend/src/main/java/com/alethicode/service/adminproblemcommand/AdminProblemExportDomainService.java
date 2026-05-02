package com.alethicode.service.adminproblemcommand;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface AdminProblemExportDomainService {

    ResponseEntity<Resource> exportProblems(List<String> problemIdParams, Authentication authentication);
}

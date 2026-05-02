package com.alethicode.service.adminproblemcommand;

import com.alethicode.dto.request.AdminProblemUpsertRequest;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

public interface AdminProblemMutationDomainService {

    ApiResponse<Object> createProblem(AdminProblemUpsertRequest request, Authentication authentication);

    ApiResponse<Object> updateProblem(AdminProblemUpsertRequest request, Authentication authentication);

    ApiResponse<Object> deleteProblem(String idParam, Authentication authentication);
}

package com.alethicode.service.account;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface AccountAdminDomainService {

    ApiResponse<Object> adminListUsers(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> adminImportUsers(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminEditUser(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminDeleteUsers(String id, Authentication authentication);

    ApiResponse<Object> adminGenerateUsers(Map<String, Object> request, Authentication authentication);

    byte[] adminDownloadGeneratedUsers(String fileId, Authentication authentication);
}

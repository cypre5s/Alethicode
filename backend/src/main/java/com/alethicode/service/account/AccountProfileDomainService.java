package com.alethicode.service.account;

import com.alethicode.dto.request.EditUserProfileRequest;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface AccountProfileDomainService {

    ApiResponse<Object> getProfile(String username, Authentication authentication);

    ApiResponse<Object> updateProfile(EditUserProfileRequest request, Authentication authentication);

    ApiResponse<Object> uploadAvatar(MultipartFile image, Authentication authentication);

    ApiResponse<Object> refreshProfileDisplayId(Authentication authentication);

    ApiResponse<Object> refreshOpenApiAppkey(Authentication authentication);
}

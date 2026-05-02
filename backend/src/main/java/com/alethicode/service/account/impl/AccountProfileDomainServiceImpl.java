package com.alethicode.service.account.impl;

import com.alethicode.dto.request.EditUserProfileRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.account.AccountService;
import com.alethicode.service.account.AccountProfileDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AccountProfileDomainServiceImpl implements AccountProfileDomainService {

    private final AccountService accountService;

    public AccountProfileDomainServiceImpl(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public ApiResponse<Object> getProfile(String username, Authentication authentication) {
        return accountService.getProfile(username, authentication);
    }

    @Override
    public ApiResponse<Object> updateProfile(EditUserProfileRequest request, Authentication authentication) {
        return accountService.updateProfile(request, authentication);
    }

    @Override
    public ApiResponse<Object> uploadAvatar(MultipartFile image, Authentication authentication) {
        return accountService.uploadAvatar(image, authentication);
    }

    @Override
    public ApiResponse<Object> refreshProfileDisplayId(Authentication authentication) {
        return accountService.refreshProfileDisplayId(authentication);
    }

    @Override
    public ApiResponse<Object> refreshOpenApiAppkey(Authentication authentication) {
        return accountService.refreshOpenApiAppkey(authentication);
    }
}

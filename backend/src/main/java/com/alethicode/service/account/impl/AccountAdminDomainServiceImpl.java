package com.alethicode.service.account.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.account.AccountService;
import com.alethicode.service.account.AccountAdminDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AccountAdminDomainServiceImpl implements AccountAdminDomainService {

    private final AccountService accountService;

    public AccountAdminDomainServiceImpl(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public ApiResponse<Object> adminListUsers(Map<String, String> params, Authentication authentication) {
        return accountService.adminListUsers(params, authentication);
    }

    @Override
    public ApiResponse<Object> adminImportUsers(Map<String, Object> request, Authentication authentication) {
        return accountService.adminImportUsers(request, authentication);
    }

    @Override
    public ApiResponse<Object> adminEditUser(Map<String, Object> request, Authentication authentication) {
        return accountService.adminEditUser(request, authentication);
    }

    @Override
    public ApiResponse<Object> adminDeleteUsers(String id, Authentication authentication) {
        return accountService.adminDeleteUsers(id, authentication);
    }

    @Override
    public ApiResponse<Object> adminGenerateUsers(Map<String, Object> request, Authentication authentication) {
        return accountService.adminGenerateUsers(request, authentication);
    }

    @Override
    public byte[] adminDownloadGeneratedUsers(String fileId, Authentication authentication) {
        return accountService.adminDownloadGeneratedUsers(fileId, authentication);
    }
}

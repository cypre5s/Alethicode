package com.alethicode.service.account.impl;

import com.alethicode.dto.request.ApplyResetPasswordRequest;
import com.alethicode.dto.request.ResetPasswordRequest;
import com.alethicode.dto.request.SsoTokenRequest;
import com.alethicode.dto.request.TwoFactorCodeRequest;
import com.alethicode.dto.request.UserChangeEmailRequest;
import com.alethicode.dto.request.UserChangePasswordRequest;
import com.alethicode.dto.request.UserLoginRequest;
import com.alethicode.dto.request.UserRegisterRequest;
import com.alethicode.dto.request.UsernameOrEmailCheckRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.account.AccountService;
import com.alethicode.service.account.AccountAuthDomainService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AccountAuthDomainServiceImpl implements AccountAuthDomainService {

    private final AccountService accountService;

    public AccountAuthDomainServiceImpl(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public ApiResponse<Object> login(UserLoginRequest request, HttpServletRequest httpServletRequest) {
        return accountService.login(request, httpServletRequest);
    }

    @Override
    public ApiResponse<Object> logout(HttpServletRequest httpServletRequest) {
        return accountService.logout(httpServletRequest);
    }

    @Override
    public ApiResponse<Object> register(UserRegisterRequest request, HttpServletRequest httpServletRequest) {
        return accountService.register(request, httpServletRequest);
    }

    @Override
    public ApiResponse<Object> usernameOrEmailCheck(UsernameOrEmailCheckRequest request) {
        return accountService.usernameOrEmailCheck(request);
    }

    @Override
    public ApiResponse<Object> changePassword(UserChangePasswordRequest request, Authentication authentication) {
        return accountService.changePassword(request, authentication);
    }

    @Override
    public ApiResponse<Object> changeEmail(UserChangeEmailRequest request, Authentication authentication) {
        return accountService.changeEmail(request, authentication);
    }

    @Override
    public ApiResponse<Object> applyResetPassword(ApplyResetPasswordRequest request,
                                                  Authentication authentication,
                                                  HttpServletRequest httpServletRequest) {
        return accountService.applyResetPassword(request, authentication, httpServletRequest);
    }

    @Override
    public ApiResponse<Object> resetPassword(ResetPasswordRequest request, HttpServletRequest httpServletRequest) {
        return accountService.resetPassword(request, httpServletRequest);
    }

    @Override
    public ApiResponse<Object> checkTfaRequired(UsernameOrEmailCheckRequest request) {
        return accountService.checkTfaRequired(request);
    }

    @Override
    public ApiResponse<Object> getTwoFactorQr(Authentication authentication) {
        return accountService.getTwoFactorQr(authentication);
    }

    @Override
    public ApiResponse<Object> enableTwoFactor(TwoFactorCodeRequest request, Authentication authentication) {
        return accountService.enableTwoFactor(request, authentication);
    }

    @Override
    public ApiResponse<Object> disableTwoFactor(TwoFactorCodeRequest request, Authentication authentication) {
        return accountService.disableTwoFactor(request, authentication);
    }

    @Override
    public ApiResponse<Object> listSessions(Authentication authentication, HttpServletRequest httpServletRequest) {
        return accountService.listSessions(authentication, httpServletRequest);
    }

    @Override
    public ApiResponse<Object> deleteSession(String sessionKey,
                                             Authentication authentication,
                                             HttpServletRequest httpServletRequest) {
        return accountService.deleteSession(sessionKey, authentication, httpServletRequest);
    }

    @Override
    public ApiResponse<Object> issueSsoToken(Authentication authentication) {
        return accountService.issueSsoToken(authentication);
    }

    @Override
    public ApiResponse<Object> resolveSsoToken(SsoTokenRequest request) {
        return accountService.resolveSsoToken(request);
    }

    @Override
    public ApiResponse<Object> issueCaptcha(HttpServletRequest httpServletRequest) {
        return accountService.issueCaptcha(httpServletRequest);
    }
}

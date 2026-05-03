package com.alethicode.service.account;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

public interface AccountAuthDomainService {

    ApiResponse<Object> login(UserLoginRequest request, HttpServletRequest httpServletRequest);

    ApiResponse<Object> logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse);

    ApiResponse<Object> register(UserRegisterRequest request, HttpServletRequest httpServletRequest);

    ApiResponse<Object> usernameOrEmailCheck(UsernameOrEmailCheckRequest request);

    ApiResponse<Object> changePassword(UserChangePasswordRequest request, Authentication authentication);

    ApiResponse<Object> changeEmail(UserChangeEmailRequest request, Authentication authentication);

    ApiResponse<Object> applyResetPassword(ApplyResetPasswordRequest request, Authentication authentication, HttpServletRequest httpServletRequest);

    ApiResponse<Object> resetPassword(ResetPasswordRequest request, HttpServletRequest httpServletRequest);

    ApiResponse<Object> checkTfaRequired(UsernameOrEmailCheckRequest request);

    ApiResponse<Object> getTwoFactorQr(Authentication authentication);

    ApiResponse<Object> enableTwoFactor(TwoFactorCodeRequest request, Authentication authentication);

    ApiResponse<Object> disableTwoFactor(TwoFactorCodeRequest request, Authentication authentication);

    ApiResponse<Object> listSessions(Authentication authentication, HttpServletRequest httpServletRequest);

    ApiResponse<Object> deleteSession(String sessionKey, Authentication authentication, HttpServletRequest httpServletRequest);

    ApiResponse<Object> issueSsoToken(Authentication authentication);

    ApiResponse<Object> resolveSsoToken(SsoTokenRequest request);

    ApiResponse<Object> issueCaptcha(HttpServletRequest httpServletRequest);
}

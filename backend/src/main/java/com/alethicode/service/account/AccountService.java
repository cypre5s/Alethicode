package com.alethicode.service.account;

import com.alethicode.dto.request.*;
import com.alethicode.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AccountService {

    ApiResponse<Object> login(UserLoginRequest request, HttpServletRequest httpServletRequest);

    ApiResponse<Object> logout(HttpServletRequest httpServletRequest);

    ApiResponse<Object> register(UserRegisterRequest request, HttpServletRequest httpServletRequest);

    ApiResponse<Object> usernameOrEmailCheck(UsernameOrEmailCheckRequest request);

    ApiResponse<Object> getProfile(String username, Authentication authentication);

    ApiResponse<Object> updateProfile(EditUserProfileRequest request, Authentication authentication);

    ApiResponse<Object> uploadAvatar(MultipartFile image, Authentication authentication);

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

    ApiResponse<Object> refreshProfileDisplayId(Authentication authentication);

    ApiResponse<Object> refreshOpenApiAppkey(Authentication authentication);

    ApiResponse<Object> issueSsoToken(Authentication authentication);

    ApiResponse<Object> resolveSsoToken(SsoTokenRequest request);

    ApiResponse<Object> issueCaptcha(HttpServletRequest httpServletRequest);

    ApiResponse<Object> adminListUsers(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> adminImportUsers(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminEditUser(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> adminDeleteUsers(String id, Authentication authentication);

    ApiResponse<Object> adminGenerateUsers(Map<String, Object> request, Authentication authentication);

    byte[] adminDownloadGeneratedUsers(String fileId, Authentication authentication);
}

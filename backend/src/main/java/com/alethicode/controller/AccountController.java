package com.alethicode.controller;

import com.alethicode.dto.request.ApplyResetPasswordRequest;
import com.alethicode.dto.request.EditUserProfileRequest;
import com.alethicode.dto.request.ResetPasswordRequest;
import com.alethicode.dto.request.SsoTokenRequest;
import com.alethicode.dto.request.TwoFactorCodeRequest;
import com.alethicode.dto.request.UserChangeEmailRequest;
import com.alethicode.dto.request.UserChangePasswordRequest;
import com.alethicode.dto.request.UserLoginRequest;
import com.alethicode.dto.request.UserRegisterRequest;
import com.alethicode.dto.request.UsernameOrEmailCheckRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.account.AccountAuthDomainService;
import com.alethicode.service.account.AccountProfileDomainService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping
public class AccountController {

    private final AccountAuthDomainService accountAuthDomainService;
    private final AccountProfileDomainService accountProfileDomainService;

    public AccountController(AccountAuthDomainService accountAuthDomainService,
                             AccountProfileDomainService accountProfileDomainService) {
        this.accountAuthDomainService = accountAuthDomainService;
        this.accountProfileDomainService = accountProfileDomainService;
    }

    @PostMapping({"/api/login", "/api/login/"})
    public ApiResponse<Object> login(@RequestBody UserLoginRequest request, HttpServletRequest httpServletRequest) {
        return accountAuthDomainService.login(request, httpServletRequest);
    }

    @GetMapping({"/api/logout", "/api/logout/"})
    public ApiResponse<Object> logout(HttpServletRequest httpServletRequest) {
        return accountAuthDomainService.logout(httpServletRequest);
    }

    @PostMapping({"/api/register", "/api/register/"})
    public ApiResponse<Object> register(@RequestBody UserRegisterRequest request, HttpServletRequest httpServletRequest) {
        return accountAuthDomainService.register(request, httpServletRequest);
    }

    @PostMapping({
            "/api/check-username-or-email", "/api/check-username-or-email/"
    })
    public ApiResponse<Object> checkUsernameOrEmail(@RequestBody UsernameOrEmailCheckRequest request) {
        return accountAuthDomainService.usernameOrEmailCheck(request);
    }

    @GetMapping({"/api/profile", "/api/profile/"})
    public ApiResponse<Object> profileGet(@RequestParam(name = "username", required = false) String username,
                                          Authentication authentication) {
        return accountProfileDomainService.getProfile(username, authentication);
    }

    @PutMapping({"/api/profile", "/api/profile/"})
    public ApiResponse<Object> profilePut(@RequestBody EditUserProfileRequest request,
                                          Authentication authentication) {
        return accountProfileDomainService.updateProfile(request, authentication);
    }

    @PostMapping({
            "/api/upload-avatar", "/api/upload-avatar/"
    })
    public ApiResponse<Object> uploadAvatar(@RequestParam(name = "image", required = false) MultipartFile image,
                                            Authentication authentication) {
        return accountProfileDomainService.uploadAvatar(image, authentication);
    }

    @PostMapping({
            "/api/change-password", "/api/change-password/"
    })
    public ApiResponse<Object> changePassword(@RequestBody UserChangePasswordRequest request,
                                              Authentication authentication) {
        return accountAuthDomainService.changePassword(request, authentication);
    }

    @PostMapping({"/api/change-email", "/api/change-email/"})
    public ApiResponse<Object> changeEmail(@RequestBody UserChangeEmailRequest request,
                                           Authentication authentication) {
        return accountAuthDomainService.changeEmail(request, authentication);
    }

    @PostMapping({
            "/api/apply-reset-password", "/api/apply-reset-password/"
    })
    public ApiResponse<Object> applyResetPassword(@RequestBody ApplyResetPasswordRequest request,
                                                   Authentication authentication,
                                                   HttpServletRequest httpServletRequest) {
        return accountAuthDomainService.applyResetPassword(request, authentication, httpServletRequest);
    }

    @PostMapping({"/api/reset-password", "/api/reset-password/"})
    public ApiResponse<Object> resetPassword(@RequestBody ResetPasswordRequest request,
                                             HttpServletRequest httpServletRequest) {
        return accountAuthDomainService.resetPassword(request, httpServletRequest);
    }

    @GetMapping({
            "/api/profile/fresh-display-id", "/api/profile/fresh-display-id/"
    })
    public ApiResponse<Object> refreshDisplayId(Authentication authentication) {
        return accountProfileDomainService.refreshProfileDisplayId(authentication);
    }

    @PostMapping({"/api/tfa-required", "/api/tfa-required/"})
    public ApiResponse<Object> checkTfaRequired(@RequestBody UsernameOrEmailCheckRequest request) {
        return accountAuthDomainService.checkTfaRequired(request);
    }

    @GetMapping({
            "/api/two-factor-auth", "/api/two-factor-auth/"
    })
    public ApiResponse<Object> getTwoFactorQr(Authentication authentication) {
        return accountAuthDomainService.getTwoFactorQr(authentication);
    }

    @PostMapping({
            "/api/two-factor-auth", "/api/two-factor-auth/"
    })
    public ApiResponse<Object> enableTwoFactor(@RequestBody TwoFactorCodeRequest request, Authentication authentication) {
        return accountAuthDomainService.enableTwoFactor(request, authentication);
    }

    @PutMapping({
            "/api/two-factor-auth", "/api/two-factor-auth/"
    })
    public ApiResponse<Object> disableTwoFactor(@RequestBody TwoFactorCodeRequest request, Authentication authentication) {
        return accountAuthDomainService.disableTwoFactor(request, authentication);
    }

    @GetMapping({"/api/sessions", "/api/sessions/"})
    public ApiResponse<Object> listSessions(Authentication authentication, HttpServletRequest httpServletRequest) {
        return accountAuthDomainService.listSessions(authentication, httpServletRequest);
    }

    @DeleteMapping({"/api/sessions", "/api/sessions/"})
    public ApiResponse<Object> deleteSession(
            @RequestParam(name = "session_key", required = false) String sessionKey,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        return accountAuthDomainService.deleteSession(sessionKey, authentication, httpServletRequest);
    }

    @PostMapping({
            "/api/open-api-appkey", "/api/open-api-appkey/"
    })
    public ApiResponse<Object> refreshOpenApiAppkey(Authentication authentication) {
        return accountProfileDomainService.refreshOpenApiAppkey(authentication);
    }

    @GetMapping({"/api/sso", "/api/sso/"})
    public ApiResponse<Object> issueSsoToken(Authentication authentication) {
        return accountAuthDomainService.issueSsoToken(authentication);
    }

    @PostMapping({"/api/sso", "/api/sso/"})
    public ApiResponse<Object> resolveSsoToken(@RequestBody SsoTokenRequest request) {
        return accountAuthDomainService.resolveSsoToken(request);
    }

    @GetMapping({"/api/captcha", "/api/captcha/"})
    public ApiResponse<Object> captcha(HttpServletRequest httpServletRequest) {
        return accountAuthDomainService.issueCaptcha(httpServletRequest);
    }
}

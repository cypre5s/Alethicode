package com.alethicode.service.account.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.*;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.middleware.SessionAuthenticationFilter;
import com.alethicode.service.account.AccountService;
import com.alethicode.util.TotpUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private static final String CAPTCHA_CODE_KEY = "CAPTCHA_CODE";
    private static final Set<String> ALLOWED_AVATAR_SUFFIX = Set.of(".gif", ".jpg", ".jpeg", ".bmp", ".png");
    /**
     * MED-3 (2026-05-02 渗透报告): 用户不存在时跑一次 fake bcrypt 校验，让登录响应时间
     * 接近真实用户路径（避免攻击者通过时序差枚举用户名）。这是一个 cost=10 的合法 bcrypt
     * hash，对应任意明文都不会 match。
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AccountServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AlethicodeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    void repairInvalidAvatarReferences() {
        List<Map<String, Object>> refs = jdbcTemplate.query(
                """
                select user_id, avatar
                from user_profile
                where avatar like '/public/avatar/%'
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("user_id", rs.getLong("user_id"));
                    item.put("avatar", rs.getString("avatar"));
                    return item;
                }
        );
        for (Map<String, Object> ref : refs) {
            Long userId = parseLong(stringValue(ref.get("user_id")));
            String avatar = trimToNull(stringValue(ref.get("avatar")));
            if (userId == null || avatar == null) {
                continue;
            }
            normalizeAvatarForUser(userId, avatar);
        }
    }

    @Override
    public ApiResponse<Object> login(UserLoginRequest request, HttpServletRequest httpServletRequest) {
        String username = lowerTrim(request.username());
        String password = trimToNull(request.password());
        if (username == null || password == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid username or password");
        }

        UserRow user = findUserByUsername(username);
        // MED-3: 即便用户不存在也跑一次 fake bcrypt，避免响应时间侧信道枚举用户名。
        String hashForCompare = (user != null && trimToNull(user.passwordHash()) != null)
                ? user.passwordHash()
                : DUMMY_PASSWORD_HASH;
        boolean passwordMatches = passwordEncoder.matches(password, hashForCompare);
        if (user == null || trimToNull(user.passwordHash()) == null || !passwordMatches) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid username or password");
        }
        if (user.disabled()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Your account has been disabled");
        }

        if (user.twoFactorAuth()) {
            String tfaCode = trimToNull(request.tfaCode());
            if (tfaCode == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "tfa_required");
            }
            if (!TotpUtils.verifyCode(trimToEmpty(user.tfaToken()), tfaCode)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid two factor verification code");
            }
        }

        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute(SessionAuthenticationFilter.AUTH_USERNAME_KEY, user.username());
        session.setAttribute(SessionAuthenticationFilter.AUTH_USER_ID_KEY, user.id());
        session.setAttribute("ip", clientIp(httpServletRequest));
        session.setAttribute("user_agent", trimToEmpty(httpServletRequest.getHeader("User-Agent")));
        session.setAttribute("last_activity", Instant.now().toString());
        upsertSessionKey(user.id(), session.getId());

        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> logout(HttpServletRequest httpServletRequest,
                                      HttpServletResponse httpServletResponse) {
        // NEW-1 (2026-05-02 渗透报告 v2): 仅 session.invalidate() 不够——
        // (1) Spring Security 的 SecurityContext 仍残留在 ThreadLocal，本请求后续 filter
        //     仍会把当前用户当成已登录；
        // (2) 浏览器/curl 仍持有旧 SESSION cookie，logout 后立刻请求 /api/profile，
        //     SessionAuthenticationFilter 会用旧 cookie 命中 Tomcat 的新 session 并重建
        //     Authentication（permitAll 路径下，profile 接口因此返回登录用户的数据）。
        // 修复：清 SecurityContext + 强制让浏览器丢弃 SESSION/csrftoken cookie。
        HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            session.removeAttribute(SessionAuthenticationFilter.AUTH_USERNAME_KEY);
            session.removeAttribute(SessionAuthenticationFilter.AUTH_USER_ID_KEY);
            session.removeAttribute(SessionAuthenticationFilter.AUTH_ROLES_KEY);
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        expireCookie(httpServletResponse, "SESSION");
        expireCookie(httpServletResponse, "csrftoken");
        return ApiResponse.success(null);
    }

    private void expireCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly("SESSION".equals(name));
        cookie.setSecure(properties.getSystem().isCookieSecure());
        response.addCookie(cookie);
    }

    @Override
    public ApiResponse<Object> register(UserRegisterRequest request, HttpServletRequest httpServletRequest) {
        if (!allowRegister()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Register function has been disabled by admin");
        }

        String username = lowerTrim(request.username());
        String password = trimToNull(request.password());
        String email = lowerTrim(request.email());
        if (username == null || password == null || email == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parameter error");
        }

        if (!isCaptchaValid(request.captcha(), httpServletRequest)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid captcha");
        }
        if (existsUsername(username)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Username already exists");
        }
        if (existsEmail(email)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Email already exists");
        }

        String encoded = passwordEncoder.encode(password);
        Long userId = jdbcTemplate.queryForObject(
                """
                insert into "user"(username, email, password_hash, admin_type, problem_permission, is_disabled, create_time)
                values (?, ?, ?, 'Regular User', 'None', false, now())
                returning id
                """,
                Long.class,
                username,
                email,
                encoded
        );
        if (userId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Register failed");
        }

        jdbcTemplate.update(
                """
                insert into user_profile(user_id, acm_problems_status, oi_problems_status, role)
                values (?, cast(? as jsonb), cast(? as jsonb), ?)
                on conflict (user_id) do nothing
                """,
                userId,
                "{}",
                "{}",
                "Student"
        );

        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> usernameOrEmailCheck(UsernameOrEmailCheckRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", existsUsername(lowerTrim(request.username())));
        result.put("email", existsEmail(lowerTrim(request.email())));
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<Object> getProfile(String username, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        boolean showRealName = false;
        UserRow target;

        String queryUsername = lowerTrim(username);
        if (queryUsername != null) {
            target = findUserByUsername(queryUsername);
            if (target == null || target.disabled()) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "User does not exist");
            }
        } else {
            if (current == null) {
                return ApiResponse.success(null);
            }
            target = current;
            showRealName = true;
        }

        ProfileRow profile = findProfile(target.id());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", target.id());
        payload.put("username", target.username());
        payload.put("email", target.email());
        payload.put("admin_type", target.adminType());
        payload.put("problem_permission", target.problemPermission());
        payload.put("avatar", profile.avatar());
        payload.put("blog", profile.blog());
        payload.put("mood", profile.mood());
        payload.put("github", profile.github());
        payload.put("school", profile.school());
        payload.put("major", profile.major());
        payload.put("language", profile.language());
        payload.put("role", profile.role());
        payload.put("accepted_number", profile.acceptedNumber());
        payload.put("total_score", profile.totalScore());
        payload.put("submission_number", profile.submissionNumberLive());
        payload.put("accepted_submission_number", profile.acceptedSubmissionNumber());
        payload.put("acm_problems_status", parseJsonMap(profile.acmProblemsStatus()));
        payload.put("oi_problems_status", parseJsonMap(profile.oiProblemsStatus()));
        payload.put("real_name", showRealName ? profile.realName() : null);
        payload.put("recent_passed", loadRecentPassedProblems(target.id(), 10));
        payload.put("solved_by_difficulty", loadSolvedByDifficulty(target.id()));

        return ApiResponse.success(payload);
    }

    @Override
    public ApiResponse<Object> updateProfile(EditUserProfileRequest request, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        jdbcTemplate.update(
                """
                update user_profile set
                    real_name = coalesce(?, real_name),
                    avatar = coalesce(?, avatar),
                    blog = coalesce(?, blog),
                    mood = coalesce(?, mood),
                    github = coalesce(?, github),
                    school = coalesce(?, school),
                    major = coalesce(?, major),
                    language = coalesce(?, language)
                where user_id = ?
                """,
                trimToNull(request.realName()),
                trimToNull(request.avatar()),
                trimToNull(request.blog()),
                trimToNull(request.mood()),
                trimToNull(request.github()),
                trimToNull(request.school()),
                trimToNull(request.major()),
                trimToNull(request.language()),
                current.id()
        );
        return getProfile(null, authentication);
    }

    @Override
    public ApiResponse<Object> uploadAvatar(MultipartFile image, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (image == null || image.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid file content");
        }
        if (image.getSize() > 2L * 1024L * 1024L) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Picture is too large");
        }
        String originalName = trimToEmpty(image.getOriginalFilename()).toLowerCase(Locale.ROOT);
        String suffix = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : "";
        if (!ALLOWED_AVATAR_SUFFIX.contains(suffix)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Unsupported file format");
        }
        // SEC HIGH-5 (2026-05-02 渗透报告): 仅靠后缀名校验时，攻击者可上传任意 binary
        // (含 PHP/HTML/shellcode) 改名 .png 落地到 /public/avatar/。这里读 bytes 一次后
        // 用 ImageIO 真实解码图片，无法解码即判定非真图，failfast 拒绝。
        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid file content");
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid file content");
        }
        if (decoded == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "file is not a valid image");
        }
        String name = randomString(10) + suffix;
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String avatarPath = "/public/avatar/" + name;
        try {
            Path avatarDir = avatarDirectory();
            Files.createDirectories(avatarDir);
            Files.write(avatarDir.resolve(name), imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Upload Error");
        }
        jdbcTemplate.update(
                "update user_profile set avatar = ? where user_id = ?",
                avatarPath,
                current.id()
        );
        jdbcTemplate.update(
                """
                insert into sys_options(key, value, created_at, updated_at)
                values (?, cast(? as jsonb), now(), now())
                on conflict (key) do update
                set value = excluded.value,
                    updated_at = now()
                """,
                "avatar_blob:" + name,
                "{\"base64\":\"" + base64 + "\"}"
        );
        return ApiResponse.success("Succeeded");
    }

    private Path avatarDirectory() {
        Path uploadDir = Path.of(properties.getSystem().getUploadDir());
        Path parent = uploadDir.getParent();
        if (parent == null) {
            return uploadDir.resolveSibling("avatar");
        }
        return parent.resolve("avatar");
    }

    @Override
    public ApiResponse<Object> changePassword(UserChangePasswordRequest request, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String oldPassword = trimToNull(request.oldPassword());
        String newPassword = trimToNull(request.newPassword());
        if (oldPassword == null || newPassword == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid old password");
        }
        if (!passwordEncoder.matches(oldPassword, trimToEmpty(current.passwordHash()))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid old password");
        }
        if (current.twoFactorAuth()) {
            String tfaCode = trimToNull(request.tfaCode());
            if (tfaCode == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "tfa_required");
            }
            if (!tfaCode.equals(trimToEmpty(current.tfaToken()))) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid two factor verification code");
            }
        }
        jdbcTemplate.update("update \"user\" set password_hash = ? where id = ?", passwordEncoder.encode(newPassword), current.id());
        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> changeEmail(UserChangeEmailRequest request, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String password = trimToNull(request.password());
        String newEmail = lowerTrim(request.newEmail());
        if (password == null || newEmail == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Wrong password");
        }
        if (!passwordEncoder.matches(password, trimToEmpty(current.passwordHash()))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Wrong password");
        }
        if (current.twoFactorAuth()) {
            String tfaCode = trimToNull(request.tfaCode());
            if (tfaCode == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "tfa_required");
            }
            if (!tfaCode.equals(trimToEmpty(current.tfaToken()))) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid two factor verification code");
            }
        }
        if (existsEmail(newEmail) && !newEmail.equals(lowerTrim(current.email()))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "The email is owned by other account");
        }
        jdbcTemplate.update("update \"user\" set email = ? where id = ?", newEmail, current.id());
        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> applyResetPassword(ApplyResetPasswordRequest request,
                                                  Authentication authentication,
                                                  HttpServletRequest httpServletRequest) {
        if (resolveAuthUser(authentication) != null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "You have already logged in, are you kidding me? ");
        }
        if (!isCaptchaValid(request.captcha(), httpServletRequest)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid captcha");
        }
        String email = lowerTrim(request.email());
        if (email == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "User does not exist");
        }

        UserRow user = findUserByEmail(email);
        if (user == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "User does not exist");
        }

        Timestamp expire = Timestamp.from(Instant.now().plusSeconds(20 * 60));
        String token = randomString(32);
        jdbcTemplate.update(
                "update \"user\" set reset_password_token = ?, reset_password_token_expire_time = ? where id = ?",
                token,
                expire,
                user.id()
        );
        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> resetPassword(ResetPasswordRequest request, HttpServletRequest httpServletRequest) {
        if (!isCaptchaValid(request.captcha(), httpServletRequest)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid captcha");
        }
        String token = trimToNull(request.token());
        String password = trimToNull(request.password());
        if (token == null || password == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Token does not exist");
        }

        UserRow user = findUserByResetToken(token);
        if (user == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Token does not exist");
        }
        if (user.resetPasswordTokenExpireTime() == null || user.resetPasswordTokenExpireTime().toInstant().isBefore(Instant.now())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Token has expired");
        }

        jdbcTemplate.update(
                """
                update "user"
                set reset_password_token = null,
                    reset_password_token_expire_time = null,
                    password_hash = ?
                where id = ?
                """,
                passwordEncoder.encode(password),
                user.id()
        );
        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> checkTfaRequired(UsernameOrEmailCheckRequest request) {
        boolean required = false;
        String username = lowerTrim(request.username());
        if (username != null) {
            UserRow user = findUserByUsername(username);
            required = user != null && user.twoFactorAuth();
        }
        return ApiResponse.success(Map.of("result", required));
    }

    @Override
    public ApiResponse<Object> getTwoFactorQr(Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (current.twoFactorAuth()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "2FA is already turned on");
        }
        String secret = TotpUtils.generateSecret();
        jdbcTemplate.update("update \"user\" set tfa_token = ? where id = ?", secret, current.id());
        String issuer = trimToEmpty(properties.getWebsite().getName());
        return ApiResponse.success(TotpUtils.otpAuthUri(current.username(), secret, issuer));
    }

    @Override
    public ApiResponse<Object> enableTwoFactor(TwoFactorCodeRequest request, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String secret = trimToNull(current.tfaToken());
        String code = trimToNull(request.code());
        if (secret == null || code == null || !TotpUtils.verifyCode(secret, code)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid code");
        }
        jdbcTemplate.update("update \"user\" set two_factor_auth = true where id = ?", current.id());
        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> disableTwoFactor(TwoFactorCodeRequest request, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!current.twoFactorAuth()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "2FA is already turned off");
        }
        String code = trimToNull(request.code());
        String secret = trimToNull(current.tfaToken());
        if (secret == null || code == null || !TotpUtils.verifyCode(secret, code)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid code");
        }
        jdbcTemplate.update("update \"user\" set two_factor_auth = false, tfa_token = null where id = ?", current.id());
        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> listSessions(Authentication authentication, HttpServletRequest httpServletRequest) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        HttpSession session = httpServletRequest.getSession(false);
        if (session == null) {
            return ApiResponse.success(List.of());
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("current_session", true);
        item.put("ip", trimToEmpty((String) session.getAttribute("ip")));
        item.put("user_agent", trimToEmpty((String) session.getAttribute("user_agent")));
        item.put("last_activity", trimToEmpty((String) session.getAttribute("last_activity")));
        item.put("session_key", session.getId());
        return ApiResponse.success(List.of(item));
    }

    @Override
    public ApiResponse<Object> deleteSession(String sessionKey, Authentication authentication, HttpServletRequest httpServletRequest) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (trimToNull(sessionKey) == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parameter Error");
        }
        HttpSession session = httpServletRequest.getSession(false);
        if (session == null || !session.getId().equals(sessionKey)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid session_key");
        }
        removeSessionKey(current.id(), sessionKey);
        session.invalidate();
        return ApiResponse.success("Succeeded");
    }

    @Override
    public ApiResponse<Object> refreshProfileDisplayId(Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Object> refreshOpenApiAppkey(Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!current.openApi()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "OpenAPI function is truned off for you");
        }
        String appkey = randomString(24);
        jdbcTemplate.update("update \"user\" set open_api_appkey = ? where id = ?", appkey, current.id());
        return ApiResponse.success(Map.of("appkey", appkey));
    }

    @Override
    public ApiResponse<Object> issueSsoToken(Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String token = randomString(24);
        jdbcTemplate.update("update \"user\" set auth_token = ? where id = ?", token, current.id());
        return ApiResponse.success(Map.of("token", token));
    }

    @Override
    public ApiResponse<Object> resolveSsoToken(SsoTokenRequest request) {
        String token = trimToNull(request.token());
        if (token == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "User does not exist");
        }
        UserRow user = findUserByAuthToken(token);
        if (user == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "User does not exist");
        }
        ProfileRow profile = findProfile(user.id());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", user.username());
        payload.put("avatar", profile.avatar());
        payload.put("admin_type", user.adminType());
        return ApiResponse.success(payload);
    }

    @Override
    public ApiResponse<Object> issueCaptcha(HttpServletRequest httpServletRequest) {
        String code = randomDigits(4);
        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute(CAPTCHA_CODE_KEY, code);
        return ApiResponse.success(Map.of("captcha", code));
    }

    @Override
    public ApiResponse<Object> adminListUsers(Map<String, String> params, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null || !isFullAdmin(current.adminType())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        Long id = parseLong(params.get("id"));
        if (id != null) {
            Map<String, Object> user = findAdminUserById(id);
            if (user == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "User does not exist");
            }
            return ApiResponse.success(user);
        }

        String keyword = trimToNull(params.get("keyword"));
        int limit = parseInt(params.get("limit"), 10);
        int offset = parseInt(params.get("offset"), 0);
        if (limit < 1 || limit > 200) {
            limit = 10;
        }
        if (offset < 0) {
            offset = 0;
        }

        String where = "";
        List<Object> args = new ArrayList<>();
        if (keyword != null) {
            where = " where u.username ilike ? or u.email ilike ? or up.real_name ilike ?";
            String pattern = "%" + keyword + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        Long total = jdbcTemplate.queryForObject(
                "select count(*) from \"user\" u left join user_profile up on up.user_id = u.id" + where,
                Long.class,
                args.toArray()
        );

        List<Object> pagingArgs = new ArrayList<>(args);
        pagingArgs.add(limit);
        pagingArgs.add(offset);
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                select u.id, u.username, u.email, u.admin_type, u.problem_permission, u.is_disabled,
                       u.open_api, u.two_factor_auth, u.create_time, up.real_name
                from "user" u
                left join user_profile up on up.user_id = u.id
                """ + where + " order by u.create_time desc limit ? offset ?",
                (rs, rowNum) -> mapAdminUser(rs.getLong("id"), rs.getString("username"), rs.getString("email"),
                        rs.getString("admin_type"), rs.getString("problem_permission"), rs.getBoolean("is_disabled"),
                        rs.getBoolean("open_api"), rs.getBoolean("two_factor_auth"), rs.getString("real_name"),
                        rs.getTimestamp("create_time")),
                pagingArgs.toArray()
        );
        return ApiResponse.success(Map.of("results", results, "total", total == null ? 0 : total));
    }

    @Override
    public ApiResponse<Object> adminImportUsers(Map<String, Object> request, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null || !isFullAdmin(current.adminType())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Object usersRaw = request.get("users");
        if (!(usersRaw instanceof List<?> rows)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Error occurred while processing data");
        }
        for (Object rowRaw : rows) {
            if (!(rowRaw instanceof List<?> row) || row.size() != 4) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Error occurred while processing data");
            }
            String username = lowerTrim(String.valueOf(row.get(0)));
            String password = trimToNull(String.valueOf(row.get(1)));
            String email = lowerTrim(String.valueOf(row.get(2)));
            String realName = trimToNull(String.valueOf(row.get(3)));
            if (username == null || password == null || username.length() > 32) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Error occurred while processing data");
            }
            if (existsUsername(username)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "duplicate key value violates unique constraint username");
            }
            Long id = jdbcTemplate.queryForObject(
                    """
                    insert into "user"(username, email, password_hash, admin_type, problem_permission, is_disabled, create_time)
                    values (?, ?, ?, 'Regular User', 'None', false, now())
                    returning id
                    """,
                    Long.class,
                    username,
                    email,
                    passwordEncoder.encode(password)
            );
            if (id != null) {
                jdbcTemplate.update(
                        """
                        insert into user_profile(user_id, real_name, acm_problems_status, oi_problems_status, role)
                        values (?, ?, cast(? as jsonb), cast(? as jsonb), 'Student')
                        on conflict (user_id) do nothing
                        """,
                        id,
                        realName,
                        "{}",
                        "{}"
                );
            }
        }
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Object> adminEditUser(Map<String, Object> request, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null || !isFullAdmin(current.adminType())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long id = parseLong(String.valueOf(request.get("id")));
        if (id == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "User does not exist");
        }
        Map<String, Object> existed = findAdminUserById(id);
        if (existed == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "User does not exist");
        }

        String username = lowerTrim(stringValue(request.get("username")));
        String email = lowerTrim(stringValue(request.get("email")));
        String adminType = trimToNull(stringValue(request.get("admin_type")));
        String problemPermission = trimToNull(stringValue(request.get("problem_permission")));
        String password = trimToNull(stringValue(request.get("password")));
        String realName = trimToNull(stringValue(request.get("real_name")));
        Boolean isDisabled = parseBoolean(request.get("is_disabled"));
        Boolean openApi = parseBoolean(request.get("open_api"));
        Boolean twoFactor = parseBoolean(request.get("two_factor_auth"));

        if (username == null || email == null || adminType == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parameter error");
        }
        if (existsUsernameExcludingId(username, id)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Username already exists");
        }
        if (existsEmailExcludingId(email, id)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Email already exists");
        }

        String finalProblemPermission = problemPermission;
        if (isFullAdmin(adminType)) {
            finalProblemPermission = "All";
        } else if ("Teacher".equals(adminType)) {
            finalProblemPermission = "Own";
        } else if ("Regular User".equals(adminType)) {
            finalProblemPermission = "None";
        } else if (finalProblemPermission == null) {
            finalProblemPermission = "Own";
        }

        String oldUsername = String.valueOf(existed.get("username"));
        jdbcTemplate.update(
                """
                update "user" set
                    username = ?,
                    email = ?,
                    admin_type = ?,
                    problem_permission = ?,
                    is_disabled = ?,
                    open_api = ?,
                    open_api_appkey = case when ? then coalesce(open_api_appkey, ?) else null end,
                    two_factor_auth = ?,
                    tfa_token = case when ? then coalesce(tfa_token, ?) else null end
                where id = ?
                """,
                username,
                email,
                adminType,
                finalProblemPermission,
                isDisabled != null && isDisabled,
                openApi != null && openApi,
                openApi != null && openApi,
                randomString(24),
                twoFactor != null && twoFactor,
                twoFactor != null && twoFactor,
                randomString(16),
                id
        );
        if (password != null) {
            jdbcTemplate.update("update \"user\" set password_hash = ? where id = ?", passwordEncoder.encode(password), id);
        }
        jdbcTemplate.update("update submission set username = ? where username = ?", username, oldUsername);
        jdbcTemplate.update("update user_profile set real_name = ? where user_id = ?", realName, id);
        return ApiResponse.success(findAdminUserById(id));
    }

    @Override
    public ApiResponse<Object> adminDeleteUsers(String id, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null || !isFullAdmin(current.adminType())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String raw = trimToNull(id);
        if (raw == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid Parameter, id is required");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String item : raw.split(",")) {
            Long parsed = parseLong(item);
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        if (ids.contains(current.id())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Current user can not be deleted");
        }
        if (!ids.isEmpty()) {
            String placeholders = String.join(",", ids.stream().map(v -> "?").toList());
            jdbcTemplate.update("delete from \"user\" where id in (" + placeholders + ")", ids.toArray());
        }
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Object> adminGenerateUsers(Map<String, Object> request, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null || !isFullAdmin(current.adminType())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String prefix = trimToEmpty(stringValue(request.get("prefix")));
        String suffix = trimToEmpty(stringValue(request.get("suffix")));
        Integer numberFrom = parseIntObj(request.get("number_from"));
        Integer numberTo = parseIntObj(request.get("number_to"));
        Integer passwordLength = parseIntObj(request.get("password_length"));
        if (numberFrom == null || numberTo == null || passwordLength == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parameter error");
        }
        int numberMaxLength = Math.max(String.valueOf(numberFrom).length(), String.valueOf(numberTo).length());
        if (numberMaxLength + prefix.length() + suffix.length() > 32) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Username should not more than 32 characters");
        }
        if (numberFrom > numberTo) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Start number must be lower than end number");
        }
        if (passwordLength < 6) {
            passwordLength = 6;
        }
        if (passwordLength > 32) {
            passwordLength = 32;
        }

        StringBuilder csv = new StringBuilder("Username,Password\n");
        String fileId = randomString(8);
        for (int number = numberFrom; number <= numberTo; number++) {
            String username = (prefix + number + suffix).toLowerCase(Locale.ROOT);
            String rawPassword = randomString(passwordLength);
            if (existsUsername(username)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "duplicate key value violates unique constraint username");
            }
            Long userId = jdbcTemplate.queryForObject(
                    """
                    insert into "user"(username, password_hash, admin_type, problem_permission, is_disabled, create_time)
                    values (?, ?, 'Regular User', 'None', false, now())
                    returning id
                    """,
                    Long.class,
                    username,
                    passwordEncoder.encode(rawPassword)
            );
            if (userId != null) {
                jdbcTemplate.update(
                        """
                        insert into user_profile(user_id, acm_problems_status, oi_problems_status, role)
                        values (?, cast(? as jsonb), cast(? as jsonb), 'Student')
                        on conflict (user_id) do nothing
                        """,
                        userId,
                        "{}",
                        "{}"
                );
            }
            csv.append(username).append(",").append(rawPassword).append("\n");
        }
        jdbcTemplate.update(
                """
                insert into generated_user_file(file_id, content, expire_time)
                values (?, ?, ?)
                on conflict (file_id) do update
                set content = excluded.content,
                    expire_time = excluded.expire_time
                """,
                fileId,
                csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS))
        );
        return ApiResponse.success(Map.of("file_id", fileId));
    }

    @Override
    public byte[] adminDownloadGeneratedUsers(String fileId, Authentication authentication) {
        UserRow current = resolveAuthUser(authentication);
        if (current == null || !isFullAdmin(current.adminType())) {
            return null;
        }
        String normalizedFileId = trimToNull(fileId);
        if (normalizedFileId == null || !normalizedFileId.matches("^[a-zA-Z0-9]+$")) {
            return null;
        }
        jdbcTemplate.update("delete from generated_user_file where expire_time < now()");
        try {
            byte[] content = jdbcTemplate.queryForObject(
                    "select content from generated_user_file where file_id = ?",
                    byte[].class,
                    normalizedFileId
            );
            jdbcTemplate.update("delete from generated_user_file where file_id = ?", normalizedFileId);
            return content;
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private UserRow resolveAuthUser(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }
        return findUserByUsername(lowerTrim(authentication.getName()));
    }

    private UserRow findUserByUsername(String username) {
        if (username == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select id, username, email, admin_type, problem_permission, is_disabled,
                           password_hash, two_factor_auth, tfa_token, open_api, open_api_appkey,
                           reset_password_token_expire_time
                    from "user"
                    where lower(username) = ?
                    """,
                    (rs, rowNum) -> new UserRow(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("admin_type"),
                            rs.getString("problem_permission"),
                            rs.getBoolean("is_disabled"),
                            rs.getString("password_hash"),
                            rs.getBoolean("two_factor_auth"),
                            rs.getString("tfa_token"),
                            rs.getBoolean("open_api"),
                            rs.getString("open_api_appkey"),
                            rs.getTimestamp("reset_password_token_expire_time")
                    ),
                    username
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private UserRow findUserByEmail(String email) {
        if (email == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select id, username, email, admin_type, problem_permission, is_disabled,
                           password_hash, two_factor_auth, tfa_token, open_api, open_api_appkey,
                           reset_password_token_expire_time
                    from "user"
                    where lower(email) = ?
                    """,
                    (rs, rowNum) -> new UserRow(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("admin_type"),
                            rs.getString("problem_permission"),
                            rs.getBoolean("is_disabled"),
                            rs.getString("password_hash"),
                            rs.getBoolean("two_factor_auth"),
                            rs.getString("tfa_token"),
                            rs.getBoolean("open_api"),
                            rs.getString("open_api_appkey"),
                            rs.getTimestamp("reset_password_token_expire_time")
                    ),
                    email
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private UserRow findUserByResetToken(String token) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select id, username, email, admin_type, problem_permission, is_disabled,
                           password_hash, two_factor_auth, tfa_token, open_api, open_api_appkey,
                           reset_password_token_expire_time
                    from "user"
                    where reset_password_token = ?
                    """,
                    (rs, rowNum) -> new UserRow(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("admin_type"),
                            rs.getString("problem_permission"),
                            rs.getBoolean("is_disabled"),
                            rs.getString("password_hash"),
                            rs.getBoolean("two_factor_auth"),
                            rs.getString("tfa_token"),
                            rs.getBoolean("open_api"),
                            rs.getString("open_api_appkey"),
                            rs.getTimestamp("reset_password_token_expire_time")
                    ),
                    token
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private UserRow findUserByAuthToken(String token) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select id, username, email, admin_type, problem_permission, is_disabled,
                           password_hash, two_factor_auth, tfa_token, open_api, open_api_appkey,
                           reset_password_token_expire_time
                    from "user"
                    where auth_token = ?
                    """,
                    (rs, rowNum) -> new UserRow(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("admin_type"),
                            rs.getString("problem_permission"),
                            rs.getBoolean("is_disabled"),
                            rs.getString("password_hash"),
                            rs.getBoolean("two_factor_auth"),
                            rs.getString("tfa_token"),
                            rs.getBoolean("open_api"),
                            rs.getString("open_api_appkey"),
                            rs.getTimestamp("reset_password_token_expire_time")
                    ),
                    token
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private ProfileRow findProfile(long userId) {
        try {
            ProfileRow raw = jdbcTemplate.queryForObject(
                    """
                    select real_name, avatar, blog, mood, github, school, major, language, role,
                           accepted_number, total_score, submission_number,
                           (select count(*) from submission s where s.user_id = up.user_id) as submission_number_live,
                           (select count(*) from submission s where s.user_id = up.user_id and s.result = 0) as accepted_submission_number,
                           acm_problems_status::text as acm_json,
                           oi_problems_status::text as oi_json
                    from user_profile up
                    where up.user_id = ?
                    """,
                    (rs, rowNum) -> new ProfileRow(
                            rs.getString("real_name"),
                            rs.getString("avatar"),
                            rs.getString("blog"),
                            rs.getString("mood"),
                            rs.getString("github"),
                            rs.getString("school"),
                            rs.getString("major"),
                            rs.getString("language"),
                            rs.getString("role"),
                            rs.getInt("accepted_number"),
                            rs.getLong("total_score"),
                            rs.getInt("submission_number"),
                            rs.getInt("submission_number_live"),
                            rs.getInt("accepted_submission_number"),
                            rs.getString("acm_json"),
                            rs.getString("oi_json")
                    ),
                    userId
            );
            if (raw == null) {
                return defaultProfile();
            }
            return new ProfileRow(
                    raw.realName(),
                    normalizeAvatarForUser(userId, raw.avatar()),
                    raw.blog(),
                    raw.mood(),
                    raw.github(),
                    raw.school(),
                    raw.major(),
                    raw.language(),
                    raw.role(),
                    raw.acceptedNumber(),
                    raw.totalScore(),
                    raw.submissionNumber(),
                    raw.submissionNumberLive(),
                    raw.acceptedSubmissionNumber(),
                    raw.acmProblemsStatus(),
                    raw.oiProblemsStatus()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return defaultProfile();
        }
    }

    private ProfileRow defaultProfile() {
        return new ProfileRow(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Student",
                0,
                0,
                0,
                0,
                0,
                "{}",
                "{}"
        );
    }

    private String normalizeAvatarForUser(long userId, String avatarRaw) {
        String avatar = trimToNull(avatarRaw);
        if (avatar == null) {
            return "";
        }
        if (!avatar.startsWith("/public/avatar/")) {
            return avatar;
        }
        String filename = trimToNull(avatar.substring("/public/avatar/".length()));
        if (filename == null || filename.contains("/") || filename.contains("..")) {
            clearAvatarReference(userId, avatarRaw);
            return "";
        }
        if (avatarFileExists(filename) || avatarBlobExists(filename)) {
            return "/public/avatar/" + filename;
        }
        clearAvatarReference(userId, avatarRaw);
        return "";
    }

    private void clearAvatarReference(long userId, String avatarRaw) {
        jdbcTemplate.update(
                "update user_profile set avatar = '' where user_id = ? and avatar = ?",
                userId,
                avatarRaw
        );
    }

    private boolean avatarFileExists(String filename) {
        try {
            Path avatarRoot = avatarDirectory().toAbsolutePath().normalize();
            Path file = avatarRoot.resolve(filename).normalize();
            if (!file.startsWith(avatarRoot)) {
                return false;
            }
            return Files.exists(file) && Files.isRegularFile(file);
        } catch (Exception e) {
            log.warn("avatarFileExists: check failed for filename={}", filename, e);
            return false;
        }
    }

    private boolean avatarBlobExists(String filename) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_options where key = ?",
                Integer.class,
                "avatar_blob:" + filename
        );
        return count != null && count > 0;
    }

    private List<Map<String, Object>> loadRecentPassedProblems(long userId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 20));
        return jdbcTemplate.query(
                """
                select p.id, p._id, p.title, max(s.create_time) as pass_time
                from submission s
                join problem p on p.id = s.problem_id
                where s.user_id = ? and s.result = 0
                group by p.id, p._id, p.title
                order by pass_time desc
                limit ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("_id", rs.getString("_id"));
                    item.put("title", rs.getString("title"));
                    item.put("pass_time", toIso(rs.getTimestamp("pass_time")));
                    return item;
                },
                userId,
                normalizedLimit
        );
    }

    private Map<String, Integer> loadSolvedByDifficulty(long userId) {
        Map<String, Integer> solvedByDiff = new LinkedHashMap<>();
        solvedByDiff.put("Low", 0);
        solvedByDiff.put("Mid", 0);
        solvedByDiff.put("High", 0);

        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select coalesce(p.difficulty, 'Low') as difficulty, count(distinct p.id) as solved
                from submission s
                join problem p on p.id = s.problem_id
                where s.user_id = ? and s.result = 0
                group by coalesce(p.difficulty, 'Low')
                """,
                (rs, rowNum) -> {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("difficulty", rs.getString("difficulty"));
                    one.put("solved", rs.getInt("solved"));
                    return one;
                },
                userId
        );

        for (Map<String, Object> row : rows) {
            String difficulty = trimToEmpty((String) row.get("difficulty"));
            Integer solved = (Integer) row.get("solved");
            if ("Mid".equalsIgnoreCase(difficulty)) {
                solvedByDiff.put("Mid", solved == null ? 0 : solved);
            } else if ("High".equalsIgnoreCase(difficulty)) {
                solvedByDiff.put("High", solved == null ? 0 : solved);
            } else {
                solvedByDiff.put("Low", solvedByDiff.get("Low") + (solved == null ? 0 : solved));
            }
        }
        return solvedByDiff;
    }

    private boolean existsUsername(String username) {
        if (username == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from \"user\" where lower(username) = ?",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    private boolean existsEmail(String email) {
        if (email == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from \"user\" where lower(email) = ?",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }

    private boolean existsUsernameExcludingId(String username, Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from \"user\" where lower(username) = ? and id <> ?",
                Integer.class,
                username,
                id
        );
        return count != null && count > 0;
    }

    private boolean existsEmailExcludingId(String email, Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from \"user\" where lower(email) = ? and id <> ?",
                Integer.class,
                email,
                id
        );
        return count != null && count > 0;
    }

    private Map<String, Object> findAdminUserById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select u.id, u.username, u.email, u.admin_type, u.problem_permission, u.is_disabled,
                           u.open_api, u.two_factor_auth, u.create_time, up.real_name
                    from "user" u
                    left join user_profile up on up.user_id = u.id
                    where u.id = ?
                    """,
                    (rs, rowNum) -> mapAdminUser(rs.getLong("id"), rs.getString("username"), rs.getString("email"),
                            rs.getString("admin_type"), rs.getString("problem_permission"), rs.getBoolean("is_disabled"),
                            rs.getBoolean("open_api"), rs.getBoolean("two_factor_auth"), rs.getString("real_name"),
                            rs.getTimestamp("create_time")),
                    id
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private static boolean isFullAdmin(String adminType) {
        return "Admin".equals(adminType);
    }

    private Map<String, Object> mapAdminUser(
            long id,
            String username,
            String email,
            String adminType,
            String problemPermission,
            boolean isDisabled,
            boolean openApi,
            boolean twoFactorAuth,
            String realName,
            Timestamp createTime
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("username", username);
        data.put("email", email);
        data.put("admin_type", adminType);
        data.put("problem_permission", problemPermission);
        data.put("is_disabled", isDisabled);
        data.put("open_api", openApi);
        data.put("two_factor_auth", twoFactorAuth);
        data.put("real_name", realName);
        data.put("create_time", createTime == null ? null : createTime.toInstant().toString());
        return data;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer parseIntObj(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseIntObj: parse failed for {}", value, e);
            return null;
        }
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseInt: invalid raw={}, using fallback {}", raw, fallback, e);
            return fallback;
        }
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseLong: invalid raw={}", raw, e);
            return null;
        }
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private boolean allowRegister() {
        try {
            String raw = jdbcTemplate.queryForObject(
                    "select value::text from sys_options where key = 'website_config'",
                    String.class
            );
            if (raw == null) {
                return true;
            }
            Map<String, Object> json = parseJsonMap(raw);
            Object allow = json.get("allow_register");
            if (allow instanceof Boolean bool) {
                return bool;
            }
            if (allow != null) {
                return Boolean.parseBoolean(String.valueOf(allow));
            }
            return true;
        } catch (EmptyResultDataAccessException ignored) {
            return true;
        }
    }

    private boolean isCaptchaValid(String captcha, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object code = session.getAttribute(CAPTCHA_CODE_KEY);
        String expected = code == null ? null : String.valueOf(code);
        String provided = trimToNull(captcha);
        return expected != null && provided != null && expected.equalsIgnoreCase(provided);
    }

    private String clientIp(HttpServletRequest request) {
        String xff = trimToNull(request.getHeader("X-Forwarded-For"));
        if (xff != null) {
            return xff.split(",")[0].trim();
        }
        return trimToEmpty(request.getRemoteAddr());
    }

    private void upsertSessionKey(long userId, String sessionId) {
        if (trimToNull(sessionId) == null) {
            return;
        }
        List<String> keys = loadSessionKeys(userId);
        if (!keys.contains(sessionId)) {
            keys.add(sessionId);
            saveSessionKeys(userId, keys);
        }
    }

    private void removeSessionKey(long userId, String sessionId) {
        List<String> keys = loadSessionKeys(userId);
        if (keys.remove(sessionId)) {
            saveSessionKeys(userId, keys);
        }
    }

    private List<String> loadSessionKeys(long userId) {
        try {
            String raw = jdbcTemplate.queryForObject(
                    "select session_keys::text from \"user\" where id = ?",
                    String.class,
                    userId
            );
            if (raw == null || raw.isBlank()) {
                return new ArrayList<>();
            }
            List<Object> rawList = objectMapper.readValue(raw, new TypeReference<>() {});
            List<String> keys = new ArrayList<>();
            for (Object item : rawList) {
                keys.add(String.valueOf(item));
            }
            return keys;
        } catch (Exception e) {
            log.debug("loadSessionKeys: JSON parse failed for userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    private void saveSessionKeys(long userId, List<String> keys) {
        try {
            jdbcTemplate.update(
                    "update \"user\" set session_keys = cast(? as jsonb) where id = ?",
                    objectMapper.writeValueAsString(keys),
                    userId
            );
        } catch (JsonProcessingException ignored) {
        }
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private String toIso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String lowerTrim(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String randomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private String randomDigits(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ('0' + random.nextInt(10)));
        }
        return builder.toString();
    }

    private record UserRow(
            long id,
            String username,
            String email,
            String adminType,
            String problemPermission,
            boolean disabled,
            String passwordHash,
            boolean twoFactorAuth,
            String tfaToken,
            boolean openApi,
            String openApiAppkey,
            Timestamp resetPasswordTokenExpireTime
    ) {
    }

    private record ProfileRow(
            String realName,
            String avatar,
            String blog,
            String mood,
            String github,
            String school,
            String major,
            String language,
            String role,
            int acceptedNumber,
            long totalScore,
            int submissionNumber,
            int submissionNumberLive,
            int acceptedSubmissionNumber,
            String acmProblemsStatus,
            String oiProblemsStatus
    ) {
    }
}

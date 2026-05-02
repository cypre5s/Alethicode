package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.account.AccountAdminDomainService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AccountAdminDomainService accountAdminDomainService;

    public AdminAccountController(AccountAdminDomainService accountAdminDomainService) {
        this.accountAdminDomainService = accountAdminDomainService;
    }

    @GetMapping({
            "/api/admin/users", "/api/admin/users/"
    })
    public ApiResponse<Object> listUsers(@RequestParam Map<String, String> params, Authentication authentication) {
        return accountAdminDomainService.adminListUsers(params, authentication);
    }

    @PostMapping({
            "/api/admin/users", "/api/admin/users/"
    })
    public ApiResponse<Object> importUsers(@RequestBody Map<String, Object> request, Authentication authentication) {
        return accountAdminDomainService.adminImportUsers(request, authentication);
    }

    @PutMapping({
            "/api/admin/users", "/api/admin/users/"
    })
    public ApiResponse<Object> editUser(@RequestBody Map<String, Object> request, Authentication authentication) {
        return accountAdminDomainService.adminEditUser(request, authentication);
    }

    @DeleteMapping({
            "/api/admin/users", "/api/admin/users/"
    })
    public ApiResponse<Object> deleteUsers(@RequestParam(name = "id", required = false) String id,
                                           Authentication authentication) {
        return accountAdminDomainService.adminDeleteUsers(id, authentication);
    }

    @PostMapping({
            "/api/admin/generate-user", "/api/admin/generate-user/"
    })
    public ApiResponse<Object> generateUser(@RequestBody Map<String, Object> request, Authentication authentication) {
        return accountAdminDomainService.adminGenerateUsers(request, authentication);
    }

    @GetMapping({
            "/api/admin/generate-user", "/api/admin/generate-user/"
    })
    public ResponseEntity<?> downloadGeneratedUser(@RequestParam(name = "file_id", required = false) String fileId,
                                                   Authentication authentication) {
        byte[] content = accountAdminDomainService.adminDownloadGeneratedUsers(fileId, authentication);
        if (content == null) {
            return ResponseEntity.ok(ApiResponse.error("error", "File does not exist"));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.xlsx")
                .contentType(MediaType.parseMediaType("application/xlsx"))
                .body(content);
    }
}

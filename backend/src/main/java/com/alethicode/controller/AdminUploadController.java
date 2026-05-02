package com.alethicode.controller;

import com.alethicode.service.admin.AdminUploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping
@PreAuthorize("hasRole('ADMIN')")
public class AdminUploadController {

    private final AdminUploadService adminUploadService;

    public AdminUploadController(AdminUploadService adminUploadService) {
        this.adminUploadService = adminUploadService;
    }

    @PostMapping({
            "/api/admin/upload-image",
            "/api/admin/upload-image/"
    })
    public Map<String, Object> uploadImage(
            @RequestPart(name = "image", required = false) MultipartFile image
    ) {
        return adminUploadService.uploadImage(image);
    }

    @PostMapping({
            "/api/admin/upload-file",
            "/api/admin/upload-file/"
    })
    public Map<String, Object> uploadFile(
            @RequestPart(name = "file", required = false) MultipartFile file
    ) {
        return adminUploadService.uploadFile(file);
    }
}

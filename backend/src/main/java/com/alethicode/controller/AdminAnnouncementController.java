package com.alethicode.controller;

import com.alethicode.dto.request.AnnouncementCreateRequest;
import com.alethicode.dto.request.AnnouncementEditRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.announcement.AnnouncementService;
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
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping({
            "/api/admin/announcements", "/api/admin/announcements/"
    })
    public ApiResponse<Object> list(@RequestParam Map<String, String> params, Authentication authentication) {
        return announcementService.listAdmin(params, authentication);
    }

    @PostMapping({
            "/api/admin/announcements", "/api/admin/announcements/"
    })
    public ApiResponse<Object> create(@RequestBody AnnouncementCreateRequest request, Authentication authentication) {
        return announcementService.create(request, authentication);
    }

    @PutMapping({
            "/api/admin/announcements", "/api/admin/announcements/"
    })
    public ApiResponse<Object> edit(@RequestBody AnnouncementEditRequest request, Authentication authentication) {
        return announcementService.edit(request, authentication);
    }

    @DeleteMapping({
            "/api/admin/announcements", "/api/admin/announcements/"
    })
    public ApiResponse<Object> delete(@RequestParam(name = "id", required = false) String id,
                                      Authentication authentication) {
        return announcementService.delete(id, authentication);
    }
}

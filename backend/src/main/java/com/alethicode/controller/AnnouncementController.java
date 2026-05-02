package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.announcement.AnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping({
            "/api/announcements", "/api/announcements/"
    })
    public ApiResponse<Object> list(@RequestParam Map<String, String> params) {
        return announcementService.listPublic(params);
    }
}

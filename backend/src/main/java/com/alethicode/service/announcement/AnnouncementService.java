package com.alethicode.service.announcement;

import com.alethicode.dto.request.AnnouncementCreateRequest;
import com.alethicode.dto.request.AnnouncementEditRequest;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface AnnouncementService {

    ApiResponse<Object> listPublic(Map<String, String> params);

    ApiResponse<Object> listAdmin(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> create(AnnouncementCreateRequest request, Authentication authentication);

    ApiResponse<Object> edit(AnnouncementEditRequest request, Authentication authentication);

    ApiResponse<Object> delete(String id, Authentication authentication);
}

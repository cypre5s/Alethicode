package com.alethicode.service.admin;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AdminUploadService {

    Map<String, Object> uploadImage(MultipartFile image);

    Map<String, Object> uploadFile(MultipartFile file);
}

package com.alethicode.service.languagepack;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentNormalizationService {

    void uploadAndNormalize(Long taskId, List<MultipartFile> files);
}

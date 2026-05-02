package com.alethicode.service.admin.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.admin.AdminUploadService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminUploadServiceImpl implements AdminUploadService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            .toCharArray();
    private static final Set<String> IMAGE_SUFFIX_WHITELIST = Set.of(
            ".gif", ".jpg", ".jpeg", ".bmp", ".png"
    );

    /**
     * 管理员普通文件上传的白名单。显式禁止可执行/脚本后缀（.exe/.sh/.jsp/.php 等），
     * 保留文档、图片、打包类型。见 BUG #44。
     */
    private static final Set<String> FILE_SUFFIX_WHITELIST = Set.of(
            ".gif", ".jpg", ".jpeg", ".bmp", ".png", ".svg", ".webp",
            ".pdf", ".txt", ".md", ".csv", ".tsv", ".json", ".yaml", ".yml", ".xml",
            ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx",
            ".zip", ".tar", ".gz", ".tgz", ".7z", ".rar",
            ".mp4", ".mp3", ".wav", ".webm", ".ogg",
            ".py", ".c", ".cpp", ".java", ".js", ".ts"
    );

    private final AlethicodeProperties properties;

    public AdminUploadServiceImpl(AlethicodeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, Object> uploadImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return imageError("Upload failed");
        }

        String suffix = suffix(image.getOriginalFilename());
        if (!IMAGE_SUFFIX_WHITELIST.contains(suffix)) {
            return imageError("Unsupported file format");
        }

        String filename = randomString(10) + suffix;
        try {
            saveFile(image, filename);
        } catch (IOException exception) {
            return imageError("Upload Error");
        }

        return Map.of(
                "success", true,
                "msg", "Success",
                "file_path", properties.getSystem().getUploadPrefix() + "/" + filename
        );
    }

    @Override
    public Map<String, Object> uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Map.of(
                    "success", false,
                    "msg", "Upload failed"
            );
        }

        String suffix = suffix(file.getOriginalFilename());
        if (suffix.isEmpty() || !FILE_SUFFIX_WHITELIST.contains(suffix)) {
            return Map.of(
                    "success", false,
                    "msg", "Unsupported file format"
            );
        }
        String filename = randomString(10) + suffix;

        try {
            saveFile(file, filename);
        } catch (IOException exception) {
            return Map.of(
                    "success", false,
                    "msg", "Upload Error"
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("msg", "Success");
        response.put("file_path", properties.getSystem().getUploadPrefix() + "/" + filename);
        response.put("file_name", file.getOriginalFilename());
        return response;
    }

    private void saveFile(MultipartFile file, String filename) throws IOException {
        Path uploadDir = Path.of(properties.getSystem().getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IOException("upload target escapes uploadDir: " + filename);
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    private Map<String, Object> imageError(String message) {
        return Map.of(
                "success", false,
                "msg", message,
                "file_path", ""
        );
    }

    private String suffix(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return originalFilename.substring(index).toLowerCase(Locale.ROOT);
    }

    private String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM_CHARS[RANDOM.nextInt(RANDOM_CHARS.length)]);
        }
        return builder.toString();
    }
}

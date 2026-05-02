package com.alethicode.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@RestController
public class PublicAssetController {

    private static final Logger log = LoggerFactory.getLogger(PublicAssetController.class);

    @Nullable
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;

    public PublicAssetController(@Nullable JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AlethicodeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @GetMapping({"/public/website/favicon.ico"})
    public ResponseEntity<byte[]> websiteFavicon() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/public/avatar/{filename:.+}"})
    public ResponseEntity<byte[]> avatar(@PathVariable("filename") String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("..") || filename.contains("\\")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Path avatarDir = avatarDirectory().toAbsolutePath().normalize();
        Path localFile = avatarDir.resolve(filename).normalize();
        if (!localFile.startsWith(avatarDir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (Files.exists(localFile)) {
            try {
                byte[] bytes = Files.readAllBytes(localFile);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(Duration.ofHours(12)).cachePublic().getHeaderValue())
                        .contentType(contentTypeForName(filename))
                        .body(bytes);
            } catch (Exception e) {
                log.warn("avatar: failed to read local file filename={}", filename, e);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }

        if (jdbcTemplate == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String raw = jdbcTemplate.query(
                "select value::text from sys_options where key = ? limit 1",
                rs -> rs.next() ? rs.getString(1) : null,
                "avatar_blob:" + filename
        );
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            Map<String, Object> data = objectMapper.readValue(raw, new TypeReference<>() {});
            Object base64Obj = data.get("base64");
            if (!(base64Obj instanceof String base64) || base64.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(Duration.ofHours(12)).cachePublic().getHeaderValue())
                    .contentType(contentTypeForName(filename))
                    .body(bytes);
        } catch (Exception e) {
            log.warn("avatar: failed to load from database blob filename={}", filename, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private Path avatarDirectory() {
        Path uploadDir = Path.of(properties.getSystem().getUploadDir());
        Path parent = uploadDir.getParent();
        if (parent == null) {
            return uploadDir.resolveSibling("avatar");
        }
        return parent.resolve("avatar");
    }

    private MediaType contentTypeForName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}

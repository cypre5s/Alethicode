package com.alethicode.service.admin.impl;

import com.alethicode.config.AlethicodeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUploadServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadImageShouldValidateSuffixAndSaveToConfiguredDirectory() throws Exception {
        AlethicodeProperties properties = new AlethicodeProperties();
        properties.getSystem().setUploadDir(tempDir.toString());
        properties.getSystem().setUploadPrefix("/public/upload");
        AdminUploadServiceImpl service = new AdminUploadServiceImpl(properties);

        Map<String, Object> invalid = service.uploadImage(new MockMultipartFile(
                "image",
                "editor.txt",
                "text/plain",
                "bad".getBytes()
        ));
        assertThat(invalid)
                .containsEntry("success", false)
                .containsEntry("msg", "Unsupported file format")
                .containsEntry("file_path", "");

        Map<String, Object> ok = service.uploadImage(new MockMultipartFile(
                "image",
                "editor.png",
                "image/png",
                "png-content".getBytes()
        ));

        assertThat(ok)
                .containsEntry("success", true)
                .containsEntry("msg", "Success");
        String filePath = String.valueOf(ok.get("file_path"));
        assertThat(filePath).startsWith("/public/upload/").endsWith(".png");

        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        Path saved = tempDir.resolve(fileName);
        assertThat(Files.exists(saved)).isTrue();
        assertThat(Files.readAllBytes(saved)).isEqualTo("png-content".getBytes());
    }

    @Test
    void uploadFileShouldSaveAndReturnOriginalFilename() throws Exception {
        AlethicodeProperties properties = new AlethicodeProperties();
        properties.getSystem().setUploadDir(tempDir.toString());
        properties.getSystem().setUploadPrefix("/public/upload");
        AdminUploadServiceImpl service = new AdminUploadServiceImpl(properties);

        Map<String, Object> missing = service.uploadFile(null);
        assertThat(missing)
                .containsEntry("success", false)
                .containsEntry("msg", "Upload failed");

        Map<String, Object> ok = service.uploadFile(new MockMultipartFile(
                "file",
                "lesson.pdf",
                "application/pdf",
                "pdf-content".getBytes()
        ));

        assertThat(ok)
                .containsEntry("success", true)
                .containsEntry("msg", "Success")
                .containsEntry("file_name", "lesson.pdf");
        String filePath = String.valueOf(ok.get("file_path"));
        assertThat(filePath).startsWith("/public/upload/").endsWith(".pdf");

        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        Path saved = tempDir.resolve(fileName);
        assertThat(Files.exists(saved)).isTrue();
        assertThat(Files.readAllBytes(saved)).isEqualTo("pdf-content".getBytes());
    }
}

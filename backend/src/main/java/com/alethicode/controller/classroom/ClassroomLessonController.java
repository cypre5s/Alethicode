package com.alethicode.controller.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomLessonDomainService;
import com.alethicode.service.classroom.ClassroomLessonService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping
public class ClassroomLessonController {

    private final ClassroomLessonDomainService classroomLessonDomainService;

    public ClassroomLessonController(ClassroomLessonDomainService classroomLessonDomainService) {
        this.classroomLessonDomainService = classroomLessonDomainService;
    }

    @GetMapping({
            "/api/classroom/{classroomId}/lessons", "/api/classroom/{classroomId}/lessons/"
    })
    public ApiResponse<Object> lessonList(@PathVariable String classroomId, Authentication authentication) {
        return classroomLessonDomainService.lessonList(classroomId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/lessons", "/api/classroom/{classroomId}/lessons/"
    })
    public ApiResponse<Object> lessonCreate(@PathVariable String classroomId,
                                            @RequestParam(name = "file", required = false) MultipartFile file,
                                            @RequestParam(name = "title", required = false) String title,
                                            @RequestParam(name = "notes", required = false) String notes,
                                            Authentication authentication) {
        return classroomLessonDomainService.lessonCreate(classroomId, file, title, notes, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/lessons/{lessonId}", "/api/classroom/{classroomId}/lessons/{lessonId}/"
    })
    public ApiResponse<Object> lessonRetrieve(@PathVariable String classroomId,
                                              @PathVariable String lessonId,
                                              Authentication authentication) {
        return classroomLessonDomainService.lessonRetrieve(classroomId, lessonId, authentication);
    }

    @DeleteMapping({
            "/api/classroom/{classroomId}/lessons/{lessonId}", "/api/classroom/{classroomId}/lessons/{lessonId}/"
    })
    public ApiResponse<Object> lessonDelete(@PathVariable String classroomId,
                                            @PathVariable String lessonId,
                                            Authentication authentication) {
        return classroomLessonDomainService.lessonDelete(classroomId, lessonId, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/lessons/{lessonId}/download", "/api/classroom/{classroomId}/lessons/{lessonId}/download/"
    })
    public ResponseEntity<byte[]> lessonDownload(@PathVariable String classroomId,
                                                  @PathVariable String lessonId,
                                                  Authentication authentication) {
        ClassroomLessonService.LessonFile file = classroomLessonDomainService.lessonDownload(classroomId, lessonId, authentication);
        return buildFileResponse(file, true);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/lessons/{lessonId}/view", "/api/classroom/{classroomId}/lessons/{lessonId}/view/"
    })
    public ResponseEntity<byte[]> lessonView(@PathVariable String classroomId,
                                              @PathVariable String lessonId,
                                              Authentication authentication) {
        ClassroomLessonService.LessonFile file = classroomLessonDomainService.lessonView(classroomId, lessonId, authentication);
        return buildFileResponse(file, false);
    }

    private ResponseEntity<byte[]> buildFileResponse(ClassroomLessonService.LessonFile file, boolean attachment) {
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        if (file.contentType() != null && !file.contentType().isBlank()) {
            headers.setContentType(MediaType.parseMediaType(file.contentType()));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        return ResponseEntity.ok().headers(headers).body(file.bytes());
    }
}

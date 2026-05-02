package com.alethicode.service.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomLessonService;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface ClassroomLessonDomainService {

    ApiResponse<Object> lessonList(String classroomId, Authentication authentication);

    ApiResponse<Object> lessonRetrieve(String classroomId, String lessonId, Authentication authentication);

    ApiResponse<Object> lessonCreate(String classroomId, MultipartFile file, String title, String notes, Authentication authentication);

    ApiResponse<Object> lessonDelete(String classroomId, String lessonId, Authentication authentication);

    ClassroomLessonService.LessonFile lessonDownload(String classroomId, String lessonId, Authentication authentication);

    ClassroomLessonService.LessonFile lessonView(String classroomId, String lessonId, Authentication authentication);
}

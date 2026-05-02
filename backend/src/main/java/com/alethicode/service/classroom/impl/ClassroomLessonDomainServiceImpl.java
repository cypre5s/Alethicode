package com.alethicode.service.classroom.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomLessonDomainService;
import com.alethicode.service.classroom.ClassroomLessonService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClassroomLessonDomainServiceImpl implements ClassroomLessonDomainService {

    private final ClassroomLessonService classroomLessonService;

    public ClassroomLessonDomainServiceImpl(ClassroomLessonService classroomLessonService) {
        this.classroomLessonService = classroomLessonService;
    }

    @Override
    public ApiResponse<Object> lessonList(String classroomId, Authentication authentication) {
        return classroomLessonService.lessonList(classroomId, authentication);
    }

    @Override
    public ApiResponse<Object> lessonRetrieve(String classroomId, String lessonId, Authentication authentication) {
        return classroomLessonService.lessonRetrieve(classroomId, lessonId, authentication);
    }

    @Override
    public ApiResponse<Object> lessonCreate(String classroomId, MultipartFile file, String title, String notes, Authentication authentication) {
        return classroomLessonService.lessonCreate(classroomId, file, title, notes, authentication);
    }

    @Override
    public ApiResponse<Object> lessonDelete(String classroomId, String lessonId, Authentication authentication) {
        return classroomLessonService.lessonDelete(classroomId, lessonId, authentication);
    }

    @Override
    public ClassroomLessonService.LessonFile lessonDownload(String classroomId, String lessonId, Authentication authentication) {
        return classroomLessonService.lessonDownload(classroomId, lessonId, authentication);
    }

    @Override
    public ClassroomLessonService.LessonFile lessonView(String classroomId, String lessonId, Authentication authentication) {
        return classroomLessonService.lessonView(classroomId, lessonId, authentication);
    }
}

package com.alethicode.dto.request;

public record SystemPathsConfigRequest(
        String testCaseDir,
        String uploadDir,
        String uploadPrefix,
        String languagePackStorageRoot,
        String languagePackPreviewDir,
        String classroomLessonDir,
        Boolean forceHttps,
        String staticCdnHost,
        String libreOfficePath,
        String pythonPath
) {
}

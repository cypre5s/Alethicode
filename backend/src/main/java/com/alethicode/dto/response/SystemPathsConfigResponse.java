package com.alethicode.dto.response;

public record SystemPathsConfigResponse(
        String testCaseDir,
        String uploadDir,
        String uploadPrefix,
        String languagePackStorageRoot,
        String languagePackPreviewDir,
        String classroomLessonDir,
        boolean forceHttps,
        String staticCdnHost,
        String libreOfficePath,
        String pythonPath,
        String source
) {
}

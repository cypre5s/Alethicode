package com.alethicode.dto.response;

public record EnvSnapshotResponse(
        String testCaseDir,
        String uploadDir,
        String languagePackStorageRoot,
        String languagePackPreviewDir,
        String classroomLessonDir,
        boolean forceHttps,
        String staticCdnHost,
        String localVersion,
        boolean judgeServerTokenSet,
        String dbUrl,
        boolean dbPasswordSet,
        boolean redisPasswordSet,
        String redisHost,
        int redisPort,
        String videoTtsProvider,
        String videoRenderProvider,
        boolean temporalEnabled,
        String temporalTarget,
        String temporalNamespace,
        String temporalTaskQueue,
        String judgeDispatchTransport,
        String natsStreamName,
        String natsSubject,
        boolean langfuseEnabled,
        String langfuseBaseUrl,
        String langfuseTracingEnvironment,
        boolean fsrsEnabled,
        double fsrsDesiredRetention
) {
}

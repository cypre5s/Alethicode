package com.alethicode.dto.response;

public record InfraSecretsResponse(
        String dbUrl,
        String dbUsername,
        boolean dbPasswordSet,
        String redisHost,
        int redisPort,
        boolean redisPasswordSet,
        boolean judgeServerTokenSet,
        String judgeServerTokenMasked,
        String temporalTarget,
        String temporalNamespace,
        String temporalTaskQueue,
        String unleashApiUrl,
        boolean unleashApiKeySet,
        String unleashProject,
        String natsUrl,
        boolean natsUrlSet,
        String natsStreamName,
        String natsSubject,
        String langfuseBaseUrl,
        boolean langfusePublicKeySet,
        boolean langfuseSecretKeySet,
        String langfuseTracingEnvironment,
        String source
) {
}

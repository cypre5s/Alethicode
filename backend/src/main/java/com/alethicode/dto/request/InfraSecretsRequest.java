package com.alethicode.dto.request;

public record InfraSecretsRequest(
        String dbUrl,
        String dbUsername,
        String dbPassword,
        String redisHost,
        Integer redisPort,
        String redisPassword,
        String judgeServerToken,
        String temporalTarget,
        String temporalNamespace,
        String temporalTaskQueue,
        String unleashApiUrl,
        String unleashApiKey,
        String unleashProject,
        String natsUrl,
        String natsStreamName,
        String natsSubject,
        String langfuseBaseUrl,
        String langfusePublicKey,
        String langfuseSecretKey,
        String langfuseTracingEnvironment
) {
}

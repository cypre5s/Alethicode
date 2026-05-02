package com.alethicode.service.system.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.AiProviderConfigRequest;
import com.alethicode.dto.request.CreateSmtpConfigRequest;
import com.alethicode.dto.request.InfraSecretsRequest;
import com.alethicode.dto.request.SystemPathsConfigRequest;
import com.alethicode.dto.request.UpdateSmtpConfigRequest;
import com.alethicode.dto.request.WebsiteConfigRequest;
import com.alethicode.dto.response.AiProviderConfigResponse;
import com.alethicode.dto.response.EnvSnapshotResponse;
import com.alethicode.dto.response.InfraSecretsResponse;
import com.alethicode.dto.response.LanguagesResponse;
import com.alethicode.dto.response.ObservabilityConfigResponse;
import com.alethicode.dto.response.SmtpConfigResponse;
import com.alethicode.dto.response.SystemPathsConfigResponse;
import com.alethicode.dto.response.WebsiteConfigResponse;
import com.alethicode.exception.BadRequestException;
import com.alethicode.service.system.SmtpMailService;
import com.alethicode.service.system.SystemOptionService;
import org.springframework.lang.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SystemOptionServiceImpl implements SystemOptionService {

    private static final String SELECT_SQL = "select value::text from sys_options where key = ?";
    private static final long OPTION_CACHE_TTL_MILLIS = 5000L;
    private static final String UPSERT_SQL =
            "insert into sys_options(key, value, updated_at) values (?, cast(? as jsonb), now()) " +
                    "on conflict (key) do update set value = excluded.value, updated_at = now()";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final SmtpMailService smtpMailService;
    private final ConcurrentMap<String, OptionCacheEntry> optionCache = new ConcurrentHashMap<>();

    public SystemOptionServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AlethicodeProperties properties,
            SmtpMailService smtpMailService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.smtpMailService = smtpMailService;
    }

    @Override
    public WebsiteConfigResponse getWebsiteConfig() {
        Map<String, Object> betaConfig = readMapOption("beta_feedback_config");
        String betaPrivacyVersion = betaConfig == null ? "" : asString(betaConfig.get("privacy_notice_version"));
        String betaWjxUrl = betaConfig == null ? "" : asString(betaConfig.get("wjx_url"));

        Map<String, Object> option = readMapOption("website_config");
        if (option == null) {
            AlethicodeProperties.Website website = properties.getWebsite();
            return new WebsiteConfigResponse(
                    website.getBaseUrl(),
                    website.getName(),
                    website.getNameShortcut(),
                    website.getFooter(),
                    website.isAllowRegister(),
                    website.isSubmissionListShowAll(),
                    betaPrivacyVersion,
                    betaWjxUrl
            );
        }
        return new WebsiteConfigResponse(
                asString(option.get("website_base_url")),
                asString(option.get("website_name")),
                asString(option.get("website_name_shortcut")),
                asString(option.get("website_footer")),
                asBoolean(option.get("allow_register")),
                asBoolean(option.get("submission_list_show_all")),
                betaPrivacyVersion,
                betaWjxUrl
        );
    }

    @Override
    public LanguagesResponse getLanguages() {
        Map<String, Object> option = readMapOption("languages");
        if (option == null) {
            return new LanguagesResponse(
                    properties.getLanguage().getLanguages(),
                    properties.getLanguage().getSpjLanguages()
            );
        }
        Object spjLanguages = option.get("spj_languages");
        if (spjLanguages == null) {
            spjLanguages = option.get("spjLanguages");
        }
        return new LanguagesResponse(
                toStringList(option.get("languages")),
                toStringList(spjLanguages)
        );
    }

    @Override
    public SmtpConfigResponse getSmtpConfig() {
        StoredSmtpConfig smtpConfig = getStoredSmtpConfig();
        if (smtpConfig == null) {
            return null;
        }
        return new SmtpConfigResponse(
                smtpConfig.server(),
                smtpConfig.port(),
                smtpConfig.email(),
                smtpConfig.tls()
        );
    }

    @Override
    public void createSmtpConfig(CreateSmtpConfigRequest request) {
        writeOption("smtp_config", Map.of(
                "server", request.server(),
                "port", request.port(),
                "email", request.email(),
                "password", request.password(),
                "tls", request.tls()
        ));
    }

    @Override
    public void updateSmtpConfig(UpdateSmtpConfigRequest request) {
        StoredSmtpConfig existing = requireStoredSmtpConfig();
        String password = request.password();
        if (password == null || password.isBlank()) {
            password = existing.password();
        }
        writeOption("smtp_config", Map.of(
                "server", request.server(),
                "port", request.port(),
                "email", request.email(),
                "password", password,
                "tls", request.tls()
        ));
    }

    @Override
    public void testSmtp(String email, String username) {
        StoredSmtpConfig smtpConfig = requireStoredSmtpConfig();
        WebsiteConfigResponse websiteConfig = getWebsiteConfig();
        smtpMailService.send(
                smtpConfig.server(),
                smtpConfig.port(),
                smtpConfig.email(),
                smtpConfig.password(),
                smtpConfig.tls(),
                websiteConfig.websiteNameShortcut(),
                email,
                username == null ? "" : username,
                "You have successfully configured SMTP",
                "You have successfully configured SMTP"
        );
    }

    @Override
    public void updateWebsiteConfig(WebsiteConfigRequest request) {
        String safeFooter = Jsoup.clean(request.websiteFooter(), Safelist.relaxed());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("website_base_url", request.websiteBaseUrl());
        payload.put("website_name", request.websiteName());
        payload.put("website_name_shortcut", request.websiteNameShortcut());
        payload.put("website_footer", safeFooter);
        payload.put("allow_register", request.allowRegister());
        payload.put("submission_list_show_all", request.submissionListShowAll());
        writeOption("website_config", payload);
    }

    @Override
    public AiProviderConfigResponse getAiProviderConfig() {
        Map<String, Object> stored = readMapOption("ai_provider_config");
        String source;
        String apiKey;
        String baseUrl;
        String model;
        String embeddingApiKey;
        String embeddingBaseUrl;
        String embeddingModel;
        int timeoutSeconds;
        int maxRetries;

        if (stored != null) {
            source = "db";
            apiKey = asString(stored.get("api_key"));
            baseUrl = firstNonBlank(asString(stored.get("base_url")), envOrDefault("LLM_BASE_URL", "https://api.minimaxi.com/v1"));
            model = firstNonBlank(asString(stored.get("model")), envOrDefault("LLM_MODEL", "MiniMax-M2.7"));
            embeddingApiKey = asString(stored.get("embedding_api_key"));
            embeddingBaseUrl = firstNonBlank(asString(stored.get("embedding_base_url")), envOrDefault("EMBEDDING_BASE_URL", "https://api.openai.com/v1"));
            embeddingModel = firstNonBlank(asString(stored.get("embedding_model")), envOrDefault("EMBEDDING_MODEL", "text-embedding-3-small"));
            timeoutSeconds = stored.get("timeout_seconds") != null ? asInteger(stored.get("timeout_seconds")) : envInt("LLM_API_TIMEOUT_SECONDS", 150);
            maxRetries = stored.get("max_retries") != null ? asInteger(stored.get("max_retries")) : envInt("LLM_API_MAX_RETRIES", 3);
        } else {
            source = "env";
            apiKey = nullIfBlank(System.getenv("OPENAI_API_KEY"));
            baseUrl = envOrDefault("LLM_BASE_URL", "https://api.minimaxi.com/v1");
            model = envOrDefault("LLM_MODEL", "MiniMax-M2.7");
            embeddingApiKey = nullIfBlank(System.getenv("EMBEDDING_API_KEY"));
            embeddingBaseUrl = envOrDefault("EMBEDDING_BASE_URL", "https://api.openai.com/v1");
            embeddingModel = envOrDefault("EMBEDDING_MODEL", "text-embedding-3-small");
            timeoutSeconds = envInt("LLM_API_TIMEOUT_SECONDS", 150);
            maxRetries = envInt("LLM_API_MAX_RETRIES", 3);
        }

        return new AiProviderConfigResponse(
                maskSecret(apiKey),
                apiKey != null && !apiKey.isBlank(),
                baseUrl,
                model,
                maskSecret(embeddingApiKey),
                embeddingApiKey != null && !embeddingApiKey.isBlank(),
                embeddingBaseUrl,
                embeddingModel,
                timeoutSeconds,
                maxRetries,
                source
        );
    }

    @Override
    public void updateAiProviderConfig(AiProviderConfigRequest request) {
        Map<String, Object> existing = readMapOption("ai_provider_config");
        Map<String, Object> payload = new LinkedHashMap<>();

        String apiKey = request.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = existing != null ? asString(existing.get("api_key")) : null;
        }
        String embeddingApiKey = request.embeddingApiKey();
        if (embeddingApiKey == null || embeddingApiKey.isBlank()) {
            embeddingApiKey = existing != null ? asString(existing.get("embedding_api_key")) : null;
        }

        if (apiKey != null && !apiKey.isBlank()) payload.put("api_key", apiKey);
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) payload.put("base_url", request.baseUrl());
        if (request.model() != null && !request.model().isBlank()) payload.put("model", request.model());
        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) payload.put("embedding_api_key", embeddingApiKey);
        if (request.embeddingBaseUrl() != null && !request.embeddingBaseUrl().isBlank()) payload.put("embedding_base_url", request.embeddingBaseUrl());
        if (request.embeddingModel() != null && !request.embeddingModel().isBlank()) payload.put("embedding_model", request.embeddingModel());
        if (request.timeoutSeconds() != null && request.timeoutSeconds() > 0) payload.put("timeout_seconds", request.timeoutSeconds());
        if (request.maxRetries() != null && request.maxRetries() >= 0) payload.put("max_retries", request.maxRetries());

        writeOption("ai_provider_config", payload);
    }

    @Override
    public EnvSnapshotResponse getEnvSnapshot() {
        AlethicodeProperties.System sys = properties.getSystem();
        String redisHost = envOrDefault("REDIS_HOST", "127.0.0.1");
        int redisPort = envInt("REDIS_PORT", 6381);
        return new EnvSnapshotResponse(
                maskPath(sys.getTestCaseDir()),
                maskPath(sys.getUploadDir()),
                maskPath(properties.getLanguagePack().getStorageRoot()),
                maskPath(properties.getLanguagePack().getPreviewDir()),
                maskPath(sys.getClassroomLessonDir()),
                sys.isForceHttps(),
                sys.getStaticCdnHost(),
                sys.getLocalVersion(),
                !properties.getJudgeServer().getToken().isBlank(),
                maskDbUrl(envOrDefault("DB_URL", "")),
                !envOrDefault("DB_PASSWORD", "").isBlank(),
                !envOrDefault("REDIS_PASSWORD", "").isBlank(),
                redisHost,
                redisPort,
                envOrDefault("VIDEO_TTS_PROVIDER", "stub"),
                envOrDefault("VIDEO_RENDER_PROVIDER", "stub"),
                Boolean.parseBoolean(envOrDefault("TEMPORAL_ENABLED", "true")),
                envOrDefault("TEMPORAL_TARGET", "127.0.0.1:7233"),
                envOrDefault("TEMPORAL_NAMESPACE", "default"),
                envOrDefault("TEMPORAL_TASK_QUEUE", "language-pack-pipeline"),
                envOrDefault("JUDGE_DISPATCH_TRANSPORT", "nats"),
                envOrDefault("NATS_JUDGE_STREAM", "ALETHICODE_JUDGE"),
                envOrDefault("NATS_JUDGE_SUBJECT", "judge.dispatch"),
                !envOrDefault("LANGFUSE_PUBLIC_KEY", "").isBlank() && !envOrDefault("LANGFUSE_SECRET_KEY", "").isBlank(),
                envOrDefault("LANGFUSE_BASE_URL", ""),
                envOrDefault("LANGFUSE_TRACING_ENVIRONMENT", "production"),
                Boolean.parseBoolean(envOrDefault("FSRS_ENABLED", "true")),
                envDouble("FSRS_DESIRED_RETENTION", 0.9)
        );
    }

    @Override
    public ObservabilityConfigResponse getObservabilityConfig() {
        Map<String, Object> stored = readMapOption("observability_config");
        String storedUrl = stored == null ? null : nullIfBlank(asString(stored.get("grafana_url")));
        if (storedUrl != null) {
            return new ObservabilityConfigResponse(normalizeGrafanaUrl(storedUrl), "db");
        }

        String envUrl = nullIfBlank(System.getenv("GRAFANA_PUBLIC_URL"));
        if (envUrl != null) {
            return new ObservabilityConfigResponse(normalizeGrafanaUrl(envUrl), "env");
        }

        boolean runningOnKubernetes = !envOrDefault("KUBERNETES_SERVICE_HOST", "").isBlank();
        String fallbackUrl = runningOnKubernetes ? "/grafana/" : "http://localhost:3000/";
        return new ObservabilityConfigResponse(fallbackUrl, runningOnKubernetes ? "k8s-default" : "local-default");
    }

    private String maskDbUrl(String dbUrl) {
        if (dbUrl == null || dbUrl.isBlank()) return "(not set)";
        try {
            java.net.URI uri = java.net.URI.create(dbUrl.replace("jdbc:", ""));
            return uri.getHost() != null ? uri.getHost() : "(configured)";
        } catch (Exception e) {
            return "(configured)";
        }
    }

    private String maskPath(String path) {
        if (path == null || path.isBlank()) return "(not set)";
        return "(configured)";
    }

    @Override
    public SystemPathsConfigResponse getSystemPathsConfig() {
        Map<String, Object> stored = readMapOption("system_paths_config");
        AlethicodeProperties.System sys = properties.getSystem();
        AlethicodeProperties.LanguagePack lp = properties.getLanguagePack();
        String source = stored != null ? "db" : "env";

        String testCaseDir = resolve(stored, "test_case_dir", sys.getTestCaseDir());
        String uploadDir = resolve(stored, "upload_dir", sys.getUploadDir());
        String uploadPrefix = resolve(stored, "upload_prefix", sys.getUploadPrefix());
        String languagePackStorageRoot = resolve(stored, "language_pack_storage_root", lp.getStorageRoot());
        String languagePackPreviewDir = resolve(stored, "language_pack_preview_dir", lp.getPreviewDir());
        String classroomLessonDir = resolve(stored, "classroom_lesson_dir", sys.getClassroomLessonDir());
        boolean forceHttps = stored != null && stored.get("force_https") != null ? asBoolean(stored.get("force_https")) : sys.isForceHttps();
        String staticCdnHost = resolve(stored, "static_cdn_host", sys.getStaticCdnHost());
        String libreOfficePath = resolve(stored, "libre_office_path", lp.getLibreOfficePath());
        String pythonPath = resolve(stored, "python_path", lp.getPythonPath());

        return new SystemPathsConfigResponse(testCaseDir, uploadDir, uploadPrefix, languagePackStorageRoot,
                languagePackPreviewDir, classroomLessonDir, forceHttps, staticCdnHost, libreOfficePath, pythonPath, source);
    }

    @Override
    public void updateSystemPathsConfig(SystemPathsConfigRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.testCaseDir() != null) payload.put("test_case_dir", request.testCaseDir());
        if (request.uploadDir() != null) payload.put("upload_dir", request.uploadDir());
        if (request.uploadPrefix() != null) payload.put("upload_prefix", request.uploadPrefix());
        if (request.languagePackStorageRoot() != null) payload.put("language_pack_storage_root", request.languagePackStorageRoot());
        if (request.languagePackPreviewDir() != null) payload.put("language_pack_preview_dir", request.languagePackPreviewDir());
        if (request.classroomLessonDir() != null) payload.put("classroom_lesson_dir", request.classroomLessonDir());
        if (request.forceHttps() != null) payload.put("force_https", request.forceHttps());
        if (request.staticCdnHost() != null) payload.put("static_cdn_host", request.staticCdnHost());
        if (request.libreOfficePath() != null) payload.put("libre_office_path", request.libreOfficePath());
        if (request.pythonPath() != null) payload.put("python_path", request.pythonPath());
        writeOption("system_paths_config", payload);
    }

    @Override
    public InfraSecretsResponse getInfraSecrets() {
        Map<String, Object> stored = readMapOption("infra_secrets");
        String source = stored != null ? "db" : "env";

        String dbUrl = maskDbUrl(resolve(stored, "db_url", envOrDefault("DB_URL", "")));
        String dbUsername = resolve(stored, "db_username", envOrDefault("DB_USERNAME", "onlinejudge"));
        boolean dbPasswordSet = (stored != null && !asString(stored.get("db_password")).isBlank())
                || !envOrDefault("DB_PASSWORD", "").isBlank();

        String redisHost = resolve(stored, "redis_host", envOrDefault("REDIS_HOST", "127.0.0.1"));
        int redisPort = stored != null && stored.get("redis_port") != null
                ? asInteger(stored.get("redis_port")) : envInt("REDIS_PORT", 6381);
        boolean redisPasswordSet = (stored != null && !asString(stored.get("redis_password")).isBlank())
                || !envOrDefault("REDIS_PASSWORD", "").isBlank();

        String rawToken = stored != null ? nullIfBlank(asString(stored.get("judge_server_token"))) : null;
        if (rawToken == null) rawToken = nullIfBlank(properties.getJudgeServer().getToken());
        boolean judgeServerTokenSet = rawToken != null;
        String judgeServerTokenMasked = judgeServerTokenSet ? maskSecret(rawToken) : "";

        String temporalTarget = resolve(stored, "temporal_target", envOrDefault("TEMPORAL_TARGET", "127.0.0.1:7233"));
        String temporalNamespace = resolve(stored, "temporal_namespace", envOrDefault("TEMPORAL_NAMESPACE", "default"));
        String temporalTaskQueue = resolve(stored, "temporal_task_queue", envOrDefault("TEMPORAL_TASK_QUEUE", "language-pack-pipeline"));
        String unleashApiUrl = resolve(stored, "unleash_api_url", envOrDefault("UNLEASH_API_URL", ""));
        boolean unleashApiKeySet = (stored != null && !asString(stored.get("unleash_api_key")).isBlank())
                || !envOrDefault("UNLEASH_API_KEY", "").isBlank();
        String unleashProject = resolve(stored, "unleash_project", envOrDefault("UNLEASH_PROJECT", "ai-tutor"));
        String natsUrl = resolve(stored, "nats_url", envOrDefault("NATS_URL", "nats://127.0.0.1:4222"));
        boolean natsUrlSet = natsUrl != null && !natsUrl.isBlank();
        String natsStreamName = resolve(stored, "nats_stream_name", envOrDefault("NATS_JUDGE_STREAM", "ALETHICODE_JUDGE"));
        String natsSubject = resolve(stored, "nats_subject", envOrDefault("NATS_JUDGE_SUBJECT", "judge.dispatch"));
        String langfuseBaseUrl = resolve(stored, "langfuse_base_url", envOrDefault("LANGFUSE_BASE_URL", ""));
        boolean langfusePublicKeySet = (stored != null && !asString(stored.get("langfuse_public_key")).isBlank())
                || !envOrDefault("LANGFUSE_PUBLIC_KEY", "").isBlank();
        boolean langfuseSecretKeySet = (stored != null && !asString(stored.get("langfuse_secret_key")).isBlank())
                || !envOrDefault("LANGFUSE_SECRET_KEY", "").isBlank();
        String langfuseTracingEnvironment = resolve(
                stored, "langfuse_tracing_environment", envOrDefault("LANGFUSE_TRACING_ENVIRONMENT", "production"));

        return new InfraSecretsResponse(dbUrl, dbUsername, dbPasswordSet, redisHost, redisPort,
                redisPasswordSet, judgeServerTokenSet, judgeServerTokenMasked,
                temporalTarget, temporalNamespace, temporalTaskQueue,
                unleashApiUrl, unleashApiKeySet, unleashProject,
                natsUrl, natsUrlSet, natsStreamName, natsSubject,
                langfuseBaseUrl, langfusePublicKeySet, langfuseSecretKeySet, langfuseTracingEnvironment,
                source);
    }

    @Override
    public void updateInfraSecrets(InfraSecretsRequest request) {
        Map<String, Object> existing = readMapOption("infra_secrets");
        Map<String, Object> payload = new LinkedHashMap<>();

        putIfNonBlank(payload, "db_url", request.dbUrl());
        putIfNonBlank(payload, "db_username", request.dbUsername());
        putIfNonBlankOrKeep(payload, "db_password", request.dbPassword(), existing);
        putIfNonBlank(payload, "redis_host", request.redisHost());
        if (request.redisPort() != null && request.redisPort() > 0) payload.put("redis_port", request.redisPort());
        putIfNonBlankOrKeep(payload, "redis_password", request.redisPassword(), existing);
        putIfNonBlankOrKeep(payload, "judge_server_token", request.judgeServerToken(), existing);
        putIfNonBlank(payload, "temporal_target", request.temporalTarget());
        putIfNonBlank(payload, "temporal_namespace", request.temporalNamespace());
        putIfNonBlank(payload, "temporal_task_queue", request.temporalTaskQueue());
        putIfNonBlank(payload, "unleash_api_url", request.unleashApiUrl());
        putIfNonBlankOrKeep(payload, "unleash_api_key", request.unleashApiKey(), existing);
        putIfNonBlank(payload, "unleash_project", request.unleashProject());
        putIfNonBlank(payload, "nats_url", request.natsUrl());
        putIfNonBlank(payload, "nats_stream_name", request.natsStreamName());
        putIfNonBlank(payload, "nats_subject", request.natsSubject());
        putIfNonBlank(payload, "langfuse_base_url", request.langfuseBaseUrl());
        putIfNonBlankOrKeep(payload, "langfuse_public_key", request.langfusePublicKey(), existing);
        putIfNonBlankOrKeep(payload, "langfuse_secret_key", request.langfuseSecretKey(), existing);
        putIfNonBlank(payload, "langfuse_tracing_environment", request.langfuseTracingEnvironment());

        if (!payload.isEmpty()) {
            writeOption("infra_secrets", payload);
        }
    }

    private String resolve(Map<String, Object> stored, String key, String fallback) {
        if (stored == null) return fallback;
        String v = nullIfBlank(asString(stored.get(key)));
        return v != null ? v : fallback;
    }

    private void putIfNonBlank(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) payload.put(key, value);
    }

    private void putIfNonBlankOrKeep(Map<String, Object> payload, String key, String newValue, Map<String, Object> existing) {
        if (newValue != null && !newValue.isBlank()) {
            payload.put(key, newValue);
        } else if (existing != null) {
            String kept = nullIfBlank(asString(existing.get(key)));
            if (kept != null) payload.put(key, kept);
        }
    }

    @Override
    @Nullable
    public String getRawAiConfigValue(String dbFieldKey) {
        Map<String, Object> stored = readMapOption("ai_provider_config");
        if (stored == null) return null;
        return nullIfBlank(asString(stored.get(dbFieldKey)));
    }

    private static String maskSecret(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private double envDouble(String key, double fallback) {
        try {
            return Double.parseDouble(envOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String envOrDefault(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static int envInt(String key, int defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return defaultValue;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignored) { return defaultValue; }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String normalizeGrafanaUrl(String url) {
        String normalized = url == null ? "" : url.trim();
        if (normalized.isEmpty()) {
            return "/grafana/";
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    private StoredSmtpConfig requireStoredSmtpConfig() {
        StoredSmtpConfig smtpConfig = getStoredSmtpConfig();
        if (smtpConfig == null) {
            throw new BadRequestException("Please setup SMTP config at first");
        }
        return smtpConfig;
    }

    private StoredSmtpConfig getStoredSmtpConfig() {
        Map<String, Object> option = readMapOption("smtp_config");
        if (option == null) {
            return null;
        }
        String server = asString(option.get("server")).trim();
        Integer port = asNullableInteger(option.get("port"));
        String email = asString(option.get("email")).trim();
        String password = asString(option.get("password")).trim();
        Object tlsValue = option.get("tls");
        if (server.isEmpty() || port == null || email.isEmpty() || password.isEmpty() || tlsValue == null) {
            return null;
        }
        return new StoredSmtpConfig(
                server,
                port,
                email,
                password,
                asBoolean(tlsValue)
        );
    }

    private Map<String, Object> readMapOption(String key) {
        long now = System.currentTimeMillis();
        OptionCacheEntry cached = optionCache.get(key);
        if (cached != null && cached.expiresAtMillis() >= now) {
            return cached.exists() ? cached.value() : null;
        }

        String rawJson;
        try {
            rawJson = jdbcTemplate.queryForObject(SELECT_SQL, String.class, key);
        } catch (EmptyResultDataAccessException ignored) {
            optionCache.put(key, new OptionCacheEntry(null, false, now + OPTION_CACHE_TTL_MILLIS));
            return null;
        }
        if (rawJson == null) {
            optionCache.put(key, new OptionCacheEntry(null, false, now + OPTION_CACHE_TTL_MILLIS));
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            Map<String, Object> parsed;
            if (root != null && root.isObject()) {
                parsed = objectMapper.convertValue(root, new TypeReference<>() {
                });
            } else if ("languages".equals(key) && root != null && root.isArray()) {
                List<Object> languages = objectMapper.convertValue(root, new TypeReference<>() {
                });
                parsed = new LinkedHashMap<>();
                parsed.put("languages", languages);
                parsed.put("spj_languages", List.of());
            } else {
                throw new IllegalStateException("Failed to deserialize sys option: " + key + ", expected json object");
            }
            optionCache.put(key, new OptionCacheEntry(parsed, true, now + OPTION_CACHE_TTL_MILLIS));
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize sys option: " + key, exception);
        }
    }

    private void writeOption(String key, Map<String, Object> value) {
        try {
            jdbcTemplate.update(UPSERT_SQL, key, objectMapper.writeValueAsString(value));
            optionCache.remove(key);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize sys option: " + key, exception);
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Integer asNullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return Integer.parseInt(normalized);
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> {
                    if (item instanceof Map<?, ?> itemMap) {
                        Object name = itemMap.get("name");
                        return name == null ? "" : String.valueOf(name);
                    }
                    return String.valueOf(item);
                })
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private record StoredSmtpConfig(
            String server,
            Integer port,
            String email,
            String password,
            boolean tls
    ) {
    }

    private record OptionCacheEntry(
            Map<String, Object> value,
            boolean exists,
            long expiresAtMillis
    ) {
    }
}

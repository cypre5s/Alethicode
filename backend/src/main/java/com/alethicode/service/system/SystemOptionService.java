package com.alethicode.service.system;

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
import org.springframework.lang.Nullable;

public interface SystemOptionService {

    WebsiteConfigResponse getWebsiteConfig();

    LanguagesResponse getLanguages();

    SmtpConfigResponse getSmtpConfig();

    void createSmtpConfig(CreateSmtpConfigRequest request);

    void updateSmtpConfig(UpdateSmtpConfigRequest request);

    void testSmtp(String email, String username);

    void updateWebsiteConfig(WebsiteConfigRequest request);

    AiProviderConfigResponse getAiProviderConfig();

    void updateAiProviderConfig(AiProviderConfigRequest request);

    EnvSnapshotResponse getEnvSnapshot();

    /**
     * Returns the raw DB-stored value for the given AI provider config field key.
     * Returns null if not set in DB (caller should fall back to env var).
     */
    @Nullable
    String getRawAiConfigValue(String dbFieldKey);

    SystemPathsConfigResponse getSystemPathsConfig();

    void updateSystemPathsConfig(SystemPathsConfigRequest request);

    InfraSecretsResponse getInfraSecrets();

    void updateInfraSecrets(InfraSecretsRequest request);

    ObservabilityConfigResponse getObservabilityConfig();
}

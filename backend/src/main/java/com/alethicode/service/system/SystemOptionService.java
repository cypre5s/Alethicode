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
     * 返回 AI Provider 配置字段在数据库中的原始值。
     *
     * 未配置时返回 null，由调用方回退到环境变量。
     */
    @Nullable
    String getRawAiConfigValue(String dbFieldKey);

    SystemPathsConfigResponse getSystemPathsConfig();

    void updateSystemPathsConfig(SystemPathsConfigRequest request);

    InfraSecretsResponse getInfraSecrets();

    void updateInfraSecrets(InfraSecretsRequest request);

    ObservabilityConfigResponse getObservabilityConfig();
}

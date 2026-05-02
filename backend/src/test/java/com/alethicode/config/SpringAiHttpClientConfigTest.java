package com.alethicode.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 Spring AI / 默认 RestClient 的传输层契约：所有走 JDK HttpClient 的出站
 * RestClient 必须强制 HTTP/1.1。原因——本地通过 Clash/HTTP 代理访问 DeepSeek 时，
 * JDK HttpClient 默认协商 HTTP/2，大响应（题目生成阶段）会出现
 * {@code Http2Connection EOF reached while reading}，使整条流水线在 problem
 * generation 阶段失败。强制 HTTP/1.1 后该问题消失，且 HTTP/1.1 是 OpenAI 兼容
 * 接口的最小公共子集，对其他 RestClient 不会引入回归。
 */
class SpringAiHttpClientConfigTest {

    @Test
    void http11CustomizerInstallsJdkRequestFactoryWithHttp11() {
        SpringAiHttpClientConfig config = new SpringAiHttpClientConfig();
        RestClientCustomizer customizer = config.http11RestClientCustomizer();

        RestClient.Builder builder = RestClient.builder();
        customizer.customize(builder);
        RestClient client = builder.build();

        Object factory = ReflectionTestUtils.getField(client, "clientRequestFactory");
        assertThat(factory)
                .as("RestClient must use the HTTP/1.1 JDK factory installed by SpringAiHttpClientConfig")
                .isInstanceOf(JdkClientHttpRequestFactory.class);

        HttpClient inner = (HttpClient) ReflectionTestUtils.getField(factory, "httpClient");
        assertThat(inner).isNotNull();
        assertThat(inner.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    void buildHttp11RequestFactoryHonorsHttp11Version() {
        JdkClientHttpRequestFactory factory = SpringAiHttpClientConfig.buildHttp11RequestFactory();

        HttpClient inner = (HttpClient) ReflectionTestUtils.getField(factory, "httpClient");
        assertThat(inner).isNotNull();
        assertThat(inner.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }
}

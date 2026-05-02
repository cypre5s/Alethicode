package com.alethicode.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 强制所有默认 {@link org.springframework.web.client.RestClient} 走 HTTP/1.1 的传输层。
 *
 * <p>背景：本地 / 客户机房通过 HTTP 代理（Clash、Squid 等）访问 LLM Provider 时，
 * JDK HttpClient 默认会与远端协商 HTTP/2。题目生成阶段的响应体较大，HTTP/2 + 代理
 * 在大响应窗口下会出现 {@code Http2Connection EOF reached while reading} 提前断流，
 * 让 Spring AI 的 {@code OpenAiApi.chatCompletionEntity} 抛出
 * {@code RestClientException: Error while extracting response}，从而把整条课件包
 * 初始化流水线在 problem-generate 阶段拖死。
 *
 * <p>本配置通过一个 {@link RestClientCustomizer} 把所有由 Spring Boot 自动装配的
 * {@code RestClient.Builder} 切换成 HTTP/1.1 的 {@link JdkClientHttpRequestFactory}，
 * Spring AI 的 {@code OpenAiApi} 在自动装配时也会复用这个 builder，所以无需改动
 * Spring AI 自身。HTTP/1.1 是 OpenAI 兼容协议的最小公共子集，对其他 RestClient 调用
 * 不会引入回归（对应 ADR-0003 的 LLM 出口契约）。
 */
@Configuration
public class SpringAiHttpClientConfig {

    /**
     * Connect timeout 与 {@link com.alethicode.service.rag.HttpRagServiceClient} 保持
     * 一致（10s），避免和已有 RAG 客户端出现两套语义。
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    static JdkClientHttpRequestFactory buildHttp11RequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        return new JdkClientHttpRequestFactory(httpClient);
    }

    @Bean
    RestClientCustomizer http11RestClientCustomizer() {
        JdkClientHttpRequestFactory factory = buildHttp11RequestFactory();
        return builder -> builder.requestFactory(factory);
    }
}

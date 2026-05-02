package com.alethicode;

import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

// Phase 3 切流：嵌入与向量检索由 alethicode-rag 微服务托管，Java 后端不再需要
// Spring AI 的 EmbeddingModel bean。显式 exclude OpenAiEmbeddingAutoConfiguration
// 避免 Spring AI 在启动时尝试创建嵌入端点 bean（即使没有调用方）。
@SpringBootApplication(exclude = {OpenAiEmbeddingAutoConfiguration.class})
@ConfigurationPropertiesScan
@EnableAsync
public class AlethicodeJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlethicodeJavaApplication.class, args);
    }
}

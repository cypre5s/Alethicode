package com.alethicode.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AITutorControllerSkeletonContractTest {

    @Test
    void ideateSkeletonEndpointShouldUseDedicatedSkeletonServiceInsteadOfWorkflowIdeating() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/alethicode/controller/AITutorController.java"));

        assertThat(source).contains("aiTutorWorkflowDomainService.ideateSkeleton(request, authentication)");
        assertThat(source).doesNotContain("event_data\", Map.of(\"thought_text\", \"__generate_skeleton__\")");
    }
}

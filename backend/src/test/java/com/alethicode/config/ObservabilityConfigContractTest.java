package com.alethicode.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigContractTest {

    private static final Path APPLICATION_YML = Path.of("src", "main", "resources", "application.yml");
    private static final Path APPLICATION_PROD_YML = Path.of("src", "main", "resources", "application-prod.yml");
    private static final Path OVERVIEW_DASHBOARD = Path.of("..", "deploy", "observability", "grafana",
            "dashboards", "alethicode-overview.json");

    @Test
    void runtimeProfilesShouldPublishHttpRequestHistogramBuckets() throws IOException {
        assertHttpRequestHistogramEnabled(APPLICATION_YML);
        assertHttpRequestHistogramEnabled(APPLICATION_PROD_YML);
    }

    @Test
    void grafanaErrorRatePanelShouldShowZeroWhenNo5xxSeriesExists() throws IOException {
        JsonNode dashboard = new ObjectMapper().readTree(OVERVIEW_DASHBOARD.toFile());
        JsonNode panel = findPanelByTitle(dashboard, "HTTP 5xx Error Rate");

        assertThat(panel).isNotNull();
        assertThat(panel.at("/targets/0/expr").asText()).contains("or vector(0)");
    }

    private void assertHttpRequestHistogramEnabled(Path configPath) throws IOException {
        String source = Files.readString(configPath);
        assertThat(source).contains("percentiles-histogram");
        assertThat(source).contains("http.server.requests: true");
    }

    private JsonNode findPanelByTitle(JsonNode dashboard, String title) {
        for (JsonNode panel : dashboard.path("panels")) {
            if (title.equals(panel.path("title").asText())) {
                return panel;
            }
        }
        return null;
    }
}

package com.alethicode.service.announcement.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.announcement.ReleaseNotesService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReleaseNotesServiceImpl implements ReleaseNotesService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;

    public ReleaseNotesServiceImpl(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AlethicodeProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Map<String, Object> getReleaseNotes() {
        try {
            String body = restClient.get()
                    .uri(properties.getSystem().getReleaseNotesUrl() + "?_=" + java.lang.System.currentTimeMillis())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return null;
            }
            Map<String, Object> payload = objectMapper.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            payload.put("local_version", properties.getSystem().getLocalVersion());
            return payload;
        } catch (RuntimeException | java.io.IOException exception) {
            return null;
        }
    }
}

package com.alethicode.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalEnvFallbackLoader {

    private static final Logger log = LoggerFactory.getLogger(LocalEnvFallbackLoader.class);

    private final Map<String, String> envFallback = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public String get(String key) {
        ensureLoaded();
        return envFallback.get(key);
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loadEnvFile(Path.of("backend", ".env"));
        loadEnvFile(Path.of(".env"));
        loadEnvFile(Path.of("..", "Alethicode", "deploy", ".env"));
        loadEnvFile(Path.of("..", "Alethicode", "backend", ".env"));
        loaded = true;
    }

    private void loadEnvFile(Path path) {
        if (!Files.isRegularFile(path)) return;
        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String k = trimmed.substring(0, eq).trim();
                String v = trimmed.substring(eq + 1).trim();
                if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                    v = v.substring(1, v.length() - 1);
                }
                envFallback.putIfAbsent(k, v);
            }
        } catch (Exception e) {
            log.debug("Failed to load env file {}: {}", path, e.getMessage());
        }
    }
}

package com.alethicode.service.languagepack.impl;

public final class LlmSchemaViolationException extends RuntimeException {

    private final String violation;
    private final String rawJson;

    public LlmSchemaViolationException(String violation, String rawJson) {
        super(violation);
        this.violation = violation == null ? "" : violation;
        this.rawJson = rawJson == null
                ? ""
                : (rawJson.length() > 2000 ? rawJson.substring(0, 2000) + "..." : rawJson);
    }

    public String violation() { return violation; }
    public String rawJson() { return rawJson; }
}

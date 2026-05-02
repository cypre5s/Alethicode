package com.alethicode.service.languagepack.impl;

public final class JudgePausedException extends RuntimeException {

    private final String sourceSignature;
    private final String reason;

    public JudgePausedException(String sourceSignature, String reason) {
        super(sourceSignature + ": judge paused: " + reason);
        this.sourceSignature = sourceSignature == null ? "" : sourceSignature;
        this.reason = reason == null ? "" : reason;
    }

    public String sourceSignature() { return sourceSignature; }
    public String reason() { return reason; }
}

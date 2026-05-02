package com.alethicode.service.languagepack.impl;

public final class ProblemUnitGenerationFailure extends RuntimeException {

    private final String sourceSignature;
    private final String reason;

    public ProblemUnitGenerationFailure(String sourceSignature, String reason) {
        super(sourceSignature + ": " + reason);
        this.sourceSignature = sourceSignature == null ? "" : sourceSignature;
        this.reason = reason == null ? "" : reason;
    }

    public String sourceSignature() { return sourceSignature; }
    public String reason() { return reason; }
}

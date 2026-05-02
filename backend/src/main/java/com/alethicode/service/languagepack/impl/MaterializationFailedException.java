package com.alethicode.service.languagepack.impl;

public final class MaterializationFailedException extends RuntimeException {

    private final String failureSummary;

    public MaterializationFailedException(String failureSummary) {
        super(failureSummary);
        this.failureSummary = failureSummary == null ? "" : failureSummary;
    }

    public String failureSummary() { return failureSummary; }
}

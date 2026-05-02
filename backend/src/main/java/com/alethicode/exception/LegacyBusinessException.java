package com.alethicode.exception;

public class LegacyBusinessException extends BusinessException {

    private final String legacyCode;

    public LegacyBusinessException(String legacyCode, String message) {
        super(ErrorCode.BAD_REQUEST, message);
        this.legacyCode = normalize(legacyCode);
    }

    public String legacyCode() {
        return legacyCode;
    }

    private static String normalize(String code) {
        if (code == null) {
            return "error";
        }
        String normalized = code.trim().toLowerCase();
        return normalized.isEmpty() ? "error" : normalized;
    }
}

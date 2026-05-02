package com.alethicode.exception;

public final class BusinessExceptions {

    private BusinessExceptions() {
    }

    public static BusinessException fromLegacy(String errorCode, String message) {
        return new LegacyBusinessException(normalize(errorCode), message);
    }

    private static String normalize(String errorCode) {
        if (errorCode == null) {
            return "";
        }
        return errorCode.trim().toLowerCase();
    }
}

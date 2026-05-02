package com.alethicode.service.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;

public final class AiTelemetrySupport {

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private AiTelemetrySupport() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for AI telemetry", exception);
        }
    }

    public static int promptLength(String... prompts) {
        int length = 0;
        if (prompts == null) {
            return 0;
        }
        for (String prompt : prompts) {
            if (prompt != null) {
                length += prompt.length();
            }
        }
        return length;
    }

    public static String promptHash(String... prompts) {
        return sha256(String.join("\n", normalizePrompts(prompts)));
    }

    public static int responseLength(Object response) {
        if (response == null) {
            return 0;
        }
        if (response instanceof CharSequence sequence) {
            return sequence.length();
        }
        if (response instanceof Collection<?> collection) {
            return collection.size();
        }
        if (response instanceof Map<?, ?> map) {
            return map.toString().length();
        }
        return String.valueOf(response).length();
    }

    public static AiTelemetryCaller inferCaller(String operation) {
        return STACK_WALKER.walk(frames -> frames
                .filter(frame -> isBusinessFrame(frame.getDeclaringClass()))
                .findFirst()
                .map(frame -> callerFromFrame(
                        frame.getDeclaringClass().getName(),
                        frame.getDeclaringClass().getSimpleName(),
                        frame.getMethodName()
                ))
                .orElseGet(() -> AiTelemetryCaller.unknown(operation)));
    }

    private static String[] normalizePrompts(String... prompts) {
        if (prompts == null || prompts.length == 0) {
            return new String[]{""};
        }
        String[] normalized = new String[prompts.length];
        for (int i = 0; i < prompts.length; i++) {
            normalized[i] = prompts[i] == null ? "" : prompts[i];
        }
        return normalized;
    }

    static AiTelemetryCaller callerFromFrame(String className, String simpleClassName, String methodName) {
        String normalizedClassName = className == null || className.isBlank() ? "unknown" : className.strip();
        String normalizedSimpleName = simpleClassName == null || simpleClassName.isBlank()
                ? lastClassSegment(normalizedClassName)
                : simpleClassName.strip();
        String normalizedMethod = methodName == null || methodName.isBlank() ? "unknown" : methodName.strip();
        String service = inferService(normalizedClassName, normalizedSimpleName);
        String domain = inferDomain(normalizedClassName, service);
        String scene = service + "." + stripImplSuffix(normalizedSimpleName) + "." + normalizedMethod;
        return new AiTelemetryCaller(service, scene, normalizedClassName, normalizedMethod, domain);
    }

    private static boolean isBusinessFrame(Class<?> declaringClass) {
        if (declaringClass == null) {
            return false;
        }
        String className = declaringClass.getName();
        return className.startsWith("com.alethicode.")
                && !className.startsWith("com.alethicode.service.ai.")
                && !className.startsWith("com.alethicode.config.")
                && !className.startsWith("com.alethicode.dto.")
                && !className.startsWith("com.alethicode.exception.");
    }

    private static String inferService(String className, String simpleClassName) {
        if (className.contains(".aitutor.eval.") || simpleClassName.contains("EvalHarness")) {
            return simpleClassName.startsWith("Qa") ? "qa-harness" : "tutor-eval";
        }
        if (className.contains(".aitutor.review.") || simpleClassName.contains("ReviewPackage")) {
            return "review-package";
        }
        if (className.contains(".languagepack.") || simpleClassName.contains("LanguagePack")) {
            return "language-pack";
        }
        if (className.contains(".Classroom") || className.contains(".classroom") || simpleClassName.contains("Classroom")) {
            return "classroom-ai";
        }
        if (className.contains(".aitutor.") || simpleClassName.contains("AITutor")) {
            return "ai-tutor";
        }
        return "java-ai";
    }

    private static String inferDomain(String className, String fallback) {
        if (className.contains(".languagepack.")) {
            return "language-pack";
        }
        if (className.contains(".aitutor.")) {
            return "ai-tutor";
        }
        if (className.contains(".classroom.") || className.contains("Classroom")) {
            return "classroom";
        }
        return fallback;
    }

    private static String stripImplSuffix(String value) {
        return value.endsWith("Impl") ? value.substring(0, value.length() - 4) : value;
    }

    private static String lastClassSegment(String className) {
        int index = className.lastIndexOf('.');
        return index >= 0 ? className.substring(index + 1) : className;
    }
}

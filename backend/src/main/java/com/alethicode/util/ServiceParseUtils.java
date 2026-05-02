package com.alethicode.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServiceParseUtils {

    private static final Logger log = LoggerFactory.getLogger(ServiceParseUtils.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private ServiceParseUtils() {
    }

    public static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static String trimToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(trimToEmpty(raw));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static int parseIntObj(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static Integer parseIntObjNullable(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    public static long longValue(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    public static Long parseLongObj(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    public static double parseDouble(Object value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static Double parseDoubleObj(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean parseBoolean(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        String raw = String.valueOf(value);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
    }

    public static Boolean parseBooleanObj(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        String raw = String.valueOf(value);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
    }

    public static List<Object> castList(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        return List.of();
    }

    public static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return data;
        }
        return new LinkedHashMap<>();
    }

    public static String toJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    public static Map<String, Object> parseJsonMap(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("parseJsonMap failed, returning empty map", e);
            return Map.of();
        }
    }

    public static List<Object> parseJsonList(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("parseJsonList failed, returning empty list", e);
            return List.of();
        }
    }

    public static String nowIso() {
        return DATE_TIME_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC));
    }

    public static String nowPlusHours(int hours) {
        return DATE_TIME_FORMATTER.format(Instant.now().plusSeconds(hours * 3600L).atOffset(ZoneOffset.UTC));
    }

    public static String formatTime(Timestamp timestamp) {
        if (timestamp == null) return null;
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    public static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成指定长度的字母数字随机 ID（使用 SecureRandom）。
     * 历史上这段逻辑在多个 Service 中被重复定义，这里统一出口避免再次发散。
     */
    public static String randomId(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("randomId length must be positive: " + length);
        }
        final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

    /**
     * 将字符串在超出 maxChars 时截断并追加省略标记，避免塞入 LLM prompt 时撑爆 token。
     * 对 null 输入返回空串，保留调用方可直接拼接的语义。
     */
    public static String shortenForPrompt(String text, int maxChars) {
        String normalized = trimToEmpty(text);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...(truncated)";
    }

    /**
     * 返回第一个非空白（trim 后非空）的字符串；全部为空则返回 null。
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    public static double acRate(long total, long ac) {
        if (total <= 0) return 0.0;
        return Math.round(ac * 1000.0 / total) / 10.0;
    }

    public static String extension(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx);
    }
}

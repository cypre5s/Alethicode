package com.alethicode.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * RFC 6238 TOTP 工具类。
 *
 * 用途：生成/验证与 Google Authenticator / Microsoft Authenticator 等兼容的 TOTP 一次性密码。
 *
 * 参数：
 * - 时间步长: 30 秒
 * - HMAC 算法: SHA-1（RFC 6238 默认，authenticator app 最广泛支持）
 * - 验证码位数: 6
 * - 密钥长度: 20 字节 (160 bit)，用 RFC 4648 base32 编码
 * - 验证窗口: ±1 个时间步（容忍客户端时钟漂移 ±30s）
 */
public final class TotpUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int CODE_DIGITS = 6;
    private static final long TIME_STEP_SECONDS = 30L;
    private static final int SECRET_BYTES = 20;
    private static final int WINDOW = 1;
    private static final char[] BASE32_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtils() {
    }

    /** 生成新的 TOTP 密钥（base32 编码，无 padding）。 */
    public static String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTES];
        RANDOM.nextBytes(buffer);
        return base32Encode(buffer);
    }

    /** 构造 otpauth:// URI，供 Authenticator app 扫码录入。 */
    public static String otpAuthUri(String label, String secret, String issuer) {
        String encodedLabel = urlEncode(label);
        String encodedIssuer = urlEncode(issuer == null ? "" : issuer);
        StringBuilder builder = new StringBuilder("otpauth://totp/");
        if (!encodedIssuer.isBlank()) {
            builder.append(encodedIssuer).append(':');
        }
        builder.append(encodedLabel)
                .append("?secret=").append(secret)
                .append("&algorithm=SHA1")
                .append("&digits=").append(CODE_DIGITS)
                .append("&period=").append(TIME_STEP_SECONDS);
        if (!encodedIssuer.isBlank()) {
            builder.append("&issuer=").append(encodedIssuer);
        }
        return builder.toString();
    }

    /**
     * 验证用户输入的 TOTP code 是否合法。
     * 会检查 [now-WINDOW, now+WINDOW] 范围内的所有可能值，以容忍时钟漂移。
     * 使用 {@link MessageDigest#isEqual} 进行常量时间比较，防止 timing attack。
     */
    public static boolean verifyCode(String secret, String inputCode) {
        if (secret == null || inputCode == null) {
            return false;
        }
        String normalized = inputCode.trim();
        if (normalized.length() != CODE_DIGITS) {
            return false;
        }
        byte[] key;
        try {
            key = base32Decode(secret);
        } catch (IllegalArgumentException e) {
            return false;
        }
        long timeStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        byte[] inputBytes = normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            String candidate = computeCode(key, timeStep + offset);
            if (MessageDigest.isEqual(candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8), inputBytes)) {
                return true;
            }
        }
        return false;
    }

    private static String computeCode(byte[] key, long counter) {
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            hash = mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA1 is required for TOTP", e);
        }
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int modulus = (int) Math.pow(10, CODE_DIGITS);
        int otp = binary % modulus;
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    private static String base32Encode(byte[] data) {
        StringBuilder builder = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                builder.append(BASE32_ALPHABET[(buffer >> bitsLeft) & 0x1F]);
            }
        }
        if (bitsLeft > 0) {
            builder.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return builder.toString();
    }

    static byte[] base32Decode(String encoded) {
        String normalized = encoded.trim().replace(" ", "").toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("empty base32 secret");
        }
        int outputLen = normalized.length() * 5 / 8;
        byte[] output = new byte[outputLen];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char ch : normalized.toCharArray()) {
            if (ch == '=') {
                break;
            }
            int value = base32CharValue(ch);
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                output[index++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        if (index < outputLen) {
            byte[] trimmed = new byte[index];
            System.arraycopy(output, 0, trimmed, 0, index);
            return trimmed;
        }
        return output;
    }

    private static int base32CharValue(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return ch - 'A';
        }
        if (ch >= '2' && ch <= '7') {
            return ch - '2' + 26;
        }
        throw new IllegalArgumentException("invalid base32 character: " + ch);
    }

    private static String urlEncode(String raw) {
        return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }
}

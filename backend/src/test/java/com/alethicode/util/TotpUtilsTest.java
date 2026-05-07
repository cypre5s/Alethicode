package com.alethicode.util;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpUtilsTest {

    @Test
    void generateSecretReturnsBase32String() {
        String secret = TotpUtils.generateSecret();
        assertNotNull(secret);
        assertEquals(32, secret.length());
        for (char ch : secret.toCharArray()) {
            assertTrue((ch >= 'A' && ch <= 'Z') || (ch >= '2' && ch <= '7'),
                    "invalid base32 char: " + ch);
        }
    }

    @Test
    void generatedSecretsAreUnique() {
        String a = TotpUtils.generateSecret();
        String b = TotpUtils.generateSecret();
        assertNotEquals(a, b);
    }

    @Test
    void otpAuthUriContainsRequiredFields() {
        String uri = TotpUtils.otpAuthUri("alice", "JBSWY3DPEHPK3PXP", "Alethicode");
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"));
        assertTrue(uri.contains("algorithm=SHA1"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
        assertTrue(uri.contains("issuer=Alethicode"));
    }

    @Test
    void verifyCodeAcceptsCurrentCode() throws Exception {
        String secret = TotpUtils.generateSecret();
        byte[] key = TotpUtils.base32Decode(secret);
        long step = System.currentTimeMillis() / 1000L / 30L;
        String code = computeCodeForTest(key, step);
        assertTrue(TotpUtils.verifyCode(secret, code));
    }

    @Test
    void verifyCodeAcceptsPreviousAndNextWindow() throws Exception {
        String secret = TotpUtils.generateSecret();
        byte[] key = TotpUtils.base32Decode(secret);
        long step = System.currentTimeMillis() / 1000L / 30L;
        assertTrue(TotpUtils.verifyCode(secret, computeCodeForTest(key, step - 1)));
        assertTrue(TotpUtils.verifyCode(secret, computeCodeForTest(key, step + 1)));
    }

    @Test
    void verifyCodeRejectsFarWindow() throws Exception {
        String secret = TotpUtils.generateSecret();
        byte[] key = TotpUtils.base32Decode(secret);
        long step = System.currentTimeMillis() / 1000L / 30L;
        assertFalse(TotpUtils.verifyCode(secret, computeCodeForTest(key, step - 5)));
        assertFalse(TotpUtils.verifyCode(secret, computeCodeForTest(key, step + 5)));
    }

    @Test
    void verifyCodeRejectsMalformedInput() {
        String secret = TotpUtils.generateSecret();
        assertFalse(TotpUtils.verifyCode(secret, null));
        assertFalse(TotpUtils.verifyCode(secret, ""));
        assertFalse(TotpUtils.verifyCode(secret, "12345"));
        assertFalse(TotpUtils.verifyCode(secret, "1234567"));
        assertFalse(TotpUtils.verifyCode(secret, "abcdef"));
    }

    @Test
    void verifyCodeRejectsInvalidSecret() {
        assertFalse(TotpUtils.verifyCode(null, "123456"));
        assertFalse(TotpUtils.verifyCode("1@#$%!", "123456"));
    }

    @Test
    void rfc6238ReferenceVector() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        byte[] key = TotpUtils.base32Decode(secret);
        String otp = computeCodeForTest(key, 1L);
        assertEquals("287082", otp);
    }

    @Test
    void base32DecodeRejectsInvalidChar() {
        assertThrows(IllegalArgumentException.class,
                () -> TotpUtils.base32Decode("####"));
    }

    private String computeCodeForTest(byte[] key, long counter) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

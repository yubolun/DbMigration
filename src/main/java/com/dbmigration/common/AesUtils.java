package com.dbmigration.common;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具类
 * <p>
 * 每次加密生成随机 12-byte IV，前置拼接到密文。
 * 输出格式: Base64( IV(12 bytes) + CipherText + AuthTag(16 bytes) )
 */
public class AesUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    private static byte[] aesKey;

    /**
     * 初始化 AES 密钥（由 Spring 配置注入调用）
     */
    public static void init(String base64Key) {
        aesKey = Base64.getDecoder().decode(base64Key);
        // Ensure 16, 24, or 32 bytes for AES-128/192/256
        if (aesKey.length != 16 && aesKey.length != 24 && aesKey.length != 32) {
            // Pad or truncate to 32 bytes for AES-256
            byte[] padded = new byte[32];
            System.arraycopy(aesKey, 0, padded, 0, Math.min(aesKey.length, 32));
            aesKey = padded;
        }
    }

    /**
     * AES-GCM 加密
     *
     * @param plainText 明文
     * @return Base64 编码的密文 (IV + cipherText + authTag)
     */
    public static String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * AES-GCM 解密
     *
     * @param encryptedText Base64 编码的密文
     * @return 明文
     */
    public static String decrypt(String encryptedText) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            // Extract ciphertext
            byte[] cipherText = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }
}

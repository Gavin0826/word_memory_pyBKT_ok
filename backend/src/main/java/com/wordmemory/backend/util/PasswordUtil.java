package com.wordmemory.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 简化版密码工具类（不依赖 Spring Security）
 */
public class PasswordUtil {

    // 使用 SHA-256 加盐哈希
    private static final int SALT_LENGTH = 16;
    private static final int HASH_ITERATIONS = 10000;

    /**
     * 私有构造函数，防止工具类被实例化
     */
    private PasswordUtil() {
        throw new IllegalStateException("PasswordUtil 是工具类，不能被实例化");
    }

    /**
     * 生成随机盐值
     */
    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * 加密密码（SHA-256 + 盐值）
     * @param rawPassword 原始密码
     * @return 加密后的密码字符串（格式：salt:hash）
     */
    public static String encryptPassword(String rawPassword) {
        try {
            byte[] salt = generateSalt();
            byte[] hash = hashWithSalt(rawPassword, salt, HASH_ITERATIONS);

            // 格式：Base64(盐值):Base64(哈希值)
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hash);

            return saltBase64 + ":" + hashBase64;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encryptedPassword 加密后的密码字符串
     * @return 验证结果
     */
    public static boolean verifyPassword(String rawPassword, String encryptedPassword) {
        try {
            if (encryptedPassword == null || !encryptedPassword.contains(":")) {
                return false;
            }

            String[] parts = encryptedPassword.split(":", 2);
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] storedHash = Base64.getDecoder().decode(parts[1]);

            byte[] computedHash = hashWithSalt(rawPassword, salt, HASH_ITERATIONS);

            return MessageDigest.isEqual(storedHash, computedHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用盐值和迭代次数生成哈希
     */
    private static byte[] hashWithSalt(String password, byte[] salt, int iterations)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);

        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

        // 多次迭代增加安全性
        for (int i = 1; i < iterations; i++) {
            digest.reset();
            hash = digest.digest(hash);
        }

        return hash;
    }

    /**
     * 生成随机令牌
     * @return 随机令牌字符串
     */
    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成验证码（6位数字）
     * @return 6位数字验证码
     */
    public static String generateVerificationCode() {
        int code = (int) ((Math.random() * 900000) + 100000);
        return String.valueOf(code);
    }

    /**
     * 检查密码强度
     * @param password 密码
     * @return 强度等级：0=弱，1=中，2=强
     */
    public static int checkPasswordStrength(String password) {
        if (password == null || password.length() < 6) {
            return 0; // 弱
        }

        int score = 0;

        // 检查长度
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;

        // 检查是否包含数字
        if (password.matches(".*\\d.*")) score++;

        // 检查是否包含小写字母
        if (password.matches(".*[a-z].*")) score++;

        // 检查是否包含大写字母
        if (password.matches(".*[A-Z].*")) score++;

        // 检查是否包含特殊字符
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) score++;

        // 根据分数返回强度等级
        if (score <= 2) return 0; // 弱
        if (score <= 4) return 1; // 中
        return 2; // 强
    }

    /**
     * 获取密码强度描述
     * @param password 密码
     * @return 强度描述文本
     */
    public static String getPasswordStrengthText(String password) {
        int strength = checkPasswordStrength(password);
        switch (strength) {
            case 0: return "弱";
            case 1: return "中";
            case 2: return "强";
            default: return "未知";
        }
    }
}
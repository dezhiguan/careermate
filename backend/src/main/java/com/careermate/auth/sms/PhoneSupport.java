package com.careermate.auth.sms;

import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class PhoneSupport {

    private static final Pattern MAINLAND_PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] USERNAME_SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private PhoneSupport() {
    }

    public static void validateMainlandPhone(String phone) {
        if (!StringUtils.hasText(phone) || !MAINLAND_PHONE.matcher(phone.trim()).matches()) {
            throw new BizException(ErrorCode.PHONE_FORMAT_INVALID);
        }
    }

    public static String normalizePhone(String phone) {
        return phone == null ? null : phone.trim();
    }

    public static String maskPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(normalized) || normalized.length() != 11) {
            return "****";
        }
        return normalized.substring(0, 3) + "****" + normalized.substring(7);
    }

    public static String hashPhone(String phone, String pepper) {
        return sha256Hex(normalizePhone(phone) + ":" + pepper);
    }

    public static String hashIp(String ip, String pepper) {
        return sha256Hex(normalizeIp(ip) + ":ip:" + pepper);
    }

    public static String hashCode(String code, String pepper) {
        return sha256Hex(code + ":code:" + pepper);
    }

    public static String generateNumericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(SECURE_RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    public static String generateUsername(String phone) {
        String suffix = phone.substring(phone.length() - 4);
        return "cm_" + suffix + "_" + randomShortCode(4);
    }

    public static String generateDisplayName(String phone) {
        return "用户" + phone.substring(phone.length() - 4);
    }

    private static String randomShortCode(int length) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = USERNAME_SUFFIX_CHARS[SECURE_RANDOM.nextInt(USERNAME_SUFFIX_CHARS.length)];
        }
        return new String(chars);
    }

    private static String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        int commaIndex = ip.indexOf(',');
        if (commaIndex > 0) {
            return ip.substring(0, commaIndex).trim();
        }
        return ip.trim();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

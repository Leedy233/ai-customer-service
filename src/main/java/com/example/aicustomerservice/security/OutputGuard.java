package com.example.aicustomerservice.security;

import java.util.regex.Pattern;

/**
 * 输出安全检查：防止模型回复中泄露 API Key 等敏感串。
 */
public final class OutputGuard {

    private static final Pattern KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9]{16,}");

    private OutputGuard() {
    }

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        return KEY_PATTERN.matcher(text).replaceAll("[密钥已脱敏]");
    }
}
package com.jongyoon.smsforward;

import java.util.Locale;

/** 전화번호를 비교용 값으로 정리한다. (하이픈/공백 제거, +82 → 0) */
final class PhoneUtil {
    private PhoneUtil() {}

    static String key(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                // 영문이 섞인 발신자(예: 은행 이름)는 대소문자 무시하고 그대로 비교
                return s.toLowerCase(Locale.ROOT);
            }
        }
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') d.append(c);
        }
        String digits = d.toString();
        String rest = null;
        if (s.startsWith("+82")) rest = digits.substring(Math.min(2, digits.length()));
        else if (s.startsWith("0082")) rest = digits.substring(Math.min(4, digits.length()));
        if (rest != null) {
            if (!rest.startsWith("0")) rest = "0" + rest;
            return rest;
        }
        return digits;
    }

    static boolean looksLikeNumber(String raw) {
        String k = key(raw);
        if (k.length() < 8) return false;
        for (int i = 0; i < k.length(); i++) {
            if (k.charAt(i) < '0' || k.charAt(i) > '9') return false;
        }
        return true;
    }
}

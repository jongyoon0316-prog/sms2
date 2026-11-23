package com.jongyoon.smsforward;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 설정과 포워딩 이력 저장소 */
final class Store {
    private static final String FILE = "smsforward";
    private static final int MAX_HISTORY = 200;

    private Store() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return sp(c).getBoolean("enabled", false); }
    static String sendersText(Context c) { return sp(c).getString("senders", ""); }
    static String dest1(Context c) { return sp(c).getString("dest1", ""); }
    static String dest2(Context c) { return sp(c).getString("dest2", ""); }

    static void saveConfig(Context c, boolean enabled, String senders, String d1, String d2) {
        sp(c).edit()
                .putBoolean("enabled", enabled)
                .putString("senders", senders)
                .putString("dest1", d1)
                .putString("dest2", d2)
                .apply();
    }

    /** 입력 문자열(줄바꿈/쉼표/세미콜론 구분)을 번호 목록으로 나눈다. */
    static List<String> parseSenders(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (String part : text.split("[\\n,;]+")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            if (seen.add(PhoneUtil.key(t))) out.add(t);
        }
        return out;
    }

    /** 전달 대상으로 등록된 발신번호들의 비교용 값 */
    static Set<String> allowedKeys(Context c) {
        Set<String> keys = new LinkedHashSet<>();
        for (String s : parseSenders(sendersText(c))) keys.add(PhoneUtil.key(s));
        return keys;
    }

    static synchronized void addHistory(Context c, long time, String from, String text,
                                        String to, boolean ok, String note) {
        try {
            JSONArray arr = new JSONArray(sp(c).getString("hist", "[]"));
            JSONObject o = new JSONObject();
            o.put("t", time);
            o.put("from", from);
            o.put("text", text);
            o.put("to", to);
            o.put("ok", ok);
            o.put("note", note);
            JSONArray next = new JSONArray();
            next.put(o);
            for (int i = 0; i < arr.length() && next.length() < MAX_HISTORY; i++) {
                next.put(arr.get(i));
            }
            sp(c).edit().putString("hist", next.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** 최신순 이력 */
    static synchronized List<JSONObject> history(Context c) {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("hist", "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(arr.getJSONObject(i));
        } catch (JSONException ignored) {
        }
        return list;
    }

    static synchronized void clearHistory(Context c) {
        sp(c).edit().putString("hist", "[]").apply();
    }
}

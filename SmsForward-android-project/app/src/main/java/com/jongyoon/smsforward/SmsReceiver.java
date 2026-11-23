package com.jongyoon.smsforward;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 문자를 받으면 호출된다.
 * 등록된 발신번호에서 온 문자만 골라, 등록된 2개 번호로 그대로 전달한다.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        if (!Store.enabled(ctx)) return;

        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        // 긴 문자는 여러 조각으로 오므로 발신번호별로 이어 붙인다.
        LinkedHashMap<String, StringBuilder> bySender = new LinkedHashMap<>();
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String addr = m.getOriginatingAddress();
            if (addr == null) addr = "";
            StringBuilder sb = bySender.get(addr);
            if (sb == null) {
                sb = new StringBuilder();
                bySender.put(addr, sb);
            }
            String body = m.getMessageBody();
            if (body != null) sb.append(body);
        }

        Set<String> allowed = Store.allowedKeys(ctx);
        String d1 = Store.dest1(ctx).trim();
        String d2 = Store.dest2(ctx).trim();
        if (allowed.isEmpty() || d1.isEmpty() || d2.isEmpty()) return;
        String k1 = PhoneUtil.key(d1);
        String k2 = PhoneUtil.key(d2);

        for (Map.Entry<String, StringBuilder> e : bySender.entrySet()) {
            String from = e.getKey();
            String key = PhoneUtil.key(from);

            if (!allowed.contains(key)) continue;          // 등록된 발신번호가 아니면 전달 안 함
            if (key.equals(k1) || key.equals(k2)) continue; // 받는 번호에서 온 문자는 되돌려 보내지 않음(무한 반복 방지)

            forward(ctx, from, e.getValue().toString(), d1, d2);
        }
    }

    private void forward(Context ctx, String from, String body, String d1, String d2) {
        long now = System.currentTimeMillis();
        String text = "[문자전달] 발신 " + from + "\n" + body;
        String snippet = body.length() > 60 ? body.substring(0, 60) + "…" : body;

        SmsManager sm;
        try {
            sm = Build.VERSION.SDK_INT >= 31
                    ? ctx.getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
        } catch (Exception ex) {
            Store.addHistory(ctx, now, from, snippet, d1 + ", " + d2, false, "문자 기능을 사용할 수 없음");
            return;
        }
        if (sm == null) {
            Store.addHistory(ctx, now, from, snippet, d1 + ", " + d2, false, "문자 기능을 사용할 수 없음");
            return;
        }

        ArrayList<String> parts = sm.divideMessage(text);
        String[] dests = {d1, d2};
        for (String dest : dests) {
            try {
                sm.sendMultipartTextMessage(dest, null, parts, null, null);
                Store.addHistory(ctx, now, from, snippet, dest, true, "발송 요청 완료");
            } catch (SecurityException se) {
                Store.addHistory(ctx, now, from, snippet, dest, false, "문자 보내기 권한 없음");
            } catch (Exception ex) {
                Store.addHistory(ctx, now, from, snippet, dest, false, "발송 실패: " + ex.getClass().getSimpleName());
            }
        }
    }
}

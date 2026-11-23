package com.jongyoon.smsforward;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private Switch swEnabled;
    private EditText etSenders, etDest1, etDest2;
    private TextView tvStatus, tvHistory;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, dp(16), 0, dp(4));
        return v;
    }

    private EditText field(String hint, int inputType, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(inputType | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) {
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        return e;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(32));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("문자 전달");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        swEnabled = new Switch(this);
        swEnabled.setText("문자 전달 사용");
        swEnabled.setTextSize(18);
        swEnabled.setPadding(0, dp(16), 0, dp(4));
        root.addView(swEnabled);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(14);
        tvStatus.setPadding(0, dp(8), 0, 0);
        root.addView(tvStatus);

        root.addView(button("권한 허용하기", v -> requestPermissions(
                new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS}, 1)));

        root.addView(label("전달할 발신번호 (이 번호에서 온 문자만 전달)", 16, true));
        etSenders = field("한 줄에 하나씩, 예: 010-1234-5678", InputType.TYPE_CLASS_TEXT, true);
        root.addView(etSenders);

        root.addView(label("받는 번호 1", 16, true));
        etDest1 = field("010-0000-0000", InputType.TYPE_CLASS_PHONE, false);
        root.addView(etDest1);

        root.addView(label("받는 번호 2", 16, true));
        etDest2 = field("010-0000-0000", InputType.TYPE_CLASS_PHONE, false);
        root.addView(etDest2);

        root.addView(button("저장", v -> save()));

        root.addView(label("안정적으로 쓰려면", 16, true));
        root.addView(button("배터리 최적화에서 제외", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                openAppSettings();
            }
        }));
        root.addView(button("앱 정보 열기", v -> openAppSettings()));

        root.addView(label("전달 이력", 16, true));
        tvHistory = new TextView(this);
        tvHistory.setTextSize(14);
        tvHistory.setTextIsSelectable(true);
        root.addView(tvHistory);
        root.addView(button("이력 지우기", v -> {
            Store.clearHistory(this);
            refreshHistory();
        }));

        setContentView(scroll);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshHistory();
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void load() {
        swEnabled.setChecked(Store.enabled(this));
        etSenders.setText(Store.sendersText(this));
        etDest1.setText(Store.dest1(this));
        etDest2.setText(Store.dest2(this));
    }

    private void save() {
        List<String> senders = Store.parseSenders(etSenders.getText().toString());
        String d1 = etDest1.getText().toString().trim();
        String d2 = etDest2.getText().toString().trim();
        boolean enabled = swEnabled.isChecked();

        for (String s : senders) {
            if (!PhoneUtil.looksLikeNumber(s) && s.length() < 2) {
                toast("발신번호를 확인해 주세요: " + s);
                return;
            }
        }
        if (enabled) {
            if (senders.isEmpty()) { toast("전달할 발신번호를 1개 이상 입력해 주세요."); return; }
            if (!PhoneUtil.looksLikeNumber(d1)) { toast("받는 번호 1을 확인해 주세요."); return; }
            if (!PhoneUtil.looksLikeNumber(d2)) { toast("받는 번호 2를 확인해 주세요."); return; }
            if (PhoneUtil.key(d1).equals(PhoneUtil.key(d2))) { toast("받는 번호 2개는 서로 달라야 해요."); return; }
        }

        StringBuilder sb = new StringBuilder();
        for (String s : senders) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        Store.saveConfig(this, enabled, sb.toString(), d1, d2);
        etSenders.setText(sb.toString());
        toast(enabled ? "저장했어요. 이제 등록된 번호의 문자만 전달돼요." : "저장했어요. (전달 꺼짐)");
        refreshStatus();
    }

    private void refreshStatus() {
        boolean recv = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean send = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        String s = "문자 받기 권한: " + (recv ? "허용됨" : "필요함")
                + "\n문자 보내기 권한: " + (send ? "허용됨" : "필요함")
                + "\n현재 상태: " + (Store.enabled(this) ? "전달 켜짐" : "전달 꺼짐");
        if (!recv || !send) {
            s += "\n\n권한이 허용되지 않으면 '앱 정보 열기' → 오른쪽 위 ⋮ → '제한된 설정 허용'을 먼저 누른 뒤 다시 시도해 주세요.";
        }
        tvStatus.setText(s);
    }

    private void refreshHistory() {
        List<JSONObject> list = Store.history(this);
        if (list.isEmpty()) {
            tvHistory.setText("아직 전달한 문자가 없어요.");
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA);
        StringBuilder sb = new StringBuilder();
        for (JSONObject o : list) {
            sb.append(f.format(new Date(o.optLong("t"))))
              .append("  ").append(o.optBoolean("ok") ? "✔" : "✘").append('\n')
              .append("보낸 사람: ").append(o.optString("from")).append('\n')
              .append("받는 번호: ").append(o.optString("to")).append('\n')
              .append("내용: ").append(o.optString("text")).append('\n')
              .append("결과: ").append(o.optString("note")).append("\n\n");
        }
        tvHistory.setText(sb.toString());
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        refreshStatus();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }
}

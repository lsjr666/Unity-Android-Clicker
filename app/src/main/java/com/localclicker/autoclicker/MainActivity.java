package com.localclicker.autoclicker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private EditText intervalInput;
    private TextView serviceState;
    private TextView runState;
    private TextView modeState;
    private TextView scriptState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private View buildUi() {
        int padding = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(248, 249, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, dp(52), padding, padding);
        scroll.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));

        serviceState = text("", 14, Color.rgb(15, 118, 110), true);
        root.addView(serviceState);
        runState = text("", 14, Color.rgb(55, 65, 81), false);
        runState.setPadding(0, dp(4), 0, 0);
        root.addView(runState);
        modeState = text("", 14, Color.rgb(55, 65, 81), false);
        modeState.setPadding(0, dp(4), 0, 0);
        root.addView(modeState);
        scriptState = text("", 13, Color.rgb(107, 114, 128), false);
        scriptState.setPadding(0, dp(4), 0, dp(18));
        root.addView(scriptState);

        root.addView(label("间隔"));
        intervalInput = input(String.valueOf(ClickConfig.intervalMs(this)), "毫秒");
        root.addView(intervalInput);

        Button save = button("保存频率");
        save.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                saveInterval();
                sendBroadcast(new Intent(ClickConfig.ACTION_CONFIG_CHANGED));
                refreshState();
                Toast.makeText(MainActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(save);

        Button record = button("录制脚本");
        record.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, RecordScriptActivity.class));
            }
        });
        root.addView(record);

        Button controls = button("显示悬浮靶标和控制块");
        controls.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                saveInterval();
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(MainActivity.this, "请先启用无障碍", Toast.LENGTH_SHORT).show();
                    openAccessibilitySettings();
                    return;
                }
                if (!canShowOverlays()) {
                    Toast.makeText(MainActivity.this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show();
                    openOverlaySettings();
                    return;
                }
                sendBroadcast(new Intent(ClickConfig.ACTION_SHOW_CONTROLS));
            }
        });
        root.addView(controls);

        Button accessibility = button("打开无障碍设置");
        accessibility.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        root.addView(accessibility);

        Button overlay = button("打开悬浮窗权限设置");
        overlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlaySettings();
            }
        });
        root.addView(overlay);

        return scroll;
    }

    private void saveInterval() {
        ClickConfig.saveInterval(this, readInt(intervalInput, 500));
    }

    private int readInt(EditText editText, int fallback) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void refreshState() {
        serviceState.setText(isAccessibilityServiceEnabled() ? "无障碍 已启用" : "无障碍 未启用");
        runState.setText(ClickConfig.running(this) ? "运行中" : "已暂停");
        modeState.setText(ScriptStore.modeLabel(this));
        scriptState.setText(scriptSummary());
    }

    private String scriptSummary() {
        ArrayList<ScriptStore.Script> scripts = ScriptStore.loadScripts(this);
        if (scripts.isEmpty()) {
            return "脚本 0/3";
        }
        StringBuilder builder = new StringBuilder("脚本 ");
        builder.append(scripts.size()).append("/3  ");
        for (int i = 0; i < scripts.size(); i++) {
            if (i > 0) {
                builder.append(" · ");
            }
            builder.append(scripts.get(i).name);
        }
        return builder.toString();
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            return false;
        }
        ComponentName expected = new ComponentName(this, ClickAccessibilityService.class);
        return enabledServices.toLowerCase().contains(expected.flattenToString().toLowerCase());
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private boolean canShowOverlays() {
        return android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private TextView label(String value) {
        TextView text = text(value, 13, Color.rgb(31, 41, 55), true);
        text.setPadding(0, dp(10), 0, dp(6));
        return text;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private EditText input(String value, String hint) {
        EditText editText = new EditText(this);
        editText.setText(value);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setTextSize(18);
        editText.setPadding(dp(12), 0, dp(12), 0);
        editText.setBackgroundColor(Color.WHITE);
        editText.setSelectAllOnFocus(true);
        editText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)));
        return editText;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50));
        params.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

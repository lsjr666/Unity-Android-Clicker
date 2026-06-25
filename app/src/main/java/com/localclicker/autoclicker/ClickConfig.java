package com.localclicker.autoclicker;

import android.content.Context;
import android.content.SharedPreferences;

final class ClickConfig {
    static final String ACTION_CONFIG_CHANGED = "com.localclicker.autoclicker.CONFIG_CHANGED";
    static final String ACTION_SHOW_CONTROLS = "com.localclicker.autoclicker.SHOW_CONTROLS";
    static final String ACTION_START = "com.localclicker.autoclicker.START";
    static final String ACTION_PAUSE = "com.localclicker.autoclicker.PAUSE";

    private static final String PREFS = "clicker_config";
    private static final String KEY_INTERVAL_MS = "interval_ms";
    private static final String KEY_X_PERCENT = "x_percent";
    private static final String KEY_Y_PERCENT = "y_percent";
    private static final String KEY_RUNNING = "running";

    private ClickConfig() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int intervalMs(Context context) {
        return clamp(prefs(context).getInt(KEY_INTERVAL_MS, 500), 50, 60000);
    }

    static int xPercent(Context context) {
        return clamp(prefs(context).getInt(KEY_X_PERCENT, 50), 0, 100);
    }

    static int yPercent(Context context) {
        return clamp(prefs(context).getInt(KEY_Y_PERCENT, 50), 0, 100);
    }

    static boolean running(Context context) {
        return prefs(context).getBoolean(KEY_RUNNING, false);
    }

    static void saveInterval(Context context, int intervalMs) {
        prefs(context).edit()
            .putInt(KEY_INTERVAL_MS, clamp(intervalMs, 50, 60000))
            .apply();
    }

    static void savePosition(Context context, int xPercent, int yPercent) {
        prefs(context).edit()
            .putInt(KEY_X_PERCENT, clamp(xPercent, 0, 100))
            .putInt(KEY_Y_PERCENT, clamp(yPercent, 0, 100))
            .apply();
    }

    static void setRunning(Context context, boolean running) {
        prefs(context).edit().putBoolean(KEY_RUNNING, running).apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

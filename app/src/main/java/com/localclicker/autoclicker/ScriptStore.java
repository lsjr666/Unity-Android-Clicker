package com.localclicker.autoclicker;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ScriptStore {
    static final int MAX_SCRIPTS = 3;
    static final int MODE_CLICK = -1;
    static final int ORIENTATION_PORTRAIT = 1;
    static final int ORIENTATION_LANDSCAPE = 2;

    private static final String PREFS = "clicker_scripts";
    private static final String KEY_SCRIPTS = "scripts";
    private static final String KEY_SELECTED_SCRIPT = "selected_script";
    private static final String KEY_PENDING_SCRIPT = "pending_script";

    private ScriptStore() {
    }

    static final class Script {
        String name;
        int orientation;
        final ArrayList<Gesture> gestures = new ArrayList<Gesture>();
    }

    static final class Gesture {
        long delayMs;
        long durationMs;
        final ArrayList<PointF> points = new ArrayList<PointF>();
    }

    static ArrayList<Script> loadScripts(Context context) {
        ArrayList<Script> scripts = new ArrayList<Script>();
        String raw = prefs(context).getString(KEY_SCRIPTS, "[]");
        try {
            JSONArray scriptArray = new JSONArray(raw);
            for (int i = 0; i < scriptArray.length() && scripts.size() < MAX_SCRIPTS; i++) {
                Script script = fromJson(scriptArray.getJSONObject(i), i);
                if (!script.gestures.isEmpty()) {
                    scripts.add(script);
                }
            }
        } catch (JSONException ignored) {
            prefs(context).edit().putString(KEY_SCRIPTS, "[]").apply();
        }
        normalizeSelectedMode(context, scripts);
        return scripts;
    }

    static void saveScript(Context context, Script script, int replaceIndex) {
        ArrayList<Script> scripts = loadScripts(context);
        if (replaceIndex >= 0 && replaceIndex < scripts.size()) {
            scripts.set(replaceIndex, script);
        } else if (scripts.size() < MAX_SCRIPTS) {
            scripts.add(script);
        } else {
            scripts.set(0, script);
        }
        saveScripts(context, scripts);
    }

    static void savePendingScript(Context context, Script script) {
        prefs(context).edit().putString(KEY_PENDING_SCRIPT, toJson(script).toString()).apply();
    }

    static Script pendingScript(Context context) {
        String raw = prefs(context).getString(KEY_PENDING_SCRIPT, "");
        if (raw.length() == 0) {
            return null;
        }
        try {
            Script script = fromJson(new JSONObject(raw), 0);
            return script.gestures.isEmpty() ? null : script;
        } catch (JSONException ex) {
            return null;
        }
    }

    static void clearPendingScript(Context context) {
        prefs(context).edit().remove(KEY_PENDING_SCRIPT).apply();
    }

    static int selectedMode(Context context) {
        ArrayList<Script> scripts = loadScripts(context);
        int selected = prefs(context).getInt(KEY_SELECTED_SCRIPT, MODE_CLICK);
        if (selected >= scripts.size()) {
            selected = MODE_CLICK;
            setSelectedMode(context, selected);
        }
        return selected;
    }

    static void setSelectedMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_SELECTED_SCRIPT, mode).apply();
    }

    static int cycleMode(Context context) {
        ArrayList<Script> scripts = loadScripts(context);
        int selected = selectedMode(context);
        if (scripts.isEmpty()) {
            selected = MODE_CLICK;
        } else if (selected == MODE_CLICK) {
            selected = 0;
        } else if (selected + 1 < scripts.size()) {
            selected++;
        } else {
            selected = MODE_CLICK;
        }
        setSelectedMode(context, selected);
        return selected;
    }

    static String modeLabel(Context context) {
        ArrayList<Script> scripts = loadScripts(context);
        int selected = selectedMode(context);
        if (selected == MODE_CLICK || selected >= scripts.size()) {
            return "模式：连点";
        }
        Script script = scripts.get(selected);
        return "脚本：" + orientationLabel(script.orientation) + " " + script.name;
    }

    static Script selectedScript(Context context) {
        ArrayList<Script> scripts = loadScripts(context);
        int selected = selectedMode(context);
        if (selected < 0 || selected >= scripts.size()) {
            return null;
        }
        return scripts.get(selected);
    }

    static int currentOrientation(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels
            > context.getResources().getDisplayMetrics().heightPixels
            ? ORIENTATION_LANDSCAPE
            : ORIENTATION_PORTRAIT;
    }

    static String orientationLabel(int orientation) {
        if (orientation == ORIENTATION_LANDSCAPE) {
            return "横屏";
        }
        if (orientation == ORIENTATION_PORTRAIT) {
            return "竖屏";
        }
        return "未知";
    }

    private static void saveScripts(Context context, List<Script> scripts) {
        JSONArray scriptArray = new JSONArray();
        for (int i = 0; i < scripts.size() && i < MAX_SCRIPTS; i++) {
            Script script = scripts.get(i);
            scriptArray.put(toJson(script));
        }
        prefs(context).edit().putString(KEY_SCRIPTS, scriptArray.toString()).apply();
        normalizeSelectedMode(context, new ArrayList<Script>(scripts));
    }

    private static void normalizeSelectedMode(Context context, ArrayList<Script> scripts) {
        int selected = prefs(context).getInt(KEY_SELECTED_SCRIPT, MODE_CLICK);
        if (selected >= scripts.size()) {
            prefs(context).edit().putInt(KEY_SELECTED_SCRIPT, MODE_CLICK).apply();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Script fromJson(JSONObject scriptObject, int fallbackIndex) throws JSONException {
        Script script = new Script();
        script.name = scriptObject.optString("name", "脚本" + (fallbackIndex + 1));
        script.orientation = scriptObject.optInt("orientation", 0);
        JSONArray gestureArray = scriptObject.optJSONArray("gestures");
        if (gestureArray == null) {
            return script;
        }
        for (int j = 0; j < gestureArray.length(); j++) {
            JSONObject gestureObject = gestureArray.getJSONObject(j);
            Gesture gesture = new Gesture();
            gesture.delayMs = Math.max(0, gestureObject.optLong("delayMs", 0));
            gesture.durationMs = Math.max(1, gestureObject.optLong("durationMs", 1));
            JSONArray pointArray = gestureObject.optJSONArray("points");
            if (pointArray == null) {
                continue;
            }
            for (int k = 0; k < pointArray.length(); k++) {
                JSONArray point = pointArray.getJSONArray(k);
                gesture.points.add(new PointF(
                    (float) point.optDouble(0, 50),
                    (float) point.optDouble(1, 50)));
            }
            if (!gesture.points.isEmpty()) {
                script.gestures.add(gesture);
            }
        }
        return script;
    }

    private static JSONObject toJson(Script script) {
        JSONObject scriptObject = new JSONObject();
        try {
            scriptObject.put("name", script.name);
            scriptObject.put("orientation", script.orientation);
            JSONArray gestureArray = new JSONArray();
            for (int j = 0; j < script.gestures.size(); j++) {
                Gesture gesture = script.gestures.get(j);
                JSONObject gestureObject = new JSONObject();
                gestureObject.put("delayMs", gesture.delayMs);
                gestureObject.put("durationMs", Math.max(1, gesture.durationMs));
                JSONArray pointArray = new JSONArray();
                for (int k = 0; k < gesture.points.size(); k++) {
                    PointF point = gesture.points.get(k);
                    JSONArray pointJson = new JSONArray();
                    pointJson.put(point.x);
                    pointJson.put(point.y);
                    pointArray.put(pointJson);
                }
                gestureObject.put("points", pointArray);
                gestureArray.put(gestureObject);
            }
            scriptObject.put("gestures", gestureArray);
        } catch (JSONException ignored) {
        }
        return scriptObject;
    }
}

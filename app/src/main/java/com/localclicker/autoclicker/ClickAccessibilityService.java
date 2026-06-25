package com.localclicker.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

public class ClickAccessibilityService extends AccessibilityService {
    private static final int TARGET_SIZE_DP = 42;
    private static final int CONTROL_WIDTH_DP = 86;
    private static final int CONTROL_HEIGHT_DP = 38;
    private static final int MODE_WIDTH_DP = 126;
    private static final int MODE_HEIGHT_DP = 34;
    private static final int REC_WIDTH_DP = 82;
    private static final int REC_HEIGHT_DP = 34;
    private static final int STOP_WIDTH_DP = 58;
    private static final int STOP_HEIGHT_DP = 34;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ClickConfig.ACTION_SHOW_CONTROLS.equals(action)) {
                ensureFloatingControls();
            } else if (ClickConfig.ACTION_START.equals(action)) {
                startRunning();
            } else if (ClickConfig.ACTION_PAUSE.equals(action)) {
                pauseRunning();
            } else if (ClickConfig.ACTION_CONFIG_CHANGED.equals(action) && ClickConfig.running(context)) {
                restartRunning();
            }
        }
    };

    private boolean registered;
    private boolean running;
    private boolean dispatching;
    private boolean overlayRecording;
    private int scriptGestureIndex;
    private WindowManager overlayManager;
    private TargetView targetView;
    private TextView controlView;
    private TextView modeView;
    private TextView recordView;
    private TextView stopRecordView;
    private RecordOverlayView recordOverlayView;
    private WindowManager.LayoutParams targetParams;
    private WindowManager.LayoutParams controlParams;
    private WindowManager.LayoutParams modeParams;
    private WindowManager.LayoutParams recordParams;
    private WindowManager.LayoutParams stopRecordParams;
    private WindowManager.LayoutParams recordOverlayParams;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running || dispatching) {
                return;
            }
            if (ScriptStore.selectedMode(ClickAccessibilityService.this) == ScriptStore.MODE_CLICK) {
                performSingleClick();
            } else {
                performSelectedScript();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        registerCommands();
        ensureFloatingControls();
        if (ClickConfig.running(this)) {
            startRunning();
        }
        Toast.makeText(this, "连点器服务已启用", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerCommands();
        if (intent != null && ClickConfig.ACTION_SHOW_CONTROLS.equals(intent.getAction())) {
            ensureFloatingControls();
        } else if (intent != null && ClickConfig.ACTION_START.equals(intent.getAction())) {
            startRunning();
        } else if (intent != null && ClickConfig.ACTION_PAUSE.equals(intent.getAction())) {
            pauseRunning();
        }
        return START_STICKY;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        pauseRunning();
    }

    @Override
    public void onDestroy() {
        pauseRunning();
        removeFloatingControls();
        if (registered) {
            unregisterReceiver(receiver);
            registered = false;
        }
        super.onDestroy();
    }

    private void registerCommands() {
        if (registered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ClickConfig.ACTION_CONFIG_CHANGED);
        filter.addAction(ClickConfig.ACTION_SHOW_CONTROLS);
        filter.addAction(ClickConfig.ACTION_START);
        filter.addAction(ClickConfig.ACTION_PAUSE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        registered = true;
    }

    private void startRunning() {
        if (!canShowOverlays()) {
            toastOverlayMissing();
        }
        if (ScriptStore.selectedMode(this) != ScriptStore.MODE_CLICK && ScriptStore.selectedScript(this) == null) {
            Toast.makeText(this, "没有可执行的脚本", Toast.LENGTH_SHORT).show();
            ScriptStore.setSelectedMode(this, ScriptStore.MODE_CLICK);
        }
        running = true;
        scriptGestureIndex = 0;
        ClickConfig.setRunning(this, true);
        ensureFloatingControls();
        updateControlLabels();
        setTargetVisible(false);
        handler.removeCallbacks(loop);
        handler.post(loop);
    }

    private void pauseRunning() {
        running = false;
        dispatching = false;
        scriptGestureIndex = 0;
        ClickConfig.setRunning(this, false);
        handler.removeCallbacks(loop);
        updateControlLabels();
        setTargetVisible(ScriptStore.selectedMode(this) == ScriptStore.MODE_CLICK);
    }

    private void restartRunning() {
        pauseRunning();
        startRunning();
    }

    private void performSingleClick() {
        Point size = currentDisplaySize();
        float x = size.x * (ClickConfig.xPercent(this) / 100f);
        float y = size.y * (ClickConfig.yPercent(this) / 100f);
        Path path = new Path();
        path.moveTo(x, y);
        dispatchPath(path, 35, new Runnable() {
            @Override
            public void run() {
                if (running) {
                    handler.postDelayed(loop, ClickConfig.intervalMs(ClickAccessibilityService.this));
                }
            }
        });
    }

    private void performSelectedScript() {
        final ScriptStore.Script script = ScriptStore.selectedScript(this);
        if (script == null || script.gestures.isEmpty()) {
            Toast.makeText(this, "没有可执行的脚本", Toast.LENGTH_SHORT).show();
            pauseRunning();
            return;
        }
        int currentOrientation = ScriptStore.currentOrientation(this);
        if (script.orientation != 0 && script.orientation != currentOrientation) {
            Toast.makeText(this, "请切换到" + ScriptStore.orientationLabel(script.orientation) + "后执行", Toast.LENGTH_SHORT).show();
            pauseRunning();
            return;
        }
        if (scriptGestureIndex >= script.gestures.size()) {
            scriptGestureIndex = 0;
            if (running) {
                handler.postDelayed(loop, ClickConfig.intervalMs(this));
            }
            return;
        }

        final ScriptStore.Gesture gesture = script.gestures.get(scriptGestureIndex);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (running) {
                    dispatchScriptGesture(gesture);
                }
            }
        }, gesture.delayMs);
    }

    private void dispatchScriptGesture(ScriptStore.Gesture gesture) {
        Path path = new Path();
        Point size = currentDisplaySize();
        for (int i = 0; i < gesture.points.size(); i++) {
            PointF point = gesture.points.get(i);
            float x = size.x * (point.x / 100f);
            float y = size.y * (point.y / 100f);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        dispatchPath(path, Math.max(1, gesture.durationMs), new Runnable() {
            @Override
            public void run() {
                scriptGestureIndex++;
                if (running) {
                    handler.post(loop);
                }
            }
        });
    }

    private void dispatchPath(Path path, long durationMs, final Runnable completed) {
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, durationMs);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();

        dispatching = true;
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                dispatching = false;
                completed.run();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                dispatching = false;
                completed.run();
            }
        }, handler);
    }

    @SuppressWarnings("deprecation")
    private Point currentDisplaySize() {
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        return size;
    }

    private void ensureFloatingControls() {
        if (!canShowOverlays()) {
            toastOverlayMissing();
            return;
        }
        if (overlayManager == null) {
            overlayManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (controlView == null) {
            addControlOverlay();
        }
        if (modeView == null) {
            addModeOverlay();
        }
        if (recordView == null) {
            addRecordOverlay();
        }
        if (targetView == null) {
            addTargetOverlay();
        }
        updateControlLabels();
        setTargetVisible(!running && ScriptStore.selectedMode(this) == ScriptStore.MODE_CLICK);
    }

    private void addControlOverlay() {
        controlView = makeTextOverlay(14, true);
        controlView.setOnTouchListener(new DraggableTouchHandler() {
            @Override
            WindowManager.LayoutParams params() {
                return controlParams;
            }

            @Override
            View overlayView() {
                return controlView;
            }

            @Override
            void onTap() {
                if (running) {
                    pauseRunning();
                } else {
                    startRunning();
                }
            }
        });

        controlParams = overlayParams(CONTROL_WIDTH_DP, CONTROL_HEIGHT_DP);
        controlParams.gravity = Gravity.TOP | Gravity.LEFT;
        Point size = currentDisplaySize();
        controlParams.x = Math.max(0, size.x - dp(CONTROL_WIDTH_DP) - dp(18));
        controlParams.y = dp(108);
        overlayManager.addView(controlView, controlParams);
    }

    private void addModeOverlay() {
        modeView = makeTextOverlay(11, false);
        modeView.setOnTouchListener(new DraggableTouchHandler() {
            @Override
            WindowManager.LayoutParams params() {
                return modeParams;
            }

            @Override
            View overlayView() {
                return modeView;
            }

            @Override
            void onTap() {
                if (running) {
                    Toast.makeText(ClickAccessibilityService.this, "请先暂停再切换模式", Toast.LENGTH_SHORT).show();
                    return;
                }
                ScriptStore.cycleMode(ClickAccessibilityService.this);
                updateControlLabels();
                setTargetVisible(ScriptStore.selectedMode(ClickAccessibilityService.this) == ScriptStore.MODE_CLICK);
            }
        });

        modeParams = overlayParams(MODE_WIDTH_DP, MODE_HEIGHT_DP);
        modeParams.gravity = Gravity.TOP | Gravity.LEFT;
        Point size = currentDisplaySize();
        modeParams.x = Math.max(0, size.x - dp(MODE_WIDTH_DP) - dp(18));
        modeParams.y = dp(150);
        overlayManager.addView(modeView, modeParams);
    }

    private void addRecordOverlay() {
        recordView = makeTextOverlay(11, false);
        recordView.setOnTouchListener(new DraggableTouchHandler() {
            @Override
            WindowManager.LayoutParams params() {
                return recordParams;
            }

            @Override
            View overlayView() {
                return recordView;
            }

            @Override
            void onTap() {
                startOverlayRecording();
            }
        });

        recordParams = overlayParams(REC_WIDTH_DP, REC_HEIGHT_DP);
        recordParams.gravity = Gravity.TOP | Gravity.LEFT;
        Point size = currentDisplaySize();
        recordParams.x = Math.max(0, size.x - dp(REC_WIDTH_DP) - dp(18));
        recordParams.y = dp(192);
        overlayManager.addView(recordView, recordParams);
    }

    private TextView makeTextOverlay(int textSp, boolean bold) {
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(textSp);
        view.setGravity(Gravity.CENTER);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        view.setPadding(dp(5), 0, dp(5), 0);
        view.setElevation(dp(8));
        return view;
    }

    private void addTargetOverlay() {
        targetView = new TargetView(this);
        targetView.setOnTouchListener(new DraggableTouchHandler() {
            @Override
            WindowManager.LayoutParams params() {
                return targetParams;
            }

            @Override
            View overlayView() {
                return targetView;
            }

            @Override
            void onDragFinished() {
                saveTargetFromOverlay();
            }

            @Override
            void onTap() {
                saveTargetFromOverlay();
                Toast.makeText(ClickAccessibilityService.this, "靶标位置已保存", Toast.LENGTH_SHORT).show();
            }
        });

        targetParams = overlayParams(TARGET_SIZE_DP, TARGET_SIZE_DP);
        targetParams.gravity = Gravity.TOP | Gravity.LEFT;
        moveTargetToSavedPosition();
        overlayManager.addView(targetView, targetParams);
    }

    private WindowManager.LayoutParams overlayParams(int widthDp, int heightDp) {
        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        return new WindowManager.LayoutParams(
            dp(widthDp),
            dp(heightDp),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
    }

    private WindowManager.LayoutParams fullScreenOverlayParams() {
        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        Point size = currentDisplaySize();
        return new WindowManager.LayoutParams(
            size.x,
            size.y,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
    }

    private void moveTargetToSavedPosition() {
        Point size = currentDisplaySize();
        int targetSize = dp(TARGET_SIZE_DP);
        int centerX = Math.round(size.x * (ClickConfig.xPercent(this) / 100f));
        int centerY = Math.round(size.y * (ClickConfig.yPercent(this) / 100f));
        targetParams.x = clamp(centerX - targetSize / 2, 0, Math.max(0, size.x - targetSize));
        targetParams.y = clamp(centerY - targetSize / 2, 0, Math.max(0, size.y - targetSize));
    }

    private void saveTargetFromOverlay() {
        if (targetParams == null) {
            return;
        }
        Point size = currentDisplaySize();
        int targetSize = dp(TARGET_SIZE_DP);
        int centerX = targetParams.x + targetSize / 2;
        int centerY = targetParams.y + targetSize / 2;
        int xPercent = Math.round(centerX * 100f / Math.max(1, size.x));
        int yPercent = Math.round(centerY * 100f / Math.max(1, size.y));
        ClickConfig.savePosition(this, xPercent, yPercent);
    }

    private void updateControlLabels() {
        if (controlView != null) {
            controlView.setText(running ? "暂停" : "启动");
            controlView.setBackground(rounded(
                running ? Color.rgb(220, 38, 38) : Color.rgb(15, 118, 110),
                dp(18)));
        }
        if (modeView != null) {
            modeView.setText(ScriptStore.modeLabel(this));
            modeView.setBackground(rounded(Color.rgb(55, 65, 81), dp(15)));
        }
        if (recordView != null) {
            recordView.setText(overlayRecording ? "录制中" : "录制");
            recordView.setBackground(rounded(
                overlayRecording ? Color.rgb(220, 38, 38) : Color.rgb(55, 65, 81),
                dp(15)));
        }
    }

    private void setTargetVisible(boolean visible) {
        if (targetView != null) {
            targetView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void removeFloatingControls() {
        stopOverlayRecording(false);
        removeOverlay(targetView);
        removeOverlay(recordView);
        removeOverlay(modeView);
        removeOverlay(controlView);
        targetView = null;
        recordView = null;
        modeView = null;
        controlView = null;
    }

    private void startOverlayRecording() {
        if (!canShowOverlays()) {
            toastOverlayMissing();
            return;
        }
        if (overlayRecording) {
            return;
        }
        if (running) {
            pauseRunning();
        }
        ensureFloatingControls();
        overlayRecording = true;
        setTargetVisible(false);

        recordOverlayView = new RecordOverlayView(this);
        recordOverlayParams = fullScreenOverlayParams();
        recordOverlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        recordOverlayParams.x = 0;
        recordOverlayParams.y = 0;
        overlayManager.addView(recordOverlayView, recordOverlayParams);

        stopRecordView = makeTextOverlay(14, true);
        stopRecordView.setText("停止");
        stopRecordView.setBackground(rounded(Color.rgb(220, 38, 38), dp(15)));
        stopRecordView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopOverlayRecording(true);
            }
        });
        stopRecordParams = overlayParams(STOP_WIDTH_DP, STOP_HEIGHT_DP);
        stopRecordParams.gravity = Gravity.TOP | Gravity.RIGHT;
        stopRecordParams.x = dp(18);
        stopRecordParams.y = dp(18);
        overlayManager.addView(stopRecordView, stopRecordParams);
        updateControlLabels();
    }

    private void stopOverlayRecording(boolean save) {
        if (!overlayRecording && recordOverlayView == null && stopRecordView == null) {
            return;
        }
        ScriptStore.Script script = recordOverlayView == null ? null : recordOverlayView.toScript();
        overlayRecording = false;
        removeOverlay(stopRecordView);
        removeOverlay(recordOverlayView);
        stopRecordView = null;
        recordOverlayView = null;
        updateControlLabels();
        setTargetVisible(ScriptStore.selectedMode(this) == ScriptStore.MODE_CLICK);
        if (!save) {
            return;
        }
        if (script == null || script.gestures.isEmpty()) {
            Toast.makeText(this, "没有录制到操作", Toast.LENGTH_SHORT).show();
            return;
        }
        ScriptStore.savePendingScript(this, script);
        Intent intent = new Intent(this, RecordScriptActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void removeOverlay(View view) {
        if (view == null || overlayManager == null) {
            return;
        }
        try {
            overlayManager.removeView(view);
        } catch (IllegalArgumentException ignored) {
            // The system may already have removed the overlay during service teardown.
        }
    }

    private boolean canShowOverlays() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void toastOverlayMissing() {
        Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radiusPx);
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private abstract class DraggableTouchHandler implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;
        private boolean moved;

        abstract WindowManager.LayoutParams params();

        abstract View overlayView();

        void onTap() {
        }

        void onDragFinished() {
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            WindowManager.LayoutParams params = params();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - touchX);
                    int dy = Math.round(event.getRawY() - touchY);
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                        moved = true;
                    }
                    Point size = currentDisplaySize();
                    int maxX = Math.max(0, size.x - params.width);
                    int maxY = Math.max(0, size.y - params.height);
                    params.x = clamp(startX + dx, 0, maxX);
                    params.y = clamp(startY + dy, 0, maxY);
                    overlayManager.updateViewLayout(overlayView(), params);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        onDragFinished();
                    } else {
                        onTap();
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class TargetView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TargetView(Context context) {
            super(context);
            fillPaint.setColor(Color.argb(190, 249, 115, 22));
            ringPaint.setColor(Color.WHITE);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(3));
            linePaint.setColor(Color.WHITE);
            linePaint.setStrokeWidth(dp(2));
            setElevation(dp(10));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(4);
            canvas.drawCircle(cx, cy, radius, fillPaint);
            canvas.drawCircle(cx, cy, radius - dp(4), ringPaint);
            canvas.drawLine(cx, dp(7), cx, getHeight() - dp(7), linePaint);
            canvas.drawLine(dp(7), cy, getWidth() - dp(7), cy, linePaint);
        }
    }

    private final class RecordOverlayView extends View {
        private final java.util.ArrayList<RecordedGesture> gestures = new java.util.ArrayList<RecordedGesture>();
        private RecordedGesture current;
        private boolean started;
        private long lastGestureEndMs;

        RecordOverlayView(Context context) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    current = new RecordedGesture();
                    long now = System.currentTimeMillis();
                    if (!started) {
                        started = true;
                        current.delayMs = 0;
                    } else {
                        current.delayMs = Math.max(0, now - lastGestureEndMs);
                    }
                    current.startedAtMs = event.getEventTime();
                    addPoint(event.getX(), event.getY());
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (current != null) {
                        PointF last = current.points.get(current.points.size() - 1);
                        if (distance(last.x, last.y, event.getX(), event.getY()) >= dp(4)) {
                            addPoint(event.getX(), event.getY());
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (current != null) {
                        addPoint(event.getX(), event.getY());
                        current.durationMs = Math.max(1, event.getEventTime() - current.startedAtMs);
                        gestures.add(current);
                        current = null;
                        lastGestureEndMs = System.currentTimeMillis();
                    }
                    return true;
                default:
                    return true;
            }
        }

        ScriptStore.Script toScript() {
            ScriptStore.Script script = new ScriptStore.Script();
            script.orientation = ScriptStore.currentOrientation(ClickAccessibilityService.this);
            for (int i = 0; i < gestures.size(); i++) {
                RecordedGesture recorded = gestures.get(i);
                ScriptStore.Gesture gesture = new ScriptStore.Gesture();
                gesture.delayMs = recorded.delayMs;
                gesture.durationMs = Math.max(1, recorded.durationMs);
                for (int j = 0; j < recorded.points.size(); j++) {
                    PointF point = recorded.points.get(j);
                    gesture.points.add(new PointF(
                        point.x * 100f / Math.max(1, getWidth()),
                        point.y * 100f / Math.max(1, getHeight())));
                }
                if (!gesture.points.isEmpty()) {
                    script.gestures.add(gesture);
                }
            }
            return script;
        }

        private void addPoint(float x, float y) {
            current.points.add(new PointF(x, y));
        }

        private float distance(float x1, float y1, float x2, float y2) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    private static final class RecordedGesture {
        long delayMs;
        long durationMs;
        long startedAtMs;
        final java.util.ArrayList<PointF> points = new java.util.ArrayList<PointF>();
    }
}

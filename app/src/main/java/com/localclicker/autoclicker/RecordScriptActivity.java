package com.localclicker.autoclicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class RecordScriptActivity extends Activity {
    private RecordCanvasView canvasView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScriptStore.Script pending = ScriptStore.pendingScript(this);
        if (pending != null) {
            showNameDialog(pending, true);
            return;
        }
        setContentView(buildUi());
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        canvasView = new RecordCanvasView(this);
        root.addView(canvasView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        Button stop = new Button(this);
        stop.setText("停止");
        stop.setTextSize(16);
        stop.setTextColor(Color.WHITE);
        stop.setBackgroundColor(Color.rgb(220, 38, 38));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAndNameScript();
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(86), dp(48));
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.setMargins(0, dp(18), dp(18), 0);
        root.addView(stop, params);
        return root;
    }

    private void stopAndNameScript() {
        final ScriptStore.Script script = canvasView.toScript();
        if (script.gestures.isEmpty()) {
            Toast.makeText(this, "没有录制到点击或滑动", Toast.LENGTH_SHORT).show();
            return;
        }

        showNameDialog(script, false);
    }

    private void showNameDialog(final ScriptStore.Script script, final boolean clearPending) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(defaultScriptName());
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
            .setTitle("命名脚本")
            .setView(input)
            .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String name = input.getText().toString().trim();
                    script.name = name.length() == 0 ? defaultScriptName() : name;
                    if (script.orientation == 0) {
                        script.orientation = ScriptStore.currentOrientation(RecordScriptActivity.this);
                    }
                    if (clearPending) {
                        ScriptStore.clearPendingScript(RecordScriptActivity.this);
                    }
                    saveWithLimit(script);
                }
            })
            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (clearPending) {
                        ScriptStore.clearPendingScript(RecordScriptActivity.this);
                        finish();
                    }
                }
            })
            .show();
    }

    private void saveWithLimit(final ScriptStore.Script script) {
        ArrayList<ScriptStore.Script> scripts = ScriptStore.loadScripts(this);
        if (scripts.size() < ScriptStore.MAX_SCRIPTS) {
            ScriptStore.saveScript(this, script, -1);
            Toast.makeText(this, "脚本已保存", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String[] names = new String[scripts.size()];
        for (int i = 0; i < scripts.size(); i++) {
            names[i] = "覆盖: " + scripts.get(i).name;
        }
        new AlertDialog.Builder(this)
            .setTitle("脚本最多保存三个")
            .setItems(names, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ScriptStore.saveScript(RecordScriptActivity.this, script, which);
                    Toast.makeText(RecordScriptActivity.this, "脚本已保存", Toast.LENGTH_SHORT).show();
                    finish();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private String defaultScriptName() {
        return "脚本" + (ScriptStore.loadScripts(this).size() + 1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class RecordCanvasView extends View {
        private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<RecordedGesture> gestures = new ArrayList<RecordedGesture>();
        private RecordedGesture current;
        private long lastGestureEndMs;
        private boolean hasStartedRecording;

        RecordCanvasView(Activity activity) {
            super(activity);
            setBackgroundColor(Color.rgb(246, 247, 249));
            pathPaint.setColor(Color.rgb(15, 118, 110));
            pathPaint.setStyle(Paint.Style.STROKE);
            pathPaint.setStrokeWidth(dp(4));
            pathPaint.setStrokeCap(Paint.Cap.ROUND);
            pathPaint.setStrokeJoin(Paint.Join.ROUND);
            pointPaint.setColor(Color.rgb(249, 115, 22));
            textPaint.setColor(Color.rgb(75, 85, 99));
            textPaint.setTextSize(dp(16));
            lastGestureEndMs = 0;
            hasStartedRecording = false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawText("在画布上点击或滑动，右上角停止保存", dp(18), dp(58), textPaint);
            for (int i = 0; i < gestures.size(); i++) {
                drawGesture(canvas, gestures.get(i));
            }
            if (current != null) {
                drawGesture(canvas, current);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isStopButtonArea(event)) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    current = new RecordedGesture();
                    long now = System.currentTimeMillis();
                    if (!hasStartedRecording) {
                        hasStartedRecording = true;
                        current.delayMs = 0;
                    } else {
                        current.delayMs = Math.max(0, now - lastGestureEndMs);
                    }
                    current.startedAtMs = event.getEventTime();
                    addPoint(current, event.getX(), event.getY());
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (current != null) {
                        PointF last = current.points.get(current.points.size() - 1);
                        if (distance(last.x, last.y, event.getX(), event.getY()) >= dp(4)) {
                            addPoint(current, event.getX(), event.getY());
                            invalidate();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (current != null) {
                        addPoint(current, event.getX(), event.getY());
                        current.durationMs = Math.max(1, event.getEventTime() - current.startedAtMs);
                        gestures.add(current);
                        lastGestureEndMs = System.currentTimeMillis();
                        current = null;
                        invalidate();
                    }
                    return true;
                default:
                    return true;
            }
        }

        ScriptStore.Script toScript() {
        ScriptStore.Script script = new ScriptStore.Script();
        script.orientation = ScriptStore.currentOrientation(RecordScriptActivity.this);
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

        private void addPoint(RecordedGesture gesture, float x, float y) {
            gesture.points.add(new PointF(x, y));
        }

        private void drawGesture(Canvas canvas, RecordedGesture gesture) {
            if (gesture.points.isEmpty()) {
                return;
            }
            Path path = new Path();
            PointF first = gesture.points.get(0);
            path.moveTo(first.x, first.y);
            for (int i = 1; i < gesture.points.size(); i++) {
                PointF point = gesture.points.get(i);
                path.lineTo(point.x, point.y);
            }
            canvas.drawPath(path, pathPaint);
            canvas.drawCircle(first.x, first.y, dp(6), pointPaint);
        }

        private boolean isStopButtonArea(MotionEvent event) {
            return event.getY() < dp(80) && event.getX() > getWidth() - dp(130);
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
        final ArrayList<PointF> points = new ArrayList<PointF>();
    }
}

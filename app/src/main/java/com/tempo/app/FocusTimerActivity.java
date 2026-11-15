package com.tempo.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tempo.app.model.TaskFeedback;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;

import java.util.Locale;

/** Dedicated focus page. The clock keeps running while this page is closed. */
public class FocusTimerActivity extends AppCompatActivity {
    private static final int INK = Color.rgb(30, 48, 52);
    private static final int BG = Color.rgb(247, 248, 245);
    private static final int MINT = Color.rgb(194, 232, 213);
    private static final String PREF = "tempo_focus_timer";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int taskId;
    private String title;
    private TextView clock;
    private TextView toggle;
    private TextView status;
    private TextView finishAction;
    private TextView targetLabel;
    private ProgressRingView progressRing;
    private int targetMinutes;
    private long previousFocusSeconds;
    private final Runnable tick = this::refresh;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        taskId = getIntent().getIntExtra("taskId", Integer.MIN_VALUE);
        title = getIntent().getStringExtra("title");
        targetMinutes = getIntent().getIntExtra("targetMinutes", 0);
        if (taskId == Integer.MIN_VALUE || title == null) { finish(); return; }
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(16), 0, dp(20));
        page.setBackgroundColor(BG);
        setContentView(page);
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        BackChevronView back = new BackChevronView(this, "返回日程");
        back.setOnClickListener(v -> finish());
        navigation.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView backLabel = text("日程", 14, Color.rgb(105, 120, 119), false);
        backLabel.setGravity(Gravity.CENTER_VERTICAL);
        backLabel.setOnClickListener(v -> finish());
        backLabel.setIncludeFontPadding(false);
        LinearLayout.LayoutParams backLabelParams = new LinearLayout.LayoutParams(-2, dp(48));
        backLabelParams.leftMargin = -dp(8);
        navigation.addView(backLabel, backLabelParams);
        page.addView(navigation, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(22), 0, dp(22), 0);
        page.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView kicker = text("专注计时", 13, Color.rgb(52, 126, 91), true);
        kicker.setGravity(Gravity.CENTER);
        center.addView(kicker);
        space(center, 12);
        TextView taskTitle = text(title, 27, INK, true);
        taskTitle.setGravity(Gravity.CENTER);
        taskTitle.setMaxLines(2);
        center.addView(taskTitle);
        space(center, 32);

        FrameLayout dial = new FrameLayout(this);
        GradientDrawable dialBackground = new GradientDrawable();
        dialBackground.setShape(GradientDrawable.OVAL);
        dialBackground.setColor(Color.WHITE);
        dialBackground.setStroke(dp(1), Color.rgb(225, 231, 226));
        dial.setBackground(dialBackground);
        progressRing = new ProgressRingView();
        dial.addView(progressRing, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout digits = new LinearLayout(this);
        digits.setOrientation(LinearLayout.VERTICAL);
        digits.setGravity(Gravity.CENTER);
        status = text("准备开始", 13, Color.rgb(52, 126, 91), true);
        status.setGravity(Gravity.CENTER);
        digits.addView(status);
        space(digits, 12);
        clock = text("00:00:00", 39, INK, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        digits.addView(clock);
        space(digits, 12);
        TextView elapsedLabel = text("已专注时间", 12, Color.rgb(105, 120, 119), false);
        elapsedLabel.setGravity(Gravity.CENTER);
        digits.addView(elapsedLabel);
        space(digits, 8);
        targetLabel = text("目标时间读取中", 12, Color.rgb(105, 120, 119), false);
        targetLabel.setGravity(Gravity.CENTER);
        digits.addView(targetLabel);
        dial.addView(digits, new FrameLayout.LayoutParams(-1, -1));
        center.addView(dial, new LinearLayout.LayoutParams(dp(264), dp(264)));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(22), 0, dp(22), 0);
        TextView note = text("离开页面后，计时仍会继续", 12, Color.rgb(105, 120, 119), false);
        note.setGravity(Gravity.CENTER);
        footer.addView(note);
        space(footer, 18);
        toggle = action("开始专注", MINT, false);
        toggle.setOnClickListener(v -> {
            if (isRunning()) pause(); else start();
            refresh();
        });
        footer.addView(toggle, new LinearLayout.LayoutParams(-1, dp(56)));
        space(footer, 10);
        finishAction = action("结束并查看用时", Color.WHITE, true);
        finishAction.setOnClickListener(v -> { if (elapsed() > 0) finishSession(); });
        footer.addView(finishAction, new LinearLayout.LayoutParams(-1, dp(54)));
        page.addView(footer);
        if (taskId > 0) RepositoryProvider.getTaskRepository().getTaskById(taskId, new TaskRepository.Callback<com.tempo.app.model.Task>() {
            @Override public void onSuccess(com.tempo.app.model.Task task) {
                if (task != null) {
                    targetMinutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
                    refresh();
                }
            }
            @Override public void onError(Exception error) { }
        });
        if (taskId > 0) RepositoryProvider.getTaskRepository().getFeedbackByTaskId(taskId,
                new TaskRepository.Callback<java.util.List<TaskFeedback>>() {
                    @Override public void onSuccess(java.util.List<TaskFeedback> feedback) {
                        long totalMinutes = 0;
                        for (TaskFeedback row : feedback) totalMinutes += Math.max(0, row.getActualDuration());
                        previousFocusSeconds = totalMinutes * 60;
                        refresh();
                    }
                    @Override public void onError(Exception error) { }
                });
        refresh();
    }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private SharedPreferences prefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }
    private boolean isRunning() { return prefs().getInt("taskId", Integer.MIN_VALUE) == taskId && prefs().getLong("started", 0) > 0; }
    private long elapsed() {
        if (prefs().getInt("taskId", Integer.MIN_VALUE) != taskId) return 0;
        long started = prefs().getLong("started", 0);
        return prefs().getLong("elapsed", 0) + (started > 0 ? Math.max(0, SystemClock.elapsedRealtime() - started) : 0);
    }
    private void start() {
        int active = prefs().getInt("taskId", Integer.MIN_VALUE);
        if (active != Integer.MIN_VALUE && active != taskId) {
            new AlertDialog.Builder(this).setMessage("请先结束另一项任务的计时。")
                    .setPositiveButton("好的", null).show(); return;
        }
        prefs().edit().putInt("taskId", taskId).putLong("started", SystemClock.elapsedRealtime()).apply();
    }
    private void pause() { prefs().edit().putLong("elapsed", elapsed()).putLong("started", 0).apply(); }
    private void refresh() {
        long seconds = elapsed() / 1000;
        clock.setText(String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
        boolean running = isRunning();
        status.setText(running ? "专注进行中" : seconds > 0 ? "已暂停" : "准备开始");
        if (targetMinutes > 0) {
            long targetSeconds = targetMinutes * 60L;
            long totalSeconds = previousFocusSeconds + seconds;
            progressRing.setProgress(Math.min(1f, totalSeconds / (float) targetSeconds));
            targetLabel.setText(totalSeconds > targetSeconds
                    ? "累计已超出预计 " + ((totalSeconds - targetSeconds + 59) / 60) + " 分钟"
                    : "累计 " + (totalSeconds / 60) + " / 预计 " + targetMinutes + " 分钟");
        } else {
            progressRing.setProgress(0f);
            targetLabel.setText("暂无预计用时");
        }
        toggle.setText(running ? "暂停计时" : seconds > 0 ? "继续专注" : "开始专注");
        finishAction.setAlpha(seconds > 0 ? 1f : 0.48f);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
    }
    private void finishSession() {
        int minutes = (int) Math.max(1, (elapsed() + 59_999) / 60_000);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(26), dp(24), dp(20));
        panel.setBackground(rounded(Color.WHITE, 22, false));
        panel.addView(text("本次专注", 14, Color.rgb(52, 126, 91), true));
        space(panel, 5);
        panel.addView(text(minutes + " 分钟", 31, INK, true));
        space(panel, 8);
        panel.addView(text("确认后保存本次用时，任务仍留在待办列表。", 13,
                Color.rgb(105, 120, 119), false));
        space(panel, 24);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        TextView confirm = action("确认并保存", MINT, false);
        panel.addView(confirm, new LinearLayout.LayoutParams(-1, dp(52)));
        confirm.setOnClickListener(v -> { dialog.dismiss(); save(minutes); });
        space(panel, 9);
        TextView discard = action("结束但不保存", BG, false);
        panel.addView(discard, new LinearLayout.LayoutParams(-1, dp(48)));
        discard.setOnClickListener(v -> { dialog.dismiss(); clear(); finish(); });
        space(panel, 5);
        TextView continueAction = text("继续计时", 14, Color.rgb(105, 120, 119), false);
        continueAction.setGravity(Gravity.CENTER);
        panel.addView(continueAction, new LinearLayout.LayoutParams(-1, dp(42)));
        continueAction.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }
    private void save(int minutes) {
        if (taskId <= 0) { clear(); finish(); return; } // Demo task.
        TaskFeedback feedback = new TaskFeedback();
        feedback.setTaskId(taskId); feedback.setActualDuration(minutes);
        feedback.setActualDifficulty(0); feedback.setCompletionStatus("PARTIAL");
        feedback.setCompletedAt(System.currentTimeMillis());
        RepositoryProvider.getTaskRepository().addFeedback(feedback, new TaskRepository.Callback<Long>() {
            @Override public void onSuccess(Long id) { clear(); finish(); }
            @Override public void onError(Exception error) { new AlertDialog.Builder(FocusTimerActivity.this)
                    .setMessage("保存失败：" + error.getMessage()).setPositiveButton("好的", null).show(); }
        });
    }
    private void clear() { prefs().edit().clear().apply(); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private TextView action(String value, int color, boolean outlined) {
        TextView view = text(value, 16, INK, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(color, 17, outlined));
        return view;
    }
    private GradientDrawable rounded(int color, int radius, boolean outlined) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color); background.setCornerRadius(dp(radius));
        if (outlined) background.setStroke(dp(1), Color.rgb(225, 231, 226));
        return background;
    }
    private final class ProgressRingView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private float progress;
        ProgressRingView() { super(FocusTimerActivity.this); }
        void setProgress(float value) { progress = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (progress <= 0f) return;
            float inset = dp(7);
            bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            ringPaint.setColor(Color.rgb(52, 126, 91));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(5));
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(bounds, -90, progress * 360f, false, ringPaint);
        }
    }
    private void space(LinearLayout page, int dp) { page.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dp))); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

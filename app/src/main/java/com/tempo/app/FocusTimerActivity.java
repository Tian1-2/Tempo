package com.tempo.app;

import android.app.AlertDialog;
import android.content.Intent;
import com.tempo.app.learning.LearningRepository;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.learning.SessionTiming;
import java.util.UUID;
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
    private final int INK = com.tempo.app.ui.Ui.INK;
    private final int BG = com.tempo.app.ui.Ui.BG;
    private final int MINT = com.tempo.app.ui.Ui.MINT;
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
    private LearningRepository learning;
    private boolean preparing;
    private static final int REVIEW_SESSION = 42;
    private final Runnable tick = this::refresh;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        learning = new LearningRepository(this);
        taskId = getIntent().getIntExtra("taskId", Integer.MIN_VALUE);
        title = getIntent().getStringExtra("title");
        targetMinutes = getIntent().getIntExtra("targetMinutes", 0);
        if (taskId == Integer.MIN_VALUE || title == null) { finish(); return; }
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(com.tempo.app.ui.Ui.isDark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(16), 0, dp(20));
        page.setBackgroundColor(BG);
        setContentView(page);
        com.tempo.app.ui.Ui.fitSystemBars(this);
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        BackChevronView back = new BackChevronView(this, "Back");
        back.setOnClickListener(v -> finish());
        navigation.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView backLabel = text("Back", 14, com.tempo.app.ui.Ui.MUTED, false);
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
        TextView kicker = text("FOCUS", 11, com.tempo.app.ui.Ui.GREEN, true);
        kicker.setGravity(Gravity.CENTER);
        kicker.setLetterSpacing(0.14f);
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
        dialBackground.setColor(com.tempo.app.ui.Ui.CARD);
        dialBackground.setStroke(dp(1), com.tempo.app.ui.Ui.LINE);
        dial.setBackground(dialBackground);
        progressRing = new ProgressRingView();
        dial.addView(progressRing, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout digits = new LinearLayout(this);
        digits.setOrientation(LinearLayout.VERTICAL);
        digits.setGravity(Gravity.CENTER);
        status = text("Ready", 13, com.tempo.app.ui.Ui.GREEN, true);
        status.setGravity(Gravity.CENTER);
        digits.addView(status);
        space(digits, 12);
        clock = text("00:00:00", 39, INK, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        digits.addView(clock);
        space(digits, 12);
        TextView elapsedLabel = text("Focused", 12, com.tempo.app.ui.Ui.MUTED, false);
        elapsedLabel.setGravity(Gravity.CENTER);
        digits.addView(elapsedLabel);
        space(digits, 8);
        targetLabel = text("Loading target…", 12, com.tempo.app.ui.Ui.MUTED, false);
        targetLabel.setGravity(Gravity.CENTER);
        digits.addView(targetLabel);
        dial.addView(digits, new FrameLayout.LayoutParams(-1, -1));
        center.addView(dial, new LinearLayout.LayoutParams(dp(264), dp(264)));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(22), 0, dp(22), 0);
        TextView note = text("The timer keeps running if you leave this page", 12, com.tempo.app.ui.Ui.MUTED, false);
        note.setGravity(Gravity.CENTER);
        footer.addView(note);
        space(footer, 18);
        toggle = action("Start focus", com.tempo.app.ui.Ui.ACTION, false);
        toggle.setTextColor(Color.WHITE); // app-wide primary action: dark button, white text
        toggle.setOnClickListener(v -> {
            if (FocusSessionState.isFromEarlierDay(prefs(), System.currentTimeMillis())) {
                reviewPreviousDaySession();
                return;
            }
            if (isRunning()) pause(); else start();
            refresh();
        });
        footer.addView(toggle, new LinearLayout.LayoutParams(-1, dp(56)));
        space(footer, 10);
        finishAction = action("Finish and review", Color.WHITE, true);
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
        handler.post(this::reviewPreviousDaySession);
    }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (learning != null) learning.close(); super.onDestroy(); }
    private SharedPreferences prefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }
    private boolean isRunning() { return prefs().getInt("taskId", Integer.MIN_VALUE) == taskId && prefs().getLong("started", 0) > 0; }
    private long elapsed() {
        if (prefs().getInt("taskId", Integer.MIN_VALUE) != taskId) return 0;
        long started = prefs().getLong("started", 0);
        return prefs().getLong("elapsed", 0) + (started > 0 ? Math.max(0, SystemClock.elapsedRealtime() - started) : 0);
    }
    private void start() {
        if (preparing) return;
        int active = prefs().getInt("taskId", Integer.MIN_VALUE);
        if (active != Integer.MIN_VALUE && active != taskId) {
            new AlertDialog.Builder(this).setMessage("Finish the timer that is running for another task first.")
                    .setPositiveButton("OK", null).show(); return;
        }
        if (taskId <= 0) { beginClock(); return; }
        preparing = true; refresh();
        learning.prepare(taskId, new TaskRepository.Callback<LearningStore.Prepared>() {
            @Override public void onSuccess(LearningStore.Prepared result) {
                preparing = false;
                targetMinutes = result.prediction; previousFocusSeconds = result.previousMinutes * 60;
                beginClock(); refresh();
            }
            @Override public void onError(Exception e) {
                preparing = false; refresh();
                new AlertDialog.Builder(FocusTimerActivity.this).setMessage(e.getMessage()).setPositiveButton("OK", null).show();
            }
        });
    }
    private void beginClock() {
        SharedPreferences.Editor editor = prefs().edit().putInt("taskId", taskId).putLong("started", SystemClock.elapsedRealtime())
                .putLong("startedWall",System.currentTimeMillis());
        if (!prefs().contains("sessionStartWall")) editor.putLong("sessionStartWall", System.currentTimeMillis());
        if(!prefs().contains("timingTrusted")) editor.putBoolean("timingTrusted",prefs().getLong("elapsed",0)==0);
        if (prefs().getString("sessionToken", null) == null) editor.putString("sessionToken", UUID.randomUUID().toString());
        editor.commit();
    }
    private void pause() {
        long started=prefs().getLong("started",0), wall=prefs().getLong("startedWall",0);
        long monotonic=SystemClock.elapsedRealtime(), elapsedAtPause=prefs().getLong("elapsed",0)+(started>0?Math.max(0,monotonic-started):0);
        SharedPreferences.Editor edit=prefs().edit();
        if(started>0) {
            long end=System.currentTimeMillis();
            if(SessionTiming.clocksAgree(wall,started,end,monotonic) && end>wall) {
                String segments=prefs().getString("segments","");
                edit.putString("segments",segments+(segments.isEmpty()?"":";")+wall+","+(wall+monotonic-started));
            } else edit.putBoolean("timingTrusted",false);
        }
        edit.putLong("elapsed",elapsedAtPause).putLong("started",0).putLong("startedWall",0).commit();
    }
    private void reviewPreviousDaySession() {
        SharedPreferences state = prefs();
        if (!FocusSessionState.isFromEarlierDay(state, System.currentTimeMillis()) || isFinishing()) return;
        if (FocusSessionState.isRunning(state)) pause();
        int previousTask = state.getInt("taskId", Integer.MIN_VALUE);
        if (previousTask != taskId) {
            new AlertDialog.Builder(this).setTitle("Previous focus session")
                    .setMessage("A session from an earlier day is still open. Review or discard it before starting another timer. You can still add and plan tasks.")
                    .setPositiveButton("Open previous timer", (dialog, which) ->
                            RepositoryProvider.getTaskRepository().getTaskById(previousTask,
                                    new TaskRepository.Callback<com.tempo.app.model.Task>() {
                                        @Override public void onSuccess(com.tempo.app.model.Task task) {
                                            if (task == null) return;
                                            Intent intent = new Intent(FocusTimerActivity.this, FocusTimerActivity.class);
                                            intent.putExtra("taskId", task.getTaskId());
                                            intent.putExtra("title", task.getTitle());
                                            startActivity(intent);
                                            finish();
                                        }
                                        @Override public void onError(Exception error) { }
                                    }))
                    .setNegativeButton("Later", (dialog, which) -> finish()).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Previous focus session")
                .setMessage("This timer was started on an earlier day. Review its recorded time before saving, or discard the session. It will not resume automatically.")
                .setPositiveButton("Finish and review", (dialog, which) -> finishSession())
                .setNeutralButton("Discard session", (dialog, which) -> {
                    clear();
                    com.tempo.app.scheduler.AutoScheduler.replan(this, null);
                    finish();
                })
                .setNegativeButton("Later", (dialog, which) -> finish()).show();
    }
    private void refresh() {
        long savedStart=prefs().getLong("started",0),savedWall=prefs().getLong("startedWall",0);
        if(prefs().getInt("taskId",Integer.MIN_VALUE)==taskId && savedStart>0 && savedWall>0
                && !SessionTiming.clocksAgree(savedWall,savedStart,System.currentTimeMillis(),SystemClock.elapsedRealtime())) {
            // Reboot or a wall-clock jump: stop the uncertain segment; retain already-paused elapsed time.
            prefs().edit().putLong("started",0).putLong("startedWall",0).putBoolean("timingTrusted",false).commit();
            android.widget.Toast.makeText(this,"Timer paused after a clock change. Review the recorded minutes before saving.",android.widget.Toast.LENGTH_LONG).show();
        }
        long seconds = elapsed() / 1000;
        clock.setText(String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
        boolean running = isRunning();
        status.setText(running ? "Focusing" : seconds > 0 ? "Paused" : "Ready");
        if (targetMinutes > 0) {
            long targetSeconds = targetMinutes * 60L;
            long totalSeconds = previousFocusSeconds + seconds;
            progressRing.setProgress(Math.min(1f, totalSeconds / (float) targetSeconds));
            targetLabel.setText(totalSeconds > targetSeconds
                    ? ((totalSeconds - targetSeconds + 59) / 60) + " min over the expected time"
                    : (totalSeconds / 60) + " of " + targetMinutes + " min expected");
        } else {
            progressRing.setProgress(0f);
            targetLabel.setText("No expected time");
        }
        toggle.setEnabled(!preparing);
        finishAction.setEnabled(!preparing);
        toggle.setText(running ? "Pause" : seconds > 0 ? "Resume" : "Start focus");
        finishAction.setAlpha(seconds > 0 ? 1f : 0.48f);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
    }
    private void finishSession() {
        if (preparing) return;
        pause(); refresh();
        if (taskId <= 0) {
            new AlertDialog.Builder(this).setMessage("This is a sample task, so nothing will be saved. Use a real task to record time.")
                    .setPositiveButton("End", (d, w) -> { clear(); finish(); }).setNegativeButton("Back", null).show();
            return;
        }
        String token = prefs().getString("sessionToken", null);
        if (token == null) {
            token = UUID.randomUUID().toString();
            prefs().edit().putString("sessionToken", token).commit();
        }
        int minutes = (int) Math.min(Integer.MAX_VALUE, Math.max(1, (elapsed() + 59_999) / 60_000));
        Intent intent = new Intent(this, TaskFeedbackActivity.class);
        intent.putExtra("taskId", taskId); intent.putExtra("title", title);
        intent.putExtra("sessionToken", token); intent.putExtra("minutes", minutes);
        intent.putExtra("previousMinutes", previousFocusSeconds / 60);
        intent.putExtra("segments",prefs().getString("segments",""));
        intent.putExtra("timingTrusted",prefs().getBoolean("timingTrusted",false));
        startActivityForResult(intent, REVIEW_SESSION);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REVIEW_SESSION && resultCode == RESULT_OK) finish();
        else refresh();
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
        if (outlined) background.setStroke(dp(1), com.tempo.app.ui.Ui.LINE);
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
            ringPaint.setColor(com.tempo.app.ui.Ui.GREEN);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(5));
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(bounds, -90, progress * 360f, false, ringPaint);
        }
    }
    private void space(LinearLayout page, int dp) { page.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dp))); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

package com.tempo.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tempo.app.home.DailyReview;
import com.tempo.app.home.MorningReview;
import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;
import com.tempo.app.model.UserProfile;
import com.tempo.app.repository.RepositoryProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launcher page. The UI is a local HTML page (assets/home/index.html) rendered in a WebView; this
 * activity loads the data on a background thread, hands it to the page as JSON, and performs the
 * navigation the page asks for through the "Tempo" JavaScript bridge.
 *
 * Between 07:00 and 09:00 the first visit of the day opens on the morning review of yesterday
 * (see {@link MorningReview}). This is checked on every resume, not only when the activity is
 * created, because Home usually stays alive in the background overnight.
 */
public class HomeActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(247, 248, 245);
    private static final String PREFS = "tempo_home";
    private static final String KEY_GREETED = "greetedDate";
    private static final String PAGE = "file:///android_asset/home/index.html";
    private static final long MINUTE = 60_000L;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private boolean pageReady;
    /** Latest data for the page; read by the bridge on its own thread. */
    private volatile String json;

    /** One task's slots for today, merged for display. */
    private static final class Row {
        Task task;
        long start;
        long nextStart = Long.MAX_VALUE; // earliest slot that has not finished yet
        long nextEnd;
        int minutes;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        web.setVerticalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);   // android_asset still loads; nothing else on disk does
        settings.setAllowContentAccess(false);
        web.addJavascriptInterface(new Bridge(), "Tempo");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // the page never navigates anywhere
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                push();
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // Keep the page clear of the status and navigation bars (edge-to-edge on recent Android).
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        web.loadUrl(PAGE);
        com.tempo.app.reminders.Reminders.refresh(this);
        askForNotificationsOnce();
    }

    /** Reminders need the notification permission on Android 13+; ask once, on first launch. */
    private void askForNotificationsOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33 || com.tempo.app.reminders.Reminders.canNotify(this)) return;
        android.content.SharedPreferences prefs = com.tempo.app.reminders.Reminders.prefs(this);
        if (prefs.getBoolean("askedPermission", false)) return;
        prefs.edit().putBoolean("askedPermission", true).apply();
        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 41);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("Tempo");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** Called by the page through window.Tempo. Methods run on a WebView thread. */
    private final class Bridge {
        @JavascriptInterface public String getData() { return json; }

        @JavascriptInterface public void openSchedule() {
            main.post(() -> startActivity(new Intent(HomeActivity.this, SchedulePreviewActivity.class)));
        }

        @JavascriptInterface public void openProfile() {
            main.post(() -> startActivity(new Intent(HomeActivity.this, ProfileSettingsActivity.class)));
        }

        @JavascriptInterface public void openInsights() {
            main.post(() -> startActivity(new Intent(HomeActivity.this, InsightsActivity.class)));
        }

        @JavascriptInterface public void openHistory() {
            main.post(() -> startActivity(new Intent(HomeActivity.this, SessionHistoryActivity.class)));
        }

        @JavascriptInterface public void openAdd() {
            main.post(() -> {
                Intent intent = new Intent(HomeActivity.this, AddTaskActivity.class);
                intent.putExtra("selectedDayMillis", startOfDay(System.currentTimeMillis()));
                startActivity(intent);
            });
        }

        @JavascriptInterface public void startFocus(int taskId) {
            main.post(() -> openTimer(taskId));
        }

        @JavascriptInterface public void finishGreeting() {
            main.post(() -> markGreeted(System.currentTimeMillis()));
        }
    }

    private void push() {
        if (pageReady && json != null && web != null) {
            web.evaluateJavascript("window.tempoRender && window.tempoRender()", null);
        }
    }

    private void load() {
        long now = System.currentTimeMillis();
        boolean greet = shouldGreet(now);
        LOADER.execute(() -> {
            String result;
            try {
                result = buildJson(now, greet).toString();
            } catch (Exception e) {
                result = null;
            }
            String data = result;
            main.post(() -> {
                if (isFinishing() || data == null) return;
                json = data;
                // The review has now reached the page: don't pop it up again today.
                if (greet) markGreeted(now);
                push();
            });
        });
    }

    /** Runs on the loader thread. */
    private JSONObject buildJson(long now, boolean greet) throws JSONException {
        UserProfile profile = RepositoryProvider.getUserProfileRepository().getProfileSync();
        List<Task> tasks = RepositoryProvider.getTaskRepository().getAllTasksSync();
        List<TaskFeedback> feedback = RepositoryProvider.getTaskRepository().getAllFeedbackSync();
        long todayStart = startOfDay(now);
        long yesterdayStart = addDays(todayStart, -1);
        long tomorrowStart = addDays(todayStart, 1);
        List<ScheduleItem> todaySlots = RepositoryProvider.getScheduleRepository().getScheduleByDateSync(dateKey(todayStart));
        List<ScheduleItem> yesterdaySlots = RepositoryProvider.getScheduleRepository().getScheduleByDateSync(dateKey(yesterdayStart));
        DailyReview today = DailyReview.compute(todayStart, tomorrowStart, todaySlots, tasks, feedback);
        DailyReview yesterday = DailyReview.compute(yesterdayStart, todayStart, yesterdaySlots, tasks, feedback);
        List<Row> rows = buildRows(todaySlots, tasks, now);

        JSONObject d = new JSONObject();
        d.put("greeting", greeting(now));
        d.put("dayName", format("EEEE", now));
        d.put("dateText", format("MMMM d", now));
        d.put("longDate", format("EEEE, MMMM d", now));
        d.put("showReview", greet);
        d.put("dayKey", dateKey(todayStart));
        d.put("loadId", now);
        d.put("hasProfile", profile != null && profile.getWakeTime() != null
                && profile.getSleepTime() != null && profile.getFocusDuration() > 0);

        JSONObject t = new JSONObject();
        t.put("planned", today.plannedTasks);
        t.put("completed", today.completedTasks);
        t.put("focusText", DailyReview.formatMinutes(today.focusMinutes));
        t.put("plannedText", DailyReview.formatMinutes(today.plannedMinutes));
        d.put("today", t);

        Row next = null;
        for (Row row : rows) {
            if (isDone(row.task) || row.nextStart == Long.MAX_VALUE) continue;
            if (next == null || row.nextStart < next.nextStart) next = row;
        }
        if (next != null) {
            JSONObject n = new JSONObject();
            n.put("taskId", next.task.getTaskId());
            n.put("title", next.task.getTitle());
            n.put("category", category(next.task.getCategory()));
            n.put("color", com.tempo.app.model.Category.hex(com.tempo.app.ui.Categories.color(next.task.getCategory())));
            n.put("timeText", time(next.nextStart) + " – " + time(next.nextEnd));
            n.put("now", next.nextStart <= now);
            long minutes = (next.nextStart - now + MINUTE - 1) / MINUTE;
            n.put("label", minutes <= 120 ? "in " + DailyReview.formatMinutes((int) minutes) : "at " + time(next.nextStart));
            d.put("next", n);
        }

        JSONArray list = new JSONArray();
        for (Row row : rows) {
            JSONObject r = new JSONObject();
            r.put("taskId", row.task.getTaskId());
            r.put("title", row.task.getTitle());
            r.put("category", category(row.task.getCategory()));
            r.put("color", com.tempo.app.model.Category.hex(com.tempo.app.ui.Categories.color(row.task.getCategory())));
            r.put("startText", time(row.start));
            r.put("minutes", DailyReview.formatMinutes(row.minutes));
            r.put("done", isDone(row.task));
            r.put("current", row == next);
            list.put(r);
        }
        d.put("rows", list);

        JSONObject y = new JSONObject();
        y.put("empty", yesterday.isEmpty());
        y.put("planned", yesterday.plannedTasks);
        y.put("completed", yesterday.completedTasks);
        y.put("focusText", DailyReview.formatMinutes(yesterday.focusMinutes));
        y.put("encouragement", yesterday.encouragement());
        y.put("overrun", yesterday.overrun != null);
        putOrNull(y, "reminder", yesterday.reminder());
        putOrNull(y, "score", yesterday.score);
        putOrNull(y, "scoreLabel", yesterday.score == null ? null : DailyReview.scoreLabel(yesterday.score));
        putOrNull(y, "completionPct", yesterday.completionPct);
        putOrNull(y, "focusPct", yesterday.focusPct);
        putOrNull(y, "accuracyPct", yesterday.accuracyPct);
        d.put("yesterday", y);
        return d;
    }

    private static void putOrNull(JSONObject o, String key, Object value) throws JSONException {
        o.put(key, value == null ? JSONObject.NULL : value);
    }

    private static boolean isDone(Task task) { return "COMPLETED".equals(task.getStatus()); }

    private static String category(String value) {
        return com.tempo.app.ui.Categories.name(value);
    }

    private static List<Row> buildRows(List<ScheduleItem> slots, List<Task> tasks, long now) {
        Map<Integer, Task> byId = new HashMap<>();
        if (tasks != null) for (Task t : tasks) byId.put(t.getTaskId(), t);
        Map<Integer, Row> byTask = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        if (slots != null) for (ScheduleItem item : slots) {
            Task task = byId.get(item.getTaskId());
            if (task == null) continue;
            Row row = byTask.get(task.getTaskId());
            if (row == null) {
                row = new Row();
                row.task = task;
                row.start = item.getStartTime();
                byTask.put(task.getTaskId(), row);
                rows.add(row);
            }
            row.start = Math.min(row.start, item.getStartTime());
            row.minutes += (int) Math.max(0, (item.getEndTime() - item.getStartTime()) / MINUTE);
            if (item.getEndTime() > now && item.getStartTime() < row.nextStart) {
                row.nextStart = item.getStartTime();
                row.nextEnd = item.getEndTime();
            }
        }
        Collections.sort(rows, (a, b) -> Long.compare(a.start, b.start));
        return rows;
    }

    private void openTimer(int taskId) {
        LOADER.execute(() -> {
            Task task = RepositoryProvider.getTaskRepository().getTaskByIdSync(taskId);
            if (task == null) return;
            main.post(() -> {
                Intent intent = new Intent(this, FocusTimerActivity.class);
                intent.putExtra("taskId", task.getTaskId());
                intent.putExtra("title", task.getTitle());
                intent.putExtra("targetMinutes", task.getPredictedDuration() > 0
                        ? task.getPredictedDuration() : task.getEstimatedDuration());
                startActivity(intent);
            });
        });
    }

    private boolean shouldGreet(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        return MorningReview.due(c.get(Calendar.HOUR_OF_DAY), dateKey(startOfDay(now)),
                prefs().getString(KEY_GREETED, ""));
    }

    private void markGreeted(long now) {
        prefs().edit().putString(KEY_GREETED, dateKey(startOfDay(now))).apply();
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private static String greeting(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }

    private static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long addDays(long dayStart, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    private static String format(String pattern, long millis) {
        return new SimpleDateFormat(pattern, Locale.US).format(new Date(millis));
    }

    private static String dateKey(long millis) { return format("yyyy-MM-dd", millis); }

    private static String time(long millis) { return format("HH:mm", millis); }
}

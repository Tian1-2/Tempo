package com.tempo.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tempo.app.home.DailyReview;
import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;
import com.tempo.app.model.UserProfile;
import com.tempo.app.repository.RepositoryProvider;

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
 * Launcher page: today's overview. Between 07:00 and 09:00 the first visit of the day starts with a
 * morning greeting and a review of yesterday (encouragement, score, then a reminder).
 */
public class HomeActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(247, 248, 245);
    private static final int INK = Color.rgb(30, 48, 52);
    private static final int MUTED = Color.rgb(105, 120, 119);
    private static final int MINT = Color.rgb(194, 232, 213);
    private static final int MINT_SOFT = Color.rgb(232, 245, 238);
    private static final int GREEN = Color.rgb(52, 126, 91);
    private static final int BORDER = Color.rgb(226, 232, 227);
    private static final int MORNING_START_HOUR = 7;
    private static final int MORNING_END_HOUR = 9;
    private static final String PREFS = "tempo_home";
    private static final String KEY_GREETED = "greetedDate";
    private static final long MINUTE = 60_000L;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout page;
    private Snapshot snapshot;
    private boolean showingReview;

    /** One task's slots for today, merged for display. */
    private static final class Row {
        Task task;
        long start;
        long nextEnd; // end of the earliest slot that has not finished yet
        long nextStart;
        int minutes;
    }

    /** Everything the page needs, loaded in one background pass. */
    private static final class Snapshot {
        boolean hasProfile;
        DailyReview yesterday;
        DailyReview today;
        final List<Row> rows = new ArrayList<>();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        setContentView(page);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        long now = System.currentTimeMillis();
        LOADER.execute(() -> {
            Snapshot s = new Snapshot();
            try {
                s.hasProfile = isUsable(RepositoryProvider.getUserProfileRepository().getProfileSync());
                List<Task> tasks = RepositoryProvider.getTaskRepository().getAllTasksSync();
                List<TaskFeedback> feedback = RepositoryProvider.getTaskRepository().getAllFeedbackSync();
                long todayStart = startOfDay(now);
                long yesterdayStart = addDays(todayStart, -1);
                long tomorrowStart = addDays(todayStart, 1);
                List<ScheduleItem> todaySlots = RepositoryProvider.getScheduleRepository().getScheduleByDateSync(dateKey(todayStart));
                List<ScheduleItem> yesterdaySlots = RepositoryProvider.getScheduleRepository().getScheduleByDateSync(dateKey(yesterdayStart));
                s.today = DailyReview.compute(todayStart, tomorrowStart, todaySlots, tasks, feedback);
                s.yesterday = DailyReview.compute(yesterdayStart, todayStart, yesterdaySlots, tasks, feedback);
                buildRows(s.rows, todaySlots, tasks, now);
            } catch (Exception e) {
                s.today = null; // render() shows an error line
            }
            main.post(() -> {
                if (isFinishing()) return;
                snapshot = s;
                showingReview = showingReview || shouldGreet(now);
                render();
            });
        });
    }

    private static boolean isUsable(UserProfile p) {
        return p != null && p.getWakeTime() != null && p.getSleepTime() != null && p.getFocusDuration() > 0;
    }

    private static void buildRows(List<Row> rows, List<ScheduleItem> slots, List<Task> tasks, long now) {
        Map<Integer, Task> byId = new HashMap<>();
        if (tasks != null) for (Task t : tasks) byId.put(t.getTaskId(), t);
        Map<Integer, Row> byTask = new HashMap<>();
        if (slots != null) for (ScheduleItem item : slots) {
            Task task = byId.get(item.getTaskId());
            if (task == null) continue;
            Row row = byTask.get(task.getTaskId());
            if (row == null) {
                row = new Row();
                row.task = task;
                row.start = item.getStartTime();
                row.nextStart = Long.MAX_VALUE;
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
    }

    private boolean shouldGreet(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        if (hour < MORNING_START_HOUR || hour >= MORNING_END_HOUR) return false;
        return !dateKey(startOfDay(now)).equals(prefs().getString(KEY_GREETED, ""));
    }

    private void finishGreeting() {
        prefs().edit().putString(KEY_GREETED, dateKey(startOfDay(System.currentTimeMillis()))).apply();
        showingReview = false;
        render();
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    // ---------------------------------------------------------------- rendering

    private void render() {
        page.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(28), dp(24), dp(24));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (showingReview) renderReview(body);
        else {
            renderOverview(body);
            page.addView(bottomBar());
        }
    }

    private void renderReview(LinearLayout body) {
        body.addView(text(greeting(System.currentTimeMillis()), 30, INK, true));
        space(body, 6);
        body.addView(text(longDate(System.currentTimeMillis()), 15, MUTED, false));
        space(body, 28);
        DailyReview r = snapshot.yesterday;
        if (r == null || r.isEmpty()) {
            LinearLayout card = card(Color.WHITE);
            card.addView(text("A brand new day. You've got this!", 18, INK, true));
            space(card, 8);
            card.addView(text("Nothing was tracked yesterday. Add a task and let Tempo plan it for you.", 14, MUTED, false));
            body.addView(card);
        } else {
            body.addView(text("YESTERDAY", 12, GREEN, true));
            space(body, 10);
            LinearLayout card = card(Color.WHITE);
            card.addView(text(r.encouragement(), 18, INK, true));
            space(card, 14);
            LinearLayout stats = new LinearLayout(this);
            stats.addView(stat("Tasks done", r.completedTasks + " / " + r.plannedTasks), new LinearLayout.LayoutParams(0, -2, 1));
            stats.addView(stat("Focused", DailyReview.formatMinutes(r.focusMinutes)), new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(stats);
            if (r.score != null) {
                space(card, 16);
                rule(card);
                space(card, 16);
                LinearLayout scoreRow = new LinearLayout(this);
                scoreRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView score = text(String.valueOf(r.score), 40, GREEN, true);
                scoreRow.addView(score);
                LinearLayout scoreText = new LinearLayout(this);
                scoreText.setOrientation(LinearLayout.VERTICAL);
                scoreText.setPadding(dp(14), 0, 0, 0);
                scoreText.addView(text("Daily score", 12, MUTED, false));
                scoreText.addView(text(DailyReview.scoreLabel(r.score), 17, INK, true));
                scoreRow.addView(scoreText);
                card.addView(scoreRow);
                space(card, 6);
                card.addView(text("Completion 50% · Focus time 30% · Estimate accuracy 20%", 11, MUTED, false));
            }
            body.addView(card);
            String reminder = r.reminder();
            if (reminder != null) {
                space(body, 14);
                LinearLayout tip = card(MINT_SOFT);
                tip.addView(text("💡 " + reminder, 14, INK, false));
                body.addView(tip);
            }
        }
        space(body, 28);
        TextView start = button("Start my day", MINT);
        start.setOnClickListener(v -> finishGreeting());
        body.addView(start, new LinearLayout.LayoutParams(-1, dp(54)));
    }

    private void renderOverview(LinearLayout body) {
        long now = System.currentTimeMillis();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(greeting(now), 14, MUTED, false));
        titles.addView(text("Today", 30, INK, true));
        titles.addView(text(longDate(now), 14, MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView review = text("Yesterday ›", 14, GREEN, true);
        review.setPadding(dp(8), dp(8), 0, dp(8));
        review.setOnClickListener(v -> { showingReview = true; render(); });
        header.addView(review);
        body.addView(header);
        space(body, 20);

        if (snapshot == null) { body.addView(text("Loading…", 14, MUTED, false)); return; }
        if (snapshot.today == null) { body.addView(text("Could not read today's data.", 14, MUTED, false)); return; }

        if (!snapshot.hasProfile) {
            LinearLayout setup = card(MINT_SOFT);
            setup.addView(text("Set up your profile", 16, INK, true));
            space(setup, 4);
            setup.addView(text("Tell Tempo when you wake, sleep and eat so it can plan your day.", 13, MUTED, false));
            setup.setOnClickListener(v -> startActivity(new Intent(this, ProfileSettingsActivity.class)));
            body.addView(setup);
            space(body, 14);
        }

        LinearLayout stats = card(Color.WHITE);
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.addView(stat("Done", snapshot.today.completedTasks + " / " + snapshot.today.plannedTasks), new LinearLayout.LayoutParams(0, -2, 1));
        statsRow.addView(stat("Focused today", DailyReview.formatMinutes(snapshot.today.focusMinutes)), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statsRow);
        body.addView(stats);
        space(body, 14);

        Row next = null;
        for (Row row : snapshot.rows) {
            if ("COMPLETED".equals(row.task.getStatus()) || row.nextStart == Long.MAX_VALUE) continue;
            if (next == null || row.nextStart < next.nextStart) next = row;
        }
        if (next != null) {
            LinearLayout card = card(Color.WHITE);
            card.addView(text(next.nextStart <= now ? "NOW" : "NEXT UP", 12, GREEN, true));
            space(card, 6);
            card.addView(text(next.task.getTitle(), 19, INK, true));
            space(card, 4);
            card.addView(text(time(next.nextStart) + " – " + time(next.nextEnd), 14, MUTED, false));
            space(card, 14);
            TextView focus = button("Start focus", MINT);
            final Task task = next.task;
            focus.setOnClickListener(v -> openTimer(task));
            card.addView(focus, new LinearLayout.LayoutParams(-1, dp(46)));
            body.addView(card);
            space(body, 20);
        }

        body.addView(text("TODAY'S PLAN", 12, GREEN, true));
        space(body, 10);
        if (snapshot.rows.isEmpty()) {
            body.addView(text("Nothing planned yet. Tap “+ Add” to create a task and Tempo will fit it into your day.", 14, MUTED, false));
        } else {
            LinearLayout list = card(Color.WHITE);
            for (int i = 0; i < snapshot.rows.size(); i++) {
                if (i > 0) { space(list, 10); rule(list); space(list, 10); }
                list.addView(planRow(snapshot.rows.get(i)));
            }
            body.addView(list);
        }
    }

    private View planRow(Row row) {
        boolean done = "COMPLETED".equals(row.task.getStatus());
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text(time(row.start), 14, MUTED, true), new LinearLayout.LayoutParams(dp(58), -2));
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.addView(text(row.task.getTitle(), 15, done ? MUTED : INK, true));
        middle.addView(text(DailyReview.formatMinutes(row.minutes), 12, MUTED, false));
        line.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));
        if (done) line.addView(text("✓", 18, GREEN, true));
        return line;
    }

    private View bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(16), dp(6), dp(16), dp(14));
        bar.setBackgroundColor(BG);
        TextView schedule = barAction("Schedule");
        schedule.setOnClickListener(v -> startActivity(new Intent(this, SchedulePreviewActivity.class)));
        TextView add = barAction("＋ Add");
        add.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddTaskActivity.class);
            intent.putExtra("selectedDayMillis", startOfDay(System.currentTimeMillis()));
            startActivity(intent);
        });
        TextView profile = barAction("Profile");
        profile.setOnClickListener(v -> startActivity(new Intent(this, ProfileSettingsActivity.class)));
        bar.addView(schedule, new LinearLayout.LayoutParams(0, dp(48), 1));
        bar.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1));
        bar.addView(profile, new LinearLayout.LayoutParams(0, dp(48), 1));
        return bar;
    }

    private void openTimer(Task task) {
        Intent intent = new Intent(this, FocusTimerActivity.class);
        intent.putExtra("taskId", task.getTaskId());
        intent.putExtra("title", task.getTitle());
        intent.putExtra("targetMinutes", task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration());
        startActivity(intent);
    }

    // ---------------------------------------------------------------- helpers

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

    private static String dateKey(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }

    private static String longDate(long millis) {
        return new SimpleDateFormat("EEEE, MMM d", Locale.US).format(new Date(millis));
    }

    private static String time(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(millis));
    }

    private LinearLayout stat(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(label, 12, MUTED, false));
        space(box, 2);
        box.addView(text(value, 20, INK, true));
        return box;
    }

    private LinearLayout card(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(18));
        if (color == Color.WHITE) bg.setStroke(dp(1), BORDER);
        card.setBackground(bg);
        return card;
    }

    private TextView button(String label, int color) {
        TextView view = text(label, 16, INK, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(17));
        view.setBackground(bg);
        return view;
    }

    private TextView barAction(String label) {
        TextView view = text(label, 15, INK, true);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void rule(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(BORDER);
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void space(LinearLayout parent, int size) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size)));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

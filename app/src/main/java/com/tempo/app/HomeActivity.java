package com.tempo.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tempo.app.home.DailyReview;
import com.tempo.app.home.RingView;
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
    private static final int WARM = Color.rgb(178, 107, 31);
    private static final int WARM_SOFT = Color.rgb(255, 246, 234);
    private static final int HERO_MUTED = Color.rgb(170, 186, 184);
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
        FrameLayout root = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(30), dp(22), dp(showingReview ? 28 : 110));
        scroll.addView(body);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        page.addView(root, new LinearLayout.LayoutParams(-1, -1));
        if (showingReview) renderReview(body);
        else {
            renderOverview(body);
            FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-1, dp(66), Gravity.BOTTOM);
            barParams.setMargins(dp(18), 0, dp(18), dp(18));
            root.addView(bottomBar(), barParams);
        }
    }

    private void renderReview(LinearLayout body) {
        long now = System.currentTimeMillis();
        body.addView(text(longDate(now), 14, MUTED, false));
        space(body, 2);
        body.addView(text(greeting(now) + " ☀", 32, INK, true));
        space(body, 22);
        DailyReview r = snapshot.yesterday;
        if (r == null || r.isEmpty()) {
            LinearLayout card = card(Color.WHITE, true);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(20), dp(28), dp(20), dp(28));
            TextView title = text("A brand new day. You've got this!", 19, INK, true);
            title.setGravity(Gravity.CENTER);
            card.addView(title);
            space(card, 8);
            TextView hint = text("Nothing was tracked yesterday. Add a task and Tempo will fit it into your day.", 14, MUTED, false);
            hint.setGravity(Gravity.CENTER);
            card.addView(hint);
            body.addView(card);
        } else {
            LinearLayout card = card(Color.WHITE, true);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(18), dp(24), dp(18), dp(22));
            card.addView(eyebrow(r.score != null ? "YESTERDAY'S SCORE" : "YESTERDAY", GREEN));
            if (r.score != null) {
                space(card, 12);
                RingView ring = new RingView(this, 12, 40, 13, MINT_SOFT, GREEN, INK);
                ring.set(r.score / 100f, String.valueOf(r.score), DailyReview.scoreLabel(r.score));
                card.addView(ring, new LinearLayout.LayoutParams(dp(150), dp(150)));
            }
            space(card, 14);
            TextView cheer = text(r.encouragement(), 16, INK, true);
            cheer.setGravity(Gravity.CENTER);
            card.addView(cheer);
            space(card, 14);
            LinearLayout chips = new LinearLayout(this);
            chips.setGravity(Gravity.CENTER);
            chips.addView(chip("✓ " + r.completedTasks + " / " + r.plannedTasks + " done", MINT_SOFT, GREEN));
            View gap = new View(this);
            chips.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
            chips.addView(chip("◷ " + DailyReview.formatMinutes(r.focusMinutes), MINT_SOFT, GREEN));
            card.addView(chips);
            if (r.score != null) {
                space(card, 12);
                TextView weights = text("Completion 50% · Focus 30% · Estimates 20%", 11, MUTED, false);
                weights.setGravity(Gravity.CENTER);
                card.addView(weights);
            }
            body.addView(card);
            String reminder = r.reminder();
            if (reminder != null) {
                space(body, 14);
                boolean warning = r.overrun != null;
                LinearLayout tip = card(warning ? WARM_SOFT : MINT_SOFT, false);
                tip.addView(eyebrow(warning ? "HEADS-UP" : "NICE", warning ? WARM : GREEN));
                space(tip, 6);
                TextView tipText = text(reminder, 14, INK, false);
                tipText.setLineSpacing(0, 1.25f);
                tip.addView(tipText);
                body.addView(tip);
            }
        }
        space(body, 26);
        TextView start = button("Start my day  →", INK, Color.WHITE);
        start.setOnClickListener(v -> finishGreeting());
        body.addView(start, new LinearLayout.LayoutParams(-1, dp(56)));
    }

    private void renderOverview(LinearLayout body) {
        long now = System.currentTimeMillis();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.TOP);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(greeting(now), 13, MUTED, false));
        titles.addView(text(new SimpleDateFormat("EEEE", Locale.US).format(new Date(now)), 32, INK, true));
        titles.addView(text(new SimpleDateFormat("MMMM d", Locale.US).format(new Date(now)), 14, MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Integer yesterdayScore = snapshot == null || snapshot.yesterday == null ? null : snapshot.yesterday.score;
        TextView review = chip(yesterdayScore == null ? "Yesterday ›" : "Yesterday " + yesterdayScore + " ›", MINT_SOFT, GREEN);
        review.setOnClickListener(v -> { showingReview = true; render(); });
        LinearLayout.LayoutParams reviewParams = new LinearLayout.LayoutParams(-2, -2);
        reviewParams.topMargin = dp(18);
        header.addView(review, reviewParams);
        body.addView(header);
        space(body, 20);

        if (snapshot == null) { body.addView(text("Loading…", 14, MUTED, false)); return; }
        if (snapshot.today == null) { body.addView(text("Could not read today's data.", 14, MUTED, false)); return; }

        if (!snapshot.hasProfile) {
            LinearLayout setup = card(MINT_SOFT, false);
            setup.addView(eyebrow("GET STARTED", GREEN));
            space(setup, 6);
            setup.addView(text("Set up your profile  →", 16, INK, true));
            space(setup, 4);
            setup.addView(text("Tell Tempo when you wake, sleep and eat so it can plan your day.", 13, MUTED, false));
            setup.setOnClickListener(v -> startActivity(new Intent(this, ProfileSettingsActivity.class)));
            body.addView(setup);
            space(body, 14);
        }

        DailyReview today = snapshot.today;
        LinearLayout summary = card(Color.WHITE, true);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        RingView ring = new RingView(this, 8, 16, 0, MINT_SOFT, GREEN, INK);
        ring.set(today.plannedTasks == 0 ? 0f : today.completedTasks / (float) today.plannedTasks,
                today.completedTasks + "/" + today.plannedTasks, null);
        summary.addView(ring, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout summaryText = new LinearLayout(this);
        summaryText.setOrientation(LinearLayout.VERTICAL);
        summaryText.setPadding(dp(16), 0, 0, 0);
        summaryText.addView(eyebrow("TODAY", GREEN));
        space(summaryText, 4);
        summaryText.addView(text(today.plannedTasks == 0 ? "No tasks planned yet"
                : today.completedTasks + " of " + today.plannedTasks + " tasks done", 17, INK, true));
        space(summaryText, 3);
        summaryText.addView(text(DailyReview.formatMinutes(today.focusMinutes) + " focused · "
                + DailyReview.formatMinutes(today.plannedMinutes) + " planned", 13, MUTED, false));
        summary.addView(summaryText, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(summary);
        space(body, 14);

        Row next = null;
        for (Row row : snapshot.rows) {
            if ("COMPLETED".equals(row.task.getStatus()) || row.nextStart == Long.MAX_VALUE) continue;
            if (next == null || row.nextStart < next.nextStart) next = row;
        }
        if (next != null) {
            LinearLayout hero = card(INK, true);
            hero.setPadding(dp(20), dp(20), dp(20), dp(20));
            hero.addView(eyebrow(nextLabel(next.nextStart, now), MINT));
            space(hero, 8);
            hero.addView(text(next.task.getTitle(), 21, Color.WHITE, true));
            space(hero, 4);
            hero.addView(text(time(next.nextStart) + " – " + time(next.nextEnd) + " · " + categoryName(next.task.getCategory()),
                    13, HERO_MUTED, false));
            space(hero, 16);
            TextView focus = button("▶  Start focus", MINT, INK);
            final Task task = next.task;
            focus.setOnClickListener(v -> openTimer(task));
            hero.addView(focus, new LinearLayout.LayoutParams(-1, dp(46)));
            body.addView(hero);
            space(body, 22);
        } else {
            space(body, 8);
        }

        body.addView(eyebrow("TODAY'S PLAN", GREEN));
        space(body, 12);
        if (snapshot.rows.isEmpty()) {
            LinearLayout empty = card(Color.WHITE, false);
            empty.addView(text("Nothing planned yet", 15, INK, true));
            space(empty, 4);
            empty.addView(text("Tap + to add a task and Tempo will fit it into your day.", 13, MUTED, false));
            body.addView(empty);
        } else {
            for (int i = 0; i < snapshot.rows.size(); i++) {
                Row row = snapshot.rows.get(i);
                body.addView(timelineRow(row, row == next, i == snapshot.rows.size() - 1));
            }
        }
    }

    private View timelineRow(Row row, boolean current, boolean last) {
        boolean done = "COMPLETED".equals(row.task.getStatus());
        LinearLayout line = new LinearLayout(this);
        FrameLayout rail = new FrameLayout(this);
        if (!last) {
            View track = new View(this);
            track.setBackgroundColor(BORDER);
            FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(dp(2), -1, Gravity.CENTER_HORIZONTAL);
            trackParams.topMargin = dp(10);
            rail.addView(track, trackParams);
        }
        View dot = new View(this);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(categoryColor(row.task.getCategory()));
        dotShape.setStroke(dp(current ? 4 : 2), current ? MINT_SOFT : BG);
        dot.setBackground(dotShape);
        int dotSize = dp(current ? 18 : 14);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER_HORIZONTAL);
        dotParams.topMargin = dp(current ? 1 : 3);
        rail.addView(dot, dotParams);
        line.addView(rail, new LinearLayout.LayoutParams(dp(18), -1));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setPadding(dp(14), 0, 0, dp(last ? 0 : 18));
        TextView title = text(row.task.getTitle(), 15, done ? MUTED : INK, true);
        if (done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        middle.addView(title);
        space(middle, 2);
        middle.addView(text(time(row.start) + " · " + DailyReview.formatMinutes(row.minutes)
                + " · " + categoryName(row.task.getCategory()), 12, MUTED, false));
        line.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));
        if (done) line.addView(text("✓", 17, GREEN, true));
        return line;
    }

    private View bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(24));
        bar.setBackground(bg);
        bar.setElevation(dp(8));
        TextView schedule = barAction("Schedule");
        schedule.setOnClickListener(v -> startActivity(new Intent(this, SchedulePreviewActivity.class)));
        TextView add = button("+", INK, Color.WHITE);
        add.setTextSize(26);
        add.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddTaskActivity.class);
            intent.putExtra("selectedDayMillis", startOfDay(System.currentTimeMillis()));
            startActivity(intent);
        });
        TextView profile = barAction("Profile");
        profile.setOnClickListener(v -> startActivity(new Intent(this, ProfileSettingsActivity.class)));
        bar.addView(schedule, new LinearLayout.LayoutParams(0, dp(52), 1));
        bar.addView(add, new LinearLayout.LayoutParams(dp(52), dp(52)));
        bar.addView(profile, new LinearLayout.LayoutParams(0, dp(52), 1));
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

    private static String nextLabel(long start, long now) {
        if (start <= now) return "NOW";
        long minutes = (start - now + MINUTE - 1) / MINUTE;
        if (minutes <= 120) return "NEXT UP · IN " + DailyReview.formatMinutes((int) minutes).toUpperCase(Locale.US);
        return "NEXT UP · " + time(start);
    }

    private static int categoryColor(String category) {
        if ("Work".equals(category)) return Color.rgb(76, 111, 181);
        if ("Life".equals(category)) return Color.rgb(208, 138, 62);
        return Color.rgb(63, 143, 107);
    }

    private static String categoryName(String category) {
        if ("Work".equals(category)) return "Work";
        if ("Life".equals(category)) return "Life";
        return "Study";
    }

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
        return new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date(millis));
    }

    private static String time(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(millis));
    }

    private TextView eyebrow(String value, int color) {
        TextView view = text(value, 11, color, true);
        view.setLetterSpacing(0.14f);
        return view;
    }

    private TextView chip(String value, int background, int color) {
        TextView view = text(value, 12, color, true);
        view.setSingleLine(true);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadius(dp(99));
        view.setBackground(bg);
        return view;
    }

    /** Rounded card; raised cards get a soft shadow instead of a border. */
    private LinearLayout card(int color, boolean raised) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        if (raised) card.setElevation(dp(2));
        return card;
    }

    private TextView button(String label, int background, int color) {
        TextView view = text(label, 16, color, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadius(dp(16));
        view.setBackground(bg);
        return view;
    }

    private TextView barAction(String label) {
        TextView view = text(label, 13, MUTED, true);
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

    private void space(LinearLayout parent, int size) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size)));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

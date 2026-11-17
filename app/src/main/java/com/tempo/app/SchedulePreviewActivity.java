package com.tempo.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import androidx.appcompat.app.AppCompatActivity;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.scheduler.AutoScheduler;
import com.tempo.app.scheduler.ScheduleDisplayBlocks;
import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.planning.ScheduleEditRules;
import com.tempo.app.ui.TimelineScale;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.text.ParseException;
import java.util.Collections;

/** Visual prototype for Home + a functional, resumable focus timer. */
public class SchedulePreviewActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(247, 248, 245);
    private static final int INK = Color.rgb(30, 48, 52);
    private static final int MUTED = Color.rgb(105, 120, 119);
    private static final int MINT = Color.rgb(194, 232, 213);
    private static final int WHITE = Color.WHITE;
    private final List<Task> tasks = new ArrayList<>();
    private final List<ScheduleItem> schedule = new ArrayList<>();
    private boolean includeBreaks = true;
    private long selectedDayMillis;
    private boolean demoData;
    private int focusBreakMinutes = 15;
    private LinearLayout content;
    private LinearLayout timelineBody;
    private ScrollView timelineScroll;
    private TimelineScale timelineScale;
    private FrameLayout timelineCanvas;
    private boolean jumpToTime = true;
    private long pendingJumpDay = Long.MIN_VALUE;
    private long loadSequence;
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeTick = new Runnable() {
        @Override public void run() { if (content != null) render(); timeHandler.postDelayed(this, 60_000); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);
        RepositoryProvider.init(this);
        selectedDayMillis = startOfDay(getIntent().getLongExtra("selectedDayMillis", System.currentTimeMillis()));
        pendingJumpDay = selectedDayMillis;
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(20), 0, 0);
        content.setBackgroundColor(BG);
        setContentView(content);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) {
            loadData();
            if (selectedDayMillis >= startOfDay(System.currentTimeMillis())) {
                AutoScheduler.replan(this, new AutoScheduler.Listener() {
                    @Override public void onScheduled(ScheduleGenerator.Result result) {
                        if (!isFinishing() && !isDestroyed()) loadData();
                    }
                    @Override public void onFailed(String error) {
                        // Existing data remains visible; task and profile save flows show errors.
                    }
                });
            }
        }
        timeHandler.postDelayed(timeTick, 60_000);
    }
    @Override protected void onPause() {
        timeHandler.removeCallbacks(timeTick);
        super.onPause();
    }
    @Override protected void onDestroy() {
        timeHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void loadData() {
        final long requestedDay = selectedDayMillis;
        final long request = ++loadSequence;
        RepositoryProvider.getUserProfileRepository().getProfile(new TaskRepository.Callback<com.tempo.app.model.UserProfile>() {
            @Override public void onSuccess(com.tempo.app.model.UserProfile profile) {
                if (profile != null && requestedDay == selectedDayMillis && request == loadSequence
                        && !isFinishing() && !isDestroyed()) {
                    focusBreakMinutes = Math.max(0, profile.getBreakDuration()); render();
                }
            }
            @Override public void onError(Exception error) { }
        });
        RepositoryProvider.getTaskRepository().getAllTasks(new TaskRepository.Callback<List<Task>>() {
            @Override public void onSuccess(List<Task> result) {
                if (requestedDay != selectedDayMillis || request != loadSequence || isFinishing() || isDestroyed()) return;
                tasks.clear();
                tasks.addAll(result);
                demoData = tasks.isEmpty();
                if (demoData) {
                    tasks.add(demoTask(-1, "Read course notes", "Study", 70));
                    tasks.add(demoTask(-2, "Finish project report", "Work", 90));
                    tasks.add(demoTask(-3, "Walk and tidy up", "Life", 45));
                }
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(selectedDayMillis));
                RepositoryProvider.getScheduleRepository().getScheduleByDate(date, new TaskRepository.Callback<List<ScheduleItem>>() {
                    @Override public void onSuccess(List<ScheduleItem> rows) {
                        if (requestedDay != selectedDayMillis || request != loadSequence || isFinishing() || isDestroyed()) return;
                        schedule.clear(); schedule.addAll(rows);
                        if (demoData && selectedDayMillis == startOfDay(System.currentTimeMillis())) addDemoSchedule();
                        if (pendingJumpDay == requestedDay) { jumpToTime = true; pendingJumpDay = Long.MIN_VALUE; }
                        render();
                    }
                    @Override public void onError(Exception error) {
                        if (requestedDay == selectedDayMillis && request == loadSequence && !isFinishing() && !isDestroyed()) render();
                    }
                });
            }
            @Override public void onError(Exception error) {
                if (requestedDay == selectedDayMillis && request == loadSequence && !isFinishing() && !isDestroyed()) render();
            }
        });
    }

    private void render() {
        int previousScroll = timelineScroll == null ? -1 : timelineScroll.getScrollY();
        content.removeAllViews();
        FrameLayout header = new FrameLayout(this);
        HomeIconView back = new HomeIconView(this);
        back.setOnClickListener(v -> {
            Intent home = new Intent(this, HomeActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL);
        backParams.leftMargin = 0;
        header.addView(back, backParams);
        TextView month = label(new SimpleDateFormat("MMMM yyyy", Locale.US).format(new Date(selectedDayMillis)),
                19, INK, true);
        month.setGravity(Gravity.CENTER);
        header.addView(month, new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER));
        content.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));
        spacer(14);
        addWeekStrip();
        spacer(13);
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(225, 231, 226));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.leftMargin = dp(22); dividerParams.rightMargin = dp(22);
        content.addView(divider, dividerParams);
        spacer(13);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), 0, dp(22), 0);
        TextView dayLabel = label(selectedDayMillis == startOfDay(System.currentTimeMillis()) ? "Today's tasks" : "Tasks this day",
                16, INK, true);
        row.addView(dayLabel);
        TextView summary = label("0/0", 16, Color.rgb(52, 126, 91), true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, -2, 1);
        countParams.leftMargin = dp(9);
        row.addView(summary, countParams);
        TextView toggle = label(includeBreaks ? "Breaks  on" : "Breaks  off", 12, MUTED, false);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(12), dp(7), dp(12), dp(7));
        toggle.setBackground(shape(Color.rgb(232, 237, 233), 12));
        toggle.setOnClickListener(v -> { includeBreaks = !includeBreaks; render(); });
        row.addView(toggle);
        content.addView(row);
        spacer(8);
        if (demoData && !schedule.isEmpty()) {
            TextView demo = label("Sample schedule · preview only, not saved", 12, MUTED, false);
            demo.setPadding(dp(22), 0, dp(22), 0);
            content.addView(demo);
        }
        spacer(8);

        timelineScroll = new ScrollView(this);
        timelineScroll.setFillViewport(true);
        timelineScroll.setVerticalScrollBarEnabled(true);
        timelineBody = new LinearLayout(this);
        timelineBody.setOrientation(LinearLayout.VERTICAL);
        timelineBody.setPadding(0, dp(10), 0, dp(28));
        timelineScroll.addView(timelineBody);
        content.addView(timelineScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Map<Integer, Task> byId = new HashMap<>();
        for (Task t : tasks) byId.put(t.getTaskId(), t);
        List<ScheduleGenerator.Session> sessions = new ArrayList<>();
        for (ScheduleItem item : schedule) {
            if (byId.containsKey(item.getTaskId()) && ("PLANNED".equals(item.getStatus())
                    || "COMPLETED".equals(item.getStatus()))) {
                sessions.add(new ScheduleGenerator.Session(item.getTaskId(),
                        new ScheduleGenerator.Interval(item.getStartTime(), item.getEndTime()), item.isManuallyEdited()));
            }
        }
        Set<Integer> dayTaskIds = new HashSet<>();
        for (ScheduleItem item : schedule) if (!"CANCELLED".equals(item.getStatus())) dayTaskIds.add(item.getTaskId());
        int finished = 0;
        for (Integer id : dayTaskIds) {
            Task task = byId.get(id);
            if (task != null && "COMPLETED".equals(task.getStatus())) finished++;
        }
        summary.setText(finished + "/" + dayTaskIds.size());
        List<ScheduleDisplayBlocks.Block> blocks = ScheduleDisplayBlocks.group(sessions,
                new ArrayList<ScheduleGenerator.Interval>(), focusBreakMinutes);
        addTimeline(blocks, byId);
        if (sessions.isEmpty()) {
            spacerIn(timelineBody, 14);
            timelineBody.addView(label("Nothing planned for this day yet. Add a task to get a suggested time.", 13, MUTED, false));
        }
        spacerIn(timelineBody, 26);
        timelineBody.addView(label("TEMPO  ·  FIND YOUR RHYTHM", 11, MUTED, true));
        addBottomBar();
        final int restoreY = previousScroll;
        final boolean jump = jumpToTime;
        jumpToTime = false;
        ScrollView newScroll = timelineScroll;
        newScroll.post(() -> {
            if (newScroll != timelineScroll) return;
            if (jump) {
                Calendar target = Calendar.getInstance();
                if (selectedDayMillis == startOfDay(System.currentTimeMillis())) target.setTimeInMillis(System.currentTimeMillis());
                else { target.setTimeInMillis(selectedDayMillis); target.set(Calendar.HOUR_OF_DAY, 8); target.set(Calendar.MINUTE, 0); }
                int minute = target.get(Calendar.HOUR_OF_DAY) * 60 + target.get(Calendar.MINUTE);
                int y = timelineCanvas.getTop() + dp(12) + Math.round(timelineScale.y(minute));
                newScroll.scrollTo(0, Math.max(0, y - newScroll.getHeight() / 2));
            } else if (restoreY >= 0) newScroll.scrollTo(0, restoreY);
        });
    }

    private void addWeekStrip() {
        Calendar first = Calendar.getInstance();
        first.setTimeInMillis(selectedDayMillis);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        first.add(Calendar.DAY_OF_MONTH, -offset);
        LinearLayout strip = new LinearLayout(this);
        strip.setPadding(dp(22), 0, dp(22), 0);
        String[] names = {"M", "T", "W", "T", "F", "S", "S"};
        for (int index = 0; index < 7; index++) {
            final long day = first.getTimeInMillis();
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, dp(2), 0, dp(2));
            TextView weekday = label(names[(first.get(Calendar.DAY_OF_WEEK) + 5) % 7], 12, MUTED, false);
            weekday.setGravity(Gravity.CENTER);
            TextView number = label(String.valueOf(first.get(Calendar.DAY_OF_MONTH)), 18, INK, true);
            number.setGravity(Gravity.CENTER);
            if (startOfDay(day) == selectedDayMillis) number.setBackground(shape(MINT, 100));
            cell.addView(weekday, new LinearLayout.LayoutParams(-1, dp(21)));
            cell.addView(number, new LinearLayout.LayoutParams(dp(42), dp(42)));
            cell.setOnTouchListener(new View.OnTouchListener() {
                private float downX;
                @Override public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) { downX = event.getX(); return true; }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        float delta = event.getX() - downX;
                        if (Math.abs(delta) > dp(35)) shiftWeek(delta < 0 ? 7 : -7);
                        else selectDay(day);
                        return true;
                    }
                    return true;
                }
            });
            strip.addView(cell, new LinearLayout.LayoutParams(0, dp(66), 1));
            first.add(Calendar.DAY_OF_MONTH, 1);
        }
        content.addView(strip);
    }

    private static final class TimelineEntry {
        final TextView view;
        final int start, end;
        TimelineEntry(TextView view, int start, int end) { this.view = view; this.start = start; this.end = end; }
    }

    private int timelineMinute(long time) {
        Calendar nextDay = Calendar.getInstance(); nextDay.setTimeInMillis(selectedDayMillis);
        nextDay.add(Calendar.DAY_OF_MONTH, 1);
        if (time >= nextDay.getTimeInMillis()) return 1440;
        if (time < selectedDayMillis) return 0;
        Calendar local = Calendar.getInstance(); local.setTimeInMillis(time);
        return local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE);
    }

    private void addTimeline(List<ScheduleDisplayBlocks.Block> blocks, Map<Integer, Task> byId) {
        TextView scaleNote = label("Short blocks are enlarged for readability · times are exact", 11, MUTED, false);
        scaleNote.setPadding(dp(62), 0, dp(10), dp(8));
        if (!blocks.isEmpty()) timelineBody.addView(scaleNote);
        FrameLayout canvas = new FrameLayout(this);
        timelineCanvas = canvas;
        canvas.setBackgroundColor(WHITE);
        canvas.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(24 * 96 + 24)));
        timelineBody.addView(canvas);
        // Measure wrapped text at the available width, then use the same expanded axis everywhere.
        int initialWidth = getResources().getDisplayMetrics().widthPixels;
        drawTimeline(canvas, blocks, byId, initialWidth);
        final int[] measuredWidth = {initialWidth};
        canvas.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            if (width > 0 && width != measuredWidth[0]) {
                measuredWidth[0] = width;
                drawTimeline(canvas, blocks, byId, width);
            }
        });
    }

    private void drawTimeline(FrameLayout canvas, List<ScheduleDisplayBlocks.Block> blocks,
                              Map<Integer, Task> byId, int width) {
        canvas.removeAllViews();
        List<TimelineEntry> entries = new ArrayList<>();
        for (ScheduleDisplayBlocks.Block block : blocks) {
            Task task = byId.get((int) block.taskId);
            int start = timelineMinute(block.startMillis), end = timelineMinute(block.endMillis);
            if (task == null || end <= start) continue;
            String duration = block.taskMinutes + " min task";
            if (includeBreaks && block.breakMinutes > 0) duration += " + " + block.breakMinutes + " min break";
            String description = task.getTitle() + "\n" + clock(block.startMillis) + "–" + clock(block.endMillis)
                    + "  ·  " + duration;
            TextView event = label(description, 13, INK, true);
            event.setGravity(Gravity.CENTER_VERTICAL);
            event.setPadding(dp(12), dp(8), dp(8), dp(8));
            event.setMinHeight(dp(48));
            event.setContentDescription(description + ("COMPLETED".equals(task.getStatus()) ? ", completed" : ", not completed"));
            event.setBackground(shape("COMPLETED".equals(task.getStatus()) ? Color.rgb(229, 233, 230)
                    : "Work".equals(task.getCategory()) ? Color.rgb(219, 231, 245)
                    : "Life".equals(task.getCategory()) ? Color.rgb(249, 233, 211) : MINT, 12));
            event.setOnClickListener(v -> openScheduleDetail(task, block));
            entries.add(new TimelineEntry(event, start, end));
        }
        if (includeBreaks) {
            List<ScheduleGenerator.Session> ordered = new ArrayList<>();
            for (ScheduleItem item : schedule) if (byId.containsKey(item.getTaskId()) && "PLANNED".equals(item.getStatus())) {
                ordered.add(new ScheduleGenerator.Session(item.getTaskId(),
                        new ScheduleGenerator.Interval(item.getStartTime(), item.getEndTime()), item.isManuallyEdited()));
            }
            for (ScheduleGenerator.Interval rest : restGaps(ordered)) {
                int start = timelineMinute(rest.start), end = timelineMinute(rest.end);
                boolean occupied = false;
                for (ScheduleDisplayBlocks.Block block : blocks) {
                    if (block.startMillis < rest.end && block.endMillis > rest.start) { occupied = true; break; }
                }
                if (end <= start || occupied) continue;
                int length = (int) ((rest.end - rest.start) / 60_000L);
                TextView restView = label("Break " + length + " min", 11, MUTED, false);
                restView.setGravity(Gravity.CENTER_VERTICAL);
                restView.setPadding(dp(12), dp(4), dp(8), dp(4));
                restView.setMinHeight(dp(28));
                restView.setBackground(shape(Color.rgb(240, 243, 239), 7));
                entries.add(new TimelineEntry(restView, start, end));
            }
        }
        List<TimelineScale.Span> spans = new ArrayList<>();
        int cardWidth = Math.max(1, width - dp(72));
        for (TimelineEntry entry : entries) {
            entry.view.measure(View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            spans.add(new TimelineScale.Span(entry.start, entry.end, entry.view.getMeasuredHeight() + dp(4)));
        }
        TimelineScale scale = new TimelineScale(dp(96) / 60f, spans);
        timelineScale = scale;
        int canvasHeight = (int) Math.ceil(scale.y(1440)) + dp(24);
        if (canvas.getLayoutParams().height != canvasHeight) {
            canvas.getLayoutParams().height = canvasHeight;
            canvas.requestLayout();
        }
        for (int hour = 0; hour <= 24; hour++) {
            int top = dp(12) + Math.round(scale.y(hour * 60));
            TextView time = label(String.format(Locale.US, "%02d:00", hour), 11, MUTED, false);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(48), dp(24));
            tp.leftMargin = dp(10); tp.topMargin = top - dp(8); canvas.addView(time, tp);
            View line = new View(this); line.setBackgroundColor(Color.rgb(231, 235, 231));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(1));
            lp.leftMargin = dp(59); lp.topMargin = top; canvas.addView(line, lp);
        }
        for (TimelineEntry entry : entries) {
            int startY = Math.round(scale.y(entry.start)), endY = Math.round(scale.y(entry.end));
            FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-1, endY - startY - dp(4));
            ep.leftMargin = dp(62); ep.rightMargin = dp(10); ep.topMargin = dp(12) + startY + dp(2);
            canvas.addView(entry.view, ep);
        }
        if (selectedDayMillis == startOfDay(System.currentTimeMillis())) {
            Calendar now = Calendar.getInstance();
            int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            int y = dp(12) + Math.round(scale.y(nowMinute));
            int red = Color.rgb(235, 67, 55);
            View marker = new View(this); marker.setBackgroundColor(red);
            FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-1, dp(2));
            mp.leftMargin = dp(55); mp.topMargin = y; canvas.addView(marker, mp);
            TextView timeBadge = label(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()), 11, WHITE, true);
            timeBadge.setGravity(Gravity.CENTER);
            timeBadge.setBackground(shape(red, 7));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(50), dp(22));
            bp.leftMargin = dp(3); bp.topMargin = y - dp(11); canvas.addView(timeBadge, bp);
        }
    }

    private List<ScheduleGenerator.Interval> restGaps(List<ScheduleGenerator.Session> source) {
        List<ScheduleGenerator.Session> ordered = new ArrayList<>(source);
        ordered.sort((a, b) -> Long.compare(a.interval.start, b.interval.start));
        List<ScheduleGenerator.Interval> rest = new ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            ScheduleGenerator.Session before = ordered.get(i - 1), after = ordered.get(i);
            long gap = after.interval.start - before.interval.end;
            if (gap > 0 && gap <= focusBreakMinutes * 60_000L) {
                rest.add(new ScheduleGenerator.Interval(before.interval.end, after.interval.start));
            }
        }
        return rest;
    }

    private int countRestMinutes(List<ScheduleGenerator.Session> sessions) {
        int sum = 0;
        for (ScheduleGenerator.Interval rest : restGaps(sessions)) sum += (rest.end - rest.start) / 60_000L;
        return sum;
    }

    private void addBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(4), 0, dp(8));
        TextView today = bottomAction("Today");
        today.setOnClickListener(v -> {
            selectedDayMillis = startOfDay(System.currentTimeMillis());
            pendingJumpDay = selectedDayMillis;
            jumpToTime = true; schedule.clear(); render(); loadData();
        });
        bar.addView(today, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView timer = bottomAction("Timer");
        timer.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("tempo_focus_timer", MODE_PRIVATE);
            int active = prefs.getInt("taskId", Integer.MIN_VALUE);
            if (active == Integer.MIN_VALUE) {
                new AlertDialog.Builder(this).setMessage("No timer is running. Open a task on the timeline and tap Start focus.")
                        .setPositiveButton("OK", null).show();
                return;
            }
            Task found = null;
            for (Task task : tasks) if (task.getTaskId() == active) found = task;
            Intent intent = new Intent(this, FocusTimerActivity.class);
            intent.putExtra("taskId", active);
            intent.putExtra("title", found == null ? "Current task" : found.getTitle());
            if (found != null) intent.putExtra("targetMinutes", focusMinutes(found));
            startActivity(intent);
        });
        bar.addView(timer, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView add = bottomAction("＋ Add");
        add.setOnClickListener(v -> addTaskForSelectedDay());
        bar.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(bar);
    }

    private TextView bottomAction(String title) {
        TextView action = label(title, 15, INK, true);
        action.setGravity(Gravity.CENTER);
        return action;
    }

    private void addTaskForSelectedDay() {
        Intent intent = new Intent(this, AddTaskActivity.class);
        intent.putExtra("selectedDayMillis", selectedDayMillis);
        startActivity(intent);
    }

    private Task demoTask(int id, String title, String category, int minutes) {
        Task task = new Task(title, category, minutes, 2, 3, 0);
        task.setTaskId(id); return task;
    }
    private void addDemoSchedule() {
        addDemoItem(-1, 9, 0, 9, 50);
        addDemoItem(-1, 10, 0, 10, 20);
        addDemoItem(-2, 11, 0, 12, 30);
        addDemoItem(-3, 16, 0, 16, 45);
    }
    private void addDemoItem(int id, int h1, int m1, int h2, int m2) {
        ScheduleItem item = new ScheduleItem(); item.setTaskId(id); item.setStatus("PLANNED");
        item.setStartTime(atDayTime(h1, m1)); item.setEndTime(atDayTime(h2, m2)); schedule.add(item);
    }
    private long atDayTime(int hour, int minute) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(selectedDayMillis);
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute); return c.getTimeInMillis();
    }
    private long startOfDay(long time) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
    private void shiftWeek(int days) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(selectedDayMillis);
        c.add(Calendar.DAY_OF_MONTH, days); selectedDayMillis = startOfDay(c.getTimeInMillis());
        pendingJumpDay = selectedDayMillis;
        jumpToTime = true; schedule.clear(); render(); loadData();
    }

    private void selectDay(long day) {
        selectedDayMillis = startOfDay(day);
        pendingJumpDay = selectedDayMillis;
        jumpToTime = true; schedule.clear(); render(); loadData();
    }

    private void openScheduleDetail(Task task, ScheduleDisplayBlocks.Block block) {
        LinearLayout body = panel(WHITE);
        body.addView(label("SCHEDULED", 11, Color.rgb(52, 126, 91), true));
        spacerIn(body, 8);
        body.addView(label(task.getTitle(), 22, INK, true));
        spacerIn(body, 8);
        body.addView(label(clock(block.startMillis) + "—" + clock(block.endMillis)
                + "  ·  " + block.taskMinutes + " min planned"
                + (block.breakMinutes > 0 ? " + " + block.breakMinutes + " min break" : ""), 14, MUTED, false));
        spacerIn(body, 18);
        View divider = new View(this); divider.setBackgroundColor(Color.rgb(225, 231, 226));
        body.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        spacerIn(body, 16);
        body.addView(label("Deadline  " + (task.getDeadline() > 0
                ? new SimpleDateFormat("MMM d, HH:mm", Locale.US).format(new Date(task.getDeadline())) : "None"), 14, INK, false));
        spacerIn(body, 8);
        body.addView(label("Difficulty " + task.getDifficulty() + "/5   ·   Importance " + task.getImportance() + "/5", 14, INK, false));
        spacerIn(body, 8);
        body.addView(label("Estimate " + task.getEstimatedDuration() + " min · Tempo predicts " + (task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration()) + " min · "
                + categoryName(task.getCategory()), 14, MUTED, false));
        if (task.getDescription() != null && !task.getDescription().trim().isEmpty()) {
            spacerIn(body, 10); body.addView(label(task.getDescription(), 13, MUTED, false));
        }
        spacerIn(body, 20);
        body.addView(label("Time slots · tap to adjust", 14, INK, true));
        spacerIn(body, 9);
        ScrollView detailScroll = new ScrollView(this);
        detailScroll.addView(body);
        AlertDialog detail = new AlertDialog.Builder(this).setView(detailScroll).create();
        for (ScheduleItem item : schedule) {
            if (item.getTaskId() != task.getTaskId() || item.getStartTime() < block.startMillis
                    || item.getEndTime() > block.endMillis) continue;
            TextView edit = detailAction(clock(item.getStartTime()) + "—" + clock(item.getEndTime())
                    + (item.isManuallyEdited() ? "  adjusted  ›" : "  suggested  ›"), BG);
            edit.setOnClickListener(v -> { detail.dismiss(); editSessionTimeTyped(task, item); });
            body.addView(edit); spacerIn(body, 8);
        }
        spacerIn(body, 9);
        if (task.getTaskId() > 0) {
            TextView editTask = detailAction("Edit task", BG);
            editTask.setOnClickListener(v -> { detail.dismiss(); editTaskInfo(task); });
            body.addView(editTask); spacerIn(body, 10);
        }
        TextView start = detailAction("COMPLETED".equals(task.getStatus()) ? "Task completed" : "Start focus  →",
                "COMPLETED".equals(task.getStatus()) ? MINT : INK);
        if (!"COMPLETED".equals(task.getStatus())) start.setTextColor(WHITE); // app-wide primary action
        start.setEnabled(!"COMPLETED".equals(task.getStatus()));
        start.setOnClickListener(v -> { detail.dismiss(); openTimer(task); });
        body.addView(start);
        if (task.getTaskId() > 0) {
            TextView delete = label("Delete task", 14, Color.rgb(170, 65, 55), false);
            delete.setGravity(Gravity.CENTER);
            delete.setOnClickListener(v -> { detail.dismiss(); confirmDeleteTask(task); });
            body.addView(delete, new LinearLayout.LayoutParams(-1, dp(48)));
        }
        TextView close = label("Close", 14, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> detail.dismiss());
        body.addView(close, new LinearLayout.LayoutParams(-1, dp(44)));
        detail.show();
        if (detail.getWindow() != null) detail.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void confirmDeleteTask(Task task) {
        if (getSharedPreferences(FocusSessionState.PREFS, MODE_PRIVATE)
                .getInt("taskId", Integer.MIN_VALUE) == task.getTaskId()) {
            Toast.makeText(this, "Finish this task's focus session before deleting it.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Delete task?")
                .setMessage("Remove “" + task.getTitle() + "” and its planned times? Recorded focus history will be kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete task", (dialog, which) ->
                        RepositoryProvider.getTaskRepository().archiveTask(task.getTaskId(), new TaskRepository.Callback<Integer>() {
                            @Override public void onSuccess(Integer rows) {
                                if (rows == null || rows == 0) { loadData(); return; }
                                AutoScheduler.replan(SchedulePreviewActivity.this, new AutoScheduler.Listener() {
                                    @Override public void onScheduled(ScheduleGenerator.Result result) { loadData(); }
                                    @Override public void onFailed(String error) {
                                        Toast.makeText(SchedulePreviewActivity.this, error, Toast.LENGTH_LONG).show();
                                        loadData();
                                    }
                                });
                            }
                            @Override public void onError(Exception error) {
                                Toast.makeText(SchedulePreviewActivity.this, "Could not delete task: " + error.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        })).show();
    }

    private String categoryName(String category) {
        return "Work".equals(category) ? "Work" : "Life".equals(category) ? "Life" : "Study";
    }

    private TextView detailAction(String text, int color) {
        TextView view = label(text, 14, INK, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(46));
        view.setPadding(dp(12), dp(9), dp(12), dp(9));
        view.setBackground(shape(color, 14));
        return view;
    }

    private String value(String text) { return text == null || text.trim().isEmpty() ? "Not set" : text; }

    private void editTaskInfo(Task source) {
        if (source.getTaskId() > 0) {
            Intent intent = new Intent(this, AddTaskActivity.class);
            intent.putExtra("taskId", source.getTaskId());
            intent.putExtra("selectedDayMillis", selectedDayMillis);
            startActivity(intent);
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(12), dp(22), dp(8));
        EditText title = input(form, "Task name", source.getTitle(), false);
        EditText description = input(form, "Description", source.getDescription(), false);
        EditText category = input(form, "Category: Study / Work / Life", source.getCategory(), false);
        EditText type = input(form, "Task type", source.getTaskType(), false);
        EditText estimate = input(form, "Estimated minutes", String.valueOf(source.getEstimatedDuration()), true);
        EditText difficulty = input(form, "Difficulty 1–5", String.valueOf(source.getDifficulty()), true);
        EditText importance = input(form, "Importance 1–5", String.valueOf(source.getImportance()), true);
        String deadlineValue = source.getDeadline() > 0
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(source.getDeadline())) : "";
        EditText deadline = input(form, "Deadline yyyy-MM-dd HH:mm · leave empty for none", deadlineValue, false);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Edit task")
                .setView(scroll).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int minutes = Integer.parseInt(estimate.getText().toString().trim());
                int difficultyValue = Integer.parseInt(difficulty.getText().toString().trim());
                int importanceValue = Integer.parseInt(importance.getText().toString().trim());
                String newTitle = title.getText().toString().trim();
                String newCategory = category.getText().toString().trim();
                if (newTitle.isEmpty() || minutes <= 0 || difficultyValue < 1 || difficultyValue > 5
                        || importanceValue < 1 || importanceValue > 5
                        || !("Study".equals(newCategory) || "Work".equals(newCategory) || "Life".equals(newCategory))) {
                    throw new IllegalArgumentException("Enter a name, a positive duration, difficulty and importance from 1 to 5, and a Study / Work / Life category.");
                }
                long due = 0;
                String dueText = deadline.getText().toString().trim();
                if (!dueText.isEmpty()) {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    format.setLenient(false);
                    Date parsed = format.parse(dueText);
                    if (parsed == null || !format.format(parsed).equals(dueText)) throw new ParseException("Invalid date", 0);
                    due = parsed.getTime();
                }
                for (ScheduleItem item : schedule) if (item.getTaskId() == source.getTaskId() && due > 0 && item.getEndTime() > due) {
                    throw new IllegalArgumentException("The deadline cannot be earlier than the planned slots.");
                }
                Task updated = new Task();
                updated.setTaskId(source.getTaskId()); updated.setTitle(newTitle);
                updated.setDescription(description.getText().toString().trim());
                updated.setCategory(newCategory); updated.setTaskType(type.getText().toString().trim());
                updated.setEstimatedDuration(minutes); updated.setPredictedDuration(source.getPredictedDuration());
                updated.setDifficulty(difficultyValue); updated.setImportance(importanceValue);
                updated.setDeadline(due); updated.setStatus(source.getStatus());
                updated.setCreatedAt(source.getCreatedAt());
                if (source.getTaskId() <= 0) {
                    tasks.set(tasks.indexOf(source), updated); dialog.dismiss(); render();
                } else {
                    RepositoryProvider.getTaskRepository().updateTask(updated, new TaskRepository.Callback<Integer>() {
                        @Override public void onSuccess(Integer rows) {
                            if (rows == null || rows == 0) { showEditError("Task not found; nothing was saved."); return; }
                            dialog.dismiss();
                            AutoScheduler.replan(SchedulePreviewActivity.this, new AutoScheduler.Listener() {
                                @Override public void onScheduled(ScheduleGenerator.Result result) { if (!isFinishing()) loadData(); }
                                @Override public void onFailed(String error) {
                                    if (isFinishing()) return;
                                    Toast.makeText(SchedulePreviewActivity.this, error, Toast.LENGTH_LONG).show();
                                    loadData();
                                }
                            });
                        }
                        @Override public void onError(Exception error) { showEditError("Could not save: " + error.getMessage()); }
                    });
                }
            } catch (NumberFormatException error) {
                showEditError("Minutes, difficulty and importance must be numbers.");
            } catch (ParseException error) {
                showEditError("Use yyyy-MM-dd HH:mm for the deadline.");
            } catch (IllegalArgumentException error) {
                showEditError(error.getMessage());
            }
        }));
        dialog.show();
    }

    private EditText input(LinearLayout form, String hint, String value, boolean numeric) {
        TextView label = label(hint, 12, MUTED, false);
        form.addView(label);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(value == null ? "" : value);
        if (numeric) field.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(field);
        spacerIn(form, 10);
        return field;
    }

    private void editSessionTimeTyped(Task task, ScheduleItem item) {
        LinearLayout body = panel(WHITE);
        body.addView(label("Adjust time slot", 21, INK, true));
        spacerIn(body, 7);
        body.addView(label("Scroll to choose a time. Saving locks this slot so re-planning keeps it.", 13, MUTED, false));
        spacerIn(body, 18);
        EditText startField = timeInput("Start HH:mm", clock(item.getStartTime()));
        EditText endField = timeInput("End HH:mm", clock(item.getEndTime()));
        body.addView(label("Start", 12, MUTED, false));
        spacerIn(body, 6);
        body.addView(startField, new LinearLayout.LayoutParams(-1, dp(50)));
        spacerIn(body, 14);
        body.addView(label("End", 12, MUTED, false));
        spacerIn(body, 6);
        body.addView(endField, new LinearLayout.LayoutParams(-1, dp(50)));
        spacerIn(body, 19);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(body).create();
        TextView save = detailAction("Save time", MINT);
        body.addView(save);
        save.setOnClickListener(v -> {
            int startMinute, endMinute;
            try {
                startMinute = parseClockMinute(startField.getText().toString().trim(), false);
                endMinute = parseClockMinute(endField.getText().toString().trim(), true);
            } catch (IllegalArgumentException error) { showEditError(error.getMessage()); return; }
            long start = atDayTime(startMinute / 60, startMinute % 60);
            long end = atDayTime(endMinute / 60, endMinute % 60);
            if (start >= end) { showEditError("The end time must be after the start time."); return; }
            if (task.getDeadline() > 0 && end > task.getDeadline()) {
                showEditError("The end time cannot be after the task deadline."); return;
            }
            int lockedMinutes = ScheduleEditRules.lockedMinutes(schedule, item, start, end);
            int plannedMinutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
            if (plannedMinutes > 0 && lockedMinutes > plannedMinutes) {
                showEditError("Locked time cannot exceed the task's expected duration."); return;
            }
            if (ScheduleEditRules.overlaps(schedule, item, start, end)) {
                showEditError("This time overlaps another planned slot."); return;
            }
            if (item.getScheduleId() <= 0) {
                item.setStartTime(start); item.setEndTime(end); item.setManuallyEdited(true);
                dialog.dismiss(); render();
            } else {
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(selectedDayMillis));
                RepositoryProvider.getScheduleRepository().updatePlannedTime(item.getScheduleId(), date, start, end,
                        new TaskRepository.Callback<Integer>() {
                            @Override public void onSuccess(Integer rows) {
                                dialog.dismiss();
                                AutoScheduler.replan(SchedulePreviewActivity.this, new AutoScheduler.Listener() {
                                    @Override public void onScheduled(ScheduleGenerator.Result result) { loadData(); }
                                    @Override public void onFailed(String error) {
                                        Toast.makeText(SchedulePreviewActivity.this, error, Toast.LENGTH_LONG).show();
                                        loadData();
                                    }
                                });
                            }
                            @Override public void onError(Exception error) { showEditError(error.getMessage()); }
                        });
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private EditText timeInput(String hint, String value) {
        EditText field = new EditText(this);
        field.setSingleLine(true); field.setText(value); field.setHint(hint);
        field.setTextSize(16); field.setTextColor(INK);
        field.setPadding(dp(14), 0, dp(14), 0);
        field.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        field.setFocusable(false);
        field.setOnClickListener(v -> WheelPickers.pickTime(this, field, hint.startsWith("End")));
        field.setBackground(shape(BG, 12));
        field.setSelectAllOnFocus(true);
        return field;
    }

    private int parseClockMinute(String text, boolean allowEndOfDay) {
        if (allowEndOfDay && "24:00".equals(text)) return 24 * 60;
        if (!text.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException("Use HH:mm, for example 09:30.");
        }
        return Integer.parseInt(text.substring(0, 2)) * 60 + Integer.parseInt(text.substring(3, 5));
    }

    private void showEditError(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }

    private void openTimer(Task task) {
        Intent intent = new Intent(this, FocusTimerActivity.class);
        intent.putExtra("taskId", task.getTaskId());
        intent.putExtra("title", task.getTitle());
        intent.putExtra("targetMinutes", focusMinutes(task));
        startActivity(intent);
    }

    private int focusMinutes(Task task) {
        return task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(sp); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private Button button(String text, int color) {
        Button view = new Button(this); view.setText(text); view.setTextColor(INK); view.setAllCaps(false);
        view.setBackground(shape(color, 14)); view.setPadding(dp(13), 0, dp(13), 0); return view;
    }
    private LinearLayout panel(int color) {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(20), dp(20), dp(20), dp(20)); view.setBackground(shape(color, 22)); return view;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void spacer(int height) { spacerIn(content, height); }
    private void spacerIn(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String clock(long millis) { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis)); }
}

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
import com.tempo.app.ui.Ui;
import com.tempo.app.scheduler.FixedBlocks;
import com.tempo.app.database.ScheduleDao;

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
    private String planError;
    private com.tempo.app.model.UserProfile routine;
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
        com.tempo.app.ui.Ui.fitSystemBars(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) {
            loadData();
            if (selectedDayMillis >= startOfDay(System.currentTimeMillis())) {
                // Opening the page must not move suggestions the user already saw: keep upcoming
                // slots and only re-place missed ones (see AutoScheduler.refresh).
                AutoScheduler.refresh(this, new AutoScheduler.Listener() {
                    @Override public void onScheduled(ScheduleGenerator.Result result) {
                        if (!isFinishing() && !isDestroyed()) { planError = null; loadData(); }
                    }
                    @Override public void onFailed(String error) {
                        if (!isFinishing() && !isDestroyed() && !AutoScheduler.NO_PROFILE.equals(error)
                                && !AutoScheduler.TIMER_ACTIVE.equals(error)) {
                            planError = error; render();
                        }
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
                    routine = profile;
                    focusBreakMinutes = Math.max(0, profile.getBreakDuration()); render();
                }
            }
            @Override public void onError(Exception error) { }
        });
        boolean pastDay = requestedDay < startOfDay(System.currentTimeMillis());
        RepositoryProvider.getTaskRepository().getAllTasks(pastDay, new TaskRepository.Callback<List<Task>>() {
            @Override public void onSuccess(List<Task> result) {
                if (requestedDay != selectedDayMillis || request != loadSequence || isFinishing() || isDestroyed()) return;
                tasks.clear();
                tasks.addAll(result);
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(selectedDayMillis));
                RepositoryProvider.getScheduleRepository().getScheduleByDate(date, new TaskRepository.Callback<List<ScheduleItem>>() {
                    @Override public void onSuccess(List<ScheduleItem> rows) {
                        if (requestedDay != selectedDayMillis || request != loadSequence || isFinishing() || isDestroyed()) return;
                        schedule.clear(); schedule.addAll(rows);
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
        TextView dayLabel = label("Scheduled tasks",
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
        String weekly = routine == null ? "" : routine.getFixedBlocks();
        List<FixedBlocks.Entry> fixed = FixedBlocks.onDay(weekly, selectedDayMillis);
        List<ScheduleGenerator.Interval> busy = FixedBlocks.intervals(weekly, selectedDayMillis);

        Button manage = new Button(this);
        manage.setText("Manage tasks");
        manage.setAllCaps(false);
        manage.setTextSize(15);
        manage.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        manage.setTextColor(WHITE);
        manage.setBackgroundTintList(null);
        manage.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), shape(INK, 14), null));
        manage.setOnClickListener(v -> showTaskList());
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(22), 0, dp(22), dp(10));
        actions.addView(manage, new LinearLayout.LayoutParams(0, dp(48), 1));
        if (!fixed.isEmpty()) {
            Button locate = new Button(this);
            locate.setText(fixed.size() + " fixed");
            locate.setAllCaps(false);
            locate.setTextColor(Ui.FIXED_INK);
            locate.setBackgroundTintList(null);
            locate.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x20606A78), Ui.round(Ui.FIXED_BG, dp(12)), null));
            locate.setContentDescription("Find fixed commitments on this day");
            locate.setOnClickListener(v -> locateFixed(fixed));
            LinearLayout.LayoutParams findParams = new LinearLayout.LayoutParams(-2, dp(48));
            findParams.leftMargin = dp(10);
            actions.addView(locate, findParams);
        }
        content.addView(actions);
        if (planError != null) {
            TextView error = label(planError, 13, Ui.RED, false);
            error.setPadding(dp(22), 0, dp(22), dp(8));
            content.addView(error);
        }
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
        List<ScheduleItem> history = new ArrayList<>();
        boolean past = selectedDayMillis < startOfDay(System.currentTimeMillis());
        for (ScheduleItem item : schedule) {
            Task task = byId.get(item.getTaskId());
            if (task == null || "CANCELLED".equals(item.getStatus())) continue;
            if (task.isArchived() || ScheduleItem.PAST_PLAN.equals(item.getStatus())) {
                if (past) history.add(item);
                continue;
            }
            if ("PLANNED".equals(item.getStatus()) || "COMPLETED".equals(item.getStatus())) {
                sessions.add(new ScheduleGenerator.Session(item.getTaskId(),
                        new ScheduleGenerator.Interval(item.getStartTime(), item.getEndTime()), item.isManuallyEdited()));
            }
        }
        Set<Integer> dayTaskIds = new HashSet<>();
        for (ScheduleGenerator.Session session : sessions) dayTaskIds.add((int) session.taskId);
        for (ScheduleItem item : history) dayTaskIds.add(item.getTaskId());
        int finished = 0;
        for (Integer id : dayTaskIds) {
            Task task = byId.get(id);
            if (task != null && "COMPLETED".equals(task.getStatus())) finished++;
        }
        summary.setText(past ? String.valueOf(dayTaskIds.size()) : finished + "/" + dayTaskIds.size());
        List<ScheduleDisplayBlocks.Block> blocks = ScheduleDisplayBlocks.group(sessions,
                busy, focusBreakMinutes);
        addTimeline(blocks, byId, fixed, history);
        if (sessions.isEmpty() && fixed.isEmpty() && history.isEmpty()) {
            spacerIn(timelineBody, 14);
            TextView add = detailAction(tasks.isEmpty() ? "No tasks yet · Add your first task" : "Nothing planned · Add a task", MINT);
            add.setOnClickListener(v -> addTaskForSelectedDay());
            timelineBody.addView(add);
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

    private void locateFixed(List<FixedBlocks.Entry> fixed) {
        if (fixed.size() == 1) {
            scrollToMinute(fixed.get(0).start);
            return;
        }
        String[] choices = new String[fixed.size()];
        for (int i = 0; i < choices.length; i++)
            choices[i] = fixed.get(i).timeRange() + "   " + fixed.get(i).title();
        new AlertDialog.Builder(this).setTitle("Fixed commitments").setItems(choices,
                (d, which) -> scrollToMinute(fixed.get(which).start)).show();
    }

    private void scrollToMinute(int minute) {
        int y = timelineCanvas.getTop() + dp(12) + Math.round(timelineScale.y(minute));
        timelineScroll.smoothScrollTo(0, Math.max(0, y - dp(24)));
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
        boolean fixed, sideBySide;
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

    private void addTimeline(List<ScheduleDisplayBlocks.Block> blocks, Map<Integer, Task> byId, List<FixedBlocks.Entry> fixed, List<ScheduleItem> history) {
        TextView scaleNote = label("Short blocks are enlarged for readability · times are exact", 11, MUTED, false);
        scaleNote.setPadding(dp(62), 0, dp(10), dp(8));
        if (!blocks.isEmpty() || !fixed.isEmpty() || !history.isEmpty()) timelineBody.addView(scaleNote);
        FrameLayout canvas = new FrameLayout(this);
        timelineCanvas = canvas;
        canvas.setBackgroundColor(WHITE);
        canvas.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(24 * 96 + 24)));
        timelineBody.addView(canvas);
        // Measure wrapped text at the available width, then use the same expanded axis everywhere.
        int initialWidth = getResources().getDisplayMetrics().widthPixels;
        drawTimeline(canvas, blocks, byId, fixed, history, initialWidth);
        final int[] measuredWidth = {initialWidth};
        canvas.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            if (width > 0 && width != measuredWidth[0]) {
                measuredWidth[0] = width;
                drawTimeline(canvas, blocks, byId, fixed, history, width);
            }
        });
    }

    private void drawTimeline(FrameLayout canvas, List<ScheduleDisplayBlocks.Block> blocks,
                              Map<Integer, Task> byId, List<FixedBlocks.Entry> fixed, List<ScheduleItem> history, int width) {
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
            if ("COMPLETED".equals(task.getStatus())) {
                event.setBackground(shape(Color.rgb(229, 233, 230), 12));
            } else {
                // Category colour: a tinted card with a solid stripe on the left edge.
                GradientDrawable fill = new GradientDrawable();
                fill.setColor(com.tempo.app.ui.Categories.soft(task.getCategory()));
                fill.setCornerRadius(dp(12));
                GradientDrawable stripe = new GradientDrawable();
                stripe.setColor(com.tempo.app.ui.Categories.color(task.getCategory()));
                stripe.setCornerRadius(dp(12));
                android.graphics.drawable.LayerDrawable layered =
                        new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{stripe, fill});
                layered.setLayerInset(1, dp(5), 0, 0, 0);
                event.setBackground(layered);
            }
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
                for (FixedBlocks.Entry commitment : fixed) {
                    if (start < commitment.end && commitment.start < end) { occupied = true; break; }
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
        for (ScheduleItem past : history) {
            int start = timelineMinute(past.getStartTime()), end = timelineMinute(past.getEndTime());
            if (end <= start) continue;
            String description = byId.get(past.getTaskId()).getTitle() + "\nPast plan · "
                    + clock(past.getStartTime()) + "–" + clock(past.getEndTime());
            TextView card = label(description, 13, MUTED, true);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(8), dp(8), dp(8));
            card.setBackground(shape(Color.rgb(238, 241, 238), 12));
            entries.add(new TimelineEntry(card, start, end));
        }
        for (FixedBlocks.Entry commitment : fixed) {
            String title = commitment.title();
            String prefix = title + "\n" + commitment.timeRange() + " · ";
            android.text.SpannableString caption = new android.text.SpannableString(prefix + "Fixed");
            int flags = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
            caption.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, title.length(), flags);
            caption.setSpan(new android.text.style.ForegroundColorSpan(Ui.FIXED_INK), prefix.length(), caption.length(), flags);
            caption.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), prefix.length(), caption.length(), flags);
            TextView card = label("", 13, Ui.INK, false);
            card.setText(caption);
            card.setContentDescription("Fixed weekly commitment, " + commitment.title() + ", " + commitment.weeklyTime());
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(8), dp(8), dp(8));
            card.setMinHeight(dp(48));
            card.setBackground(Ui.fixedBackground(this));
            TimelineEntry entry = new TimelineEntry(card, commitment.start, commitment.end);
            entry.fixed = true;
            // A failed replan can leave the old task and the newly saved routine overlapping.
            for (TimelineEntry other : entries) {
                if (!other.fixed && other.start < entry.end && entry.start < other.end) {
                    other.sideBySide = true;
                    entry.sideBySide = true;
                }
            }
            entries.add(entry);
        }
        List<TimelineScale.Span> spans = new ArrayList<>();
        int cardWidth = Math.max(1, width - dp(72));
        int laneWidth = Math.max(1, (cardWidth - dp(4)) / 2);
        for (TimelineEntry entry : entries) {
            entry.view.measure(View.MeasureSpec.makeMeasureSpec(entry.sideBySide ? laneWidth : cardWidth, View.MeasureSpec.EXACTLY),
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
            if (entry.sideBySide) {
                ep.width = laneWidth;
                if (entry.fixed) ep.leftMargin += laneWidth + dp(4);
            }
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
            for (Task task : tasks) if (!task.isArchived() && task.getTaskId() == active) found = task;
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
            if ("CANCELLED".equals(item.getStatus()) || ScheduleItem.PAST_PLAN.equals(item.getStatus())
                    || item.getTaskId() != task.getTaskId() || item.getStartTime() < block.startMillis
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
            TextView delete = label("Archive task", 14, Color.rgb(170, 65, 55), false);
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
        if (task.isArchived()) { confirmErase(task); return; }
        new AlertDialog.Builder(this).setTitle("Archive task?")
                .setMessage("Remove “" + task.getTitle() + "” from the plan? Your saved activity stays in History and Insights.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete permanently…", (d,w) -> confirmErase(task))
                .setPositiveButton("Archive task", (d,w) -> removeTask(task, false)).show();
    }

    private void confirmErase(Task task) {
        new AlertDialog.Builder(this).setTitle("Delete permanently?")
                .setMessage("Delete “" + task.getTitle() + "” and all its activity records? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete permanently", (d,w) -> removeTask(task, true)).show();
    }

    private void removeTask(Task task, boolean erase) {
        TaskRepository repo = RepositoryProvider.getTaskRepository();
        if (erase) repo.deleteTask(task.getTaskId(), afterTaskChange());
        else repo.archiveTask(task.getTaskId(), afterTaskChange());
    }

    private TaskRepository.Callback<Integer> afterTaskChange() {
        return new TaskRepository.Callback<Integer>() {
            public void onSuccess(Integer rows) {
                loadData();
                AutoScheduler.replan(SchedulePreviewActivity.this, new AutoScheduler.Listener() {
                    public void onScheduled(ScheduleGenerator.Result result) { planError = null; loadData(); }
                    public void onFailed(String error) { planError = error; loadData(); }
                });
            }
            public void onError(Exception error) { showEditError(error.getMessage()); }
        };
    }

    private void showTaskList() {
        RepositoryProvider.getTaskRepository().getManageableTasks(new TaskRepository.Callback<List<Task>>() {
            public void onSuccess(List<Task> list) {
                if (isFinishing() || isDestroyed()) return;
                String[] names = new String[list.size()];
                for (int i = 0; i < names.length; i++) {
                    Task task = list.get(i);
                    String state = task.isArchived() ? "Archived" : "COMPLETED".equals(task.getStatus()) ? "Completed"
                            : task.hasFutureSlot() ? "Scheduled" : "Unscheduled";
                    names[i] = state + "  ·  " + task.getTitle();
                }
                new AlertDialog.Builder(SchedulePreviewActivity.this).setTitle("Manage tasks")
                        .setItems(names, (d,i) -> showTaskActions(list.get(i)))
                        .setPositiveButton("Add task", (d,w) -> addTaskForSelectedDay())
                        .setNegativeButton("Close", null).show();
            }
            public void onError(Exception error) { showEditError(error.getMessage()); }
        });
    }

    private void showTaskActions(Task task) {
        if (task.isArchived()) {
            new AlertDialog.Builder(this).setTitle(task.getTitle())
                    .setItems(new String[]{"Restore task", "Delete permanently…"}, (d,i) -> {
                        if (i == 0) RepositoryProvider.getTaskRepository().restoreTask(task.getTaskId(), afterTaskChange());
                        else confirmDeleteTask(task);
                    }).setNegativeButton("Close", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(task.getTitle())
                .setItems(new String[]{"Edit task", "Archive or delete…"}, (d,i) -> {
                    if (i == 0) editTaskInfo(task);
                    else confirmDeleteTask(task);
                }).setNegativeButton("Close", null).show();
    }

    private String categoryName(String category) {
        return com.tempo.app.ui.Categories.name(category);
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
        Intent intent = new Intent(this, AddTaskActivity.class);
        intent.putExtra("taskId", source.getTaskId());
        intent.putExtra("selectedDayMillis", selectedDayMillis);
        startActivity(intent);
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
            if (ScheduleEditRules.overlaps(schedule, item, start, end)) {
                showEditError("This time overlaps another planned slot."); return;
            }
            if (item.getScheduleId() <= 0) {
                item.setStartTime(start); item.setEndTime(end); item.setManuallyEdited(true);
                dialog.dismiss(); render();
            } else {
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(selectedDayMillis));
                saveSlot(dialog, save, item.getScheduleId(), date, start, end, null);
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void saveSlot(AlertDialog editor, TextView save, int id, String date, long start, long end,
                          ScheduleDao.RemainingConflict confirmation) {
        save.setEnabled(false);
        RepositoryProvider.getScheduleRepository().updatePlannedTime(id, date, start, end, confirmation,
                new TaskRepository.Callback<Integer>() {
                    @Override public void onSuccess(Integer rows) {
                        if (isFinishing() || isDestroyed()) return;
                        editor.dismiss();
                        AutoScheduler.replan(SchedulePreviewActivity.this, new AutoScheduler.Listener() {
                            @Override public void onScheduled(ScheduleGenerator.Result result) { planError = null; loadData(); }
                            @Override public void onFailed(String error) { planError = error; loadData(); }
                        });
                    }
                    @Override public void onError(Exception error) {
                        if (isFinishing() || isDestroyed() || !editor.isShowing()) return;
                        save.setEnabled(true);
                        if (error instanceof ScheduleDao.RemainingConflict) {
                            ScheduleDao.RemainingConflict conflict = (ScheduleDao.RemainingConflict) error;
                            new AlertDialog.Builder(SchedulePreviewActivity.this)
                                    .setTitle("Update remaining time?")
                                    .setMessage(conflict.getMessage() + " Update the remaining estimate and save this slot?")
                                    .setNegativeButton("Adjust slot", null)
                                    .setPositiveButton("Use " + conflict.required + " min", (d, w) ->
                                            saveSlot(editor, save, id, date, start, end, conflict)).show();
                        } else showEditError(error.getMessage());
                    }
                });
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

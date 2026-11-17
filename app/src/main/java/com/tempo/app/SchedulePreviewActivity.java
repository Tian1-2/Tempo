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
    private boolean jumpToTime = true;
    private long pendingJumpDay = Long.MIN_VALUE;
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
        selectedDayMillis = startOfDay(System.currentTimeMillis());
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
        if (content != null) loadData();
        timeHandler.postDelayed(timeTick, 60_000);
    }
    @Override protected void onPause() { timeHandler.removeCallbacks(timeTick); super.onPause(); }

    private void loadData() {
        final long requestedDay = selectedDayMillis;
        RepositoryProvider.getUserProfileRepository().getProfile(new TaskRepository.Callback<com.tempo.app.model.UserProfile>() {
            @Override public void onSuccess(com.tempo.app.model.UserProfile profile) {
                if (profile != null && requestedDay == selectedDayMillis) {
                    focusBreakMinutes = Math.max(0, profile.getBreakDuration()); render();
                }
            }
            @Override public void onError(Exception error) { }
        });
        RepositoryProvider.getTaskRepository().getAllTasks(new TaskRepository.Callback<List<Task>>() {
            @Override public void onSuccess(List<Task> result) {
                if (requestedDay != selectedDayMillis) return;
                tasks.clear();
                tasks.addAll(result);
                demoData = tasks.isEmpty();
                if (demoData) {
                    tasks.add(demoTask(-1, "阅读课程资料", "Study", 70));
                    tasks.add(demoTask(-2, "完成项目报告", "Work", 90));
                    tasks.add(demoTask(-3, "散步与整理", "Life", 45));
                }
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(selectedDayMillis));
                RepositoryProvider.getScheduleRepository().getScheduleByDate(date, new TaskRepository.Callback<List<ScheduleItem>>() {
                    @Override public void onSuccess(List<ScheduleItem> rows) {
                        if (requestedDay != selectedDayMillis) return;
                        schedule.clear(); schedule.addAll(rows);
                        if (demoData && selectedDayMillis == startOfDay(System.currentTimeMillis())) addDemoSchedule();
                        if (pendingJumpDay == requestedDay) { jumpToTime = true; pendingJumpDay = Long.MIN_VALUE; }
                        render();
                    }
                    @Override public void onError(Exception error) { if (requestedDay == selectedDayMillis) render(); }
                });
            }
            @Override public void onError(Exception error) { if (requestedDay == selectedDayMillis) render(); }
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
        TextView month = label(new SimpleDateFormat("yyyy年M月", Locale.CHINA).format(new Date(selectedDayMillis)),
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
        TextView dayLabel = label(selectedDayMillis == startOfDay(System.currentTimeMillis()) ? "今日事项" : "当日事项",
                16, INK, true);
        row.addView(dayLabel);
        TextView summary = label("0/0", 16, Color.rgb(52, 126, 91), true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, -2, 1);
        countParams.leftMargin = dp(9);
        row.addView(summary, countParams);
        TextView toggle = label(includeBreaks ? "休息  开" : "休息  关", 12, MUTED, false);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(12), dp(7), dp(12), dp(7));
        toggle.setBackground(shape(Color.rgb(232, 237, 233), 12));
        toggle.setOnClickListener(v -> { includeBreaks = !includeBreaks; render(); });
        row.addView(toggle);
        content.addView(row);
        spacer(8);
        if (demoData && !schedule.isEmpty()) {
            TextView demo = label("示例日程 · 用于预览排版，不会写入数据库", 12, MUTED, false);
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
        for (ScheduleItem item : schedule) dayTaskIds.add(item.getTaskId());
        int finished = 0;
        for (Integer id : dayTaskIds) {
            Task task = byId.get(id);
            if (task != null && "COMPLETED".equals(task.getStatus())) finished++;
        }
        summary.setText(finished + "/" + dayTaskIds.size());
        List<ScheduleDisplayBlocks.Block> blocks = ScheduleDisplayBlocks.group(sessions,
                new ArrayList<ScheduleGenerator.Interval>(), 0);
        addTimeline(blocks, byId);
        if (sessions.isEmpty()) {
            spacerIn(timelineBody, 14);
            timelineBody.addView(label("这一天还没有生成计划。任务会在排程后出现在对应时间。", 13, MUTED, false));
        }
        spacerIn(timelineBody, 26);
        timelineBody.addView(label("TEMPO  ·  FIND YOUR RHYTHM", 11, MUTED, true));
        addBottomBar();
        final int restoreY = previousScroll;
        final boolean jump = jumpToTime;
        jumpToTime = false;
        ScrollView newScroll = timelineScroll;
        newScroll.post(() -> {
            if (jump) {
                Calendar target = Calendar.getInstance();
                if (selectedDayMillis == startOfDay(System.currentTimeMillis())) target.setTimeInMillis(System.currentTimeMillis());
                else { target.setTimeInMillis(selectedDayMillis); target.set(Calendar.HOUR_OF_DAY, 8); target.set(Calendar.MINUTE, 0); }
                int minute = target.get(Calendar.HOUR_OF_DAY) * 60 + target.get(Calendar.MINUTE);
                newScroll.scrollTo(0, Math.max(0, minute * dp(96) / 60 - newScroll.getHeight() / 2));
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
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
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

    private void addTimeline(List<ScheduleDisplayBlocks.Block> blocks, Map<Integer, Task> byId) {
        int firstHour = 0, lastHour = 24;
        final int hourHeight = dp(96);
        FrameLayout canvas = new FrameLayout(this);
        canvas.setBackgroundColor(WHITE);
        canvas.setLayoutParams(new LinearLayout.LayoutParams(-1, (lastHour - firstHour) * hourHeight + dp(24)));
        for (int hour = firstHour; hour <= lastHour; hour++) {
            int top = dp(12) + (hour - firstHour) * hourHeight;
            TextView time = label(String.format(Locale.US, "%02d:00", hour), 11, MUTED, false);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(48), dp(24));
            tp.leftMargin = dp(10); tp.topMargin = top - dp(8); canvas.addView(time, tp);
            View line = new View(this); line.setBackgroundColor(Color.rgb(231, 235, 231));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(1));
            lp.leftMargin = dp(59); lp.topMargin = top; canvas.addView(line, lp);
        }
        for (ScheduleDisplayBlocks.Block block : blocks) {
            Task task = byId.get((int) block.taskId);
            if (task == null) continue;
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(block.startMillis);
            int startMinute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            c.setTimeInMillis(block.endMillis);
            int endMinute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            if (endMinute <= startMinute) endMinute = 24 * 60;
            int y = dp(12) + (startMinute - firstHour * 60) * hourHeight / 60;
            int height = Math.max(dp(43), (endMinute - startMinute) * hourHeight / 60);
            String duration = block.taskMinutes + " 分钟任务";
            if (includeBreaks && block.breakMinutes > 0) duration += " + " + block.breakMinutes + " 分钟休息";
            TextView event = label(task.getTitle() + "\n" + clock(block.startMillis) + "–" + clock(block.endMillis)
                    + "  ·  " + duration, 13, INK, true);
            event.setGravity(Gravity.CENTER_VERTICAL);
            event.setPadding(dp(12), dp(4), dp(8), dp(4));
            event.setBackground(shape("COMPLETED".equals(task.getStatus()) ? Color.rgb(229, 233, 230)
                    : "Work".equals(task.getCategory()) ? Color.rgb(219, 231, 245)
                    : "Life".equals(task.getCategory()) ? Color.rgb(249, 233, 211) : MINT, 12));
            event.setOnClickListener(v -> openScheduleDetail(task, block));
            FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-1, height);
            ep.leftMargin = dp(62); ep.rightMargin = dp(10); ep.topMargin = y + dp(2);
            canvas.addView(event, ep);
        }
        if (includeBreaks) {
            List<ScheduleGenerator.Session> ordered = new ArrayList<>();
            for (ScheduleItem item : schedule) if (byId.containsKey(item.getTaskId()) && "PLANNED".equals(item.getStatus())) {
                ordered.add(new ScheduleGenerator.Session(item.getTaskId(),
                        new ScheduleGenerator.Interval(item.getStartTime(), item.getEndTime()), item.isManuallyEdited()));
            }
            for (ScheduleGenerator.Interval rest : restGaps(ordered)) {
                Calendar c = Calendar.getInstance(); c.setTimeInMillis(rest.start);
                int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
                int y = dp(12) + (minute - firstHour * 60) * hourHeight / 60;
                int length = (int) ((rest.end - rest.start) / 60_000L);
                TextView restView = label("休息 " + length + " 分钟", 10, MUTED, true);
                restView.setGravity(Gravity.CENTER);
                restView.setBackground(shape(Color.rgb(230, 234, 229), 7));
                FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-1, length * hourHeight / 60);
                rp.leftMargin = dp(62); rp.rightMargin = dp(10); rp.topMargin = y;
                canvas.addView(restView, rp);
            }
        }
        if (selectedDayMillis == startOfDay(System.currentTimeMillis())) {
            Calendar now = Calendar.getInstance();
            int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            if (nowMinute >= firstHour * 60 && nowMinute <= lastHour * 60) {
                int y = dp(12) + (nowMinute - firstHour * 60) * hourHeight / 60;
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
        timelineBody.addView(canvas);
    }

    private List<ScheduleGenerator.Interval> restGaps(List<ScheduleGenerator.Session> source) {
        List<ScheduleGenerator.Session> ordered = new ArrayList<>(source);
        ordered.sort((a, b) -> Long.compare(a.interval.start, b.interval.start));
        List<ScheduleGenerator.Interval> rest = new ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            ScheduleGenerator.Session before = ordered.get(i - 1), after = ordered.get(i);
            long gap = after.interval.start - before.interval.end;
            if (before.taskId == after.taskId && gap > 0 && gap <= focusBreakMinutes * 60_000L) {
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
        TextView today = bottomAction("今天");
        today.setOnClickListener(v -> {
            selectedDayMillis = startOfDay(System.currentTimeMillis());
            pendingJumpDay = selectedDayMillis;
            jumpToTime = true; schedule.clear(); render(); loadData();
        });
        bar.addView(today, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView timer = bottomAction("计时");
        timer.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("tempo_focus_timer", MODE_PRIVATE);
            int active = prefs.getInt("taskId", Integer.MIN_VALUE);
            if (active == Integer.MIN_VALUE) {
                new AlertDialog.Builder(this).setMessage("还没有进行中的计时。请先打开一项日程并点“开始专注”。")
                        .setPositiveButton("好的", null).show();
                return;
            }
            Task found = null;
            for (Task task : tasks) if (task.getTaskId() == active) found = task;
            Intent intent = new Intent(this, FocusTimerActivity.class);
            intent.putExtra("taskId", active);
            intent.putExtra("title", found == null ? "当前任务" : found.getTitle());
            if (found != null) intent.putExtra("targetMinutes", focusMinutes(found));
            startActivity(intent);
        });
        bar.addView(timer, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView add = bottomAction("＋ 添加");
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
        body.addView(label("日程安排", 12, Color.rgb(52, 126, 91), true));
        spacerIn(body, 8);
        body.addView(label(task.getTitle(), 22, INK, true));
        spacerIn(body, 8);
        body.addView(label(clock(block.startMillis) + "—" + clock(block.endMillis)
                + "  ·  计划 " + block.taskMinutes + " 分钟"
                + (block.breakMinutes > 0 ? " + 休息 " + block.breakMinutes + " 分钟" : ""), 14, MUTED, false));
        spacerIn(body, 18);
        View divider = new View(this); divider.setBackgroundColor(Color.rgb(225, 231, 226));
        body.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        spacerIn(body, 16);
        body.addView(label("截止时间  " + (task.getDeadline() > 0
                ? new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(task.getDeadline())) : "未设置"), 14, INK, false));
        spacerIn(body, 8);
        body.addView(label("难度 " + task.getDifficulty() + "/5   ·   重要程度 " + task.getImportance() + "/5", 14, INK, false));
        spacerIn(body, 8);
        body.addView(label("预估 " + task.getEstimatedDuration() + " 分钟   ·   "
                + categoryName(task.getCategory()), 14, MUTED, false));
        if (task.getDescription() != null && !task.getDescription().trim().isEmpty()) {
            spacerIn(body, 10); body.addView(label(task.getDescription(), 13, MUTED, false));
        }
        spacerIn(body, 20);
        body.addView(label("安排时间 · 可手动调整", 14, INK, true));
        spacerIn(body, 9);
        ScrollView detailScroll = new ScrollView(this);
        detailScroll.addView(body);
        AlertDialog detail = new AlertDialog.Builder(this).setView(detailScroll).create();
        for (ScheduleItem item : schedule) {
            if (item.getTaskId() != task.getTaskId() || item.getStartTime() < block.startMillis
                    || item.getEndTime() > block.endMillis) continue;
            TextView edit = detailAction(clock(item.getStartTime()) + "—" + clock(item.getEndTime())
                    + (item.isManuallyEdited() ? "  已调整  ›" : "  推荐  ›"), BG);
            edit.setOnClickListener(v -> { detail.dismiss(); editSessionTimeTyped(task, item); });
            body.addView(edit); spacerIn(body, 8);
        }
        spacerIn(body, 9);
        if (task.getTaskId() > 0) {
            TextView editTask = detailAction("编辑任务条件", BG);
            editTask.setOnClickListener(v -> { detail.dismiss(); editTaskInfo(task); });
            body.addView(editTask); spacerIn(body, 10);
        }
        TextView start = detailAction("开始专注  →", MINT);
        start.setOnClickListener(v -> { detail.dismiss(); openTimer(task); });
        body.addView(start);
        TextView close = label("关闭", 14, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> detail.dismiss());
        body.addView(close, new LinearLayout.LayoutParams(-1, dp(44)));
        detail.show();
        if (detail.getWindow() != null) detail.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private String categoryName(String category) {
        return "Work".equals(category) ? "工作" : "Life".equals(category) ? "生活" : "学习";
    }

    private TextView detailAction(String text, int color) {
        TextView view = label(text, 14, INK, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(46));
        view.setPadding(dp(12), dp(9), dp(12), dp(9));
        view.setBackground(shape(color, 14));
        return view;
    }

    private String value(String text) { return text == null || text.trim().isEmpty() ? "未设置" : text; }

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
        EditText title = input(form, "任务名称", source.getTitle(), false);
        EditText description = input(form, "描述", source.getDescription(), false);
        EditText category = input(form, "类别：Study / Work / Life", source.getCategory(), false);
        EditText type = input(form, "任务类型", source.getTaskType(), false);
        EditText estimate = input(form, "预估分钟数", String.valueOf(source.getEstimatedDuration()), true);
        EditText difficulty = input(form, "难度 1–5", String.valueOf(source.getDifficulty()), true);
        EditText importance = input(form, "重要度 1–5", String.valueOf(source.getImportance()), true);
        String deadlineValue = source.getDeadline() > 0
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(source.getDeadline())) : "";
        EditText deadline = input(form, "截止时间 yyyy-MM-dd HH:mm；留空为无", deadlineValue, false);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("编辑任务信息")
                .setView(scroll).setNegativeButton("取消", null)
                .setPositiveButton("保存", null).create();
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
                    throw new IllegalArgumentException("请填写名称、正数时长、1–5 的难度与重要度，以及 Study / Work / Life 类别");
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
                    throw new IllegalArgumentException("截止时间不能早于已安排的任务时段");
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
                            if (rows == null || rows == 0) { showEditError("任务未找到，未保存"); return; }
                            dialog.dismiss();
                            AutoScheduler.regenerateToday(new AutoScheduler.Listener() {
                                @Override public void onScheduled(ScheduleGenerator.Result result) { if (!isFinishing()) loadData(); }
                                @Override public void onFailed(String error) {
                                    if (isFinishing()) return;
                                    Toast.makeText(SchedulePreviewActivity.this, error, Toast.LENGTH_LONG).show();
                                    loadData();
                                }
                            });
                        }
                        @Override public void onError(Exception error) { showEditError("保存失败：" + error.getMessage()); }
                    });
                }
            } catch (NumberFormatException error) {
                showEditError("分钟数、难度和重要度必须是数字");
            } catch (ParseException error) {
                showEditError("截止时间格式应为 yyyy-MM-dd HH:mm");
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
        body.addView(label("调整安排时间", 21, INK, true));
        spacerIn(body, 7);
        body.addView(label("直接输入 24 小时制时间，保存后将固定这一时段。", 13, MUTED, false));
        spacerIn(body, 18);
        EditText startField = timeInput("开始时间 HH:mm", clock(item.getStartTime()));
        EditText endField = timeInput("结束时间 HH:mm", clock(item.getEndTime()));
        body.addView(label("开始时间", 12, MUTED, false));
        spacerIn(body, 6);
        body.addView(startField, new LinearLayout.LayoutParams(-1, dp(50)));
        spacerIn(body, 14);
        body.addView(label("结束时间", 12, MUTED, false));
        spacerIn(body, 6);
        body.addView(endField, new LinearLayout.LayoutParams(-1, dp(50)));
        spacerIn(body, 19);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(body).create();
        TextView save = detailAction("保存时间", MINT);
        body.addView(save);
        save.setOnClickListener(v -> {
            int startMinute, endMinute;
            try {
                startMinute = parseClockMinute(startField.getText().toString().trim(), false);
                endMinute = parseClockMinute(endField.getText().toString().trim(), true);
            } catch (IllegalArgumentException error) { showEditError(error.getMessage()); return; }
            long start = atDayTime(startMinute / 60, startMinute % 60);
            long end = atDayTime(endMinute / 60, endMinute % 60);
            if (start >= end) { showEditError("结束时间必须晚于开始时间"); return; }
            if (task.getDeadline() > 0 && end > task.getDeadline()) {
                showEditError("结束时间不能晚于任务截止时间"); return;
            }
            int lockedMinutes = (int) ((end - start) / 60_000L);
            for (ScheduleItem other : schedule) {
                if (other != item && other.getTaskId() == task.getTaskId() && other.isManuallyEdited()) {
                    lockedMinutes += (int) ((other.getEndTime() - other.getStartTime()) / 60_000L);
                }
            }
            int plannedMinutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
            if (plannedMinutes > 0 && lockedMinutes > plannedMinutes) {
                showEditError("手动固定的总时间不能超过任务预计时长"); return;
            }
            for (ScheduleItem other : schedule) {
                if (other == item || (item.getScheduleId() > 0 && other.getScheduleId() == item.getScheduleId())) continue;
                if (start < other.getEndTime() && other.getStartTime() < end) {
                    showEditError("这个时间与另一项日程重叠"); return;
                }
            }
            if (item.getScheduleId() <= 0) {
                item.setStartTime(start); item.setEndTime(end); item.setManuallyEdited(true);
                dialog.dismiss(); render();
            } else {
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(selectedDayMillis));
                RepositoryProvider.getScheduleRepository().updatePlannedTime(item.getScheduleId(), date, start, end,
                        new TaskRepository.Callback<Integer>() {
                            @Override public void onSuccess(Integer rows) { dialog.dismiss(); loadData(); }
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
        field.setBackground(shape(BG, 12));
        field.setSelectAllOnFocus(true);
        return field;
    }

    private int parseClockMinute(String text, boolean allowEndOfDay) {
        if (allowEndOfDay && "24:00".equals(text)) return 24 * 60;
        if (!text.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException("时间请按 HH:mm 输入，例如 09:30");
        }
        return Integer.parseInt(text.substring(0, 2)) * 60 + Integer.parseInt(text.substring(3, 5));
    }

    private void showEditError(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("好的", null).show();
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

package com.tempo.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.scheduler.AutoScheduler;
import com.tempo.app.scheduler.ScheduleGenerator;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Date;
import java.util.Locale;

/** Collects task conditions before showing schedule recommendations from the schedule repository. */
public class AddTaskActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(247, 248, 245);
    private static final int INK = Color.rgb(30, 48, 52);
    private static final int MUTED = Color.rgb(105, 120, 119);
    private static final int MINT = Color.rgb(194, 232, 213);
    private static final int BORDER = Color.rgb(226, 232, 227);
    private long selectedDayMillis;
    private EditText title, description, estimate, deadline;
    private TextView save;
    private LinearLayout page;
    private int editingTaskId = -1;
    private Task sourceTask;
    private LinearLayout categoryRow, difficultyRow, importanceRow;
    private String category = "Study";
    private int difficulty = 3, importance = 3;
    private String scheduleNote;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        selectedDayMillis = getIntent().getLongExtra("selectedDayMillis", System.currentTimeMillis());
        editingTaskId = getIntent().getIntExtra("taskId", -1);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        setContentView(page);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(14), dp(20), dp(4));
        BackChevronView back = new BackChevronView(this, "返回日程");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView heading = text(editingTaskId > 0 ? "编辑任务" : "新建任务", 14, MUTED, false);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setIncludeFontPadding(false);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        headingParams.leftMargin = -dp(8); header.addView(heading, headingParams);
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(16), dp(22), dp(24));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView date = text(editingTaskId > 0 ? "任务信息" : "第 1 步 · 填写任务条件",
                13, Color.rgb(52, 126, 91), true);
        body.addView(date);
        space(body, 8);
        body.addView(text(editingTaskId > 0 ? "调整任务条件" : "先告诉我们任务是什么", 26, INK, true));
        space(body, 6);
        body.addView(text("排程会参考截止时间、重要程度和难度安排合适的时段。", 13, MUTED, false));
        space(body, 26);

        section(body, "任务内容");
        LinearLayout contentCard = card();
        title = edit("任务名称", "", false);
        title.setTextSize(18);
        contentCard.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));
        rule(contentCard);
        description = edit("补充描述（可选）", "", false);
        contentCard.addView(description, new LinearLayout.LayoutParams(-1, dp(48)));
        body.addView(contentCard);
        space(body, 20);

        section(body, "任务类别");
        categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(categoryRow);
        renderCategories();
        space(body, 20);

        section(body, "完成条件");
        LinearLayout timeCard = card();
        LinearLayout estimateRow = new LinearLayout(this);
        estimateRow.setGravity(Gravity.CENTER_VERTICAL);
        estimateRow.addView(text("预计任务时长", 14, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        estimate = edit("分钟", "60", true);
        estimate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        estimateRow.addView(estimate, new LinearLayout.LayoutParams(dp(76), dp(48)));
        estimateRow.addView(text("分钟", 14, MUTED, false));
        timeCard.addView(estimateRow);
        rule(timeCard);
        TextView dueLabel = text("截止时间", 14, MUTED, false);
        dueLabel.setPadding(0, dp(13), 0, dp(4));
        timeCard.addView(dueLabel);
        deadline = edit("例如 2026-11-20 23:59（可留空）", "", false);
        deadline.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL);
        timeCard.addView(deadline, new LinearLayout.LayoutParams(-1, dp(48)));
        body.addView(timeCard);
        space(body, 20);

        section(body, "排程参考");
        LinearLayout weightCard = card();
        weightCard.addView(text("难度", 14, MUTED, false));
        weightCard.addView(text("完成它有多费力；难度高的任务优先匹配高效时段。", 12, MUTED, false));
        space(weightCard, 8);
        difficultyRow = new LinearLayout(this); weightCard.addView(difficultyRow);
        space(weightCard, 17);
        weightCard.addView(text("重要程度", 14, MUTED, false));
        weightCard.addView(text("它对你的目标有多重要；会与截止紧迫度一起决定优先级。", 12, MUTED, false));
        space(weightCard, 8);
        importanceRow = new LinearLayout(this); weightCard.addView(importanceRow);
        body.addView(weightCard);
        renderScale(difficultyRow, difficulty, true);
        renderScale(importanceRow, importance, false);
        space(body, 16);
        body.addView(text("保存后先查看系统推荐时间，再到日程中调整。", 12, MUTED, false));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(22), dp(10), dp(22), dp(14));
        footer.setBackgroundColor(BG);
        save = text(editingTaskId > 0 ? "保存任务信息" : "保存并查看推荐", 16, INK, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(shape(MINT, 18));
        save.setOnClickListener(v -> saveTask());
        footer.addView(save, new LinearLayout.LayoutParams(-1, dp(54)));
        page.addView(footer);
        if (editingTaskId > 0) loadTask();
    }

    private void renderCategories() {
        categoryRow.removeAllViews();
        String[] keys = {"Study", "Work", "Life"};
        String[] names = {"学习", "工作", "生活"};
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            TextView choice = text(names[i], 14, INK, category.equals(key));
            choice.setGravity(Gravity.CENTER);
            choice.setBackground(shape(category.equals(key) ? MINT : Color.WHITE, 14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
            if (i > 0) params.leftMargin = dp(9);
            categoryRow.addView(choice, params);
            choice.setOnClickListener(v -> { category = key; renderCategories(); });
        }
    }

    private void renderScale(LinearLayout row, int selected, boolean isDifficulty) {
        row.removeAllViews();
        for (int value = 1; value <= 5; value++) {
            final int score = value;
            TextView choice = text(String.valueOf(value), 14, INK, value == selected);
            choice.setGravity(Gravity.CENTER);
            choice.setBackground(shape(value == selected ? MINT : BG, 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (value > 1) params.leftMargin = dp(7);
            row.addView(choice, params);
            choice.setOnClickListener(v -> {
                if (isDifficulty) difficulty = score; else importance = score;
                renderScale(row, score, isDifficulty);
            });
        }
    }

    private void loadTask() {
        RepositoryProvider.getTaskRepository().getTaskById(editingTaskId, new TaskRepository.Callback<Task>() {
            @Override public void onSuccess(Task task) {
                if (task == null) { message("任务不存在"); finish(); return; }
                sourceTask = task;
                title.setText(task.getTitle());
                description.setText(task.getDescription());
                estimate.setText(String.valueOf(task.getEstimatedDuration()));
                deadline.setText(task.getDeadline() > 0
                        ? new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(task.getDeadline())) : "");
                category = task.getCategory(); difficulty = task.getDifficulty(); importance = task.getImportance();
                renderCategories(); renderScale(difficultyRow, difficulty, true);
                renderScale(importanceRow, importance, false);
            }
            @Override public void onError(Exception error) { message("读取任务失败：" + error.getMessage()); finish(); }
        });
    }

    private void saveTask() {
        String name = title.getText().toString().trim();
        int expected;
        try { expected = Integer.parseInt(estimate.getText().toString().trim()); }
        catch (NumberFormatException error) { message("请填写预计任务时长"); return; }
        if (name.isEmpty()) { title.setError("请填写任务名称"); title.requestFocus(); return; }
        if (expected <= 0) { message("预计任务时长必须大于 0"); return; }
        long due = 0;
        String dueText = deadline.getText().toString().trim();
        if (!dueText.isEmpty()) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            format.setLenient(false);
            try {
                Date parsed = format.parse(dueText);
                if (parsed == null || !dueText.equals(format.format(parsed))) throw new ParseException("Invalid date", 0);
                due = parsed.getTime();
            } catch (ParseException error) { deadline.setError("请按 yyyy-MM-dd HH:mm 输入"); deadline.requestFocus(); return; }
        }
        Task task = new Task(name, category, expected, difficulty, importance, due);
        task.setDescription(description.getText().toString().trim());
        save.setEnabled(false); save.setText("正在保存…");
        if (editingTaskId > 0) {
            if (sourceTask == null) { message("任务仍在加载，请稍候"); resetSave(); return; }
            task.setTaskId(editingTaskId);
            task.setTaskType(sourceTask.getTaskType());
            task.setPredictedDuration(sourceTask.getPredictedDuration());
            task.setStatus(sourceTask.getStatus());
            task.setCreatedAt(sourceTask.getCreatedAt());
            RepositoryProvider.getTaskRepository().updateTask(task, new TaskRepository.Callback<Integer>() {
                @Override public void onSuccess(Integer rows) {
                    if (rows == null || rows == 0) { message("任务未找到"); resetSave(); return; }
                    setResult(RESULT_OK);
                    save.setText("Updating schedule…");
                    AutoScheduler.regenerateToday(new AutoScheduler.Listener() {
                        @Override public void onScheduled(ScheduleGenerator.Result result) { if (!isFinishing()) finish(); }
                        @Override public void onFailed(String error) { message(error); if (!isFinishing()) finish(); }
                    });
                }
                @Override public void onError(Exception error) { message("保存失败：" + error.getMessage()); resetSave(); }
            });
        } else {
            RepositoryProvider.getTaskRepository().addTask(task, new TaskRepository.Callback<Long>() {
                @Override public void onSuccess(Long id) {
                    if (id == null || id <= 0 || id > Integer.MAX_VALUE) { message("任务保存失败"); resetSave(); return; }
                    setResult(RESULT_OK);
                    save.setText("Scheduling…");
                    scheduleAndShow((int) (long) id, task.getTitle());
                }
                @Override public void onError(Exception error) { message("保存失败：" + error.getMessage()); resetSave(); }
            });
        }
    }

    private void resetSave() {
        save.setEnabled(true);
        save.setText(editingTaskId > 0 ? "保存任务信息" : "保存并查看推荐");
    }

    /** After saving, runs the scheduler for today, then reads the recommended slots back. */
    private void scheduleAndShow(int taskId, String taskTitle) {
        AutoScheduler.regenerateToday(new AutoScheduler.Listener() {
            @Override public void onScheduled(ScheduleGenerator.Result result) {
                if (isFinishing() || isDestroyed()) return;
                int left = AutoScheduler.remainingMinutes(result, taskId);
                scheduleNote = left > 0 ? "Not enough time left today: " + left + " min could not be scheduled." : null;
                showRecommendation(taskId, taskTitle);
            }
            @Override public void onFailed(String error) {
                if (isFinishing() || isDestroyed()) return;
                scheduleNote = error;
                showRecommendation(taskId, taskTitle);
            }
        });
    }

    private void showRecommendation(int taskId, String taskTitle) {
        RepositoryProvider.getTaskRepository().getTaskById(taskId, new TaskRepository.Callback<Task>() {
            @Override public void onSuccess(Task task) {
                if (task == null) { message("任务未找到"); return; }
                RepositoryProvider.getScheduleRepository().getScheduleByTaskId(taskId,
                        new TaskRepository.Callback<List<ScheduleItem>>() {
                            @Override public void onSuccess(List<ScheduleItem> rows) { renderRecommendation(task, rows); }
                            @Override public void onError(Exception error) { message("读取推荐时间失败"); renderRecommendation(task, null); }
                        });
            }
            @Override public void onError(Exception error) { message("读取任务失败"); }
        });
    }

    private void renderRecommendation(Task task, List<ScheduleItem> rows) {
        page.removeAllViews();
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(32), dp(24), dp(24));
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(text("第 2 步 · 推荐排程", 13, Color.rgb(52, 126, 91), true));
        space(body, 10);
        body.addView(text("任务已保存", 28, INK, true));
        space(body, 8);
        body.addView(text(task.getTitle(), 17, MUTED, false));
        space(body, 28);
        LinearLayout card = card();
        int plannedMinutes = 0;
        if (rows != null) for (ScheduleItem item : rows) plannedMinutes += Math.max(0,
                (int) ((item.getEndTime() - item.getStartTime()) / 60_000L));
        int expectedMinutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
        card.addView(text("预计完成用时  " + expectedMinutes + " 分钟"
                + (task.getPredictedDuration() > 0 ? " · 系统预测" : " · 用户预估"), 15, INK, true));
        space(card, 9);
        card.addView(text(rows == null || rows.isEmpty() ? "已安排时间  等待排程"
                : "已安排时间  " + plannedMinutes + " 分钟", 14, MUTED, false));
        space(card, 17);
        rule(card);
        space(card, 17);
        if (rows == null || rows.isEmpty()) {
            card.addView(text("等待推荐时间", 20, INK, true));
            space(card, 10);
            card.addView(text(scheduleNote != null ? scheduleNote
                    : "排程生成后，这里会显示建议时段。返回日程后可查看已安排的任务。", 14, MUTED, false));
        } else {
            card.addView(text("建议时段", 20, INK, true));
            space(card, 12);
            SimpleDateFormat dateFormat = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA);
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
            for (ScheduleItem item : rows) {
                card.addView(text(dateFormat.format(new Date(item.getStartTime())) + " – "
                        + timeFormat.format(new Date(item.getEndTime())), 15, INK, true));
                space(card, 10);
            }
            if (scheduleNote != null) {
                card.addView(text(scheduleNote, 13, MUTED, false));
                space(card, 6);
            }
            card.addView(text("返回日程，点击任务即可调整时段。", 13, MUTED, false));
        }
        body.addView(card);
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(22), dp(10), dp(22), dp(20));
        if (rows == null || rows.isEmpty()) {
            TextView retry = text("刷新推荐", 14, Color.rgb(52, 126, 91), true);
            retry.setGravity(Gravity.CENTER);
            retry.setOnClickListener(v -> scheduleAndShow(task.getTaskId(), task.getTitle()));
            footer.addView(retry, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        TextView done = text("返回日程", 16, INK, true);
        done.setGravity(Gravity.CENTER); done.setBackground(shape(MINT, 18));
        done.setOnClickListener(v -> finish());
        footer.addView(done, new LinearLayout.LayoutParams(-1, dp(54)));
        page.addView(footer);
    }
    private void section(LinearLayout parent, String value) {
        TextView label = text(value, 13, MUTED, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10); parent.addView(label, params);
    }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(12), dp(17), dp(12)); card.setBackground(shape(Color.WHITE, 18));
        return card;
    }
    private EditText edit(String hint, String value, boolean numeric) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint(hint);
        input.setText(value); input.setTextSize(15); input.setTextColor(INK); input.setHintTextColor(MUTED);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        return input;
    }
    private void rule(LinearLayout parent) {
        View rule = new View(this); rule.setBackgroundColor(BORDER);
        parent.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private void space(LinearLayout parent, int size) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size))); }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

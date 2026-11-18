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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.learning.LearningRepository;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.learning.TaskTypes;
import com.tempo.app.model.Task;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.scheduler.AutoScheduler;
import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.scheduler.ScheduleService;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Date;
import java.util.Locale;

/** Collects task conditions before showing schedule recommendations from the schedule repository. */
public class AddTaskActivity extends AppCompatActivity {
    private static final int EDIT_ROUTINE = 71;
    private int taskAwaitingRoutine = -1;
    private String taskAwaitingRoutineTitle;
    private final int BG = com.tempo.app.ui.Ui.BG;
    private final int INK = com.tempo.app.ui.Ui.INK;
    private final int MUTED = com.tempo.app.ui.Ui.MUTED;
    private final int MINT = com.tempo.app.ui.Ui.MINT;
    private final int BORDER = com.tempo.app.ui.Ui.LINE;
    private long selectedDayMillis;
    private EditText title, description, estimate, deadline;
    private TextView save;
    private LinearLayout page;
    private int editingTaskId = -1;
    private Task sourceTask;
    private LearningRepository learning;
    private Spinner taskType;
    private Bundle draftState;
    private LinearLayout categoryRow, difficultyRow, importanceRow;
    private String category = com.tempo.app.ui.Categories.first();
    private int difficulty = 3, importance = 3;
    private String scheduleNote;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        draftState=state;
        RepositoryProvider.init(this);
        learning = new LearningRepository(this);
        selectedDayMillis = getIntent().getLongExtra("selectedDayMillis", System.currentTimeMillis());
        editingTaskId = getIntent().getIntExtra("taskId", -1);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(com.tempo.app.ui.Ui.isDark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        setContentView(page);
        com.tempo.app.ui.Ui.fitSystemBars(this);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(14), dp(20), dp(4));
        BackChevronView back = new BackChevronView(this, "Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView heading = text("Back", 14, MUTED, false);
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

        TextView date = text(editingTaskId > 0 ? "TASK DETAILS" : "STEP 1 · DESCRIBE THE TASK",
                13, com.tempo.app.ui.Ui.GREEN, true);
        body.addView(date);
        space(body, 8);
        body.addView(text(editingTaskId > 0 ? "Edit task" : "New task", 26, INK, true));
        space(body, 6);
        body.addView(text("Tempo uses the deadline, importance and difficulty to find the right time.", 13, MUTED, false));
        space(body, 26);

        section(body, "WHAT");
        LinearLayout contentCard = card();
        title = edit("Task name", "", false);
        title.setTextSize(18);
        contentCard.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));
        rule(contentCard);
        description = edit("Notes (optional)", "", false);
        contentCard.addView(description, new LinearLayout.LayoutParams(-1, dp(48)));
        body.addView(contentCard);
        space(body, 20);

        // "+ New category" sits on the section header, so it is visible however many categories exist.
        LinearLayout categoryHeader = new LinearLayout(this);
        categoryHeader.setGravity(Gravity.CENTER_VERTICAL);
        section(categoryHeader, "CATEGORY");
        ((LinearLayout.LayoutParams) categoryHeader.getChildAt(0).getLayoutParams()).width = 0;
        ((LinearLayout.LayoutParams) categoryHeader.getChildAt(0).getLayoutParams()).weight = 1;
        ((LinearLayout.LayoutParams) categoryHeader.getChildAt(0).getLayoutParams()).bottomMargin = 0;
        TextView addCategory = text("+ New category", 13, com.tempo.app.ui.Ui.GREEN, true);
        addCategory.setGravity(Gravity.CENTER);
        addCategory.setMinHeight(dp(40));
        addCategory.setPadding(dp(10), 0, 0, 0);
        addCategory.setContentDescription("New category");
        addCategory.setOnClickListener(v -> com.tempo.app.ui.CategoryDialogs.create(this, name -> {
            category = name; renderTypes(null); renderCategories();
        }));
        categoryHeader.addView(addCategory);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.bottomMargin = dp(4);
        body.addView(categoryHeader, headerParams);
        categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView categoryScroll = new android.widget.HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryScroll.setClipToPadding(false);
        categoryScroll.addView(categoryRow);
        body.addView(categoryScroll);
        renderCategories();
        space(body,12);
        body.addView(text("Task type · optional",13,MUTED,true));
        taskType=new Spinner(this);body.addView(taskType,new LinearLayout.LayoutParams(-1,dp(48)));
        renderTypes(null);
        body.addView(text("Helps Tempo compare tasks of the same type.",12,MUTED,false));
        space(body, 20);

        section(body, "TIME");
        LinearLayout timeCard = card();
        LinearLayout estimateRow = new LinearLayout(this);
        estimateRow.setGravity(Gravity.CENTER_VERTICAL);
        estimateRow.addView(text("Estimated duration", 14, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        estimate = edit("min", "60", true);
        estimate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        estimateRow.addView(estimate, new LinearLayout.LayoutParams(dp(76), dp(48)));
        estimateRow.addView(text("min", 14, MUTED, false));
        timeCard.addView(estimateRow);
        rule(timeCard);
        TextView dueLabel = text("Deadline", 14, MUTED, false);
        dueLabel.setPadding(0, dp(13), 0, dp(4));
        timeCard.addView(dueLabel);
        deadline = edit("Tap to choose date and time (optional)", "", false);
        deadline.setFocusable(false);
        deadline.setOnClickListener(v -> WheelPickers.pickDeadline(this, deadline));
        timeCard.addView(deadline, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView clearDeadline = text("No deadline · clear", 12, MUTED, false);
        clearDeadline.setOnClickListener(v -> deadline.setText(""));
        timeCard.addView(clearDeadline);
        body.addView(timeCard);
        space(body, 20);

        section(body, "PRIORITY");
        LinearLayout weightCard = card();
        weightCard.addView(text("Difficulty", 14, MUTED, false));
        weightCard.addView(text("How demanding it is. Harder tasks go into your best focus time.", 12, MUTED, false));
        space(weightCard, 8);
        difficultyRow = new LinearLayout(this); weightCard.addView(difficultyRow);
        space(weightCard, 17);
        weightCard.addView(text("Importance", 14, MUTED, false));
        weightCard.addView(text("How much it matters to your goals. Together with the deadline it sets the priority.", 12, MUTED, false));
        space(weightCard, 8);
        importanceRow = new LinearLayout(this); weightCard.addView(importanceRow);
        body.addView(weightCard);
        renderScale(difficultyRow, difficulty, true);
        renderScale(importanceRow, importance, false);
        space(body, 16);
        body.addView(text("After saving you will see the suggested time, which you can adjust in the schedule.", 12, MUTED, false));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(22), dp(10), dp(22), dp(14));
        footer.setBackgroundColor(BG);
        // App-wide primary action: dark button with white text.
        save = text(editingTaskId > 0 ? "Save changes" : "Save and plan", 16, Color.WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(shape(com.tempo.app.ui.Ui.ACTION, 16));
        save.setOnClickListener(v -> saveTask());
        footer.addView(save, new LinearLayout.LayoutParams(-1, dp(54)));
        page.addView(footer);
        if (editingTaskId > 0) loadTask(); else restoreDraft();
    }

    private void renderCategories() {
        categoryRow.removeAllViews();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (com.tempo.app.model.Category c : com.tempo.app.ui.Categories.all()) names.add(c.getName());
        // A task may use a category that no longer exists in the list (e.g. old data): keep it selectable.
        if (com.tempo.app.ui.Categories.find(category) == null && category != null && !category.trim().isEmpty()) names.add(category);
        for (String key : names) {
            boolean selected = key.equalsIgnoreCase(category);
            int color = com.tempo.app.ui.Categories.color(key);
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(14), 0, dp(16), 0);
            GradientDrawable bg = shape(selected ? com.tempo.app.ui.Ui.soften(color) : com.tempo.app.ui.Ui.CARD, 14);
            if (selected) bg.setStroke(dp(2), color);
            chip.setBackground(bg);
            View dot = new View(this);
            GradientDrawable dotShape = new GradientDrawable();
            dotShape.setShape(GradientDrawable.OVAL);
            dotShape.setColor(color);
            dot.setBackground(dotShape);
            chip.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
            TextView label = text(com.tempo.app.ui.Categories.name(key), 14, INK, selected);
            label.setPadding(dp(8), 0, 0, 0);
            chip.addView(label);
            chip.setContentDescription(key + (selected ? ", selected" : ""));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(46));
            params.rightMargin = dp(9);
            categoryRow.addView(chip, params);
            chip.setOnClickListener(v -> { if (!key.equals(category)) { category = key; renderTypes(null); } renderCategories(); });
        }
    }

    private void renderTypes(String existing) {
        if(taskType==null)return;
        List<String> options=TaskTypes.options(category,existing);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);taskType.setAdapter(adapter);
        taskType.setSelection(existing==null?0:Math.max(0,options.indexOf(existing.trim())));
    }

    private String selectedType() {return taskType.getSelectedItemPosition()==0?null:taskType.getSelectedItem().toString();}

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
                if (task == null) { message("This task no longer exists."); finish(); return; }
                sourceTask = task;
                title.setText(task.getTitle());
                description.setText(task.getDescription());
                estimate.setText(String.valueOf(task.getEstimatedDuration()));
                deadline.setText(task.getDeadline() > 0
                        ? new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(task.getDeadline())) : "");
                category = task.getCategory(); difficulty = task.getDifficulty(); importance = task.getImportance();
                renderTypes(task.getTaskType());
                renderCategories(); renderScale(difficultyRow, difficulty, true);
                renderScale(importanceRow, importance, false);
                restoreDraft();
            }
            @Override public void onError(Exception error) { message("Could not read the task: " + error.getMessage()); finish(); }
        });
    }

    private void restoreDraft() {
        if(draftState==null)return;
        title.setText(draftState.getString("draftTitle",title.getText().toString()));
        description.setText(draftState.getString("draftDescription",description.getText().toString()));
        estimate.setText(draftState.getString("draftEstimate",estimate.getText().toString()));
        deadline.setText(draftState.getString("draftDeadline",deadline.getText().toString()));
        category=draftState.getString("draftCategory",category);difficulty=draftState.getInt("draftDifficulty",difficulty);importance=draftState.getInt("draftImportance",importance);
        renderCategories();renderTypes(draftState.getString("draftType",null));renderScale(difficultyRow,difficulty,true);renderScale(importanceRow,importance,false);
        draftState=null;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("draftTitle",title.getText().toString());out.putString("draftDescription",description.getText().toString());
        out.putString("draftEstimate",estimate.getText().toString());out.putString("draftDeadline",deadline.getText().toString());
        out.putString("draftCategory",category);out.putString("draftType",selectedType());out.putInt("draftDifficulty",difficulty);out.putInt("draftImportance",importance);
        super.onSaveInstanceState(out);
    }

    private void saveTask() {
        String name = title.getText().toString().trim();
        int expected;
        try { expected = Integer.parseInt(estimate.getText().toString().trim()); }
        catch (NumberFormatException error) { message("Enter the estimated duration."); return; }
        if (name.isEmpty()) { title.setError("Enter a task name"); title.requestFocus(); return; }
        if (expected <= 0) { message("The estimated duration must be above 0."); return; }
        long due = 0;
        String dueText = deadline.getText().toString().trim();
        if (!dueText.isEmpty()) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            format.setLenient(false);
            try {
                Date parsed = format.parse(dueText);
                if (parsed == null || !dueText.equals(format.format(parsed))) throw new ParseException("Invalid date", 0);
                due = parsed.getTime();
            } catch (ParseException error) { deadline.setError("Use yyyy-MM-dd HH:mm"); deadline.requestFocus(); return; }
        }
        Task task = new Task(name, category, expected, difficulty, importance, due);
        task.setTaskType(selectedType());
        task.setDescription(description.getText().toString().trim());
        save.setEnabled(false); save.setText("Saving…");
        if (editingTaskId > 0) {
            if (sourceTask == null) { message("The task is still loading. Please wait."); resetSave(); return; }
            task.setTaskId(editingTaskId);
            task.setPredictedDuration(sourceTask.getPredictedDuration());
            task.setStatus(sourceTask.getStatus());
            task.setCreatedAt(sourceTask.getCreatedAt());
            learning.editContext(editingTaskId,new TaskRepository.Callback<LearningStore.EditContext>() {
                public void onSuccess(LearningStore.EditContext context) {
                    boolean changed=sourceTask.getEstimatedDuration()!=task.getEstimatedDuration()
                            || !java.util.Objects.equals(sourceTask.getCategory(),task.getCategory())
                            || !java.util.Objects.equals(normalType(sourceTask.getTaskType()),normalType(task.getTaskType()));
                    if(changed && getSharedPreferences("tempo_focus_timer",MODE_PRIVATE).getInt("taskId",-1)==editingTaskId) {
                        message("Finish the active timer before changing this task's estimate, category or type.");resetSave();return;
                    }
                    if(context.started && changed) confirmStartedEdit(task,context); else persistEdit(task,null);
                }
                public void onError(Exception error) {message(error.getMessage());resetSave();}
            });
        } else {
            RepositoryProvider.getTaskRepository().addTask(task, new TaskRepository.Callback<Long>() {
                @Override public void onSuccess(Long id) {
                    if (id == null || id <= 0 || id > Integer.MAX_VALUE) { message("Could not save the task."); resetSave(); return; }
                    setResult(RESULT_OK);
                    save.setText("Scheduling…");
                    refreshLearning(() -> scheduleAndShow((int) (long) id, task.getTitle()));
                }
                @Override public void onError(Exception error) { message("Could not save: " + error.getMessage()); resetSave(); }
            });
        }
    }

    private static String normalType(String type){return type==null?"":type.trim();}

    private void confirmStartedEdit(Task task,LearningStore.EditContext context) {
        boolean completed="COMPLETED".equals(sourceTask.getStatus());
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(12),dp(22),dp(8));
        form.addView(text("The original prediction stays in your history. Changing the estimate, category or type excludes this task from duration learning and accuracy comparisons.",14,INK,false));
        EditText left=edit("Remaining minutes", "",true);
        if(!completed) {
            space(form,16);form.addView(text("How many minutes are still needed?",14,INK,true));
            form.addView(left);form.addView(text("Previously recorded: "+context.recorded+" min. Enter the remaining work, not the whole task duration.",12,MUTED,false));
        }
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Update a started task").setView(form)
                .setNegativeButton("Cancel",(d,w)->resetSave()).setPositiveButton("Save changes",null).create();
        dialog.setOnCancelListener(d->resetSave());
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Integer remaining=null;
            if(!completed) try {
                remaining=Integer.valueOf(left.getText().toString().trim());
                if(remaining<1||remaining>10080) throw new NumberFormatException();
            }catch(NumberFormatException error){left.setError("Enter 1–10080 remaining minutes.");return;}
            dialog.dismiss();persistEdit(task,remaining);
        }));
        dialog.show();
    }

    private void persistEdit(Task task,Integer remaining) {
        learning.updateTask(task,remaining,new TaskRepository.Callback<Integer>() {
            public void onSuccess(Integer rows) {
                if(rows==null||rows==0){message("Task not found.");resetSave();return;}
                setResult(RESULT_OK);
                save.setText("Updating schedule…");
                AutoScheduler.replan(AddTaskActivity.this, new AutoScheduler.Listener() {
                    @Override public void onScheduled(ScheduleGenerator.Result result) { if (!isFinishing()) finish(); }
                    @Override public void onFailed(String error) { message(error); if (!isFinishing()) finish(); }
                });
            }
            public void onError(Exception error) {message(error.getMessage());resetSave();}
        });
    }

    @Override protected void onDestroy() { if (learning != null) learning.close(); super.onDestroy(); }

    private void refreshLearning(Runnable next) {
        learning.refresh(new TaskRepository.Callback<Void>() {
            @Override public void onSuccess(Void ignored) { if (!isFinishing() && !isDestroyed()) next.run(); }
            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                message("Task saved, but the estimate could not be updated. It will retry when planning."); next.run();
            }
        });
    }

    private void resetSave() {
        save.setEnabled(true);
        save.setText(editingTaskId > 0 ? "Save changes" : "Save and plan");
    }

    /** After saving, runs the scheduler for today, then reads the recommended slots back. */
    private void scheduleAndShow(int taskId, String taskTitle) {
        AutoScheduler.replan(AddTaskActivity.this, new AutoScheduler.Listener() {
            @Override public void onScheduled(ScheduleGenerator.Result result) {
                if (isFinishing() || isDestroyed()) return;
                int left = AutoScheduler.remainingMinutes(result, taskId);
                scheduleNote = left > 0 ? "Not enough free time in the next " + ScheduleService.HORIZON_DAYS + " days: " + left + " min could not be planned." : null;
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
                if (task == null) { message("Task not found."); return; }
                RepositoryProvider.getScheduleRepository().getScheduleByTaskId(taskId,
                        new TaskRepository.Callback<List<ScheduleItem>>() {
                            @Override public void onSuccess(List<ScheduleItem> rows) { renderRecommendation(task, rows); }
                            @Override public void onError(Exception error) { message("Could not read the suggested time."); renderRecommendation(task, null); }
                        });
            }
            @Override public void onError(Exception error) { message("Could not read the task."); }
        });
    }

    private void renderRecommendation(Task task, List<ScheduleItem> rows) {
        if(rows!=null) {List<ScheduleItem> active=new java.util.ArrayList<>();for(ScheduleItem item:rows)if(!"CANCELLED".equals(item.getStatus()))active.add(item);rows=active;}
        page.removeAllViews();
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(32), dp(24), dp(24));
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(text("STEP 2 · SUGGESTED TIME", 11, com.tempo.app.ui.Ui.GREEN, true));
        space(body, 10);
        body.addView(text(rows == null || rows.isEmpty() ? "Task saved · not scheduled" : "Task scheduled", 28, INK, true));
        space(body, 8);
        body.addView(text(task.getTitle(), 17, MUTED, false));
        space(body, 28);
        LinearLayout card = card();
        int plannedMinutes = 0;
        if (rows != null) for (ScheduleItem item : rows) plannedMinutes += Math.max(0,
                (int) ((item.getEndTime() - item.getStartTime()) / 60_000L));
        int expectedMinutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
        card.addView(text("Expected  " + expectedMinutes + " min"
                + (task.getPredictedDuration() > 0 ? " · Tempo prediction" : " · your estimate"), 15, INK, true));
        space(card, 9);
        card.addView(text(rows == null || rows.isEmpty() ? "Planned  0 min"
                : "Planned  " + plannedMinutes + " min", 14, MUTED, false));
        space(card, 17);
        rule(card);
        space(card, 17);
        if (rows == null || rows.isEmpty()) {
            card.addView(text("No time slot yet", 20, INK, true));
            space(card, 10);
            card.addView(text(scheduleNote != null ? scheduleNote
                    : "The task is saved, but it has no scheduled time. Check your routine and deadline, then try planning again. It will not appear on the schedule until a slot is created.", 14, MUTED, false));
        } else {
            card.addView(text("Suggested slots", 20, INK, true));
            space(card, 12);
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d · HH:mm", Locale.US);
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
            card.addView(text("Open the schedule and tap the task to adjust a slot.", 13, MUTED, false));
        }
        body.addView(card);
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(22), dp(10), dp(22), dp(20));
        if (rows == null || rows.isEmpty()) {
            if (AutoScheduler.NO_PROFILE.equals(scheduleNote)) {
                TextView profile = text("Set up routine", 14, com.tempo.app.ui.Ui.GREEN, true);
                profile.setGravity(Gravity.CENTER);
                profile.setOnClickListener(v -> {
                    taskAwaitingRoutine = task.getTaskId();
                    taskAwaitingRoutineTitle = task.getTitle();
                    startActivityForResult(new Intent(this, ProfileSettingsActivity.class), EDIT_ROUTINE);
                });
                footer.addView(profile, new LinearLayout.LayoutParams(-1, dp(42)));
            }
            TextView retry = text("Plan again", 14, com.tempo.app.ui.Ui.GREEN, true);
            retry.setGravity(Gravity.CENTER);
            retry.setOnClickListener(v -> scheduleAndShow(task.getTaskId(), task.getTitle()));
            footer.addView(retry, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        TextView done = text(rows == null || rows.isEmpty() ? "Close · task saved" : "View on schedule", 16, INK, true);
        done.setGravity(Gravity.CENTER); done.setBackground(shape(MINT, 18));
        if (rows == null || rows.isEmpty()) done.setOnClickListener(v -> finish());
        else {
            long firstDay = rows.get(0).getStartTime();
            done.setOnClickListener(v -> {
                Intent schedule = new Intent(this, SchedulePreviewActivity.class);
                schedule.putExtra("selectedDayMillis", firstDay);
                startActivity(schedule);
                finish();
            });
        }
        footer.addView(done, new LinearLayout.LayoutParams(-1, dp(54)));
        page.addView(footer);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_ROUTINE && resultCode == RESULT_OK && taskAwaitingRoutine > 0) {
            scheduleNote = data == null ? null : data.getStringExtra("planningFailure");
            showRecommendation(taskAwaitingRoutine, taskAwaitingRoutineTitle);
        }
    }
    private void section(LinearLayout parent, String value) {
        // Section label in the app-wide eyebrow style.
        TextView label = text(value.toUpperCase(Locale.US), 11, com.tempo.app.ui.Ui.GREEN, true);
        label.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10); parent.addView(label, params);
    }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(12), dp(18), dp(12)); card.setBackground(shape(com.tempo.app.ui.Ui.CARD, 22));
        card.setElevation(dp(2));
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

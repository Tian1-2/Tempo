package com.tempo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tempo.app.model.Task;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.scheduler.AutoScheduler;
import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.ui.Tips;
import com.tempo.app.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Every task in one place (was a plain dialog list on the Schedule). Tasks are grouped by state
 * with a filter, show their category colour, estimate and deadline, and open a small action sheet.
 */
public class ManageTasksActivity extends Activity {
    private enum Filter { TODO, DONE, ARCHIVED }

    private final List<Task> tasks = new ArrayList<>();
    private Filter filter = Filter.TODO;
    private boolean loaded;
    private String planError;
    private LinearLayout body;
    private long selectedDayMillis;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.window(this);
        RepositoryProvider.init(this);
        selectedDayMillis = getIntent().getLongExtra("selectedDayMillis", System.currentTimeMillis());
        if (state != null) filter = Filter.values()[state.getInt("filter", 0)];

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 100));
        scroll.addView(body);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView add = Ui.primary(this, "＋  New task");
        add.setElevation(Ui.dp(this, 6));
        add.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddTaskActivity.class);
            intent.putExtra("selectedDayMillis", selectedDayMillis);
            startActivity(intent);
        });
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(-1, Ui.dp(this, 54), Gravity.BOTTOM);
        addParams.setMargins(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 16));
        root.addView(add, addParams);
        setContentView(root);
        Ui.fitSystemBars(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("filter", filter.ordinal());
        super.onSaveInstanceState(out);
    }

    private void load() {
        RepositoryProvider.getTaskRepository().getManageableTasks(new TaskRepository.Callback<List<Task>>() {
            @Override public void onSuccess(List<Task> result) {
                if (isFinishing() || isDestroyed()) return;
                tasks.clear();
                tasks.addAll(result);
                loaded = true;
                render();
            }
            @Override public void onError(Exception e) {
                if (!isFinishing()) Toast.makeText(ManageTasksActivity.this, "Could not load tasks: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static Filter filterOf(Task task) {
        if (task.isArchived()) return Filter.ARCHIVED;
        return "COMPLETED".equals(task.getStatus()) ? Filter.DONE : Filter.TODO;
    }

    private void render() {
        body.removeAllViews();
        body.addView(Ui.backRow(this, "Back", v -> finish()));
        Ui.space(body, 4);
        body.addView(Ui.title(this, "Tasks"));
        Ui.space(body, 4);
        int[] counts = new int[3];
        int needsTime = 0;
        for (Task task : tasks) {
            counts[filterOf(task).ordinal()]++;
            if (filterOf(task) == Filter.TODO && !task.hasFutureSlot()) needsTime++;
        }
        body.addView(Ui.text(this, !loaded ? "Loading your tasks…"
                : counts[0] + " to do · " + counts[1] + " done" + (needsTime > 0 ? " · " + needsTime + " not planned yet" : ""),
                14, Ui.MUTED, false));
        Ui.space(body, 14);

        View tip = Tips.card(this, Tips.TASKS, "Keep your list tidy", Ui.LILAC, Ui.LILAC_SOFT,
                "Tap a task to edit it, or archive it when it no longer matters.",
                "Archived tasks keep their history; restore them from Archived.",
                "\"Not planned\" means Tempo found no free time before the deadline.");
        if (tip != null) { body.addView(tip); Ui.space(body, 14); }

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        tabs.setBackground(Ui.round(Ui.SUBTLE, Ui.dp(this, 16)));
        tabs.addView(tab("To do", counts[0], Filter.TODO), new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
        tabs.addView(tab("Done", counts[1], Filter.DONE), new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
        tabs.addView(tab("Archived", counts[2], Filter.ARCHIVED), new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
        body.addView(tabs);
        Ui.space(body, 14);
        if (planError != null) {
            LinearLayout notice = Ui.panel(this, Ui.WARM_SOFT);
            notice.addView(Ui.text(this, planError, 13, Ui.WARM, false));
            body.addView(notice);
            Ui.space(body, 12);
        }
        if (!loaded) return;

        List<Task> shown = new ArrayList<>();
        for (Task task : tasks) if (filterOf(task) == filter) shown.add(task);
        if (filter == Filter.TODO) {
            // Unplanned first: they need attention.
            List<Task> sorted = new ArrayList<>();
            for (Task t : shown) if (!t.hasFutureSlot()) sorted.add(t);
            for (Task t : shown) if (t.hasFutureSlot()) sorted.add(t);
            shown = sorted;
        }
        if (shown.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView icon = Ui.text(this, filter == Filter.TODO ? "🌱" : filter == Filter.DONE ? "✅" : "🗂", 30, Ui.INK, false);
            icon.setGravity(Gravity.CENTER);
            empty.addView(icon);
            TextView line = Ui.text(this, filter == Filter.TODO ? "Nothing to do. Tap New task to add one."
                    : filter == Filter.DONE ? "Finished tasks will appear here." : "No archived tasks.", 14, Ui.MUTED, false);
            line.setGravity(Gravity.CENTER);
            empty.addView(line);
            body.addView(empty);
            return;
        }
        for (Task task : shown) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = Ui.dp(this, 10);
            body.addView(row(task), params);
        }
    }

    private View tab(String label, int count, Filter value) {
        boolean on = filter == value;
        TextView view = Ui.text(this, label + "  " + count, 13, on ? Ui.INK : Ui.MUTED, true);
        view.setGravity(Gravity.CENTER);
        if (on) {
            view.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 12)));
            view.setElevation(Ui.dp(this, 1));
        }
        view.setContentDescription(label + ", " + count + (on ? ", selected" : ""));
        view.setOnClickListener(v -> { filter = value; render(); });
        return view;
    }

    private View row(Task task) {
        int color = Ui.categoryColor(task.getCategory());
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.soften(color)),
                Ui.round(Ui.CARD, Ui.dp(this, 18)), null));
        card.setElevation(Ui.dp(this, 2));
        card.setPadding(0, 0, Ui.dp(this, 14), 0);
        card.setMinimumHeight(Ui.dp(this, 72));

        View stripe = new View(this);
        stripe.setBackground(Ui.round(color, Ui.dp(this, 3)));
        LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(Ui.dp(this, 5), Ui.dp(this, 44));
        stripeParams.leftMargin = Ui.dp(this, 10);
        stripeParams.rightMargin = Ui.dp(this, 12);
        card.addView(stripe, stripeParams);

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        boolean done = "COMPLETED".equals(task.getStatus());
        TextView title = Ui.text(this, task.getTitle(), 15, task.isArchived() ? Ui.MUTED : Ui.INK, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        middle.addView(title);
        Ui.space(middle, 4);
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView category = Ui.text(this, Ui.categoryName(task.getCategory()), 12, color, true);
        meta.addView(category);
        String details = "  ·  " + minutes(task.getEstimatedDuration());
        if (task.getDeadline() > 0) {
            details += "  ·  due " + new SimpleDateFormat("MMM d", Locale.US).format(new Date(task.getDeadline()));
        }
        TextView rest = Ui.text(this, details, 12, Ui.MUTED, false);
        rest.setSingleLine(true);
        rest.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(rest, new LinearLayout.LayoutParams(0, -2, 1));
        middle.addView(meta);
        card.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView chip = status(task);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, -2);
        chipParams.leftMargin = Ui.dp(this, 8);
        card.addView(chip, chipParams);
        card.setContentDescription(task.getTitle() + ", " + chip.getText() + ". Double-tap for actions.");
        card.setOnClickListener(v -> showActions(task));
        return card;
    }

    private TextView status(Task task) {
        if (task.isArchived()) return Ui.chip(this, "Archived", Ui.SUBTLE, Ui.MUTED);
        if ("COMPLETED".equals(task.getStatus())) return Ui.chip(this, "✓ Done", Ui.MINT_SOFT, Ui.GREEN);
        if (task.hasFutureSlot()) return Ui.chip(this, "Planned", Ui.SKY_SOFT, Ui.SKY);
        if (task.getDeadline() > 0 && task.getDeadline() < System.currentTimeMillis()) {
            return Ui.chip(this, "Overdue", Ui.WARM_SOFT, Ui.RED);
        }
        return Ui.chip(this, "Not planned", Ui.WARM_SOFT, Ui.WARM);
    }

    private static String minutes(int value) {
        if (value <= 0) return "no estimate";
        if (value < 60) return value + " min";
        return value % 60 == 0 ? value / 60 + " h" : value / 60 + " h " + value % 60 + " min";
    }

    /** A small sheet with the actions that make sense for the task's state. */
    private void showActions(Task task) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 12));
        sheet.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 24)));
        sheet.addView(Ui.eyebrow(this, Ui.categoryName(task.getCategory()), Ui.categoryColor(task.getCategory())));
        Ui.space(sheet, 4);
        sheet.addView(Ui.text(this, task.getTitle(), 20, Ui.INK, true));
        Ui.space(sheet, 4);
        sheet.addView(Ui.text(this, status(task).getText() + " · " + minutes(task.getEstimatedDuration())
                + (task.getDeadline() > 0 ? " · due " + new SimpleDateFormat("EEE, MMM d HH:mm", Locale.US)
                .format(new Date(task.getDeadline())) : ""), 13, Ui.MUTED, false));
        Ui.space(sheet, 16);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(sheet).create();
        if (task.isArchived()) {
            sheet.addView(action("↺  Restore task", Ui.MINT_SOFT, Ui.GREEN, () -> {
                dialog.dismiss();
                RepositoryProvider.getTaskRepository().restoreTask(task.getTaskId(), afterChange("Task restored"));
            }));
            sheet.addView(action("Delete permanently…", Ui.WARM_SOFT, Ui.RED, () -> { dialog.dismiss(); confirmErase(task); }));
        } else {
            sheet.addView(action("✎  Edit task", Ui.SKY_SOFT, Ui.SKY, () -> {
                dialog.dismiss();
                Intent intent = new Intent(this, AddTaskActivity.class);
                intent.putExtra("taskId", task.getTaskId());
                intent.putExtra("selectedDayMillis", selectedDayMillis);
                startActivity(intent);
            }));
            sheet.addView(action("🗂  Archive task", Ui.SUBTLE, Ui.INK, () -> { dialog.dismiss(); confirmArchive(task); }));
        }
        TextView close = Ui.link(this, "Close");
        close.setTextColor(Ui.MUTED);
        close.setOnClickListener(v -> dialog.dismiss());
        sheet.addView(close, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private View action(String label, int background, int color, Runnable run) {
        TextView view = Ui.text(this, label, 15, color, true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        view.setBackground(Ui.round(background, Ui.dp(this, 14)));
        view.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(this, 50));
        params.bottomMargin = Ui.dp(this, 8);
        view.setLayoutParams(params);
        return view;
    }

    private boolean timerRunningFor(Task task) {
        return getSharedPreferences(FocusSessionState.PREFS, MODE_PRIVATE)
                .getInt("taskId", Integer.MIN_VALUE) == task.getTaskId();
    }

    private void confirmArchive(Task task) {
        if (timerRunningFor(task)) {
            Toast.makeText(this, "Finish this task's focus session before archiving it.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Archive task?")
                .setMessage("Remove “" + task.getTitle() + "” from the plan? Your saved activity stays in History and Insights.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Archive", (d, w) -> RepositoryProvider.getTaskRepository()
                        .archiveTask(task.getTaskId(), afterChange("Task archived"))).show();
    }

    private void confirmErase(Task task) {
        if (timerRunningFor(task)) {
            Toast.makeText(this, "Finish this task's focus session before deleting it.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Delete permanently?")
                .setMessage("Delete “" + task.getTitle() + "” and all its activity records? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete permanently", (d, w) -> RepositoryProvider.getTaskRepository()
                        .deleteTask(task.getTaskId(), afterChange("Task deleted"))).show();
    }

    private TaskRepository.Callback<Integer> afterChange(String message) {
        return new TaskRepository.Callback<Integer>() {
            @Override public void onSuccess(Integer rows) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ManageTasksActivity.this, message, Toast.LENGTH_SHORT).show();
                load();
                AutoScheduler.replan(ManageTasksActivity.this, new AutoScheduler.Listener() {
                    @Override public void onScheduled(ScheduleGenerator.Result result) { planError = null; load(); }
                    @Override public void onFailed(String error) {
                        planError = AutoScheduler.NO_PROFILE.equals(error) || AutoScheduler.TIMER_ACTIVE.equals(error) ? null : error;
                        load();
                    }
                });
            }
            @Override public void onError(Exception e) {
                if (!isFinishing()) Toast.makeText(ManageTasksActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
    }
}

package com.tempo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tempo.app.model.FixedEvent;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.scheduler.AutoScheduler;
import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.ui.Ui;

import java.util.List;

/**
 * Weekly fixed commitments such as classes and meetings. The planner treats them as busy time,
 * and the schedule page draws them as grey blocks. Every change re-plans the coming week.
 */
public class FixedScheduleActivity extends Activity {
    private static final int STEP = 5; // minutes per wheel step
    private LinearLayout body;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        Ui.window(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(40));
        scroll.addView(body);
        setContentView(scroll);
        Ui.fitSystemBars(this);
        load();
    }

    private void load() {
        new Thread(() -> {
            List<FixedEvent> events = RepositoryProvider.getFixedEventDao().getAll();
            runOnUiThread(() -> { if (!isFinishing()) render(events); });
        }, "fixed-events").start();
    }

    private void render(List<FixedEvent> events) {
        body.removeAllViews();
        body.addView(Ui.backRow(this, "Back", v -> finish()), new LinearLayout.LayoutParams(-1, dp(44)));
        Ui.space(body, 6);
        body.addView(Ui.title(this, "Fixed schedule"));
        Ui.space(body, 4);
        body.addView(Ui.text(this, "Classes, meetings and other weekly commitments. Tempo never plans tasks on top of them.",
                14, Ui.MUTED, false));
        Ui.space(body, 18);

        if (events.isEmpty()) {
            LinearLayout panel = Ui.panel(this, Ui.MINT_SOFT);
            panel.addView(Ui.text(this, "Nothing fixed yet", 15, Ui.INK, true));
            Ui.space(panel, 4);
            panel.addView(Ui.text(this, "Add your lectures or regular meetings, for example Tuesday 14:00–16:00.",
                    13, Ui.MUTED, false));
            body.addView(panel);
        } else {
            int day = 0;
            LinearLayout card = null;
            for (FixedEvent event : events) {
                if (event.getDayOfWeek() != day) {
                    day = event.getDayOfWeek();
                    Ui.space(body, 12);
                    body.addView(Ui.eyebrow(this, event.dayName()));
                    Ui.space(body, 6);
                    card = Ui.card(this);
                    card.setPadding(dp(16), dp(4), dp(8), dp(4));
                    body.addView(card);
                }
                card.addView(row(event));
            }
        }
        Ui.space(body, 22);
        TextView add = Ui.primary(this, "Add fixed time");
        add.setOnClickListener(v -> showAddDialog());
        body.addView(add);
    }

    private LinearLayout row(FixedEvent event) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(this, event.getTitle(), 15, Ui.INK, true));
        text.addView(Ui.text(this, event.timeText(), 13, Ui.MUTED, false));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView remove = Ui.text(this, "Remove", 13, Ui.RED, true);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(dp(12), 0, dp(12), 0);
        remove.setMinHeight(dp(44));
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Remove " + event.getTitle() + "?")
                .setMessage("Tempo will be free to plan tasks on " + event.dayName() + " " + event.timeText() + ".")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> write(() -> RepositoryProvider.getFixedEventDao().delete(event.getEventId()),
                        "Removed"))
                .show());
        row.addView(remove);
        return row;
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText title = new EditText(this);
        title.setHint("Name, e.g. COMP7506 lecture");
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setSingleLine(true);
        form.addView(title);

        NumberPicker weekday = new NumberPicker(this);
        weekday.setMinValue(1); weekday.setMaxValue(7);
        weekday.setDisplayedValues(FixedEvent.DAY_NAMES);
        weekday.setValue(FixedEvent.isoDayOfWeek(System.currentTimeMillis()));
        weekday.setWrapSelectorWheel(true);
        weekday.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        form.addView(caption("Every"));
        form.addView(weekday);

        LinearLayout times = new LinearLayout(this);
        times.setGravity(Gravity.CENTER);
        NumberPicker start = timeWheel(14 * 60 / STEP);
        NumberPicker end = timeWheel(16 * 60 / STEP);
        times.addView(start, new LinearLayout.LayoutParams(0, -2, 1));
        TextView dash = Ui.text(this, "to", 14, Ui.MUTED, false);
        dash.setPadding(dp(8), 0, dp(8), 0);
        times.addView(dash);
        times.addView(end, new LinearLayout.LayoutParams(0, -2, 1));
        form.addView(caption("From"));
        form.addView(times);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Add fixed time").setView(form)
                .setNegativeButton("Cancel", null).setPositiveButton("Add", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = title.getText().toString().trim();
            int from = start.getValue() * STEP, to = end.getValue() * STEP;
            String problem = FixedEvent.validate(name, weekday.getValue(), from, to);
            if (problem != null) { Toast.makeText(this, problem, Toast.LENGTH_SHORT).show(); return; }
            FixedEvent event = new FixedEvent(name, weekday.getValue(), from, to);
            dialog.dismiss();
            write(() -> RepositoryProvider.getFixedEventDao().insert(event), "Added · plan updated");
        }));
        dialog.show();
    }

    private NumberPicker timeWheel(int value) {
        int steps = 24 * 60 / STEP; // 0 … 24:00
        String[] labels = new String[steps + 1];
        for (int i = 0; i <= steps; i++) labels[i] = FixedEvent.clock(i * STEP);
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(0); picker.setMaxValue(steps);
        picker.setDisplayedValues(labels);
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return picker;
    }

    private TextView caption(String value) {
        TextView view = Ui.text(this, value, 12, Ui.MUTED, true);
        view.setPadding(0, dp(12), 0, 0);
        return view;
    }

    /** Runs a database change off the UI thread, re-plans the week, then reloads the list. */
    private void write(Runnable change, String message) {
        new Thread(() -> {
            String error = null;
            try { change.run(); } catch (RuntimeException e) { error = e.getMessage(); }
            final String failure = error;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (failure != null) {
                    Toast.makeText(this, "Could not save: " + failure, Toast.LENGTH_LONG).show();
                    return;
                }
                load();
                AutoScheduler.replan(this, new AutoScheduler.Listener() {
                    @Override public void onScheduled(ScheduleGenerator.Result result) { toast(message); }
                    @Override public void onFailed(String reason) {
                        // Saved either way; without a profile there is simply nothing to plan yet.
                        toast(AutoScheduler.NO_PROFILE.equals(reason) ? "Saved" : "Saved · " + reason);
                    }
                });
            });
        }, "fixed-events-write").start();
    }

    private void toast(String text) {
        if (!isFinishing()) Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) { return Ui.dp(this, value); }
}

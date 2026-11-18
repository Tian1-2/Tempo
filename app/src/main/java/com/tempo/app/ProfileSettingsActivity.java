package com.tempo.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tempo.app.home.DailyReview;
import com.tempo.app.home.SampleData;
import com.tempo.app.ui.Ui;
import com.tempo.app.model.UserProfile;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.scheduler.AutoScheduler;
import com.tempo.app.scheduler.DefaultScheduleConstraints;

import java.util.Locale;

/** Profile settings: every value can be dragged on a slider or typed into its field. */
public class ProfileSettingsActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(247, 248, 245);
    private static final int INK = Color.rgb(30, 48, 52);
    private static final int MUTED = Color.rgb(105, 120, 119);
    private static final int MINT = Color.rgb(194, 232, 213);
    private static final int MINT_SOFT = Color.rgb(232, 245, 238);
    private static final int GREEN = Color.rgb(52, 126, 91);
    private static final int BORDER = Color.rgb(226, 232, 227);
    private static final int MEAL = Color.rgb(208, 138, 62);

    private UserProfile profile;
    private LinearLayout body;
    private TextView save;
    private DayStripView strip;
    private LinearLayout dayCard;
    private TextView summary;
    private TextView profileState, recommended;
    private String planningFailure;
    private Field wake, sleep, breakfast, lunch, dinner, start, focus, rest, capacity;

    /** One slider + text input pair. Stored values are minutes; daily capacity is shown in hours. */
    private final class Field {
        final boolean time, hours;
        final int min, max, step;
        int value;
        EditText input;
        SeekBar bar;
        boolean syncing;

        Field(boolean time, int min, int max, int step, int value) {
            this(time, false, min, max, step, value);
        }

        Field(boolean time, boolean hours, int min, int max, int step, int value) {
            this.time = time; this.hours = hours;
            this.min = min; this.max = max; this.step = step; this.value = value;
        }

        String format(int minutes) {
            if (time) return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
            if (hours) return minutes % 60 == 0 ? String.valueOf(minutes / 60)
                    : String.format(Locale.US, "%.2f", minutes / 60.0).replaceAll("0+$", "").replaceAll("\\.$", "");
            return String.valueOf(minutes);
        }

        /** Parses typed text; returns null when it is not a valid value. */
        Integer parse(String text) {
            String t = text.trim();
            if (time) {
                if (!t.matches("\\d{1,2}:\\d{2}")) return null;
                int colon = t.indexOf(':');
                int h = Integer.parseInt(t.substring(0, colon));
                int m = Integer.parseInt(t.substring(colon + 1));
                return h <= 23 && m <= 59 ? h * 60 + m : null;
            }
            if (!t.matches(hours ? "\\d{1,2}(?:\\.\\d{1,2})?" : "\\d{1,4}")) return null;
            int v;
            if (hours) {
                double minutes = Double.parseDouble(t) * 60;
                v = (int) Math.round(minutes);
                if (Math.abs(minutes - v) > 0.000001 || v % step != 0) return null;
            } else v = Integer.parseInt(t);
            return v >= min && v <= max ? v : null;
        }

        void set(int minutes, boolean updateText) {
            value = minutes;
            syncing = true;
            bar.setProgress((Math.max(min, Math.min(max, minutes)) - min + step / 2) / step);
            if (updateText) input.setText(format(minutes));
            syncing = false;
            refreshSummary();
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(110));
        scroll.addView(body);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        com.tempo.app.ui.Ui.fitSystemBars(this);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setTranslationX(-dp(14));
        BackChevronView back = new BackChevronView(this, "Back");
        back.setOnClickListener(v -> finish());
        navigation.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView backLabel = text("Back", 14, MUTED, false);
        backLabel.setOnClickListener(v -> finish());
        navigation.addView(backLabel);
        body.addView(navigation, new LinearLayout.LayoutParams(-1, dp(44)));
        space(body, 6);
        TextView heading = text("Your rhythm", 30, INK, true);
        body.addView(heading);
        space(body, 4);
        body.addView(text("Tempo plans your day around these. Drag a slider or tap a value to type it.", 14, MUTED, false));
        space(body, 18);

        profileState = text("Loading your routine…", 14, MUTED, false);
        body.addView(profileState);
        recommended = Ui.secondary(this, "Use recommended routine");
        recommended.setOnClickListener(v -> useRecommendedRoutine());
        recommended.setVisibility(View.GONE);
        body.addView(recommended);
        space(body, 16);

        dayCard = card();
        dayCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        dayCard.addView(eyebrow("YOUR DAY"));
        space(dayCard, 12);
        strip = new DayStripView(this);
        dayCard.addView(strip, new LinearLayout.LayoutParams(-1, dp(20)));
        space(dayCard, 4);
        LinearLayout axis = new LinearLayout(this);
        axis.addView(text("00:00", 11, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView noon = text("12:00", 11, MUTED, false);
        noon.setGravity(Gravity.CENTER);
        axis.addView(noon, new LinearLayout.LayoutParams(0, -2, 1));
        TextView midnight = text("24:00", 11, MUTED, false);
        midnight.setGravity(Gravity.END);
        axis.addView(midnight, new LinearLayout.LayoutParams(0, -2, 1));
        dayCard.addView(axis);
        space(dayCard, 10);
        summary = text("", 13, INK, false);
        dayCard.addView(summary);
        body.addView(dayCard);

        wake = new Field(true, 0, 1435, 5, 7 * 60);
        sleep = new Field(true, 0, 1435, 5, 23 * 60 + 30);
        breakfast = new Field(true, 0, 1435, 5, 8 * 60);
        lunch = new Field(true, 0, 1435, 5, 12 * 60 + 30);
        dinner = new Field(true, 0, 1435, 5, 18 * 60 + 30);
        start = new Field(true, 0, 1435, 5, 9 * 60);
        focus = new Field(false, 5, 120, 5, 25);
        rest = new Field(false, 0, 30, 1, 5);
        capacity = new Field(false, true, 30, 720, 15, 240);

        section("ROUTINE");
        LinearLayout routine = card();
        addField(routine, wake, "Wake up", null, null);
        addField(routine, sleep, "Bedtime", "Must be later than wake-up", null);
        body.addView(routine);

        section("MEALS · " + DefaultScheduleConstraints.MEAL_MINUTES + " MIN EACH");
        LinearLayout meals = card();
        addField(meals, breakfast, "Breakfast", null, null);
        addField(meals, lunch, "Lunch", null, null);
        addField(meals, dinner, "Dinner", null, null);
        body.addView(meals);

        section("FOCUS");
        LinearLayout focusCard = card();
        addField(focusCard, start, "Best focus time", "Hard tasks go into the " + DefaultScheduleConstraints.PRODUCTIVE_MINUTES / 60 + " h after this", null);
        addField(focusCard, focus, "Focus session", "One block of deep work", "min");
        addField(focusCard, rest, "Break", "Rest between focus blocks", "min");
        addField(focusCard, capacity, "Daily task time", "Most Tempo will plan per day", "h");
        body.addView(focusCard);

        section("FIXED SCHEDULE");
        LinearLayout fixedCard = card();
        fixedCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        fixedCard.addView(text("Classes and meetings", 15, INK, true));
        space(fixedCard, 2);
        fixedCard.addView(text("Weekly times Tempo keeps free, e.g. Tuesday 14:00–16:00 lecture. Saved separately from this page.",
                13, MUTED, false));
        space(fixedCard, 10);
        TextView editFixed = Ui.secondary(this, "Edit fixed schedule");
        editFixed.setOnClickListener(v -> startActivity(new Intent(this, FixedScheduleActivity.class)));
        fixedCard.addView(editFixed);
        body.addView(fixedCard);

        section("CATEGORIES");
        LinearLayout categoryCard = card();
        categoryCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        categoryCard.addView(text("Task labels and colours", 15, INK, true));
        space(categoryCard, 2);
        categoryCard.addView(text("Study, Work and Life to start with. Add your own, such as Club or Part-time job.",
                13, MUTED, false));
        space(categoryCard, 10);
        TextView editCategories = Ui.secondary(this, "Edit categories");
        editCategories.setOnClickListener(v -> startActivity(new Intent(this, CategoriesActivity.class)));
        categoryCard.addView(editCategories);
        body.addView(categoryCard);

        section("REMINDERS");
        reminderCard = card();
        reminderCard.setPadding(dp(16), dp(10), dp(16), dp(14));
        body.addView(reminderCard);
        renderReminders();

        // Demo seeding is available only through a hidden debug gesture.
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            heading.setOnLongClickListener(v -> { confirmSampleData(); return true; });
        }

        save = text("Save", 16, Color.WHITE, true);
        save.setGravity(Gravity.CENTER);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(INK);
        saveBg.setCornerRadius(dp(16));
        save.setBackground(saveBg);
        save.setElevation(dp(6));
        save.setOnClickListener(v -> save());
        FrameLayout.LayoutParams saveParams = new FrameLayout.LayoutParams(-1, dp(56), Gravity.BOTTOM);
        saveParams.setMargins(dp(20), 0, dp(20), dp(20));
        root.addView(save, saveParams);
        refreshSummary();

        RepositoryProvider.getUserProfileRepository().getProfile(new TaskRepository.Callback<UserProfile>() {
            @Override public void onSuccess(UserProfile value) {
                profile = value;
                if (value == null) {
                    profileState.setText("No routine saved yet. Enter your own values or apply a recommended starting point, then save to enable planning.");
                    recommended.setVisibility(View.VISIBLE);
                    save.setText("Save routine");
                    refreshSummary();
                    return;
                }
                profileState.setText("Your saved routine · edit any value below.");
                recommended.setVisibility(View.GONE);
                save.setText("Save changes");
                load(wake, value.getWakeTime()); load(sleep, value.getSleepTime());
                load(breakfast, value.getBreakfastTime()); load(lunch, value.getLunchTime());
                load(dinner, value.getDinnerTime()); load(start, value.getPreferredStartTime());
                if (value.getFocusDuration() > 0) focus.set(value.getFocusDuration(), true);
                if (value.getBreakDuration() >= 0) rest.set(value.getBreakDuration(), true);
                if (value.getDailyCapacity() > 0) capacity.set(value.getDailyCapacity(), true);
            }
            @Override public void onError(Exception error) {
                Toast.makeText(ProfileSettingsActivity.this, "Could not read your profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void load(Field field, String hhmm) {
        Integer minutes = hhmm == null ? null : field.parse(hhmm);
        if (minutes != null) field.set(minutes, true);
    }

    private void useRecommendedRoutine() {
        wake.set(7 * 60, true); sleep.set(23 * 60 + 30, true);
        breakfast.set(8 * 60, true); lunch.set(12 * 60 + 30, true);
        dinner.set(18 * 60 + 30, true); start.set(9 * 60, true);
        focus.set(25, true); rest.set(5, true); capacity.set(240, true);
        profileState.setText("Recommended values applied · review them, then tap Save routine.");
        refreshSummary();
    }

    private void addField(LinearLayout card, Field field, String label, String hint, String unit) {
        if (card.getChildCount() > 0) {
            View rule = new View(this);
            rule.setBackgroundColor(BORDER);
            card.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(14), 0, dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(label, 15, INK, true));
        if (hint != null) titles.addView(text(hint, 12, MUTED, false));
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(MINT_SOFT);
        chipBg.setCornerRadius(dp(12));
        chip.setBackground(chipBg);
        EditText input = new EditText(this);
        input.setBackground(null);
        input.setPadding(0, 0, 0, 0);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(15);
        input.setTextColor(GREEN);
        input.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        input.setMinWidth(dp(field.time ? 52 : field.hours ? 44 : 34));
        input.setInputType(field.time ? InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME
                : field.hours ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint("e.g. " + field.format(field.value));
        input.setHintTextColor(MUTED);
        input.setText("");
        chip.addView(input);
        if (unit != null) {
            TextView unitView = text(" " + unit, 13, GREEN, true);
            chip.addView(unitView);
        }
        top.addView(chip, new LinearLayout.LayoutParams(-2, dp(36)));
        block.addView(top);

        SeekBar bar = new SeekBar(this);
        bar.setMax((field.max - field.min) / field.step);
        bar.setProgressTintList(ColorStateList.valueOf(GREEN));
        bar.setThumbTintList(ColorStateList.valueOf(GREEN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(36));
        barParams.leftMargin = -dp(14);
        barParams.rightMargin = -dp(14);
        block.addView(bar, barParams);
        card.addView(block);

        field.input = input;
        field.bar = bar;
        field.set(field.value, false);
        bar.setProgress(0);

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || field.syncing) return;
                field.value = field.min + progress * field.step;
                field.syncing = true;
                input.setText(field.format(field.value));
                field.syncing = false;
                refreshSummary();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { input.clearFocus(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (field.syncing) return;
                Integer parsed = field.parse(s.toString());
                if (parsed != null) field.set(parsed, false);
            }
        });
        input.setOnFocusChangeListener((v, hasFocus) -> {
            // On leaving the field, show the last valid value again in its canonical form.
            if (!hasFocus && !input.getText().toString().trim().isEmpty()
                    && field.parse(input.getText().toString()) == null) {
                field.syncing = true;
                input.setText(field.format(field.value));
                field.syncing = false;
            }
        });
    }

    private void refreshSummary() {
        if (summary == null || capacity == null || capacity.bar == null) return;
        Field[] all = {wake, sleep, breakfast, lunch, dinner, start, focus, rest, capacity};
        for (Field field : all) if (field == null || field.input == null
                || field.parse(field.input.getText().toString()) == null) {
            dayCard.setVisibility(View.GONE);
            return;
        }
        dayCard.setVisibility(View.VISIBLE);
        int awake = sleep.value - wake.value;
        summary.setText(awake > 0
                ? DailyReview.formatMinutes(awake) + " awake · up to " + DailyReview.formatMinutes(capacity.value)
                        + " of tasks · " + focus.value + " / " + rest.value + " rhythm"
                : "Bedtime must be later than wake-up");
        summary.setTextColor(awake > 0 ? INK : Color.rgb(178, 60, 47));
        strip.update(wake.value, sleep.value, start.value, DefaultScheduleConstraints.PRODUCTIVE_MINUTES,
                new int[]{breakfast.value, lunch.value, dinner.value});
    }

    private void save() {
        Field[] all = {wake, sleep, breakfast, lunch, dinner, start, focus, rest, capacity};
        for (Field f : all) {
            if (f.parse(f.input.getText().toString()) == null) {
                f.input.requestFocus();
                Toast.makeText(this, f.time ? "Use HH:mm, e.g. 07:30"
                                : f.hours ? "Enter 0.5–12 hours in 0.25-hour steps"
                                : "Enter a number between " + f.min + " and " + f.max,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (wake.value >= sleep.value) {
            Toast.makeText(this, "Bedtime must be later than wake-up", Toast.LENGTH_SHORT).show();
            return;
        }
        UserProfile updated = profile == null ? new UserProfile() : profile;
        updated.setWakeTime(wake.format(wake.value));
        updated.setSleepTime(sleep.format(sleep.value));
        updated.setBreakfastTime(breakfast.format(breakfast.value));
        updated.setLunchTime(lunch.format(lunch.value));
        updated.setDinnerTime(dinner.format(dinner.value));
        updated.setPreferredStartTime(start.format(start.value));
        updated.setFocusDuration(focus.value);
        updated.setBreakDuration(rest.value);
        updated.setDailyCapacity(capacity.value);
        save.setEnabled(false);
        save.setText("Saving…");
        RepositoryProvider.getUserProfileRepository().saveProfile(updated, new TaskRepository.Callback<Long>() {
            @Override public void onSuccess(Long id) {
                profile = updated;
                // The routine changed, so re-plan today before leaving.
                AutoScheduler.replan(ProfileSettingsActivity.this, new AutoScheduler.Listener() {
                    @Override public void onScheduled(com.tempo.app.scheduler.ScheduleGenerator.Result result) { done("Saved · today's plan updated"); }
                    @Override public void onFailed(String message) { planningFailure = message; done("Saved · plan needs attention"); }
                });
            }
            @Override public void onError(Exception error) {
                save.setEnabled(true);
                save.setText("Save");
                Toast.makeText(ProfileSettingsActivity.this, "Could not save: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmSampleData() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Load sample data?")
                .setMessage("Adds a week of example tasks and focus sessions, then plans today. This only works while the app has no tasks.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Load", (d, w) -> loadSampleData())
                .show();
    }

    private void loadSampleData() {
        save.setEnabled(false);
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            String error = null;
            try {
                SampleData.seed();
            } catch (Exception e) {
                error = e.getMessage();
            }
            String failure = error;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (failure != null) {
                    save.setEnabled(true);
                    Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
                    return;
                }
                AutoScheduler.replan(ProfileSettingsActivity.this, new AutoScheduler.Listener() {
                    @Override public void onScheduled(com.tempo.app.scheduler.ScheduleGenerator.Result result) { done("Sample data loaded"); }
                    @Override public void onFailed(String message) { done("Sample data loaded"); }
                });
            });
        });
    }

    private void done(String message) {
        if (isFinishing()) return;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        android.content.Intent result = new android.content.Intent();
        if (planningFailure != null) result.putExtra("planningFailure", planningFailure);
        setResult(RESULT_OK, result);
        finish();
    }

    // ---------------------------------------------------------------- views

    /** A 24-hour strip: awake time, the productive window and meal markers. */
    private static final class DayStripView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint awake = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint meal = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int wake, sleep, focusStart, focusMinutes;
        private int[] meals = new int[0];

        DayStripView(Context context) {
            super(context);
            track.setColor(MINT_SOFT);
            awake.setColor(MINT);
            focusPaint.setColor(GREEN);
            meal.setColor(MEAL);
        }

        void update(int wake, int sleep, int focusStart, int focusMinutes, int[] meals) {
            this.wake = wake; this.sleep = sleep; this.focusStart = focusStart;
            this.focusMinutes = focusMinutes; this.meals = meals;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float barTop = h * 0.2f, barBottom = h * 0.8f, r = (barBottom - barTop) / 2f;
            rect.set(0, barTop, w, barBottom);
            canvas.drawRoundRect(rect, r, r, track);
            if (sleep > wake) {
                rect.set(x(wake, w), barTop, x(sleep, w), barBottom);
                canvas.drawRoundRect(rect, r, r, awake);
            }
            rect.set(x(focusStart, w), barTop, x(Math.min(1440, focusStart + focusMinutes), w), barBottom);
            canvas.drawRoundRect(rect, r, r, focusPaint);
            float half = h * 0.1f;
            for (int m : meals) {
                float cx = x(m, w);
                rect.set(cx - half, 0, cx + half, h);
                canvas.drawRoundRect(rect, half, half, meal);
            }
        }

        private static float x(int minutes, float width) { return width * minutes / 1440f; }
    }

    private LinearLayout reminderCard;

    @Override protected void onResume() {
        super.onResume();
        if (reminderCard != null) renderReminders(); // permission may have changed in Settings
    }

    /** Reminder switch and lead time; these apply at once and do not need Save. */
    private void renderReminders() {
        reminderCard.removeAllViews();
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setText("Remind me before each task");
        toggle.setTextSize(15);
        toggle.setTextColor(INK);
        toggle.setMinHeight(dp(48));
        toggle.setChecked(com.tempo.app.reminders.Reminders.enabled(this));
        toggle.setOnCheckedChangeListener((b, on) -> {
            com.tempo.app.reminders.Reminders.setEnabled(this, on);
            if (on && android.os.Build.VERSION.SDK_INT >= 33 && !com.tempo.app.reminders.Reminders.canNotify(this)) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 42);
            }
            renderReminders();
        });
        reminderCard.addView(toggle);
        if (!com.tempo.app.reminders.Reminders.enabled(this)) return;
        reminderCard.addView(text("A notification shortly before a planned task; tap it to start the focus timer. How early:",
                13, MUTED, false));
        space(reminderCard, 10);
        LinearLayout chips = new LinearLayout(this);
        int current = com.tempo.app.reminders.Reminders.leadMinutes(this);
        for (int minutes : com.tempo.app.reminders.Reminders.LEAD_CHOICES) {
            boolean selected = minutes == current;
            TextView chip = text(minutes == 0 ? "At start" : minutes + " min", 13, INK, selected);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(selected ? MINT : MINT_SOFT);
            bg.setCornerRadius(dp(12));
            chip.setBackground(bg);
            chip.setOnClickListener(v -> { com.tempo.app.reminders.Reminders.setLeadMinutes(this, minutes); renderReminders(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            lp.rightMargin = dp(6);
            chips.addView(chip, lp);
        }
        reminderCard.addView(chips);
        if (!com.tempo.app.reminders.Reminders.canNotify(this)) {
            space(reminderCard, 10);
            TextView fix = Ui.link(this, "Notifications are off for Tempo · Open settings");
            fix.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())));
            reminderCard.addView(fix);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (reminderCard != null) renderReminders();
    }

    private void section(String title) {
        space(body, 22);
        body.addView(eyebrow(title));
        space(body, 8);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(2), dp(16), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        card.setElevation(dp(2));
        return card;
    }

    private TextView eyebrow(String value) {
        TextView view = text(value, 11, GREEN, true);
        view.setLetterSpacing(0.14f);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void space(LinearLayout parent, int size) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size)));
    }

    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
}

package com.tempo.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.tempo.app.insights.InsightsIcon;
import com.tempo.app.insights.InsightsRepository;
import com.tempo.app.insights.InsightsStats;

import com.google.android.material.tabs.TabLayout;
import com.tempo.app.insights.RecordedTimeChart;
import com.tempo.app.insights.TimeOfDayStats;
import com.tempo.app.learning.LearningSummary;
import com.tempo.app.learning.LearningRepository;
import com.tempo.app.model.Task;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Displays one read-only snapshot in three views. Manual logging and history use the existing screens. */
public class InsightsActivity extends AppCompatActivity {
    private enum Page {
        OVERVIEW("Overview"), ESTIMATES("Estimates"), PATTERNS("Patterns");

        final String title;
        Page(String title) { this.title = title; }
    }

    private Page page = Page.OVERVIEW;
    private final int[] offsets = new int[Page.values().length];
    private LinearLayout content;
    private ScrollView scroll;
    private TabLayout tabs;
    private InsightsIcon refresh;
    private InsightsRepository repository;
    private LearningRepository actions;
    private InsightsStats stats;
    private LearningSummary learning;
    private boolean loading, accuracyExpanded, patternsExpanded;
    // During layout changes scrollY can still belong to the preceding page.
    private OneShotPreDrawListener restorePosition;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Build.VERSION.SDK_INT >= 26 ? Color.TRANSPARENT : Ui.INK);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        setContentView(R.layout.activity_insights);
        View root = findViewById(R.id.insights_root);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        content = findViewById(R.id.insights_content);
        scroll = findViewById(R.id.insights_scroll);
        ((android.widget.LinearLayout) root).addView(com.tempo.app.ui.BottomNav.create(this,
                com.tempo.app.ui.BottomNav.Tab.INSIGHTS, null));

        tabs = findViewById(R.id.insights_tabs);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.insights_title), true);
        if (savedInstanceState != null) {
            int restored = savedInstanceState.getInt("insightsPage");
            if (restored >= 0 && restored < Page.values().length) page = Page.values()[restored];
            int[] savedOffsets = savedInstanceState.getIntArray("insightsOffsets");
            if (savedOffsets != null && savedOffsets.length == offsets.length) {
                System.arraycopy(savedOffsets, 0, offsets, 0, offsets.length);
            }
            accuracyExpanded = savedInstanceState.getBoolean("accuracyExpanded");
            patternsExpanded = savedInstanceState.getBoolean("patternsExpanded");
        }
        buildToolbar();
        buildTabs();
        repository = new InsightsRepository(this);
        actions = new LearningRepository(this);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    @Override protected void onDestroy() {
        cancelRestore();
        repository.close();
        actions.close();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        rememberPosition();
        state.putInt("insightsPage", page.ordinal());
        state.putIntArray("insightsOffsets", offsets.clone());
        state.putBoolean("accuracyExpanded", accuracyExpanded);
        state.putBoolean("patternsExpanded", patternsExpanded);
        super.onSaveInstanceState(state);
    }

    private void buildTabs() {
        if (stackColumns()) tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        for (Page item : Page.values()) {
            tabs.addTab(tabs.newTab().setText(item.title), item == page);
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                Page next = Page.values()[tab.getPosition()];
                if (page == next) return;
                rememberPosition();
                page = next;
                if (stats != null) render();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void rememberPosition() {
        if (stats != null && restorePosition == null) offsets[page.ordinal()] = scroll.getScrollY();
    }

    private void cancelRestore() {
        if (restorePosition != null) {
            restorePosition.removeListener();
            restorePosition = null;
        }
    }

    private void restorePositionAfterLayout() {
        cancelRestore();
        final int savedY = offsets[page.ordinal()];
        restorePosition = OneShotPreDrawListener.add(scroll, () -> {
            restorePosition = null;
            scroll.scrollTo(0, savedY);
        });
    }

    private void buildToolbar() {
        LinearLayout toolbar = findViewById(R.id.insights_toolbar);
        InsightsIcon back = iconButton(InsightsIcon.Kind.BACK, R.string.insights_back);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView home = label(getString(R.string.insights_home), 14, Ui.MUTED, false);
        home.setGravity(Gravity.CENTER_VERTICAL);
        home.setOnClickListener(v -> finish());
        toolbar.addView(home, new LinearLayout.LayoutParams(0, -2, 1));
        InsightsIcon info = iconButton(InsightsIcon.Kind.INFO, R.string.insights_definitions);
        info.setOnClickListener(v -> showDefinitions());
        toolbar.addView(info, new LinearLayout.LayoutParams(dp(48), dp(48)));
        refresh = iconButton(InsightsIcon.Kind.REFRESH, R.string.insights_refresh);
        refresh.setOnClickListener(v -> load());
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }


    private void load() {
        if (loading) return;
        loading = true;
        rememberPosition();
        refresh.setEnabled(false);
        refresh.setAlpha(.45f);
        if (stats == null) {
            content.removeAllViews();
            LinearLayout panel = panel(Ui.MINT_SOFT, false);
            ProgressBar spinner = new ProgressBar(this);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
            panel.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
            gap(panel, 12);
            panel.addView(label(getString(R.string.insights_loading), 16, Ui.INK, true));
            ViewCompat.setAccessibilityLiveRegion(panel, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
            content.addView(panel);
        }
        repository.load(new InsightsRepository.Callback() {
            @Override public void onLoaded(InsightsStats newStats, LearningSummary newLearning) {
                if (isFinishing() || isDestroyed()) return;
                rememberPosition();
                stats = newStats;
                learning = newLearning;
                finishLoading();
                render();
            }

            @Override public void onError() {
                if (isFinishing() || isDestroyed()) return;
                rememberPosition();
                cancelRestore();
                stats = null;
                learning = null;
                finishLoading();
                content.removeAllViews();
                LinearLayout error = panel(Ui.MINT_SOFT, false);
                heading(error, getString(R.string.insights_error));
                error.addView(label(getString(R.string.insights_error_body), 14, Ui.MUTED, false));
                gap(error, 16);
                error.addView(action(getString(R.string.insights_retry), v -> load()));
                content.addView(error);
                gap(content, 16);
                showShortcuts();
                scroll.scrollTo(0, 0);
            }
        });
    }

    private void finishLoading() {
        loading = false;
        refresh.setEnabled(true);
        refresh.setAlpha(1f);
    }

    private void render() {
        content.removeAllViews();
        switch (page) {
            case OVERVIEW: showOverview(); break;
            case ESTIMATES: showEstimates(); break;
            case PATTERNS: showPatterns(); break;
        }
        restorePositionAfterLayout();
    }

    private void showOverview() {
        eyebrow(content, "Last 7 days");
        SimpleDateFormat day = new SimpleDateFormat("MMM d", Locale.ENGLISH);
        content.addView(caption(day.format(new Date(stats.dayStarts[0])) + " – "
                + day.format(new Date(stats.dayStarts[6]))));
        gap(content, 12);

        LinearLayout recorded = metric(duration(stats.recordedMinutes), "Recorded time",
                count(stats.sessionCount, "saved session", "saved sessions"), Ui.MINT_SOFT, 33);
        String completion = stats.taskCount == 0 ? "—" : stats.completionPercent() + "%";
        String taskLabel = stats.taskCount == 0 ? "No tasks yet" : stats.completedTasks + " of "
                + count(stats.taskCount, "task", "tasks");
        LinearLayout completed = metric(completion, "Overall completion",
                taskLabel + "\nAll history", Ui.FIXED_SURFACE, 33);
        pair(content, recorded, completed);
        gap(content, 20);

        titleAndValue(content, "Your week", stats.recordedMinutes + " min");
        if (stats.recordedMinutes == 0) {
            gap(content, 8);
            content.addView(caption(getString(R.string.insights_chart_empty)));
            gap(content, 12);
            content.addView(action(getString(R.string.insights_open_schedule), v -> openSchedule()));
        } else {
            gap(content, 6);
            int chartHeight = dp(100) + Math.round(30 * getResources().getDisplayMetrics().scaledDensity);
            content.addView(new RecordedTimeChart(this, stats),
                    new LinearLayout.LayoutParams(MATCH_PARENT, chartHeight));
            TextView note = caption("Recorded time · by save date");
            note.setGravity(Gravity.CENTER);
            content.addView(note);
        }
        gap(content, 20);

        LinearLayout categories = panel(Ui.MINT_SOFT, false);
        heading(categories, "Where time went");
        if (stats.categories.isEmpty()) {
            categories.addView(caption(getString(R.string.insights_categories_empty)));
        } else {
            int index = 0;
            for (InsightsStats.Category category : stats.categories) {
                double share = (double) category.minutes / stats.recordedMinutes;
                if (index > 0) gap(categories, 8);
                // The same colour the category has on Home, the schedule and History.
                barRow(categories, Ui.categoryName(category.name), exactDuration(category.minutes) + " · " + Math.round(share * 100) + "%", share,
                        Ui.categoryColor(category.name), true);
                index++;
            }
        }
        content.addView(categories);
        gap(content, 12);
        showShortcuts();
    }

    private void showShortcuts() {
        TextView log = action("+  Log activity", v -> logActivity());
        TextView history = action("History  ↗", v -> startActivity(new Intent(this, SessionHistoryActivity.class)));
        history.setContentDescription("Activity history");
        history.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.MINT_SOFT), null, shape(Ui.CARD, false)));
        pair(content, log, history);
    }

    private void showEstimates() {
        heading(content, "Estimate accuracy");
        content.addView(caption(count(learning.evaluatedTasks, "evaluated task", "evaluated tasks") + " · all history"));
        gap(content, 12);
        LinearLayout comparison = panel(Ui.MINT_SOFT, false);
        if (learning.evaluatedTasks == 0) {
            heading(comparison, "No comparisons yet");
            comparison.addView(caption("Record a task through completion to compare its original estimates with the time you saved."));
        } else {
            eyebrow(comparison, "Average error");
            gap(comparison, 10);
            pair(comparison, figure(decimal(learning.estimateMae) + " min", "Your estimates", Ui.INK, 25),
                    figure(decimal(learning.predictionMae) + " min", "Tempo estimates", Ui.GREEN, 25));
            gap(comparison, 16);
            // Both lengths use the same scale. A perfect match produces an empty bar.
            double largest = Math.max(learning.estimateMae, learning.predictionMae);
            barRow(comparison, "Your estimates", decimal(learning.estimateMae) + " min",
                    largest > 0 ? learning.estimateMae / largest : 0, Ui.STUDY, false);
            gap(comparison, 5);
            barRow(comparison, "Tempo estimates", decimal(learning.predictionMae) + " min",
                    largest > 0 ? learning.predictionMae / largest : 0, Ui.GREEN, false);
            gap(comparison, 10);
            comparison.addView(caption("Lower is closer to recorded time."));
        }
        content.addView(comparison);
        if (learning.evaluatedTasks > 0) {
            gap(content, 10);
            content.addView(caption(learning.evaluatedTasks == 1 ? "One evaluated task · early results"
                    : "Based on the same completed tasks."));
            gap(content, 16);
            LinearLayout details = panel(Ui.CARD, true);
            heading(details, "Mean absolute error");
            details.addView(caption("For each task, take the absolute difference between the estimate and the recorded minutes. Average those differences across the evaluated tasks."));
            gap(details, 12);
            double bias = learning.predictionBias;
            details.addView(label("Tempo signed bias  " + signed(bias) + " min", 15, Ui.INK, true));
            String biasNote = Math.round(Math.abs(bias) * 10) == 0
                    ? "Positive and negative differences average to zero at this precision."
                    : "Tempo's estimates were " + decimal(Math.abs(bias)) + " min "
                            + (bias > 0 ? "higher" : "lower") + " than recorded time on average.";
            gap(details, 5);
            details.addView(caption(biasNote));
            gap(details, 10);
            details.addView(caption("Both figures use predictions saved before work began. Corrections to recorded minutes count. Missing, later-added or retrospectively logged time, changed task scope and older edits with uncertain history are excluded."));
            disclosure("How this is calculated", details, true);
        }
        gap(content, 20);
        heading(content, "Upcoming estimates");
        content.addView(caption("Pending & in progress · " + count(learning.historyTasks,
                "completed task", "completed tasks") + " available for learning"));
        gap(content, 10);
        LinearLayout list = panel(Ui.CARD, true);
        if (learning.previews.isEmpty()) {
            list.addView(caption("Add a task with an estimate to see its preview here."));
        } else {
            for (int i = 0; i < learning.previews.size(); i++) {
                if (i > 0) divider(list);
                previewRow(list, learning.previews.get(i));
            }
        }
        content.addView(list);
    }

    private void previewRow(LinearLayout parent, LearningSummary.Preview item) {
        LinearLayout original = column();
        original.addView(label(item.title, 16, Ui.INK, true));
        original.addView(caption((item.fixed ? "Original estimate " : "Your estimate ") + item.estimate + " min"));
        LinearLayout predicted = column();
        predicted.setGravity(stackColumns() ? Gravity.START : Gravity.END);
        predicted.addView(caption("Tempo"));
        predicted.addView(label(item.prediction + " min", 17, Ui.GREEN, true));
        lineWithValue(parent, original, predicted);
        gap(parent, 4);
        String basis = item.samples == 0 ? "No matching history yet"
                : "Based on " + count(item.samples, "completed " + item.basis + " task",
                                                    "completed " + item.basis + " tasks");
        parent.addView(caption(item.fixed ? "Saved prediction · " + item.remaining + " min remaining" : basis));
        if (item.scopeChanged) parent.addView(caption("Scope changed · excluded from learning and accuracy."));
    }

    private void showPatterns() {
        eyebrow(content, "Last 7 days");
        gap(content, 10);
        LinearLayout rhythm = panel(Ui.MINT_SOFT, false);
        heading(rhythm, "Your rhythm");
        rhythm.addView(figure(stats.activeDays + " / 7", "days with recorded time", Ui.INK, 33));
        gap(rhythm, 14);
        addWeek(rhythm);
        gap(rhythm, 10);
        rhythm.addView(caption("Unrecorded days may still include work."));
        content.addView(rhythm);
        gap(content, 18);

        heading(content, "How tasks felt");
        String rated = stats.ratedSessions > 0 ? decimal(stats.averageDifficulty) + " / 5" : "—";
        boolean hasPairs = learning.difficultyComparisons > 0;
        double delta = learning.difficultyDelta;
        String deltaLabel = !hasPairs ? "Difference from expected" : Math.abs(delta) < .05 ? "As expected"
                : delta > 0 ? "Above expected" : "Below expected";
        pair(content, metric(rated, "Reported difficulty", null, Ui.MINT_SOFT, 25),
                metric(hasPairs ? signed(learning.difficultyDelta) : "—", deltaLabel, null, Ui.FIXED_SURFACE, 25));
        gap(content, 8);
        content.addView(caption(stats.ratedSessions == 0 ? "No ratings yet · optional when saving a session."
                : count(stats.ratedSessions, "saved rating", "saved ratings") + " · "
                        + count(learning.difficultyComparisons, "paired rating", "paired ratings")));
        gap(content, 18);

        heading(content, "When you worked");
        TimeOfDayStats timing = learning.timeOfDay;
        long total = 0;
        for (long millis : timing.millis) total += millis;
        if (total == 0) {
            content.addView(caption("Save a timer session with its measured duration to see your time of day."));
        } else {
            // Include all four R4 buckets, even on weeks with no overnight time.
            for (int i = 0; i < timing.millis.length; i++) {
                if (i > 0) gap(content, 8);
                String name = TimeOfDayStats.LABELS[i].split(" · ", 2)[0];
                long minutes = (timing.millis[i] + 59_999) / 60_000;
                barRow(content, name, exactDuration(minutes), (double) timing.millis[i] / total,
                        i % 2 == 0 ? Ui.STUDY : Ui.GREEN, false);
            }
            gap(content, 10);
            content.addView(caption(count(timing.sessions, "timed session", "timed sessions") + " · active intervals only"));
        }
        gap(content, 18);
        LinearLayout details = panel(Ui.CARD, true);
        heading(details, "Completion by deadline");
        if (learning.deadlineTasks > 0) {
            details.addView(label(learning.onTimeTasks + " of " + learning.deadlineTasks + " on time", 17, Ui.INK, true));
            gap(details, 6);
        }
        details.addView(caption(learning.deadlineTasks == 0 ? "No eligible completed tasks with a deadline yet."
                : "All history · completed tasks with a deadline saved before work began."));
        if (learning.untimedRecords > 0) {
            gap(details, 12);
            details.addView(caption(count(learning.untimedRecords, "untimed activity", "untimed activities")
                    + " saved in the last 7 days. These contribute no minutes."));
        }
        gap(details, 12);
        details.addView(caption("Difficulty uses optional ratings from 1 to 5. The difference uses only paired ratings (reported minus expected)."));
        gap(details, 10);
        details.addView(caption("Active intervals exclude pauses, manual entries and edited durations. Local time windows start at midnight, 6 am, noon and 6 pm. Displayed minutes round up."));
        disclosure("About these patterns", details, false);
        gap(content, 10);
        content.addView(caption("Time of day describes when you recorded work."));
    }

    private void addWeek(LinearLayout parent) {
        LinearLayout week = row();
        SimpleDateFormat day = new SimpleDateFormat("EEE", Locale.ENGLISH);
        SimpleDateFormat date = new SimpleDateFormat("EEEE, MMM d", Locale.ENGLISH);
        for (int i = 0; i < stats.dayStarts.length; i++) {
            Date stamp = new Date(stats.dayStarts[i]);
            LinearLayout cell = column();
            cell.setGravity(Gravity.CENTER);
            String abbreviated = day.format(stamp);
            TextView title = label(stackColumns() ? abbreviated.substring(0, 1) : abbreviated,
                    12, Ui.INK, false);
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(title);
            gap(cell, 6);
            boolean active = stats.dailyMinutes[i] > 0;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(active ? Ui.GREEN : Color.TRANSPARENT);
            dot.setStroke(dp(1), active ? Ui.GREEN : Ui.MUTED);
            View mark = new View(this);
            mark.setBackground(dot);
            cell.addView(mark, new LinearLayout.LayoutParams(dp(15), dp(15)));
            cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            cell.setContentDescription(date.format(stamp) + ", " + stats.dailyMinutes[i] + " recorded minutes");
            week.addView(cell, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        }
        parent.addView(week);
    }

    private void logActivity() {
        if(getSharedPreferences("tempo_focus_timer",MODE_PRIVATE).contains("taskId")) {
            new AlertDialog.Builder(this).setMessage("Finish the active timer before logging another activity.").setPositiveButton("Got it",null).show();return;
        }
        actions.pendingTasks(new TaskRepository.Callback<List<Task>>() {
            public void onSuccess(List<Task> tasks) {
                if(isFinishing()||isDestroyed())return;
                if(tasks.isEmpty()) {
                    new AlertDialog.Builder(InsightsActivity.this).setMessage("Add an unfinished task before logging activity.")
                            .setPositiveButton("Open schedule",(d,w)->openSchedule()).setNegativeButton("Cancel",null).show();return;
                }
                String[] names=new String[tasks.size()];for(int i=0;i<tasks.size();i++)names[i]=tasks.get(i).getTitle()+" · "+tasks.get(i).getCategory();
                new AlertDialog.Builder(InsightsActivity.this).setTitle("Choose a task").setItems(names,(d,which)->{
                    Task task=tasks.get(which);Intent intent=new Intent(InsightsActivity.this,TaskFeedbackActivity.class);
                    intent.putExtra("taskId",task.getTaskId());intent.putExtra("title",task.getTitle());intent.putExtra("manual",true);
                    intent.putExtra("sessionToken",UUID.randomUUID().toString());startActivity(intent);
                }).setNegativeButton("Cancel",null).show();
            }
            public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(InsightsActivity.this)
                        .setMessage("Could not load tasks. Please try again.")
                        .setPositiveButton("Got it", null).show();
            }
        });
    }

    private void showDefinitions() {
        LinearLayout panel = panel(Ui.CARD, false);
        heading(panel, getString(R.string.insights_definitions));
        panel.addView(label(getString(R.string.insights_definitions_body), 14, Ui.MUTED, false));
        gap(panel, 20);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(scroller).create();
        panel.addView(action(getString(R.string.insights_close), v -> dialog.dismiss()));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(shape(Ui.CARD, false));
            int height = getResources().getDisplayMetrics().heightPixels;
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(440)),
                    (int) (height * .75f));
        }
    }

    private void openSchedule() {
        Intent intent = new Intent(this, SchedulePreviewActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private InsightsIcon iconButton(InsightsIcon.Kind kind, int description) {
        InsightsIcon icon = new InsightsIcon(this, kind, Ui.MUTED);
        icon.setContentDescription(getString(description));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        icon.setFocusable(true);
        icon.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.MINT), null, shape(Ui.CARD, false)));
        return icon;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private TextView caption(String text) {
        return label(text, 13, Ui.MUTED, false);
    }

    private void heading(LinearLayout parent, String text) {
        TextView title = label(text, 20, Ui.INK, true);
        title.setLetterSpacing(-.015f);
        ViewCompat.setAccessibilityHeading(title, true);
        parent.addView(title);
        gap(parent, 6);
    }

    private void eyebrow(LinearLayout parent, String text) {
        TextView header = label(text.toUpperCase(Locale.ENGLISH), 12, Ui.GREEN, true);
        header.setLetterSpacing(.075f);
        parent.addView(header);
        gap(parent, 4);
    }

    private LinearLayout figure(String value, String name, int color, int size) {
        LinearLayout group = column();
        TextView number = label(value, size, color, true);
        number.setLetterSpacing(-.025f);
        group.addView(number);
        gap(group, 4);
        group.addView(label(name, 14, Ui.INK, false));
        return group;
    }

    private LinearLayout metric(String value, String name, String detail, int fill, int size) {
        LinearLayout tile = panel(fill, false);
        tile.addView(figure(value, name, Ui.INK, size));
        if (detail != null) {
            gap(tile, 6);
            tile.addView(caption(detail));
        }
        return tile;
    }

    private TextView action(String text, View.OnClickListener listener) {
        TextView button = label(text, 14, Ui.GREEN, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.MINT_SOFT), shape(Ui.CARD, true), null));
        button.setOnClickListener(listener);
        button.setFocusable(true);
        return button;
    }

    private void disclosure(String title, LinearLayout details, boolean forAccuracy) {
        TextView toggle = action(title, v -> {
            if (forAccuracy) accuracyExpanded = !accuracyExpanded;
            else patternsExpanded = !patternsExpanded;
            setDisclosure((TextView) v, details, forAccuracy ? accuracyExpanded : patternsExpanded);
        });
        toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        toggle.setCompoundDrawablePadding(dp(10));
        setDisclosure(toggle, details, forAccuracy ? accuracyExpanded : patternsExpanded);
        content.addView(toggle);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.topMargin = dp(8);
        content.addView(details, params);
    }

    private void setDisclosure(TextView button, LinearLayout details, boolean expanded) {
        details.setVisibility(expanded ? View.VISIBLE : View.GONE);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                expanded ? R.drawable.insights_chevron_up : R.drawable.insights_chevron, 0);
        ViewCompat.setStateDescription(button, expanded ? "Expanded" : "Collapsed");
    }

    private GradientDrawable shape(int fill, boolean outline) {
        GradientDrawable result = Ui.round(fill, dp(14));
        if (outline) result.setStroke(dp(1), Ui.LINE);
        return result;
    }

    private LinearLayout panel(int fill, boolean outline) {
        LinearLayout group = column();
        group.setBackground(shape(fill, outline));
        group.setPadding(dp(16), dp(14), dp(16), dp(14));
        return group;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private boolean stackColumns() {
        android.content.res.Configuration config = getResources().getConfiguration();
        return config.screenWidthDp < 340 || config.fontScale > 1.25f;
    }

    private void pair(LinearLayout parent, View first, View second) {
        boolean stacked = stackColumns();
        LinearLayout group = stacked ? column() : row();
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                stacked ? MATCH_PARENT : 0, stacked ? WRAP_CONTENT : MATCH_PARENT, stacked ? 0 : 1);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(left);
        if (stacked) right.topMargin = dp(8);
        else right.setMarginStart(dp(10));
        group.addView(first, left);
        group.addView(second, right);
        parent.addView(group);
    }

    private void lineWithValue(LinearLayout parent, View title, View value) {
        if (stackColumns()) {
            parent.addView(title);
            gap(parent, 3);
            parent.addView(value);
        } else {
            LinearLayout group = row();
            group.addView(title, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.setMarginStart(dp(8));
            group.addView(value, params);
            parent.addView(group);
        }
    }

    private void titleAndValue(LinearLayout parent, String title, String value) {
        TextView name = label(title, 20, Ui.INK, true);
        ViewCompat.setAccessibilityHeading(name, true);
        lineWithValue(parent, name, label(value, 14, Ui.INK, false));
    }

    private void barRow(LinearLayout parent, String name, String value, double fraction, int color, boolean category) {
        LinearLayout key = row();
        if (category) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            View mark = new View(this);
            mark.setBackground(dot);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(9), dp(9));
            params.setMarginEnd(dp(6));
            key.addView(mark, params);
        }
        key.addView(label(name, 13, Ui.INK, false));
        TextView amount = label(value, 13, Ui.INK, false);
        amount.setGravity(Gravity.END);
        View bar = new ShareBar(this, (float) fraction, color, Ui.LINE);
        if (stackColumns()) {
            parent.addView(key);
            parent.addView(amount);
            gap(parent, 5);
            parent.addView(bar, new LinearLayout.LayoutParams(MATCH_PARENT, dp(5)));
        } else {
            LinearLayout group = row();
            group.addView(key, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams track = new LinearLayout.LayoutParams(0, dp(6), 1.05f);
            track.setMarginStart(dp(5));
            track.setMarginEnd(dp(7));
            group.addView(bar, track);
            group.addView(amount, new LinearLayout.LayoutParams(0, WRAP_CONTENT, category ? 1.25f : .72f));
            parent.addView(group);
        }
    }

    private void divider(LinearLayout parent) {
        gap(parent, 11);
        parent.addView(Ui.divider(this));
        gap(parent, 11);
    }

    private void gap(LinearLayout parent, int height) { Ui.space(parent, height); }

    private static String count(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    private static String duration(long minutes) {
        if (minutes < 60) return minutes + " min";
        return minutes % 60 == 0 ? (minutes / 60) + " h" : decimal(minutes / 60.0) + " h";
    }

    private static String exactDuration(long minutes) {
        if (minutes < 60 || minutes % 60 == 0) return duration(minutes);
        return (minutes / 60) + " h " + (minutes % 60) + " m";
    }

    private static String decimal(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    private static String signed(double value) {
        return Math.abs(value) < .05 ? "0.0" : String.format(Locale.ENGLISH, "%+.1f", value);
    }

    private int dp(int value) { return Ui.dp(this, value); }

    private static final class ShareBar extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float share;
        private final int fill, track;

        ShareBar(android.content.Context context, float share, int fill, int track) {
            super(context);
            this.share = Math.max(0f, Math.min(share, 1f));
            this.fill = fill;
            this.track = track;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float radius = getHeight() / 2f;
            paint.setColor(track);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
            if (share > 0) {
                paint.setColor(fill);
                canvas.drawRoundRect(0, 0, getWidth() * share, getHeight(), radius, radius, paint);
            }
        }
    }
}

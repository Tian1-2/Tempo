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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.tempo.app.insights.InsightsIcon;
import com.tempo.app.insights.InsightsRepository;
import com.tempo.app.insights.InsightsStats;
import com.tempo.app.insights.RecordedTimeChart;
import com.tempo.app.learning.LearningSummary;
import com.tempo.app.learning.LearningRepository;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.model.Task;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.insights.TimeOfDayStats;
import java.util.List;
import java.util.UUID;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native, read-only Insights. All values come from the existing local database. */
public class InsightsActivity extends AppCompatActivity {
    private int white, ice, pale, navy, muted, blue, line;
    private LinearLayout content;
    private ScrollView scroll;
    private InsightsIcon refresh;
    private InsightsRepository repository;
    private boolean loading;
    private int restoreScroll;
    private boolean accuracyExpanded;
    private LearningRepository actions;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        white = Color.WHITE; // card fill; the page itself uses insights_white (off-white) from the layout
        ice = getColor(R.color.insights_ice);
        pale = getColor(R.color.insights_pale);
        navy = getColor(R.color.insights_navy);
        muted = getColor(R.color.insights_muted);
        blue = getColor(R.color.insights_blue);
        line = getColor(R.color.insights_line);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Build.VERSION.SDK_INT >= 26 ? Color.TRANSPARENT : navy);
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
        if (savedInstanceState != null) restoreScroll = savedInstanceState.getInt("insightsScroll", 0);
        buildToolbar();
        repository = new InsightsRepository(this);
        actions = new LearningRepository(this);
        if(savedInstanceState!=null) accuracyExpanded=savedInstanceState.getBoolean("accuracyExpanded",false);
    }

    @Override protected void onResume() { super.onResume(); load(); }
    @Override protected void onDestroy() {
        if (repository != null) repository.close();
        if(actions!=null) actions.close();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("insightsScroll", scroll.getScrollY());
        state.putBoolean("accuracyExpanded",accuracyExpanded);
        super.onSaveInstanceState(state);
    }

    private void buildToolbar() {
        LinearLayout toolbar = findViewById(R.id.insights_toolbar);
        InsightsIcon back = iconButton(InsightsIcon.Kind.BACK, R.string.insights_back);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView home = label(getString(R.string.insights_home), 14, muted, false);
        home.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(home, new LinearLayout.LayoutParams(0, -2, 1));
        InsightsIcon info = iconButton(InsightsIcon.Kind.INFO, R.string.insights_definitions);
        info.setOnClickListener(v -> showDefinitions());
        toolbar.addView(info, new LinearLayout.LayoutParams(dp(48), dp(48)));
        refresh = iconButton(InsightsIcon.Kind.REFRESH, R.string.insights_refresh);
        refresh.setOnClickListener(v -> { restoreScroll = scroll.getScrollY(); load(); });
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }

    private void load() {
        if (loading) return;
        loading = true;
        refresh.setEnabled(false);
        refresh.setAlpha(.4f);
        header();
        LinearLayout card = card(ice, false);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminateTintList(ColorStateList.valueOf(blue));
        card.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        gap(card, 16);
        card.addView(label(getString(R.string.insights_loading), 20, navy, true));
        gap(card, 8);
        card.addView(label(getString(R.string.insights_loading_body), 14, muted, false));
        ViewCompat.setAccessibilityLiveRegion(card, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        addCard(card);
        repository.load(new InsightsRepository.Callback() {
            @Override public void onLoaded(InsightsStats stats, LearningSummary learning) {
                if (isFinishing() || isDestroyed()) return;
                finishLoading();
                render(stats, learning);
            }
            @Override public void onError() {
                if (isFinishing() || isDestroyed()) return;
                finishLoading();
                header();
                LinearLayout error = card(ice, false);
                error.addView(label(getString(R.string.insights_error), 22, navy, true));
                gap(error, 8);
                error.addView(label(getString(R.string.insights_error_body), 14, muted, false));
                gap(error, 16);
                error.addView(action(getString(R.string.insights_retry), v -> load()));
                addCard(error);
            }
        });
    }

    private void finishLoading() {
        loading = false;
        refresh.setEnabled(true);
        refresh.setAlpha(1f);
    }

    private void header() {
        content.removeAllViews();
        TextView title = label(getString(R.string.insights_title), 34, navy, true);
        ViewCompat.setAccessibilityHeading(title, true);
        content.addView(title);
        gap(content, 8);
        content.addView(label(getString(R.string.insights_subtitle), 15, muted, false));
        gap(content, 16);
        LinearLayout shortcuts=row();
        shortcuts.addView(action("Log activity",v->logActivity()),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams historyParams=new LinearLayout.LayoutParams(0,-2,1);historyParams.leftMargin=dp(12);
        shortcuts.addView(action("Activity history",v->startActivity(new Intent(this,SessionHistoryActivity.class))),historyParams);
        content.addView(shortcuts);gap(content,20);
    }

    private void render(InsightsStats stats, LearningSummary learning) {
        header();
        LinearLayout hero = card(ice, false);
        LinearLayout heroTop = row();
        heroTop.addView(new InsightsIcon(this, InsightsIcon.Kind.BARS, blue),
                new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView kicker = label(getString(R.string.insights_week), 12, blue, true);
        kicker.setLetterSpacing(.10f);
        LinearLayout.LayoutParams kickerParams = new LinearLayout.LayoutParams(-2, -2);
        kickerParams.leftMargin = dp(12);
        heroTop.addView(kicker, kickerParams);
        hero.addView(heroTop);
        gap(hero, 20);
        String headline, detail;
        LearningSummary.Preview next=null;
        for(LearningSummary.Preview preview:learning.previews) if(!preview.fixed && preview.samples>0 && preview.prediction!=preview.estimate) {next=preview;break;}
        if(next!=null) {
            kicker.setText(R.string.insights_estimate_preview);
            headline=next.prediction>next.estimate?"Make a little more room for this task.":"A lighter estimate for this task.";
            detail=next.title+": "+next.estimate+" → "+next.prediction+" min. Based on "+next.samples+" completed "+next.basis+" "+(next.samples==1?"task.":"tasks.");
        } else if (stats.recordedMinutes == 0) {
            headline = getString(R.string.insights_empty_title);
            detail = getString(R.string.insights_empty_body);
        } else if (stats.sessionCount >= 3 && !stats.categories.isEmpty()
                && stats.categories.get(0).minutes > stats.recordedMinutes / 2.0) {
            InsightsStats.Category top = stats.categories.get(0);
            String category = top.name;
            if (category.equals("Study") || category.equals("Work") || category.equals("Life")) {
                category = category.toLowerCase(Locale.ENGLISH);
            }
            headline = getString(R.string.insights_hero_category, category);
            detail = getString(R.string.insights_hero_category_body, duration(top.minutes));
        } else {
            headline = getString(R.string.insights_hero_days);
            detail = getString(R.string.insights_hero_days_body, duration(stats.recordedMinutes), stats.activeDays);
        }
        hero.addView(label(headline, 24, navy, true));
        gap(hero, 12);
        hero.addView(label(detail, 14, muted, false));
        if (stats.recordedMinutes == 0) {
            gap(hero, 16);
            hero.addView(action(getString(R.string.insights_open_schedule), v -> openSchedule()));
        }
        addCard(hero);
        learningCard(learning);
        summary(stats);
        chart(stats);
        categories(stats);
        accuracyCard(learning);
        difficultyCard(stats,learning);
        habitsCard(stats,learning);
        timeOfDayCard(learning.timeOfDay);
        gap(content, 8);
        TextView footer = label(getString(R.string.insights_footer), 12, muted, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer);
        scroll.post(() -> scroll.scrollTo(0, restoreScroll));
    }

    private void summary(InsightsStats stats) {
        LinearLayout group = new LinearLayout(this);
        boolean stack = getResources().getConfiguration().screenWidthDp < 340
                || getResources().getConfiguration().fontScale > 1.25f;
        group.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        LinearLayout completion = metric(InsightsIcon.Kind.CHECK,
                stats.taskCount == 0 ? "—" : stats.completionPercent() + "%",
                getString(R.string.insights_completion), stats.taskCount == 0
                        ? getString(R.string.insights_no_tasks)
                        : getString(stats.taskCount == 1 ? R.string.insights_completion_detail_one
                                : R.string.insights_completion_detail, stats.completedTasks, stats.taskCount));
        LinearLayout time = metric(InsightsIcon.Kind.CLOCK, duration(stats.recordedMinutes),
                getString(R.string.insights_time), getString(stats.sessionCount == 1 ? R.string.insights_time_detail_one
                        : R.string.insights_time_detail, stats.sessionCount));
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(stack ? -1 : 0, stack ? -2 : -1, stack ? 0 : 1);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(stack ? -1 : 0, stack ? -2 : -1, stack ? 0 : 1);
        if (stack) second.topMargin = dp(12); else second.leftMargin = dp(12);
        group.addView(completion, first);
        group.addView(time, second);
        content.addView(group, new LinearLayout.LayoutParams(-1, -2));
        gap(content, 16);
    }

    private LinearLayout metric(InsightsIcon.Kind kind, String value, String title, String detail) {
        LinearLayout card = card(pale, false);
        card.setPadding(dp(16), dp(20), dp(16), dp(20));
        card.addView(new InsightsIcon(this, kind, blue), new LinearLayout.LayoutParams(dp(24), dp(24)));
        gap(card, 16);
        card.addView(label(value, 28, navy, true));
        gap(card, 6);
        card.addView(label(title, 14, navy, true));
        gap(card, 6);
        card.addView(label(detail, 13, muted, false));
        return card;
    }

    private void chart(InsightsStats stats) {
        LinearLayout card = card(white, true);
        heading(card, getString(R.string.insights_chart_title));
        SimpleDateFormat format = new SimpleDateFormat("MMM d", Locale.ENGLISH);
        card.addView(label(format.format(new Date(stats.dayStarts[0])) + " – "
                + format.format(new Date(stats.dayStarts[6])), 13, muted, false));
        gap(card, 16);
        if (stats.recordedMinutes == 0) {
            TextView empty = label(getString(R.string.insights_chart_empty), 14, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28));
            card.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            int height = dp(184) + (int) (26 * getResources().getDisplayMetrics().scaledDensity);
            card.addView(new RecordedTimeChart(this, stats), new LinearLayout.LayoutParams(-1, height));
        }
        gap(card, 12);
        card.addView(label(getString(R.string.insights_chart_note), 13, muted, false));
        addCard(card);
    }

    private void categories(InsightsStats stats) {
        LinearLayout card = card(pale, false);
        heading(card, getString(R.string.insights_categories));
        card.addView(label(getString(R.string.insights_categories_note), 13, muted, false));
        gap(card, 20);
        if (stats.categories.isEmpty()) {
            card.addView(label(getString(R.string.insights_categories_empty), 14, muted, false));
        } else {
            for (int i = 0; i < stats.categories.size(); i++) {
                InsightsStats.Category category = stats.categories.get(i);
                if (i > 0) gap(card, 20);
                // Vertical text also accommodates long custom category names and large fonts.
                card.addView(label(category.name, 15, navy, true));
                gap(card, 4);
                int percent = (int) Math.round(100.0 * category.minutes / stats.recordedMinutes);
                card.addView(label(duration(category.minutes) + " · " + percent + "%", 13, muted, false));
                gap(card, 8);
                View track = new ShareBar(this, (float) category.minutes / stats.recordedMinutes, blue, line);
                track.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                card.addView(track, new LinearLayout.LayoutParams(-1, dp(5)));
            }
        }
        addCard(card);
    }

    private void learningCard(LearningSummary summary) {
        LinearLayout card=card(ice,false);
        heading(card,"Your time estimates");
        card.addView(label(summary.historyTasks+" completed "+(summary.historyTasks==1?"task":"tasks")+" available · all history",13,muted,false));
        if(summary.previews.isEmpty()) {
            gap(card,12);card.addView(label("Add a task to see your next estimate. Completed tasks with fully recorded time help Tempo make small adjustments.",14,navy,false));
        } else {
            gap(card,12);card.addView(label("Pending & in progress",13,muted,false));
            for(LearningSummary.Preview preview:summary.previews) {
                divider(card);card.addView(label(preview.title,16,navy,true));gap(card,8);
                card.addView(label((preview.fixed?"Original estimate ":"Your estimate ")+preview.estimate+" min  →  Tempo "+preview.prediction+" min",15,blue,true));gap(card,6);
                String basis=preview.fixed?"Saved prediction · fixed for this task":preview.samples==0?"No matching history yet · using your estimate"
                        :"Based on "+preview.samples+" completed "+preview.basis+" "+(preview.samples==1?"task":"tasks");
                card.addView(label(basis,13,muted,false));
                if(preview.fixed) {gap(card,8);card.addView(label("Remaining for planning: "+preview.remaining+" min",14,navy,true));}
                if(preview.scopeChanged) {gap(card,6);card.addView(label("Task scope changed. The original comparison is kept; this task is excluded from duration learning and accuracy.",12,muted,false));}
            }
        }
        addCard(card);
    }

    private void accuracyCard(LearningSummary summary) {
        LinearLayout card=card(white,true);heading(card,"How close were the estimates?");
        card.addView(label("All evaluated tasks · "+summary.evaluatedTasks+" "+(summary.evaluatedTasks==1?"task":"tasks"),13,muted,false));gap(card,12);
        if(summary.evaluatedTasks==0) {
            card.addView(label("No comparisons yet.",16,navy,true));gap(card,6);
            card.addView(label("Start a task with the timer and record its work through completion. Tempo keeps the prediction from before you started.",13,muted,false));
        } else {
            card.addView(label("Average absolute error · lower is better",13,muted,false));gap(card,8);
            card.addView(label(String.format(Locale.ENGLISH,"Your estimates  %.1f min",summary.estimateMae),15,navy,false));gap(card,4);
            card.addView(label(String.format(Locale.ENGLISH,"Tempo estimates  %.1f min",summary.predictionMae),18,blue,true));gap(card,12);
            card.addView(label(summary.evaluatedTasks==1?"One task is a starting point, not a trend.":"Same tasks, same recorded outcomes. More records are needed to assess a lasting improvement.",13,muted,false));
            LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);
            details.setVisibility(accuracyExpanded?View.VISIBLE:View.GONE);
            divider(details);
            double bias=summary.predictionBias;
            String explanation=Math.round(Math.abs(bias)*10)==0
                    ?(summary.evaluatedTasks==1?"For this task, Tempo matched the recorded time.":"Positive and negative errors balance out on average; individual errors may still be large.")
                    :String.format(Locale.ENGLISH,summary.evaluatedTasks==1?"For this task, Tempo estimated %.1f min %s than recorded time.":"Across these tasks, Tempo estimates were %.1f min %s than recorded time on average.",Math.abs(bias),bias>0?"higher":"lower");
            details.addView(label(explanation,14,navy,false));gap(details,8);
            details.addView(label(String.format(Locale.ENGLISH,"MAE · Your estimates %.1f / Tempo %.1f min",summary.estimateMae,summary.predictionMae),13,navy,false));gap(details,6);
            details.addView(label(String.format(Locale.ENGLISH,"Signed bias: %+.1f min · estimate minus recorded time",Math.abs(bias)<.05?0.0:bias),12,muted,false));gap(details,8);
            details.addView(label("Predictions were saved before work began. Corrections to already-recorded time are included. Missing or later-added time, changed task scope and retrospective manual entries are excluded. Older time edits with uncertain recording history are also excluded.",13,muted,false));
            TextView toggle=action(accuracyExpanded?"Hide calculation details":"Show calculation details",v->{});
            toggle.setOnClickListener(v->{accuracyExpanded=!accuracyExpanded;details.setVisibility(accuracyExpanded?View.VISIBLE:View.GONE);toggle.setText(accuracyExpanded?"Hide calculation details":"Show calculation details");});
            card.addView(toggle);card.addView(details);
        }
        addCard(card);
    }

    private void difficultyCard(InsightsStats stats,LearningSummary summary) {
        LinearLayout card=card(pale,false);heading(card,"How tasks felt");
        card.addView(label("Last 7 days · optional session ratings",13,muted,false));gap(card,12);
        if(stats.ratedSessions==0) card.addView(label("No ratings yet. Add one when saving an activity if you want to compare how it felt with your expectation.",14,muted,false));
        else {
            card.addView(label(String.format(Locale.ENGLISH,"%.1f / 5 reported difficulty",stats.averageDifficulty),18,navy,true));gap(card,6);
            card.addView(label(stats.ratedSessions+" saved "+(stats.ratedSessions==1?"rating":"ratings")+" · unrated activities excluded",13,muted,false));
        }
        if(summary.difficultyComparisons>0) {
            divider(card);double delta=summary.difficultyDelta;
            String comparison=Math.round(Math.abs(delta)*10)==0?"Matched the expected difficulty on average.":String.format(Locale.ENGLISH,"Rated %.1f points %s than expected on average.",Math.abs(delta),delta>0?"higher":"lower");
            card.addView(label(comparison,15,navy,true));gap(card,6);
            card.addView(label(summary.difficultyComparisons+" paired "+(summary.difficultyComparisons==1?"rating":"ratings")+" · reported minus expected",13,muted,false));
        }
        addCard(card);
    }

    private void habitsCard(InsightsStats stats,LearningSummary summary) {
        LinearLayout card=card(white,true);heading(card,"Completion & consistency");
        card.addView(label(stats.activeDays+" of the last 7 days with recorded time",16,navy,true));gap(card,6);
        card.addView(label("Days without records are not necessarily days without work.",13,muted,false));
        if(summary.untimedRecords>0) {gap(card,12);card.addView(label(summary.untimedRecords+" untimed "+(summary.untimedRecords==1?"activity":"activities")+" saved in the last 7 days · excluded from time totals",13,muted,false));}
        divider(card);
        if(summary.deadlineTasks>0) {
            card.addView(label(summary.onTimeTasks+" / "+summary.deadlineTasks+" completed by their deadline",16,navy,true));gap(card,6);
            card.addView(label("Among completed tasks with a deadline saved before work began · all history. Unfinished overdue tasks are not included.",13,muted,false));
        } else card.addView(label("No deadline comparisons yet. Completed tasks with a deadline saved before work began will appear here.",14,muted,false));
        addCard(card);
    }

    private void timeOfDayCard(TimeOfDayStats stats) {
        LinearLayout card=card(pale,false);heading(card,"When you worked");
        card.addView(label("Last 7 days · active timer intervals",13,muted,false));gap(card,12);
        long total=0;for(long value:stats.millis)total+=value;
        if(total==0) card.addView(label("Save a new timer session to start seeing your time-of-day pattern. Earlier records and manual entries do not contain reliable execution intervals.",14,muted,false));
        else {
            for(int i=0;i<stats.millis.length;i++) {
                if(i>0)gap(card,14);
                long minutes=(stats.millis[i]+59_999)/60_000;
                card.addView(label(TimeOfDayStats.LABELS[i]+" · "+duration(minutes),14,navy,true));gap(card,6);
                card.addView(new ShareBar(this,(float)stats.millis[i]/total,blue,line),new LinearLayout.LayoutParams(-1,dp(5)));
            }
            gap(card,16);card.addView(label(stats.sessions+" timed "+(stats.sessions==1?"session":"sessions")+" · minutes rounded up for display",12,muted,false));
        }
        gap(card,12);card.addView(label("Time spent shows when you worked, not how productive you were. Pauses, manual entries and adjusted durations are excluded.",13,muted,false));
        addCard(card);
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
            public void onError(Exception e) {new AlertDialog.Builder(InsightsActivity.this).setMessage("Could not load tasks. Please try again.").setPositiveButton("Got it",null).show();}
        });
    }

    private void showDefinitions() {
        LinearLayout panel = card(white, false);
        heading(panel, getString(R.string.insights_definitions));
        panel.addView(label(getString(R.string.insights_definitions_body), 14, muted, false));
        gap(panel, 20);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(scroller).create();
        panel.addView(action(getString(R.string.insights_close), v -> dialog.dismiss()));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(shape(white, false));
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
        InsightsIcon icon = new InsightsIcon(this, kind, muted);
        icon.setContentDescription(getString(description));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        icon.setFocusable(true);
        icon.setBackground(new RippleDrawable(ColorStateList.valueOf(ice), null, shape(white, false)));
        return icon;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private TextView action(String text, View.OnClickListener listener) {
        TextView button = label(text, 14, blue, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(ice), shape(white, true), null));
        button.setOnClickListener(listener);
        button.setFocusable(true);
        return button;
    }

    private LinearLayout card(int color, boolean border) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        // App-wide card style: white cards float on the off-white page with a soft shadow, no border.
        card.setBackground(shape(color, false));
        if (color == white) card.setElevation(dp(2));
        return card;
    }

    private GradientDrawable shape(int color, boolean border) {
        // GradientDrawable is Android's shape drawable; this is a single flat color.
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(22));
        if (border) shape.setStroke(dp(1), line);
        return shape;
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    private void heading(LinearLayout parent, String text) {
        TextView heading = label(text, 19, navy, true);
        ViewCompat.setAccessibilityHeading(heading, true);
        parent.addView(heading);
        gap(parent, 8);
    }
    private void addCard(LinearLayout card) {
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        gap(content, 16);
    }
    private void divider(LinearLayout parent) {
        gap(parent, 20);
        View divider = new View(this);
        divider.setBackgroundColor(line);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        gap(parent, 20);
    }
    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private static String duration(long minutes) {
        if (minutes < 60) return minutes + " min";
        return minutes % 60 == 0 ? (minutes / 60) + " h"
                : String.format(Locale.ENGLISH, "%.1f h", minutes / 60.0);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class ShareBar extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float share;
        private final int fill, track;
        ShareBar(android.content.Context context, float share, int fill, int track) {
            super(context); this.share = share; this.fill = fill; this.track = track;
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float radius = getHeight() / 2f;
            paint.setColor(track);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
            paint.setColor(fill);
            canvas.drawRoundRect(0, 0, getWidth() * share, getHeight(), radius, radius, paint);
        }
    }
}

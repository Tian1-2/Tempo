package com.tempo.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tempo.app.ui.Tips;
import com.tempo.app.ui.Ui;

/**
 * "How to use Tempo": every feature, including the ones people rarely find on their own, with
 * where it lives, numbered steps and a button that opens the right page. Cards start folded so
 * the page reads as a table of contents; tapping a card opens its steps.
 */
public class GuideActivity extends Activity {
    private LinearLayout body;

    private static final class Topic {
        final String glyph, title, summary, button; final int accent, surface; final String[] steps;
        final Class<?> page; final String extra;
        Topic(String glyph, String title, String summary, int accent, int surface, Class<?> page, String extra,
              String button, String... steps) {
            this.glyph = glyph; this.title = title; this.summary = summary; this.accent = accent; this.surface = surface;
            this.page = page; this.extra = extra; this.button = button; this.steps = steps;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.window(this);
        Tips.markSeen(this, Tips.TOUR);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 32));
        scroll.addView(body);
        setContentView(scroll);
        Ui.fitSystemBars(this);

        body.addView(Ui.backRow(this, "Back", v -> finish()));
        Ui.space(body, 4);
        body.addView(Ui.title(this, "How Tempo works"));
        Ui.space(body, 4);
        body.addView(Ui.text(this, "Tap a topic to see the steps. Every topic has a button that takes you there.", 14, Ui.MUTED, false));
        Ui.space(body, 16);
        addMap();

        section("Get set up", Ui.GREEN);
        topic(new Topic("☀", "Your daily rhythm", "Wake-up, bedtime, meals and how long you focus",
                Ui.GREEN, Ui.MINT_SOFT, ProfileSettingsActivity.class, null, "Open Profile",
                "Open Profile (bottom bar, right).",
                "Drag a slider or tap a value to type it, e.g. 07:30.",
                "Set Best focus time: hard tasks are planned in the hours after it.",
                "Tap Save changes at the bottom. Tempo re-plans the next 7 days."));
        topic(new Topic("🎓", "Weekly classes and meetings", "Times Tempo must keep free every week",
                Ui.FIXED_INK, Ui.FIXED_BG, ProfileSettingsActivity.class, null, "Open Profile",
                "Profile → Fixed weekly commitments → + Add weekly commitment.",
                "Pick the weekday, start and end, and give it a name (e.g. COMP7506 lecture).",
                "Save your routine. The time shows as a striped grey block on the Schedule.",
                "On a busy day, the \"N fixed\" button on the Schedule jumps to those blocks."));
        topic(new Topic("🏷", "Categories and colours", "Study, Work, Life, or your own",
                Ui.LILAC, Ui.LILAC_SOFT, CategoriesActivity.class, null, "Edit categories",
                "Profile → Categories → Edit categories.",
                "Add one (e.g. Club) and pick a colour. That colour marks its tasks everywhere.",
                "Tap a category's name to rename it. Tasks and history follow the new name."));

        section("Plan your days", Ui.SKY);
        topic(new Topic("＋", "Add a task", "Tempo finds the time for it",
                Ui.SKY, Ui.SKY_SOFT, AddTaskActivity.class, null, "Add a task",
                "Tap + in the middle of the bottom bar.",
                "Enter a name, category, your time estimate and (optional) a deadline.",
                "Tap Save and plan. The task appears on the Schedule within the next 7 days.",
                "Long tasks are split into focus-sized pieces, never shorter than 10 minutes."));
        topic(new Topic("🗓", "Read and adjust the Schedule", "Week strip, 24-hour timeline, breaks",
                Ui.SKY, Ui.SKY_SOFT, SchedulePreviewActivity.class, null, "Open Schedule",
                "Tap a day in the week strip; Today appears at the top when you look at another day.",
                "Tap a coloured block to see its details.",
                "Under Time slots, tap a slot to move it with the time wheels. Moved slots stay put.",
                "Breaks on/off shows or hides the rest time between blocks."));
        topic(new Topic("☰", "Manage all tasks", "Edit, archive, restore or delete",
                Ui.LILAC, Ui.LILAC_SOFT, ManageTasksActivity.class, null, "Open Tasks",
                "Home → Tasks, or Schedule → Manage tasks.",
                "Filter by To do, Done or Archived.",
                "Tap a task to edit it or archive it. Archived tasks keep their history.",
                "In Archived you can restore a task or delete it permanently."));

        section("Do the work", Ui.WARM);
        topic(new Topic("⏱", "Focus timer", "Start, pause, then tell Tempo how it went",
                Ui.WARM, Ui.PEACH_SOFT, SchedulePreviewActivity.class, null, "Open Schedule",
                "Tap Start focus on Home, or tap a block on the Schedule → Start focus.",
                "Pause and resume as needed. While it runs, a banner on the Schedule leads back to it.",
                "At the end, save the minutes, how hard it was and whether the task is done.",
                "Tempo learns from this: its next estimate for similar tasks gets closer."));
        topic(new Topic("🔔", "Reminders", "A notification shortly before each task",
                Ui.WARM, Ui.PEACH_SOFT, ProfileSettingsActivity.class, null, "Open Profile",
                "Profile → Reminders: turn them on.",
                "Choose when: at the start, or 5, 10 or 15 minutes before.",
                "Tapping the notification opens the focus timer for that task."));

        section("Look back", Ui.GREEN);
        topic(new Topic("☀", "Yesterday's review", "A short score and a tip each morning",
                Ui.GREEN, Ui.MINT_SOFT, null, null, null,
                "Opens by itself on your first visit between 07:00 and 09:00.",
                "Any time: tap the Yesterday pill at the top right of Home.",
                "The score combines completion, recorded time and how good your estimates were."));
        topic(new Topic("📊", "Insights", "Overview, Estimates and Patterns",
                Ui.SKY, Ui.SKY_SOFT, InsightsActivity.class, null, "Open Insights",
                "Switch between the three tabs at the top.",
                "Tap ⓘ to see what each number means.",
                "Did work without the timer? Tap + Log activity to add it."));
        topic(new Topic("🕘", "History and corrections", "Every recorded session",
                Ui.SKY, Ui.SKY_SOFT, SessionHistoryActivity.class, null, "Open History",
                "Home → History, Insights → History, or Profile → Activity history.",
                "Tap a record to correct its minutes or difficulty.",
                "Corrections update Insights and what Tempo learns."));

        section("Your data", Ui.LILAC);
        topic(new Topic("⬇", "Export to a spreadsheet", "All activity as a CSV file",
                Ui.LILAC, Ui.LILAC_SOFT, ProfileSettingsActivity.class, null, "Open Profile",
                "Profile → Your data → Export CSV.",
                "Choose where to save it (e.g. Downloads). No storage permission is needed.",
                "Open it in Excel or Google Sheets."));
        topic(new Topic("🌙", "Dark mode", "Follows your phone",
                Ui.INK, Ui.SUBTLE, null, null, null,
                "Switch your phone to dark mode in Android settings; every Tempo page follows."));

        Ui.space(body, 20);
        TextView reset = Ui.link(this, "Show page tips again");
        reset.setOnClickListener(v -> {
            Tips.resetAll(this);
            Tips.markSeen(this, Tips.TOUR);
            Toast.makeText(this, "Tips will appear again on each page", Toast.LENGTH_SHORT).show();
        });
        body.addView(reset);
    }

    /** A small map of the app: what the four tabs are for. */
    private void addMap() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.eyebrow(this, "The bottom bar", Ui.GREEN));
        Ui.space(card, 10);
        mapRow(card, "Home", "Today at a glance and what's next", Ui.GREEN);
        mapRow(card, "Schedule", "Your week, hour by hour", Ui.SKY);
        mapRow(card, "＋", "Add a task from anywhere", Ui.INK);
        mapRow(card, "Insights", "How your time went", Ui.WARM);
        mapRow(card, "Profile", "Routine, classes, reminders, data", Ui.LILAC);
        Ui.space(card, 4);
        card.addView(Ui.text(this, "Other pages (Tasks, History, Add task…) open on top and have Back at the top left.",
                12, Ui.MUTED, false));
        body.addView(card);
    }

    private void mapRow(LinearLayout card, String name, String what, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        TextView chip = Ui.chip(this, name, color, Ui.onAccent());
        chip.setGravity(Gravity.CENTER);
        row.addView(chip, new LinearLayout.LayoutParams(Ui.dp(this, 84), -2));
        TextView text = Ui.text(this, what, 13, Ui.INK, false);
        text.setPadding(Ui.dp(this, 12), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
    }

    private void section(String label, int color) {
        Ui.space(body, 22);
        body.addView(Ui.sectionLabel(this, label, color));
        Ui.space(body, 10);
    }

    private void topic(Topic topic) {
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = Ui.badge(this, topic.glyph, topic.surface, topic.accent);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        badgeParams.rightMargin = Ui.dp(this, 12);
        head.addView(badge, badgeParams);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(this, topic.title, 15, Ui.INK, true));
        titles.addView(Ui.text(this, topic.summary, 12, Ui.MUTED, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = Ui.text(this, "⌄", 18, Ui.MUTED, true);
        head.addView(chevron);
        card.addView(head);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        Ui.space(details, 10);
        for (int i = 0; i < topic.steps.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            TextView number = Ui.text(this, String.valueOf(i + 1), 11, Ui.onAccent(), true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.round(topic.accent, Ui.dp(this, 10)));
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
            numberParams.rightMargin = Ui.dp(this, 10);
            row.addView(number, numberParams);
            row.addView(Ui.text(this, topic.steps[i], 14, Ui.INK, false), new LinearLayout.LayoutParams(0, -2, 1));
            details.addView(row);
        }
        if (topic.page != null) {
            Ui.space(details, 10);
            TextView open = Ui.text(this, topic.button + "  →", 14, topic.accent, true);
            open.setGravity(Gravity.CENTER);
            open.setBackground(Ui.round(topic.surface, Ui.dp(this, 14)));
            open.setOnClickListener(v -> startActivity(new Intent(this, topic.page)));
            details.addView(open, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
        }
        card.addView(details);
        card.setContentDescription(topic.title + ". " + topic.summary + ". Double-tap to show steps.");
        card.setOnClickListener(v -> {
            boolean show = details.getVisibility() != View.VISIBLE;
            details.setVisibility(show ? View.VISIBLE : View.GONE);
            chevron.setText(show ? "⌃" : "⌄");
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(this, 10);
        body.addView(card, params);
    }
}

package com.tempo.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tempo.app.AddTaskActivity;
import com.tempo.app.InsightsActivity;
import com.tempo.app.ProfileSettingsActivity;
import com.tempo.app.R;
import com.tempo.app.SchedulePreviewActivity;
import com.tempo.app.SessionHistoryActivity;

/**
 * The bottom bar shared by Schedule, Insights and History. It matches the bar on the Home page
 * (Schedule · Insights · + · History · Profile) and highlights the current page. Switching pages
 * replaces the current one, so Back always returns to Home.
 */
public final class BottomNav {
    public enum Tab { SCHEDULE, INSIGHTS, HISTORY, NONE }

    private BottomNav() {}

    /**
     * @param onAdd what the + button does, or null to open a new task for today
     */
    public static View create(Activity activity, Tab current, Runnable onAdd) {
        FrameLayout holder = new FrameLayout(activity);
        holder.setClipChildren(false);
        holder.setClipToPadding(false);
        holder.setPadding(dp(activity, 16), dp(activity, 6), dp(activity, 16), dp(activity, 10));

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable card = Ui.round(Ui.CARD, dp(activity, 24));
        bar.setBackground(card);
        bar.setElevation(dp(activity, 6));
        bar.addView(item(activity, R.drawable.ic_nav_schedule, "Schedule", current == Tab.SCHEDULE,
                () -> go(activity, current, Tab.SCHEDULE)), weight());
        bar.addView(item(activity, R.drawable.ic_nav_insights, "Insights", current == Tab.INSIGHTS,
                () -> go(activity, current, Tab.INSIGHTS)), weight());
        bar.addView(new View(activity), new LinearLayout.LayoutParams(dp(activity, 72), 1));
        bar.addView(item(activity, R.drawable.ic_nav_history, "History", current == Tab.HISTORY,
                () -> go(activity, current, Tab.HISTORY)), weight());
        bar.addView(item(activity, R.drawable.ic_nav_profile, "Profile", false,
                () -> activity.startActivity(new Intent(activity, ProfileSettingsActivity.class))), weight());
        holder.addView(bar, new FrameLayout.LayoutParams(-1, dp(activity, 64), Gravity.BOTTOM));

        ImageView add = new ImageView(activity);
        add.setImageResource(R.drawable.ic_nav_add);
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setBackground(Ui.round(Ui.ACTION, dp(activity, 19)));
        add.setElevation(dp(activity, 10));
        add.setContentDescription("Add task");
        add.setOnClickListener(v -> {
            if (onAdd != null) onAdd.run();
            else activity.startActivity(new Intent(activity, AddTaskActivity.class));
        });
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(activity, 54), dp(activity, 54),
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        holder.addView(add, addParams);
        holder.setMinimumHeight(dp(activity, 84));
        return holder;
    }

    private static LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -1, 1); }

    private static View item(Activity activity, int icon, String label, boolean selected, Runnable action) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        ImageView image = new ImageView(activity);
        Drawable drawable = activity.getDrawable(icon);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTintList(ColorStateList.valueOf(selected ? Ui.GREEN : Ui.INK));
        }
        image.setImageDrawable(drawable);
        item.addView(image, new LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 22)));
        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextSize(11);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setTextColor(selected ? Ui.GREEN : Ui.MUTED);
        text.setPadding(0, dp(activity, 3), 0, 0);
        item.addView(text);
        item.setContentDescription(label + (selected ? ", current page" : ""));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private static void go(Activity activity, Tab current, Tab target) {
        if (current == target) return;
        Class<?> page = target == Tab.SCHEDULE ? SchedulePreviewActivity.class
                : target == Tab.INSIGHTS ? InsightsActivity.class : SessionHistoryActivity.class;
        Intent intent = new Intent(activity, page);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(intent);
        // Replace rather than stack tab pages, so Back returns to Home.
        if (current != Tab.NONE) {
            activity.finish();
            activity.overridePendingTransition(0, 0);
        }
    }

    private static int dp(Activity activity, int value) { return Ui.dp(activity, value); }
}

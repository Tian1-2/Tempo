package com.tempo.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tempo.app.AddTaskActivity;
import com.tempo.app.HomeActivity;
import com.tempo.app.InsightsActivity;
import com.tempo.app.ProfileSettingsActivity;
import com.tempo.app.R;
import com.tempo.app.SchedulePreviewActivity;

/**
 * The one bottom bar of the app, on the four top-level pages only:
 * Home · Schedule · (+) · Insights · Profile. It mirrors the bar drawn by the Home web page.
 *
 * <p>Page hierarchy: Home is the root and stays underneath. The other three tabs replace each other
 * on top of it, so Back from any tab returns to Home. Secondary pages (History, Tasks, Add task,
 * Categories, Guide, Focus timer) have a Back row instead of this bar.
 */
public final class BottomNav {
    public enum Tab { HOME, SCHEDULE, INSIGHTS, PROFILE }

    /** A tab page with unsaved work implements this to ask before the bar switches away. */
    public interface Guard {
        /** @return true to leave now; false if it will call {@code leave} itself once confirmed */
        boolean canLeave(Runnable leave);
    }

    private BottomNav() {}

    /**
     * @param onAdd what the + button does, or null to open a new task for today
     */
    public static View create(Activity activity, Tab current, Runnable onAdd) {
        FrameLayout holder = new FrameLayout(activity);
        holder.setClipChildren(false);
        holder.setClipToPadding(false);
        holder.setPadding(dp(activity, 16), dp(activity, 20), dp(activity, 16), dp(activity, 12));

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable card = Ui.round(Ui.CARD, dp(activity, 24));
        bar.setBackground(card);
        bar.setElevation(dp(activity, 8));
        bar.addView(item(activity, R.drawable.ic_nav_home, "Home", current == Tab.HOME,
                () -> go(activity, current, Tab.HOME)), weight());
        bar.addView(item(activity, R.drawable.ic_nav_schedule, "Schedule", current == Tab.SCHEDULE,
                () -> go(activity, current, Tab.SCHEDULE)), weight());
        // Same width as a tab, so the five slots are evenly spaced and + sits exactly in the middle.
        bar.addView(new View(activity), weight());
        bar.addView(item(activity, R.drawable.ic_nav_insights, "Insights", current == Tab.INSIGHTS,
                () -> go(activity, current, Tab.INSIGHTS)), weight());
        bar.addView(item(activity, R.drawable.ic_nav_profile, "Profile", current == Tab.PROFILE,
                () -> go(activity, current, Tab.PROFILE)), weight());
        holder.addView(bar, new FrameLayout.LayoutParams(-1, dp(activity, 66), Gravity.BOTTOM));

        ImageView add = new ImageView(activity);
        add.setImageResource(R.drawable.ic_nav_add);
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setBackground(Ui.round(Ui.ACTION, dp(activity, 19)));
        add.setElevation(dp(activity, 12));
        add.setContentDescription("Add task");
        add.setOnClickListener(v -> {
            if (onAdd != null) onAdd.run();
            else activity.startActivity(new Intent(activity, AddTaskActivity.class));
        });
        // Raised above the bar by a third of its height, centred on the middle slot.
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(activity, 56), dp(activity, 56),
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        addParams.bottomMargin = dp(activity, 18);
        holder.addView(add, addParams);
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
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 22));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        item.addView(image, iconParams);
        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextSize(11);
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setTextColor(selected ? Ui.GREEN : Ui.MUTED);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.gravity = Gravity.CENTER_HORIZONTAL;
        textParams.topMargin = dp(activity, 3);
        item.addView(text, textParams);
        if (selected) {
            // A short pill under the label marks the current tab.
            View mark = new View(activity);
            mark.setBackground(Ui.round(Ui.GREEN, dp(activity, 2)));
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(activity, 16), dp(activity, 3));
            markParams.gravity = Gravity.CENTER_HORIZONTAL;
            markParams.topMargin = dp(activity, 3);
            item.addView(mark, markParams);
        }
        item.setContentDescription(label + (selected ? ", current page" : ""));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    /** Opens a tab. Home is always underneath; the other tabs replace each other on top of it. */
    public static void go(Activity activity, Tab current, Tab target) {
        if (current == target) return;
        if (activity instanceof Guard && !((Guard) activity).canLeave(() -> go(activity, current, target))) return;
        if (target == Tab.HOME) {
            Intent home = new Intent(activity, HomeActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(home);
            activity.finish();
            activity.overridePendingTransition(0, 0);
            return;
        }
        Class<?> page = target == Tab.SCHEDULE ? SchedulePreviewActivity.class
                : target == Tab.INSIGHTS ? InsightsActivity.class : ProfileSettingsActivity.class;
        Intent intent = new Intent(activity, page);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
        if (current != Tab.HOME) activity.finish();
    }

    private static int dp(Activity activity, int value) { return Ui.dp(activity, value); }
}

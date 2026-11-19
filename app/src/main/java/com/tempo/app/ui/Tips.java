package com.tempo.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tempo.app.GuideActivity;

/**
 * One-time tip cards. Each page shows its card on the first visits until the person taps "Got it";
 * the full guide is one tap away from every card, from Home and from Profile.
 */
public final class Tips {
    public static final String TOUR = "tour";
    public static final String SCHEDULE = "schedule";
    public static final String INSIGHTS = "insights";
    public static final String PROFILE = "profile";
    public static final String TASKS = "tasks";
    private static final String PREFS = "tempo_tips";

    private Tips() {}

    public static boolean seen(Context context, String key) {
        return prefs(context).getBoolean(key, false);
    }

    public static void markSeen(Context context, String key) {
        prefs(context).edit().putBoolean(key, true).apply();
    }

    /** Shows every tip and the Home tour again. */
    public static void resetAll(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * A tinted card with a title and numbered hints, or null when the tip was dismissed.
     * @param steps short "how to" lines, shown as 1, 2, 3…
     */
    public static View card(Activity activity, String key, String title, int accent, int surface, String... steps) {
        if (seen(activity, key)) return null;
        LinearLayout card = Ui.panel(activity, surface);
        card.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 14), Ui.dp(activity, 16), Ui.dp(activity, 8));

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = Ui.badge(activity, "💡", Ui.CARD, accent);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(Ui.dp(activity, 32), Ui.dp(activity, 32));
        badgeParams.rightMargin = Ui.dp(activity, 10);
        badge.setTextSize(15);
        head.addView(badge, badgeParams);
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.eyebrow(activity, "Tip", accent));
        titles.addView(Ui.text(activity, title, 15, Ui.INK, true));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(head);
        Ui.space(card, 8);

        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(activity);
            row.setPadding(0, Ui.dp(activity, 3), 0, Ui.dp(activity, 3));
            TextView number = Ui.text(activity, String.valueOf(i + 1), 11, Ui.onAccent(), true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.round(accent, Ui.dp(activity, 9)));
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(Ui.dp(activity, 18), Ui.dp(activity, 18));
            numberParams.rightMargin = Ui.dp(activity, 10);
            numberParams.topMargin = Ui.dp(activity, 1);
            row.addView(number, numberParams);
            row.addView(Ui.text(activity, steps[i], 13, Ui.INK, false), new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(row);
        }

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView guide = Ui.text(activity, "Full guide", 13, accent, true);
        guide.setGravity(Gravity.CENTER);
        guide.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
        guide.setOnClickListener(v -> activity.startActivity(new Intent(activity, GuideActivity.class)));
        actions.addView(guide, new LinearLayout.LayoutParams(-2, Ui.dp(activity, 44)));
        TextView ok = Ui.text(activity, "Got it", 13, Ui.INK, true);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        ok.setBackground(Ui.round(Ui.CARD, Ui.dp(activity, 12)));
        actions.addView(ok, new LinearLayout.LayoutParams(-2, Ui.dp(activity, 36)));
        card.addView(actions);
        ok.setOnClickListener(v -> {
            markSeen(activity, key);
            ViewGroup parent = (ViewGroup) card.getParent();
            if (parent != null) parent.removeView(card);
        });
        return card;
    }
}

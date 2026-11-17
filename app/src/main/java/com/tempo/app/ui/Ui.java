package com.tempo.app.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tempo.app.BackChevronView;

/**
 * Shared look for every native page, matching the HTML home page: off-white background, white
 * rounded cards with soft elevation, dark ink text, mint/green accents and uppercase eyebrow labels.
 */
public final class Ui {
    public static final int BG = Color.rgb(247, 248, 245);
    public static final int CARD = Color.WHITE;
    public static final int INK = Color.rgb(30, 48, 52);
    public static final int MUTED = Color.rgb(110, 126, 124);
    public static final int LINE = Color.rgb(228, 234, 229);
    public static final int MINT = Color.rgb(194, 232, 213);
    public static final int MINT_SOFT = Color.rgb(233, 245, 238);
    public static final int GREEN = Color.rgb(52, 126, 91);
    public static final int WARM = Color.rgb(178, 107, 31);
    public static final int WARM_SOFT = Color.rgb(255, 244, 229);
    public static final int RED = Color.rgb(178, 60, 47);
    public static final int STUDY = Color.rgb(63, 143, 107);
    public static final int WORK = Color.rgb(76, 111, 181);
    public static final int LIFE = Color.rgb(208, 138, 62);

    private Ui() {}

    /** Off-white window with dark status and navigation bar icons. */
    @SuppressWarnings("deprecation")
    public static void window(Activity activity) {
        activity.getWindow().setStatusBarColor(BG);
        activity.getWindow().setNavigationBarColor(BG);
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    /**
     * Pads the page clear of the status and navigation bars. Apps targeting Android 15+ are always
     * drawn edge-to-edge, so without this the top row sits under the clock and bottom buttons under
     * the gesture bar. Call after setContentView. The bar areas take the page's background colour.
     */
    public static void fitSystemBars(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        int color = BG;
        if (content instanceof android.view.ViewGroup && ((android.view.ViewGroup) content).getChildCount() > 0) {
            android.graphics.drawable.Drawable bg = ((android.view.ViewGroup) content).getChildAt(0).getBackground();
            if (bg instanceof android.graphics.drawable.ColorDrawable) color = ((android.graphics.drawable.ColorDrawable) bg).getColor();
        }
        content.setBackgroundColor(color);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(content);
    }

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    /** Large page title, e.g. "Schedule". */
    public static TextView title(Context c, String value) {
        TextView view = text(c, value, 30, INK, true);
        view.setLetterSpacing(-0.01f);
        return view;
    }

    /** Small uppercase label above a section. */
    public static TextView eyebrow(Context c, String value) {
        return eyebrow(c, value, GREEN);
    }

    public static TextView eyebrow(Context c, String value, int color) {
        TextView view = text(c, value.toUpperCase(java.util.Locale.US), 11, color, true);
        view.setLetterSpacing(0.14f);
        return view;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** White rounded card with a soft shadow. */
    public static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        card.setBackground(round(CARD, dp(c, 22)));
        card.setElevation(dp(c, 2));
        return card;
    }

    /** Flat tinted panel (tips, notices). */
    public static LinearLayout panel(Context c, int color) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        card.setBackground(round(color, dp(c, 20)));
        return card;
    }

    /** Dark primary action. */
    public static TextView primary(Context c, String label) {
        TextView view = text(c, label, 16, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(INK, dp(c, 16)));
        view.setMinHeight(dp(c, 54));
        return view;
    }

    /** Mint secondary action. */
    public static TextView secondary(Context c, String label) {
        TextView view = text(c, label, 15, INK, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(MINT, dp(c, 16)));
        view.setMinHeight(dp(c, 48));
        return view;
    }

    /** Quiet text action. */
    public static TextView link(Context c, String label) {
        TextView view = text(c, label, 14, GREEN, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(c, 44));
        return view;
    }

    public static TextView chip(Context c, String label, int background, int color) {
        TextView view = text(c, label, 12, color, true);
        view.setSingleLine(true);
        view.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        view.setBackground(round(background, dp(c, 99)));
        return view;
    }

    public static View divider(Context c) {
        View line = new View(c);
        line.setBackgroundColor(LINE);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1))));
        return line;
    }

    public static void space(LinearLayout parent, int dpValue) {
        parent.addView(new View(parent.getContext()), new LinearLayout.LayoutParams(1, dp(parent.getContext(), dpValue)));
    }

    /** Back chevron + label row used at the top of every secondary page. */
    public static LinearLayout backRow(Context c, String label, View.OnClickListener onBack) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTranslationX(-dp(c, 14));
        BackChevronView back = new BackChevronView(c, label);
        back.setOnClickListener(onBack);
        row.addView(back, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        TextView text = text(c, label, 14, MUTED, false);
        text.setOnClickListener(onBack);
        row.addView(text);
        return row;
    }

    public static int categoryColor(String category) {
        if ("Work".equalsIgnoreCase(category)) return WORK;
        if ("Life".equalsIgnoreCase(category)) return LIFE;
        return STUDY;
    }

    public static int categorySoft(String category) {
        if ("Work".equalsIgnoreCase(category)) return Color.rgb(228, 235, 248);
        if ("Life".equalsIgnoreCase(category)) return Color.rgb(250, 238, 222);
        return MINT_SOFT;
    }

    public static String categoryName(String category) {
        if ("Work".equalsIgnoreCase(category)) return "Work";
        if ("Life".equalsIgnoreCase(category)) return "Life";
        return "Study";
    }
}

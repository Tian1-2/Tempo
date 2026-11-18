package com.tempo.app.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Rect;
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
    // Palette tokens. They are not compile-time constants: applyTheme() switches them between the
    // light and dark palettes, so every page reads the current values when it builds its views.
    public static int BG, CARD, INK, MUTED, LINE, MINT, MINT_SOFT, GREEN, WARM, WARM_SOFT, RED;
    public static int STUDY, WORK, LIFE, FIXED_SURFACE, FIXED_BG, FIXED_INK;
    /** Filled buttons (white text on it): dark ink by day, green at night. */
    public static int ACTION;
    /** Neutral surface for completed / inactive blocks and toggles. */
    public static int SUBTLE;
    private static boolean dark;

    static { palette(false); }

    public static boolean isDark() { return dark; }

    /** Picks the palette for the current system light/dark setting. Call before building views. */
    public static void applyTheme(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        palette(mode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
    }

    private static void palette(boolean night) {
        dark = night;
        if (!night) {
            BG = Color.rgb(247, 248, 245); CARD = Color.WHITE; INK = Color.rgb(30, 48, 52);
            MUTED = Color.rgb(110, 126, 124); LINE = Color.rgb(228, 234, 229); MINT = Color.rgb(194, 232, 213);
            MINT_SOFT = Color.rgb(233, 245, 238); GREEN = Color.rgb(52, 126, 91); WARM = Color.rgb(178, 107, 31);
            WARM_SOFT = Color.rgb(255, 244, 229); RED = Color.rgb(178, 60, 47); STUDY = Color.rgb(63, 143, 107);
            WORK = Color.rgb(76, 111, 181); LIFE = Color.rgb(208, 138, 62); FIXED_SURFACE = Color.rgb(239, 245, 242);
            FIXED_BG = Color.rgb(238, 240, 243); FIXED_INK = Color.rgb(96, 106, 120);
            ACTION = Color.rgb(30, 48, 52); SUBTLE = Color.rgb(232, 237, 233);
        } else {
            BG = Color.rgb(18, 24, 22); CARD = Color.rgb(28, 36, 34); INK = Color.rgb(230, 238, 235);
            MUTED = Color.rgb(154, 171, 167); LINE = Color.rgb(46, 56, 53); MINT = Color.rgb(47, 92, 72);
            MINT_SOFT = Color.rgb(30, 45, 39); GREEN = Color.rgb(111, 199, 154); WARM = Color.rgb(227, 161, 90);
            WARM_SOFT = Color.rgb(51, 40, 28); RED = Color.rgb(229, 115, 102); STUDY = Color.rgb(93, 179, 138);
            WORK = Color.rgb(125, 155, 224); LIFE = Color.rgb(224, 160, 96); FIXED_SURFACE = Color.rgb(31, 40, 38);
            FIXED_BG = Color.rgb(36, 42, 49); FIXED_INK = Color.rgb(154, 166, 181);
            ACTION = Color.rgb(52, 126, 91); SUBTLE = Color.rgb(40, 50, 47);
        }
    }

    /** A category's soft card tint: mixed towards white by day, towards the card colour at night. */
    public static int soften(int color) {
        if (!dark) return com.tempo.app.model.Category.soft(color);
        float keep = 0.28f;
        return Color.rgb((int) (Color.red(color) * keep + Color.red(CARD) * (1 - keep)),
                (int) (Color.green(color) * keep + Color.green(CARD) * (1 - keep)),
                (int) (Color.blue(color) * keep + Color.blue(CARD) * (1 - keep)));
    }

    /**
     * Both screens use the same rounded card: a light slate surface with faint diagonal hatching
     * (busy time, not a task) and a four-dp slate stripe clipped to its left edge.
     */
    public static GradientDrawable fixedBackground(Context context) {
        int radius = dp(context, 12);
        int stripeWidth = dp(context, 4);
        int gap = dp(context, 10);
        GradientDrawable accent = round(FIXED_INK, radius);
        android.graphics.Paint hatch = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        hatch.setColor(Color.argb(dark ? 40 : 28, Color.red(FIXED_INK), Color.green(FIXED_INK), Color.blue(FIXED_INK)));
        hatch.setStrokeWidth(dp(context, 1));
        GradientDrawable surface = new GradientDrawable() {
            @Override public void draw(Canvas canvas) {
                super.draw(canvas);
                Rect bounds = getBounds();
                int saved = canvas.save();
                canvas.clipRect(bounds.left + stripeWidth, bounds.top + 1, bounds.right - 1, bounds.bottom - 1);
                int h = bounds.height();
                for (int x = bounds.left - h; x < bounds.right; x += gap) {
                    canvas.drawLine(x, bounds.bottom, x + h, bounds.top, hatch);
                }
                canvas.restoreToCount(saved);
                saved = canvas.save();
                canvas.clipRect(bounds.left, bounds.top, bounds.left + stripeWidth, bounds.bottom);
                accent.setBounds(bounds);
                accent.draw(canvas);
                canvas.restoreToCount(saved);
            }
        };
        surface.setColor(FIXED_BG);
        surface.setCornerRadius(radius);
        return surface;
    }

    private Ui() {}

    /** Off-white window with dark status and navigation bar icons. */
    @SuppressWarnings("deprecation")
    public static void window(Activity activity) {
        activity.getWindow().setStatusBarColor(BG);
        activity.getWindow().setNavigationBarColor(BG);
        activity.getWindow().getDecorView().setSystemUiVisibility(dark ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
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
        view.setBackground(round(ACTION, dp(c, 16)));
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

    public static int categoryColor(String category) { return Categories.color(category); }

    public static int categorySoft(String category) { return soften(Categories.color(category)); }

    public static String categoryName(String category) { return Categories.name(category); }
}

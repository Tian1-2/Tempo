package com.tempo.app.insights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import com.tempo.app.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Seven daily totals. Zero days have no bar. Exact values are announced to TalkBack. */
public final class RecordedTimeChart extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final InsightsStats stats;
    private final int blue, muted, line;
    private final String[] labels = new String[7];

    public RecordedTimeChart(Context context, InsightsStats stats) {
        super(context);
        this.stats = stats;
        blue = context.getColor(R.color.insights_blue);
        muted = context.getColor(R.color.insights_muted);
        line = context.getColor(R.color.insights_line);
        StringBuilder spoken = new StringBuilder();
        SimpleDateFormat shortDay = new SimpleDateFormat("d", Locale.ENGLISH);
        SimpleDateFormat fullDay = new SimpleDateFormat("EEEE, MMM d", Locale.ENGLISH);
        for (int i = 0; i < 7; i++) {
            // Day-of-month labels are disambiguated by the date range above the chart.
            labels[i] = shortDay.format(new Date(stats.dayStarts[i]));
            spoken.append(fullDay.format(new Date(stats.dayStarts[i]))).append(": ")
                    .append(stats.dailyMinutes[i]).append(" minutes. ");
        }
        setContentDescription(context.getString(R.string.insights_chart_accessibility, spoken.toString()));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float textSize = 12 * getResources().getDisplayMetrics().scaledDensity;
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(textSize);
        long max = 0;
        for (long minutes : stats.dailyMinutes) max = Math.max(max, minutes);
        double top = max <= 60 ? 60 : Math.ceil(max / 60.0) * 60;
        String topLabel = formatHours(top);
        String midLabel = formatHours(top / 2);
        float left = Math.max(paint.measureText(topLabel), paint.measureText(midLabel)) + dp(10);
        float right = getWidth() - dp(4);
        float yTop = textSize;
        float bottom = getHeight() - textSize * 1.8f - dp(8);
        if (right <= left || bottom <= yTop) return;
        paint.setTextAlign(Paint.Align.RIGHT);
        for (int tick = 0; tick < 3; tick++) {
            float y = bottom - (bottom - yTop) * tick / 2;
            paint.setColor(line);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(left, y, right, y, paint);
            paint.setColor(muted);
            canvas.drawText(formatHours(top * tick / 2), left - dp(8), y + textSize * .35f, paint);
        }
        float step = (right - left) / 7;
        float width = Math.min(dp(28), step * .60f);
        float widestLabel = 0;
        for (String label : labels) widestLabel = Math.max(widestLabel, paint.measureText(label));
        // Keep all seven bars, but thin the labels when large fonts need more space.
        int labelStride = widestLabel + dp(6) > step ? 2 : 1;
        for (int i = 0; i < 7; i++) {
            float center = left + step * (i + .5f);
            if (stats.dailyMinutes[i] > 0) {
                float height = (float) ((bottom - yTop) * stats.dailyMinutes[i] / top);
                paint.setColor(blue);
                paint.setAlpha(i == 6 ? 255 : 82);
                canvas.drawRoundRect(center - width / 2, bottom - Math.max(dp(2), height),
                        center + width / 2, bottom, dp(5), dp(5), paint);
                paint.setAlpha(255);
            }
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(i == 6 ? blue : muted);
            if (i % labelStride == 0) {
                canvas.drawText(labels[i], center, bottom + textSize + dp(10), paint);
            }
        }
    }

    private String formatHours(double minutes) {
        double hours = minutes / 60;
        return hours == Math.floor(hours) ? String.format(Locale.ENGLISH, "%.0fh", hours)
                : String.format(Locale.ENGLISH, "%.1fh", hours);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}

package com.tempo.app.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

/** Progress ring with a bold centre value and an optional caption underneath. */
public final class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float progress;
    private String value = "";
    private String caption;

    public RingView(Context context, float strokeDp, float valueSp, float captionSp,
                    int trackColor, int arcColor, int valueColor) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[]{track, arc}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(strokeDp * density);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(trackColor);
        arc.setColor(arcColor);
        valuePaint.setColor(valueColor);
        valuePaint.setTextSize(sp(context, valueSp));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        captionPaint.setColor(arcColor);
        captionPaint.setTextSize(sp(context, captionSp));
        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    private static float sp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.getResources().getDisplayMetrics());
    }

    /** @param progress 0..1 */
    public void set(float progress, String value, String caption) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.value = value == null ? "" : value;
        this.caption = caption;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float inset = track.getStrokeWidth() / 2f;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(bounds, 0, 360, false, track);
        if (progress > 0f) canvas.drawArc(bounds, -90, 360 * progress, false, arc);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        if (caption == null) {
            canvas.drawText(value, cx, cy - (valuePaint.descent() + valuePaint.ascent()) / 2f, valuePaint);
        } else {
            float gap = captionPaint.getTextSize() * 0.35f;
            float valueHeight = -valuePaint.ascent();
            float total = valueHeight + gap + captionPaint.getTextSize();
            float top = cy - total / 2f;
            canvas.drawText(value, cx, top + valueHeight, valuePaint);
            canvas.drawText(caption, cx, top + valueHeight + gap + captionPaint.getTextSize(), captionPaint);
        }
    }
}

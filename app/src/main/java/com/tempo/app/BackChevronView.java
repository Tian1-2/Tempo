package com.tempo.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Consistent centered back icon with a full-size touch target. */
public final class BackChevronView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public BackChevronView(Context context, String description) {
        super(context);
        setContentDescription(description);
        paint.setColor(Color.rgb(105, 120, 119));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        path.reset();
        path.moveTo(cx + dp(4), cy - dp(8));
        path.lineTo(cx - dp(4), cy);
        path.lineTo(cx + dp(4), cy + dp(8));
        canvas.drawPath(path, paint);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}

package com.tempo.app.insights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Consistent 24-unit outline icons; no font glyphs or image dependency. */
public final class InsightsIcon extends View {
    public enum Kind { BACK, REFRESH, INFO, BARS, CHECK, CLOCK, CALENDAR, LAYERS }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Kind kind;

    public InsightsIcon(Context context, Kind kind, int color) {
        super(context);
        this.kind = kind;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float iconSize = Math.min(size, 24 * getResources().getDisplayMetrics().density);
        canvas.save();
        canvas.translate((getWidth() - iconSize) / 2, (getHeight() - iconSize) / 2);
        canvas.scale(iconSize / 24, iconSize / 24);
        switch (kind) {
            case BACK: line(canvas, 14, 5, 7, 12, 14, 19); break;
            case REFRESH:
                canvas.drawArc(4, 4, 20, 20, 45, 285, false, paint);
                line(canvas, 20, 3, 20, 9, 14, 9); break;
            case INFO:
                canvas.drawCircle(12, 12, 9, paint);
                canvas.drawLine(12, 11, 12, 17, paint);
                canvas.drawPoint(12, 7, paint); break;
            case CHECK:
                canvas.drawCircle(12, 12, 9, paint);
                line(canvas, 7.5f, 12, 10.5f, 15, 16.5f, 9); break;
            case CLOCK:
                canvas.drawCircle(12, 12, 9, paint);
                line(canvas, 12, 6, 12, 12, 16, 14); break;
            case CALENDAR:
                canvas.drawRoundRect(3, 5, 21, 21, 3, 3, paint);
                canvas.drawLine(3, 10, 21, 10, paint);
                canvas.drawLine(8, 3, 8, 7, paint);
                canvas.drawLine(16, 3, 16, 7, paint); break;
            case LAYERS:
                line(canvas, 3, 8, 12, 3, 21, 8, 12, 13, 3, 8);
                line(canvas, 3, 13, 12, 18, 21, 13);
                line(canvas, 3, 18, 12, 23, 21, 18); break;
            case BARS:
                canvas.drawRoundRect(3, 13, 6, 21, 1, 1, paint);
                canvas.drawRoundRect(10.5f, 4, 13.5f, 21, 1, 1, paint);
                canvas.drawRoundRect(18, 9, 21, 21, 1, 1, paint); break;
        }
        canvas.restore();
    }

    private void line(Canvas canvas, float... points) {
        Path path = new Path();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        canvas.drawPath(path, paint);
    }
}

package com.tempo.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Home entry icon; the destination can be replaced when the main page is ready. */
public final class HomeIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path roof = new Path();

    public HomeIconView(Context context) {
        super(context);
        setContentDescription("Go to home");
        paint.setColor(com.tempo.app.ui.Ui.MUTED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        roof.reset();
        roof.moveTo(cx - dp(9), cy - dp(1));
        roof.lineTo(cx, cy - dp(9));
        roof.lineTo(cx + dp(9), cy - dp(1));
        canvas.drawPath(roof, paint);
        canvas.drawLine(cx - dp(7), cy - dp(2), cx - dp(7), cy + dp(8), paint);
        canvas.drawLine(cx + dp(7), cy - dp(2), cx + dp(7), cy + dp(8), paint);
        canvas.drawLine(cx - dp(7), cy + dp(8), cx + dp(7), cy + dp(8), paint);
        canvas.drawLine(cx - dp(2), cy + dp(8), cx - dp(2), cy + dp(2), paint);
        canvas.drawLine(cx + dp(2), cy + dp(2), cx + dp(2), cy + dp(8), paint);
        canvas.drawLine(cx - dp(2), cy + dp(2), cx + dp(2), cy + dp(2), paint);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}

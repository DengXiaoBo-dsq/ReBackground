package com.dsq.rebackground;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

public class CircleOverlayView extends View {

    private Paint paint;
    private int circleX = 0;
    private int circleY = 0;
    private int circleRadius = 0;

    public CircleOverlayView(Context context) {
        super(context);
        init();
    }

    public CircleOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0xFFFF0000); // 红色
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setAntiAlias(true);
    }

    public void setCircle(int x, int y, int radius) {
        this.circleX = x;
        this.circleY = y;
        this.circleRadius = radius;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (circleRadius > 0) {
            Log.d("CircleOverlayView", "Drawing circle at (" + circleX + ", " + circleY + ") with radius " + circleRadius);
            canvas.drawCircle(circleX, circleY, circleRadius, paint);
        }
    }
}
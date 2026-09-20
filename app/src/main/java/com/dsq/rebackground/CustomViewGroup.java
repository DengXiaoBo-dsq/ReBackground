package com.dsq.rebackground;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

public class CustomViewGroup extends ViewGroup {

    private String text = "Hello, World!";
    private int textColor = Color.BLACK;
    private float textSize = 30f;
    private float textAlpha = 255;
    private TextPaint textPaint;

    private RectF rect = new RectF(100, 100, 300, 300);  // 绘制的矩形框
    private Matrix transformMatrix = new Matrix();  // 变换矩阵（用于旋转和缩放）
    private Bitmap backgroundBitmap;  // 背景图片
    private float lastTouchX, lastTouchY;

    public CustomViewGroup(Context context) {
        super(context);
        init();
    }

    public CustomViewGroup(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint = new TextPaint();
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
        textPaint.setAlpha((int) textAlpha);
        textPaint.setAntiAlias(true);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // 这里可以重新计算子控件的位置，根据需要自定义布局
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 先绘制背景图片
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap, null, new Rect(0, 0, getWidth(), getHeight()), null);
        }

        canvas.save();
        canvas.concat(transformMatrix);  // 应用旋转和缩放矩阵

        // 绘制矩形框（带颜色边框）
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.argb(150, 0, 0, 255));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);
        canvas.drawRect(rect, borderPaint);

        // 绘制文本
        float textWidth = textPaint.measureText(text);
        float textHeight = textPaint.descent() - textPaint.ascent();
        float textX = rect.left + (rect.width() - textWidth) / 2;
        float textY = rect.top + (rect.height() + textHeight) / 2 - textPaint.descent();
        canvas.drawText(text, textX, textY, textPaint);

        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                // 拖动矩形
                float dx = event.getX() - lastTouchX;
                float dy = event.getY() - lastTouchY;
                rect.offset(dx, dy);
                invalidate();
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        return true;
    }

    // 设置背景图片
    public void setBackgroundImage(Bitmap bitmap) {
        backgroundBitmap = bitmap;
        invalidate();
    }

    // 设置文本内容
    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    // 设置文本颜色
    public void setTextColor(int color) {
        textColor = color;
        textPaint.setColor(color);
        invalidate();
    }

    // 设置文本大小
    public void setTextSize(float size) {
        textSize = size;
        textPaint.setTextSize(size);
        invalidate();
    }

    // 设置矩形框的大小
    public void setRectSize(float left, float top, float right, float bottom) {
        rect.set(left, top, right, bottom);
        invalidate();
    }

    // 设置背景透明
    public void setTransparentBackground() {
        setBackgroundColor(Color.TRANSPARENT);
    }

    // 获取矩形的像素坐标
    public RectF getRectCoordinates() {
        return rect;
    }

    // 旋转矩形框
    public void rotate(float angle) {
        transformMatrix.postRotate(angle, rect.centerX(), rect.centerY());
        invalidate();
    }

    // 缩放矩形框
    public void scale(float scaleX, float scaleY) {
        transformMatrix.postScale(scaleX, scaleY, rect.centerX(), rect.centerY());
        invalidate();
    }

    // 获取文本的像素坐标
    public Rect getTextBounds() {
        Rect bounds = new Rect();
        float textWidth = textPaint.measureText(text);
        float textHeight = textPaint.descent() - textPaint.ascent();
        textPaint.getTextBounds(text, 0, text.length(), bounds);
        return bounds;
    }

    // 获取文本在背景图片上的准确像素坐标
    public RectF getTextPixelCoordinates() {
        // 获取文本的实际位置
        float textWidth = textPaint.measureText(text);
        float textHeight = textPaint.descent() - textPaint.ascent();
        float textX = rect.left + (rect.width() - textWidth) / 2;
        float textY = rect.top + (rect.height() + textHeight) / 2 - textPaint.descent();

        // 将坐标转换为背景图片的像素坐标
        float[] points = new float[]{textX, textY};
        transformMatrix.mapPoints(points);
        return new RectF(points[0], points[1], points[0] + textWidth, points[1] + textHeight);
    }
}

package com.dsq.rebackground.ui.colorpicker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class ColorPickerView extends View {
    private static final String TAG = "ColorPickerView";

    // ---- 样式枚举（保留但图片模式下不使用） ----
    public enum Style {
        HSV_DISCONNECTED,
        DUAL_RING,
        RGB_DISCONNECTED
    }

    private Style currentStyle = Style.HSV_DISCONNECTED;

    // ---- 颜色状态 ----
    private int selectedColor = Color.WHITE;
    private float hue = 0f;
    private float saturation = 1f;
    private float value = 1f;

    // ---- 图片模式 ----
    private Bitmap originalBitmap;
    private boolean isCustomPalette = false;
    private float displayScale = 1.0f;
    private float displayOffsetX = 0f;
    private float displayOffsetY = 0f;
    private float scale = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;
    private float minScale = 0.3f;
    private float maxScale = 5.0f;
    private ScaleGestureDetector scaleDetector;
    private boolean isScaling = false;
    private float selectedX = -1, selectedY = -1;

    // ---- 绘制相关 ----
    private Paint paint;
    private Paint crossPaint;
    private Paint textPaint;

    // ---- 监听器 ----
    private OnColorChangedListener listener;

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    public ColorPickerView(Context context) {
        super(context);
        init();
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crossPaint.setColor(Color.WHITE);
        crossPaint.setStrokeWidth(2);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        setFocusable(true);
        setFocusableInTouchMode(true);
        hue = 0;
        saturation = 1f;
        value = 1f;
        selectedColor = Color.WHITE;
    }

    // ---- 公共方法 ----
    public void setStyle(Style style) {
        this.currentStyle = style;
        invalidate();
    }

    public Style getStyle() {
        return currentStyle;
    }

    public void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        selectedColor = color;
        if (isCustomPalette) {
            isCustomPalette = false;
            if (originalBitmap != null) {
                originalBitmap.recycle();
                originalBitmap = null;
            }
            resetUserTransform();
        }
        invalidate();
        if (listener != null) listener.onColorChanged(color);
    }

    public int getSelectedColor() {
        return selectedColor;
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    // ---- 图片模式设置（核心修正） ----
    public void setPaletteDrawable(Drawable drawable) {
        if (drawable == null) return;
        Bitmap bm;
        if (drawable instanceof BitmapDrawable) {
            bm = ((BitmapDrawable) drawable).getBitmap();
        } else {
            int w = getWidth() > 0 ? getWidth() : 500;
            int h = getHeight() > 0 ? getHeight() : 500;
            bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bm);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        // 保存 Bitmap，标记为图片模式
        this.isCustomPalette = true;
        this.originalBitmap = bm;
        resetUserTransform();
        selectedX = -1;
        selectedY = -1;

        // 如果当前已有尺寸，直接计算偏移
        if (getWidth() > 0 && getHeight() > 0) {
            setOriginalBitmap(bm);
        } else {
            // 否则延迟到布局完成后重新计算
            post(() -> {
                if (originalBitmap != null && !originalBitmap.isRecycled()) {
                    setOriginalBitmap(originalBitmap);
                    invalidate();
                }
            });
        }
        // 请求重新布局以触发 onSizeChanged
        requestLayout();
        invalidate();
    }

    public void setHsvPalette() {
        this.isCustomPalette = false;
        if (this.originalBitmap != null && !this.originalBitmap.isRecycled()) {
            this.originalBitmap.recycle();
        }
        this.originalBitmap = null;
        resetUserTransform();
        invalidate();
    }

    public boolean isCustomPalette() {
        return isCustomPalette;
    }

    public Bitmap getCurrentBitmap() {
        return originalBitmap;
    }

    // ---- 坐标映射 ----
    private float[] mapScreenToBitmap(float screenX, float screenY) {
        float userX = (screenX - translateX) / scale;
        float userY = (screenY - translateY) / scale;
        float bmX = (userX - displayOffsetX) / displayScale;
        float bmY = (userY - displayOffsetY) / displayScale;
        return new float[]{bmX, bmY};
    }

    private float[] mapBitmapToScreen(float bmX, float bmY) {
        float userX = bmX * displayScale + displayOffsetX;
        float userY = bmY * displayScale + displayOffsetY;
        float screenX = userX * scale + translateX;
        float screenY = userY * scale + translateY;
        return new float[]{screenX, screenY};
    }

    // ---- 设置原始图片并计算居中偏移 ----
    private void setOriginalBitmap(Bitmap bm) {
        if (this.originalBitmap != null && this.originalBitmap != bm && !this.originalBitmap.isRecycled()) {
            this.originalBitmap.recycle();
        }
        this.originalBitmap = bm;
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth > 0 && viewHeight > 0 && bm != null) {
            float imgWidth = bm.getWidth();
            float imgHeight = bm.getHeight();
            float viewRatio = (float) viewWidth / viewHeight;
            float imgRatio = imgWidth / imgHeight;
            if (imgRatio > viewRatio) {
                displayScale = (float) viewWidth / imgWidth;
            } else {
                displayScale = (float) viewHeight / imgHeight;
            }
            float scaledWidth = imgWidth * displayScale;
            float scaledHeight = imgHeight * displayScale;
            displayOffsetX = (viewWidth - scaledWidth) / 2f;
            displayOffsetY = (viewHeight - scaledHeight) / 2f;
        } else {
            displayScale = 1.0f;
            displayOffsetX = 0;
            displayOffsetY = 0;
        }
    }

    private void resetUserTransform() {
        scale = 1.0f;
        translateX = 0f;
        translateY = 0f;
    }

    // ---- 缩放监听器 ----
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (!isCustomPalette || originalBitmap == null || originalBitmap.isRecycled()) return false;

            float newScale = scale * detector.getScaleFactor();
            newScale = Math.max(minScale, Math.min(maxScale, newScale));

            float centerUserX = displayOffsetX + (originalBitmap.getWidth() * displayScale) / 2f;
            float centerUserY = displayOffsetY + (originalBitmap.getHeight() * displayScale) / 2f;

            float centerScreenX = centerUserX * scale + translateX;
            float centerScreenY = centerUserY * scale + translateY;

            translateX = centerScreenX - centerUserX * newScale;
            translateY = centerScreenY - centerUserY * newScale;

            scale = newScale;
            invalidate();
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            isScaling = true;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isScaling = false;
        }
    }

    // ---- Touch事件 ----
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isCustomPalette) {
            return handleImageTouch(event);
        } else {
            return false;
        }
    }

    private boolean handleImageTouch(MotionEvent event) {
        getParent().requestDisallowInterceptTouchEvent(true);
        scaleDetector.onTouchEvent(event);

        if (event.getPointerCount() > 1 || isScaling) {
            return true;
        }

        if (originalBitmap == null || originalBitmap.isRecycled()) return true;

        float x = event.getX();
        float y = event.getY();
        float[] mapped = mapScreenToBitmap(x, y);
        int ix = (int) mapped[0];
        int iy = (int) mapped[1];
        int bmW = originalBitmap.getWidth();
        int bmH = originalBitmap.getHeight();

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_MOVE) {
            if (ix >= 0 && ix < bmW && iy >= 0 && iy < bmH) {
                selectedX = ix;
                selectedY = iy;
                int color = originalBitmap.getPixel(ix, iy);
                selectedColor = color;
                if (listener != null) listener.onColorChanged(color);
                invalidate();
            }
        }
        return true;
    }

    // ---- onDraw ----
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isInEditMode()) {
            canvas.drawText("图片取色视图", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        if (isCustomPalette && originalBitmap != null && !originalBitmap.isRecycled()) {
            drawImageMode(canvas);
        } else {
            drawNoImageMessage(canvas);
        }
    }

    private void drawNoImageMessage(Canvas canvas) {
        String msg = "请选择图片";
        float x = getWidth() / 2f;
        float y = getHeight() / 2f;
        float textSize = Math.min(getWidth(), getHeight()) / 10f;
        if (textSize < 20) textSize = 20;
        textPaint.setTextSize(textSize);
        canvas.drawText(msg, x, y, textPaint);
    }

    private void drawImageMode(Canvas canvas) {
        if (originalBitmap == null || originalBitmap.isRecycled()) {
            drawNoImageMessage(canvas);
            return;
        }
        canvas.save();
        canvas.translate(translateX, translateY);
        canvas.scale(scale, scale);
        canvas.translate(displayOffsetX, displayOffsetY);
        canvas.scale(displayScale, displayScale);
        canvas.drawBitmap(originalBitmap, 0, 0, paint);
        canvas.restore();

        if (selectedX >= 0 && selectedY >= 0 &&
                selectedX < originalBitmap.getWidth() && selectedY < originalBitmap.getHeight()) {
            float[] screenPos = mapBitmapToScreen(selectedX, selectedY);
            float sx = screenPos[0];
            float sy = screenPos[1];
            int crossSize = 30;
            canvas.drawLine(sx - crossSize, sy, sx + crossSize, sy, crossPaint);
            canvas.drawLine(sx, sy - crossSize, sx, sy + crossSize, crossPaint);
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(Color.RED);
            canvas.drawCircle(sx, sy, 6, dot);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            if (isCustomPalette && originalBitmap != null) {
                // 重新计算居中偏移
                setOriginalBitmap(originalBitmap);
                resetUserTransform();
                invalidate();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
            originalBitmap = null;
        }
    }
}
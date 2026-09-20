
package com.dsq.rebackground;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class WatermarkView extends View {
    public Bitmap imageBitmap;
    public List<Watermark> watermarks = new ArrayList<>();
    public Watermark currentWatermark;
    private int activeIndex = -1;

    private Paint textPaint, borderPaint;
    private float touchStartX, touchStartY;
    private boolean isMoving, isRotating, isScaling;

    public float viewScale;
    public float viewLeft;
    public float viewTop;

    public static final float TEXT_PADDING = 10;
    private static final float BUTTON_OFFSET = 20;
    private Bitmap closeButtonBitmap, addButtonBitmap, scaleButtonBitmap, rotateButtonBitmap;

    static class Watermark implements Parcelable {
        RectF rect = new RectF();

        RectF closeButtonRect = new RectF();
        RectF addButtonRect = new RectF();
        RectF scaleButtonRect = new RectF();
        RectF rotateButtonRect = new RectF();
        float rotation;
        String text = "我们的水印很特别！";
        float textSize = 24;
        int textColor = Color.WHITE;

        RectF textPixelRect = new RectF();
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public enum Type { TEXT, IMAGE }
        public Type type = Type.TEXT;

        public Bitmap patternBitmap;
        public String patternPath;
        public float patternScale = 1.0f;
        public int patternColorFilter = Color.TRANSPARENT;
        public float patternAlpha = 1.0f;

        // 新增：存储初始旋转角度和触摸角度
        private float startRotationAngle;
        private float startTouchAngle;

        // 深拷贝构造函数
        public Watermark(Watermark src) {
            this.rect = new RectF(src.rect);
            this.rotation = src.rotation;
            this.type = src.type;
            this.text = src.text;
            this.textSize = src.textSize;
            this.textColor = src.textColor;
            this.textPaint = new Paint(src.textPaint);

            if (src.patternBitmap != null) {
                this.patternBitmap = src.patternBitmap.copy(Bitmap.Config.ARGB_8888, true);
            }

            this.patternPath = src.patternPath;
            this.patternScale = src.patternScale;
            this.patternColorFilter = src.patternColorFilter;
            this.patternAlpha = src.patternAlpha;

            this.startRotationAngle = src.startRotationAngle;
            this.startTouchAngle = src.startTouchAngle;

            this.closeButtonRect = new RectF();
            this.addButtonRect = new RectF();
            this.scaleButtonRect = new RectF();
            this.rotateButtonRect = new RectF();
        }


        public void initPatternWatermark(Bitmap bitmap, String path) {
            type = Type.IMAGE;
            patternBitmap = bitmap;
            patternPath = path;
        }

        Watermark() {
            textPaint.setTextSize(textSize);
            textPaint.setColor(textColor);
        }

        // Parcelable 实现
        protected Watermark(Parcel in) {
            rect = in.readParcelable(RectF.class.getClassLoader());
            rotation = in.readFloat();
            text = in.readString();
            textSize = in.readFloat();
            textColor = in.readInt();
            type = Type.values()[in.readInt()];
            patternBitmap = in.readParcelable(Bitmap.class.getClassLoader());
            patternPath = in.readString();
            patternScale = in.readFloat();
            patternColorFilter = in.readInt();
            patternAlpha = in.readFloat();
            startRotationAngle = in.readFloat();
            startTouchAngle = in.readFloat();
        }
        @Override
        public String toString() {
            return "Watermark{" +
                    "type=" + type +
                    ", text='" + text + '\'' +
                    ", rect=" + rect +
                    '}';
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(rect, flags);
            dest.writeFloat(rotation);
            dest.writeString(text);
            dest.writeFloat(textSize);
            dest.writeInt(textColor);
            dest.writeInt(type.ordinal());
            dest.writeParcelable(patternBitmap, flags);
            dest.writeString(patternPath);
            dest.writeFloat(patternScale);
            dest.writeInt(patternColorFilter);
            dest.writeFloat(patternAlpha);
            dest.writeFloat(startRotationAngle);
            dest.writeFloat(startTouchAngle);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<Watermark> CREATOR = new Creator<Watermark>() {
            @Override
            public Watermark createFromParcel(Parcel in) {
                return new Watermark(in);
            }

            @Override
            public Watermark[] newArray(int size) {
                return new Watermark[size];
            }
        };
    }
    public void setCurrentWatermark(Watermark watermark) {
        this.currentWatermark = watermark;
        invalidate();
    }

    public WatermarkView(Context context) {
        super(context);
        init();
    }

    public WatermarkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        closeButtonBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_close);
        addButtonBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_copy);
        scaleButtonBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_scale);
        rotateButtonBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_rotate);

        currentWatermark = new Watermark();
        watermarks.add(currentWatermark);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(currentWatermark.textSize);
        textPaint.setColor(currentWatermark.textColor);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        borderPaint.setColor(Color.RED);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (Watermark wm : watermarks) {
            updateWatermarkRect(wm);
        }
    }

    public void updateWatermarkRect(Watermark watermark) {
        if (watermark.rect.isEmpty()) {
            if (watermark.type == Watermark.Type.TEXT) {
                watermark.textPaint.setTextSize(watermark.textSize);
                float textWidth = watermark.textPaint.measureText(watermark.text);
                float textHeight = watermark.textPaint.descent() - watermark.textPaint.ascent();

                float centerX = getWidth() / 2f;
                float centerY = getHeight() / 2f;
                watermark.rect.set(
                        centerX - (textWidth / 2 + TEXT_PADDING),
                        centerY - (textHeight / 2 + TEXT_PADDING),
                        centerX + (textWidth / 2 + TEXT_PADDING),
                        centerY + (textHeight / 2 + TEXT_PADDING)
                );
            } else if (watermark.type == Watermark.Type.IMAGE && watermark.patternBitmap != null) {
                int bitmapWidth = watermark.patternBitmap.getWidth();
                int bitmapHeight = watermark.patternBitmap.getHeight();

                float centerX = getWidth() / 2f;
                float centerY = getHeight() / 2f;
                watermark.rect.set(
                        centerX - bitmapWidth / 2f,
                        centerY - bitmapHeight / 2f,
                        centerX + bitmapWidth / 2f,
                        centerY + bitmapHeight / 2f
                );
            }
        }
        updateButtonPositions(watermark);
    }

    private void updateButtonPositions(Watermark watermark) {
        float btnSize = 40;

        // 关闭按钮（左上角外延20px）
        watermark.closeButtonRect.set(
                watermark.rect.left - BUTTON_OFFSET,
                watermark.rect.top - BUTTON_OFFSET,
                watermark.rect.left - BUTTON_OFFSET + btnSize,
                watermark.rect.top - BUTTON_OFFSET + btnSize
        );

        // 添加按钮（右上角外延20px）
        watermark.addButtonRect.set(
                watermark.rect.right - BUTTON_OFFSET,
                watermark.rect.top - BUTTON_OFFSET,
                watermark.rect.right + BUTTON_OFFSET,
                watermark.rect.top + BUTTON_OFFSET
        );

        // 缩放按钮（右下角内缩20px）
        watermark.scaleButtonRect.set(
                watermark.rect.right - BUTTON_OFFSET,
                watermark.rect.bottom - BUTTON_OFFSET,
                watermark.rect.right + BUTTON_OFFSET,
                watermark.rect.bottom + BUTTON_OFFSET
        );

        // 旋转按钮（正下方40px，扩大点击区域）
        float rotateBtnSize = btnSize * 1.5f;
        watermark.rotateButtonRect.set(
                watermark.rect.centerX() - rotateBtnSize / 2,
                watermark.rect.bottom + 40,
                watermark.rect.centerX() + rotateBtnSize / 2,
                watermark.rect.bottom + 40 + rotateBtnSize
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (imageBitmap != null) {
            viewScale = Math.min((float) getWidth() / imageBitmap.getWidth(),
                    (float) getHeight() / imageBitmap.getHeight());
            viewLeft = (getWidth() - imageBitmap.getWidth() * viewScale) / 2;
            viewTop = (getHeight() - imageBitmap.getHeight() * viewScale) / 2;

            canvas.save();
            canvas.translate(viewLeft, viewTop);
            canvas.scale(viewScale, viewScale);
            canvas.drawBitmap(imageBitmap, 0, 0, null);
            canvas.restore();
        }

        for (Watermark wm : watermarks) {
            Log.d("水印区：WatermarkView", "Drawing Watermark: " + wm.toString());
            canvas.save();
            canvas.rotate(wm.rotation, wm.rect.centerX(), wm.rect.centerY());

            borderPaint.setColor(wm == currentWatermark ? Color.GREEN : Color.RED);
            canvas.drawRect(wm.rect, borderPaint);

            canvas.drawBitmap(closeButtonBitmap, null, wm.closeButtonRect, null);
            canvas.drawBitmap(addButtonBitmap, null, wm.addButtonRect, null);
            canvas.drawBitmap(scaleButtonBitmap, null, wm.scaleButtonRect, null);
            canvas.drawBitmap(rotateButtonBitmap, null, wm.rotateButtonRect, null);

            if (wm.type == Watermark.Type.IMAGE && wm.patternBitmap != null) {
                Paint patternPaint = new Paint();
                patternPaint.setAlpha((int) (wm.patternAlpha * 255));
                if (wm.patternColorFilter != Color.TRANSPARENT) {
                    patternPaint.setColorFilter(new PorterDuffColorFilter(
                            wm.patternColorFilter,
                            PorterDuff.Mode.SRC_ATOP
                    ));
                }
                Matrix matrix = new Matrix();
                RectF srcRect = new RectF(0, 0,
                        wm.patternBitmap.getWidth(),
                        wm.patternBitmap.getHeight());
                matrix.setRectToRect(srcRect, wm.rect, Matrix.ScaleToFit.CENTER);
                canvas.drawBitmap(wm.patternBitmap, matrix, patternPaint);
            } else {
                if (wm.text.isEmpty()) wm.text = "我们的水印很特别！";
                wm.textPaint.setColor(wm.textColor);
                canvas.drawText(wm.text,
                        wm.rect.left + TEXT_PADDING,
                        wm.rect.top + TEXT_PADDING + wm.textPaint.getTextSize(),
                        wm.textPaint
                );
            }
            canvas.restore();
        }
    }

    public void setImageBitmap(Bitmap bitmap) { // 已经定义了 setImageBitmap 方法
        this.imageBitmap = bitmap;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = x;
                touchStartY = y;
                activeIndex = -1;

                for (int i = 0; i < watermarks.size(); i++) {
                    Watermark wm = watermarks.get(i);

                    Matrix matrix = new Matrix();
                    matrix.setRotate(-wm.rotation, wm.rect.centerX(), wm.rect.centerY());
                    float[] point = {x, y};
                    matrix.mapPoints(point);
                    float localX = point[0];
                    float localY = point[1];

                    if (wm.closeButtonRect.contains(localX, localY)) {
                        watermarks.remove(i);
                        invalidate();
                        return true;
                    } else if (wm.addButtonRect.contains(localX, localY)) {
                        addWatermark(wm);
                        invalidate();
                        return true;
                    } else if (wm.scaleButtonRect.contains(localX, localY)) {
                        activeIndex = i;
                        currentWatermark = wm;
                        isScaling = true;
                        invalidate();
                        return true;
                    } else if (wm.rotateButtonRect.contains(localX, localY)) {
                        activeIndex = i;
                        currentWatermark = wm;
                        isRotating = true;

                        // 记录初始旋转角度和触摸角度
                        currentWatermark.startRotationAngle = currentWatermark.rotation;
                        float centerX = currentWatermark.rect.centerX();
                        float centerY = currentWatermark.rect.centerY();
                        float initialDeltaX = x - centerX;
                        float initialDeltaY = y - centerY;
                        currentWatermark.startTouchAngle = (float) Math.toDegrees(Math.atan2(initialDeltaY, initialDeltaX));

                        invalidate();
                        return true;
                    } else if (wm.rect.contains(localX, localY)) {
                        activeIndex = i;
                        currentWatermark = wm;
                        isMoving = true;
                        invalidate();
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (activeIndex == -1) break;
                Watermark activeWM = watermarks.get(activeIndex);
                boolean needUpdate = false;

                if (isMoving) {
                    float dx = x - touchStartX;
                    float dy = y - touchStartY;
                    activeWM.rect.offset(dx, dy);
                    updateButtonPositions(activeWM);
                    touchStartX = x;
                    touchStartY = y;
                    needUpdate = true;
                } else if (isScaling) {
                    float dx = x - touchStartX;
                    float dy = y - touchStartY;
                    float scale = 1 + (dx + dy) / 500f;

                    float minWidth = 150;
                    float minHeight = 60;
                    if (activeWM.rect.width() * scale < minWidth || activeWM.rect.height() * scale < minHeight) {
                        scale = Math.max(minWidth / activeWM.rect.width(), minHeight / activeWM.rect.height());
                    }

                    activeWM.rect.set(
                            activeWM.rect.centerX() - (activeWM.rect.width() * scale / 2),
                            activeWM.rect.centerY() - (activeWM.rect.height() * scale / 2),
                            activeWM.rect.centerX() + (activeWM.rect.width() * scale / 2),
                            activeWM.rect.centerY() + (activeWM.rect.height() * scale / 2)
                    );

                    activeWM.textSize *= scale;
                    activeWM.textPaint.setTextSize(activeWM.textSize);

                    updateButtonPositions(activeWM);
                    touchStartX = x;
                    touchStartY = y;
                    needUpdate = true;
                } else if (isRotating) {
                    float centerX = activeWM.rect.centerX();
                    float centerY = activeWM.rect.centerY();

                    float currentDeltaX = x - centerX;
                    float currentDeltaY = y - centerY;
                    float currentTouchAngle = (float) Math.toDegrees(Math.atan2(currentDeltaY, currentDeltaX));

                    float angleDelta = currentTouchAngle - activeWM.startTouchAngle;
                    activeWM.rotation = activeWM.startRotationAngle + angleDelta;
                    activeWM.rotation = (activeWM.rotation % 360 + 360) % 360;

                    updateButtonPositions(activeWM);
                    invalidate();
                }
                if (needUpdate) {
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (activeIndex != -1) {
                    updateButtonPositions(watermarks.get(activeIndex));
                }

                isMoving = isRotating = isScaling = false;
                activeIndex = -1;
                invalidate();
                break;
        }
        return true;
    }

    private void addWatermark(Watermark source) {
        Watermark newWM = new Watermark(source);

        // 计算原水印高度的一半，并取整
        float halfHeight = Math.round(source.rect.height() / 2);

        // 设置新水印的位置
        newWM.rect.set(
                source.rect.left + halfHeight,  // x 坐标加上高度的一半
                source.rect.top + halfHeight,   // y 坐标加上高度的一半
                source.rect.right + halfHeight,
                source.rect.bottom + halfHeight
        );

        newWM.rotation = source.rotation; // 保持旋转角度

        updateWatermarkRect(newWM); // 更新水印区域
        watermarks.add(newWM);     // 添加到水印列表
        currentWatermark = newWM;  // 设置为当前水印
        invalidate();              // 刷新视图
    }


    public void setTextTypeface(Typeface typeface) {
        if (currentWatermark != null && currentWatermark.type == Watermark.Type.TEXT) {
            currentWatermark.textPaint.setTypeface(typeface);  // 修改当前水印区的字体
            invalidate();  // 刷新视图
        }
    }



    public void setWatermarkText(String text) {
        if (currentWatermark != null) {
            currentWatermark.text = text;
            updateWatermarkRect(currentWatermark);
            invalidate();
        }
    }

    public void setTextSize(float size) {
        if (currentWatermark != null) {
            currentWatermark.textSize = size;
            updateWatermarkRect(currentWatermark);
            invalidate();
        }
    }

    public void setTextColor(int color) {
        if (currentWatermark != null) {
            if (currentWatermark.type == Watermark.Type.TEXT) {
                currentWatermark.textColor = color;
                currentWatermark.textPaint.setColor(color);
            } else {
                currentWatermark.patternColorFilter = color;
            }
            invalidate();
        }
    }

}
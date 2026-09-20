package com.dsq.rebackground.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DrawView extends View {

    private Bitmap mBackgroundBitmap; // 背景层（原始图片）
    private Bitmap mDrawingBitmap;    // 绘制层（透明图层）
    private Canvas mDrawingCanvas;   // 绘制层的画布
    private Paint mPaint;            // 通用画笔
    private Path mPath;
    private int mTextSize = 12;            // 文本大小
    private Typeface mTypeface;// 绘制路径
    private boolean isTextMode = false; // 当前是否为文本编辑模式

    private int mBrushColor = 0x00000000;; // 画笔颜色
    private int mBrushSize = 0;            // 画笔大小
    private int mEraseSize = 30;           // 橡皮擦大小
    private boolean mIsPainting = true;    // 模式（true: 画笔，false: 橡皮擦）

    private List<TextObject> mTextObjects = new ArrayList<>(); // 存储所有文本对象
    private TextObject mCurrentTextObject; // 当前正在编辑的文本对象
    private TextObject mSelectedTextObject;
    private float mTouchStartX, mTouchStartY;

    public DrawView(Context context, Bitmap backgroundBitmap) {
        super(context);
        init(backgroundBitmap);
    }

    private void init(Bitmap backgroundBitmap) {
        // 初始化背景层
        if (backgroundBitmap == null || backgroundBitmap.isRecycled()) {
            throw new IllegalArgumentException("Bitmap cannot be null or recycled");
        }
        mBackgroundBitmap = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true);

        // 初始化透明绘制层
        mDrawingBitmap = Bitmap.createBitmap(
                mBackgroundBitmap.getWidth(),
                mBackgroundBitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        mDrawingCanvas = new Canvas(mDrawingBitmap);

        // 初始化画笔
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(mBrushSize);
        mPaint.setColor(mBrushColor);

        mPath = new Path();
    }

    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. 绘制背景层
        canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);

        // 2. 绘制透明层
        canvas.drawBitmap(mDrawingBitmap, 0, 0, null);

        // 3. 实时绘制当前路径（预览）
        canvas.drawPath(mPath, mPaint);

        // 4. 绘制所有文本对象
        for (TextObject textObject : mTextObjects) {
            textObject.draw(canvas);
        }
    }

    private TextObject findTextObjectAt(float x, float y) {
        for (int i = mTextObjects.size() - 1; i >= 0; i--) {
            TextObject textObject = mTextObjects.get(i);
            if (textObject.contains(x, y)) {
                return textObject;
            }
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isTextMode) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mSelectedTextObject = findTextObjectAt(x, y);
                    if (mSelectedTextObject != null) {
                        mTouchStartX = x;
                        mTouchStartY = y;
                    } else {
                        showTextInputDialog();
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mSelectedTextObject != null) {
                        float dx = x - mTouchStartX;
                        float dy = y - mTouchStartY;
                        mSelectedTextObject.setPosition(
                                mSelectedTextObject.x + dx,
                                mSelectedTextObject.y + dy
                        );
                        mTouchStartX = x;
                        mTouchStartY = y;
                        invalidate();
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    mSelectedTextObject = null;
                    break;
            }
            return true;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mPath.reset();
                mPath.moveTo(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                mPath.lineTo(x, y);
                break;
            case MotionEvent.ACTION_UP:
                // 将路径绘制到透明层
                mDrawingCanvas.drawPath(mPath, mPaint);
                mPath.reset();
                break;
        }

        invalidate();
        return true;
    }

    // 设置画笔/橡皮擦模式
    public void setPaintingMode(boolean isPainting) {
        mIsPainting = isPainting;
        if (mIsPainting) {
            // 画笔模式：使用画笔颜色
            mPaint.setColor(mBrushColor);
            mPaint.setXfermode(null); // 清除混合模式
            Log.d("DrawView", "切换到画笔模式: 颜色=" + String.format("#%06X", 0xFFFFFF & mBrushColor) + ", 大小=" + mBrushSize);
        } else {
            // 橡皮擦模式：使用透明色（清除像素）
            mPaint.setColor(Color.TRANSPARENT);
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            Log.d("DrawView", "切换到橡皮擦模式: 大小=" + mEraseSize);
        }
    }

    // 获取最终合并后的图片（保存用）
    // 获取最终合并后的图片（保存用）
    public Bitmap getFinalBitmap() {
        // 创建一个与原图尺寸相同的 Bitmap
        Bitmap result = Bitmap.createBitmap(
                mBackgroundBitmap.getWidth(),
                mBackgroundBitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(result);

        // 绘制背景层
        canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);

        // 绘制透明层
        canvas.drawBitmap(mDrawingBitmap, 0, 0, null);

        // 绘制所有文本对象
        for (TextObject textObject : mTextObjects) {
            textObject.draw(canvas);
        }

        return result;
    }

    // 设置画笔颜色
    public void setBrushColor(int color) {
        mBrushColor = color;
        if (mIsPainting) {
            mPaint.setColor(color);
        }
    }

    // 设置画笔大小
    public void setBrushSize(int size) {
        mBrushSize = size;
        mPaint.setStrokeWidth(size);
    }

    // 设置橡皮擦大小
    public void setEraseSize(int size) {
        mEraseSize = size;
        if (!mIsPainting) {
            mPaint.setStrokeWidth(size);
        }
    }

    // 更新图像位图
    public void setImageBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            mBackgroundBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            mDrawingBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            mDrawingCanvas = new Canvas(mDrawingBitmap);
            invalidate(); // 重绘界面
        }
    }

    // 显示文本输入对话框
    private void showTextInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("编辑文本");

        // 输入框
        final EditText input = new EditText(getContext());
        builder.setView(input);

        // 确认按钮
        builder.setPositiveButton("确定", (dialog, which) -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                // 创建新的文本对象
                mCurrentTextObject = new TextObject(text, mBrushColor, mTextSize, mTypeface, 100, 100);
                mTextObjects.add(mCurrentTextObject);
                invalidate();  // 刷新视图
            }
        });

        // 取消按钮
        builder.setNegativeButton("取消", (dialog, which) -> {
            // 取消文本输入
        });

        builder.show();
    }

    // 设置文本模式
    public void setTextMode(boolean isTextMode) {
        this.isTextMode = isTextMode;
        invalidate(); // 重绘界面
    }

    // 设置文本颜色
    public void setTextColor(int color) {
        mBrushColor = color;
        if (mCurrentTextObject != null) {
            mCurrentTextObject.setTextColor(color);
        }
        invalidate(); // 重绘界面
    }

    // 设置文本大小
    public void setTextSize(int size) {
        mTextSize = size;
        if (mCurrentTextObject != null) {
            mCurrentTextObject.setTextSize(size);
        }
        invalidate(); // 重绘界面
    }

    // 设置文本字体
    public void setTypeface(Typeface typeface) {
        mTypeface = typeface;
        if (mCurrentTextObject != null) {
            mCurrentTextObject.setTypeface(typeface);
        }
        invalidate(); // 重绘界面
    }


    // 从文件加载字体
    public void loadTypefaceFromFile(String fontPath) {
        Log.e("DrawView", "字体路径:"+fontPath);
        if (fontPath == null || fontPath.isEmpty()) {
            Log.e("DrawView", "字体路径无效，使用默认字体");
            return; // 或者使用默认字体
        }

        File fontFile = new File(fontPath);
        if (fontFile.exists()) {
            mTypeface = Typeface.createFromFile(fontFile);
            invalidate(); // 重绘界面
        } else {
            Log.e("DrawView", "字体文件不存在: " + fontPath);
            // 如果文件不存在，可以选择加载一个默认字体
            mTypeface = Typeface.DEFAULT;
            invalidate(); // 重绘界面
        }

    }
    public void clearAll() {
        // 清除绘制层
        mDrawingBitmap.eraseColor(Color.TRANSPARENT);

        // 清除所有文本对象
        mTextObjects.clear();

        // 重置当前文本对象
        mCurrentTextObject = null;

        // 重绘视图
        invalidate();
    }

    // 文本对象类
    private static class TextObject {
        private String text;
        private int textColor;
        private int textSize;
        private Typeface typeface;
        private float x, y;
        private float rotation = 0; // 旋转角度

        public TextObject(String text, int textColor, int textSize, Typeface typeface, float x, float y) {
            this.text = text;
            this.textColor = textColor;
            this.textSize = textSize;
            this.typeface = typeface;
            this.x = x;
            this.y = y;
        }

        public void draw(Canvas canvas) {
            Paint textPaint = new Paint();
            textPaint.setColor(textColor);
            textPaint.setTextSize(textSize);
            textPaint.setTypeface(typeface);
            textPaint.setAntiAlias(true);

            // 应用旋转
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation, x, y);
            canvas.setMatrix(matrix);

            // 绘制文本
            canvas.drawText(text, x, y, textPaint);

            // 重置矩阵
            canvas.setMatrix(null);
        }

        public void setTextColor(int color) {
            this.textColor = color;
        }

        public void setTextSize(int size) {
            this.textSize = size;
        }

        public void setTypeface(Typeface typeface) {
            this.typeface = typeface;
        }

        public void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
        // 清除所有绘制和文本


        public void setRotation(float rotation) {
            this.rotation = rotation;
        }

        public boolean contains(float touchX, float touchY) {
            Paint textPaint = new Paint();
            textPaint.setTextSize(textSize);
            float textWidth = textPaint.measureText(text);
            float textHeight = textPaint.getTextSize();
            return touchX >= x && touchX <= x + textWidth &&
                    touchY >= y - textHeight && touchY <= y;
        }
    }
}
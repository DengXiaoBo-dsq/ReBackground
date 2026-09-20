package com.dsq.rebackground.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.RegionIterator;
import android.graphics.Typeface;

public class WatermarkUtils {

    /**
     * 添加文本水印
     *
     * @param source    原图
     * @param text      水印文本
     * @param textColor 文本颜色
     * @param textSize  文本大小
     * @param typeface  字体
     * @param rotation  旋转角度
     * @return 添加水印后的图片
     */
    public static Bitmap addTextWatermark(Bitmap source, String text, int textColor, float textSize, Typeface typeface, float rotation) {
        if (source == null || text == null || text.isEmpty()) return source;

        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);


        Paint paint = new Paint();
        // 创建包含透明度的颜色
        int colorWithAlpha = Color.argb(
                Color.alpha(textColor),
                Color.red(textColor),
                Color.green(textColor),
                Color.blue(textColor)
        );

        paint.setColor(colorWithAlpha);

        paint.setTextSize(textSize);
        paint.setTypeface(typeface);
        paint.setAntiAlias(true);

        // 计算文本居中位置
        float textWidth = paint.measureText(text);
        float textHeight = paint.descent() - paint.ascent();
        float x = (canvas.getWidth() - textWidth) / 2;
        float y = (canvas.getHeight() + textHeight) / 2;

        // 旋转画布并绘制文本
        canvas.save();
        canvas.rotate(rotation, x, y);
        canvas.drawText(text, x, y, paint);
        canvas.restore();

        return result;
    }

    public static Bitmap addTextWatermarkToPixels(Bitmap source, String text, int textColor, float textSize, Typeface typeface, float rotation) {
        if (source == null || text == null || text.isEmpty()) return source;

        // 创建可修改的Bitmap
        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);

        // 初始化Paint
        Paint paint = new Paint();
        paint.setColor(textColor);
        paint.setTextSize(textSize);
        paint.setTypeface(typeface);
        paint.setAntiAlias(true);

        // 获取文本的Path
        Path textPath = new Path();
        float textWidth = paint.measureText(text);
        float textHeight = paint.descent() - paint.ascent();
        float x = (result.getWidth() - textWidth) / 2;
        float y = (result.getHeight() + textHeight) / 2;
        paint.getTextPath(text, 0, text.length(), x, y, textPath);

        // 创建Region并设置Path
        Region region = new Region();
        region.setPath(textPath, new Region(0, 0, result.getWidth(), result.getHeight()));

        // 获取文本颜色的透明度
        int textAlpha = Color.alpha(textColor);
        float alpha = textAlpha / 255f; // 将透明度转换为 0-1 范围

        // 遍历Region中的像素
        RegionIterator iterator = new RegionIterator(region);
        Rect rect = new Rect();
        int[] pixels = new int[result.getWidth() * result.getHeight()];
        result.getPixels(pixels, 0, result.getWidth(), 0, 0, result.getWidth(), result.getHeight());

        while (iterator.next(rect)) {
            for (int i = rect.left; i < rect.right; i++) {
                for (int j = rect.top; j < rect.bottom; j++) {
                    if (region.contains(i, j)) {
                        // 获取原图像素的颜色
                        int originalColor = pixels[j * result.getWidth() + i];
                        int originalRed = Color.red(originalColor);
                        int originalGreen = Color.green(originalColor);
                        int originalBlue = Color.blue(originalColor);

                        // 获取文本颜色的RGB分量
                        int textRed = Color.red(textColor);
                        int textGreen = Color.green(textColor);
                        int textBlue = Color.blue(textColor);

                        // 颜色混合
                        int mixedRed = (int) (textRed * alpha + originalRed * (1 - alpha));
                        int mixedGreen = (int) (textGreen * alpha + originalGreen * (1 - alpha));
                        int mixedBlue = (int) (textBlue * alpha + originalBlue * (1 - alpha));

                        // 将混合后的颜色写回像素
                        int mixedColor = Color.argb(255, mixedRed, mixedGreen, mixedBlue);
                        pixels[j * result.getWidth() + i] = mixedColor;
                    }
                }
            }
        }

        // 将修改后的像素写回Bitmap
        result.setPixels(pixels, 0, result.getWidth(), 0, 0, result.getWidth(), result.getHeight());

        return result;
    }
    /**
     * 添加图案水印
     *
     * @param source      原图
     * @param watermark   水印图案
     * @param matrix      变换矩阵
     * @return 添加水印后的图片
     */
    public static Bitmap addImageWatermark(Bitmap source, Bitmap watermark, Matrix matrix) {
        if (source == null || watermark == null) return source;

        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        // 应用变换矩阵并绘制图案
        canvas.save();
        canvas.concat(matrix);
        canvas.drawBitmap(watermark, 0, 0, null);
        canvas.restore();

        return result;
    }

    /**
     * 提取前景并生成遮罩图
     *
     * @param source 原图
     * @return 遮罩图
     */
    public static Bitmap extractForegroundMask(Bitmap source) {
        if (source == null) return null;

        Bitmap mask = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mask);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        canvas.drawBitmap(source, 0, 0, paint);

        // 使用 PorterDuffXfermode 提取前景
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(source, 0, 0, paint);

        return mask;
    }
}
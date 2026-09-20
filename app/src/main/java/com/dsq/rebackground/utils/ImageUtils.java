package com.dsq.rebackground.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.Color;


public class ImageUtils {

    public static Bitmap resizeBitmap(Bitmap original, float width, float height) {
        float ratioBitmap = original.getWidth() / (float) original.getHeight();
        float ratioMax = width / height;
        float finalWidth = width;
        float finalHeight = height;

        if (ratioMax > ratioBitmap) {
            finalWidth = height * ratioBitmap;
        } else {
            finalHeight = width / ratioBitmap;
        }

        return Bitmap.createScaledBitmap(original, (int) finalWidth, (int) finalHeight, true);
    }


    public static Bitmap rotateBitmap(Bitmap bitmap, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    public static float[] preprocessImage(Bitmap bitmap) {
        int width = 1024;
        int height = 1024;

        // 调整大小为1024x1024
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);

        // 转换为CHW格式的归一化数据（均值[0.485, 0.456, 0.406]，方差[0.229, 0.224, 0.225]）
        float[] inputData = new float[3 * width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = scaledBitmap.getPixel(x, y);

                // 提取RGB通道并归一化
                float r = Color.red(pixel) / 255.0f;
                float g = Color.green(pixel) / 255.0f;
                float b = Color.blue(pixel) / 255.0f;

                inputData[y * width + x] = (r - 0.485f) / 0.229f;       // R
                inputData[width * height + y * width + x] = (g - 0.456f) / 0.224f; // G
                inputData[2 * width * height + y * width + x] = (b - 0.406f) / 0.225f; // B
            }
        }
        return inputData;
    }

    public static Bitmap applyMaskToImage(Bitmap originalBitmap, float[] maskData) {
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();

        // 调整掩码大小与原图一致
        Bitmap maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 将掩码数据转换为Alpha值（0-255）
                int alpha = (int) (maskData[y * width + x] * 255);
                alpha = Math.min(255, Math.max(0, alpha)); // 限制范围
                maskBitmap.setPixel(x, y, Color.argb(alpha, 0, 0, 0));
            }
        }

        // 应用Alpha通道到原图
        Bitmap resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        resultBitmap.setHasAlpha(true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = Color.alpha(maskBitmap.getPixel(x, y));
                int pixel = resultBitmap.getPixel(x, y);
                resultBitmap.setPixel(x, y, (pixel & 0x00FFFFFF) | (alpha << 24));
            }
        }

        return resultBitmap;
    }
}


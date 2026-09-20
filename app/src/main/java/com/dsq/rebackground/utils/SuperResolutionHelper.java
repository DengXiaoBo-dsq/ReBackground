package com.dsq.rebackground.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class SuperResolutionHelper {
    private static final String TAG = "SuperResolutionHelper";
    private Interpreter interpreter;
    private final int INPUT_SIZE = 128;
    private final int OUTPUT_SIZE = 512;
    private final int SCALE = 4;

    public SuperResolutionHelper(Context context) throws IOException {
        loadModel(context);
    }

    private void loadModel(Context context) throws IOException {
        MappedByteBuffer modelBuffer = loadModelFile(context);
        interpreter = new Interpreter(modelBuffer);
        Log.d(TAG, "✅ 模型加载成功");
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        var afd = context.getAssets().openFd("realesr_x4v3.tflite");
        var inputStream = new FileInputStream(afd.getFileDescriptor());
        var fileChannel = inputStream.getChannel();
        long startOffset = afd.getStartOffset();
        long declaredLength = afd.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public Bitmap upscaleWithAlpha(Bitmap input, int targetWidth, int targetHeight) {
        if (interpreter == null) {
            Log.e(TAG, "❌ Interpreter 为空");
            return null;
        }

        int srcW = input.getWidth();
        int srcH = input.getHeight();
        Log.d(TAG, String.format("📐 输入: %dx%d, 目标: %dx%d, 是否有Alpha: %b",
                srcW, srcH, targetWidth, targetHeight, input.hasAlpha()));

        if (srcW <= INPUT_SIZE && srcH <= INPUT_SIZE) {
            Log.d(TAG, "📌 图片<=128x128，整体超分");
            return upscaleSingleWithAlpha(input, targetWidth, targetHeight);
        }

        int tilesX = (srcW + INPUT_SIZE - 1) / INPUT_SIZE;
        int tilesY = (srcH + INPUT_SIZE - 1) / INPUT_SIZE;
        Log.d(TAG, String.format("🧩 分块: %dx%d = %d块", tilesX, tilesY, tilesX * tilesY));

        int outW = tilesX * OUTPUT_SIZE;
        int outH = tilesY * OUTPUT_SIZE;

        Bitmap result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        Log.d(TAG, "🎨 结果图已清空为透明背景");

        Paint paint = new Paint();
        int blockIndex = 0;

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                blockIndex++;
                int srcX = tx * INPUT_SIZE;
                int srcY = ty * INPUT_SIZE;
                int srcW1 = Math.min(INPUT_SIZE, srcW - srcX);
                int srcH1 = Math.min(INPUT_SIZE, srcH - srcY);

                Log.d(TAG, String.format("🔲 [块%d] 位置(%d,%d) 尺寸%dx%d",
                        blockIndex, srcX, srcY, srcW1, srcH1));

                Bitmap tile = Bitmap.createBitmap(input, srcX, srcY, srcW1, srcH1);
                Log.d(TAG, String.format("📦 [块%d] 原始块有Alpha: %b", blockIndex, tile.hasAlpha()));
                boolean tileHasTransparent = hasTransparentPixel(tile);
                Log.d(TAG, String.format("🔍 [块%d] 原始块含透明像素: %b", blockIndex, tileHasTransparent));

                if (srcW1 < INPUT_SIZE || srcH1 < INPUT_SIZE) {
                    Bitmap padded = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(padded);
                    c.drawColor(0, PorterDuff.Mode.CLEAR);
                    c.drawBitmap(tile, 0, 0, null);
                    for (int y = 0; y < INPUT_SIZE; y++) {
                        for (int x = 0; x < INPUT_SIZE; x++) {
                            if (x >= srcW1 || y >= srcH1) {
                                int srcX2 = Math.min(x, srcW1 - 1);
                                int srcY2 = Math.min(y, srcH1 - 1);
                                int pixel = tile.getPixel(srcX2, srcY2);
                                padded.setPixel(x, y, pixel);
                            }
                        }
                    }
                    tile.recycle();
                    tile = padded;
                    Log.d(TAG, String.format("📦 [块%d] 已填充到128x128", blockIndex));
                }

                Bitmap srTile = processTile(tile, blockIndex);
                tile.recycle();

                if (srTile == null) {
                    Log.e(TAG, String.format("❌ [块%d] 超分失败", blockIndex));
                    continue;
                }

                boolean srHasTransparent = hasTransparentPixel(srTile);
                Log.d(TAG, String.format("🔍 [块%d] 超分后含透明像素: %b", blockIndex, srHasTransparent));

                int dstX = tx * OUTPUT_SIZE;
                int dstY = ty * OUTPUT_SIZE;
                canvas.drawBitmap(srTile, dstX, dstY, paint);
                srTile.recycle();

                Log.d(TAG, String.format("✅ [块%d] 已合成到结果图 (%d,%d)", blockIndex, dstX, dstY));
            }
        }

        boolean resultHasTransparent = hasTransparentPixel(result);
        Log.d(TAG, String.format("🎯 最终结果图含透明像素: %b", resultHasTransparent));

        int cropW = srcW * SCALE;
        int cropH = srcH * SCALE;
        if (cropW < outW || cropH < outH) {
            Log.d(TAG, String.format("✂️ 裁剪: %dx%d -> %dx%d", outW, outH, cropW, cropH));
            Bitmap cropped = Bitmap.createBitmap(result, 0, 0, cropW, cropH);
            result.recycle();
            result = cropped;
        }

        if (result.getWidth() != targetWidth || result.getHeight() != targetHeight) {
            Log.d(TAG, String.format("📏 缩放: %dx%d -> %dx%d", result.getWidth(), result.getHeight(), targetWidth, targetHeight));
            Bitmap scaled = Bitmap.createScaledBitmap(result, targetWidth, targetHeight, true);
            result.recycle();
            result = scaled;
        }

        boolean finalHasTransparent = hasTransparentPixel(result);
        Log.d(TAG, String.format("🏁 最终结果含透明像素: %b", finalHasTransparent));

        return result;
    }

    private Bitmap processTile(Bitmap tile, int blockIndex) {
        // 提取 Alpha（使用像素操作确保正确）
        Bitmap alpha = extractAlphaPixels(tile);
        Log.d(TAG, String.format("🔲 [块%d] Alpha通道提取完成，尺寸%dx%d", blockIndex, alpha.getWidth(), alpha.getHeight()));
        boolean alphaHasTransparent = hasTransparentPixel(alpha);
        Log.d(TAG, String.format("🔍 [块%d] Alpha通道含透明像素: %b", blockIndex, alphaHasTransparent));

        // 提取 RGB（强制不透明）
        Bitmap rgb = extractRGB(tile);
        Log.d(TAG, String.format("🔲 [块%d] RGB提取完成，尺寸%dx%d", blockIndex, rgb.getWidth(), rgb.getHeight()));

        // 超分 RGB
        Bitmap srRgb = upscaleSingle(rgb);
        rgb.recycle();
        if (srRgb == null) {
            Log.e(TAG, String.format("❌ [块%d] RGB超分失败", blockIndex));
            return null;
        }
        Log.d(TAG, String.format("🔲 [块%d] RGB超分完成，尺寸%dx%d", blockIndex, srRgb.getWidth(), srRgb.getHeight()));

        // 缩放 Alpha 到 512x512
        Bitmap srAlpha = Bitmap.createScaledBitmap(alpha, OUTPUT_SIZE, OUTPUT_SIZE, false);
        alpha.recycle();
        Log.d(TAG, String.format("🔲 [块%d] Alpha缩放完成，尺寸%dx%d", blockIndex, srAlpha.getWidth(), srAlpha.getHeight()));
        boolean srAlphaHasTransparent = hasTransparentPixel(srAlpha);
        Log.d(TAG, String.format("🔍 [块%d] 缩放后Alpha含透明像素: %b", blockIndex, srAlphaHasTransparent));

        // 使用像素操作合成透明块
        Bitmap result = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888);
        int[] rgbPixels = new int[OUTPUT_SIZE * OUTPUT_SIZE];
        srRgb.getPixels(rgbPixels, 0, OUTPUT_SIZE, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
        int[] alphaPixels = new int[OUTPUT_SIZE * OUTPUT_SIZE];
        srAlpha.getPixels(alphaPixels, 0, OUTPUT_SIZE, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);

        // 统计 alpha 值范围
        int minAlpha = 255, maxAlpha = 0;
        for (int a : alphaPixels) {
            int av = a & 0xFF;
            if (av < minAlpha) minAlpha = av;
            if (av > maxAlpha) maxAlpha = av;
        }
        Log.d(TAG, String.format("📊 [块%d] srAlpha 像素值范围: min=%d, max=%d", blockIndex, minAlpha, maxAlpha));

        int[] resultPixels = new int[OUTPUT_SIZE * OUTPUT_SIZE];
        for (int i = 0; i < resultPixels.length; i++) {
            int rgbValue = rgbPixels[i] & 0x00FFFFFF; // 去掉原 Alpha（应为255）
            int alphaValue = alphaPixels[i] & 0xFF;
            // 如果 alphaValue 为 0，则完全透明；否则合成
            resultPixels[i] = (alphaValue << 24) | rgbValue;
        }
        result.setPixels(resultPixels, 0, OUTPUT_SIZE, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);

        srRgb.recycle();
        srAlpha.recycle();

        boolean resultHasTransparent = hasTransparentPixel(result);
        Log.d(TAG, String.format("🎨 [块%d] 合成后含透明像素: %b", blockIndex, resultHasTransparent));

        return result;
    }

    private Bitmap upscaleSingle(Bitmap input) {
        if (input.getWidth() != INPUT_SIZE || input.getHeight() != INPUT_SIZE) {
            input = Bitmap.createScaledBitmap(input, INPUT_SIZE, INPUT_SIZE, true);
        }

        float[][][][] inputArray = new float[1][INPUT_SIZE][INPUT_SIZE][3];
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        input.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int p = pixels[y * INPUT_SIZE + x];
                inputArray[0][y][x][0] = ((p >> 16) & 0xFF) / 255.0f;
                inputArray[0][y][x][1] = ((p >> 8) & 0xFF) / 255.0f;
                inputArray[0][y][x][2] = (p & 0xFF) / 255.0f;
            }
        }

        float[][][][] outputArray = new float[1][3][OUTPUT_SIZE][OUTPUT_SIZE];
        interpreter.run(inputArray, outputArray);

        Bitmap result = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888);
        int[] outPixels = new int[OUTPUT_SIZE * OUTPUT_SIZE];
        for (int y = 0; y < OUTPUT_SIZE; y++) {
            for (int x = 0; x < OUTPUT_SIZE; x++) {
                float r = outputArray[0][0][y][x];
                float g = outputArray[0][1][y][x];
                float b = outputArray[0][2][y][x];
                r = Math.max(0, Math.min(1, r));
                g = Math.max(0, Math.min(1, g));
                b = Math.max(0, Math.min(1, b));
                int rgb = ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
                outPixels[y * OUTPUT_SIZE + x] = 0xFF000000 | rgb;
            }
        }
        result.setPixels(outPixels, 0, OUTPUT_SIZE, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
        return result;
    }

    /**
     * 使用像素操作提取 Alpha 通道，确保正确性
     */
    private Bitmap extractAlphaPixels(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Bitmap alpha = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        byte[] alphaPixels = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            alphaPixels[i] = (byte) ((pixels[i] >> 24) & 0xFF);
        }
        ByteBuffer buffer = ByteBuffer.wrap(alphaPixels);
        alpha.copyPixelsFromBuffer(buffer);
        return alpha;
    }

    private Bitmap extractRGB(Bitmap bitmap) {
        Bitmap rgb = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rgb);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        // 强制 Alpha 为 255
        int[] pixels = new int[rgb.getWidth() * rgb.getHeight()];
        rgb.getPixels(pixels, 0, rgb.getWidth(), 0, 0, rgb.getWidth(), rgb.getHeight());
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF000000 | (pixels[i] & 0x00FFFFFF);
        }
        rgb.setPixels(pixels, 0, rgb.getWidth(), 0, 0, rgb.getWidth(), rgb.getHeight());
        return rgb;
    }

    private Bitmap upscaleSingleWithAlpha(Bitmap input, int targetWidth, int targetHeight) {
        Bitmap alpha = extractAlphaPixels(input);
        Bitmap rgb = extractRGB(input);
        Bitmap srRgb = upscaleSingle(rgb);
        Bitmap srAlpha = Bitmap.createScaledBitmap(alpha, srRgb.getWidth(), srRgb.getHeight(), false);
        Bitmap result = Bitmap.createBitmap(srRgb.getWidth(), srRgb.getHeight(), Bitmap.Config.ARGB_8888);
        // 像素合成
        int[] rgbPixels = new int[srRgb.getWidth() * srRgb.getHeight()];
        srRgb.getPixels(rgbPixels, 0, srRgb.getWidth(), 0, 0, srRgb.getWidth(), srRgb.getHeight());
        int[] alphaPixels = new int[srAlpha.getWidth() * srAlpha.getHeight()];
        srAlpha.getPixels(alphaPixels, 0, srAlpha.getWidth(), 0, 0, srAlpha.getWidth(), srAlpha.getHeight());
        int[] resultPixels = new int[rgbPixels.length];
        for (int i = 0; i < resultPixels.length; i++) {
            int rgbValue = rgbPixels[i] & 0x00FFFFFF;
            int alphaValue = alphaPixels[i] & 0xFF;
            resultPixels[i] = (alphaValue << 24) | rgbValue;
        }
        result.setPixels(resultPixels, 0, srRgb.getWidth(), 0, 0, srRgb.getWidth(), srRgb.getHeight());

        boolean hasTransparent = hasTransparentPixel(result);
        Log.d(TAG, String.format("🎨 整体超分结果含透明像素: %b", hasTransparent));

        if (result.getWidth() != targetWidth || result.getHeight() != targetHeight) {
            Bitmap scaled = Bitmap.createScaledBitmap(result, targetWidth, targetHeight, true);
            result.recycle();
            return scaled;
        }
        return result;
    }

    private boolean hasTransparentPixel(Bitmap bitmap) {
        if (bitmap == null || bitmap.getConfig() == null) return false;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (bitmap.getConfig() == Bitmap.Config.ALPHA_8) {
            // 对于 ALPHA_8，直接读取字节
            ByteBuffer buffer = ByteBuffer.allocate(w * h);
            bitmap.copyPixelsToBuffer(buffer);
            byte[] data = buffer.array();
            for (byte b : data) {
                if ((b & 0xFF) != 0xFF) {
                    return true;
                }
            }
            return false;
        }
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int p : pixels) {
            if ((p & 0xFF000000) != 0xFF000000) {
                return true;
            }
        }
        return false;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}
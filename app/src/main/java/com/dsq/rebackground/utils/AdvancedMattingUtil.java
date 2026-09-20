package com.dsq.rebackground.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;
import android.util.Size;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 高级背景去除工具类（支持TorchScript模型）
 * 功能：
 * 1. 从外部存储加载模型
 * 2. 自动处理大尺寸图像（最大1024x1024）
 * 3. 支持模型参数配置
 * 4. 结果合成与原始尺寸恢复
 *
 * 模型规范：
 * - 输入：src(原始图像), bgr(背景图像)
 * - 输出：pha(透明度遮罩), fgr(前景图像)
 * - 合成公式：result = pha * fgr + (1 - pha) * bgr
 */
public class AdvancedMattingUtil {
    private static final String TAG = "AdvancedMatting";
    private static final int MAX_INPUT_SIZE = 1024;

    private static Module model; // 单例模型实例

    /**
     * 初始化模型（单例模式）
     * @param modelPath 模型文件完整路径
     *
     */

    public static synchronized boolean initModel(String modelPath) {
        if (model != null) return false;

        try {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                throw new RuntimeException("Model file not found: " + modelPath);
            }

            // 加载模型
            model = Module.load(modelPath);
            Log.i(TAG, "Model initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Model initialization failed", e);
            throw new RuntimeException(e);
        }
    }


    /**
     * 处理图像主方法
     * @param srcPath 原始图像路径
     * @param bgrPath 背景图像路径（null时使用黑色背景）
     * @return 包含原始尺寸结果的MattingResult
     */
    public static MattingResult processImage(String srcPath, String bgrPath) {
        // 1. 加载并预处理图像
        Bitmap srcBitmap = loadAndResize(srcPath);
        Bitmap bgrBitmap = (bgrPath != null) ? loadAndResize(bgrPath) : createBlackBackground(srcBitmap.getWidth(), srcBitmap.getHeight());

        // 2. 记录原始尺寸
        Size originalSize = new Size(srcBitmap.getWidth(), srcBitmap.getHeight());

        // 3. 转换为模型输入张量
        Map<String, Tensor> inputs = new HashMap<>();
        inputs.put("src", bitmapToTensor(srcBitmap));  // [1,3,H,W] 0~1
        inputs.put("bgr", bitmapToTensor(bgrBitmap));  // [1,3,H,W] 0~1

        // 4. 运行推理
        long startTime = System.currentTimeMillis();
        Map<String, Tensor> outputs = runInference(inputs);
        long endTime = System.currentTimeMillis();
        Log.d(TAG, "Inference time: " + (endTime - startTime) + "ms");

        // 5. 结果合成与尺寸恢复
        MattingResult result = processOutputs(outputs, originalSize, srcBitmap, bgrBitmap);


        // 6. 保存处理结果
        saveResult(result.resultBitmap);

        return result;
    }

    /**
     * 图像加载与尺寸处理
     */
    private static Bitmap loadAndResize(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // 计算缩放比例
        int scale = 1;
        while (options.outWidth / scale > MAX_INPUT_SIZE || options.outHeight / scale > MAX_INPUT_SIZE) {
            scale *= 2;
        }

        // 加载缩放后的图像
        options.inJustDecodeBounds = false;
        options.inSampleSize = scale;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        // 确保尺寸能被4整除
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int newWidth = width - (width % 4);
        int newHeight = height - (height % 4);

        if (newWidth != width || newHeight != height) {
            bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        }

        return bitmap;
    }

    /**
     * 创建黑色背景
     */
    private static Bitmap createBlackBackground(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);
        return bitmap;
    }

    /**
     * Bitmap转Tensor（含归一化）
     */
    private static Tensor bitmapToTensor(Bitmap bitmap) {
        // 创建纯 RGB Bitmap（移除 Alpha 通道）
        Bitmap rgbBitmap = bitmap.copy(Bitmap.Config.RGB_565, false);

        float[] floatArray = new float[3 * rgbBitmap.getWidth() * rgbBitmap.getHeight()];
        for (int y = 0; y < rgbBitmap.getHeight(); y++) {
            for (int x = 0; x < rgbBitmap.getWidth(); x++) {
                int pixel = rgbBitmap.getPixel(x, y);
                floatArray[y * rgbBitmap.getWidth() + x] = Color.red(pixel) / 255.0f;    // R
                floatArray[rgbBitmap.getWidth() * rgbBitmap.getHeight() + y * rgbBitmap.getWidth() + x] = Color.green(pixel) / 255.0f;  // G
                floatArray[2 * rgbBitmap.getWidth() * rgbBitmap.getHeight() + y * rgbBitmap.getWidth() + x] = Color.blue(pixel) / 255.0f; // B
            }
        }
        return Tensor.fromBlob(floatArray, new long[]{1, 3, rgbBitmap.getHeight(), rgbBitmap.getWidth()});
    }

    /**
     * 执行模型推理
     */
    private static Map<String, Tensor> runInference(Map<String, Tensor> inputs) {
        checkModelInitialized();

        try {
            IValue output = model.forward(
                    IValue.from(inputs.get("src")),
                    IValue.from(inputs.get("bgr"))
            );

            // 解析输出元组
            Map<String, Tensor> outputs = new HashMap<>();
            outputs.put("pha", output.toTuple()[0].toTensor());  // [1,1,H,W]
            outputs.put("fgr", output.toTuple()[1].toTensor());  // [1,3,H,W]
            return outputs;
        } catch (Exception e) {
            throw new RuntimeException("Inference failed", e);
        }
    }

    /**
     * 结果后处理（使用正确的合成公式）
     */
    private static MattingResult processOutputs(Map<String, Tensor> outputs, Size originalSize, Bitmap srcBitmap, Bitmap bgrBitmap) {
        int outputHeight = (int) outputs.get("pha").shape()[2];
        int outputWidth = (int) outputs.get("pha").shape()[3];
        int srcWidth = srcBitmap.getWidth();
        int srcHeight = srcBitmap.getHeight();

        // 获取模型输出的pha和fgr
        float[] phaData = outputs.get("pha").getDataAsFloatArray();
        float[] fgrData = outputs.get("fgr").getDataAsFloatArray();

        // 将fgr数据转换为Bitmap
        Bitmap fgrBitmap = tensorToBitmap(fgrData, outputWidth, outputHeight);

        // 缩放Alpha遮罩到原始尺寸
        Bitmap alphaBitmap = resizeMask(phaData, outputWidth, outputHeight, srcWidth, srcHeight);

        // 缩放fgr到原始尺寸
        Bitmap resizedFgrBitmap = Bitmap.createScaledBitmap(fgrBitmap, srcWidth, srcHeight, true);

        // 合成最终结果：result = pha * fgr + (1 - pha) * bgr
        Bitmap resultBitmap = compositeImages(resizedFgrBitmap, bgrBitmap, alphaBitmap);

        return new MattingResult(resultBitmap, phaData, fgrData);
    }


    /**
     * 将模型输出的fgr张量转换为Bitmap
     */
    private static Bitmap tensorToBitmap(float[] fgrData, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int pixelCount = width * height;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int r = (int) (fgrData[index] * 255); // R channel
                int g = (int) (fgrData[pixelCount + index] * 255); // G channel
                int b = (int) (fgrData[2 * pixelCount + index] * 255); // B channel
                bitmap.setPixel(x, y, Color.argb(255, r, g, b));
            }
        }
        return bitmap;
    }
    /**
     * 缩放遮罩到目标尺寸
     */
    private static Bitmap resizeMask(float[] maskData, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        Bitmap maskBitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < srcHeight; y++) {
            for (int x = 0; x < srcWidth; x++) {
                int alpha = (int) (maskData[y * srcWidth + x] * 255);
                maskBitmap.setPixel(x, y, Color.argb(alpha, 0, 0, 0));
            }
        }
        return Bitmap.createScaledBitmap(maskBitmap, dstWidth, dstHeight, true);
    }



    /**
     * 图像合成（pha * fgr + (1 - pha) * bgr）
     */
    private static Bitmap compositeImages(Bitmap fgr, Bitmap bgr, Bitmap alpha) {
        Bitmap result = Bitmap.createBitmap(fgr.getWidth(), fgr.getHeight(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < fgr.getHeight(); y++) {
            for (int x = 0; x < fgr.getWidth(); x++) {
                int fgrPixel = fgr.getPixel(x, y);
                int bgPixel = bgr.getPixel(x, y);
                float alphaValue = Color.alpha(alpha.getPixel(x, y)) / 255.0f;

                int r = (int) (Color.red(fgrPixel) * alphaValue + Color.red(bgPixel) * (1 - alphaValue));
                int g = (int) (Color.green(fgrPixel) * alphaValue + Color.green(bgPixel) * (1 - alphaValue));
                int b = (int) (Color.blue(fgrPixel) * alphaValue + Color.blue(bgPixel) * (1 - alphaValue));

                result.setPixel(x, y, Color.argb(255, r, g, b));
            }
        }
        return result;
    }
//    private static Bitmap compositeImages(Bitmap src, Bitmap bgr, Bitmap alpha) {
//        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
//        for (int y = 0; y < src.getHeight(); y++) {
//            for (int x = 0; x < src.getWidth(); x++) {
//                int srcPixel = src.getPixel(x, y);
//                int bgPixel = bgr.getPixel(x, y);
//                float alphaValue = Color.alpha(alpha.getPixel(x, y)) / 255.0f;
//
//                int r = (int) (Color.red(srcPixel) * alphaValue + Color.red(bgPixel) * (1 - alphaValue));
//                int g = (int) (Color.green(srcPixel) * alphaValue + Color.green(bgPixel) * (1 - alphaValue));
//                int b = (int) (Color.blue(srcPixel) * alphaValue + Color.blue(bgPixel) * (1 - alphaValue));
//
//                result.setPixel(x, y, Color.argb(255, r, g, b));
//            }
//        }
//        return result;
//    }

    private static void checkModelInitialized() {
        if (model == null) {
            throw new IllegalStateException("Model not initialized. Call initModel() first.");
        }
    }

    /**
     * 结果保存
     */
    private static void saveResult(Bitmap resultBitmap) {
        try {
            File outputDir = new File(Environment.getExternalStorageDirectory(), "ProcessedResults");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // 生成文件名（使用时间戳）
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File outputFile = new File(outputDir, "result_" + timestamp + ".png");

            // 保存图片
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                Log.i(TAG, "Result saved: " + outputFile.getAbsolutePath());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving result", e);
        }
    }

    /**
     * 结果对象
     */
    public static class MattingResult {
        public final Bitmap resultBitmap;
        public final float[] pha;
        public final float[] fgr;

        public MattingResult(Bitmap resultBitmap, float[] pha, float[] fgr) {
            this.resultBitmap = resultBitmap;
            this.pha = pha;
            this.fgr = fgr;
        }
    }

    // 获取默认模型路径（示例）
    public static String getDefaultModelPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                + "/Models/torchscript_resnet101_fp32.pth";
    }
}
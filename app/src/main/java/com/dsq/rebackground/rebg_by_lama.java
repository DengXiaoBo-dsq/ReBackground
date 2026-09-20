package com.dsq.rebackground;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dsq.rebackground.utils.BitmapCache;
import com.dsq.rebackground.utils.MaskDrawView;
import com.dsq.rebackground.utils.ToastUtil;
import com.dsq.rebackground.utils.Utils_re;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class rebg_by_lama extends AppCompatActivity {
    private int BRUSH_SIZE = 20;
    private Bitmap originalBitmap, resultBitmap,cachedBitmap;
    private MaskDrawView maskView;
    private TextView brushSizeText;
    private SeekBar Pen_size_seekBar;
    private Button saveBtn, workBtn, clearBtn;
    private String imagePath;
    private ProgressDialog progressDialog;
    private List<int[]> watermarkRegions=null;
    private boolean isload=true;
    private static Module model = null; // 全局模型

    // 备份原图
    private Bitmap backupBitmap;
    private String savePath="*.*_123_no_pic_*.*--dsq";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.re_lama);

        // 初始化控件
        saveBtn = findViewById(R.id.saveButton);
        workBtn = findViewById(R.id.workButton);
        clearBtn = findViewById(R.id.clearButton);
        maskView = findViewById(R.id.maskView);
        Pen_size_seekBar = findViewById(R.id.PenSizeBar);
        brushSizeText = findViewById(R.id.brushSizeText);

        // 初始化画笔参数
        maskView.setPaintingMode(true);

        // 获取传递的 imageKey
        String imageKey = getIntent().getStringExtra("imageKey");
        if (imageKey != null) {
            // 从缓存中获取图片和属性信息（新增日志）
            Log.d("ModelLoad", "接收的imageKey：" + imageKey);
            cachedBitmap = BitmapCache.getInstance().getBitmap(imageKey);
            BitmapCache.ImageInfo imageInfo = BitmapCache.getInstance().getImageInfo(imageKey);

            if (cachedBitmap != null && imageInfo != null) {
                Log.d("ModelLoad", "缓存读取成功，图片宽度：" + cachedBitmap.getWidth() + "，路径：" + imageInfo.getPath());
                maskView.setImageBitmap(cachedBitmap);
                imagePath=imageInfo.getPath();
                originalBitmap = BitmapFactory.decodeFile(imagePath);
                backupBitmap = cachedBitmap.copy(Bitmap.Config.ARGB_8888, true); // 创建备份图
                maskView.setOriginalBitmap(backupBitmap);  // 设置备份图显示
            } else {
                Log.e("ModelLoad", "缓存读取失败：cachedBitmap=" + (cachedBitmap == null ? "null" : "非null") + "，imageInfo=" + (imageInfo == null ? "null" : "非null"));
                ToastUtil.showToast(this, "缓存读取失败！", Toast.LENGTH_SHORT);
                finish();
            }
        } else {
            Log.e("ModelLoad", "未接收到imageKey，无法获取图片");
            ToastUtil.showToast(this, "没有找到图片！", Toast.LENGTH_SHORT);
            finish();
        }

        // 检查是否已加载模型，避免重复加载（核心日志添加部分）
        if (model == null) {
            try {
                // 1. 调用Utils_re获取模型路径，打印路径（关键：确认路径是否正确）
                String modelPath = Utils_re.assetFilePath(this, "DSQ_REBG.pt");
                Log.d("ModelLoad", "通过Utils_re获取的模型路径：" + modelPath);

                // 2. 检查路径对应的文件是否存在
                File modelFile = new File(modelPath);
                if (!modelFile.exists()) {
                    Log.e("ModelLoad", "致命错误：模型文件不存在！路径：" + modelPath);
                    ToastUtil.showToast(this, "模型文件不存在：" + modelPath, Toast.LENGTH_LONG);
                    finish();
                    return;
                }

                // 3. 检查模型文件大小是否正常（避免空文件或损坏文件）
                long fileSize = modelFile.length();
                String fileSizeStr = fileSize < 1024 * 1024 ? (fileSize / 1024) + "KB" : (fileSize / 1024 / 1024) + "MB";
                Log.d("ModelLoad", "模型文件存在，大小：" + fileSize + "字节（" + fileSizeStr + "）");
                if (fileSize < 1024 * 100) { // 小于100KB判定为异常（可根据实际模型大小调整）
                    Log.e("ModelLoad", "致命错误：模型文件损坏（大小异常）！正常模型应几MB到几十MB，当前仅" + fileSizeStr);
                    ToastUtil.showToast(this, "模型文件损坏（大小仅" + fileSizeStr + "）", Toast.LENGTH_LONG);
                    finish();
                    return;
                }

                // 4. 加载模型（打印加载开始日志）
                Log.d("ModelLoad", "开始加载模型...");
                model = Module.load(modelPath);

                // 5. 加载成功日志
                Log.d("ModelLoad", "模型加载成功！Module实例：" + model.toString());
                ToastUtil.showToast(this, "模型加载成功", Toast.LENGTH_SHORT);
                isload = true;

                // 6. 正确设置返回结果（用RESULT_OK，确保MainActivity能接收）
                Intent resultIntent = new Intent();
                resultIntent.putExtra("isload", "isload");
                setResult(RESULT_OK, resultIntent);

            } catch (IOException e) {
                // 捕获“文件复制/读取错误”（如assets文件没找到、复制失败）
                Log.e("ModelLoad", "模型文件复制/读取失败（IOException）：", e); // 打印完整异常栈
                ToastUtil.showToast(this, "模型文件读取失败：" + e.getMessage(), Toast.LENGTH_LONG);
                finish();
            } catch (Exception e) {
                // 捕获“模型加载错误”（如PyTorch依赖不兼容、模型格式损坏）
                Log.e("ModelLoad", "模型加载失败（非文件问题，Exception）：", e); // 打印完整异常栈
                ToastUtil.showToast(this, "模型加载失败：" + e.getMessage(), Toast.LENGTH_LONG);
                finish();
            }
        } else {
            // 模型已缓存，无需重复加载
            Log.d("ModelLoad", "模型已缓存（Module非null），无需重复加载");
            Intent resultIntent = new Intent();
            resultIntent.putExtra("isload", "isload");
            setResult(RESULT_OK, resultIntent);
        }

        // 画笔大小调节（原逻辑不变）
        Pen_size_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                BRUSH_SIZE = progress;
                brushSizeText.setText(String.valueOf(BRUSH_SIZE));
                maskView.setBrushSize(BRUSH_SIZE);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 保存按钮事件（原逻辑不变）
        saveBtn.setOnClickListener(v -> {
            if (backupBitmap != null) saveImage(backupBitmap);
            else ToastUtil.showToast(this, "无处理结果", Toast.LENGTH_SHORT);
        });
        // 重来按钮（原逻辑不变）
        clearBtn.setOnClickListener(v -> clear_all());
        // 处理按钮（原逻辑不变）
        workBtn.setOnClickListener(v -> processImage());
    }

    // 清理功能（原逻辑不变）
    private void clear_all() {
        Log.d("ModelLoad", "调用清理功能：清空掩码和水印区域");
        maskView.clearMask();
        maskView.clearWatermarkRegions();
        watermarkRegions = new ArrayList<>();
        backupBitmap = cachedBitmap.copy(Bitmap.Config.ARGB_8888, true);
        maskView.setOriginalBitmap(backupBitmap);
        Log.d("ModelLoad", "清理功能完成");
    }

    // 图像处理（原逻辑不变）
    private void processImage() {
        setButtonsEnabled(false);
        progressDialog = new ProgressDialog(rebg_by_lama.this, R.style.CustomProgressDialog);
        progressDialog.setMessage("正在处理...");
        progressDialog.show();
        Bitmap maskBitmap = maskView.getMaskBitmap();
        if (backupBitmap == null || maskBitmap == null) {
            ToastUtil.showToast(this, "请先绘制掩码区域", Toast.LENGTH_SHORT);
            return;
        }

        // 获取水印区域的坐标列表
        watermarkRegions = maskView.getWatermarkRegions();
        if (watermarkRegions.isEmpty()) {
            ToastUtil.showToast(this, "未找到水印区域", Toast.LENGTH_SHORT);
            progressDialog.dismiss();
            setButtonsEnabled(true);
            return;
        }

        new Thread(() -> {
            try {
                Log.d("ModelLoad", "开始处理水印区域，共" + watermarkRegions.size() + "个区域");
                // 遍历所有水印区域，进行处理
                for (int[] watermarkBounds : watermarkRegions) {
                    int minX = watermarkBounds[0];
                    int minY = watermarkBounds[1];
                    int maxX = watermarkBounds[2];
                    int maxY = watermarkBounds[3];
                    Log.d("ModelLoad", "处理水印区域坐标：minX=" + minX + ", minY=" + minY + ", maxX=" + maxX + ", maxY=" + maxY);

                    // 对水印区域进行放大20像素，确保不越界
                    int padding = 40;
                    int imageWidth = backupBitmap.getWidth();
                    int imageHeight = backupBitmap.getHeight();

                    minX = Math.max(minX - padding, 0);
                    minY = Math.max(minY - padding, 0);
                    maxX = Math.min(maxX + padding, imageWidth);
                    maxY = Math.min(maxY + padding, imageHeight);
                    Log.d("ModelLoad", "调整后水印区域坐标：minX=" + minX + ", minY=" + minY + ", maxX=" + maxX + ", maxY=" + maxY);

                    // 裁剪水印区域和掩码区域
                    Rect watermarkRect = new Rect(minX, minY, maxX, maxY);
                    Bitmap watermarkRegion = Bitmap.createBitmap(backupBitmap, watermarkRect.left, watermarkRect.top, watermarkRect.width(), watermarkRect.height());
                    Bitmap maskRegion = Bitmap.createBitmap(maskBitmap, watermarkRect.left, watermarkRect.top, watermarkRect.width(), watermarkRect.height());
                    Log.d("ModelLoad", "裁剪水印区域大小：" + watermarkRegion.getWidth() + "x" + watermarkRegion.getHeight());

                    // 调用模型处理
                    processWithModel(watermarkRegion, maskRegion, watermarkRect);
                }

                // 所有水印处理完毕后，更新 UI
                runOnUiThread(() -> {
                    watermarkRegions.clear();
                    maskView.clearMask();
                    maskView.clearWatermarkRegions();
                    maskView.setImageBitmap(backupBitmap);
                    progressDialog.dismiss();
                    setButtonsEnabled(true);
                    ToastUtil.showToast(this, "处理完成", Toast.LENGTH_SHORT);
                    Log.d("ModelLoad", "所有水印区域处理完成");
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    maskView.clearMask();
                    watermarkRegions.clear();
                    maskView.clearWatermarkRegions();
                    progressDialog.dismiss();
                    setButtonsEnabled(true);
                    ToastUtil.showToast(this, "处理失败", Toast.LENGTH_SHORT);
                    Log.e("ModelLoad", "图像处理异常：", e);
                });
            }
        }).start();
    }

    // 模型处理（原逻辑不变）
    private void processWithModel(Bitmap watermarkRegion, Bitmap maskRegion, Rect watermarkRect) {
        try {
            Log.d("ModelLoad", "进入模型处理方法，水印区域大小：" + watermarkRegion.getWidth() + "x" + watermarkRegion.getHeight());
            // 缩放图像和掩码到模型要求的大小
            int newWidth = (watermarkRegion.getWidth() / 16) * 16;
            int newHeight = (watermarkRegion.getHeight() / 16) * 16;
            Log.d("ModelLoad", "模型要求缩放后大小：" + newWidth + "x" + newHeight);

            Bitmap resizedWatermark = Bitmap.createScaledBitmap(watermarkRegion, newWidth, newHeight, true);
            Bitmap resizedMask = Bitmap.createScaledBitmap(maskRegion, newWidth, newHeight, false);

            // 将裁剪后的水印和掩码区域转换为模型输入的 Tensor
            float[] imageTensor = TensorImageUtils.bitmapToFloat32Tensor(
                    resizedWatermark,
                    new float[]{0.0f, 0.0f, 0.0f},
                    new float[]{1.0f, 1.0f, 1.0f}
            ).getDataAsFloatArray();
            Log.d("ModelLoad", "图像Tensor生成完成，长度：" + imageTensor.length);

            float[] maskTensor = new float[newWidth * newHeight];
            for (int y = 0; y < newHeight; y++) {
                for (int x = 0; x < newWidth; x++) {
                    int pixel = resizedMask.getPixel(x, y);
                    maskTensor[y * newWidth + x] = ((pixel >> 16) & 0xFF) / 255.0f;
                }
            }
            Log.d("ModelLoad", "掩码Tensor生成完成，长度：" + maskTensor.length);

            // 输入 Tensor 到模型
            Tensor inputTensor = Tensor.fromBlob(imageTensor, new long[]{1, 3, newHeight, newWidth});
            Tensor maskInput = Tensor.fromBlob(maskTensor, new long[]{1, 1, newHeight, newWidth});
            Log.d("ModelLoad", "输入Tensor准备完成，调用模型forward...");
            Tensor outputTensor = model.forward(IValue.from(inputTensor), IValue.from(maskInput)).toTensor();
            Log.d("ModelLoad", "模型forward完成，输出Tensor形状：" + outputTensor.shape());

            // 将模型输出转换为 Bitmap
            Bitmap resultBitmap = tensorToBitmap(outputTensor, newWidth, newHeight);
            Log.d("ModelLoad", "模型输出转换为Bitmap完成，大小：" + resultBitmap.getWidth() + "x" + resultBitmap.getHeight());

            resultBitmap = Bitmap.createScaledBitmap(resultBitmap, watermarkRegion.getWidth(), watermarkRegion.getHeight(), true);
            Log.d("ModelLoad", "Bitmap缩放回原区域大小完成");

            // 使用 Canvas 将处理后的水印区域绘制回备份
            Bitmap finalResult = backupBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(finalResult);
            canvas.drawBitmap(resultBitmap, watermarkRect.left, watermarkRect.top, null);
            backupBitmap = finalResult;
            Log.d("ModelLoad", "处理后的区域绘制回备份图完成");
        } catch (Exception e) {
            Log.e("ModelLoad", "模型处理过程异常：", e);
            throw e; // 抛出异常，让上层处理
        }
    }

    // Tensor转Bitmap（原逻辑不变）
    private Bitmap tensorToBitmap(Tensor tensor, int width, int height) {
        float[] data = tensor.getDataAsFloatArray();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (int) (data[y * width + x] * 255);
                int g = (int) (data[width * height + y * width + x] * 255);
                int b = (int) (data[2 * width * height + y * width + x] * 255);
                bitmap.setPixel(x, y, Color.rgb(r, g, b));
            }
        }
        return bitmap;
    }

    // 按钮启用控制（原逻辑不变）
    private void setButtonsEnabled(boolean enabled) {
        workBtn.setEnabled(enabled);
        saveBtn.setEnabled(enabled);
        clearBtn.setEnabled(enabled);
    }

    // 图片保存（原逻辑不变）
    private void saveImage(Bitmap bitmapToSave) {
        if (bitmapToSave == null) {
            ToastUtil.showToast(this, "没有可保存的图片", Toast.LENGTH_SHORT);
            return;
        }

        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // 提取文件扩展名
        String fileExtension = imagePath.substring(imagePath.lastIndexOf(".")).toLowerCase();
        String fileName = "rebg_lama_" + System.currentTimeMillis() + fileExtension;
        File file = new File(directory, fileName);
        savePath = file.getAbsolutePath();
        Log.d("ModelLoad", "准备保存图片到：" + savePath);

        try {
            OutputStream outputStream = new FileOutputStream(file);
            // 根据文件扩展名选择保存格式
            if (".png".equals(fileExtension)) {
                bitmapToSave.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            } else if (".jpg".equals(fileExtension) || ".jpeg".equals(fileExtension)) {
                bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            } else {
                bitmapToSave.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }
            outputStream.close();
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));

            ToastUtil.showToast(this, "图片已保存在: " + file.getAbsolutePath(), Toast.LENGTH_SHORT);
            Log.d("ModelLoad", "图片保存成功");
        } catch (IOException e) {
            e.printStackTrace();
            ToastUtil.showToast(this, "保存失败", Toast.LENGTH_SHORT);
            Log.e("ModelLoad", "图片保存异常：", e);
        }
    }

    // 资源释放（原逻辑不变）
    @Override
    protected void onDestroy() {
        Log.d("ModelLoad", "进入onDestroy，释放资源");
        if (maskView != null) {
            maskView.clearMask();
        }
        if (backupBitmap != null && !backupBitmap.isRecycled()) {
            backupBitmap.recycle();
            backupBitmap = null;
        }
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
            originalBitmap = null;
        }
        Log.d("ModelLoad", "onDestroy资源释放完成");
        super.onDestroy();
    }
}
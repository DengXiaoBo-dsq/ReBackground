package com.dsq.rebackground;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dsq.rebackground.utils.BitmapCache;
import com.dsq.rebackground.utils.FileUtils;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BackgroundPickerActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;


    private ImageView backgroundImage, foregroundImage,bgview;
    private Button btnSelectImage, btnSelectColor, btnSave;
    private SeekBar seekBarScale, seekBarTranslateX, seekBarTranslateY, seekBarRotate;

    private Matrix matrix = new Matrix();
    private float initialScale = 1.0f; // 初始缩放比例
    private float initialTranslateX = 0f; // 初始水平偏移
    private float initialTranslateY = 0f; // 初始垂直偏移
    private float initialRotation = 0f; // 初始旋转角度
    private String imagePath;
    private int img_w=0,img_h=0,img_w2=0,img_h2=0;
    private  int opacity=255, textColor;




    // 定义监听器
    private SeekBar.OnSeekBarChangeListener translateXListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                int imageWidth = foregroundImage.getDrawable().getIntrinsicWidth();
                int viewWidth = foregroundImage.getWidth();
                float scaledWidth = imageWidth * initialScale;
                float minTx = -scaledWidth + 10;
                float maxTx = viewWidth - 10;
                float txRange = maxTx - minTx;
                if (txRange <= 0) return;
                float translateX = minTx + (progress / 1000f) * txRange;
                applyTransformation(initialScale, translateX, initialTranslateY, initialRotation);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };

    private SeekBar.OnSeekBarChangeListener translateYListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                int imageHeight = foregroundImage.getDrawable().getIntrinsicHeight();
                int viewHeight = foregroundImage.getHeight();
                float scaledHeight = imageHeight * initialScale;
                float minTy = -scaledHeight + 10;
                float maxTy = viewHeight - 10;
                float tyRange = maxTy - minTy;
                if (tyRange <= 0) return;
                float translateY = minTy + (progress / 1000f) * tyRange;
                applyTransformation(initialScale, initialTranslateX, translateY, initialRotation);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_picker);

        // 初始化控件
        backgroundImage = findViewById(R.id.backgroundImage);
        foregroundImage = findViewById(R.id.foregroundImage);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnSelectColor = findViewById(R.id.btnSelectColor);
        btnSave = findViewById(R.id.btnSave);
        seekBarScale = findViewById(R.id.seekBarScale);
        seekBarTranslateX = findViewById(R.id.seekBarTranslateX);
        seekBarTranslateY = findViewById(R.id.seekBarTranslateY);
        seekBarRotate = findViewById(R.id.seekBarRotate);
        bgview=findViewById(R.id.background);
        Button btnReset = findViewById(R.id.btnReset);


        // 设置默认背景颜色
//        backgroundImage.setBackgroundColor(Color.GRAY);


        // 获取传递的 imageKey
        String imageKey = getIntent().getStringExtra("imageKey");
        if (imageKey != null) {
            // 从缓存中获取图片和属性信息
            Bitmap foregroundBitmap = BitmapCache.getInstance().getBitmap(imageKey);
            BitmapCache.ImageInfo imageInfo = BitmapCache.getInstance().getImageInfo(imageKey);
            imagePath=imageInfo.getPath();

            if (foregroundBitmap != null && imageInfo != null) {
                foregroundImage.setImageBitmap(foregroundBitmap);
                initializeMatrix(); // 初始化 Matrix，使图片居中

            } else {
                ToastUtil.showToast(this, "缓存读取失败！", Toast.LENGTH_SHORT);
                finish();
            }
        } else {
            ToastUtil.showToast(this, "没有找到图片！", Toast.LENGTH_SHORT);
            finish();
        }
        btnReset.setOnClickListener(v -> resetAll());
        seekBarScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float scale = progress / 1000f; // 将进度值转换为缩放比例（范围：0.2x - 4x）
                // 将进度值转换为缩放比例
                if (scale <= 0.05) {
                    scale = 0.05f;
                }
                applyTransformation(scale, initialTranslateX, initialTranslateY, initialRotation);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        // 水平移动控制
        seekBarTranslateX.setOnSeekBarChangeListener(translateXListener);

        // 垂直移动控制
        seekBarTranslateY.setOnSeekBarChangeListener(translateYListener);

        seekBarRotate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                initialRotation = progress; // 更新旋转角度
                applyTransformation(initialScale, initialTranslateX, initialTranslateY, initialRotation);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });


        // 选择图片按钮点击事件
        btnSelectImage.setOnClickListener(v -> openImagePicker());

        // 选择颜色按钮点击事件
        btnSelectColor.setOnClickListener(v -> showColorPickerDialog());

        // 保存效果按钮点击事件
        btnSave.setOnClickListener(v -> saveResult());
    }

    private void initializeMatrix() {
        if (foregroundImage.getDrawable() != null) {
            // 监听视图布局完成事件
            foregroundImage.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // 移除监听器，避免重复调用
                    foregroundImage.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    // 获取图片的宽高
                    int imageWidth = foregroundImage.getDrawable().getIntrinsicWidth();
                    int imageHeight = foregroundImage.getDrawable().getIntrinsicHeight();
                    img_w = -imageWidth;
                    img_h = -imageHeight;

                    // 获取 ImageView 的宽高
                    int viewWidth = foregroundImage.getWidth();
                    int viewHeight = foregroundImage.getHeight();
                    img_h2 = viewHeight + imageHeight;
                    img_w2 = viewWidth + imageWidth;

                    // 计算初始缩放比例
                    initialScale = Math.min((float) viewWidth / imageWidth, (float) viewHeight / imageHeight);

                    // 计算初始偏移量，使图片居中
                    initialTranslateX = (viewWidth - imageWidth * initialScale) / 2;
                    initialTranslateY = (viewHeight - imageHeight * initialScale) / 2;

                    // 设置 Matrix
                    matrix.setScale(initialScale, initialScale);
                    matrix.postTranslate(initialTranslateX, initialTranslateY);
                    foregroundImage.setImageMatrix(matrix);

                    // 强制刷新视图
                    foregroundImage.invalidate();

                    // 初始化进度条
                    updateSeekBarProgress(initialTranslateX, initialTranslateY, imageWidth * initialScale, imageHeight * initialScale, viewWidth, viewHeight);
                }
            });
        }
    }
    // 重置方法实现
    private void resetAll() {
        // 重置背景
        backgroundImage.setImageDrawable(null);
        backgroundImage.setBackgroundColor(Color.TRANSPARENT);

        // 重置前景变换
        initializeMatrix(); // 复用初始化矩阵方法

        // 重置进度条
        seekBarScale.setProgress(1000);
        seekBarTranslateX.setProgress(500);
        seekBarTranslateY.setProgress(500);
        seekBarRotate.setProgress(0);

        // 清除颜色选择
        textColor = Color.TRANSPARENT;

        ToastUtil.showToast(this, "已重置所有设置", Toast.LENGTH_SHORT);
    }

    private void applyTransformation(float scale, float translateX, float translateY, float rotation) {
        int imageWidth = foregroundImage.getDrawable().getIntrinsicWidth();
        int imageHeight = foregroundImage.getDrawable().getIntrinsicHeight();
        int viewWidth = foregroundImage.getWidth();
        int viewHeight = foregroundImage.getHeight();

        float scaledWidth = imageWidth * scale;
        float scaledHeight = imageHeight * scale;

        // 计算允许的平移范围（保留原有范围计算逻辑）
        float minTx = -scaledWidth + 10;
        float maxTx = viewWidth - 10;
        float clampedTx = Math.max(minTx, Math.min(translateX, maxTx));

        float minTy = -scaledHeight + 10;
        float maxTy = viewHeight - 10;
        float clampedTy = Math.max(minTy, Math.min(translateY, maxTy));

        // 更新全局变量
        initialScale = scale;
        initialTranslateX = clampedTx;
        initialTranslateY = clampedTy;
        initialRotation = rotation;

        // 恢复原有矩阵变换顺序
        matrix.reset();
        matrix.postScale(initialScale, initialScale);
        matrix.postRotate(initialRotation);
        matrix.postTranslate(initialTranslateX, initialTranslateY);

        foregroundImage.setImageMatrix(matrix);
        foregroundImage.invalidate();
    }
    private void updateSeekBarProgress(float clampedTx, float clampedTy, float scaledWidth, float scaledHeight, int viewWidth, int viewHeight) {
        // 更新水平进度条
        float minTx = -scaledWidth + 10;
        float maxTx = viewWidth - 10;
        float txRange = maxTx - minTx;
        if (txRange > 0) {
            int progressX = (int) (((clampedTx - minTx) / txRange) * 1000);
            progressX = Math.max(0, Math.min(progressX, 1000));
            if (seekBarTranslateX.getProgress() != progressX) {
                seekBarTranslateX.setOnSeekBarChangeListener(null);
                seekBarTranslateX.setProgress(progressX);
                seekBarTranslateX.setOnSeekBarChangeListener(translateXListener);
            }
        }

        // 更新垂直进度条
        float minTy = -scaledHeight + 10;
        float maxTy = viewHeight - 10;
        float tyRange = maxTy - minTy;
        if (tyRange > 0) {
            int progressY = (int) (((clampedTy - minTy) / tyRange) * 1000);
            progressY = Math.max(0, Math.min(progressY, 1000));
            if (seekBarTranslateY.getProgress() != progressY) {
                seekBarTranslateY.setOnSeekBarChangeListener(null);
                seekBarTranslateY.setProgress(progressY);
                seekBarTranslateY.setOnSeekBarChangeListener(translateYListener);
            }
        }
    }

    // 打开图库选择图片
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }


    private void showColorPickerDialog() {

        Intent intent = new Intent(BackgroundPickerActivity.this, color_picker_view.class);
        startActivityForResult(intent, 99);
    }


    private void captureAndSaveView(File outputFile) {
        // 获取预览容器尺寸
        View container = findViewById(R.id.previewContainer);
        int width = container.getWidth();
        int height = container.getHeight();

        if (width <= 0 || height <= 0) {
            ToastUtil.showToast(this, "预览区域尺寸无效", Toast.LENGTH_SHORT);
            return;
        }

        // 创建画布
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        // 绘制背景
        drawBackground(canvas, width, height);

        // 绘制前景
        drawForeground(canvas);

        // 保存结果
        saveBitmap(result, outputFile);
    }

    private void drawBackground(Canvas canvas, int width, int height) {
        Drawable bg = backgroundImage.getDrawable();
        Drawable bgColor = backgroundImage.getBackground();

        if (bg != null) { // 图片背景
            Bitmap bgBitmap = ((BitmapDrawable) bg).getBitmap();
            if (bgBitmap != null) {
                // 使用原有背景图片的显示方式
                Matrix bgMatrix = new Matrix();
                float scale = Math.min(
                        (float) width / bgBitmap.getWidth(),
                        (float) height / bgBitmap.getHeight()
                );
                bgMatrix.postScale(scale, scale);
                bgMatrix.postTranslate(
                        (width - bgBitmap.getWidth() * scale) / 2,
                        (height - bgBitmap.getHeight() * scale) / 2
                );
                canvas.drawBitmap(bgBitmap, bgMatrix, null);
            }
        } else if (bgColor instanceof ColorDrawable) {
            int color = ((ColorDrawable) bgColor).getColor();
            // 使用带透明度的绘制方式
            canvas.drawColor(color);
        } else {
            canvas.drawColor(Color.TRANSPARENT);
        }
    }
    private void drawForeground(Canvas canvas) {
        Bitmap fgBitmap = ((BitmapDrawable) foregroundImage.getDrawable()).getBitmap();
        if (fgBitmap == null) {
            ToastUtil.showToast(this, "前景图片无效", Toast.LENGTH_SHORT);
            return;
        }

        // 应用当前变换矩阵
        Matrix matrix = new Matrix(foregroundImage.getImageMatrix());
        canvas.drawBitmap(fgBitmap, matrix, null);
    }

    private void saveBitmap(Bitmap bitmap, File outputFile) {
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            ToastUtil.showToast(this, "保存成功：" + outputFile.getAbsolutePath(), Toast.LENGTH_LONG);
        } catch (IOException e) {
            e.printStackTrace();
            ToastUtil.showToast(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT);
        } finally {
            bitmap.recycle();
        }
    }
    private void saveResult() {
        // 获取背景信息
        Drawable backgroundDrawable = backgroundImage.getDrawable();
        Drawable bgColorDrawable = backgroundImage.getBackground();

        // 检查是否设置了背景（颜色或图片）
        boolean hasBackground = (backgroundDrawable != null) ||
                (bgColorDrawable instanceof ColorDrawable &&
                        ((ColorDrawable) bgColorDrawable).getAlpha() > 0);

        if (!hasBackground) {
            ToastUtil.showToast(this, "请先设置背景颜色或图片！", Toast.LENGTH_SHORT);
            return;
        }

        // 创建输出文件
        File newFile = createOutputFile();
        if (newFile == null) return;

        // 执行保存
        captureAndSaveView(newFile);
    }

    private File createOutputFile() {
        File originalFile = new File(imagePath);
        if (!originalFile.exists()) return null;

        String dirPath = originalFile.getParent();
        String fileName = "Result_" + System.currentTimeMillis() + ".png";
        return new File(dirPath, fileName);
    }

    private void handleColorResult(Intent data) {
        String colorHex = data.getStringExtra("bgColor");
        Log.d("主界面：","颜色值："+colorHex);

        try {
            // 直接解析完整颜色值
            textColor = Color.parseColor(colorHex);
            backgroundImage.setBackgroundColor(textColor);



        } catch (IllegalArgumentException e) {
            ToastUtil.showToast(this, "无效的颜色值", Toast.LENGTH_SHORT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK && resultCode !=99) {
            return; // 如果用户左滑退出，直接返回
        }

        if (requestCode == PICK_IMAGE_REQUEST && data != null) {
            Uri imageUri = data.getData();
            try {
                // 加载缩放后的图片
                Bitmap bitmap = FileUtils.getBitmapFromUri(imageUri, this);
                if (bitmap != null) {
                    String imagePath1 = FileUtils.getPathFromUri(imageUri, this);
                    imagePath = imagePath1;

                    bitmap = FileUtils.rotateImageIfRequired(bitmap, imagePath1);

                    // 清除背景颜色
                    backgroundImage.setBackgroundColor(Color.TRANSPARENT);
                    // 设置背景图片
                    backgroundImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    backgroundImage.setImageBitmap(bitmap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (requestCode == 99 && data != null) {
            handleColorResult(data); // 处理颜色结果

            Log.d("颜色设置界面返回：","返回值11："+textColor);

            Log.d("颜色设置界面返回：","返回值："+textColor);
            backgroundImage.setImageDrawable(null); // 清除图片背景

            // 设置背景颜色
            backgroundImage.setBackgroundColor(textColor);


        }
    }
}

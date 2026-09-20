package com.dsq.rebackground;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

public class CircleCropActivity extends AppCompatActivity {

    private ImageView imageView;
    private EditText etRadius, etX, etY;
    private SeekBar seekBarScale;
    private Bitmap originalBitmap;
    private Matrix matrix = new Matrix();
    private float scaleFactor = 1.0f;
    private int previewWidth, previewHeight; // 预览界面的宽高
    private int previewCenterX, previewCenterY; // 预览界面的中心点
    private File tempFile; // 临时文件
    private CircleOverlayView circleOverlayView; // 用于绘制红色圆圈的叠加视图
    private String imageKey, imagePath;
    private boolean is_hide_quan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circle_crop);

        // 初始化控件
        imageView = findViewById(R.id.imageView);
        etRadius = findViewById(R.id.et_radius);
        etX = findViewById(R.id.et_x);
        etY = findViewById(R.id.et_y);
        seekBarScale = findViewById(R.id.seekBarScale);
        Button btnCrop = findViewById(R.id.btn_crop);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnReset = findViewById(R.id.btn_reset);

        // 初始化叠加视图
        FrameLayout frameLayout = findViewById(R.id.frameLayout);
        circleOverlayView = new CircleOverlayView(this);
        frameLayout.addView(circleOverlayView);

        // 获取传递的 imageKey
        imageKey = getIntent().getStringExtra("imagekey");


        if (imageKey != null) {
            // 从缓存中获取图片和属性信息
            originalBitmap = BitmapCache.getInstance().getBitmap(imageKey);
            BitmapCache.ImageInfo imageInfo = BitmapCache.getInstance().getImageInfo(imageKey);

            if (originalBitmap != null) {
                imageView.setImageBitmap(originalBitmap);

                // 监听 ImageView 的布局完成事件
                imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // 移除监听器，避免重复调用
                        imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                        // 获取预览界面的宽高
                        previewWidth = imageView.getWidth();
                        previewHeight = imageView.getHeight();
                        previewCenterX = previewWidth / 2;
                        previewCenterY = previewHeight / 2 - 350;

                        // 设置 SeekBar 的最大值
                        seekBarScale.setMax(800); // 最大缩放比例为 2 倍
                        seekBarScale.setProgress(100); // 初始缩放比例为 1 倍

                        // 初始化图片位置
                        centerImage();
                    }
                });

            } else {
                ToastUtil.showToast(this, "缓存读取失败！", Toast.LENGTH_SHORT);
                finish();
            }
        } else {
            ToastUtil.showToast(this, "没有找到图片！", Toast.LENGTH_SHORT);
            finish();
        }

        // 设置滑动条监听
        seekBarScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                scaleFactor = progress / 100f; // 将进度值转换为缩放比例
                if (scaleFactor <= 0.05) {
                    scaleFactor = 0.05f;
                }
                applyScale();
                updateCircleOverlay(); // 更新红色圆圈
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // 设置输入框监听
        TextWatcher textWatcher = new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (is_hide_quan){
                    ToastUtil.showToast(CircleCropActivity.this, "在未保存的效果图上裁剪，可能会导致位置不准确！", Toast.LENGTH_LONG);
                    is_hide_quan = false;
                }

                updateCircleOverlay(); // 更新红色圆圈
            }
        };
        etRadius.addTextChangedListener(textWatcher);
        etX.addTextChangedListener(textWatcher);
        etY.addTextChangedListener(textWatcher);

        // 裁剪按钮点击事件
        btnCrop.setOnClickListener(v -> {
            cropImage();

            circleOverlayView.setVisibility(View.INVISIBLE); // 裁剪后隐藏圆圈
        });

        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> saveImage());

        // 重来按钮点击事件
        btnReset.setOnClickListener(v -> reset());
    }

    // 初始化图片位置
    private void centerImage() {
        float imageCenterX = originalBitmap.getWidth() / 2f;
        float imageCenterY = originalBitmap.getHeight() / 2f;

        matrix.setTranslate(previewCenterX - imageCenterX, previewCenterY - imageCenterY);
        imageView.setImageMatrix(matrix);
    }

    // 应用缩放
    private void applyScale() {
        float[] values = new float[9];
        matrix.getValues(values);
        float currentScale = values[Matrix.MSCALE_X];
        float currentTranslateX = values[Matrix.MTRANS_X];
        float currentTranslateY = values[Matrix.MTRANS_Y];

        float newTranslateX = previewCenterX - (previewCenterX - currentTranslateX) * (scaleFactor / currentScale);
        float newTranslateY = previewCenterY - (previewCenterY - currentTranslateY) * (scaleFactor / currentScale);

        matrix.setScale(scaleFactor, scaleFactor, previewCenterX, previewCenterY);
        matrix.postTranslate(newTranslateX - currentTranslateX, newTranslateY - currentTranslateY);
        imageView.setImageMatrix(matrix);

        alignImageCenter();
    }

    // 中心点对齐逻辑
    private void alignImageCenter() {
        float[] values = new float[9];
        matrix.getValues(values);
        float currentTranslateX = values[Matrix.MTRANS_X];
        float currentTranslateY = values[Matrix.MTRANS_Y];

        float imageCenterX = currentTranslateX + (imageView.getDrawable().getIntrinsicWidth() / 2f) * values[Matrix.MSCALE_X];
        float imageCenterY = currentTranslateY + (imageView.getDrawable().getIntrinsicHeight() / 2f) * values[Matrix.MSCALE_Y];

        float deltaX = previewCenterX - imageCenterX;
        float deltaY = previewCenterY - imageCenterY;

        if (Math.abs(deltaX) > 1 || Math.abs(deltaY) > 1) {
            matrix.postTranslate(deltaX, deltaY);
            imageView.setImageMatrix(matrix);
        }
    }

    // 更新红色圆圈
    private void updateCircleOverlay() {
        if (is_hide_quan) {
            return;
        }
        try {
            int radius = Integer.parseInt(etRadius.getText().toString());
            int x = Integer.parseInt(etX.getText().toString());
            int y = Integer.parseInt(etY.getText().toString());

            // 将 ImageView 的坐标转换为 CircleOverlayView 的坐标
            float[] point = new float[]{x, y};
            matrix.mapPoints(point); // 应用 ImageView 的变换矩阵
            int overlayX = (int) point[0];
            int overlayY = (int) point[1];

            // 根据缩放比例更新半径
            float[] values = new float[9];
            matrix.getValues(values);
            float currentScale = values[Matrix.MSCALE_X];
            int scaledRadius = (int) (radius * currentScale);

            // 设置圆圈的位置和大小
            circleOverlayView.setCircle(overlayX, overlayY, scaledRadius);
            circleOverlayView.setVisibility(View.VISIBLE); // 显示圆圈
            circleOverlayView.invalidate(); // 刷新绘制
        } catch (NumberFormatException e) {
            // 输入无效时忽略
        }
    }

    // 圆形裁剪
    private void cropImage() {
        try {
            int radius = Integer.parseInt(etRadius.getText().toString());
            int x = Integer.parseInt(etX.getText().toString());
            int y = Integer.parseInt(etY.getText().toString());

            // 检查输入值是否有效
            if (radius <= 0 || x < 0 || y < 0 || x >= originalBitmap.getWidth() || y >= originalBitmap.getHeight()) {
                ToastUtil.showToast(this, "输入的半径或坐标无效", Toast.LENGTH_SHORT);
                return;
            }

            // 创建裁剪后的 Bitmap（比圆形直径大 20 像素的正方形）
            int diameter = radius * 2;
            int squareSize = diameter + 20; // 正方形边长 = 直径 + 20 像素
            Bitmap output = Bitmap.createBitmap(squareSize, squareSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            // 绘制圆形
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0); // 清空画布
            canvas.drawCircle(squareSize / 2f, squareSize / 2f, radius, paint); // 圆形居中
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

            // 计算原图中圆形区域的矩形
            Rect srcRect = new Rect(x - radius, y - radius, x + radius, y + radius);
            Rect dstRect = new Rect(10, 10, squareSize - 10, squareSize - 10); // 圆形区域在正方形中的位置

            // 将原图中的圆形区域绘制到裁剪后的 Bitmap 中
            canvas.drawBitmap(originalBitmap, srcRect, dstRect, paint);

            // 显示裁剪后的图片
            imageView.setImageBitmap(output);

            // 重新初始化图片位置，使其居中
            centerImageAfterCrop(output);
            //裁剪后不显示圆圈
            is_hide_quan = true;
        } catch (NumberFormatException e) {
            ToastUtil.showToast(this, "请输入有效的数字", Toast.LENGTH_SHORT);
            is_hide_quan = false;
        }
    }

    private void centerImageAfterCrop(Bitmap croppedBitmap) {
        if (croppedBitmap == null || imageView == null) {
            return;
        }

        // 获取裁剪后图片的宽高
        int imageWidth = croppedBitmap.getWidth();
        int imageHeight = croppedBitmap.getHeight();

        // 获取 ImageView 的宽高
        int viewWidth = imageView.getWidth();
        int viewHeight = imageView.getHeight();

        // 计算初始缩放比例
        float scaleX = (float) viewWidth / imageWidth;
        float scaleY = (float) viewHeight / imageHeight;
        float initialScale = Math.min(scaleX, scaleY);

        // 计算初始偏移量，使图片居中
        float translateX = (viewWidth - imageWidth * initialScale) / 2;
        float translateY = (viewHeight - imageHeight * initialScale) / 2;

        // 重置 Matrix 并应用新的变换
        matrix.reset();
        matrix.setScale(initialScale, initialScale);
        matrix.postTranslate(translateX, translateY);

        // 设置 Matrix 并刷新视图
        imageView.setImageMatrix(matrix);
        imageView.setImageBitmap(croppedBitmap);
        imageView.invalidate();
    }

    // 保存裁剪后的图片为临时文件
    private File saveTempImage(Bitmap bitmap) {
        File tempFile = null;
        try {
            File directory = new File(getCacheDir(), "temp_images");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            tempFile = new File(directory, "temp_cropped_image.png");
            FileOutputStream out = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tempFile;
    }

    // 保存裁剪后的图片
    private void saveImage() {
        is_hide_quan = false;
        Bitmap croppedBitmap = ((android.graphics.drawable.BitmapDrawable) imageView.getDrawable()).getBitmap();
        if (croppedBitmap != null) {

            tempFile = saveTempImage(croppedBitmap);
            if (tempFile != null && tempFile.exists()) {
                boolean isSaved = FileUtils.saveImageToStorage(BitmapFactory.decodeFile(tempFile.getAbsolutePath()), this);
                if (isSaved) {

                    tempFile.delete(); // 删除临时文件


                    // 更新界面显示的图片
                    originalBitmap=croppedBitmap;
                    ToastUtil.showToast(this, "图片已更新，可以继续操作！", Toast.LENGTH_SHORT);
                    imageView.setImageBitmap(originalBitmap);
                    centerImage();
                    applyScale();
                } else {
                        ToastUtil.showToast(this, "保存失败,请重新裁剪！", Toast.LENGTH_SHORT);
                        reset();

                    }
                } else {
                    ToastUtil.showToast(this, "保存临时图片失败！", Toast.LENGTH_SHORT);
                    reset();
                }
            } else {
                ToastUtil.showToast(this, "没有可保存的图片！", Toast.LENGTH_SHORT);
                finish();
            }
        }

        // 重来功能
        private void reset(){
            // 恢复原图
            imageView.setImageBitmap(originalBitmap);
            centerImage();

            // 清空输入框
            etRadius.setText("");
            etX.setText("");
            etY.setText("");

            // 隐藏圆圈
            circleOverlayView.setVisibility(View.INVISIBLE);
        }

}
package com.dsq.rebackground;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;

import com.dsq.rebackground.utils.BitmapCache;
import com.dsq.rebackground.utils.FileUtils;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FreeCropActivity extends AppCompatActivity {

    private DrawableImageView imageView; // 使用自定义的 DrawableImageView
    private Bitmap originalBitmap;
    private Path path;
    private Paint cropPaint;
    private Bitmap croppedBitmap;
    private File tempFile; // 临时文件
    private SeekBar seekBarScale; // 滑动条
    private Matrix matrix = new Matrix(); // 用于图片缩放和移动
    private float scaleFactor = 1.0f; // 缩放比例
    private int previewWidth, previewHeight; // 预览界面的宽高
    private int previewCenterX, previewCenterY; // 预览界面的中心点
    private String imageKey,imagePath;
    private Button reset_btn;
    // 在FreeCropActivity类中添加
    private List<Path> pathList = new ArrayList<>();  // 存储所有闭合路径
    private Path currentPath = new Path();  // 当前正在绘制的路径
    private boolean is_show_tell=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_crop);

        // 初始化控件
        imageView = findViewById(R.id.imageView);
        seekBarScale = findViewById(R.id.seekBarScale);
        Button btnCrop = findViewById(R.id.btn_crop);
        Button btnSave = findViewById(R.id.btn_save);
        reset_btn=findViewById(R.id.btn_reset);


        // 获取传递的 imageKey
        imageKey = getIntent().getStringExtra("imagekey");


        if (imageKey != null) {
            // 从缓存中获取图片和属性信息
            // 从缓存获取 Bitmap 和 ImageInfo
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
                        previewCenterY = previewHeight / 2;

                        // 设置 SeekBar 的最大值
                        seekBarScale.setMax(400); // 最大缩放比例为 4 倍
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

        // 初始化路径和画笔
        path = new Path();
        cropPaint = new Paint();
        cropPaint.setAntiAlias(true);

        // 设置滑动条监听
        seekBarScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                scaleFactor = progress / 100f; // 将进度值转换为缩放比例
                if (scaleFactor <= 0.05) {
                    scaleFactor = 0.05f;
                }
                applyScale();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });



// 在触摸事件中更新路径
        imageView.setOnTouchListener((v, event) -> {
            if (is_show_tell) {
                ToastUtil.showToast(this, "提示: 在未保存的裁剪区操作，容易裁剪失败，因此会裁剪新绘制的区域！", Toast.LENGTH_LONG);
                pathList.clear();
                currentPath.reset();
                imageView.setPath(new Path());
                cropPaint.reset();
                is_show_tell = false;
            }

            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    currentPath.moveTo(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    currentPath.lineTo(x, y);
                    imageView.setPath(currentPath); // 实时更新当前路径
                    break;
                case MotionEvent.ACTION_UP:
                    currentPath.close();
                    // 保存闭合路径并创建新路径
                    pathList.add(new Path(currentPath));
                    currentPath.reset();
                    break;
            }
            return true;
        });

        // 裁剪按钮点击事件
        btnCrop.setOnClickListener(v -> cropImage());

        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> saveImage());
        reset_btn.setOnClickListener(v->reset());
    }

    private void centerImage() {
        if (originalBitmap == null || imageView == null) {
            Log.d("FreeCropActivity", "originalBitmap or imageView is null");
            return;
        }

        // 获取图片的宽高
        int imageWidth = originalBitmap.getWidth();
        int imageHeight = originalBitmap.getHeight();
        Log.d("FreeCropActivity", "Image dimensions - Width: " + imageWidth + ", Height: " + imageHeight);

        // 获取 ImageView 的宽高
        int viewWidth = imageView.getWidth();
        int viewHeight = imageView.getHeight();
        Log.d("FreeCropActivity", "View dimensions - Width: " + viewWidth + ", Height: " + viewHeight);

        // 计算初始缩放比例，使图片适配预览界面
        float scaleX = (float) viewWidth / imageWidth;
        float scaleY = (float) viewHeight / imageHeight;
        float initialScale = Math.min(scaleX, scaleY);
        Log.d("FreeCropActivity", "Initial scale - scaleX: " + scaleX + ", scaleY: " + scaleY + ", initialScale: " + initialScale);

        // 计算图片的中心点
        float imageCenterX = imageWidth / 2f;
        float imageCenterY = imageHeight / 2f;
        Log.d("FreeCropActivity", "Image center - X: " + imageCenterX + ", Y: " + imageCenterY);

        // 计算 ImageView 的中心点
        float viewCenterX = viewWidth / 2f;
        float viewCenterY = viewHeight / 2f;
        Log.d("FreeCropActivity", "View center - X: " + viewCenterX + ", Y: " + viewCenterY);

        // 重置矩阵
        matrix.reset();

        // 设置初始缩放
        matrix.setScale(initialScale, initialScale);

        // 计算平移量，使图片居中
        float scaledImageWidth = imageWidth * initialScale;
        float scaledImageHeight = imageHeight * initialScale;
        float translateX = viewCenterX - (scaledImageWidth / 2f);
        float translateY = viewCenterY - (scaledImageHeight / 2f);

        // 应用平移
        matrix.postTranslate(translateX, translateY);

        // 设置矩阵到 ImageView
        imageView.setImageMatrix(matrix);

        // 打印矩阵值
        float[] values = new float[9];
        matrix.getValues(values);
        Log.d("FreeCropActivity", "Matrix values: " +
                "\nScaleX: " + values[Matrix.MSCALE_X] +
                "\nScaleY: " + values[Matrix.MSCALE_Y] +
                "\nTranslateX: " + values[Matrix.MTRANS_X] +
                "\nTranslateY: " + values[Matrix.MTRANS_Y]);
    }
    // 应用缩放
    private void applyScale() {
        float[] values = new float[9];
        matrix.getValues(values);
        float currentScale = values[Matrix.MSCALE_X];
        float currentTranslateX = values[Matrix.MTRANS_X];
        float currentTranslateY = values[Matrix.MTRANS_Y];

        // 计算新的平移值，保持缩放中心点固定
        float newTranslateX = previewCenterX - (previewCenterX - currentTranslateX) * (scaleFactor / currentScale);
        float newTranslateY = previewCenterY - (previewCenterY - currentTranslateY) * (scaleFactor / currentScale);

        // 应用缩放和平移
        matrix.setScale(scaleFactor, scaleFactor, previewCenterX, previewCenterY); // 缩放中心点为 ImageView 的中心点
        matrix.postTranslate(newTranslateX - currentTranslateX, newTranslateY - currentTranslateY);
        imageView.setImageMatrix(matrix);

        // 确保图片居中
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

    //=====================================
    // 在 FreeCropActivity 中添加以下方法
    private void cropImage() {
        if (pathList.isEmpty()) {
            ToastUtil.showToast(this, "请先绘制裁剪区域", Toast.LENGTH_SHORT);
            return;
        }

        // 创建与源图片尺寸相同的 Bitmap
        Bitmap output = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        // 获取变换矩阵
        Matrix matrix = new Matrix();
        imageView.getImageMatrix().invert(matrix);

        // 转换所有路径到图片坐标系并计算包围盒
        List<Path> transformedPaths = new ArrayList<>();
        List<Float> areas = new ArrayList<>();

        for (Path p : pathList) {
            Path transformedPath = new Path(p);
            transformedPath.transform(matrix);
            transformedPaths.add(transformedPath);

            RectF bounds = new RectF();
            transformedPath.computeBounds(bounds, true);
            areas.add(bounds.width() * bounds.height());
        }

        // 按面积从大到小排序
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < transformedPaths.size(); i++) indices.add(i);
        indices.sort((i1, i2) -> Float.compare(areas.get(i2), areas.get(i1)));

        // 构建组合路径
        Path combinedPath = new Path();
        boolean isFirst = true;

        for (int idx : indices) {
            Path p = transformedPaths.get(idx);
            if (isFirst) {
                combinedPath.op(p, Path.Op.UNION);
                isFirst = false;
            } else {
                combinedPath.op(p, Path.Op.DIFFERENCE);
            }
        }

        // 绘制裁剪区域
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawPath(combinedPath, cropPaint);
        cropPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(originalBitmap, 0, 0, cropPaint);

        // 更新状态
        is_show_tell = true;
        croppedBitmap = output;
        imageView.setImageBitmap(croppedBitmap);

        // 清空路径记录
        pathList.clear();
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
        if (croppedBitmap == null) {
            ToastUtil.showToast(this, "请先裁剪图片！", Toast.LENGTH_SHORT);
            return;
        }

        // 保存为临时文件
        tempFile = saveTempImage(croppedBitmap);
        if (tempFile != null && tempFile.exists()) {
            // 将临时文件保存到外部存储
            boolean isSaved = FileUtils.saveImageToStorage(BitmapFactory.decodeFile(tempFile.getAbsolutePath()), this);
            if (isSaved) {
                ToastUtil.showToast(this, "图片已保存！", Toast.LENGTH_SHORT);
                originalBitmap=croppedBitmap.copy(Bitmap.Config.ARGB_8888, true);
                reset();
                tempFile.delete(); // 删除临时文件
            } else {
                reset();
                ToastUtil.showToast(this, "保存失败！", Toast.LENGTH_SHORT);

            }
        } else {
            reset();
            ToastUtil.showToast(this, "保存临时图片失败！", Toast.LENGTH_SHORT);
        }
    }
    public List<Path> getPathList() {
        return new ArrayList<>(pathList); // 返回副本保证线程安全
    }

    public Path getCurrentPath() {
        return new Path(currentPath); // 返回副本
    }
    private void reset(){
        imageView.setImageBitmap(originalBitmap);
        centerImage();
        is_show_tell=false;  //表示已重置或已保存，不要提示

        // 重置矩阵并居中图片
        matrix.reset();
        centerImage();

        // 清除所有路径和点
        path.reset();
        cropPaint.reset();

        imageView.setPath(path);

    }
}
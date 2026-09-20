package com.dsq.rebackground;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
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

public class RectCropActivity extends AppCompatActivity {

    private static final String TAG = "RectCropActivity";

    private ImageView imageView;
    private View cropFrame;
    private EditText etWidth, etHeight;
    private SeekBar seekBarX, seekBarY;
    private Bitmap originalBitmap;
    private Matrix matrix = new Matrix();
    private float scaleFactor = 1.0f;
    private PointF start = new PointF();
    private float lastX = 0, lastY = 0; // 记录线框上一次的位置
    private int previewWidth, previewHeight; // 预览界面的宽高
    private int previewCenterX, previewCenterY; // 预览界面的中心点
    private File tempFile; // 临时文件
    private Button res_btn;
    private boolean is_hide_quan = false; //裁剪之后不显示线框
    private String imageKey,imagePath;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rect_crop);

        // 初始化控件
        imageView = findViewById(R.id.imageView);
        cropFrame = findViewById(R.id.cropFrame);
        etWidth = findViewById(R.id.et_width);
        etHeight = findViewById(R.id.et_height);
        seekBarX = findViewById(R.id.seekBarX);
        seekBarY = findViewById(R.id.seekBarY);
        res_btn=findViewById(R.id.btn_reset);

        // 绑定按钮
        Button btnCrop = findViewById(R.id.btn_crop);
        Button btnSave = findViewById(R.id.btn_save);
        // 获取传递的 imageKey
        imageKey = getIntent().getStringExtra("imagekey");

        if (imageKey != null) {
            // 从缓存中获取图片和属性信息
            // 从缓存获取 Bitmap 和 ImageInfo
            originalBitmap = BitmapCache.getInstance().getBitmap(imageKey);
            BitmapCache.ImageInfo imageInfo = BitmapCache.getInstance().getImageInfo(imageKey);


            if (originalBitmap != null) {
                imagePath=imageInfo.getPath();
                imageView.setImageBitmap(originalBitmap);
//                / 监听 ImageView 的布局完成事件
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
                        seekBarX.setMax(previewWidth);
                        seekBarY.setMax(previewHeight);

                        // 设置 SeekBar 的初始值
                        seekBarX.setProgress(previewCenterX);
                        seekBarY.setProgress(previewCenterY);
                        res_btn.setOnClickListener(v->reset());
                        // 初始化图片和线框位置
                        centerImageAndCropFrame();
                    }
                });
            }else {
                ToastUtil.showToast(this, "缓存读取失败！", Toast.LENGTH_SHORT);
                finish();
            }
        } else {
            ToastUtil.showToast(this, "没有找到图片！", Toast.LENGTH_SHORT);
            finish();
        }



        // 设置宽高输入监听
        etWidth.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (is_hide_quan){
                    ToastUtil.showToast(RectCropActivity.this, "在未保存的效果图上裁剪，可能会导致位置不准确！", Toast.LENGTH_LONG);
                    is_hide_quan = false;
                }
                updateCropFrameSize();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etHeight.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (is_hide_quan){
                    ToastUtil.showToast(RectCropActivity.this, "在未保存的效果图上裁剪，可能会导致位置不准确！", Toast.LENGTH_LONG);
                    is_hide_quan = false;
                }
                updateCropFrameSize();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 设置滑动条监听
        seekBarX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (is_hide_quan){
                    ToastUtil.showToast(RectCropActivity.this, "在未保存的效果图上裁剪，可能会导致位置不准确！", Toast.LENGTH_LONG);
                    is_hide_quan = false;
                }
                updateCropFramePosition();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekBarY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (is_hide_quan){
                    ToastUtil.showToast(RectCropActivity.this, "在未保存的效果图上裁剪，可能会导致位置不准确！", Toast.LENGTH_LONG);
                    is_hide_quan = false;
                }
                updateCropFramePosition();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // 设置图片缩放监听
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        start.set(event.getX(), event.getY());
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        float x = event.getX(0) - event.getX(1);
                        float y = event.getY(0) - event.getY(1);
                        start.set((float) Math.sqrt(x * x + y * y), 0);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (event.getPointerCount() == 2) {
                            float x1 = event.getX(0) - event.getX(1);
                            float y1 = event.getY(0) - event.getY(1);
                            float end = (float) Math.sqrt(x1 * x1 + y1 * y1);

                            float newScaleFactor = end / start.x;
                            float scaleIncrement = (newScaleFactor - 1) * 0.1f;
                            scaleFactor += scaleIncrement;
                            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 8.0f));

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

                            start.set(end, 0);
                            alignImageCenter();
                        }
                        break;
                }
                return true;
            }
        });

        // 裁剪按钮点击事件
        btnCrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                previewCroppedImage();
            }
        });

        // 保存按钮点击事件
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCroppedImage();
            }
        });
    }


    // 旧中心点对齐逻辑
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


    // 旧初始化图片和线框位置
    private void centerImageAndCropFrame() {
        float imageCenterX = originalBitmap.getWidth() / 2f;
        float imageCenterY = originalBitmap.getHeight() / 2f;

        matrix.setTranslate(previewCenterX - imageCenterX, previewCenterY - imageCenterY);
        imageView.setImageMatrix(matrix);

    }

    // 裁剪图片的方法
    private Bitmap cropImage() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cropFrame.getLayoutParams();
        int cropX = params.leftMargin;
        int cropY = params.topMargin;
        int cropWidth = params.width;
        int cropHeight = params.height;

        float[] values = new float[9];
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        float translateX = values[Matrix.MTRANS_X];
        float translateY = values[Matrix.MTRANS_Y];

        int srcX = (int) ((cropX - translateX) / scale);
        int srcY = (int) ((cropY - translateY) / scale);
        int srcWidth = (int) (cropWidth / scale);
        int srcHeight = (int) (cropHeight / scale);

        srcX = Math.max(0, srcX);
        srcY = Math.max(0, srcY);
        srcWidth = Math.min(originalBitmap.getWidth() - srcX, srcWidth);
        srcHeight = Math.min(originalBitmap.getHeight() - srcY, srcHeight);

        // 裁剪图片
        Bitmap croppedBitmap = Bitmap.createBitmap(originalBitmap, srcX, srcY, srcWidth, srcHeight);

        // 读取用户输入的宽高
        String widthText = etWidth.getText().toString();
        String heightText = etHeight.getText().toString();

        is_hide_quan=true;
        cropFrame.setVisibility(View.INVISIBLE);  //裁剪后隐藏
        if (widthText.isEmpty() || heightText.isEmpty()) {
            ToastUtil.showToast(this, "输入无效，默认位置大小裁剪！", Toast.LENGTH_SHORT);
            return croppedBitmap; // 如果用户未输入宽高，直接返回裁剪后的图片
        }

        try {
            int targetWidth = Integer.parseInt(widthText);
            int targetHeight = Integer.parseInt(heightText);

            // 将裁剪后的图片缩放到用户输入的宽高

            return Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, true);
        } catch (NumberFormatException e) {
            ToastUtil.showToast(this, "输入无效，默认位置大小裁剪！", Toast.LENGTH_SHORT);
            return croppedBitmap;// 如果输入无效，直接返回裁剪后的图片
        }


    }

    // 预览裁剪效果
    private void previewCroppedImage() {
        Bitmap croppedBitmap = cropImage();

        if (croppedBitmap != null) {
            // 保存裁剪后的图片为临时文件
            tempFile = saveTempImage(croppedBitmap);
            if (tempFile != null) {
                // 重新加载临时图片
                Bitmap tempBitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath());
                if (tempBitmap != null) {
                    imageView.setImageBitmap(tempBitmap);
                    adjustCroppedImageDisplay(tempBitmap); // 调整显示
                    ToastUtil.showToast(this, "裁剪成功！", Toast.LENGTH_SHORT);
                } else {
                    ToastUtil.showToast(this, "加载临时图片失败！", Toast.LENGTH_SHORT);
                }
            } else {
                ToastUtil.showToast(this, "保存临时图片失败！", Toast.LENGTH_SHORT);
            }
        } else {
            ToastUtil.showToast(this, "裁剪失败！", Toast.LENGTH_SHORT);
        }
    }


    // 新增方法：调整裁剪后图片的显示
    private void adjustCroppedImageDisplay(Bitmap bitmap) {
        // 计算缩放比例
        float scale = Math.min((float) previewWidth / bitmap.getWidth(),
                (float) previewHeight / bitmap.getHeight());

        // 重置矩阵并应用缩放
        matrix.reset();
        matrix.postScale(scale, scale);

        // 计算平移使图片居中
        float translateX = (previewWidth - bitmap.getWidth() * scale) / 2;
        float translateY = (previewHeight - bitmap.getHeight() * scale) / 2;
        matrix.postTranslate(translateX, translateY);

        imageView.setImageMatrix(matrix);

        // 重置线框位置
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cropFrame.getLayoutParams();
        params.width = (int) (bitmap.getWidth() * scale);
        params.height = (int) (bitmap.getHeight() * scale);
        params.leftMargin = (int) translateX;
        params.topMargin = (int) translateY;
        cropFrame.setLayoutParams(params);
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

    private void saveCroppedImage() {
        is_hide_quan=false;
        if (tempFile != null && tempFile.exists()) {
            // 读取用户输入的宽高
            String widthText = etWidth.getText().toString();
            String heightText = etHeight.getText().toString();

            if (widthText.isEmpty() || heightText.isEmpty()) {
                ToastUtil.showToast(this, "请输入宽高", Toast.LENGTH_SHORT);
                return;
            }

            try {
                int targetWidth = Integer.parseInt(widthText);
                int targetHeight = Integer.parseInt(heightText);

                // 加载裁剪后的图片
                Bitmap croppedBitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath());
                if (croppedBitmap == null) {
                    ToastUtil.showToast(this, "加载裁剪图片失败", Toast.LENGTH_SHORT);
                    return;
                }

                // 将裁剪后的图片缩放到用户输入的宽高
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, true);

                // 保存缩放后的图片
                boolean isSaved = FileUtils.saveImageToStorage(scaledBitmap, this);
                if (isSaved) {

                    // 更新界面显示的图片
                    originalBitmap=croppedBitmap;
                    imageView.setImageBitmap(originalBitmap);
                    centerImageAndCropFrame();
                    updateCropFramePosition();
                    updateCropFrameSize();
                    ToastUtil.showToast(this, "图片已保存并更新，可以继续操作了!", Toast.LENGTH_SHORT);
                    tempFile.delete(); // 删除临时文件
                } else {

                    ToastUtil.showToast(this, "保存失败!", Toast.LENGTH_SHORT);
                }
            } catch (NumberFormatException e) {

                ToastUtil.showToast(this, "请输入有效的宽高!", Toast.LENGTH_SHORT);
            }
        } else {

            ToastUtil.showToast(this, "没有可保存的图片!", Toast.LENGTH_SHORT);
        }
    }


    // 旧更新线框大小
    private void updateCropFrameSize() {
        if(is_hide_quan){
            Log.d("cianjjdjjdsjdsj:","线框印象只能");
            return;
        }

        String widthText = etWidth.getText().toString();
        String heightText = etHeight.getText().toString();

        if (widthText.isEmpty() || heightText.isEmpty()) {
            return;
        }

        try {
            int width = Integer.parseInt(widthText);
            int height = Integer.parseInt(heightText);

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cropFrame.getLayoutParams();
            params.width = width;
            params.height = height;
            params.leftMargin = (int) (lastX - (width - params.width) / 2f);
            params.topMargin = (int) (lastY - (height - params.height) / 2f);
            cropFrame.setLayoutParams(params);

            lastX = params.leftMargin;
            lastY = params.topMargin;
            cropFrame.setVisibility(View.VISIBLE); // 显示圆圈
            cropFrame.invalidate(); // 刷新绘制
        } catch (NumberFormatException e) {
            ToastUtil.showToast(this, "请输入有效的数字", Toast.LENGTH_SHORT);
        }
    }

    // 更新线框位置

    private void updateCropFramePosition() {
        if(is_hide_quan){
            Log.d("cianjjdjjdsjdsj:","线框印象只能");
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cropFrame.getLayoutParams();

        // 确保位置不会超出预览区域
        int x = Math.max(params.width/2, Math.min(seekBarX.getProgress(), previewWidth - params.width/2));
        int y = Math.max(params.height/2, Math.min(seekBarY.getProgress(), previewHeight - params.height/2));

        params.leftMargin = x - params.width / 2;
        params.topMargin = y - params.height / 2;
        cropFrame.setLayoutParams(params);
        cropFrame.setVisibility(View.VISIBLE); // 显示圆圈
        cropFrame.invalidate(); // 刷新绘制

        lastX = x;
        lastY = y;
    }

    // 重来功能
    private void reset(){
        // 恢复原图
        imageView.setImageBitmap(originalBitmap);

        centerImageAndCropFrame();

    }

}
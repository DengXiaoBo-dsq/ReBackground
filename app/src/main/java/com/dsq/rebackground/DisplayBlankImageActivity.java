package com.dsq.rebackground;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dsq.rebackground.utils.DrawView;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class DisplayBlankImageActivity extends AppCompatActivity {

    private Button toolButton,saveBtton,resBtn;
    private Bitmap imageBitmap;
    private DrawView drawView;  // 用于图像操作
    private int brushColor = Color.BLACK;
    private int brushSize = 5;  // 默认画笔大小
    private int eraseSize = 30,fontSize; // 默认橡皮擦大小
    private boolean isPainting = true; // 默认画画模式
    private boolean isTextmodel = false;
    private boolean isErear=false;
    private FrameLayout frameLayout=null;
    private String fontName="/storage/emulated/0/ReMoveBg-Config/fonts/font9.ttf";

    @SuppressLint("LongLogTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_blank_image);

        toolButton = findViewById(R.id.toolButton);
        saveBtton=findViewById(R.id.saveButton);
        saveBtton.setOnClickListener(v -> saveModifiedImage());
        resBtn=findViewById(R.id.res_btn);
        resBtn.setOnClickListener(v->reset());
        // 从 Intent 获取图片路径
        String imagePath = getIntent().getStringExtra("imagePath");
        if (imagePath != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            imageBitmap = BitmapFactory.decodeFile(imagePath, options); // 使用类级别的 imageBitmap
            Log.d("BitmapConfig", imageBitmap.getConfig().toString()); // 确保输出 ARGB_8888
        }


        // 设置绘画区域
        if (imageBitmap != null && !imageBitmap.isRecycled()) {
            frameLayout = findViewById(R.id.frameLayout);

            // 等待 FrameLayout 完成布局后调整图片大小
            frameLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // 移除监听器，避免重复调用
                    frameLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    // 初始化 DrawView 并添加到 FrameLayout
                    drawView = new DrawView(DisplayBlankImageActivity.this, imageBitmap);
                    frameLayout.addView(drawView);

                    // 调整图片大小以填满 FrameLayout
                    adjustImageViewSize(frameLayout, imageBitmap);
                }
            });
        } else {
            Log.e("DisplayBlankImageActivity", "Invalid Bitmap passed to DrawView");
        }

        // 工具按钮打开工具设置界面
        toolButton.setOnClickListener(v -> openToolSettingsActivity());
    }

    private void openToolSettingsActivity() {
        // 打开工具设置界面，配置画笔、文本、橡皮擦等工具
        Intent intent = new Intent(DisplayBlankImageActivity.this, ToolSettingsActivity.class);
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {


            // 从设置界面获取画笔颜色、大小和工具模式
            String colorValue = data.getStringExtra("brushColor");
            if (colorValue != null && (colorValue.matches("#[A-Fa-f0-9]{6}") || colorValue.matches("#[A-Fa-f0-9]{8}"))) {
                try {
                    brushColor = Color.parseColor(colorValue); // 解析颜色字符串
                } catch (IllegalArgumentException e) {
                    // 颜色值无效，设置为默认颜色（黑色）
                    brushColor = Color.BLACK;
                    ToastUtil.showToast(this, "颜色值无效，已设置为默认颜色（黑色）", Toast.LENGTH_SHORT);
                }
            } else {
                // 颜色值无效，设置为默认颜色（黑色）
                brushColor = Color.BLACK;
                ToastUtil.showToast(this, "颜色值无效，已设置为默认颜色（黑色）", Toast.LENGTH_SHORT);
            }

            // 从设置界面获取画笔颜色、大小和工具模式

            brushSize = data.getIntExtra("brushSize", 5);
            eraseSize = data.getIntExtra("eraseSize", 30);
            isPainting = data.getBooleanExtra("isPainting", true); // true 为画画模式，false 为橡皮擦模式
            isTextmodel=data.getBooleanExtra("isTextModel",false);
            fontSize=data.getIntExtra("textSize",12);
            fontName=data.getStringExtra("fontPath");
            isErear=data.getBooleanExtra("isErear",false);
            String fontPath = data.getStringExtra("fontPath"); // 获取字体路径

            // 打印用户选择的参数
            Log.d("ToolSettings", "========== 工具参数设置 ==========");
            Log.d("ToolSettings", "画笔颜色: #" + String.format("#%08X", 0xFFFFFFFF & brushColor));
            Log.d("ToolSettings", "画笔大小: " + brushSize + "px");
            Log.d("ToolSettings", "橡皮擦大小: " + eraseSize + "px");
            Log.d("ToolSettings", "当前模式: " + (isPainting ? "绘画模式" : "擦除模式"));

            Log.d("ToolSettings", "字体路径: " + fontName);
            Log.d("ToolSettings", "================================");

            // 字体路径检查
            if (fontName== null || fontPath.isEmpty()) {
                Log.e("ToolSettings", "字体路径为空，使用默认字体");
            }

           
            if (drawView != null) {

                if(isTextmodel){

                        drawView.setTextMode(true);
                        drawView.setBrushColor(brushColor); // 设置新文本颜色
                        drawView.setTextSize(fontSize);     // 设置新文本大小
                        drawView.loadTypefaceFromFile(fontName); // 设置新文本字体


                }else if(isErear){
                    drawView.setPaintingMode(false);
                    drawView.setTextMode(false);
                    drawView.setEraseSize(eraseSize);

                }else{
                    drawView.setPaintingMode(true);
                    drawView.setTextMode(false);
                    drawView.setBrushSize(brushSize);
                    drawView.setBrushColor(brushColor);

                }


            }
        }
    }

    private void saveModifiedImage() {
        if (drawView == null) return;

        // 获取合并后的最终图片
        Bitmap finalBitmap = drawView.getFinalBitmap();

        // 按原图尺寸保存
        if (imageBitmap != null && !imageBitmap.isRecycled()) {
            // 创建一个与原图尺寸相同的 Bitmap
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                    finalBitmap,
                    imageBitmap.getWidth(),
                    imageBitmap.getHeight(),
                    true
            );

            // 保存到文件
            String timestamp = String.valueOf(System.currentTimeMillis());
            File originalFile = new File("draw.png");
            String fileName = originalFile.getName();
            String newFileName = fileName.replace(".", "_modified_" + timestamp + ".");

            File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File outputFile = new File(outputDir, newFileName);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                ToastUtil.showToast(this, "图片已保存", Toast.LENGTH_SHORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            ToastUtil.showToast(this, "原图无效，无法保存", Toast.LENGTH_SHORT);
        }
    }

    // 自适应调整绘画区域的大小，确保图像按比例填充
    @SuppressLint("LongLogTag")
    private void adjustImageViewSize(FrameLayout frameLayout, Bitmap imageBitmap) {
        if (imageBitmap == null || imageBitmap.getWidth() == 0 || imageBitmap.getHeight() == 0) {
            Log.e("DisplayBlankImageActivity", "Invalid Bitmap dimensions");
            return;
        }

        // 获取 FrameLayout 的宽高
        int frameWidth = frameLayout.getWidth();
        int frameHeight = frameLayout.getHeight();

        // 计算宽高比
        float imageRatio = (float) imageBitmap.getWidth() / imageBitmap.getHeight();
        float frameRatio = (float) frameWidth / frameHeight;

        // 根据宽高比决定缩放方向
        int newWidth, newHeight;

        if (imageRatio > frameRatio) {
            // 图片宽度大于高度，按宽度缩放
            newWidth = frameWidth;
            newHeight = (int) (frameWidth / imageRatio);
        } else {
            // 图片高度大于或等于宽度，按高度缩放
            newHeight = frameHeight;
            newWidth = (int) (frameHeight * imageRatio);
        }

        // 如果图片的宽高比不符合预期，避免生成无效的 Bitmap
        if (newWidth <= 0 || newHeight <= 0) {
            Log.e("DisplayBlankImageActivity", "Invalid new dimensions: " + newWidth + "x" + newHeight);
            return;
        }

        // 创建缩放后的 Bitmap
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(imageBitmap, newWidth, newHeight, false);

// 确保缩放后的 Bitmap 使用 ARGB_8888 配置
        if (scaledBitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap argbBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true);
            scaledBitmap.recycle(); // 释放原始 Bitmap
            scaledBitmap = argbBitmap;
        }

// 更新绘制的图片
        drawView.setImageBitmap(scaledBitmap);

    }

    //重来按钮清除所有绘制和文本
    // 重来按钮清除所有绘制和文本
    private void reset() {
        if (drawView != null) {
            // 清除所有绘制和文本
            drawView.clearAll();

            // 重新设置原始图片
            drawView.setImageBitmap(imageBitmap);

            // 调整图片大小以填满 FrameLayout
            adjustImageViewSize(frameLayout, imageBitmap);
        }
    }
}
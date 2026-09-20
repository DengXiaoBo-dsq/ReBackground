package com.dsq.rebackground;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;

import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.dsq.rebackground.utils.BitmapCache;
import com.dsq.rebackground.utils.FileUtils;
import com.dsq.rebackground.utils.ToastUtil;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeInfoActivity extends AppCompatActivity {

    private TextView imageInfoText;
    private EditText widthInput, heightInput;
    private CheckBox keepAspectRatioCheckBox;
    private Spinner formatSpinner;
    private Button btnSave;
    private CheckBox btnchang_RGBA;
    private String imageKey,imagePath;
    private EditText input_RGBA;
    private String pic_fromat="moren";
    private int originalWidth, originalHeight;
    private boolean is_change_rgb=false;

    private boolean isProgrammaticChange = false; // 标志位，用于判断是否是程序触发的 setText

    private Bitmap oringBitmap;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.change_info);

        // 初始化控件
        imageInfoText = findViewById(R.id.image_info);
        widthInput = findViewById(R.id.width_input);
        heightInput = findViewById(R.id.height_input);
        keepAspectRatioCheckBox = findViewById(R.id.keep_aspect_ratio);
        formatSpinner = findViewById(R.id.format_spinner);
        btnSave = findViewById(R.id.btn_save);
        btnchang_RGBA=findViewById(R.id.btn_change_RGBA);
        input_RGBA=findViewById(R.id.input_rgba);

        // 默认保持宽高比勾选
        keepAspectRatioCheckBox.setChecked(true);
        btnchang_RGBA.setChecked(false);
        input_RGBA.setEnabled(false);
        btnchang_RGBA.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // isChecked 表示当前勾选状态
                if (isChecked) {
                    // 勾选时触发
                    pic_fromat=".png";
                    input_RGBA.setEnabled(true);
                } else {
                    // 取消勾选时触发

                    input_RGBA.setText("");
                    input_RGBA.setEnabled(false);
                }
            }
        });


        // 设置图片格式选项
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.image_formats,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(adapter);

        // 获取传递的 imageKey
        imageKey = getIntent().getStringExtra("imagekey");
        Log.d("文件路径qqqq：","ljiiis:"+imageKey);
        if (imageKey != null) {
            // 从缓存中获取图片和属性信息
            oringBitmap = BitmapCache.getInstance().getBitmap(imageKey);
            BitmapCache.ImageInfo imageInfo = BitmapCache.getInstance().getImageInfo(imageKey);
            imagePath = imageInfo.getPath();

            Log.d("文件路径：","ljiiis:"+imagePath);
        }

        // 获取图片信息并显示
        if (imagePath != null) {
            updateImageInfo(imagePath);
        } else {
            ToastUtil.showToast(this, "图片路径为空", Toast.LENGTH_SHORT);
            finish();
        }

        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> saveImage());

        // 输入框监听
        widthInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isProgrammaticChange && keepAspectRatioCheckBox.isChecked()) {
                    updateDimensions(true);  // 当宽度变化时更新高度
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        heightInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isProgrammaticChange && keepAspectRatioCheckBox.isChecked()) {
                    updateDimensions(false);  // 当高度变化时更新宽度
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateDimensions(boolean isWidthChanged) {
        if (!keepAspectRatioCheckBox.isChecked()) return;


        // 使用原始宽高
        int currentWidth = originalWidth;
        int currentHeight = originalHeight;

        if (isWidthChanged) {
            String widthText = widthInput.getText().toString();
            if (!widthText.isEmpty()) {
                int newWidth = Integer.parseInt(widthText);
                int newHeight = (int) (newWidth * (float) currentHeight / currentWidth);

                // 设置标志位，避免递归调用
                isProgrammaticChange = true;
                heightInput.setText(String.valueOf(newHeight));
                isProgrammaticChange = false;
            }
        } else {
            String heightText = heightInput.getText().toString();
            if (!heightText.isEmpty()) {
                int newHeight = Integer.parseInt(heightText);
                int newWidth = (int) (newHeight * (float) currentWidth / currentHeight);

                // 设置标志位，避免递归调用
                isProgrammaticChange = true;
                widthInput.setText(String.valueOf(newWidth));
                isProgrammaticChange = false;
            }
        }
    }

    private void updateImageInfo(String imagePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap != null) {
            originalWidth = bitmap.getWidth();
            originalHeight = bitmap.getHeight();
            String imageFormat = getImageFormat(imagePath);
            long fileSize = new File(imagePath).length();
            String fileSizeStr = formatFileSize(fileSize);

            String imageInfo = "路径: " + imagePath + "\n大小: " + fileSizeStr + "\n" +
                    "宽高: " + originalWidth + " x " + originalHeight + "\n格式: " + imageFormat;
            imageInfoText.setText(imageInfo);

            widthInput.setText(String.valueOf(originalWidth));
            heightInput.setText(String.valueOf(originalHeight));
        } else {
            ToastUtil.showToast(this, "加载图片失败", Toast.LENGTH_SHORT);
        }
    }


    private void saveImage() {
        String newWidthStr = widthInput.getText().toString();
        String newHeightStr = heightInput.getText().toString();

        is_change_rgb = btnchang_RGBA.isChecked();

        if (newWidthStr.isEmpty() || newHeightStr.isEmpty()) {
            ToastUtil.showToast(this, "宽度和高度不能为空", Toast.LENGTH_SHORT);
            return;
        }

        int newWidth = Integer.parseInt(newWidthStr);
        int newHeight = Integer.parseInt(newHeightStr);
        boolean keepAspectRatio = keepAspectRatioCheckBox.isChecked();
        String selectedFormat = "";

        // 如果保持宽高比，根据宽度或高度重新计算另一个维度
        if (keepAspectRatio) {
            float aspectRatio = (float) originalWidth / originalHeight;
            if (newWidth > 0) {
                newHeight = (int) (newWidth / aspectRatio);
            } else if (newHeight > 0) {
                newWidth = (int) (newHeight * aspectRatio);
            }
        }

        // 获取原始文件名并添加修改标注
        File originalFile = new File(imagePath);
        String originalFileName = originalFile.getName();
        String fileNameWithoutExtension = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String fileExtension = "";

        if (!pic_fromat.equals("moren")) {
            selectedFormat = pic_fromat;
            fileExtension = pic_fromat;
        } else {
            selectedFormat = formatSpinner.getSelectedItem().toString();
            fileExtension = selectedFormat.equals("JPEG") ? ".jpg" : selectedFormat.toLowerCase();
        }

        // 在原文件名后加上标注（例如 "_modified_时间戳"）
        String timestamp = String.valueOf(System.currentTimeMillis());
        String newFileName = fileNameWithoutExtension + "_modified_" + timestamp + fileExtension;

        // 创建输出目录（Environment.DIRECTORY_PICTURES）
        File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!outputDir.exists()) {
            outputDir.mkdirs(); // 如果目录不存在，则创建
        }

        // 创建输出文件
        File outputFile = new File(outputDir, newFileName);
        String save_path=outputFile.getAbsolutePath();
        // 将布尔值转换为字符串传递
        String changeRgbStr = String.valueOf(is_change_rgb);
        String RGBA="255";
        RGBA=input_RGBA.getText().toString();
        if(RGBA.equals("")){
            RGBA="255";
        }
        int a_value=Integer.parseInt(RGBA);
        if (a_value>=255){
            a_value=255;
        }
        if(a_value<=0){
            a_value=0;
        }

        Python py = Python.getInstance();
        PyObject pyObj = py.getModule("resizePic"); // 这里是你的 Python 文件名，不带 .py 后缀
        PyObject result = pyObj.callAttr("resizeIamge", imagePath,newWidth,newHeight,save_path,RGBA,changeRgbStr);
        Log.d("输出路径：",result.toString());

        if (result.toString().equals(save_path)){

            ToastUtil.showToast(this, "图片属性修改成功！已保存至"+save_path, Toast.LENGTH_SHORT);
        }else{
            ToastUtil.showToast(this, "图片修改失败！", Toast.LENGTH_LONG);
        }


    }



    /**
     * 将 Bitmap 保存为文件
     */
    private void saveBitmapToFile(Bitmap bitmap, File outputFile, String selectedFormat) {
        System.out.printf("保存格式:%s", selectedFormat.toUpperCase());
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            boolean success = false;

            switch (selectedFormat.toUpperCase()) {
                case ".JPEG":
                    success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    break;
                case ".PNG":
                    success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    break;
                case ".WEBP":
                    success = bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out);
                    break;
                case ".ICO":
                    System.out.printf("现在开始生成ICO图片");

                    break;
                default:
                    ToastUtil.showToast(ChangeInfoActivity.this, "不支持的格式: " + selectedFormat, Toast.LENGTH_LONG);
                    return;
            }

            if (success) {
                // 显示保存成功的 Toast（5秒自动关闭）
                Toast toast = Toast.makeText(ChangeInfoActivity.this,
                        "图片保存成功: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG);
                toast.show();
                new Handler().postDelayed(toast::cancel, 5000);
            } else {
                ToastUtil.showToast(ChangeInfoActivity.this, "图片保存失败", Toast.LENGTH_LONG);
            }
        } catch (IOException e) {
            ToastUtil.showToast(ChangeInfoActivity.this, "保存图片失败: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    /**
     * 生成ICO所需的多尺寸Bitmap
     */
    private List<Bitmap> generateIconBitmaps(Bitmap original) {
        List<Bitmap> bitmaps = new ArrayList<>();
        int[] sizes = {16, 32, 48, 64, 128};

        for (int size : sizes) {
            try {
                // 跳过比原始图大的尺寸（可选逻辑）
                if (size > original.getWidth() || size > original.getHeight()) {
                    continue;
                }
                Bitmap scaled = Bitmap.createScaledBitmap(original, size, size, true);
                if (scaled != null) {
                    bitmaps.add(scaled);
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        return bitmaps;
    }

    // 转换通道
    private Bitmap convertChannels(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        String RGBA=input_RGBA.getText().toString();
        int a_value=Integer.parseInt(RGBA);
        if (a_value>=255){
            a_value=255;
        }
        if(a_value<=0){
            a_value=0;
        }


        // 将 3 通道（RGB）转为 4 通道（RGBA）
        Bitmap rgbaBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                // 提取 RGB 值
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                // 添加不透明的 Alpha 通道（255）
                int rgbaPixel = (a_value << 24) | (r << 16) | (g << 8) | b;
                rgbaBitmap.setPixel(x, y, rgbaPixel);
            }
        }
        float a=(float)a_value/255;
        ToastUtil.showToast(this, "图片已转换为RGBA通道，设置透明度为:"+a, Toast.LENGTH_SHORT);
        return rgbaBitmap;

    }

    // 获取图片格式（通过文件扩展名）
    private String getImageFormat(String path) {
        String format = "未知格式";
        File file = new File(path);
        String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1).toUpperCase();
        if (extension.equals("JPG") || extension.equals("JPEG")) {
            format = "JPEG";
        } else if (extension.equals("PNG")) {
            format = "PNG";
        }
        return format;
    }

    // 格式化文件大小
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return size / 1024 + " KB";
        } else {
            return size / (1024 * 1024) + " MB";
        }
    }
}

package com.dsq.rebackground;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dsq.rebackground.utils.BitmapCache;
import com.dsq.rebackground.utils.FileUtils;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;

public class UniversalCropActivity extends AppCompatActivity {

    private ImageView imageView;
    private Button btnRectCrop, btnCircleCrop, btnFreeCrop, btnModifyProperties,btnCreateBlank,add_text;
    private TextView imageInfoText; // 新增的 TextView
    private String imagePath;
    private String imageKey;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_universal_crop);

        // 初始化控件
        imageView = findViewById(R.id.imageView);
        btnRectCrop = findViewById(R.id.btn_rect_crop);
        btnCircleCrop = findViewById(R.id.btn_circle_crop);
        btnFreeCrop = findViewById(R.id.btn_free_crop);
        btnModifyProperties = findViewById(R.id.btn_modify_attributes); // 获取“修改属性”按钮
        imageInfoText = findViewById(R.id.image_info); // 获取 TextView 控件
        btnCreateBlank=findViewById(R.id.btn_create_blank);
        add_text=findViewById(R.id.btn_add_text);

        // 获取传递的 imageKey
        imageKey = getIntent().getStringExtra("imageKey");

        if (imageKey != null) {
            // 从缓存中获取图片和属性信息
            // 从缓存获取 Bitmap 和 ImageInfo
            Bitmap cachedBitmap = BitmapCache.getInstance().getBitmap(imageKey);
            BitmapCache.ImageInfo imageInfo = BitmapCache.getInstance().getImageInfo(imageKey);


            if (cachedBitmap != null) {
                imagePath=imageInfo.getPath();
                imageView.setImageBitmap(cachedBitmap);

                // 显示图片属性信息
                String imageInfoTextStr = "路径: " + imageInfo.getPath() + "\n" +
                        "宽高: " + imageInfo.getWidth() + " x " + imageInfo.getHeight() + "\n" +
                        "格式: " + imageInfo.getFormat() + "\n" +
                        "大小: " + formatFileSize(imageInfo.getFileSize());
                imageInfoText.setText(imageInfoTextStr);
            } else {
                ToastUtil.showToast(this, "缓存读取失败！", Toast.LENGTH_SHORT);
                finish();
            }
        } else {
            ToastUtil.showToast(this, "没有找到图片！", Toast.LENGTH_SHORT);
            finish();
        }


        // 设置按钮点击事件
        btnRectCrop.setOnClickListener(v -> openRectCropActivity());
        btnCircleCrop.setOnClickListener(v -> openCircleCropActivity());
        btnFreeCrop.setOnClickListener(v -> openFreeCropActivity());
        btnModifyProperties.setOnClickListener(v -> openModifyPropertiesActivity()); // 点击“修改属性”按钮跳转
        btnCreateBlank.setOnClickListener(v -> openCreateImageSettingsActivity());
        add_text.setOnClickListener(v->openAddTextActivity());
    }

    //涂鸦
    private void openCreateImageSettingsActivity() {

        // 跳转到涂鸦界面，并传递文件路径
        Intent intent = new Intent(UniversalCropActivity.this, DisplayBlankImageActivity.class);
        intent.putExtra("imagePath", imagePath); // 传递文件路径
        startActivity(intent);

    }
    private void openAddTextActivity(){
        Intent intent = new Intent(UniversalCropActivity.this,AddTextActivity.class);
        intent.putExtra("imagekey",imageKey );
        startActivity(intent);
    }
    //四边
    private void openRectCropActivity() {
        Intent intent = new Intent(UniversalCropActivity.this, RectCropActivity.class);
        intent.putExtra("imagekey",imageKey );
        startActivity(intent);
    }

    //圆形
    private void openCircleCropActivity() {
        Intent intent = new Intent(UniversalCropActivity.this, CircleCropActivity.class);
        intent.putExtra("imagekey",imageKey);
        startActivity(intent);
    }

    //自由
    private void openFreeCropActivity() {
        Intent intent = new Intent(UniversalCropActivity.this, FreeCropActivity.class);
        intent.putExtra("imagekey",imageKey);
        startActivity(intent);
    }

    // 点击“修改属性”按钮跳转到修改属性界面
    private void openModifyPropertiesActivity() {
        Intent intent = new Intent(UniversalCropActivity.this, ChangeInfoActivity.class);
        intent.putExtra("imagekey",imageKey); // 传递图片路径到修改属性界面
        startActivity(intent);
    }



    // 格式化文件大小，转换成 KB, MB, GB 等单位
    private String formatFileSize(long size) {
        String formattedSize;
        if (size < 1024) {
            formattedSize = size + " B";
        } else if (size < 1048576) {
            formattedSize = size / 1024 + " KB";
        } else if (size < 1073741824) {
            formattedSize = size / 1048576 + " MB";
        } else {
            formattedSize = size / 1073741824 + " GB";
        }
        return formattedSize;
    }
}


//===========================================================================
//============================================================================
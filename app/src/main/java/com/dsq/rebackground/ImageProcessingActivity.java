package com.dsq.rebackground;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dsq.rebackground.api.RemoveBgApi;
import com.dsq.rebackground.utils.ToastUtil;

public class ImageProcessingActivity extends AppCompatActivity {

    private ImageView imageView;
    private Button btnRemoveBg, btnChangeBg;
    private Bitmap imageToProcess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_processing);

        imageView = findViewById(R.id.imageView);
        btnRemoveBg = findViewById(R.id.btn_remove_bg);
        btnChangeBg = findViewById(R.id.btn_change_bg);

        // 假设imageToProcess已经通过Intent传递
        imageToProcess = getIntent().getParcelableExtra("image");

        if (imageToProcess != null) {
            imageView.setImageBitmap(imageToProcess);
        }

        btnRemoveBg.setOnClickListener(v -> removeBackground());
        btnChangeBg.setOnClickListener(v -> changeBackground());
    }

    private void removeBackground() {
        if (imageToProcess != null) {
            // 使用 RemoveBgApi 去除背景，传递当前Activity作为第三个参数
            RemoveBgApi.removeBackground(imageToProcess, new RemoveBgApi.RemoveBgCallback() {
                @Override
                public void onSuccess(Bitmap result) {
                    imageView.setImageBitmap(result);
                    imageToProcess = result; // 更新处理后的图片
                }

                @Override
                public void onError() {
                    ToastUtil.showToast(ImageProcessingActivity.this, "Error removing background", Toast.LENGTH_SHORT);
                }
            }, this); // 传递当前Activity
        }
    }

    private void changeBackground() {
        // 打开背景选择界面
        startActivity(new Intent(this, BackgroundPickerActivity.class));
    }
}

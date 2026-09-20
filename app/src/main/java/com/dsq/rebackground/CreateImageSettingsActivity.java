package com.dsq.rebackground;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CreateImageSettingsActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final int REQUEST_IMPORT_DRAFT = 1008;  // 新增
    private static final int REQUEST_PERMISSION_READ = 1002;
    private static final int REQUEST_COLOR_PICKER = 1003;

    private EditText widthInput, heightInput;
    private View colorPreview;
    private TextView colorHexText;
    private Spinner textureSpinner;
    private Button createButton;
    private ImageButton btnPickImage;
    private Button btnImportDraft;  // 新增
    private Button[] presetButtons;

    private int currentColor = Color.WHITE;
    private Uri pickedImageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_new_pic);

        widthInput = findViewById(R.id.widthInput);
        heightInput = findViewById(R.id.heightInput);
        colorPreview = findViewById(R.id.colorPreview);
        colorHexText = findViewById(R.id.colorHexText);
        textureSpinner = findViewById(R.id.textureSpinner);
        createButton = findViewById(R.id.createButton);
        btnPickImage = findViewById(R.id.btnPickImage);
        btnImportDraft = findViewById(R.id.btnImportDraft);  // 新增

        // 预设尺寸按钮
        presetButtons = new Button[]{
                findViewById(R.id.preset_3_4),
                findViewById(R.id.preset_1_1),
                findViewById(R.id.preset_16_9),
                findViewById(R.id.preset_4_3),
                findViewById(R.id.preset_screen),
                findViewById(R.id.preset_a4),
                findViewById(R.id.preset_a5),
                findViewById(R.id.preset_custom)
        };

        for (Button btn : presetButtons) {
            btn.setOnClickListener(v -> onPresetClick(btn));
        }

        colorPreview.setOnClickListener(v -> startColorPicker());
        colorHexText.setOnClickListener(v -> startColorPicker());

        btnPickImage.setOnClickListener(v -> pickImageFromGallery());

        // 新增：导入草稿
        btnImportDraft.setOnClickListener(v -> importDraft());

        createButton.setOnClickListener(v -> createCanvas());
    }

    // 新增：导入草稿方法
    private void importDraft() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQUEST_PERMISSION_READ);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_READ);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/*"});
        startActivityForResult(intent, REQUEST_IMPORT_DRAFT);
    }

    private void onPresetClick(Button btn) {
        for (Button b : presetButtons) {
            b.setSelected(false);
        }
        btn.setSelected(true);

        String text = btn.getText().toString();
        if (text.contains("自定义")) {
            widthInput.setText("");
            heightInput.setText("");
            return;
        }

        String[] lines = text.split("\n");
        if (lines.length >= 2) {
            String sizeStr = lines[1];
            String[] wh = sizeStr.split("×");
            if (wh.length == 2) {
                widthInput.setText(wh[0].trim());
                heightInput.setText(wh[1].trim());
            }
        }
    }

    private void startColorPicker() {
        Intent intent = new Intent(this, color_picker_view.class);
        startActivityForResult(intent, REQUEST_COLOR_PICKER);
    }

    private void pickImageFromGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQUEST_PERMISSION_READ);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_READ);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_COLOR_PICKER && resultCode == RESULT_OK && data != null) {
            String hex = data.getStringExtra("bgColor");
            if (hex != null) {
                try {
                    int color = Color.parseColor(hex);
                    currentColor = color;
                    colorPreview.setBackgroundColor(color);
                    colorHexText.setText(hex);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            pickedImageUri = data.getData();
            if (pickedImageUri != null) {
                ToastUtil.showToast(this, "图片已选择，点击“创建画布”开始绘画");
            }
        } else if (requestCode == REQUEST_IMPORT_DRAFT && resultCode == RESULT_OK && data != null) {
            Uri draftUri = data.getData();
            if (draftUri != null) {
                // 直接启动 PaintActivity 并传递草稿 Uri
                Intent intent = new Intent(this, PaintActivity.class);
                intent.putExtra("draftUri", draftUri.toString());
                startActivity(intent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_READ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 重新执行导入操作（但不知道是图片还是草稿，简单处理：弹窗让用户选择）
                showImportTypeChooser();
            } else {
                ToastUtil.showToast(this, "需要存储权限才能导入文件");
            }
        }
    }

    private void showImportTypeChooser() {
        // 权限授予后让用户选择导入类型，简单处理：直接调用导入草稿
        importDraft();
    }

    private void createCanvas() {
        // 如果用户选择了图片，则直接启动绘画界面
        if (pickedImageUri != null) {
            Intent intent = new Intent(this, PaintActivity.class);
            intent.putExtra("imageUri", pickedImageUri.toString());
            intent.putExtra("bgColor", currentColor);
            startActivity(intent);
            return;
        }

        // 否则使用尺寸和颜色创建新画布
        String widthStr = widthInput.getText().toString().trim();
        String heightStr = heightInput.getText().toString().trim();

        if (widthStr.isEmpty() || heightStr.isEmpty()) {
            ToastUtil.showToast(this, "请输入或选择画布尺寸");
            return;
        }

        int width, height;
        try {
            width = Integer.parseInt(widthStr);
            height = Integer.parseInt(heightStr);
        } catch (NumberFormatException e) {
            ToastUtil.showToast(this, "请输入有效的数字");
            return;
        }

        if (width <= 0 || height <= 0) {
            ToastUtil.showToast(this, "尺寸必须大于0");
            return;
        }

        int textureIndex = textureSpinner.getSelectedItemPosition();

        Intent intent = new Intent(this, PaintActivity.class);
        intent.putExtra("canvasWidth", width);
        intent.putExtra("canvasHeight", height);
        intent.putExtra("bgColor", currentColor);
        intent.putExtra("textureIndex", textureIndex);
        startActivity(intent);
    }
}
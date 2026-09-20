package com.dsq.rebackground;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ToolSettingsActivity extends AppCompatActivity {

    private CheckBox paintCheckBox, textCheckBox, eraseCheckBox;
    private SeekBar brushSizeSeekBar, textSizeSeekBar, eraseSizeSeekBar;
    private TextView brushSizeText, textSizeText, eraseSizeText;
    private Spinner fontStyleSpinner;
    private Button confirmButton;
    private View currentColorView;
    private GridLayout commonColorsGridLayout;
    private EditText colorInput;
    private String fontPath,iserror;
    private int currentColor;  // 记录当前选中的颜色

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool_settings);

        // 获取控件
        paintCheckBox = findViewById(R.id.paintCheckBox);
        textCheckBox = findViewById(R.id.textCheckBox);
        eraseCheckBox = findViewById(R.id.eraseCheckBox);
        brushSizeSeekBar = findViewById(R.id.brushSizeSeekBar);
        textSizeSeekBar = findViewById(R.id.textSizeSeekBar);
        eraseSizeSeekBar = findViewById(R.id.eraseSizeSeekBar);
        brushSizeText = findViewById(R.id.brushSizeText);
        textSizeText = findViewById(R.id.textSizeText);
        eraseSizeText = findViewById(R.id.eraseSizeText);
        fontStyleSpinner = findViewById(R.id.fontStyleSpinner);
        confirmButton = findViewById(R.id.confirmButton);
        currentColorView = findViewById(R.id.currentColorView);
        commonColorsGridLayout = findViewById(R.id.commonColorsGridLayout);
        colorInput = findViewById(R.id.colorInput);

        // 设置初始颜色
        // 初始化控件
        currentColorView = findViewById(R.id.currentColorView);

        // 设置初始颜色
        currentColor = 0xFF000000;  // 默认颜色为黑色
        currentColorView.setBackgroundColor(currentColor);

        // 监听输入框的文本变化
        colorInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要处理
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 不需要处理
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 输入框内容变化后，更新当前颜色
                updateCurrentColor();
            }
        });
        // 初始化工具勾选框的状态
        paintCheckBox.setChecked(false);
        textCheckBox.setChecked(false);
        eraseCheckBox.setChecked(false);

        // 画笔模式选中事件
        paintCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 如果画笔模式选中，取消橡皮擦模式
                eraseCheckBox.setChecked(false);
                textCheckBox.setChecked(false);
            }
        });

        loadFonts();
// 橡皮擦模式选中事件
        eraseCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 如果橡皮擦模式选中，取消画笔模式
                paintCheckBox.setChecked(false);
                textCheckBox.setChecked(false);
            }
        });
        textCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 如果橡皮擦模式选中，取消画笔模式
                paintCheckBox.setChecked(false);
                eraseCheckBox.setChecked(false);
            }
        });

        // 更新颜色选择
        currentColorView.setOnClickListener(v -> updateCurrentColor());
        // 为常用颜色方块设置点击事件
        for (int i = 0; i < commonColorsGridLayout.getChildCount(); i++) {
            final View colorView = commonColorsGridLayout.getChildAt(i);
            colorView.setOnClickListener(v -> updateCurrentColor(colorView));
        }

        // 设置SeekBar监听器
        brushSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brushSizeText.setText("Brush Size: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        textSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textSizeText.setText("Text Size: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        eraseSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                eraseSizeText.setText("Erase Size: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        // 获取文本相关设置（字体，大小，颜色）
        fontStyleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                String selectedFont = parentView.getItemAtPosition(position).toString();
                // 根据选择加载字体
                fontPath = "/storage/emulated/0/ReMoveBg-Config/fonts/" + selectedFont;

                Log.d("ToolSettings", "字体路径："+fontPath);
                // 将字体路径传递到绘画界面

            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 未选择任何字体时
            }
        });

        confirmButton.setOnClickListener(v -> {
            // 获取设置的值并返回到主界面
            int brushSize = brushSizeSeekBar.getProgress();
            int textSize = textSizeSeekBar.getProgress();
            int eraseSize = eraseSizeSeekBar.getProgress();

            // 判断当前模式
            boolean isErear = eraseCheckBox.isChecked();
            boolean isPainting = paintCheckBox.isChecked(); // 如果画笔模式选中，则为 true
            boolean isTextModel = textCheckBox.isChecked();
            if (eraseCheckBox.isChecked()) {
                isPainting = false; // 如果橡皮擦模式选中，则为 false
            }

            // 获取颜色字符串
            String colorValue = colorInput.getText().toString().trim();
            // 检查颜色值是否有效
            if (!colorValue.matches("#[A-Fa-f0-9]{6}") && !colorValue.matches("#[A-Fa-f0-9]{8}")) {
                // 颜色值无效，设置为默认颜色（黑色）
                colorValue = "#FF000000"; // 黑色
                ToastUtil.showToast(this, "颜色值无效，已设置为默认颜色（黑色）", Toast.LENGTH_SHORT);
            }

            // 将设置值传回主界面
            Intent returnIntent = new Intent();
            returnIntent.putExtra("brushColor", colorValue); // 传递颜色字符串
            returnIntent.putExtra("brushSize", brushSize);
            returnIntent.putExtra("textSize", textSize);
            returnIntent.putExtra("eraseSize", eraseSize);
            returnIntent.putExtra("isPainting", isPainting); // 传递模式参数
            returnIntent.putExtra("isTextModel", isTextModel);
            returnIntent.putExtra("isErear", isErear);
            returnIntent.putExtra("fontPath", fontPath);

            setResult(RESULT_OK, returnIntent);
            finish();
        });


    }
    private void loadFonts() {
        File fontDirectory = new File(getFilesDir(), "fonts");
        if (!fontDirectory.exists()) {
            fontDirectory.mkdirs();
        }

        List<String> fontList = new ArrayList<>();

        // 检查 res/font/fonts 目录
        try {
            AssetManager assets = getAssets();
            String[] fonts = assets.list("font/fonts");
            if (fonts != null) {
                for (String font : fonts) {
                    fontList.add(font);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 检查 /storage/emulated/0/ReMoveBg-Config/fonts 目录
        File externalFontDirectory = new File("/storage/emulated/0/ReMoveBg-Config/fonts");
        if (externalFontDirectory.exists() && externalFontDirectory.isDirectory()) {
            File[] files = externalFontDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".ttf") || file.getName().endsWith(".otf")) {
                        fontList.add(file.getName());
                    }
                }
            }
        }

        // 如果没有字体文件，提示用户下载
        if (fontList.isEmpty()) {
            ToastUtil.showToast(this, "请下载字体并放到字体目录中", Toast.LENGTH_SHORT);
        }

        // 将字体文件添加到 Spinner 中
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fontList);
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fontStyleSpinner.setAdapter(fontAdapter);
    }


    // 更新当前颜色的方法

    private void updateCurrentColor() {
        String colorValue = colorInput.getText().toString().trim();

        // 支持 6 位（RGB）或 8 位（ARGB）颜色值
        if (colorValue.matches("#[A-Fa-f0-9]{6}") || colorValue.matches("#[A-Fa-f0-9]{8}")) {
            try {
                currentColor = Color.parseColor(colorValue); // 更新 currentColor
                currentColorView.setBackgroundColor(currentColor);
            } catch (IllegalArgumentException e) {
                // 颜色值无效
                iserror="error";

            }
        } else {
            iserror="error";

        }
    }


    private void updateCurrentColor(View colorView) {
        int color = ((ColorDrawable) colorView.getBackground()).getColor();
        currentColor = ((ColorDrawable) colorView.getBackground()).getColor();
        currentColorView.setBackgroundColor(color);
        colorInput.setText(String.format("#%06X", (0xFFFFFF & color))); // 将颜色值显示在输入框中
    }
}


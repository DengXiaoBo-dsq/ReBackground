package com.dsq.rebackground;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dsq.rebackground.utils.ToastUtil;
import com.madrapps.pikolo.HSLColorPicker;
import com.madrapps.pikolo.RGBColorPicker;
import com.madrapps.pikolo.listeners.SimpleColorSelectionListener;

import java.util.ArrayList;
import java.util.List;

public class color_picker_view extends AppCompatActivity {

    private static final String TAG = "ColorPicker";
    private static final int REQUEST_IMAGE_PICKER = 1001;      // 用于图片取色 Activity 返回颜色
    private static final int REQUEST_FAVORITE_GROUPS = 1002;
    private static final int REQUEST_PICK_IMAGE = 1003;        // 选择图片请求码
    private static final int REQUEST_PERMISSION_READ = 1004;

    // 色盘实例
    private HSLColorPicker colorPickerStyle1;
    private HSLColorPicker colorPickerOuter;   // 样式2外层（H）
    private HSLColorPicker colorPickerInner;   // 样式2内层（S/V）
    private RGBColorPicker colorPickerStyle3;
    private FrameLayout colorPickerStyle2Container;

    // 预览控件
    private View colorPreview;          // 色盘中心的预览圆
    private View currentColorPreview;   // 独立的预览色块

    // HSV 数值
    private TextView tvHue, tvSaturation, tvValue;

    // 透明度
    private SeekBar alphaSeekBar;
    private TextView alphaText;

    // HEX/RGB 输入
    private EditText etHex, etR, etG, etB;

    // 按钮
    private Button btnSwitchStyle;
    private Button btnAddFavorite;
    private Button btnCancel;
    private Button btnConfirm;
    private ImageButton btnGallery;
    private ImageButton btnMoreFavorites;

    // 收藏相关
    private LinearLayout favoritesContainer;
    private HorizontalScrollView favoritesScrollView;
    private TextView tvNoFavorites;

    private int currentColor = Color.WHITE;
    private boolean isUpdatingUI = false;
    private boolean isSyncing = false;
    private int currentStyle = 1; // 1,2,3

    // ============================================================
    // [MOD 2026-09-10] 输入框修复：跟踪当前正在被用户编辑的字段
    // 目的：updateUI 时跳过该字段，不重写其内容，避免吞输入
    // ============================================================
    private EditText currentEditingField = null;
    // ============================================================

    // 收藏管理器
    private ColorManager colorManager;

    // 输入框背景颜色（自适应）
    private static final int DEFAULT_BG_DARK = 0xDD333333;
    private static final int DEFAULT_BG_LIGHT = 0xDDEEEEEE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_picker_official);

        Log.d(TAG, "=== onCreate ===");

        colorManager = ColorManager.getInstance(this);

        // 初始化控件
        colorPickerStyle1 = findViewById(R.id.colorPickerStyle1);
        colorPickerOuter = findViewById(R.id.colorPickerOuter);
        colorPickerInner = findViewById(R.id.colorPickerInner);
        colorPickerStyle3 = findViewById(R.id.colorPickerStyle3);
        colorPickerStyle2Container = findViewById(R.id.colorPickerStyle2);

        colorPreview = findViewById(R.id.colorPreview);
        currentColorPreview = findViewById(R.id.currentColorPreview);

        tvHue = findViewById(R.id.tvHue);
        tvSaturation = findViewById(R.id.tvSaturation);
        tvValue = findViewById(R.id.tvValue);

        alphaSeekBar = findViewById(R.id.alphaSeekBar);
        alphaText = findViewById(R.id.alphaText);

        etHex = findViewById(R.id.etHex);
        etR = findViewById(R.id.etR);
        etG = findViewById(R.id.etG);
        etB = findViewById(R.id.etB);

        btnSwitchStyle = findViewById(R.id.btnSwitchStyle);
        btnAddFavorite = findViewById(R.id.btnAddFavorite);
        btnCancel = findViewById(R.id.btnCancel);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnGallery = findViewById(R.id.btnGallery);
        btnMoreFavorites = findViewById(R.id.btnMoreFavorites);

        favoritesContainer = findViewById(R.id.favoritesContainer);
        favoritesScrollView = findViewById(R.id.favoritesScrollView);
        tvNoFavorites = findViewById(R.id.tvNoFavorites);

        alphaSeekBar.setMax(255);

        // ========== 设置各样式监听器 ==========

        // 样式1
        colorPickerStyle1.setColorSelectionListener(new SimpleColorSelectionListener() {
            @Override
            public void onColorSelected(int color) {
                if (isUpdatingUI || isSyncing) return;
                int alpha = alphaSeekBar.getProgress();
                int finalColor = (alpha << 24) | (color & 0x00FFFFFF);
                currentColor = finalColor;
                updateUI();
            }
        });

        // 样式2外层（H）
        colorPickerOuter.setColorSelectionListener(new SimpleColorSelectionListener() {
            @Override
            public void onColorSelected(int color) {
                if (isUpdatingUI || isSyncing) return;
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                float newH = hsv[0];
                float[] curHsv = new float[3];
                Color.colorToHSV(currentColor, curHsv);
                float newS = curHsv[1];
                float newV = curHsv[2];
                int rgb = Color.HSVToColor(new float[]{newH, newS, newV});
                int alpha = alphaSeekBar.getProgress();
                int finalColor = (alpha << 24) | rgb;
                currentColor = finalColor;

                isSyncing = true;
                colorPickerInner.setColor(rgb);
                colorPickerOuter.setColor(rgb);
                isSyncing = false;
                updateUI();
            }
        });

        // 样式2内层（S/V）
        colorPickerInner.setColorSelectionListener(new SimpleColorSelectionListener() {
            @Override
            public void onColorSelected(int color) {
                if (isUpdatingUI || isSyncing) return;
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                float newS = hsv[1];
                float newV = hsv[2];
                float[] curHsv = new float[3];
                Color.colorToHSV(currentColor, curHsv);
                float newH = curHsv[0];
                int rgb = Color.HSVToColor(new float[]{newH, newS, newV});
                int alpha = alphaSeekBar.getProgress();
                int finalColor = (alpha << 24) | rgb;
                currentColor = finalColor;

                isSyncing = true;
                colorPickerOuter.setColor(rgb);
                colorPickerInner.setColor(rgb);
                isSyncing = false;
                updateUI();
            }
        });

        // 样式3
        colorPickerStyle3.setColorSelectionListener(new SimpleColorSelectionListener() {
            @Override
            public void onColorSelected(int color) {
                if (isUpdatingUI || isSyncing) return;
                int alpha = alphaSeekBar.getProgress();
                int finalColor = (alpha << 24) | (color & 0x00FFFFFF);
                currentColor = finalColor;
                updateUI();
            }
        });

        // ========== 透明度滑块 ==========
        alphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) alphaText.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isUpdatingUI) return;
                int alpha = seekBar.getProgress();
                int rgb = currentColor & 0x00FFFFFF;
                currentColor = (alpha << 24) | rgb;
                updateUI();
            }
        });

        // ========== HEX/RGB 输入 ==========
        // [MOD 2026-09-10] 用 setupColorInputs() 替代原 TextWatcher 块
        // 修复：无法输入/删除、输入被吞、非法字符、越界值
        setupColorInputs();

        // ========== 样式切换按钮 ==========
        btnSwitchStyle.setOnClickListener(v -> {
            currentStyle = currentStyle % 3 + 1;
            switchToStyle(currentStyle);
            syncColorToActivePicker(currentColor & 0x00FFFFFF);
            updateUI();
        });

        // ========== 收藏按钮 ==========
        btnAddFavorite.setOnClickListener(v -> showAddFavoriteDialog());

        // ========== 图片取色按钮（优化后：直接弹出选择图片对话框） ==========
        btnGallery.setOnClickListener(v -> {
            if (!checkStoragePermission()) {
                requestStoragePermission();
                return;
            }
            showImageSourceDialog();
        });

        // ========== 更多收藏夹按钮 ==========
        btnMoreFavorites.setOnClickListener(v -> {
            Intent intent = new Intent(this, FavoriteGroupsActivity.class);
            startActivityForResult(intent, REQUEST_FAVORITE_GROUPS);
        });

        // ========== 取消 / 确定 ==========
        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnConfirm.setOnClickListener(v -> {
            Intent data = new Intent();
            String hex = String.format("#%08X", currentColor);
            data.putExtra("bgColor", hex);
            setResult(RESULT_OK, data);
            finish();
        });

        // ========== 初始颜色（修复：为所有色盘设置初始颜色） ==========
        int initColor = getIntent().getIntExtra("defaultColor", Color.parseColor("#333333"));
        currentColor = initColor;
        int alpha = (initColor >> 24) & 0xFF;
        int rgb = initColor & 0x00FFFFFF;
        alphaSeekBar.setProgress(alpha);
        alphaText.setText(String.valueOf(alpha));

        switchToStyle(1); // 默认显示样式1

        // 延迟到布局完全测量后，为所有色盘实例设置颜色
        colorPickerStyle1.post(() -> {
            isSyncing = true;
            // 样式1
            colorPickerStyle1.setColor(rgb);
            // 样式2 内外层
            colorPickerOuter.setColor(rgb);
            colorPickerInner.setColor(rgb);
            // 样式3
            colorPickerStyle3.setColor(rgb);
            isSyncing = false;
            updateUI();
        });

        // 加载收藏
        renderFavorites();

        Log.d(TAG, "=== onCreate 结束 ===");
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderFavorites(); // 从收藏夹返回后刷新
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 处理从图片取色界面返回的颜色
        if (requestCode == REQUEST_IMAGE_PICKER && resultCode == RESULT_OK && data != null) {
            String hex = data.getStringExtra("selectedColorHex");
            if (hex != null) {
                try {
                    int color = Color.parseColor(hex);
                    currentColor = color;
                    int alpha = (color >> 24) & 0xFF;
                    int rgb = color & 0x00FFFFFF;
                    alphaSeekBar.setProgress(alpha);
                    alphaText.setText(String.valueOf(alpha));
                    syncColorToActivePicker(rgb);
                    updateUI();
                } catch (Exception e) {
                    Log.e(TAG, "解析返回颜色异常", e);
                }
            }
            return;
        }

        // 处理从相册/文件管理器选择图片的结果
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                // 启动图片取色界面，传递图片 URI
                Intent intent = new Intent(this, ImageColorPickerActivity.class);
                intent.setData(imageUri);  // 使用 setData 传递 URI
                // 也可用 putExtra，但 setData 更标准
                startActivityForResult(intent, REQUEST_IMAGE_PICKER);
            } else {
                ToastUtil.showToast(this, "未获取到图片");
            }
            return;
        }

        // 处理从收藏夹返回的颜色（FavoriteGroupsActivity 返回 selectedColorHex）
        if (requestCode == REQUEST_FAVORITE_GROUPS && resultCode == RESULT_OK && data != null) {
            String hex = data.getStringExtra("selectedColorHex");
            if (hex != null) {
                try {
                    int color = Color.parseColor(hex);
                    currentColor = color;
                    int alpha = (color >> 24) & 0xFF;
                    int rgb = color & 0x00FFFFFF;
                    alphaSeekBar.setProgress(alpha);
                    alphaText.setText(String.valueOf(alpha));
                    syncColorToActivePicker(rgb);
                    updateUI();
                } catch (Exception e) {
                    Log.e(TAG, "解析收藏返回颜色异常", e);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_READ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showImageSourceDialog();
            } else {
                ToastUtil.showToast(this, "需要存储权限才能选择图片");
            }
        }
    }

    // ===================== 权限检查 =====================
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_PERMISSION_READ);
    }

    private void showImageSourceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogThemeDark);
        builder.setTitle("选择图片来源");
        String[] options = {"从相册选择", "从文件管理器选择"};
        builder.setItems(options, (dialog, which) -> {
            Intent intent = new Intent();
            if (which == 0) {
                intent.setAction(Intent.ACTION_PICK);
                intent.setType("image/*");
            } else {
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
            }
            startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_PICK_IMAGE);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ===================== 样式切换逻辑 =====================
    private void switchToStyle(int style) {
        colorPickerStyle1.setVisibility(View.GONE);
        colorPickerStyle2Container.setVisibility(View.GONE);
        colorPickerStyle3.setVisibility(View.GONE);

        switch (style) {
            case 1:
                colorPickerStyle1.setVisibility(View.VISIBLE);
                break;
            case 2:
                colorPickerStyle2Container.setVisibility(View.VISIBLE);
                break;
            case 3:
                colorPickerStyle3.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void syncColorToActivePicker(int rgb) {
        isSyncing = true;
        switch (currentStyle) {
            case 1:
                colorPickerStyle1.setColor(rgb);
                break;
            case 2:
                colorPickerOuter.setColor(rgb);
                colorPickerInner.setColor(rgb);
                break;
            case 3:
                colorPickerStyle3.setColor(rgb);
                break;
        }
        isSyncing = false;
    }

    // ============================================================
    // [MOD 2026-09-10] 颜色输入框初始化与监听
    // 设计目标：
    //   1. InputFilter 硬过滤非法字符（非数字、非 hex、越界）
    //   2. 编辑期间跳过 updateUI 重写正在编辑的字段 → 不吞输入
    //   3. 允许空字符串（编辑中间态）
    //   4. 失焦时校正非法/空值
    // ============================================================
    private void setupColorInputs() {
        // ---------- HEX: 只允许 [0-9A-Fa-f] 和开头一个 # ----------
        etHex.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etHex.setFilters(new InputFilter[]{
                (source, start, end, dest, dstart, dend) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        char c = source.charAt(i);
                        if (Character.digit(c, 16) >= 0) {
                            sb.append(c);
                        } else if (c == '#' && dstart == 0) {
                            sb.append(c);
                        }
                    }
                    String newContent = dest.subSequence(0, dstart).toString()
                            + sb + dest.subSequence(dend, dest.length()).toString();
                    String stripped = newContent.startsWith("#")
                            ? newContent.substring(1) : newContent;
                    if (stripped.length() > 8) return "";  // 超过 8 位拒绝
                    return sb.toString();
                }
        });

        // ---------- RGB: 只允许数字，最多 3 位，值 ≤ 255 ----------
        InputFilter digitFilter = (source, start, end, dest, dstart, dend) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                }
            }
            String newContent = dest.subSequence(0, dstart).toString()
                    + sb + dest.subSequence(dend, dest.length()).toString();
            if (newContent.isEmpty()) return sb.toString();
            if (newContent.length() > 3) return "";  // 超过 3 位拒绝
            try {
                int v = Integer.parseInt(newContent);
                if (v > 255) return "";  // 超过 255 拒绝
            } catch (NumberFormatException e) {
                return "";
            }
            return sb.toString();
        };
        etR.setFilters(new InputFilter[]{digitFilter});
        etG.setFilters(new InputFilter[]{digitFilter});
        etB.setFilters(new InputFilter[]{digitFilter});

        // ---------- 焦点监听：跟踪正在编辑的字段，失焦时校正 ----------
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            EditText et = (EditText) v;
            if (hasFocus) {
                currentEditingField = et;
            } else {
                if (currentEditingField == et) {
                    currentEditingField = null;
                    normalizeField(et);  // 失焦时把空/非法值恢复
                }
            }
        };
        etHex.setOnFocusChangeListener(focusListener);
        etR.setOnFocusChangeListener(focusListener);
        etG.setOnFocusChangeListener(focusListener);
        etB.setOnFocusChangeListener(focusListener);

        // 长按全选，方便替换
        etHex.setOnLongClickListener(v -> { etHex.selectAll(); return true; });
        etR.setOnLongClickListener(v -> { etR.selectAll(); return true; });
        etG.setOnLongClickListener(v -> { etG.selectAll(); return true; });
        etB.setOnLongClickListener(v -> { etB.selectAll(); return true; });

        // ---------- HEX 监听 ----------
        etHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (isUpdatingUI) return;
                if (currentEditingField != etHex) return;  // 不是我编辑的，忽略

                String hex = s.toString().trim();
                if (hex.startsWith("#")) hex = hex.substring(1);
                // 只在长度凑够 6 或 8 时才应用（避免中间态乱跳）
                if (hex.length() != 6 && hex.length() != 8) return;

                try {
                    long val = Long.parseLong(hex, 16);
                    int color;
                    if (hex.length() == 6) {
                        // 6 位：保留当前 alpha
                        int a = (currentColor >> 24) & 0xFF;
                        color = (a << 24) | (int) val;
                    } else {
                        color = (int) val;
                    }
                    currentColor = color;
                    int alpha = (color >> 24) & 0xFF;
                    alphaSeekBar.setProgress(alpha);
                    alphaText.setText(String.valueOf(alpha));
                    syncColorToActivePicker(color & 0x00FFFFFF);
                    // 只更新其他控件，不重写 HEX 自己
                    updateUIInternal(etHex);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "HEX 解析失败: " + hex, e);
                }
            }
        });

        // ---------- RGB 监听（共享） ----------
        TextWatcher rgbWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (isUpdatingUI) return;

                EditText which = null;
                if (s == etR.getEditableText()) which = etR;
                else if (s == etG.getEditableText()) which = etG;
                else if (s == etB.getEditableText()) which = etB;
                if (which == null || which != currentEditingField) return;

                String rs = etR.getText().toString();
                String gs = etG.getText().toString();
                String bs = etB.getText().toString();
                // 任一为空 = 编辑中间态，跳过
                if (rs.isEmpty() || gs.isEmpty() || bs.isEmpty()) return;

                int r = safeParseInt(rs);
                int g = safeParseInt(gs);
                int b = safeParseInt(bs);
                if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) return;

                int rgb = Color.rgb(r, g, b);
                int alpha = alphaSeekBar.getProgress();
                currentColor = (alpha << 24) | rgb;
                syncColorToActivePicker(rgb);
                // 只更新其他控件，不重写正在编辑的那个
                updateUIInternal(which);
            }
        };
        etR.addTextChangedListener(rgbWatcher);
        etG.addTextChangedListener(rgbWatcher);
        etB.addTextChangedListener(rgbWatcher);
    }

    /** 安全解析，失败返回 -1 */
    private int safeParseInt(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 失焦时：把空或非法的输入恢复为当前有效值 */
    private void normalizeField(EditText field) {
        if (isUpdatingUI) return;
        isUpdatingUI = true;
        try {
            int r = (currentColor >> 16) & 0xFF;
            int g = (currentColor >> 8) & 0xFF;
            int b = currentColor & 0xFF;

            if (field == etHex) {
                field.setText(String.format("#%08X", currentColor));
            } else if (field == etR) {
                field.setText(String.valueOf(r));
            } else if (field == etG) {
                field.setText(String.valueOf(g));
            } else if (field == etB) {
                field.setText(String.valueOf(b));
            }
        } finally {
            isUpdatingUI = false;
        }
    }

    // ===================== UI 更新 =====================

    /**
     * 普通 UI 更新（所有控件都刷新）
     * 用于非输入框触发的场景（色盘选色、滑块、收藏点击等）
     */
    private void updateUI() {
        updateUIInternal(null);
    }

    /**
     * UI 更新，但跳过 skipField（正在被用户编辑的字段不重写，避免吞输入）
     *
     * @param skipField 要跳过的字段（null = 不跳过）
     */
    private void updateUIInternal(EditText skipField) {
        if (isUpdatingUI) return;
        isUpdatingUI = true;
        try {
            int alpha = (currentColor >> 24) & 0xFF;
            int r = (currentColor >> 16) & 0xFF;
            int g = (currentColor >> 8) & 0xFF;
            int b = currentColor & 0xFF;

            // HSV 数值
            float[] hsv = new float[3];
            Color.colorToHSV(currentColor, hsv);
            tvHue.setText(String.format("%.0f°", hsv[0]));
            tvSaturation.setText(String.format("%.0f%%", hsv[1] * 100));
            tvValue.setText(String.format("%.0f%%", hsv[2] * 100));

            // HEX/RGB 输入框（跳过正在编辑的）
            if (skipField != etHex) etHex.setText(String.format("#%08X", currentColor));
            if (skipField != etR) etR.setText(String.valueOf(r));
            if (skipField != etG) etG.setText(String.valueOf(g));
            if (skipField != etB) etB.setText(String.valueOf(b));

            // 透明度
            alphaSeekBar.setProgress(alpha);
            alphaText.setText(String.valueOf(alpha));

            // 更新两个预览圆
            GradientDrawable drawableCenter = new GradientDrawable();
            drawableCenter.setShape(GradientDrawable.OVAL);
            drawableCenter.setColor(currentColor);
            drawableCenter.setStroke(2, Color.WHITE);
            colorPreview.setBackground(drawableCenter);

            GradientDrawable drawableCurrent = new GradientDrawable();
            drawableCurrent.setShape(GradientDrawable.OVAL);
            drawableCurrent.setColor(currentColor);
            drawableCurrent.setStroke(2, Color.WHITE);
            currentColorPreview.setBackground(drawableCurrent);

            // 输入框文字颜色自适应（不跳过，只改颜色不改文本，安全）
            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
            int textColor = Color.rgb(r, g, b);
            int bgColor = (luminance > 128) ? DEFAULT_BG_DARK : DEFAULT_BG_LIGHT;
            setEditTextStyle(etHex, textColor, bgColor);
            setEditTextStyle(etR, textColor, bgColor);
            setEditTextStyle(etG, textColor, bgColor);
            setEditTextStyle(etB, textColor, bgColor);

        } finally {
            isUpdatingUI = false;
        }
    }

    private void setEditTextStyle(EditText editText, int textColor, int bgColor) {
        editText.setTextColor(textColor);
        editText.setBackgroundColor(bgColor);
    }

    // ===================== 收藏功能 =====================
    private void showAddFavoriteDialog() {
        String currentHex = String.format("#%08X", currentColor);
        List<FavoriteGroup> groups = colorManager.getAllGroups();

        if (groups.isEmpty()) {
            showCreateGroupDialog(null);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogThemeDark);
        builder.setTitle("选择收藏夹");

        String[] groupNames = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            groupNames[i] = groups.get(i).groupName;
        }

        builder.setItems(groupNames, (dialog, which) -> {
            String selected = groupNames[which];
            colorManager.addColorToGroup(selected, currentHex);
            ToastUtil.showToast(this, "已收藏到 “" + selected + "”");
            renderFavorites();
        });

        builder.setPositiveButton("新建收藏夹", (dialog, which) -> showCreateGroupDialog(null));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showCreateGroupDialog(String defaultName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogThemeDark);
        builder.setTitle("新建收藏夹");

        final EditText input = new EditText(this);
        input.setHint("输入名称，如“肤色”");
        input.setTextColor(Color.BLACK);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(16, 8, 16, 8);
        if (!TextUtils.isEmpty(defaultName)) {
            input.setText(defaultName);
        }
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                ToastUtil.showToast(this, "名称不能为空");
                return;
            }
            String hex = String.format("#%08X", currentColor);
            colorManager.addColorToGroup(name, hex);
            ToastUtil.showToast(this, "已新建 “" + name + "” 并收藏此颜色");
            renderFavorites();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void renderFavorites() {
        favoritesContainer.removeAllViews();
        List<FavoriteGroup> groups = colorManager.getAllGroups();
        List<String> allColors = new ArrayList<>();
        for (FavoriteGroup group : groups) {
            allColors.addAll(group.colors);
        }

        final int MAX_DISPLAY = 200;
        boolean hasMore = allColors.size() > MAX_DISPLAY;
        List<String> displayColors = allColors.size() > MAX_DISPLAY ?
                allColors.subList(0, MAX_DISPLAY) : allColors;

        boolean hasFavorites = !displayColors.isEmpty();
        favoritesScrollView.setVisibility(hasFavorites ? View.VISIBLE : View.GONE);
        tvNoFavorites.setVisibility(hasFavorites ? View.GONE : View.VISIBLE);
        btnMoreFavorites.setVisibility(hasFavorites ? View.VISIBLE : View.GONE);

        if (!hasFavorites) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        for (String colorHex : displayColors) {
            View item = inflater.inflate(R.layout.item_common_color, favoritesContainer, false);
            FrameLayout colorSquare = item.findViewById(R.id.colorSquare);
            try {
                int color = Color.parseColor(colorHex);
                colorSquare.setBackgroundColor(color);
                colorSquare.setOnClickListener(v -> {
                    currentColor = color;
                    int alpha = (color >> 24) & 0xFF;
                    int rgb = color & 0x00FFFFFF;
                    alphaSeekBar.setProgress(alpha);
                    alphaText.setText(String.valueOf(alpha));
                    syncColorToActivePicker(rgb);
                    updateUI();
                });
                colorSquare.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this, R.style.AlertDialogThemeDark)
                            .setTitle("删除收藏")
                            .setMessage("确定要删除该颜色吗？")
                            .setPositiveButton("确定", (dialog, which) -> {
                                for (FavoriteGroup g : groups) {
                                    g.colors.remove(colorHex);
                                }
                                colorManager.saveGroups(groups);
                                renderFavorites();
                                ToastUtil.showToast(this, "已删除");
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                });
                favoritesContainer.addView(item);
            } catch (Exception e) {
                Log.e(TAG, "解析收藏颜色出错", e);
            }
        }

        // 如果超过200个，显示 +N 提示
        if (hasMore) {
            View moreItem = inflater.inflate(R.layout.item_common_color, favoritesContainer, false);
            FrameLayout colorSquare = moreItem.findViewById(R.id.colorSquare);
            colorSquare.setBackgroundColor(Color.TRANSPARENT);
            // 清除所有子 View（如果有）
            colorSquare.removeAllViews();
            TextView tvMore = new TextView(this);
            tvMore.setText("+ " + (allColors.size() - MAX_DISPLAY));
            tvMore.setTextColor(Color.WHITE);
            tvMore.setGravity(View.TEXT_ALIGNMENT_CENTER);
            tvMore.setTextSize(16);
            colorSquare.addView(tvMore, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            favoritesContainer.addView(moreItem);
        }
    }
}
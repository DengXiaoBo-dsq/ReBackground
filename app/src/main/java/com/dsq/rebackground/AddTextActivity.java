
package com.dsq.rebackground;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RegionIterator;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.dsq.rebackground.utils.BitmapCache;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class AddTextActivity extends AppCompatActivity {
    private WatermarkView watermarkView;
    private List<WatermarkView.Watermark> watermarks = new ArrayList<>(); // 定义水印区数组
    private EditText etText;
    private SeekBar sbSize;
    private int textColor = Color.WHITE;
    private Spinner spFont;
    // 添加满屏水印标识

    private String currentFontPath;
    private TextView currentColorView;
    private ProgressDialog progressDialog;
    private AlertDialog patternSelectionDialog;

    private Bitmap originalBitmap;
    private String LogloPath = "/storage/emulated/0/ReMoveBg-Config/loglos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watermark);

        // 初始化控件
        watermarkView = findViewById(R.id.watermarkView);
        etText = findViewById(R.id.et_text);

        spFont = findViewById(R.id.sp_font);
        currentColorView = findViewById(R.id.currentColorView);
        Button btnSelectImage = findViewById(R.id.btn_select_image);
        Button btnColor = findViewById(R.id.btn_color);
        Button btnReset = findViewById(R.id.btn_reset);
        Button btnSave = findViewById(R.id.btn_save);


        // 加载原始图片
        loadOriginalImage();
        initFonts();

        // 文本输入监听
        etText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                WatermarkView.Watermark current = watermarkView.currentWatermark;
                boolean needNewTextWatermark = false;

                // 情况1：没有文本水印区存在
                if (hasNoTextWatermark()) {
                    needNewTextWatermark = true;
                }
                // 情况2：当前选中的是图案水印区
                else if (current != null && current.type == WatermarkView.Watermark.Type.IMAGE) {
                    needNewTextWatermark = true;
                }

                if (needNewTextWatermark) {
                    createNewTextWatermark(s.toString());
                } else {
                    updateExistingTextWatermark(s.toString());
                }
                watermarkView.invalidate();
            }

            private boolean hasNoTextWatermark() {
                for (WatermarkView.Watermark wm : watermarkView.watermarks) {
                    if (wm.type == WatermarkView.Watermark.Type.TEXT) return false;
                }
                return true;
            }

            private void createNewTextWatermark(String text) {
                WatermarkView.Watermark newWM = new WatermarkView.Watermark();
                newWM.type = WatermarkView.Watermark.Type.TEXT;
                newWM.text = text;

                // 如果有激活的图案水印，继承位置
                if (watermarkView.currentWatermark != null) {
                    newWM.rect.set(
                            watermarkView.currentWatermark.rect.centerX(),
                            watermarkView.currentWatermark.rect.centerY(),
                            watermarkView.currentWatermark.rect.centerX() + 200, // 默认宽度
                            watermarkView.currentWatermark.rect.centerY() + 60    // 默认高度
                    );
                }

                watermarkView.watermarks.add(newWM);
                watermarkView.setCurrentWatermark(newWM);
                watermarkView.updateWatermarkRect(newWM);
            }

            private void updateExistingTextWatermark(String text) {
                if (watermarkView.currentWatermark != null) {
                    watermarkView.setWatermarkText(text);
                }
            }
        });



        // 颜色选择
        btnColor.setOnClickListener(v -> showColorPickerDialog());

        // 重置按钮
        btnReset.setOnClickListener(v -> resetWatermark());

        // 保存按钮
        btnSave.setOnClickListener(v -> saveWatermarkedImage());

        // 点击“添加图案水印”按钮
        btnSelectImage.setOnClickListener(v -> {
            // 加载素材图片并显示选择对话框
            List<Bitmap> patterns = loadPatternsFromFolder(LogloPath);
            showPatternSelectionDialog(patterns);
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 99 && resultCode == RESULT_OK) {
            handleColorResult(data);
            Log.d("颜色返回界面：","颜色值"+textColor);
            watermarkView.setTextColor(textColor);
        }
    }

    private void initFonts() {
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
            fontList.add("默认字体");
            ToastUtil.showToast(this, "请下载字体并放到字体目录中", Toast.LENGTH_SHORT);
        }

        // 将字体文件添加到 Spinner 中
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fontList);
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFont.setAdapter(fontAdapter);

        spFont.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String fontName = parent.getItemAtPosition(position).toString();
                if (!fontName.equals("默认字体")) {
                    currentFontPath = externalFontDirectory.getAbsolutePath() + "/" + fontName;
                    Typeface typeface = Typeface.createFromFile(currentFontPath);
                    watermarkView.setTextTypeface(typeface);
                } else {
                    watermarkView.setTextTypeface(Typeface.DEFAULT);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

//    private List<Bitmap> loadPatternsFromFolder(String folderPath) {
//        List<Bitmap> patterns = new ArrayList<>();
//        File folder = new File(folderPath);
//
//        // 检查文件夹是否存在
//
//        if (!folder.exists()) {
//            folder.mkdirs();   // 创建目录及其父目录
//            ToastUtil.showToast(this, "图案素材目录不存在，已自动创建，请放入图片后重试", Toast.LENGTH_SHORT);
//            return patterns;   // 返回空列表
//        }
//
//        // 检查文件夹是否为空
//        File[] files = folder.listFiles((dir, name) ->
//                name.endsWith(".png") || name.endsWith(".jpg"));
//        if (files == null || files.length == 0) {
//            ToastUtil.showToast(this, "素材文件夹为空，请下载图案", Toast.LENGTH_SHORT);
//            return patterns;
//        }
//
//        // 加载图案
//        for (File file : files) {
//            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
//            if (bitmap != null) {
//                patterns.add(bitmap);
//            }
//        }
//
//        return patterns;
//    }

    private List<Bitmap> loadPatternsFromFolder(String folderPath) {
        List<Bitmap> patterns = new ArrayList<>();
        File folder = new File(folderPath);

        // 如果文件夹不存在，则创建并提示路径
        if (!folder.exists()) {
            boolean created = folder.mkdirs();  // 创建目录（包括父目录）
            if (created) {
                ToastUtil.showToast(this, "图案素材目录已自动创建，请将图案图片放入：\n" + folder.getAbsolutePath(), Toast.LENGTH_LONG);
            } else {
                ToastUtil.showToast(this, "图案素材目录创建失败，请检查存储权限", Toast.LENGTH_SHORT);
            }
            return patterns;  // 返回空列表
        }

        // 检查文件夹是否为空
        File[] files = folder.listFiles((dir, name) ->
                name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"));
        if (files == null || files.length == 0) {
            ToastUtil.showToast(this, "图案素材文件夹为空，请将图片放入：\n" + folder.getAbsolutePath(), Toast.LENGTH_LONG);
            return patterns;
        }

        // 加载图案
        for (File file : files) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                patterns.add(bitmap);
            }
        }

        return patterns;
    }
    private void showPatternSelectionDialog(List<Bitmap> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            ToastUtil.showToast(this, "没有可用的图案，请下载图案", Toast.LENGTH_SHORT);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogThemeDark);
        builder.setTitle("选择水印图案");
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        PatternAdapter adapter = new PatternAdapter(patterns);
        recyclerView.setAdapter(adapter);
        builder.setView(recyclerView);
        patternSelectionDialog = builder.create();
        patternSelectionDialog.show();
    }

    private class PatternAdapter extends RecyclerView.Adapter<PatternViewHolder> {
        private List<Bitmap> patterns;

        public PatternAdapter(List<Bitmap> patterns) {
            this.patterns = patterns;
        }

        @Override
        public PatternViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(200, 200));
            return new PatternViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(PatternViewHolder holder, int position) {
            holder.imageView.setImageBitmap(patterns.get(position));
            holder.imageView.setOnClickListener(v -> {
                // 用户选择图片后，直接创建图片水印
                Bitmap selectedPattern = patterns.get(position);
                createPatternWatermark(selectedPattern);

                // 关闭对话框
                if (patternSelectionDialog != null && patternSelectionDialog.isShowing()) {
                    patternSelectionDialog.dismiss();
                }
            });
        }

        @Override
        public int getItemCount() {
            return patterns.size();
        }
    }

    private static class PatternViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public PatternViewHolder(ImageView itemView) {
            super(itemView);
            imageView = itemView;
        }
    }

    private void createPatternWatermark(Bitmap patternBitmap) {
        WatermarkView.Watermark watermark = new WatermarkView.Watermark();
        watermark.initPatternWatermark(patternBitmap, "path/to/pattern");
        watermarkView.watermarks.add(watermark);
        watermarkView.setCurrentWatermark(watermark);
        watermarkView.updateWatermarkRect(watermark);
        watermarkView.invalidate();
    }

    private void loadOriginalImage() {
        String imageKey = getIntent().getStringExtra("imagekey");
        Log.d("水印设置界面：", imageKey);
        if (imageKey != null) {
            Bitmap bitmap = BitmapCache.getInstance().getBitmap(imageKey);
            if (bitmap != null) {
                originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                watermarkView.setImageBitmap(originalBitmap);
            } else {
                ToastUtil.showToast(this, "图片加载失败", Toast.LENGTH_SHORT);
                finish();
            }
        }
    }

    private void showColorPickerDialog() {
        Intent intent = new Intent(this, color_picker_view.class);
        startActivityForResult(intent, 99);
    }

    private void resetWatermark() {
        watermarkView.setWatermarkText("我们的水印很特别！");
        watermarkView.setTextSize(24);
        watermarkView.setTextColor(Color.WHITE);
    }

    private void handleColorResult(Intent data) {
        String colorHex = data.getStringExtra("bgColor");
        Log.d("主界面：","颜色值："+colorHex);

        try {
            // 直接解析完整颜色值
            textColor = Color.parseColor(colorHex);
            currentColorView.setBackgroundColor(textColor);

            // 修改后（同步更新）
            if (watermarkView.currentWatermark != null) {
                watermarkView.currentWatermark.textColor = textColor;
                watermarkView.currentWatermark.textPaint.setColor(textColor);
                watermarkView.currentWatermark.patternColorFilter = textColor; // ✅ 同步更新图案颜色
            }
        } catch (IllegalArgumentException e) {
            ToastUtil.showToast(this, "无效的颜色值", Toast.LENGTH_SHORT);
        }
    }

    private Bitmap ensureTransparency(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap transparentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(transparentBitmap);

        // 绘制原始图片，并强制设置透明度通道为 254
        Paint paint = new Paint();
        paint.setAlpha(254);  // 设置透明度为 254
        canvas.drawBitmap(bitmap, 0, 0, paint);

        return transparentBitmap;
    }

    private boolean saveBitmapToGallery(Bitmap bitmap) {
        // 使用MediaStore保存到公共目录
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "watermark_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void saveWatermarkedImage() {
        if (originalBitmap == null) {
            ToastUtil.showToast(this, "图片不可用！", Toast.LENGTH_SHORT);
            return;
        }
        progressDialog = new ProgressDialog(AddTextActivity.this, R.style.CustomProgressDialog);
        progressDialog.setMessage("正在保存水印信息...");
        progressDialog.show();
        // 在子线程中执行保存操作
        new Thread(() -> {
            Bitmap result = ensureTransparency(originalBitmap).copy(Bitmap.Config.ARGB_8888, true);

            // 获取视图的缩放和偏移参数
            float viewScale = watermarkView.viewScale;
            float viewLeft = watermarkView.viewLeft;
            float viewTop = watermarkView.viewTop;


            // 绘制所有水印
            for (WatermarkView.Watermark wm : watermarkView.watermarks) {

                if (wm.type == WatermarkView.Watermark.Type.IMAGE) {
                    // 绘制图案水印
                    Bitmap patternBitmap = wm.patternBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    int color = wm.patternColorFilter;

                    // 应用颜色和透明度到图案像素
                    int[] pixels = new int[patternBitmap.getWidth() * patternBitmap.getHeight()];
                    patternBitmap.getPixels(pixels, 0, patternBitmap.getWidth(), 0, 0,
                            patternBitmap.getWidth(), patternBitmap.getHeight());
                    for (int i = 0; i < pixels.length; i++) {
                        int originalAlpha = Color.alpha(pixels[i]);
                        int newAlpha = (int) (originalAlpha * wm.patternAlpha);
                        pixels[i] = Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color));
                    }
                    patternBitmap.setPixels(pixels, 0, patternBitmap.getWidth(), 0, 0,
                            patternBitmap.getWidth(), patternBitmap.getHeight());

                    // 计算目标位置（转换为原始图片坐标）
                    float imageLeft = (wm.rect.left - viewLeft) / viewScale;
                    float imageTop = (wm.rect.top - viewTop) / viewScale;
                    float imageRight = (wm.rect.right - viewLeft) / viewScale;
                    float imageBottom = (wm.rect.bottom - viewTop) / viewScale;
                    RectF imageRect = new RectF(imageLeft, imageTop, imageRight, imageBottom);

                    // 构建矩阵（与视图相同的缩放逻辑）
                    Matrix matrix = new Matrix();
                    RectF srcRect = new RectF(0, 0, patternBitmap.getWidth(), patternBitmap.getHeight());
                    matrix.setRectToRect(srcRect, imageRect, Matrix.ScaleToFit.CENTER);
                    matrix.postRotate(wm.rotation, imageRect.centerX(), imageRect.centerY());

                    Canvas canvas = new Canvas(result);
                    canvas.drawBitmap(patternBitmap, matrix, new Paint());
                } else {
                    // 绘制文本水印
                    String text = wm.text;
                    int color = wm.textColor;

                    float textSize = wm.textSize / viewScale; // 根据图片缩放比例调整字体大小
                    Typeface typeface = wm.textPaint.getTypeface();
                    float rotation = wm.rotation;

                    // 设置Paint
                    Paint paint = new Paint();
                    paint.setColor(color); // 直接使用带透明度的颜色
                    paint.setTextSize(textSize);
                    paint.setTypeface(typeface);
                    paint.setAntiAlias(true);

                    // 计算文本起始点（包含 TEXT_PADDING）
                    float textX = (wm.rect.left + WatermarkView.TEXT_PADDING - viewLeft) / viewScale;
                    float textY = (wm.rect.top + WatermarkView.TEXT_PADDING - viewTop) / viewScale;

                    // 获取文本的所有坐标
                    List<int[]> coordinates = getTextPixelCoordinates(result, text, textX, textY, rotation, paint);

                    // 修改像素颜色
                    int[] pixels = new int[result.getWidth() * result.getHeight()];
                    result.getPixels(pixels, 0, result.getWidth(), 0, 0, result.getWidth(), result.getHeight());
                    for (int[] coord : coordinates) {
                        int x = coord[0];
                        int y = coord[1];
                        // 直接使用 textColor 替换原图像素
                        pixels[y * result.getWidth() + x] = color;
                    }

                    // 将修改后的像素写回Bitmap
                    result.setPixels(pixels, 0, result.getWidth(), 0, 0, result.getWidth(), result.getHeight());
                }
            }

            // 保存图片
            boolean is_save = saveBitmapToGallery(result);
            // 隐藏 ProgressDialog
            runOnUiThread(() -> {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                    if (is_save) {
                        ToastUtil.showToast(this, "图片保存成功！", Toast.LENGTH_SHORT);
                    } else {
                        ToastUtil.showToast(this, "图片保存失败！", Toast.LENGTH_SHORT);
                    }
                }
            });
        }).start();
    }

    private List<int[]> getTextPixelCoordinates(Bitmap bitmap, String text, float textX, float textY, float textRotation, Paint paint) {
        List<int[]> coordinates = new ArrayList<>();

        // 获取文本的Path
        Path textPath = new Path();
        float textWidth = paint.measureText(text);
        float textHeight = paint.descent() - paint.ascent();
        float x = textX; // 使用传入的起始点
        float y = textY + (-paint.ascent()); // 计算基线位置
        paint.getTextPath(text, 0, text.length(), x, y, textPath);

        // 创建旋转矩阵
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.postRotate(textRotation, x + textWidth / 2, y - textHeight / 2);

        // 应用旋转矩阵到Path
        textPath.transform(rotationMatrix);

        // 创建Region并设置Path
        Region region = new Region();
        region.setPath(textPath, new Region(0, 0, bitmap.getWidth(), bitmap.getHeight()));

        // 遍历Region中的像素
        RegionIterator iterator = new RegionIterator(region);
        Rect rect = new Rect();
        while (iterator.next(rect)) {
            for (int i = rect.left; i < rect.right; i++) {
                for (int j = rect.top; j < rect.bottom; j++) {
                    if (region.contains(i, j)) {
                        // 将坐标存入数组
                        coordinates.add(new int[]{i, j});
                    }
                }
            }
        }

        return coordinates;
    }
}
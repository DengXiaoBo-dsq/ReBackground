//package com.dsq.rebackground;
//
//import android.app.AlertDialog;
//import android.app.ProgressDialog;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.Color;
//import android.graphics.drawable.BitmapDrawable;
//import android.graphics.drawable.Drawable;
//import android.net.Uri;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.text.TextUtils;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.LinearLayout;
//import android.widget.ProgressBar;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.recyclerview.widget.GridLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.dsq.rebackground.ui.colorpicker.ColorPickerView;
//import com.dsq.rebackground.utils.ToastUtil;
//
//import java.io.FileNotFoundException;
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//
//public class ImageColorPickerActivity extends AppCompatActivity {
//
//    private ColorPickerView imagePickerView;
//    private View colorPreview;
//    private TextView hexText;
//    private Button btnExtract, btnCreateFavorite, btnCancel, btnConfirm;
//    private ProgressBar progressBar;
//    private TextView tvProgressStatus;
//    private RecyclerView recyclerView;
//    private LinearLayout parentLayout;
//    private TextView tvColorCount;
//
//    private int currentColor = Color.WHITE;
//    private Bitmap currentBitmap = null;
//    private List<Integer> extractedColors = new ArrayList<>();
//    private ColorExtractAdapter adapter;
//
//    // 创建收藏夹相关
//    private CreateFavoriteTask createFavoriteTask;
//    private ProgressDialog progressDialog; // 使用 ProgressDialog
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_image_color_picker);
//
//        imagePickerView = findViewById(R.id.imagePickerView);
//        colorPreview = findViewById(R.id.imgColorPreview);
//        hexText = findViewById(R.id.imgHexText);
//        btnExtract = findViewById(R.id.btnExtract);
//        btnCreateFavorite = findViewById(R.id.btnCreateFavorite);
//        btnCancel = findViewById(R.id.btnCancelImg);
//        btnConfirm = findViewById(R.id.btnConfirmImg);
//        progressBar = findViewById(R.id.progressBar);
//        tvProgressStatus = findViewById(R.id.tvProgressStatus);
//        recyclerView = findViewById(R.id.recyclerViewExtracted);
//        parentLayout = findViewById(R.id.parentLayout);
//        tvColorCount = findViewById(R.id.tvColorCount);
//
//        imagePickerView.setOnColorChangedListener(color -> {
//            currentColor = color;
//            updatePreview();
//        });
//
//        btnExtract.setOnClickListener(v -> showExtractDialog());
//        btnCreateFavorite.setOnClickListener(v -> showCreateFavoriteDialog());
//
//        btnCancel.setOnClickListener(v -> {
//            setResult(RESULT_CANCELED);
//            finish();
//        });
//        btnConfirm.setOnClickListener(v -> {
//            Intent data = new Intent();
//            data.putExtra("selectedColorHex", String.format("#%08X", currentColor));
//            setResult(RESULT_OK, data);
//            finish();
//        });
//
//        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
//        recyclerView.setLayoutManager(layoutManager);
//        adapter = new ColorExtractAdapter();
//        recyclerView.setAdapter(adapter);
//
//        Uri imageUri = getIntent().getData();
//        if (imageUri == null) {
//            String uriString = getIntent().getStringExtra("imageUri");
//            if (uriString != null) imageUri = Uri.parse(uriString);
//        }
//
//        if (imageUri != null) {
//            loadImageFromUri(imageUri);
//        } else {
//            ToastUtil.showToast(this, "未接收到图片，请返回重新选择");
//            finish();
//        }
//
//        int initColor = getIntent().getIntExtra("initialColor", Color.WHITE);
//        currentColor = initColor;
//        updatePreview();
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        // 取消正在执行的任务，防止内存泄漏
//        if (createFavoriteTask != null && createFavoriteTask.getStatus() != AsyncTask.Status.FINISHED) {
//            createFavoriteTask.cancel(true);
//        }
//        if (progressDialog != null && progressDialog.isShowing()) {
//            progressDialog.dismiss();
//            progressDialog = null;
//        }
//    }
//
//    private void loadImageFromUri(Uri uri) {
//        try {
//            InputStream inputStream = getContentResolver().openInputStream(uri);
//            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
//            if (bitmap != null) {
//                currentBitmap = bitmap;
//                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
//                imagePickerView.setPaletteDrawable(drawable);
//                ToastUtil.showToast(this, "点击图片取色，双指可缩放");
//            } else {
//                ToastUtil.showToast(this, "图片解码失败");
//                finish();
//            }
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//            ToastUtil.showToast(this, "图片加载失败");
//            finish();
//        }
//    }
//
//    private void updatePreview() {
//        colorPreview.setBackgroundColor(currentColor);
//        hexText.setText(String.format("#%08X", currentColor));
//    }
//
//    // ========== 提取颜色逻辑 ==========
//    private void showExtractDialog() {
//        if (currentBitmap == null) {
//            ToastUtil.showToast(this, "请先加载图片");
//            return;
//        }
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogThemeDark);
//        builder.setTitle("提取图片颜色");
//
//        final EditText inputThreshold = new EditText(this);
//        inputThreshold.setHint("容差阈值 (0-255)");
//        inputThreshold.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
//        inputThreshold.setText("1");
//        inputThreshold.setTextColor(Color.BLACK);
//        inputThreshold.setBackgroundColor(Color.WHITE);
//        inputThreshold.setPadding(16, 8, 16, 8);
//
//        final android.widget.CheckBox checkBox = new android.widget.CheckBox(this);
//        checkBox.setText("启用缩放（长边>600时缩至600）");
//        checkBox.setTextColor(Color.WHITE);
//        checkBox.setChecked(true);
//
//        LinearLayout layout = new LinearLayout(this);
//        layout.setOrientation(LinearLayout.VERTICAL);
//        layout.setPadding(50, 20, 50, 20);
//        layout.addView(inputThreshold);
//        layout.addView(checkBox);
//
//        builder.setView(layout);
//
//        builder.setPositiveButton("提取", (dialog, which) -> {
//            int threshold = 1;
//            try {
//                threshold = Integer.parseInt(inputThreshold.getText().toString());
//                if (threshold < 0 || threshold > 255) threshold = 1;
//            } catch (Exception e) {
//                threshold = 1;
//            }
//            boolean useThumbnail = checkBox.isChecked();
//            startExtract(threshold, useThumbnail);
//        });
//        builder.setNegativeButton("取消", null);
//        builder.show();
//    }
//
//    private void startExtract(int threshold, boolean useThumbnail) {
//        progressBar.setVisibility(View.VISIBLE);
//        tvProgressStatus.setVisibility(View.VISIBLE);
//        tvProgressStatus.setText("正在提取...");
//        btnExtract.setEnabled(false);
//        recyclerView.setVisibility(View.GONE);
//        btnCreateFavorite.setVisibility(View.GONE);
//        tvColorCount.setVisibility(View.GONE);
//
//        new ExtractTask().execute(threshold, useThumbnail);
//    }
//
//    private class ExtractTask extends AsyncTask<Object, Integer, List<Integer>> {
//        @Override
//        protected List<Integer> doInBackground(Object... params) {
//            int threshold = (int) params[0];
//            boolean useThumbnail = (boolean) params[1];
//            Bitmap bitmap = currentBitmap;
//            if (bitmap == null) return new ArrayList<>();
//
//            if (useThumbnail) {
//                int maxSize = 600;
//                if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > maxSize) {
//                    float scale = (float) maxSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
//                    int newWidth = (int) (bitmap.getWidth() * scale);
//                    int newHeight = (int) (bitmap.getHeight() * scale);
//                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
//                }
//            }
//
//            int w = bitmap.getWidth();
//            int h = bitmap.getHeight();
//            int[] pixels = new int[w * h];
//            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
//
//            HashSet<Integer> colorSet = new HashSet<>();
//            int total = pixels.length;
//            int processed = 0;
//            publishProgress(0, total);
//            for (int pixel : pixels) {
//                int rgb = pixel & 0x00FFFFFF;
//                if (threshold <= 2) {
//                    colorSet.add(rgb);
//                } else {
//                    int step = threshold + 1;
//                    int r = (rgb >> 16) & 0xFF;
//                    int g = (rgb >> 8) & 0xFF;
//                    int b = rgb & 0xFF;
//                    int qr = (r / step) * step + step / 2;
//                    int qg = (g / step) * step + step / 2;
//                    int qb = (b / step) * step + step / 2;
//                    int quantized = (qr << 16) | (qg << 8) | qb;
//                    colorSet.add(quantized);
//                }
//                processed++;
//                if (processed % 10000 == 0) {
//                    publishProgress(processed, total);
//                }
//            }
//            publishProgress(total, total);
//            return new ArrayList<>(colorSet);
//        }
//
//        @Override
//        protected void onProgressUpdate(Integer... values) {
//            int processed = values[0];
//            int total = values[1];
//            if (total > 0) {
//                int percent = processed * 100 / total;
//                progressBar.setProgress(percent);
//                tvProgressStatus.setText(String.format("处理中 %d/%d (%d%%)", processed, total, percent));
//            }
//        }
//
//        @Override
//        protected void onPostExecute(List<Integer> result) {
//            progressBar.setVisibility(View.GONE);
//            tvProgressStatus.setVisibility(View.GONE);
//            btnExtract.setEnabled(true);
//
//            extractedColors = result;
//            if (extractedColors.isEmpty()) {
//                ToastUtil.showToast(ImageColorPickerActivity.this, "未提取到颜色");
//                return;
//            }
//
//            adapter.setColors(extractedColors);
//            recyclerView.setVisibility(View.VISIBLE);
//            btnCreateFavorite.setVisibility(View.VISIBLE);
//            tvColorCount.setText("共提取到 " + extractedColors.size() + " 种颜色");
//            tvColorCount.setVisibility(View.VISIBLE);
//
//            LinearLayout.LayoutParams imageParams = (LinearLayout.LayoutParams) imagePickerView.getLayoutParams();
//            imageParams.weight = 0.6f;
//            imagePickerView.setLayoutParams(imageParams);
//
//            LinearLayout.LayoutParams recyclerParams = (LinearLayout.LayoutParams) recyclerView.getLayoutParams();
//            recyclerParams.weight = 1.4f;
//            recyclerView.setLayoutParams(recyclerParams);
//
//            parentLayout.requestLayout();
//
//            ToastUtil.showToast(ImageColorPickerActivity.this, "提取完成，共 " + extractedColors.size() + " 种颜色", Toast.LENGTH_LONG);
//        }
//    }
//
//    // ========== 创建收藏夹相关 ==========
//    private void showCreateFavoriteDialog() {
//        if (extractedColors.isEmpty()) {
//            ToastUtil.showToast(this, "没有颜色可收藏");
//            return;
//        }
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogThemeDark);
//        builder.setTitle("新建收藏夹");
//
//        final EditText input = new EditText(this);
//        input.setHint("输入收藏夹名称");
//        input.setTextColor(Color.BLACK);
//        input.setBackgroundColor(Color.WHITE);
//        input.setPadding(16, 8, 16, 8);
//        builder.setView(input);
//
//        builder.setPositiveButton("确定", (dialog, which) -> {
//            String name = input.getText().toString().trim();
//            if (TextUtils.isEmpty(name)) {
//                ToastUtil.showToast(ImageColorPickerActivity.this, "名称不能为空");
//                return;
//            }
//            createFavorite(name);
//        });
//        builder.setNegativeButton("取消", null);
//        builder.show();
//    }
//
//    private void createFavorite(String folderName) {
//        // 启动异步任务
//        createFavoriteTask = new CreateFavoriteTask(folderName);
//        createFavoriteTask.execute();
//    }
//
//    /**
//     * 异步创建收藏夹任务
//     */
//    private class CreateFavoriteTask extends AsyncTask<Void, Integer, Boolean> {
//        private String folderName;
//        private String errorMsg;
//        private int colorCount;
//
//        public CreateFavoriteTask(String folderName) {
//            this.folderName = folderName;
//            this.colorCount = extractedColors.size();
//        }
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            // 使用 ProgressDialog，根据颜色数量决定样式
//            progressDialog = new ProgressDialog(ImageColorPickerActivity.this);
//            progressDialog.setTitle("正在创建收藏夹...");
//            progressDialog.setMessage("正在保存 " + colorCount + " 种颜色...");
//            progressDialog.setCancelable(true); // 允许用户取消
//            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "取消", (dialog, which) -> {
//                if (createFavoriteTask != null && createFavoriteTask.getStatus() != AsyncTask.Status.FINISHED) {
//                    createFavoriteTask.cancel(true);
//                }
//                dialog.dismiss();
//                ToastUtil.showToast(ImageColorPickerActivity.this, "已取消创建");
//            });
//
//            // 如果颜色超过 2000，使用水平进度条样式
//            if (colorCount > 2000) {
//                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//                progressDialog.setMax(100);
//                progressDialog.setProgress(0);
//            } else {
//                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//            }
//            progressDialog.show();
//        }
//
//        @Override
//        protected Boolean doInBackground(Void... params) {
//            try {
//                // 1. 构造 Hex 列表
//                List<String> hexList = new ArrayList<>();
//                int total = colorCount;
//                for (int i = 0; i < total; i++) {
//                    // 检查是否被取消
//                    if (isCancelled()) {
//                        return false;
//                    }
//                    int rgb = extractedColors.get(i);
//                    int r = (rgb >> 16) & 0xFF;
//                    int g = (rgb >> 8) & 0xFF;
//                    int b = rgb & 0xFF;
//                    hexList.add(String.format("#%02X%02X%02X", r, g, b));
//
//                    // 更新进度（如果显示进度条）
//                    if (total > 2000 && i % 10 == 0) {
//                        int progress = (i * 100) / total;
//                        publishProgress(progress);
//                    }
//                }
//
//                // 2. 获取所有组
//                ColorManager colorManager = ColorManager.getInstance(ImageColorPickerActivity.this);
//                List<FavoriteGroup> groups = colorManager.getAllGroups();
//
//                // 3. 查找或创建组
//                FavoriteGroup exist = null;
//                for (FavoriteGroup g : groups) {
//                    if (g.groupName.equals(folderName)) {
//                        exist = g;
//                        break;
//                    }
//                }
//                if (exist != null) {
//                    exist.colors.addAll(hexList);
//                    HashSet<String> set = new HashSet<>(exist.colors);
//                    exist.colors = new ArrayList<>(set);
//                } else {
//                    FavoriteGroup newGroup = new FavoriteGroup(folderName);
//                    newGroup.colors.addAll(hexList);
//                    groups.add(newGroup);
//                }
//
//                // 4. 同步保存（commit）
//                colorManager.saveGroupsSync(groups);
//                return true;
//            } catch (Exception e) {
//                e.printStackTrace();
//                errorMsg = e.getMessage();
//                return false;
//            }
//        }
//
//        @Override
//        protected void onProgressUpdate(Integer... values) {
//            super.onProgressUpdate(values);
//            if (progressDialog != null && progressDialog.isShowing() && colorCount > 2000) {
//                progressDialog.setProgress(values[0]);
//            }
//        }
//
//        @Override
//        protected void onPostExecute(Boolean success) {
//            // 关闭对话框
//            if (progressDialog != null && progressDialog.isShowing()) {
//                progressDialog.dismiss();
//                progressDialog = null;
//            }
//
//            if (success) {
//                ToastUtil.showToast(ImageColorPickerActivity.this,
//                        "已创建/更新收藏夹: " + folderName, Toast.LENGTH_LONG);
//                finish();
//            } else if (!isCancelled()) {
//                String msg = "创建失败：" + (errorMsg != null ? errorMsg : "未知错误");
//                ToastUtil.showToast(ImageColorPickerActivity.this, msg);
//            }
//        }
//
//        @Override
//        protected void onCancelled() {
//            // 任务被取消，关闭对话框
//            if (progressDialog != null && progressDialog.isShowing()) {
//                progressDialog.dismiss();
//                progressDialog = null;
//            }
//            // 不保存数据，但不用重复 Toast，已在取消按钮中提示
//        }
//    }
//
//    // ===== Adapter for extracted colors =====
//    private class ColorExtractAdapter extends RecyclerView.Adapter<ColorExtractAdapter.ViewHolder> {
//        private List<Integer> data = new ArrayList<>();
//
//        public void setColors(List<Integer> colors) {
//            this.data = colors;
//            notifyDataSetChanged();
//        }
//
//        @NonNull
//        @Override
//        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_extracted_color, parent, false);
//            return new ViewHolder(view);
//        }
//
//        @Override
//        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
//            int rgb = data.get(position);
//            int r = (rgb >> 16) & 0xFF;
//            int g = (rgb >> 8) & 0xFF;
//            int b = rgb & 0xFF;
//            String hex = String.format("#%02X%02X%02X", r, g, b);
//            holder.colorView.setBackgroundColor(Color.rgb(r, g, b));
//            holder.hexText.setText(hex);
//
//            holder.itemView.setOnClickListener(v -> {
//                currentColor = Color.rgb(r, g, b);
//                updatePreview();
//                ToastUtil.showToast(ImageColorPickerActivity.this, "已选择颜色: " + hex);
//            });
//        }
//
//        @Override
//        public int getItemCount() {
//            return data.size();
//        }
//
//        class ViewHolder extends RecyclerView.ViewHolder {
//            View colorView;
//            TextView hexText;
//
//            ViewHolder(@NonNull View itemView) {
//                super(itemView);
//                colorView = itemView.findViewById(R.id.extractedColorSquare);
//                hexText = itemView.findViewById(R.id.extractedHexText);
//            }
//        }
//    }
//}
package com.dsq.rebackground;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dsq.rebackground.ui.colorpicker.ColorPickerView;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ImageColorPickerActivity extends AppCompatActivity {

    private ColorPickerView imagePickerView;
    private View colorPreview;
    private TextView hexText;
    private Button btnExtract, btnCreateFavorite, btnCancel, btnConfirm;
    private ProgressBar progressBar;
    private TextView tvProgressStatus;
    private RecyclerView recyclerView;
    private LinearLayout parentLayout;
    private TextView tvColorCount;

    private int currentColor = Color.WHITE;
    private Bitmap currentBitmap = null;
    private List<Integer> extractedColors = new ArrayList<>();
    private ColorExtractAdapter adapter;

    // 创建收藏夹相关
    private CreateFavoriteTask createFavoriteTask;
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_color_picker);

        imagePickerView = findViewById(R.id.imagePickerView);
        colorPreview = findViewById(R.id.imgColorPreview);
        hexText = findViewById(R.id.imgHexText);
        btnExtract = findViewById(R.id.btnExtract);
        btnCreateFavorite = findViewById(R.id.btnCreateFavorite);
        btnCancel = findViewById(R.id.btnCancelImg);
        btnConfirm = findViewById(R.id.btnConfirmImg);
        progressBar = findViewById(R.id.progressBar);
        tvProgressStatus = findViewById(R.id.tvProgressStatus);
        recyclerView = findViewById(R.id.recyclerViewExtracted);
        parentLayout = findViewById(R.id.parentLayout);
        tvColorCount = findViewById(R.id.tvColorCount);

        imagePickerView.setOnColorChangedListener(color -> {
            currentColor = color;
            updatePreview();
        });

        btnExtract.setOnClickListener(v -> showExtractDialog());
        btnCreateFavorite.setOnClickListener(v -> showCreateFavoriteDialog());

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        btnConfirm.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra("selectedColorHex", String.format("#%08X", currentColor));
            setResult(RESULT_OK, data);
            finish();
        });

        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ColorExtractAdapter();
        recyclerView.setAdapter(adapter);

        Uri imageUri = getIntent().getData();
        if (imageUri == null) {
            String uriString = getIntent().getStringExtra("imageUri");
            if (uriString != null) imageUri = Uri.parse(uriString);
        }

        if (imageUri != null) {
            loadImageFromUri(imageUri);
        } else {
            ToastUtil.showToast(this, "未接收到图片，请返回重新选择");
            finish();
        }

        int initColor = getIntent().getIntExtra("initialColor", Color.WHITE);
        currentColor = initColor;
        updatePreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (createFavoriteTask != null && createFavoriteTask.getStatus() != AsyncTask.Status.FINISHED) {
            createFavoriteTask.cancel(true);
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private void loadImageFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                currentBitmap = bitmap;
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                imagePickerView.setPaletteDrawable(drawable);
                ToastUtil.showToast(this, "点击图片取色，双指可缩放");
            } else {
                ToastUtil.showToast(this, "图片解码失败");
                finish();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            ToastUtil.showToast(this, "图片加载失败");
            finish();
        }
    }

    private void updatePreview() {
        colorPreview.setBackgroundColor(currentColor);
        hexText.setText(String.format("#%08X", currentColor));
    }

    // ========== 提取颜色逻辑 ==========
    private void showExtractDialog() {
        if (currentBitmap == null) {
            ToastUtil.showToast(this, "请先加载图片");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogThemeDark);
        builder.setTitle("提取图片颜色");

        final EditText inputThreshold = new EditText(this);
        inputThreshold.setHint("容差阈值 (0-255)");
        inputThreshold.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputThreshold.setText("1");
        inputThreshold.setTextColor(Color.BLACK);
        inputThreshold.setBackgroundColor(Color.WHITE);
        inputThreshold.setPadding(16, 8, 16, 8);

        final android.widget.CheckBox checkBox = new android.widget.CheckBox(this);
        checkBox.setText("启用缩放（长边>600时缩至600）");
        checkBox.setTextColor(Color.WHITE);
        checkBox.setChecked(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);
        layout.addView(inputThreshold);
        layout.addView(checkBox);

        builder.setView(layout);

        builder.setPositiveButton("提取", (dialog, which) -> {
            int threshold = 1;
            try {
                threshold = Integer.parseInt(inputThreshold.getText().toString());
                if (threshold < 0 || threshold > 255) threshold = 1;
            } catch (Exception e) {
                threshold = 1;
            }
            boolean useThumbnail = checkBox.isChecked();
            startExtract(threshold, useThumbnail);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void startExtract(int threshold, boolean useThumbnail) {
        progressBar.setVisibility(View.VISIBLE);
        tvProgressStatus.setVisibility(View.VISIBLE);
        tvProgressStatus.setText("正在提取...");
        btnExtract.setEnabled(false);
        recyclerView.setVisibility(View.GONE);
        btnCreateFavorite.setVisibility(View.GONE);
        tvColorCount.setVisibility(View.GONE);

        new ExtractTask().execute(threshold, useThumbnail);
    }

    private class ExtractTask extends AsyncTask<Object, Integer, List<Integer>> {
        @Override
        protected List<Integer> doInBackground(Object... params) {
            int threshold = (int) params[0];
            boolean useThumbnail = (boolean) params[1];
            Bitmap bitmap = currentBitmap;
            if (bitmap == null) return new ArrayList<>();

            if (useThumbnail) {
                int maxSize = 600;
                if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > maxSize) {
                    float scale = (float) maxSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
                    int newWidth = (int) (bitmap.getWidth() * scale);
                    int newHeight = (int) (bitmap.getHeight() * scale);
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }
            }

            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

            HashSet<Integer> colorSet = new HashSet<>();
            int total = pixels.length;
            int processed = 0;
            publishProgress(0, total);
            for (int pixel : pixels) {
                int rgb = pixel & 0x00FFFFFF;
                if (threshold <= 2) {
                    colorSet.add(rgb);
                } else {
                    int step = threshold + 1;
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int qr = (r / step) * step + step / 2;
                    int qg = (g / step) * step + step / 2;
                    int qb = (b / step) * step + step / 2;
                    int quantized = (qr << 16) | (qg << 8) | qb;
                    colorSet.add(quantized);
                }
                processed++;
                if (processed % 10000 == 0) {
                    publishProgress(processed, total);
                }
            }
            publishProgress(total, total);
            return new ArrayList<>(colorSet);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            int processed = values[0];
            int total = values[1];
            if (total > 0) {
                int percent = processed * 100 / total;
                progressBar.setProgress(percent);
                tvProgressStatus.setText(String.format("处理中 %d/%d (%d%%)", processed, total, percent));
            }
        }

        @Override
        protected void onPostExecute(List<Integer> result) {
            progressBar.setVisibility(View.GONE);
            tvProgressStatus.setVisibility(View.GONE);
            btnExtract.setEnabled(true);

            extractedColors = result;
            if (extractedColors.isEmpty()) {
                ToastUtil.showToast(ImageColorPickerActivity.this, "未提取到颜色");
                return;
            }

            adapter.setColors(extractedColors);
            recyclerView.setVisibility(View.VISIBLE);
            btnCreateFavorite.setVisibility(View.VISIBLE);
            tvColorCount.setText("共提取到 " + extractedColors.size() + " 种颜色");
            tvColorCount.setVisibility(View.VISIBLE);

            LinearLayout.LayoutParams imageParams = (LinearLayout.LayoutParams) imagePickerView.getLayoutParams();
            imageParams.weight = 0.6f;
            imagePickerView.setLayoutParams(imageParams);

            LinearLayout.LayoutParams recyclerParams = (LinearLayout.LayoutParams) recyclerView.getLayoutParams();
            recyclerParams.weight = 1.4f;
            recyclerView.setLayoutParams(recyclerParams);

            parentLayout.requestLayout();

            ToastUtil.showToast(ImageColorPickerActivity.this, "提取完成，共 " + extractedColors.size() + " 种颜色", Toast.LENGTH_LONG);
        }
    }

    // ========== 创建收藏夹相关 ==========
    private void showCreateFavoriteDialog() {
        if (extractedColors.isEmpty()) {
            ToastUtil.showToast(this, "没有颜色可收藏");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogThemeDark);
        builder.setTitle("新建收藏夹");

        final EditText input = new EditText(this);
        input.setHint("输入收藏夹名称");
        input.setTextColor(Color.BLACK);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(16, 8, 16, 8);
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                ToastUtil.showToast(ImageColorPickerActivity.this, "名称不能为空");
                return;
            }
            createFavorite(name);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void createFavorite(String folderName) {
        boolean showProgress = extractedColors.size() > 2000;
        createFavoriteTask = new CreateFavoriteTask(folderName, showProgress);
        createFavoriteTask.execute();
    }

    private class CreateFavoriteTask extends AsyncTask<Void, Integer, Boolean> {
        private String folderName;
        private boolean showProgress;
        private String errorMsg;

        public CreateFavoriteTask(String folderName, boolean showProgress) {
            this.folderName = folderName;
            this.showProgress = showProgress;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            AlertDialog.Builder builder = new AlertDialog.Builder(ImageColorPickerActivity.this, R.style.AlertDialogThemeDark);
            builder.setCancelable(true);
            builder.setNegativeButton("取消", (dialog, which) -> {
                if (createFavoriteTask != null && createFavoriteTask.getStatus() != AsyncTask.Status.FINISHED) {
                    createFavoriteTask.cancel(true);
                }
                dialog.dismiss();
                ToastUtil.showToast(ImageColorPickerActivity.this, "已取消创建");
            });

            View view;
            if (showProgress) {
                view = LayoutInflater.from(ImageColorPickerActivity.this)
                        .inflate(R.layout.dialog_progress_horizontal, null);
                // 设置进度条初始值
                ProgressBar pb = view.findViewById(R.id.progressBar);
                pb.setProgress(0);
            } else {
                view = LayoutInflater.from(ImageColorPickerActivity.this)
                        .inflate(R.layout.dialog_progress_spinner, null);
            }
            builder.setView(view);
            progressDialog = builder.create();
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                List<String> hexList = new ArrayList<>();
                int total = extractedColors.size();
                for (int i = 0; i < total; i++) {
                    if (isCancelled()) {
                        return false;
                    }
                    int rgb = extractedColors.get(i);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    hexList.add(String.format("#%02X%02X%02X", r, g, b));

                    if (showProgress && i % 10 == 0) {
                        int progress = (i * 100) / total;
                        publishProgress(progress);
                    }
                }

                ColorManager colorManager = ColorManager.getInstance(ImageColorPickerActivity.this);
                List<FavoriteGroup> groups = colorManager.getAllGroups();

                FavoriteGroup exist = null;
                for (FavoriteGroup g : groups) {
                    if (g.groupName.equals(folderName)) {
                        exist = g;
                        break;
                    }
                }
                if (exist != null) {
                    exist.colors.addAll(hexList);
                    HashSet<String> set = new HashSet<>(exist.colors);
                    exist.colors = new ArrayList<>(set);
                } else {
                    FavoriteGroup newGroup = new FavoriteGroup(folderName);
                    newGroup.colors.addAll(hexList);
                    groups.add(newGroup);
                }

                colorManager.saveGroupsSync(groups);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                errorMsg = e.getMessage();
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (showProgress && progressDialog != null && progressDialog.isShowing()) {
                ProgressBar pb = progressDialog.findViewById(R.id.progressBar);
                if (pb != null) {
                    pb.setProgress(values[0]);
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
                progressDialog = null;
            }
            if (success) {
                ToastUtil.showToast(ImageColorPickerActivity.this,
                        "已创建/更新收藏夹: " + folderName, Toast.LENGTH_LONG);
                finish();
            } else {
                String msg = "创建失败：" + (errorMsg != null ? errorMsg : "未知错误");
                ToastUtil.showToast(ImageColorPickerActivity.this, msg);
            }
        }

        @Override
        protected void onCancelled() {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
                progressDialog = null;
            }
            ToastUtil.showToast(ImageColorPickerActivity.this, "已取消创建");
        }
    }

    // ===== Adapter for extracted colors =====
    private class ColorExtractAdapter extends RecyclerView.Adapter<ColorExtractAdapter.ViewHolder> {
        private List<Integer> data = new ArrayList<>();

        public void setColors(List<Integer> colors) {
            this.data = colors;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_extracted_color, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int rgb = data.get(position);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            String hex = String.format("#%02X%02X%02X", r, g, b);
            holder.colorView.setBackgroundColor(Color.rgb(r, g, b));
            holder.hexText.setText(hex);

            holder.itemView.setOnClickListener(v -> {
                currentColor = Color.rgb(r, g, b);
                updatePreview();
                ToastUtil.showToast(ImageColorPickerActivity.this, "已选择颜色: " + hex);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View colorView;
            TextView hexText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                colorView = itemView.findViewById(R.id.extractedColorSquare);
                hexText = itemView.findViewById(R.id.extractedHexText);
            }
        }
    }
}
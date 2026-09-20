package com.dsq.rebackground;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ColorExtractSettingActivity extends AppCompatActivity {

    private EditText etThreshold;
    private CheckBox cbUseThumbnail;
    private Button btnExtract;
    private ProgressBar progressBar;
    private TextView tvProgressStatus;
    private RecyclerView recyclerView;
    private LinearLayout layoutCreateFavorite;
    private EditText etFolderName;
    private Button btnCreateFavorite;

    private ColorExtractAdapter adapter;
    private List<Integer> extractedColors = new ArrayList<>();
    private String imagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_extract_setting);

        etThreshold = findViewById(R.id.etThreshold);
        cbUseThumbnail = findViewById(R.id.cbUseThumbnail);
        btnExtract = findViewById(R.id.btnExtract);
        progressBar = findViewById(R.id.progressBar);
        tvProgressStatus = findViewById(R.id.tvProgressStatus);
        recyclerView = findViewById(R.id.recyclerViewColors);
        layoutCreateFavorite = findViewById(R.id.layoutCreateFavorite);
        etFolderName = findViewById(R.id.etFolderName);
        btnCreateFavorite = findViewById(R.id.btnCreateFavorite);

        imagePath = getIntent().getStringExtra("imagePath");

        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ColorExtractAdapter();
        recyclerView.setAdapter(adapter);

        btnExtract.setOnClickListener(v -> startExtract());
        btnCreateFavorite.setOnClickListener(v -> createFavorite());
    }

    private void startExtract() {
        int threshold;
        try {
            threshold = Integer.parseInt(etThreshold.getText().toString());
            if (threshold < 0 || threshold > 255) threshold = 1;
        } catch (Exception e) {
            threshold = 1;
        }
        boolean useThumbnail = cbUseThumbnail.isChecked();

        progressBar.setVisibility(View.VISIBLE);
        tvProgressStatus.setVisibility(View.VISIBLE);
        tvProgressStatus.setText("正在提取...");
        btnExtract.setEnabled(false);
        recyclerView.setVisibility(View.GONE);
        layoutCreateFavorite.setVisibility(View.GONE);

        new ExtractTask().execute(threshold, useThumbnail);
    }

    private class ExtractTask extends AsyncTask<Object, Integer, List<Integer>> {
        @Override
        protected List<Integer> doInBackground(Object... params) {
            int threshold = (int) params[0];
            boolean useThumbnail = (boolean) params[1];
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
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
                ToastUtil.showToast(ColorExtractSettingActivity.this, "未提取到颜色", Toast.LENGTH_SHORT);
                return;
            }

            adapter.setColors(extractedColors);
            recyclerView.setVisibility(View.VISIBLE);
            layoutCreateFavorite.setVisibility(View.VISIBLE);
            ToastUtil.showToast(ColorExtractSettingActivity.this, "提取完成，共 " + extractedColors.size() + " 种颜色", Toast.LENGTH_LONG);
        }
    }

    private void createFavorite() {
        String name = etFolderName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            ToastUtil.showToast(this, "请输入收藏夹名称", Toast.LENGTH_SHORT);
            return;
        }
        if (extractedColors.isEmpty()) {
            ToastUtil.showToast(this, "没有颜色可收藏", Toast.LENGTH_SHORT);
            return;
        }

        List<String> hexList = new ArrayList<>();
        for (int rgb : extractedColors) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            hexList.add(String.format("#%02X%02X%02X", r, g, b));
        }

        ColorManager colorManager = ColorManager.getInstance(this);
        List<FavoriteGroup> groups = colorManager.getAllGroups();
        FavoriteGroup exist = null;
        for (FavoriteGroup g : groups) {
            if (g.groupName.equals(name)) {
                exist = g;
                break;
            }
        }
        if (exist != null) {
            exist.colors.addAll(hexList);
            HashSet<String> set = new HashSet<>(exist.colors);
            exist.colors = new ArrayList<>(set);
        } else {
            FavoriteGroup newGroup = new FavoriteGroup(name);
            newGroup.colors.addAll(hexList);
            groups.add(newGroup);
        }
        colorManager.saveGroups(groups);
        ToastUtil.showToast(this, "已创建/更新收藏夹: " + name, Toast.LENGTH_LONG);
        finish();
    }

    // ===== Adapter =====
    private class ColorExtractAdapter extends RecyclerView.Adapter<ColorExtractAdapter.ViewHolder> {
        private List<Integer> data = new ArrayList<>();

        public void setColors(List<Integer> colors) {
            this.data = colors;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_color_square, parent, false);
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
                colorView = itemView.findViewById(R.id.colorSquare);
                hexText = itemView.findViewById(R.id.hexText);
            }
        }
    }
}
package com.dsq.rebackground;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dsq.rebackground.utils.ToastUtil;

import java.util.ArrayList;
import java.util.List;

public class FavoriteColorsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ColorAdapter adapter;
    private ColorManager colorManager;
    private String groupName;
    private List<String> allColors = new ArrayList<>();
    private List<String> displayColors = new ArrayList<>();
    private int pageSize = 100;
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_colors);

        groupName = getIntent().getStringExtra("groupName");
        TextView title = findViewById(R.id.titleGroupName);
        title.setText(groupName);

        colorManager = ColorManager.getInstance(this);
        recyclerView = findViewById(R.id.recyclerViewColors);
        // 改为 GridLayoutManager，每行4列
        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ColorAdapter();
        recyclerView.setAdapter(adapter);

        // 滚动加载更多
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int totalItemCount = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (!isLoading && hasMore && lastVisible >= totalItemCount - 5) {
                    loadMore();
                }
            }
        });

        loadColors();
    }

    private void loadColors() {
        allColors = colorManager.getColorsInGroup(groupName);
        currentPage = 0;
        displayColors.clear();
        hasMore = true;
        loadMore();
    }

    private void loadMore() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, allColors.size());
        if (start >= allColors.size()) {
            hasMore = false;
            isLoading = false;
            return;
        }
        List<String> subList = allColors.subList(start, end);
        displayColors.addAll(subList);
        currentPage++;
        if (end >= allColors.size()) {
            hasMore = false;
        }
        adapter.notifyDataSetChanged();
        isLoading = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadColors();
    }

    // ---------- Adapter ----------
    private class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ColorViewHolder> {

        @NonNull
        @Override
        public ColorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_favorite_color, parent, false);
            return new ColorViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ColorViewHolder holder, int position) {
            String hex = displayColors.get(position);
            try {
                int color = Color.parseColor(hex);
                holder.colorSquare.setBackgroundColor(color);
            } catch (Exception e) {
                holder.colorSquare.setBackgroundColor(Color.WHITE);
            }
            holder.hexText.setText(hex);

            holder.colorSquare.setOnClickListener(v -> {
                Intent result = new Intent();
                result.putExtra("selectedColorHex", hex);
                setResult(RESULT_OK, result);
                finish();
            });

            holder.colorSquare.setOnLongClickListener(v -> {
                showColorOptionsDialog(hex);
                return true;
            });

            holder.hexText.setOnLongClickListener(v -> {
                showHexOptionsDialog(hex);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return displayColors.size();
        }

        class ColorViewHolder extends RecyclerView.ViewHolder {
            View colorSquare;
            TextView hexText;

            ColorViewHolder(@NonNull View itemView) {
                super(itemView);
                colorSquare = itemView.findViewById(R.id.colorSquare);
                hexText = itemView.findViewById(R.id.hexText);
            }
        }

        private void showColorOptionsDialog(String hex) {
            String[] options = {"复制Hex值", "删除此颜色"};
            new AlertDialog.Builder(FavoriteColorsActivity.this, R.style.AlertDialogThemeDark)
                    .setTitle("颜色操作")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            copyToClipboard(hex);
                            ToastUtil.showToast(FavoriteColorsActivity.this, "已复制 " + hex);
                        } else if (which == 1) {
                            new AlertDialog.Builder(FavoriteColorsActivity.this, R.style.AlertDialogThemeDark)
                                    .setTitle("删除颜色")
                                    .setMessage("确定要删除该颜色吗？")
                                    .setPositiveButton("确定", (d, w) -> {
                                        List<FavoriteGroup> groups = colorManager.getAllGroups();
                                        for (FavoriteGroup g : groups) {
                                            if (g.groupName.equals(groupName)) {
                                                g.colors.remove(hex);
                                                break;
                                            }
                                        }
                                        colorManager.saveGroups(groups);
                                        loadColors();
                                        ToastUtil.showToast(FavoriteColorsActivity.this, "已删除");
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        }
                    })
                    .show();
        }

        private void showHexOptionsDialog(String currentHex) {
            String[] options = {"复制此Hex值", "粘贴颜色（从剪贴板添加）"};
            new AlertDialog.Builder(FavoriteColorsActivity.this, R.style.AlertDialogThemeDark)
                    .setTitle("Hex操作")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            copyToClipboard(currentHex);
                            ToastUtil.showToast(FavoriteColorsActivity.this, "已复制 " + currentHex);
                        } else if (which == 1) {
                            String clip = getClipboardText();
                            if (clip == null || clip.isEmpty()) {
                                ToastUtil.showToast(FavoriteColorsActivity.this, "剪贴板为空");
                                return;
                            }
                            String hex = clip.trim();
                            if (!hex.startsWith("#")) hex = "#" + hex;
                            try {
                                int color = Color.parseColor(hex);
                                String formatted = String.format("#%08X", color);
                                List<FavoriteGroup> groups = colorManager.getAllGroups();
                                for (FavoriteGroup g : groups) {
                                    if (g.groupName.equals(groupName)) {
                                        if (!g.colors.contains(formatted)) {
                                            g.colors.add(formatted);
                                        } else {
                                            ToastUtil.showToast(FavoriteColorsActivity.this, "颜色已存在");
                                        }
                                        break;
                                    }
                                }
                                colorManager.saveGroups(groups);
                                loadColors();
                                ToastUtil.showToast(FavoriteColorsActivity.this, "已添加 " + formatted);
                            } catch (Exception e) {
                                ToastUtil.showToast(FavoriteColorsActivity.this, "无效的颜色值");
                            }
                        }
                    })
                    .show();
        }

        private void copyToClipboard(String text) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("color", text);
            clipboard.setPrimaryClip(clip);
        }

        private String getClipboardText() {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    return clip.getItemAt(0).getText().toString();
                }
            }
            return null;
        }
    }
}
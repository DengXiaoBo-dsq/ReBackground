package com.dsq.rebackground;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dsq.rebackground.utils.ToastUtil;

import java.util.List;

public class FavoriteGroupsActivity extends AppCompatActivity {

    private static final int REQUEST_COLORS = 2001;
    private RecyclerView recyclerView;
    private GroupAdapter adapter;
    private ColorManager colorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_groups);

        colorManager = ColorManager.getInstance(this);
        recyclerView = findViewById(R.id.recyclerViewGroups);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadGroups();
    }

    private void loadGroups() {
        List<FavoriteGroup> groups = colorManager.getAllGroups();
        if (adapter == null) {
            adapter = new GroupAdapter(groups);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateGroups(groups);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroups();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_COLORS && resultCode == RESULT_OK && data != null) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

    private class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

        private List<FavoriteGroup> groups;

        GroupAdapter(List<FavoriteGroup> groups) {
            this.groups = groups;
        }

        void updateGroups(List<FavoriteGroup> newGroups) {
            this.groups = newGroups;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_favorite_group, parent, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            FavoriteGroup group = groups.get(position);
            // 显示组名 + 颜色数量
            String displayName = group.groupName + " (" + group.colors.size() + ")";
            holder.groupName.setText(displayName);

            if (!group.colors.isEmpty()) {
                try {
                    int color = Color.parseColor(group.colors.get(0));
                    holder.colorPreview.setBackgroundColor(color);
                } catch (Exception e) {
                    holder.colorPreview.setBackgroundColor(Color.WHITE);
                }
            } else {
                holder.colorPreview.setBackgroundColor(Color.WHITE);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(FavoriteGroupsActivity.this, FavoriteColorsActivity.class);
                intent.putExtra("groupName", group.groupName);
                startActivityForResult(intent, REQUEST_COLORS);
            });

            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(FavoriteGroupsActivity.this, R.style.AlertDialogThemeDark)
                        .setTitle("删除收藏夹")
                        .setMessage("确定要删除收藏夹 \"" + group.groupName + "\" 及其所有颜色吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            List<FavoriteGroup> currentGroups = colorManager.getAllGroups();
                            currentGroups.removeIf(g -> g.groupName.equals(group.groupName));
                            colorManager.saveGroups(currentGroups);
                            loadGroups();
                            ToastUtil.showToast(FavoriteGroupsActivity.this, "已删除");
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        class GroupViewHolder extends RecyclerView.ViewHolder {
            View colorPreview;
            TextView groupName;

            GroupViewHolder(@NonNull View itemView) {
                super(itemView);
                colorPreview = itemView.findViewById(R.id.groupColorPreview);
                groupName = itemView.findViewById(R.id.groupName);
            }
        }
    }
}
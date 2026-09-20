package com.dsq.rebackground;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dsq.rebackground.api.ApiKeyManager;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class KeyManagementActivity extends AppCompatActivity {

    private EditText etKeyInput, etWatermarkUrl;
    private Button btnAddKey, btnViewAllKeys, btnUseSelectedKey, btnDeleteKey, btnSaveUrl;
    private Spinner spinnerKeys;
    private KeyAdapter keyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_management);

        etKeyInput = findViewById(R.id.et_key_input);
        etWatermarkUrl = findViewById(R.id.et_watermark_url);
        btnAddKey = findViewById(R.id.btn_add_key);
        btnViewAllKeys = findViewById(R.id.btn_view_all_keys);
        btnUseSelectedKey = findViewById(R.id.btn_use_selected_key);
        btnDeleteKey = findViewById(R.id.btn_delete_key);
        btnSaveUrl = findViewById(R.id.btn_save_url);
        spinnerKeys = findViewById(R.id.spinner_keys);

        // 检查并复制必要的文件
        ApiKeyManager.checkAndCopyFiles(this);

        // 加载密钥
        ApiKeyManager.loadApiKeys(this);

        // 获取所有密钥并初始化 adapter
        ArrayList<String> allKeys = new ArrayList<>(ApiKeyManager.getAllApiKeys());
        keyAdapter = new KeyAdapter(this, allKeys);
        spinnerKeys.setAdapter(keyAdapter);
        btnAddKey.setOnClickListener(v -> {
            String newKey = etKeyInput.getText().toString().trim();
            if (!newKey.isEmpty()) {
                ApiKeyManager.addApiKey(newKey);

                // 重新获取所有密钥并刷新适配器
                keyAdapter.clear();
                keyAdapter.addAll(ApiKeyManager.getAllApiKeys());
                keyAdapter.notifyDataSetChanged();

                etKeyInput.setText("");  // 清空输入框
                ToastUtil.showToast(this, "密钥已添加", Toast.LENGTH_SHORT);
            } else {
                ToastUtil.showToast(this, "请输入密钥", Toast.LENGTH_SHORT);
            }
        });

        // 查看所有密钥
        btnViewAllKeys.setOnClickListener(v -> {
            if (keyAdapter.getCount() == 0) {
                ToastUtil.showToast(this, "没有密钥可显示", Toast.LENGTH_SHORT);
            } else {
                keyAdapter.notifyDataSetChanged(); // 确保 Spinner 刷新显示
                ToastUtil.showToast(this, "显示所有密钥", Toast.LENGTH_SHORT);
            }
        });


        // 使用选中的密钥
        btnUseSelectedKey.setOnClickListener(v -> {
            String selectedKey = (String) spinnerKeys.getSelectedItem();
            if (selectedKey != null) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("selectedKey", selectedKey);
                setResult(RESULT_OK, resultIntent);
                finish();  // 返回主界面
            }
        });

        // 删除选中的密钥
        btnDeleteKey.setOnClickListener(v -> {
            int selectedPosition = spinnerKeys.getSelectedItemPosition();
            if (selectedPosition != AdapterView.INVALID_POSITION) {
                String selectedKey = (String) spinnerKeys.getSelectedItem();
                if (ApiKeyManager.removeApiKey(selectedKey)) {
                    keyAdapter.notifyDataSetChanged(); // 刷新 spinner
                    ToastUtil.showToast(this, "密钥已删除", Toast.LENGTH_SHORT);
                } else {
                    ToastUtil.showToast(this, "删除失败", Toast.LENGTH_SHORT);
                }
            } else {
                ToastUtil.showToast(this, "请选中一个密钥", Toast.LENGTH_SHORT);
            }
        });

        // 保存去水印接口 URL

        btnSaveUrl.setOnClickListener(v -> {
            String watermarkUrl = etWatermarkUrl.getText().toString().trim();
            if (!watermarkUrl.isEmpty()) {
                // 保存 URL 到指定目录
                saveUrlToFile(watermarkUrl);
                ToastUtil.showToast(this, "去水印接口 URL 已保存", Toast.LENGTH_SHORT);
                etWatermarkUrl.setText("");  // 清空输入框
            } else {
                ToastUtil.showToast(this, "请输入 URL", Toast.LENGTH_SHORT);
            }
        });

// 保存 URL 到文件

    }
    private void saveUrlToFile(String url) {
        File configDir = new File("/storage/emulated/0/ReMoveBg-Config/");
        if (!configDir.exists()) {
            configDir.mkdirs();  // 如果目录不存在，创建它
        }

        File urlFile = new File(configDir, "url.txt");

        // 追加写入 URL（不覆盖）
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(urlFile, true))) {
            writer.write(url);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
            ToastUtil.showToast(this, "保存 URL 时出错", Toast.LENGTH_SHORT);
        }
    }


    // 不再需要手动保存和加载密钥，ApiKeyManager 会自动处理
    // 因为 ApiKeyManager 已经实现了密钥的保存和加载逻辑
}

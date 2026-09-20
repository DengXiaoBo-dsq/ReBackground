package com.dsq.rebackground;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;



public class ActivationActivity extends AppCompatActivity {

    private static final String CONFIG_FOLDER = "/storage/emulated/0/ReMoveBg-Config";
    private static final String CONFIG_FILE = CONFIG_FOLDER + "/.config.ini";
    private static final String INFO_FILE = CONFIG_FOLDER + "/.info.ini";
    private static final int REQUEST_STORAGE_PERMISSION = 1;

    private TextView txtStatus, txtCode;
    private EditText edtKey;
    private Button btnCopy, btnActivate, btnClearKey;

    private String deviceCode;
    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        // 检查权限
        checkStoragePermissions();
        checkManageExternalStoragePermission();

        txtStatus = findViewById(R.id.txt_status);
        txtCode = findViewById(R.id.txt_code);
        edtKey = findViewById(R.id.edt_key);
        btnCopy = findViewById(R.id.btn_copy);
        btnActivate = findViewById(R.id.btn_activate);
        btnClearKey = findViewById(R.id.btn_clear_key);

        ensureConfigFolder();
        deviceCode = getDeviceMD5();
        txtCode.setText(deviceCode);

        btnCopy.setOnClickListener(v -> copyToClipboard(deviceCode));
        btnActivate.setOnClickListener(v -> activateSoftware());
        btnClearKey.setOnClickListener(v -> clearActivation());
    }

    // 确保配置文件夹和 .config.ini 文件存在
    private void ensureConfigFolder() {
        File folder = new File(CONFIG_FOLDER);
        if (!folder.exists()) folder.mkdirs();

        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(getDeviceMD5());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void checkStoragePermissions() {
        // 检查存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，申请权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        } else {
            // 权限已经授予
            ensureConfigFolder();
        }
    }


    private void checkManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 没有权限，申请权限
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
            } else {
                // 已有权限
                ensureConfigFolder();
            }
        } else {
            // 低于 Android 11，可以跳过此步骤，直接创建文件夹
            ensureConfigFolder();
        }
    }

    // 获取设备唯一识别码并进行 MD5 加密
    private String getDeviceMD5() {
        String deviceId = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        return md5(deviceId);
    }

    // 计算 MD5
    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // 计算 SHA-256
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // 复制到剪贴板
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Device Code", text);
        clipboard.setPrimaryClip(clip);
        ToastUtil.showToast(this, "已复制设备编码", Toast.LENGTH_SHORT);
    }

    // 执行激活流程
    private void activateSoftware() {
        String inputKey = edtKey.getText().toString().trim();
        if (inputKey.isEmpty()) {
            txtStatus.setText("请输入激活码");
            return;
        }

        // 获取当前日期
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // 计算 SHA-256 哈希
        String expectedKey = sha256(deviceCode + "DsqIsNumberOne" + date);

        if (expectedKey.equals(inputKey)) {
            String vipKey = sha256(deviceCode + "DsqIsNumberOneThisIsVip");

            try (FileWriter writer = new FileWriter(INFO_FILE)) {
                writer.write(vipKey);
            } catch (IOException e) {
                e.printStackTrace();
            }

            txtStatus.setText("恭喜您已成功激活ReMoveBg软件的使用权限！");
            txtStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

            // 10秒后跳转到主界面
            new android.os.Handler().postDelayed(() -> {
                startActivity(new Intent(ActivationActivity.this, MainActivity.class));
                finish();
            }, 2000);

        } else {
            txtStatus.setText("激活码错误，请联系管理员！");
            txtStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    // 清除激活信息
    private void clearActivation() {
        File infoFile = new File(INFO_FILE);
        if (infoFile.exists()) infoFile.delete();
        txtStatus.setText("激活信息已清除");
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // 授予了管理外部存储权限
                    ensureConfigFolder();
                } else {
                    ToastUtil.showToast(this, "需要管理外部存储权限", Toast.LENGTH_SHORT);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
                ensureConfigFolder();
            } else {
                // 权限被拒绝，提示用户
                ToastUtil.showToast(this, "存储权限被拒绝，无法创建配置文件夹", Toast.LENGTH_SHORT);
            }
        }
    }

}

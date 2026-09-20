package com.dsq.rebackground.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import com.dsq.rebackground.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ApiKeyManager {
    private static List<String> apiKeys = new ArrayList<>();
    private static int currentKeyIndex = 0;
    private static final String PREFS_NAME = "api_key_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String DEFAULT_URL = "https://47b8-171-94-4-46.ngrok-free.app/remove-watermark/";

    private static final String CONFIG_DIR = "/storage/emulated/0/ReMoveBg-Config/";

    // 检查并复制文件
    public static void checkAndCopyFiles(Context context) {
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();  // 创建目录
        }

        copyFileIfNotExist(context, R.raw.key, "key.txt");
        copyFileIfNotExist(context, R.raw.url, "url.txt");
    }

    // 复制资源文件到指定目录
    private static void copyFileIfNotExist(Context context, int rawResId, String fileName) {
        File file = new File(CONFIG_DIR + fileName);
        if (!file.exists()) {
            try (InputStream inputStream = context.getResources().openRawResource(rawResId);
                 OutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 加载 API 密钥
    // 从 key.txt 加载 API Keys
    public static void loadApiKeys(Context context) {
        apiKeys.clear();
        File keyFile = new File(CONFIG_DIR + "key.txt");

        // 如果文件不存在，先复制 raw 资源文件
        if (!keyFile.exists()) {
            checkAndCopyFiles(context);
        }

        // 再次检查文件是否存在，防止复制失败导致读取错误
        if (!keyFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(keyFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().replaceAll("\"", "");
                if (!line.isEmpty() && !apiKeys.contains(line)) {
                    apiKeys.add(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // 获取当前 API Key
    public static String getApiKey() {
        return apiKeys.isEmpty() ? null : apiKeys.get(currentKeyIndex);
    }

    // 切换到下一个 API Key
    public static boolean switchToNextKey() {
        if (apiKeys.isEmpty()) return false;
        if (currentKeyIndex < apiKeys.size() - 1) {
            currentKeyIndex++;
            return true;
        }
        return false;
    }

    // 将 apiKeys 保存到 key.txt
    private static void saveApiKeysToFile() {
        File keyFile = new File(CONFIG_DIR + "key.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(keyFile))) {
            for (String key : apiKeys) {
                writer.write(key);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 添加新的密钥并保存到文件
    public static void addApiKey(String newKey) {
        if (newKey != null && !newKey.trim().isEmpty() && !apiKeys.contains(newKey.trim())) {
            apiKeys.add(newKey.trim());
            saveApiKeysToFile();  // 追加保存
        }
    }
    // 删除指定的密钥并更新文件
    public static boolean removeApiKey(String keyToRemove) {
        if (keyToRemove != null && apiKeys.contains(keyToRemove)) {
            apiKeys.remove(keyToRemove);
            saveApiKeysToFile();  // 重新保存到文件
            return true;
        }
        return false;
    }
    // 获取所有密钥
    public static List<String> getAllApiKeys() {
        return new ArrayList<>(apiKeys);
    }


    // 获取当前的 URL
    public static String getCurrentUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, DEFAULT_URL);
    }

    // 切换 URL 并保存
    public static void setCurrentUrl(Context context, String newUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SERVER_URL, newUrl);
        editor.apply();
    }

    // 读取配置文件 url.txt
    public static String readUrlFromFile(Context context) {
        File configFile = new File("/storage/emulated/0/ReMoveBg-Config/url.txt");
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String url = reader.readLine();
                return url != null ? url : DEFAULT_URL;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return DEFAULT_URL;  // 默认 URL
    }


}

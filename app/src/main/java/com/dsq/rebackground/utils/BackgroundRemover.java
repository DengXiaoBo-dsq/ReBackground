//
//package com.dsq.rebackground.utils;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.util.Log;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.util.concurrent.TimeUnit;
//
//import okhttp3.MediaType;
//import okhttp3.MultipartBody;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.Response;
//
///**
// * 远程抠图服务（通过 HTTP 调用 server.py）
// * 保持与原有同步方法签名兼容
// */
//public class BackgroundRemover {
//    private static final String TAG = "BackgroundRemover";
//    // 替换为您的 ngrok 公网地址（动态更新）
//    private static final String SERVER_URL = "https://stained-ashen-zookeeper.ngrok-free.dev/remove_bg";
//
//    private final OkHttpClient client;
//
//    // 构造函数保留 Context 参数（兼容旧代码）
//    public BackgroundRemover(Context context) {
//        client = new OkHttpClient.Builder()
//                .connectTimeout(30, TimeUnit.SECONDS)
//                .writeTimeout(30, TimeUnit.SECONDS)
//                .readTimeout(120, TimeUnit.SECONDS) // 处理时间可能较长
//                .build();
//        Log.d(TAG, "远程抠图服务初始化，URL: " + SERVER_URL);
//    }
//
//    /**
//     * 同步抠图（在子线程调用）
//     * @throws IOException 网络或服务端错误
//     */
//    public Bitmap removeBackground(Bitmap inputBitmap) throws IOException {
//        if (inputBitmap == null) {
//            throw new IllegalArgumentException("Bitmap is null");
//        }
//
//        // 1. 将 Bitmap 转为 PNG 字节数组
//        ByteArrayOutputStream stream = new ByteArrayOutputStream();
//        inputBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
//        byte[] imageBytes = stream.toByteArray();
//
//        // 2. 构建 multipart 请求
//        RequestBody body = new MultipartBody.Builder()
//                .setType(MultipartBody.FORM)
//                .addFormDataPart("file", "image.png",
//                        RequestBody.create(MediaType.parse("image/png"), imageBytes))
//                .build();
//
//        Request request = new Request.Builder()
//                .url(SERVER_URL)
//                .post(body)
//                .build();
//
//        // 3. 同步执行
//        try (Response response = client.newCall(request).execute()) {
//            if (!response.isSuccessful()) {
//                throw new IOException("Server error: " + response.code());
//            }
//            byte[] resultBytes = response.body().bytes();
//            Bitmap result = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.length);
//            if (result == null) {
//                throw new IOException("Failed to decode server response");
//            }
//            return result;
//        }
//    }
//
//    // 空实现，保持兼容
//    public void close() {
//        // 无需操作
//    }
//}

package com.dsq.rebackground.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BackgroundRemover {
    private static final String TAG = "BackgroundRemover";
    private static final String BASE_URL = "https://stained-ashen-zookeeper.ngrok-free.dev/remove_bg";
    private String modelType = "birefnet"; // 默认模型
    private final OkHttpClient client;

    public BackgroundRemover(Context context) {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        Log.d(TAG, "远程抠图服务初始化，URL: " + BASE_URL);
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
        Log.d(TAG, "切换模型: " + modelType);
    }

    public Bitmap removeBackground(Bitmap inputBitmap) throws IOException {
        if (inputBitmap == null) {
            throw new IllegalArgumentException("Bitmap is null");
        }

        // 构建带模型参数的 URL
        String url = BASE_URL + "?model=" + modelType;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        inputBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] imageBytes = stream.toByteArray();

        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "image.png",
                        RequestBody.create(MediaType.parse("image/png"), imageBytes))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Server error: " + response.code());
            }
            byte[] resultBytes = response.body().bytes();
            Bitmap result = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.length);
            if (result == null) {
                throw new IOException("Failed to decode server response");
            }
            return result;
        }
    }

    public void close() {
        // 无需操作
    }
}
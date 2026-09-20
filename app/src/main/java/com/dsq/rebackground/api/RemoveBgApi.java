//
//package com.dsq.rebackground.api;
//
//import android.app.ProgressDialog;
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.net.ConnectivityManager;
//import android.net.NetworkInfo;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.dsq.rebackground.R;
//
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.concurrent.TimeUnit;
//
//import okhttp3.Call;
//import okhttp3.Callback;
//import okhttp3.MediaType;
//import okhttp3.MultipartBody;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.Response;
//
//public class RemoveBgApi {
//
//    private static final String API_URL = "https://api.remove.bg/v1.0/removebg";
//    private static final int MAX_RETRY_COUNT = 2; // 重试次数（尺寸降级 + 密钥切换）
//
//    public static void removeBackground(final Bitmap bitmap, final RemoveBgCallback callback, final AppCompatActivity activity) {
//        // 检查网络
//        if (!isNetworkAvailable(activity)) {
//            activity.runOnUiThread(() -> {
//                ToastUtil.showToast(activity, "没有网络！请尝试到设置界面设置用本地模型去除！", Toast.LENGTH_SHORT);
//            });
//            callback.onError();
//            return;
//        }
//
//        String apiKey = ApiKeyManager.getApiKey();
//        if (apiKey == null) {
//            activity.runOnUiThread(() -> ToastUtil.showToast(activity, "密匙为空！", Toast.LENGTH_SHORT));
//            callback.onError();
//            return;
//        }
//
//        // 显示进度
//        ProgressDialog progressDialog = new ProgressDialog(activity, R.style.CustomProgressDialog);
//        progressDialog.setMessage("正在处理...");
//        progressDialog.show();
//
//        // 计算图片分辨率（百万像素）
//        float megapixels = (bitmap.getWidth() * bitmap.getHeight()) / 1_000_000f;
//        String sizeParam;
//        if (megapixels <= 25) {
//            sizeParam = "full";   // 最高 25MP，消耗 1 积分
//        } else {
//            sizeParam = "50MP";   // 最高 50MP，消耗 1 积分
//        }
//
//        // 使用 WebP 格式（支持透明且可高达 50MP）
//        String formatParam = "webp";
//
//        // 发起首次请求（尝试高分辨率）
//        doRequest(bitmap, callback, activity, apiKey, progressDialog, sizeParam, formatParam, 0);
//    }
//
//    private static void doRequest(final Bitmap bitmap,
//                                  final RemoveBgCallback callback,
//                                  final AppCompatActivity activity,
//                                  final String apiKey,
//                                  final ProgressDialog progressDialog,
//                                  final String sizeParam,
//                                  final String formatParam,
//                                  final int retryCount) {
//
//        // 如果重试次数过多，终止
//        if (retryCount > MAX_RETRY_COUNT) {
//            activity.runOnUiThread(() -> {
//                progressDialog.dismiss();
//                ToastUtil.showToast(activity, "多次重试失败，请检查网络或积分", Toast.LENGTH_LONG);
//            });
//            callback.onError();
//            return;
//        }
//
//        // 准备请求体
//        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
//        byte[] imageBytes = byteArrayOutputStream.toByteArray();
//
//        OkHttpClient client = new OkHttpClient.Builder()
//                .connectTimeout(60, TimeUnit.SECONDS)
//                .readTimeout(60, TimeUnit.SECONDS)
//                .writeTimeout(60, TimeUnit.SECONDS)
//                .build();
//
//        RequestBody requestBody = new MultipartBody.Builder()
//                .setType(MultipartBody.FORM)
//                .addFormDataPart("image_file", "image.png",
//                        RequestBody.create(imageBytes, MediaType.parse("image/png")))
//                .addFormDataPart("size", sizeParam)
//                .addFormDataPart("format", formatParam)
//                .build();
//
//        Request request = new Request.Builder()
//                .url(API_URL)
//                .addHeader("X-Api-Key", apiKey)
//                .post(requestBody)
//                .build();
//
//        client.newCall(request).enqueue(new Callback() {
//            @Override
//            public void onFailure(Call call, IOException e) {
//                activity.runOnUiThread(() -> {
//                    progressDialog.dismiss();
//                    ToastUtil.showToast(activity, "请求失败: " + e.getMessage(), Toast.LENGTH_SHORT);
//                });
//                // 网络失败，切换密钥重试（或直接报错）
//                switchApiKeyAndRetry(bitmap, callback, activity, progressDialog, sizeParam, formatParam, retryCount + 1);
//            }
//
//            @Override
//            public void onResponse(Call call, Response response) throws IOException {
//                int code = response.code();
//
//                if (code == 200) {
//                    // 成功
//                    activity.runOnUiThread(() -> progressDialog.dismiss());
//                    byte[] resultBytes = response.body().bytes();
//                    File outputDir = activity.getCacheDir();
//                    File outputFile = new File(outputDir, "output.webp"); // 保存为 webp
//                    try (FileOutputStream out = new FileOutputStream(outputFile)) {
//                        out.write(resultBytes);
//                        Bitmap resultBitmap = BitmapFactory.decodeFile(outputFile.getAbsolutePath());
//                        activity.runOnUiThread(() -> callback.onSuccess(resultBitmap));
//                    } catch (IOException e) {
//                        activity.runOnUiThread(() -> {
//                            ToastUtil.showToast(activity, "保存图片失败！", Toast.LENGTH_SHORT);
//                        });
//                        callback.onError();
//                    }
//                } else if (code == 402) {
//                    // 积分不足：降级到免费尺寸（preview）
//                    activity.runOnUiThread(() -> {
//                        ToastUtil.showToast(activity, "积分不足，已降级为预览尺寸（免费）", Toast.LENGTH_SHORT);
//                    });
//                    // 使用 preview 重试（不消耗积分）
//                    doRequest(bitmap, callback, activity, apiKey, progressDialog, "preview", formatParam, retryCount + 1);
//                } else if (code == 403) {
//                    // 密钥无效，切换密钥重试
//                    activity.runOnUiThread(() -> {
//                        ToastUtil.showToast(activity, "密钥无效，尝试切换...", Toast.LENGTH_SHORT);
//                    });
//                    switchApiKeyAndRetry(bitmap, callback, activity, progressDialog, sizeParam, formatParam, retryCount + 1);
//                } else {
//                    // 其他错误（如 400, 429 等）
//                    activity.runOnUiThread(() -> {
//                        progressDialog.dismiss();
//                        ToastUtil.showToast(activity, "处理失败，错误码: " + code, Toast.LENGTH_SHORT);
//                    });
//                    callback.onError();
//                }
//            }
//        });
//    }
//
//    // 切换密钥并重试
//    private static void switchApiKeyAndRetry(final Bitmap bitmap,
//                                             final RemoveBgCallback callback,
//                                             final AppCompatActivity activity,
//                                             final ProgressDialog progressDialog,
//                                             final String sizeParam,
//                                             final String formatParam,
//                                             final int retryCount) {
//        if (ApiKeyManager.switchToNextKey()) {
//            String newKey = ApiKeyManager.getApiKey();
//            activity.runOnUiThread(() -> {
//                // 切换密钥后重新发起请求
//                doRequest(bitmap, callback, activity, newKey, progressDialog, sizeParam, formatParam, retryCount);
//            });
//        } else {
//            activity.runOnUiThread(() -> {
//                progressDialog.dismiss();
//                ToastUtil.showToast(activity, "所有密钥均失效，请联系管理员", Toast.LENGTH_LONG);
//            });
//            callback.onError();
//        }
//    }
//
//    private static boolean isNetworkAvailable(AppCompatActivity activity) {
//        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
//        if (connectivityManager != null) {
//            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
//            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
//        }
//        return false;
//    }
//
//    public interface RemoveBgCallback {
//        void onSuccess(Bitmap result);
//        void onError();
//    }
//}

package com.dsq.rebackground.api;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dsq.rebackground.R;
import com.dsq.rebackground.utils.SuperResolutionHelper;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RemoveBgApi {

    private static final String API_URL = "https://api.remove.bg/v1.0/removebg";
    private static final int MAX_RETRY_COUNT = 2;

    public static void removeBackground(final Bitmap bitmap, final RemoveBgCallback callback, final AppCompatActivity activity) {
        // 检查网络
        if (!isNetworkAvailable(activity)) {
            activity.runOnUiThread(() -> {
                ToastUtil.showToast(activity, "没有网络！请尝试到设置界面设置用本地模型去除！", Toast.LENGTH_SHORT);
            });
            callback.onError();
            return;
        }

        String apiKey = ApiKeyManager.getApiKey();
        if (apiKey == null) {
            activity.runOnUiThread(() -> ToastUtil.showToast(activity, "密匙为空！", Toast.LENGTH_SHORT));
            callback.onError();
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(activity, R.style.CustomProgressDialog);
        progressDialog.setMessage("正在处理...");
        progressDialog.show();

        final int origWidth = bitmap.getWidth();
        final int origHeight = bitmap.getHeight();

        // 包装回调，在抠图成功后进行超分放大
        RemoveBgCallback wrappedCallback = new RemoveBgCallback() {
            @Override
            public void onSuccess(Bitmap result) {
                if (result == null || result.isRecycled()) {
                    callback.onSuccess(result);
                    return;
                }

                // 如果结果尺寸已经不小于原图，直接回调
                if (result.getWidth() >= origWidth && result.getHeight() >= origHeight) {
                    callback.onSuccess(result);
                    return;
                }

                // 尝试超分放大
                // 在 wrappedCallback 的 onSuccess 中
                try {
                    SuperResolutionHelper helper = new SuperResolutionHelper(activity);
                    Bitmap srResult = helper.upscaleWithAlpha(result, origWidth, origHeight);
                    helper.close();
                    callback.onSuccess(srResult != null ? srResult : result);
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onError() {
                callback.onError();
            }
        };

        float megapixels = (bitmap.getWidth() * bitmap.getHeight()) / 1_000_000f;
        String sizeParam = (megapixels <= 25) ? "full" : "50MP";
        String formatParam = "webp";

        doRequest(bitmap, wrappedCallback, activity, apiKey, progressDialog, sizeParam, formatParam, 0);
    }

    private static void doRequest(final Bitmap bitmap,
                                  final RemoveBgCallback callback,
                                  final AppCompatActivity activity,
                                  final String apiKey,
                                  final ProgressDialog progressDialog,
                                  final String sizeParam,
                                  final String formatParam,
                                  final int retryCount) {

        if (retryCount > MAX_RETRY_COUNT) {
            activity.runOnUiThread(() -> {
                progressDialog.dismiss();
                ToastUtil.showToast(activity, "多次重试失败，请检查网络或积分", Toast.LENGTH_LONG);
            });
            callback.onError();
            return;
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image_file", "image.png",
                        RequestBody.create(imageBytes, MediaType.parse("image/png")))
                .addFormDataPart("size", sizeParam)
                .addFormDataPart("format", formatParam)
                .build();

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("X-Api-Key", apiKey)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    ToastUtil.showToast(activity, "请求失败: " + e.getMessage(), Toast.LENGTH_SHORT);
                });
                switchApiKeyAndRetry(bitmap, callback, activity, progressDialog, sizeParam, formatParam, retryCount + 1);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();

                if (code == 200) {
                    activity.runOnUiThread(() -> progressDialog.dismiss());
                    byte[] resultBytes = response.body().bytes();
                    File outputDir = activity.getCacheDir();
                    File outputFile = new File(outputDir, "output.webp");
                    try (FileOutputStream out = new FileOutputStream(outputFile)) {
                        out.write(resultBytes);
                        Bitmap resultBitmap = BitmapFactory.decodeFile(outputFile.getAbsolutePath());
                        activity.runOnUiThread(() -> callback.onSuccess(resultBitmap));
                    } catch (IOException e) {
                        activity.runOnUiThread(() -> {
                            ToastUtil.showToast(activity, "保存图片失败！", Toast.LENGTH_SHORT);
                        });
                        callback.onError();
                    }
                } else if (code == 402) {
                    activity.runOnUiThread(() -> {
                        ToastUtil.showToast(activity, "积分不足，已降级为预览尺寸（免费）", Toast.LENGTH_SHORT);
                    });
                    doRequest(bitmap, callback, activity, apiKey, progressDialog, "preview", formatParam, retryCount + 1);
                } else if (code == 403) {
                    activity.runOnUiThread(() -> {
                        ToastUtil.showToast(activity, "密钥无效，尝试切换...", Toast.LENGTH_SHORT);
                    });
                    switchApiKeyAndRetry(bitmap, callback, activity, progressDialog, sizeParam, formatParam, retryCount + 1);
                } else {
                    activity.runOnUiThread(() -> {
                        progressDialog.dismiss();
                        ToastUtil.showToast(activity, "处理失败，错误码: " + code, Toast.LENGTH_SHORT);
                    });
                    callback.onError();
                }
            }
        });
    }

    private static void switchApiKeyAndRetry(final Bitmap bitmap,
                                             final RemoveBgCallback callback,
                                             final AppCompatActivity activity,
                                             final ProgressDialog progressDialog,
                                             final String sizeParam,
                                             final String formatParam,
                                             final int retryCount) {
        if (ApiKeyManager.switchToNextKey()) {
            String newKey = ApiKeyManager.getApiKey();
            activity.runOnUiThread(() -> {
                doRequest(bitmap, callback, activity, newKey, progressDialog, sizeParam, formatParam, retryCount);
            });
        } else {
            activity.runOnUiThread(() -> {
                progressDialog.dismiss();
                ToastUtil.showToast(activity, "所有密钥均失效，请联系管理员", Toast.LENGTH_LONG);
            });
            callback.onError();
        }
    }

    private static boolean isNetworkAvailable(AppCompatActivity activity) {
        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    public interface RemoveBgCallback {
        void onSuccess(Bitmap result);
        void onError();
    }
}
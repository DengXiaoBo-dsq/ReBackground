package com.dsq.rebackground.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.dsq.rebackground.ChangeInfoActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    private static final String TAG = "FileUtils";

    public static int getExifOrientation(String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            return exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
        } catch (IOException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }


    /**
     * 保存图片到设备存储
     *
     * @param bitmap  要保存的 Bitmap
     * @param context 上下文
     * @return 是否保存成功
     */
    public static boolean saveImageToStorage(Bitmap bitmap, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 及以上版本，使用 MediaStore 保存到公共目录

            return saveImageToGallery(bitmap, context);
        } else {

                boolean is_save=saveImageToGallery(bitmap, context);
                if(is_save){
                    return true;
                }
                // Android 10 以下版本，保存到私有目录
                else{
                    return saveImageToPrivateStorage(bitmap, context);
                }
            }


    }
    /**
     * 保存图片到公共目录（相册）
     *
     * @param bitmap  要保存的 Bitmap
     * @param context 上下文
     * @return 是否保存成功
     */
    private static boolean saveImageToGallery(Bitmap bitmap, Context context) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "image_" + System.currentTimeMillis() + "fill.png"); // 使用 PNG 格式
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png"); // 使用 PNG 格式
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // 使用 PNG 格式
//                    ToastUtil.showToast(context, "图片已保存到：Pictures文件夹！", Toast.LENGTH_SHORT);
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
//                ToastUtil.showToast(context, "保存失败！", Toast.LENGTH_SHORT);
            }
        }
        return false;
    }


    /**
     * 从URI获取Bitmap
     *
     * @param uri     URI
     * @param context 上下文
     * @return 解码后的 Bitmap
     */
    public static Bitmap getBitmapFromUri(Uri uri, Context context) {
        Bitmap bitmap = null;
        try {
            // 检查SDK版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android Q and later, using ContentResolver
                ContentResolver contentResolver = context.getContentResolver();
                InputStream inputStream = contentResolver.openInputStream(uri);
                bitmap = BitmapFactory.decodeStream(inputStream);
            } else {
                // For older versions, use the MediaStore API
                String path = getPathFromUri(uri, context);
                if (path != null) {
                    bitmap = BitmapFactory.decodeFile(path);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }
    /**
     * 保存图片到私有目录
     *
     * @param bitmap  要保存的 Bitmap
     * @param context 上下文
     * @return 是否保存成功
     */
    private static boolean saveImageToPrivateStorage(Bitmap bitmap, Context context) {
        FileOutputStream outStream = null;
        try {
            // 获取应用私有存储目录
            File directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ReBackground");
            if (!directory.exists()) {
                directory.mkdirs();
                Log.d("SaveImage", "Directory created: " + directory.getAbsolutePath());
            }

            // 创建保存图片的文件
            String newFileName = "image_" + System.currentTimeMillis() + ".png"; // 使用 PNG 格式
            File file = new File(directory, newFileName);
            Log.d("SaveImage", "File path: " + file.getAbsolutePath());

            // 将Bitmap保存为图片
            outStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream); // 使用 PNG 格式
            outStream.flush();
            ToastUtil.showToast(context, "图片已保存到软件私有目录中！"+file.getAbsolutePath(), Toast.LENGTH_SHORT);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            ToastUtil.showToast(context, "私有目录保存失败！", Toast.LENGTH_LONG);
            return false;
        } finally {
            try {
                if (outStream != null) {
                    outStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 获取文件路径（针对Android 4.4以下和Android 4.4及以上的不同路径处理）
     *
     * @param uri     URI
     * @param context 上下文
     * @return 文件路径
     */
    public static String getPathFromUri(Uri uri, Context context) {
        String path = null;

        if (DocumentsContract.isDocumentUri(context, uri)) {
            // 如果是DocumentProvider
            String documentId = DocumentsContract.getDocumentId(uri);
            if (uri.getAuthority().equals("com.android.providers.media")) {
                // 如果是MediaProvider，处理图片
                String id = documentId.split(":")[1];
                String[] columns = {MediaStore.Images.Media.DATA};
                String selection = MediaStore.Images.Media._ID + "=?";
                String[] selectionArgs = new String[]{id};

                Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        columns, selection, selectionArgs, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(columns[0]);
                    path = cursor.getString(columnIndex);
                    cursor.close();
                }
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // 如果是ContentProvider
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(projection[0]);
                path = cursor.getString(columnIndex);
                cursor.close();
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // 如果是文件路径
            path = uri.getPath();
        }

        return path;
    }

    /**
     * 根据 Exif 信息旋转图片
     *
     * @param bitmap    原始 Bitmap
     * @param imagePath 图片路径
     * @return 旋转后的 Bitmap
     */
    public static Bitmap rotateImageIfRequired(Bitmap bitmap, String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return rotateBitmap(bitmap, 90);
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return rotateBitmap(bitmap, 180);
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return rotateBitmap(bitmap, 270);
                default:
                    return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * 从文件加载缩放后的 Bitmap
     *
     * @param path      文件路径
     * @param reqWidth  目标宽度
     * @param reqHeight 目标高度
     * @return 缩放后的 Bitmap
     */
    public static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    /**
     * 从URI加载缩放后的Bitmap
     *
     * @param uri       URI
     * @param context   上下文
     * @param reqWidth  目标宽度
     * @param reqHeight 目标高度
     * @return 缩放后的Bitmap
     * @throws IOException 如果无法读取URI
     */
    public static Bitmap decodeSampledBitmapFromUri(Uri uri, Context context, int reqWidth, int reqHeight) throws IOException {
        if (uri == null) {
            Log.e("FileUtils", "URI is null");
            return null;
        }

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        // 第一次解码，获取图片尺寸
        ContentResolver contentResolver = context.getContentResolver();
        InputStream inputStream = contentResolver.openInputStream(uri);
        BitmapFactory.decodeStream(inputStream, null, options);
        if (inputStream != null) {
            inputStream.close();
        }

        // 打印图片原始宽高
        Log.d("FileUtils", "Original image dimensions: " + options.outWidth + "x" + options.outHeight);

        // 计算采样率
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        Log.d("FileUtils", "Sample size: " + options.inSampleSize);

        // 第二次解码，加载缩放后的图片
        options.inJustDecodeBounds = false;
        inputStream = contentResolver.openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
        if (inputStream != null) {
            inputStream.close();
        }

        // 打印缩放后的图片宽高
        if (bitmap != null) {
            Log.d("FileUtils", "Scaled image dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        } else {
            Log.e("FileUtils", "Failed to decode scaled bitmap");
        }

        return bitmap;
    }
    /**
     * 计算采样率
     *
     * @param options    BitmapFactory.Options
     * @param reqWidth   目标宽度
     * @param reqHeight  目标高度
     * @return 采样率
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * 将 Bitmap 保存到文件
     *
     * @param bitmap 要保存的 Bitmap
     * @param file   目标文件
     * @return 是否保存成功
     */
    public static boolean saveBitmapToFile(Bitmap bitmap, File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Bitmap rotateBitmap1(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    /**
     * 旋转 Bitmap
     *
     * @param bitmap 原始 Bitmap
     * @param degree 旋转角度
     * @return 旋转后的 Bitmap
     */
    private static Bitmap rotateBitmap(Bitmap bitmap, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
//package com.dsq.rebackground;
//
//import android.Manifest;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.Matrix;
//import android.graphics.PointF;
//import android.media.ExifInterface;
//import android.net.Uri;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.provider.Settings;
//import android.util.Log;
//import android.view.MotionEvent;
//import android.view.View;
//import android.view.ViewTreeObserver;
//import android.widget.Button;
//import android.widget.ImageView;
//import android.widget.Toast;
//
//import java.io.FileOutputStream;
//import java.nio.charset.StandardCharsets;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import androidx.core.content.FileProvider;
//import com.dsq.rebackground.api.ApiKeyManager;
//import com.dsq.rebackground.api.RemoveBgApi;
//import com.dsq.rebackground.utils.BitmapCache;
//import com.dsq.rebackground.utils.FileUtils;
//
//import java.io.File;
//import java.io.IOException;
//
//import ai.onnxruntime.OrtException;
//
//public class MainActivity extends AppCompatActivity {
//
//    private static final int PICK_IMAGE_REQUEST = 1;
//    private static final int TAKE_PICTURE_REQUEST = 2;
//    private static final int REQUEST_CAMERA_PERMISSION = 1;
//    private static final int REQUEST_STORAGE_PERMISSION = 2;
//    private static final int REQUEST_CHANGE_BACKGROUND = 3;
//    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 4;
//
//
//    private boolean ishow=true;
//    private ImageView imageView;
//    private String photoFileName = null;
//    private Button btnPickImage, btnTakePicture, btnRemoveBg, btnChangeBg, btnSave, btnCrop, btnRemoveWatermark;
//    private Bitmap selectedBitmap;
//    private Button btnInputNewKey;
//    private String imagePath="";
//    private String currentImageKey; // 当前缓存的图片键
//
//    private Matrix matrix = new Matrix();
//    private float scaleFactor = 1.0f;
//    private PointF start = new PointF();
//    private int previewCenterX, previewCenterY; // 图片显示区域中心点
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        // 【核心修改1：移除激活检查与跳转逻辑，直接进入主界面】
//        // 原激活检查代码已删除，无需再判断激活状态，直接初始化主功能
//        ApiKeyManager.loadApiKeys(this);
//        setContentView(R.layout.activity_main);
//
//        // 初始化视图
//        imageView = findViewById(R.id.imageView);
//        btnPickImage = findViewById(R.id.btn_pick_image);
//        btnTakePicture = findViewById(R.id.btn_take_picture);
//        btnRemoveBg = findViewById(R.id.btn_remove_bg);
//        btnChangeBg = findViewById(R.id.btn_change_bg);
//        btnSave = findViewById(R.id.btn_save);
//        btnCrop = findViewById(R.id.btn_crop);
//        btnRemoveWatermark = findViewById(R.id.btn_remove_watermark);
//        btnInputNewKey = findViewById(R.id.btn_input_new_key);
//
//        // 设置按钮点击事件（密钥管理按钮保留，不影响主功能）
//        btnInputNewKey.setOnClickListener(v -> AddNewKey());
//
//        // 检查存储权限（正常保留，确保文件操作可用）
//        checkStoragePermission();
//        checkManageExternalStoragePermission();
//        init_file();
//
//        // 设置图片选择、拍照等按钮点击事件
//        btnPickImage.setOnClickListener(v -> openFilePicker());
//        btnTakePicture.setOnClickListener(v -> {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//                    != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
//            } else {
//                openCamera();
//            }
//        });
//
//        // 初始化图片视图的布局监听（图片居中显示逻辑）
//        imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @Override
//            public void onGlobalLayout() {
//                imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//                previewCenterX = imageView.getWidth() / 2;
//                previewCenterY = imageView.getHeight() / 2;
//                centerImage();
//            }
//        });
//
//        // 设置图片触摸监听（缩放、拖动逻辑）
//        imageView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                handleZoom(event);
//                return true;
//            }
//        });
//
//        // 去背景按钮点击事件（保留原逻辑）
//        btnRemoveBg.setOnClickListener(v -> removeBackground_http());
//        // btnRemoveBg.setOnClickListener(v -> remove_u2net()); // 本地去背景逻辑保留，按需切换
//
//        // 其他功能按钮点击事件（正常保留）
//        btnChangeBg.setOnClickListener(v -> changeBackground());
//        btnSave.setOnClickListener(v->openCreateImageSettingsActivity());
//        btnCrop.setOnClickListener(v -> openCropActivity());
//        btnRemoveWatermark.setOnClickListener(v -> handleRemoveWatermark());
//    }
//
//
//    //==================================================
//    // 图片缩放、拖动相关逻辑（完全保留，确保交互正常）
//    //==================================================
//    private void handleZoom(MotionEvent event) {
//        switch (event.getAction() & MotionEvent.ACTION_MASK) {
//            case MotionEvent.ACTION_DOWN:
//                start.set(event.getX(), event.getY());
//                break;
//
//            case MotionEvent.ACTION_POINTER_DOWN:
//                float x = event.getX(0) - event.getX(1);
//                float y = event.getY(0) - event.getY(1);
//                start.set((float) Math.sqrt(x * x + y * y), 0);
//                break;
//
//            case MotionEvent.ACTION_MOVE:
//                if (event.getPointerCount() == 2) {
//                    handlePinchZoom(event);
//                }
//                break;
//        }
//    }
//
//    private void handlePinchZoom(MotionEvent event) {
//        float x = event.getX(0) - event.getX(1);
//        float y = event.getY(0) - event.getY(1);
//        float end = (float) Math.sqrt(x * x + y * y);
//
//        float newScaleFactor = end / start.x;
//        float scaleIncrement = (newScaleFactor - 1) * 0.1f;
//        scaleFactor += scaleIncrement;
//        scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));
//
//        updateImageMatrix();
//        start.set(end, 0);
//        alignImageCenter();
//    }
//
//    private void updateImageMatrix() {
//        float[] values = new float[9];
//        matrix.getValues(values);
//        float currentScale = values[Matrix.MSCALE_X];
//        float currentTranslateX = values[Matrix.MTRANS_X];
//        float currentTranslateY = values[Matrix.MTRANS_Y];
//
//        float newTranslateX = previewCenterX - (previewCenterX - currentTranslateX) * (scaleFactor / currentScale);
//        float newTranslateY = previewCenterY - (previewCenterY - currentTranslateY) * (scaleFactor / currentScale);
//
//        matrix.setScale(scaleFactor, scaleFactor, previewCenterX, previewCenterY);
//        matrix.postTranslate(newTranslateX - currentTranslateX, newTranslateY - currentTranslateY);
//        imageView.setImageMatrix(matrix);
//    }
//
//    private void alignImageCenter() {
//        float[] values = new float[9];
//        matrix.getValues(values);
//        float currentTranslateX = values[Matrix.MTRANS_X];
//        float currentTranslateY = values[Matrix.MTRANS_Y];
//
//        float imageCenterX = currentTranslateX + (imageView.getDrawable().getIntrinsicWidth() / 2f) * values[Matrix.MSCALE_X];
//        float imageCenterY = currentTranslateY + (imageView.getDrawable().getIntrinsicHeight() / 2f) * values[Matrix.MSCALE_Y];
//
//        float deltaX = previewCenterX - imageCenterX;
//        float deltaY = previewCenterY - imageCenterY;
//
//        if (Math.abs(deltaX) > 1 || Math.abs(deltaY) > 1) {
//            matrix.postTranslate(deltaX, deltaY);
//            imageView.setImageMatrix(matrix);
//        }
//    }
//
//    private void centerImage() {
//        if (selectedBitmap != null) {
//            matrix.reset();
//            float dx = (imageView.getWidth() - selectedBitmap.getWidth()) / 2f;
//            float dy = (imageView.getHeight() - selectedBitmap.getHeight()) / 2f;
//            matrix.postTranslate(dx, dy);
//            imageView.setImageMatrix(matrix);
//            scaleFactor = 1.0f;
//        }
//    }
//
//
//    //==========================================
//    // 文件夹初始化、权限检查相关逻辑（完全保留）
//    //==========================================
//    private void init_file(){
//        checkStoragePermission();
//        checkManageExternalStoragePermission();
//        File folder = new File("/storage/emulated/0/ReMoveBg-Config");
//        File folder_fonts = new File("/storage/emulated/0/ReMoveBg-Config/fonts");
//
//        boolean success = folder.mkdir();
//        if(success){
//            ToastUtil.showToast(this, "软件配置文件夹创建成功！", Toast.LENGTH_SHORT);
//            ToastUtil.showToast(this, folder.getAbsolutePath(), Toast.LENGTH_SHORT);
//        }
//        File folder_loglos = new File("/storage/emulated/0/ReMoveBg-Config/loglos");
//        boolean success_loglos = folder_loglos.mkdir();
//        if(success_loglos) {
//            ToastUtil.showToast(this, "图案素材文件夹创建成功，请将图案图片放入该目录", Toast.LENGTH_LONG);
//        }
//
//        boolean success_fonts=folder_fonts.mkdir();
//        if(success_fonts){
//            Toast toast1 = Toast.makeText(this,
//                    "软件文本字体文件夹创建成功，您可以下载喜欢的字体文件放入文件夹供文本编辑使用，文件夹路径为:" + folder_fonts.getAbsolutePath(), Toast.LENGTH_LONG);
//            toast1.show();
//            new Handler().postDelayed(toast1::cancel, 10000);
//        }
//    }
//
//    private void checkStoragePermission() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
//        }
//    }
//
//    private void checkManageExternalStoragePermission() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            if (!Environment.isExternalStorageManager()) {
//                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
//                Uri uri = Uri.fromParts("package", getPackageName(), null);
//                intent.setData(uri);
//                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
//            }
//        }
//    }
//
//
//    //======================工具方法=========================================
//    // 图片缓存与显示（完全保留，确保图片加载正常）
//    private void cacheAndDisplayImage(Bitmap bitmap, String imagePath) {
//        if (bitmap == null || imagePath == null) {
//            ToastUtil.showToast(this, "图片加载失败", Toast.LENGTH_SHORT);
//            return;
//        }
//
//        // 清理旧缓存
//        if (currentImageKey != null) {
//            BitmapCache.getInstance().remove(currentImageKey);
//        }
//
//        // 生成新缓存键
//        currentImageKey = "img_" + System.currentTimeMillis();
//        // 获取 Exif 方向信息
//        int orientation = FileUtils.getExifOrientation(imagePath);
//
//        // 获取图片信息
//        File file = new File(imagePath);
//        String format = getImageFormat(imagePath);
//        // 存入缓存时包含方向信息
//        BitmapCache.ImageInfo imageInfo = new BitmapCache.ImageInfo(
//                imagePath,
//                bitmap.getWidth(),
//                bitmap.getHeight(),
//                format,
//                file.length(),
//                orientation // 传递方向信息
//        );
//        BitmapCache.getInstance().put(currentImageKey, bitmap, imageInfo);
//
//        // 显示图片
//        imageView.setImageBitmap(bitmap);
//        selectedBitmap = bitmap;
//        // 新增居中显示逻辑
//        centerImage();
//        imageView.setImageMatrix(matrix);
//    }
//
//    // 获取图片的格式（通过文件扩展名）
//    private String getImageFormat(String path) {
//        String format = "未知格式";
//        File file = new File(path);
//        if (file.exists()) {
//            String fileName = file.getName();
//            if (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg")) {
//                format = "JPEG";
//            } else if (fileName.toLowerCase().endsWith(".png")) {
//                format = "PNG";
//            } else if (fileName.toLowerCase().endsWith(".gif")) {
//                format = "GIF";
//            }
//            // 可以根据实际需求扩展其他格式
//        }
//        return format;
//    }
//
//    // 设置按钮禁用启用
//    private void setButtonsEnabled(boolean enabled) {
//        btnPickImage.setEnabled(enabled);
//        btnTakePicture.setEnabled(enabled);
//        btnRemoveBg.setEnabled(enabled);
//        btnChangeBg.setEnabled(enabled);
//        btnSave.setEnabled(enabled);
//        btnCrop.setEnabled(enabled);
//        btnRemoveWatermark.setEnabled(enabled);
//    }
//
//    // 保存图片（完全保留，确保图片导出正常）
//    private void saveImage() {
//        if (selectedBitmap == null) {
//            ToastUtil.showToast(this, "背景去除失败！", Toast.LENGTH_LONG);
//            return;
//        }
//
//        // 检查原文件路径是否有效
//        if (imagePath == null || imagePath.isEmpty()) {
//            ToastUtil.showToast(this, "原文件路径无效！", Toast.LENGTH_LONG);
//            return;
//        }
//
//        // 获取原文件的目录和文件名
//        File originalFile = new File(imagePath);
//        String originalFileName = originalFile.getName();
//        String directoryPath = originalFile.getParent();
//
//        // 生成新文件名（在原文件名后添加 _ReBG_）
//        String fileNameWithoutExtension = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
//        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf('.'));
//        String newFileName = fileNameWithoutExtension + "_ReBG_" + System.currentTimeMillis() + fileExtension;
//
//        // 创建新文件
//        File newFile = new File(directoryPath, newFileName);
//
//        // 保存 Bitmap 到新文件
//        try (FileOutputStream out = new FileOutputStream(newFile)) {
//            selectedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // 使用 PNG 格式保存
//            ToastUtil.showToast(this, "去背景图片保存成功！路径：" + newFile.getAbsolutePath(), Toast.LENGTH_SHORT);
//
//            // 更新缓存和显示
//            cacheAndDisplayImage(selectedBitmap, newFile.getAbsolutePath());
//        } catch (IOException e) {
//            e.printStackTrace();
//            ToastUtil.showToast(this, "去背景图片保存失败！", Toast.LENGTH_LONG);
//        }
//        // 保存后重置缩放
//        new Handler().postDelayed(() -> {
//            centerImage();
//            imageView.setImageMatrix(matrix);
//        }, 500);
//    }
//
//
//    //=================================功能方法=============================
//    // 跳转新建空白界面
//    private void openCreateImageSettingsActivity() {
//        Intent intent = new Intent(this, CreateImageSettingsActivity.class);
//        startActivity(intent);
//    }
//
//    // 密匙管理功能（保留，不影响主功能）
//    private void AddNewKey(){
//        Intent intent = new Intent(MainActivity.this, KeyManagementActivity.class);
//        startActivityForResult(intent, 11);  // 1是请求码
//    }
//
//    // 打开选择图片
//    private void openFilePicker() {
//        Intent intent = new Intent(Intent.ACTION_PICK);
//        intent.setType("image/*");
//        startActivityForResult(intent, PICK_IMAGE_REQUEST);
//    }
//
//    // 拍照功能
//    private void openCamera() {
//        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
//
//        // 获取公共图片目录路径
//        File pictureDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
//
//        // 使用时间戳生成唯一的文件名
//        photoFileName = "photo_" + System.currentTimeMillis() + ".jpg";
//        File photoFile = new File(pictureDirectory, photoFileName);
//
//        // 创建Uri
//        Uri photoURI = FileProvider.getUriForFile(this, "com.dsq.rebackground.fileprovider", photoFile);
//        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI);
//        startActivityForResult(intent, TAKE_PICTURE_REQUEST);
//    }
//
//    // 去背景方法（HTTP接口方式，保留原逻辑）
//    private void removeBackground_http() {
//        if (selectedBitmap != null) {
//            setButtonsEnabled(false);
//            RemoveBgApi.removeBackground(selectedBitmap, new RemoveBgApi.RemoveBgCallback() {
//                @Override
//                public void onSuccess(Bitmap result) {
//                    setButtonsEnabled(true);
//                    imageView.setImageBitmap(result);
//                    selectedBitmap = result;
//                    if(selectedBitmap!=null){
//                        saveImage();
//                    }
//                }
//
//                @Override
//                public void onError() {
//                    setButtonsEnabled(true);
//                    ToastUtil.showToast(MainActivity.this, "图片背景去除错误！", Toast.LENGTH_LONG);
//                }
//            }, this);
//        } else {
//            ToastUtil.showToast(this, "请选择图片！", Toast.LENGTH_SHORT);
//        }
//    }
//
//    // 水印处理
//    private void handleRemoveWatermark() {
//        if (selectedBitmap != null) {
//            if(!ishow){
//                ToastUtil.showToast(this, "模型正在加载......", Toast.LENGTH_SHORT);
//            }
//
//            if (currentImageKey != null) {
//                Intent intent = new Intent(this, rebg_by_lama.class);
//                intent.putExtra("imageKey", currentImageKey);
//                startActivityForResult(intent, 33);
//            }else{
//                ToastUtil.showToast(this, "读取图片缓存失败！", Toast.LENGTH_SHORT);
//            }
//        }else{
//            ToastUtil.showToast(this, "请先选择图片！", Toast.LENGTH_SHORT);
//        }
//    }
//
//    // 万能裁剪方法
//    private void openCropActivity() {
//        if (selectedBitmap != null) {
//            if (currentImageKey != null) {
//                Intent intent = new Intent(this, UniversalCropActivity.class);
//                intent.putExtra("imageKey", currentImageKey);
//                startActivity(intent);
//            } else {
//                ToastUtil.showToast(this, "读取图片缓存失败！", Toast.LENGTH_SHORT);
//            }
//        } else {
//            ToastUtil.showToast(this, "请先选择图片！", Toast.LENGTH_SHORT);
//        }
//    }
//
//    // 换背景方法
//    private void changeBackground() {
//        if (selectedBitmap != null) {
//            if (currentImageKey != null) {
//                Intent intent = new Intent(MainActivity.this, BackgroundPickerActivity.class);
//                intent.putExtra("imageKey", currentImageKey);
//                startActivity(intent);
//            } else {
//                ToastUtil.showToast(this, "读取图片缓存失败！", Toast.LENGTH_SHORT);
//            }
//        } else {
//            ToastUtil.showToast(this, "请先选择图片！", Toast.LENGTH_SHORT);
//        }
//    }
//
//
//    //=================================界面交互与权限回调=============================
//    // 处理各界面返回数据（完全保留）
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (resultCode == RESULT_OK && requestCode == 11) {
//            String selectedKey = data.getStringExtra("selectedKey");
//            if (selectedKey != null) {
//                ToastUtil.showToast(this, "选中的密钥: " + selectedKey, Toast.LENGTH_SHORT);
//                // 根据选中的密钥执行进一步操作
//            }
//            // 处理图片缓存
//            if(selectedBitmap!=null){
//                cacheAndDisplayImage(selectedBitmap, imagePath);
//            }
//        }
//        else if (resultCode == RESULT_OK) {
//            if (requestCode == TAKE_PICTURE_REQUEST) {
//                // 拍照后加载图片
//                File pictureDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
//                File photoFile = new File(pictureDirectory, photoFileName);
//                imagePath=photoFile.getAbsolutePath();
//                Log.d("拍照后的路径：",imagePath);
//
//                if (photoFile.exists()) {
//                    Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
//                    if (bitmap != null) {
//                        bitmap = FileUtils.rotateImageIfRequired(bitmap, photoFile.getAbsolutePath());
//                        cacheAndDisplayImage(bitmap, imagePath);
//                    } else {
//                        ToastUtil.showToast(this, "Failed to load image", Toast.LENGTH_SHORT);
//                    }
//                } else {
//                    ToastUtil.showToast(this, "File does not exist", Toast.LENGTH_SHORT);
//                }
//            }
//            else if (requestCode == PICK_IMAGE_REQUEST && data != null) {
//                // 选择图片后加载
//                Uri imageUri = data.getData();
//                Bitmap bitmap = FileUtils.getBitmapFromUri(imageUri, this);
//                if (bitmap != null) {
//                    String imagePath1 = FileUtils.getPathFromUri(imageUri, this);
//                    imagePath=imagePath1;
//                    bitmap = FileUtils.rotateImageIfRequired(bitmap, imagePath1);
//                    cacheAndDisplayImage(bitmap, imagePath);
//                } else {
//                    ToastUtil.showToast(this, "Failed to load image", Toast.LENGTH_SHORT);
//                }
//            } else if (requestCode == REQUEST_CHANGE_BACKGROUND && data != null) {
//                // 换背景后加载结果
//                String resultPath = data.getStringExtra("resultPath");
//                imagePath=resultPath;
//                Log.d("改变背景后的路径：",imagePath);
//                if (resultPath != null) {
//                    Bitmap resultBitmap = BitmapFactory.decodeFile(resultPath);
//                    if (resultBitmap != null) {
//                        cacheAndDisplayImage(resultBitmap, imagePath);
//                    } else {
//                        ToastUtil.showToast(this, "Failed to load result image", Toast.LENGTH_SHORT);
//                    }
//                }
//            }
//        } else if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
//            // 管理存储权限回调
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                if (Environment.isExternalStorageManager()) {
//                    handleRemoveWatermark();
//                } else {
//                    ToastUtil.showToast(this, "需要管理外部存储权限", Toast.LENGTH_SHORT);
//                }
//            }
//        }
//        else if (requestCode == 33) {
//            // 模型加载状态回调
//            String flag = data.getStringExtra("isload");
//            if (flag != null && flag.equals("isload")) {
//                ishow = true;  // 更新 ishow 标志
//                Log.d("模型加载状态：", "模型已加载");
//            }
//        }
//    }
//
//    // 权限请求回调（完全保留）
//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == REQUEST_CAMERA_PERMISSION) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                openCamera();
//            } else {
//                ToastUtil.showToast(this, "Camera permission is required", Toast.LENGTH_SHORT);
//            }
//        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // 权限已授予
//            } else {
//                ToastUtil.showToast(this, "Storage permission is required to save image", Toast.LENGTH_SHORT);
//            }
//        } else if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // 权限已授予
//            } else {
//                ToastUtil.showToast(this, "Manage external storage permission is required", Toast.LENGTH_SHORT);
//            }
//        }
//    }
//
//}

package com.dsq.rebackground;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.dsq.rebackground.api.ApiKeyManager;
import com.dsq.rebackground.api.RemoveBgApi;
import com.dsq.rebackground.utils.BitmapCache;
import com.dsq.rebackground.utils.FileUtils;
import com.dsq.rebackground.realsr.RealSRMainActivity; // 导入 RealSR
import com.dsq.rebackground.utils.ToastUtil;

import java.io.File;
import java.io.IOException;

import ai.onnxruntime.OrtException;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int TAKE_PICTURE_REQUEST = 2; // 保留但不再使用
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_STORAGE_PERMISSION = 2;
    private static final int REQUEST_CHANGE_BACKGROUND = 3;
    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 4;


    private boolean ishow=true;
    private ImageView imageView;
    private String photoFileName = null;
    private Button btnPickImage, btnTakePicture, btnRemoveBg, btnChangeBg, btnSave, btnCrop, btnRemoveWatermark;
    private Bitmap selectedBitmap;
    private Button btnInputNewKey;
    private String imagePath="";
    private String currentImageKey; // 当前缓存的图片键

    private Matrix matrix = new Matrix();
    private float scaleFactor = 1.0f;
    private PointF start = new PointF();
    private int previewCenterX, previewCenterY; // 图片显示区域中心点


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApiKeyManager.loadApiKeys(this);
        setContentView(R.layout.activity_main);

        // 初始化视图
        imageView = findViewById(R.id.imageView);
        btnPickImage = findViewById(R.id.btn_pick_image);
        btnTakePicture = findViewById(R.id.btn_take_picture);
        btnRemoveBg = findViewById(R.id.btn_remove_bg);
        btnChangeBg = findViewById(R.id.btn_change_bg);
        btnSave = findViewById(R.id.btn_save);
        btnCrop = findViewById(R.id.btn_crop);
        btnRemoveWatermark = findViewById(R.id.btn_remove_watermark);
        btnInputNewKey = findViewById(R.id.btn_input_new_key);

        // 设置按钮点击事件（密钥管理按钮保留）
        btnInputNewKey.setOnClickListener(v -> AddNewKey());

        // 检查存储权限
        checkStoragePermission();
        checkManageExternalStoragePermission();
        init_file();

        // 图片选择按钮
        btnPickImage.setOnClickListener(v -> openFilePicker());

        // 超分辨按钮：启动 RealSRMainActivity
        btnTakePicture.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RealSRMainActivity.class);
            startActivity(intent);
        });

        // 初始化图片视图的布局监听（图片居中显示逻辑）
        imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                previewCenterX = imageView.getWidth() / 2;
                previewCenterY = imageView.getHeight() / 2;
                centerImage();
            }
        });

        // 设置图片触摸监听（缩放、拖动逻辑）
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleZoom(event);
                return true;
            }
        });

        // 去背景按钮点击事件
        btnRemoveBg.setOnClickListener(v -> removeBackground_http());

        // 其他功能按钮
        btnChangeBg.setOnClickListener(v -> changeBackground());
        btnSave.setOnClickListener(v->openCreateImageSettingsActivity());
        btnCrop.setOnClickListener(v -> openCropActivity());
        btnRemoveWatermark.setOnClickListener(v -> handleRemoveWatermark());
    }


    //==================================================
    // 图片缩放、拖动相关逻辑（完全保留）
    //==================================================
    private void handleZoom(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                start.set(event.getX(), event.getY());
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                start.set((float) Math.sqrt(x * x + y * y), 0);
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 2) {
                    handlePinchZoom(event);
                }
                break;
        }
    }

    private void handlePinchZoom(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        float end = (float) Math.sqrt(x * x + y * y);

        float newScaleFactor = end / start.x;
        float scaleIncrement = (newScaleFactor - 1) * 0.1f;
        scaleFactor += scaleIncrement;
        scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));

        updateImageMatrix();
        start.set(end, 0);
        alignImageCenter();
    }

    private void updateImageMatrix() {
        float[] values = new float[9];
        matrix.getValues(values);
        float currentScale = values[Matrix.MSCALE_X];
        float currentTranslateX = values[Matrix.MTRANS_X];
        float currentTranslateY = values[Matrix.MTRANS_Y];

        float newTranslateX = previewCenterX - (previewCenterX - currentTranslateX) * (scaleFactor / currentScale);
        float newTranslateY = previewCenterY - (previewCenterY - currentTranslateY) * (scaleFactor / currentScale);

        matrix.setScale(scaleFactor, scaleFactor, previewCenterX, previewCenterY);
        matrix.postTranslate(newTranslateX - currentTranslateX, newTranslateY - currentTranslateY);
        imageView.setImageMatrix(matrix);
    }

    private void alignImageCenter() {
        float[] values = new float[9];
        matrix.getValues(values);
        float currentTranslateX = values[Matrix.MTRANS_X];
        float currentTranslateY = values[Matrix.MTRANS_Y];

        float imageCenterX = currentTranslateX + (imageView.getDrawable().getIntrinsicWidth() / 2f) * values[Matrix.MSCALE_X];
        float imageCenterY = currentTranslateY + (imageView.getDrawable().getIntrinsicHeight() / 2f) * values[Matrix.MSCALE_Y];

        float deltaX = previewCenterX - imageCenterX;
        float deltaY = previewCenterY - imageCenterY;

        if (Math.abs(deltaX) > 1 || Math.abs(deltaY) > 1) {
            matrix.postTranslate(deltaX, deltaY);
            imageView.setImageMatrix(matrix);
        }
    }

    private void centerImage() {
        if (selectedBitmap != null) {
            matrix.reset();
            float dx = (imageView.getWidth() - selectedBitmap.getWidth()) / 2f;
            float dy = (imageView.getHeight() - selectedBitmap.getHeight()) / 2f;
            matrix.postTranslate(dx, dy);
            imageView.setImageMatrix(matrix);
            scaleFactor = 1.0f;
        }
    }


    //==========================================
    // 文件夹初始化、权限检查相关逻辑（完全保留）
    //==========================================
    private void init_file(){
        checkStoragePermission();
        checkManageExternalStoragePermission();
        File folder = new File("/storage/emulated/0/ReMoveBg-Config");
        File folder_fonts = new File("/storage/emulated/0/ReMoveBg-Config/fonts");

        boolean success = folder.mkdir();
        if(success){
            ToastUtil.showToast(this, "软件配置文件夹创建成功！", Toast.LENGTH_SHORT);
            ToastUtil.showToast(this, folder.getAbsolutePath(), Toast.LENGTH_SHORT);
        }
        File folder_loglos = new File("/storage/emulated/0/ReMoveBg-Config/loglos");
        boolean success_loglos = folder_loglos.mkdir();
        if(success_loglos) {
            ToastUtil.showToast(this, "图案素材文件夹创建成功，请将图案图片放入该目录", Toast.LENGTH_LONG);
        }

        boolean success_fonts=folder_fonts.mkdir();
        if(success_fonts){
            Toast toast1 = Toast.makeText(this,
                    "软件文本字体文件夹创建成功，您可以下载喜欢的字体文件放入文件夹供文本编辑使用，文件夹路径为:" + folder_fonts.getAbsolutePath(), Toast.LENGTH_LONG);
            toast1.show();
            new Handler().postDelayed(toast1::cancel, 10000);
        }
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
    }

    private void checkManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
            }
        }
    }


    //======================工具方法=========================================
    private void cacheAndDisplayImage(Bitmap bitmap, String imagePath) {
        if (bitmap == null || imagePath == null) {
            ToastUtil.showToast(this, "图片加载失败", Toast.LENGTH_SHORT);
            return;
        }

        if (currentImageKey != null) {
            BitmapCache.getInstance().remove(currentImageKey);
        }

        currentImageKey = "img_" + System.currentTimeMillis();
        int orientation = FileUtils.getExifOrientation(imagePath);

        File file = new File(imagePath);
        String format = getImageFormat(imagePath);
        BitmapCache.ImageInfo imageInfo = new BitmapCache.ImageInfo(
                imagePath,
                bitmap.getWidth(),
                bitmap.getHeight(),
                format,
                file.length(),
                orientation
        );
        BitmapCache.getInstance().put(currentImageKey, bitmap, imageInfo);

        imageView.setImageBitmap(bitmap);
        selectedBitmap = bitmap;
        centerImage();
        imageView.setImageMatrix(matrix);
    }

    private String getImageFormat(String path) {
        String format = "未知格式";
        File file = new File(path);
        if (file.exists()) {
            String fileName = file.getName();
            if (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg")) {
                format = "JPEG";
            } else if (fileName.toLowerCase().endsWith(".png")) {
                format = "PNG";
            } else if (fileName.toLowerCase().endsWith(".gif")) {
                format = "GIF";
            }
        }
        return format;
    }

    private void setButtonsEnabled(boolean enabled) {
        btnPickImage.setEnabled(enabled);
        btnTakePicture.setEnabled(enabled);
        btnRemoveBg.setEnabled(enabled);
        btnChangeBg.setEnabled(enabled);
        btnSave.setEnabled(enabled);
        btnCrop.setEnabled(enabled);
        btnRemoveWatermark.setEnabled(enabled);
    }

    private void saveImage() {
        if (selectedBitmap == null) {
            ToastUtil.showToast(this, "背景去除失败！", Toast.LENGTH_LONG);
            return;
        }

        if (imagePath == null || imagePath.isEmpty()) {
            ToastUtil.showToast(this, "原文件路径无效！", Toast.LENGTH_LONG);
            return;
        }

        File originalFile = new File(imagePath);
        String originalFileName = originalFile.getName();
        String directoryPath = originalFile.getParent();

        String fileNameWithoutExtension = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf('.'));
        String newFileName = fileNameWithoutExtension + "_ReBG_" + System.currentTimeMillis() + fileExtension;

        File newFile = new File(directoryPath, newFileName);

        try (FileOutputStream out = new FileOutputStream(newFile)) {
            selectedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            ToastUtil.showToast(this, "去背景图片保存成功！路径：" + newFile.getAbsolutePath(), Toast.LENGTH_SHORT);

            cacheAndDisplayImage(selectedBitmap, newFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            ToastUtil.showToast(this, "去背景图片保存失败！", Toast.LENGTH_LONG);
        }
        new Handler().postDelayed(() -> {
            centerImage();
            imageView.setImageMatrix(matrix);
        }, 500);
    }


    //=================================功能方法=============================
    private void openCreateImageSettingsActivity() {
        Intent intent = new Intent(this, CreateImageSettingsActivity.class);
        startActivity(intent);
    }

    private void AddNewKey(){
        Intent intent = new Intent(MainActivity.this, KeyManagementActivity.class);
        startActivityForResult(intent, 11);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    // 注意：原拍照方法 openCamera 不再使用，但保留方法体以防其他引用，但不会有调用。
    private void openCamera() {
        // 已废弃，不再使用，但保留以防万一
    }

    private void removeBackground_http() {
        if (selectedBitmap != null) {
            setButtonsEnabled(false);
            RemoveBgApi.removeBackground(selectedBitmap, new RemoveBgApi.RemoveBgCallback() {
                @Override
                public void onSuccess(Bitmap result) {
                    setButtonsEnabled(true);
                    imageView.setImageBitmap(result);
                    selectedBitmap = result;
                    if(selectedBitmap!=null){
                        saveImage();
                    }
                }

                @Override
                public void onError() {
                    setButtonsEnabled(true);
                    ToastUtil.showToast(MainActivity.this, "图片背景去除错误！", Toast.LENGTH_LONG);
                }
            }, this);
        } else {
            ToastUtil.showToast(this, "请选择图片！", Toast.LENGTH_SHORT);
        }
    }

    private void handleRemoveWatermark() {
        if (selectedBitmap != null) {
            if(!ishow){
                ToastUtil.showToast(this, "模型正在加载......", Toast.LENGTH_SHORT);
            }

            if (currentImageKey != null) {
                Intent intent = new Intent(this, rebg_by_lama.class);
                intent.putExtra("imageKey", currentImageKey);
                startActivityForResult(intent, 33);
            }else{
                ToastUtil.showToast(this, "读取图片缓存失败！", Toast.LENGTH_SHORT);
            }
        }else{
            ToastUtil.showToast(this, "请先选择图片！", Toast.LENGTH_SHORT);
        }
    }

    private void openCropActivity() {
        if (selectedBitmap != null) {
            if (currentImageKey != null) {
                Intent intent = new Intent(this, UniversalCropActivity.class);
                intent.putExtra("imageKey", currentImageKey);
                startActivity(intent);
            } else {
                ToastUtil.showToast(this, "读取图片缓存失败！", Toast.LENGTH_SHORT);
            }
        } else {
            ToastUtil.showToast(this, "请先选择图片！", Toast.LENGTH_SHORT);
        }
    }

    private void changeBackground() {
        if (selectedBitmap != null) {
            if (currentImageKey != null) {
                Intent intent = new Intent(MainActivity.this, BackgroundPickerActivity.class);
                intent.putExtra("imageKey", currentImageKey);
                startActivity(intent);
            } else {
                ToastUtil.showToast(this, "读取图片缓存失败！", Toast.LENGTH_SHORT);
            }
        } else {
            ToastUtil.showToast(this, "请先选择图片！", Toast.LENGTH_SHORT);
        }
    }


    //=================================界面交互与权限回调=============================
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 11) {
            String selectedKey = data.getStringExtra("selectedKey");
            if (selectedKey != null) {
                ToastUtil.showToast(this, "选中的密钥: " + selectedKey, Toast.LENGTH_SHORT);
            }
            if(selectedBitmap!=null){
                cacheAndDisplayImage(selectedBitmap, imagePath);
            }
        }
        else if (resultCode == RESULT_OK) {
            if (requestCode == TAKE_PICTURE_REQUEST) {
                // 拍照已废弃，但保留以防有残留逻辑
                // 实际上不会执行到这里，因为按钮已改为超分辨
            }
            else if (requestCode == PICK_IMAGE_REQUEST && data != null) {
                Uri imageUri = data.getData();
                Bitmap bitmap = FileUtils.getBitmapFromUri(imageUri, this);
                if (bitmap != null) {
                    String imagePath1 = FileUtils.getPathFromUri(imageUri, this);
                    imagePath=imagePath1;
                    bitmap = FileUtils.rotateImageIfRequired(bitmap, imagePath1);
                    cacheAndDisplayImage(bitmap, imagePath);
                } else {
                    ToastUtil.showToast(this, "Failed to load image", Toast.LENGTH_SHORT);
                }
            } else if (requestCode == REQUEST_CHANGE_BACKGROUND && data != null) {
                String resultPath = data.getStringExtra("resultPath");
                imagePath=resultPath;
                Log.d("改变背景后的路径：",imagePath);
                if (resultPath != null) {
                    Bitmap resultBitmap = BitmapFactory.decodeFile(resultPath);
                    if (resultBitmap != null) {
                        cacheAndDisplayImage(resultBitmap, imagePath);
                    } else {
                        ToastUtil.showToast(this, "Failed to load result image", Toast.LENGTH_SHORT);
                    }
                }
            }
        } else if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    handleRemoveWatermark();
                } else {
                    ToastUtil.showToast(this, "需要管理外部存储权限", Toast.LENGTH_SHORT);
                }
            }
        }
        else if (requestCode == 33) {
            String flag = data.getStringExtra("isload");
            if (flag != null && flag.equals("isload")) {
                ishow = true;
                Log.d("模型加载状态：", "模型已加载");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            // 不再需要相机权限，但保留不影响
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
            } else {
                ToastUtil.showToast(this, "Storage permission is required to save image", Toast.LENGTH_SHORT);
            }
        } else if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
            } else {
                ToastUtil.showToast(this, "Manage external storage permission is required", Toast.LENGTH_SHORT);
            }
        }
    }
}
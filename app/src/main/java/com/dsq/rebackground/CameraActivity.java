package com.dsq.rebackground;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dsq.rebackground.utils.ToastUtil;

public class CameraActivity extends AppCompatActivity {

    private ImageView imageView;
    private Button btnTakePicture;
    private static final int TAKE_PICTURE_REQUEST = 2;
    private Bitmap capturedImage;
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        imageView = findViewById(R.id.imageView);
        btnTakePicture = findViewById(R.id.btn_take_picture);

        btnTakePicture.setOnClickListener(v -> {
            // Check if the camera permission is granted
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            } else {
                openCamera();
            }
        });
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // Ensure there is a camera app available to handle the intent
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, TAKE_PICTURE_REQUEST);
            } else {
                ToastUtil.showToast(this, "No camera app found", Toast.LENGTH_SHORT);
            }
        } else {
            // If permission is not granted, show a message to the user
            ToastUtil.showToast(this, "Camera permission is required", Toast.LENGTH_SHORT);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("权限拒绝")
                        .setMessage("您拒绝了相机权限，无法使用拍照功能。请在设置中授予相机权限。")
                        .setPositiveButton("确定", null)
                        .setNegativeButton("去设置", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 打开应用的权限设置页面
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == TAKE_PICTURE_REQUEST) {
                capturedImage = (Bitmap) data.getExtras().get("data");
                imageView.setImageBitmap(capturedImage);
            }
        }
    }
}

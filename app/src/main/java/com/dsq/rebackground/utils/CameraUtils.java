package com.dsq.rebackground.utils;

import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;

import com.dsq.rebackground.MainActivity;

public class CameraUtils {

    public static void openCamera(Context context, int requestCode) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            ((MainActivity) context).startActivityForResult(intent, requestCode);
        }
    }
}

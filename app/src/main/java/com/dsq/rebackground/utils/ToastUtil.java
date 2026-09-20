package com.dsq.rebackground.utils;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.dsq.rebackground.R;

public class ToastUtil {

    // ---------- 普通 Toast ----------
    public static void showToast(Context context, String message) {
        showToast(context, message, Toast.LENGTH_SHORT);
    }

    public static void showLongToast(Context context, String message) {
        showToast(context, message, Toast.LENGTH_LONG);
    }

    public static void showToast(Context context, String message, int duration) {
        if (context == null) return;
        View layout = LayoutInflater.from(context).inflate(R.layout.custom_toast, null);
        TextView textView = layout.findViewById(android.R.id.message);
        textView.setText(message);
        Toast toast = new Toast(context);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    public static void showToast(Context context, int resId) {
        showToast(context, context.getString(resId), Toast.LENGTH_SHORT);
    }

    public static void showLongToast(Context context, int resId) {
        showToast(context, context.getString(resId), Toast.LENGTH_LONG);
    }

    public static void showToast(Context context, int resId, int duration) {
        showToast(context, context.getString(resId), duration);
    }

    // ---------- 彩色 Toast ----------
    public static void showColoredToast(Context context, String message, int color) {
        showColoredToast(context, message, color, Toast.LENGTH_SHORT);
    }

    public static void showColoredToast(Context context, String message, int color, int duration) {
        if (context == null) return;
        View layout = LayoutInflater.from(context).inflate(R.layout.custom_toast, null);
        TextView textView = layout.findViewById(android.R.id.message);
        SpannableString spannable = new SpannableString(message);
        spannable.setSpan(new ForegroundColorSpan(color), 0, message.length(), 0);
        textView.setText(spannable);
        Toast toast = new Toast(context);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    // ---------- 红色错误 Toast ----------
    public static void showErrorToast(Context context, String message) {
        showColoredToast(context, message, Color.RED);
    }

    public static void showErrorToast(Context context, String message, int duration) {
        showColoredToast(context, message, Color.RED, duration);
    }
}
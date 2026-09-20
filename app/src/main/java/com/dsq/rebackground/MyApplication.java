package com.dsq.rebackground;
// 文件：MyApplication.java
import android.app.Application;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (!Python.isStarted()) {  // 检查是否已初始化
            Python.start(new AndroidPlatform(this));
        }
    }
}
package com.dsq.rebackground.realsr;


import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.text.format.Formatter;
import com.dsq.rebackground.R;

public class DeviceInfo {

    public static String getInfo(Context context) {
        ActivityManager mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        //获得MemoryInfo对象
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        //获得系统可用内存，保存在MemoryInfo对象上
        mActivityManager.getMemoryInfo(memoryInfo);
        long memSize = memoryInfo.totalMem;
        String availMemStr = Formatter.formatFileSize(context, memSize);

        return String.format("%s:\t%s, %s:\t%s, %s:\t%s, %s:\t%s",
                context.getString(R.string.model), Build.MODEL,
                context.getString(R.string.system_version), Build.VERSION.RELEASE,
                context.getString(R.string.cpu_model), Build.HARDWARE,
                context.getString(R.string.ram_size), availMemStr

        );
    }

    public static String getConfigStr(boolean cpu, int tile) {
        String str = cpu ? "CPU" : "GPU";
        if (tile > 0)
            return str + ", tile=" + tile;
        return str;
    }


}

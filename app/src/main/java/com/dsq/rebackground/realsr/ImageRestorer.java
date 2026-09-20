package com.dsq.rebackground.realsr;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * 图片修复工具：先使用 Real-ESRGAN 超分（2倍），再缩小回原始尺寸，
 * 实现“只修复、不放大”的效果。
 */
public class ImageRestorer {

    private static final String TAG = "ImageRestorer";

    /**
     * 确保可执行文件具有执行权限
     * @param workDir 工作目录
     * @param executables 可执行文件名列表
     */
    private static void ensureExecutable(File workDir, String... executables) {
        for (String exe : executables) {
            try {
                ProcessBuilder pb = new ProcessBuilder("chmod", "+x", exe);
                pb.directory(workDir);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int code = p.waitFor();
                if (code != 0) {
                    Log.w(TAG, "chmod +x " + exe + " 返回非零: " + code);
                } else {
                    Log.d(TAG, "chmod +x " + exe + " 成功");
                }
            } catch (IOException | InterruptedException e) {
                Log.w(TAG, "chmod +x " + exe + " 失败", e);
            }
        }
    }

    /**
     * 执行修复
     * @param context       上下文，用于获取缓存目录
     * @param inputPath     输入图片的绝对路径
     * @param outputPath    输出图片的绝对路径（最终结果）
     * @param modelName     超分模型名称，如 "models-Real-ESRGANv3-anime"
     * @param scale         超分倍数，建议 2（修复效果最佳）
     * @param resizeFilter  缩小滤镜，推荐 "Lanczos"
     * @return  true 表示修复成功，false 表示失败
     */
    public static boolean restore(Context context, String inputPath, String outputPath,
                                  String modelName, int scale, String resizeFilter) {
        // 工作目录（存放可执行文件和模型）
        String workDir = context.getCacheDir().getAbsolutePath() + "/realsr";
        File workDirFile = new File(workDir);
        if (!workDirFile.exists() || !workDirFile.isDirectory()) {
            Log.e(TAG, "工作目录不存在: " + workDir);
            return false;
        }

        // -------- 确保可执行文件具有执行权限 --------
        ensureExecutable(workDirFile, "realsr-ncnn", "magick");

        // 获取输入图片原始尺寸
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(inputPath, opts);
        int origWidth = opts.outWidth;
        int origHeight = opts.outHeight;
        if (origWidth <= 0 || origHeight <= 0) {
            Log.e(TAG, "无法读取输入图片尺寸: " + inputPath);
            return false;
        }

        // 临时输出文件（超分后的大图）
        String tempOutput = workDir + "/temp_restore_sr.png";
        File tempFile = new File(tempOutput);
        if (tempFile.exists()) tempFile.delete();

        // -------- 第一步：超分（放大） --------
        ProcessBuilder pb = new ProcessBuilder(
                "./realsr-ncnn",
                "-i", inputPath,
                "-o", tempOutput,
                "-m", modelName,
                "-s", String.valueOf(scale)
        );
        pb.directory(workDirFile);
        pb.environment().put("LD_LIBRARY_PATH", workDir);
        pb.redirectErrorStream(true);

        Log.d(TAG, "执行超分命令: " + String.join(" ", pb.command()));
        try {
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                Log.e(TAG, "超分失败，退出码: " + exitCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "超分执行异常", e);
            return false;
        }

        // 检查临时文件是否生成
        if (!tempFile.exists() || tempFile.length() == 0) {
            Log.e(TAG, "超分后未生成输出文件");
            return false;
        }

        // -------- 第二步：缩小到原始尺寸 --------
        pb = new ProcessBuilder(
                "./magick",
                tempOutput,
                "-filter", resizeFilter,
                "-resize", origWidth + "x" + origHeight + "!",
                outputPath
        );
        pb.directory(workDirFile);
        pb.environment().put("LD_LIBRARY_PATH", workDir);
        pb.redirectErrorStream(true);

        Log.d(TAG, "执行缩小命令: " + String.join(" ", pb.command()));
        try {
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                Log.e(TAG, "缩小失败，退出码: " + exitCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "缩小执行异常", e);
            return false;
        }

        // 检查最终输出是否存在
        File outFile = new File(outputPath);
        if (!outFile.exists() || outFile.length() == 0) {
            Log.e(TAG, "最终输出文件不存在");
            return false;
        }

        // 删除临时文件
        tempFile.delete();

        Log.d(TAG, "修复成功，输出: " + outputPath);
        return true;
    }

    /**
     * 使用默认参数（最佳推荐）进行修复
     * @param context     上下文
     * @param inputPath   输入图片路径
     * @param outputPath  输出图片路径
     * @return  true 成功
     */
    public static boolean restoreWithDefault(Context context, String inputPath, String outputPath) {
        return restore(context, inputPath, outputPath,
                "models-Real-ESRGANv3-anime",   // 最佳通用模型
                2,                               // 2倍超分，修复效果最稳定
                "Lanczos"                        // 最锐利的缩小滤镜
        );
    }
}
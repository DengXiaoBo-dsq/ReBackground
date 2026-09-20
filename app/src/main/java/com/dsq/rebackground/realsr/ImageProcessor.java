package com.dsq.rebackground.realsr;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ImageProcessor {
    private static final String TAG = "ImageProcessor";
    private final ExecutorService executorService;
    private Process currentProcess;
    private Future<?> currentTask;
    private volatile boolean taskCancelled;

    public interface ProcessCallback {
        void onProgress(String line);
        void onCompleted(String result, boolean success);
        void onError(String error);
    }

    public ImageProcessor() {
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void executeCommand(String command, String workingDir, ProcessCallback callback) {
        cancelCurrentTask();
        taskCancelled = false;
        currentTask = executorService.submit(() -> {
            runProcess(command, workingDir, callback);
        });
    }

    private void runProcess(String command, String workingDir, ProcessCallback callback) {
        StringBuilder resultBuilder = new StringBuilder();
        boolean success = false;

        try {
            Log.d(TAG, "Executing command: " + command);

            // 解析命令，提取可执行文件和参数
            String[] parts = command.split("\\s+");
            if (parts.length == 0) {
                callback.onError("Empty command");
                return;
            }

            // 构建命令列表，可执行文件使用绝对路径
            List<String> cmdList = new ArrayList<>();
            String exeName = parts[0];
            // 如果命令以 ./ 开头，则替换为 workingDir 下的绝对路径
            if (exeName.startsWith("./")) {
                exeName = workingDir + "/" + exeName.substring(2);
            } else if (!exeName.startsWith("/")) {
                exeName = workingDir + "/" + exeName;
            }
            cmdList.add(exeName);
            for (int i = 1; i < parts.length; i++) {
                cmdList.add(parts[i]);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
            processBuilder.directory(new File(workingDir));
            processBuilder.redirectErrorStream(true);

            // 设置环境变量
            Map<String, String> env = processBuilder.environment();
            env.put("LD_LIBRARY_PATH", workingDir);
            env.put("PATH", workingDir + ":" + System.getenv("PATH"));

            Log.d(TAG, "Running: " + String.join(" ", cmdList));

            synchronized (this) {
                currentProcess = processBuilder.start();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Task interrupted");
                }
                // 过滤无用日志
                if (line.contains("unused DT entry")) continue;
                if (line.startsWith("CPU Group:")) continue;
                if (line.startsWith("(last_midr")) continue;
                if (line.startsWith("Error tunning info")) continue;

                Log.d(TAG, line);
                callback.onProgress(line);
                resultBuilder.append(line).append("\n");
            }

            int exitCode = currentProcess.waitFor();
            success = (exitCode == 0);
            Log.d(TAG, "Process finished with exit code: " + exitCode);

        } catch (InterruptedException e) {
            Log.w(TAG, "Process interrupted");
            callback.onError("Process interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Error executing process", e);
            callback.onError(e.getMessage());
        } finally {
            synchronized (this) {
                if (currentProcess != null) {
                    currentProcess.destroy();
                    currentProcess = null;
                }
            }
            if (!taskCancelled) {
                callback.onCompleted(resultBuilder.toString(), success);
            }
        }
    }

    public void cancelCurrentTask() {
        taskCancelled = true;
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
        synchronized (this) {
            if (currentProcess != null) {
                currentProcess.destroy();
                currentProcess = null;
            }
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
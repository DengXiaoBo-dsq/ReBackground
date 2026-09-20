package com.dsq.rebackground.realsr;

import static com.dsq.rebackground.realsr.UriUntils.getFileName;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.dsq.rebackground.R;
import com.dsq.rebackground.utils.ToastUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RealSRMainActivity extends AppCompatActivity {
    private static final int SELECT_IMAGE = 1, SELECT_MULTI_IMAGE = 2;
    private static final int MY_PERMISSIONS_REQUEST = 100;
    private static final String CMD_CP_LIB_OPENCL = " if [ -e /system/vendor/lib64/libOpenCL.so ]; then cp /system/vendor/lib64/libOpenCL.so ./; elif [ -e /system/lib64/libOpenCL.so ]; then cp /system/lib64/libOpenCL.so ./; elif  [ -e /system/vendor/lib/libOpenCL.so ]; then cp /system/vendor/lib/libOpenCL.so ./; elif [ -e /system/lib/libOpenCL.so ]; then cp /system/lib/libOpenCL.so ./; else echo \"[warning]libOpenCL.so not find\"; fi; if [ -e /system/vendor/lib/egl/libGLES_mali.so ]; then cp /system/vendor/lib/egl/libGLES_mali.so ./; elif [ -e /system/lib/egl/libGLES_mali.so ]; then cp /system/lib/egl/libGLES_mali.so ./; else echo \"[warning]libGLES_mali.so not find\"; fi";
    private static final String CMD_RESET_CACHE = CMD_CP_LIB_OPENCL
            + ";rm -f *.cache;rm -f */*.cache;chmod +x *; echo Cache has been reset.;ls";
    private int selectCommand = 0;
    private String threadCount = "";
    private SubsamplingScaleImageView imageView;
    private TextView logTextView;
    private boolean initProcess;
    private final String galleryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            + File.separator + "RealSR";
    private File outputFile, outputGif, inputFile, titleFile;

    private static final String TAG = "RealSRMainActivity";

    /**
     * String dir 是应用的工作目录，用于存放临时文件和执行命令。
     * 路径：/data/data/.../cache/realsr/realsr
     */
    private String dir;
    private String modelName = "SR";
    private SearchView searchView;
    private MenuItem menuProgress;
    private Spinner spinner;
    private boolean newTask;
    private int format, name, name2, notify, dirOutputFormat;
    private String BUSY, ERR, DONE;
    private String outputSavePath = "";
    private String inputFileName = "";

    private String[] formats;

    private String[] command = null;
    private String log = "";
    private CommandListManager commandListManager;
    private ProgressLogHelper progressLogHelper;

    private final String[] bench_mark_commands = new String[] {
            "./realsr-ncnn -c 46 -i img/PM5544.jpeg -o input.png  -m models-Real-ESRGAN",
            "./realsr-ncnn -c 46 -i input.png -o output.png  -m models-Real-ESRGANv3-anime -s 4"
    };
    private int tileSize;
    private boolean useCPU;
    private int mnnBackend;
    private boolean keepScreen;
    private boolean useMultFiles;
    private boolean prePng;
    private boolean preFrame;
    private boolean autoSave;
    private boolean showSearchView, showFinalCommand;
    private String savePath = galleryPath;

    private static final int NOTIFY_ID = 1;
    private static final String CHANNEL_ID_RESULT = "channel_result";

    private void sendNotification(Context mContext, String text, boolean force) {
        if (!force && (notify == 0 || notify == 3))
            return;

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (text == null) {
            notificationManager.cancel(NOTIFY_ID);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_RESULT,
                    getString(R.string.notification_channel_result),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Shows result of image processing tasks");
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, RealSRMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext, CHANNEL_ID_RESULT);
        mBuilder.setContentTitle(getString(R.string.realsr_app_name))
                .setContentText(text)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        Notification notification = mBuilder.build();
        notificationManager.notify(NOTIFY_ID, notification);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_realsr_main, menu);
        menuProgress = menu.findItem(R.id.progress);
        if (initProcess) {
            initProcess = false;
            if (menuProgress != null) menuProgress.setTitle("");
            Log.i(TAG, "onCreateOptionsMenu() done");
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        final String q;
        String imageName = "/output.png";
        boolean bench_mark_mode = false;
        int v = item.getItemId();
        if (v == R.id.progress) {
            stopCommand();
            return false;
        } else if (v == R.id.menu_share) {
            if (inputIsGifAnimation)
                shareImage("output.gif");
            else
                shareImage("output.png");
            return false;
        } else if (v == R.id.menu_avir2) {
            q = "./resize-ncnn -i input.png -o output.png  -m avir -s 0.5";
        } else if (v == R.id.menu_nearest4) {
            q = "./resize-ncnn -i input.png -o output.png  -m nearest -s 4";
        } else if (v == R.id.menu_de_nearest) {
            q = "./resize-ncnn -i input.png -o output.png  -m de-nearest";
        } else if (v == R.id.menu_de_nearest2) {
            q = "./resize-ncnn -i input.png -o output.png  -m de-nearest2";
        }  else if (v == R.id.menu_perfectpixel || v == R.id.menu_perfectpixel1 || v == R.id.menu_perfectpixel2) {
            performRestore();
            return true;
        }else if (v == R.id.menu_magick2) {
            q = "./magick input.png -resize 50% output.png";
        } else if (v == R.id.menu_magick3) {
            q = "./magick input.png -resize 33.33% output.png";
        } else if (v == R.id.menu_magick4) {
            q = "./magick input.png -resize 25% output.png";
        } else if (v == R.id.menu_out2in) {
            if (inputIsGifAnimation) {
                ToastUtil.showToast(this, R.string.not_support_animation, Toast.LENGTH_SHORT);
                return false;
            } else {
                q = "cp output.png input.png";
                imageName = "/input.png";
            }
        } else if (v == R.id.menu_in) {
            q = "in";
        } else if (v == R.id.menu_out) {
            q = "out";
        } else if (v == R.id.menu_help) {
            q = "help";
        } else if (v == R.id.menu_reset_cache) {
            q = CMD_RESET_CACHE;
            imageName = "";
        } else if (v == R.id.menu_bench_mark) {
            String append_param = "";
            if (tileSize > 0)
                append_param = " -t " + tileSize;
            if (useCPU)
                append_param += (" -g -1");

            append_param += ";";
            q = "rm -rf *.png; ls *.png; " + bench_mark_commands[0] + append_param + bench_mark_commands[1]
                    + append_param;

            imageName = "/img/realsr.png";
            bench_mark_mode = true;
            imageView.setVisibility(View.GONE);
            if (keepScreen) {
                logTextView.setKeepScreenOn(true);
            }
        } else if (v == R.id.menu_dir_batch) {
            Intent intent = new Intent(this, DirectoryProcessActivity.class);
            startActivity(intent);
            return true;
        } else
            q = "";

        if (!run_fake_command(q)) {
            stopCommand();
            String finalImageName = imageName;
            boolean final_bench_mark_mode = bench_mark_mode;
            new Thread(() -> {
                if (q.equals(CMD_RESET_CACHE)) {
                    AssetsCopyer.releaseAssets(this, "realsr", dir, false);
                }

                run20(q, final_bench_mark_mode, false);
                final File finalfile = new File(dir + finalImageName);
                if (finalfile.exists() && (!finalfile.isDirectory())) {
                    runOnUiThread(() -> {
                        imageView.setVisibility(View.VISIBLE);
                        imageView.setImage(ImageSource.uri(finalfile.getAbsolutePath()));
                        logTextView.setKeepScreenOn(false);
                    });
                } else {
                    runOnUiThread(() -> imageView.setVisibility(View.GONE));
                }
            }).start();
        }

        return super.onOptionsItemSelected(item);
    }

    // 删除文件或者目录
    public static void deleteFile(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteFile(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        f.delete();
    }

    public void shareImage(String path) {
        Intent share_intent = new Intent();

        Uri contentUri = null;
        File file = null;
        if (!outputSavePath.isEmpty()) {
            file = new File(outputSavePath);
            if (file.exists()) {
                contentUri = FileProvider.getUriForFile(this,
                        "com.dsq.rebackground.fileprovider",
                        file);
            }
        }

        if (contentUri == null) {
            file = new File(dir, path);
            if (file.exists()) {
                contentUri = FileProvider.getUriForFile(this,
                        "com.dsq.rebackground.fileprovider",
                        file);
            }
        }

        if (contentUri != null) {
            String suffix = file.getName().replaceFirst(".+\\.([^.]+)$", "$1").toLowerCase(Locale.ROOT);
            switch (suffix) {
                case "png":
                    share_intent.setType("image/png");
                    break;
                case "jpg":
                    share_intent.setType("image/jpg");
                    break;
                case "webp":
                    share_intent.setType("image/webp");
                    break;
                case "heif":
                    share_intent.setType("image/heif");
                    break;
                case "gif":
                    share_intent.setType("image/gif");
                    break;
                default:
                    share_intent.setType("image/*");
                    break;
            }

            share_intent.setAction(Intent.ACTION_SEND);
            share_intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            share_intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            Log.i(TAG, "shareImage() uri = " + contentUri);
            startActivity(Intent.createChooser(share_intent, "Share"));

        } else {
            ToastUtil.showToast(getApplicationContext(), R.string.output_not_exits, Toast.LENGTH_SHORT);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * 根据命令字符串生成对应的中文功能描述
     */
    private String getCommandDescription(String command) {
        if (command == null || command.trim().isEmpty()) return "";

        String cmd = command.trim();
        String program = CommandListManager.getProgramType(command);
        if (program.isEmpty()) return "";

        String model = "";
        if (cmd.matches(".*\\s-m\\s+\\S*models-([^\\s]+).*")) {
            model = cmd.replaceFirst(".*\\s-m\\s+\\S*models-([^\\s]+).*", "$1");
        } else if (cmd.matches(".*\\s-m\\s+\\S*\\.mnn.*")) {
            model = "MNN";
        } else if (cmd.startsWith("./Anime4k")) {
            model = "Anime4K";
        } else if (cmd.startsWith("./magick")) {
            if (cmd.contains("-filter")) {
                model = cmd.replaceFirst(".*-filter\\s+(\\w+).*", "$1");
            } else {
                model = "Magick";
            }
        } else if (cmd.startsWith("./resize-ncnn")) {
            if (cmd.matches(".*\\s-m\\s+(\\w+).*")) {
                model = cmd.replaceFirst(".*\\s-m\\s+(\\w+).*", "$1");
            }
        }

        switch (program) {
            case CommandListManager.PROGRAM_REALSR:
                if (model.contains("anime")) return "动漫超分，细节增强";
                else if (model.contains("v3")) return "最新版通用超分，画质提升";
                else if (model.contains("SourceBook")) return "老照片修复，还原细节";
                else return "通用超分，清晰放大";

            case CommandListManager.PROGRAM_SRMD:
                return "超分+降噪，适合模糊照片";

            case CommandListManager.PROGRAM_WAIFU2X:
                return "经典动漫超分，可选降噪";

            case CommandListManager.PROGRAM_REALCUGAN:
                if (model.contains("nose")) return "无降噪动漫超分";
                else if (model.contains("se")) return "动漫超分+轻度降噪";
                else if (model.contains("pro")) return "专业版动漫超分，细节丰富";
                else return "动漫超分，线条锐利";

            case CommandListManager.PROGRAM_MNNSR:
                return "MNN超分，可自定义模型";

            case CommandListManager.PROGRAM_RESIZE:
                if (model.equals("bicubic")) return "双三次插值，平滑放大";
                else if (model.equals("bilinear")) return "双线性插值，简单放大";
                else if (model.equals("nearest")) return "最近邻插值，像素块状";
                else if (model.equals("avir")) return "AVIR插值，边缘锐化";
                else if (model.equals("de-nearest")) return "去最近邻插值，抗锯齿";
                else if (model.equals("perfectpixel")) return "完美像素缩放（可能失效）";
                else return "插值缩放";

            case CommandListManager.PROGRAM_MAGICK:
                if (model.contains("Lanczos")) return "Lanczos滤镜，高质量缩放";
                else if (model.contains("Mitchell")) return "Mitchell滤镜，柔和缩放";
                else return "ImageMagick缩放";

            case CommandListManager.PROGRAM_ANIME4K:
                return "动漫线条增强，速度极快";

            default:
                return "";
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        formats = getResources().getStringArray(R.array.format);
        BUSY = getResources().getString(R.string.busy);
        ERR = getString(R.string.notification_fail);
        DONE = getString(R.string.done);

        SharedPreferences mySharePerferences = getSharedPreferences("config", Activity.MODE_PRIVATE);
        tileSize = mySharePerferences.getInt("tileSize", 0);
        threadCount = mySharePerferences.getString("threadCount", "");
        keepScreen = mySharePerferences.getBoolean("keepScreen", false);

        useMultFiles = mySharePerferences.getBoolean("useMultFiles", false);
        prePng = mySharePerferences.getBoolean("PrePng", true);
        preFrame = mySharePerferences.getBoolean("PreFrame", true);
        useCPU = mySharePerferences.getBoolean("useCPU", false);
        mnnBackend = mySharePerferences.getInt("mnnBackend", 3);
        autoSave = mySharePerferences.getBoolean("autoSave", false);
        showSearchView = mySharePerferences.getBoolean("showSearchView", false);
        if (showSearchView)
            searchView.setVisibility(View.VISIBLE);
        else
            searchView.setVisibility(View.GONE);

        showFinalCommand = mySharePerferences.getBoolean("showFinalCommand", false) && showSearchView;

        notify = mySharePerferences.getInt("notify", 0);

        format = mySharePerferences.getInt("format", 0);
        dirOutputFormat = mySharePerferences.getInt("dirOutputFormat", 0);
        name = mySharePerferences.getInt("name", 0);
        name2 = mySharePerferences.getInt("name2", 0);

        String[] presetLabels = getResources().getStringArray(R.array.style_array);
        boolean useCustomLabel = mySharePerferences.getBoolean("useCustomLabel", false);
        commandListManager = new CommandListManager(presetLabels,
                mySharePerferences.getString("extraPath", "").trim(),
                mySharePerferences.getString("extraCommand", "").trim(),
                mySharePerferences.getString("classicalFilters", getString(R.string.default_classical_filters))
                        .split("\\s+"),
                mySharePerferences.getString("magickFilters", getString(R.string.default_magick_filters))
                        .split("\\s+"));
        commandListManager.loadCustomLabels(mySharePerferences.getString("customLabels", ""));

        Set<String> hiddenPrograms = mySharePerferences.getStringSet("hiddenPrograms", new HashSet<String>());
        command = commandListManager.getFilteredCommands(hiddenPrograms);
        String[] displayLabels = commandListManager.getFilteredLabels(hiddenPrograms, useCustomLabel);

        String[] labelsWithDesc = new String[displayLabels.length];
        for (int i = 0; i < displayLabels.length; i++) {
            String desc = getCommandDescription(command[i]);
            if (!desc.isEmpty()) {
                labelsWithDesc[i] = displayLabels[i] + " - " + desc;
            } else {
                labelsWithDesc[i] = displayLabels[i];
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labelsWithDesc);
        spinner.setAdapter(adapter);

        if (selectCommand >= command.length)
            selectCommand = Math.max(0, command.length - 1);
        spinner.setSelection(selectCommand);
        Log.d(TAG, "onResume: selectCommand=" + selectCommand + ", command.length=" + command.length);

        savePath = mySharePerferences.getString("savePath", "");
        if (savePath.isEmpty())
            savePath = galleryPath;
        try {
            File file = new File(savePath);
            if (file.isFile())
                file.delete();
            if (!file.exists())
                file.mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void readFileFromShare() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            deleteFile(inputFile);
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            inputFileName = getFileName(uri, this);
            assert inputFileName != null;
            inputFileName = inputFileName.replaceFirst("\\.[^.]+$", "");
            Log.i(TAG, "input file name: " + inputFileName);
            whiteFileFromUri(uri, "");

        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            handleSelectedImages(imageUris);
        }
    }

    private boolean whiteFileFromUri(Uri uri, String path) {
        if (uri != null) {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (null != in)
                    saveInputImage(in, path);
                else
                    ToastUtil.showToast(this, R.string.share_is_null, Toast.LENGTH_SHORT);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private ProcessingService processingService;
    private boolean isBound = false;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ProcessingService.LocalBinder binder = (ProcessingService.LocalBinder) service;
            processingService = binder.getService();
            isBound = true;
            Log.d(TAG, "ProcessingService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            Log.d(TAG, "ProcessingService disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_realsr_main);

        setTitle(getString(R.string.realsr_app_name));

        Intent serviceIntent = new Intent(this, ProcessingService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        imageView = findViewById(R.id.photo_view);
        logTextView = findViewById(R.id.tv_log);
        searchView = findViewById(R.id.serarch_view);

        logTextView.setText("");

        SharedPreferences mySharePerferences = getSharedPreferences("config", Activity.MODE_PRIVATE);
        prePng = mySharePerferences.getBoolean("PrePng", true);
        preFrame = mySharePerferences.getBoolean("PreFrame", true);

        String defaultCommand = mySharePerferences.getString("defaultCommand", "");
        searchView.setQuery(defaultCommand, false);

        // ★★★ 统一目录设置 ★★★
        String baseDir = getFilesDir().getAbsolutePath() + "/realsr";
        File baseFile = new File(baseDir);
        if (!baseFile.exists()) {
            baseFile.mkdirs();
        }
        AssetsCopyer.releaseAssets(this, "realsr", baseDir, false);
        dir = baseDir + "/realsr";
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        Log.d(TAG, "Working directory: " + dir);

        // 赋予可执行权限
        run_command("chmod +x " + dir + " -R");

        int orientation = mySharePerferences.getInt("ORIENTATION", 0);
        if (orientation == 1) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else if (orientation == 2)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else if (orientation == 3) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        outputFile = new File(dir, "output.png");
        outputGif = new File(dir, "output.gif");
        inputFile = new File(dir, "input.png");
        titleFile = new File(dir, "img/realsr.png");

        imageView.setVisibility(View.GONE);

        spinner = findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectCommand = pos;
                Log.i(TAG, "setOnItemSelectedListener select " + pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        selectCommand = mySharePerferences.getInt("selectCommand", 0);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {

                String q = searchView.getQuery().toString().trim();

                if (!run_fake_command(q)) {
                    stopCommand();
                    run20(q, false, true);
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.trim().length() < 2) {
                    if (menuProgress != null)
                        menuProgress.setTitle("");
                    return true;
                }
                if (imageView.getVisibility() == View.VISIBLE)
                    imageView.setVisibility(View.GONE);
                return true;
            }
        });
        findViewById(R.id.btn_open).setOnClickListener(view -> {
            if (useMultFiles) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, SELECT_MULTI_IMAGE);

            } else {

                Intent i = new Intent(Intent.ACTION_PICK);
                i.setType("image/*");
                startActivityForResult(i, SELECT_IMAGE);
            }
        });

        findViewById(R.id.btn_save).setOnClickListener(view -> {
            File f = inputIsGifAnimation ? outputGif : outputFile;

            if (!f.exists()) {
                ToastUtil.showToast(this, R.string.output_not_exits, Toast.LENGTH_SHORT);
                return;
            } else if (f.isDirectory()) {
                File[] files = f.listFiles();
                if (files == null || files.length == 0) {
                    ToastUtil.showToast(this, R.string.output_not_exits, Toast.LENGTH_SHORT);
                } else {
                    ToastUtil.showToast(this, R.string.output_is_dir, Toast.LENGTH_SHORT);
                }
                return;
            }
            run_command(saveOutputCmd());
            checkSaveOutput();
        });

        findViewById(R.id.btn_run).setOnClickListener(view -> {
            if (menuProgress != null) menuProgress.setTitle("");
            {
                stopCommand();
                log = "";
                StringBuffer cmd;

                if (selectCommand >= command.length) {

                    cmd = new StringBuffer(spinner.getSelectedItem().toString());
                    Log.w(TAG, "btn_run.onClick select=" + selectCommand + ", length=" + command.length + " text=" + cmd);

                    if (run_fake_command(cmd.toString()))
                        return;
                } else {
                    final String cmd_head = command[selectCommand];
                    cmd = new StringBuffer(cmd_head);
                    if (cmd_head.matches("./(realsr|srmd|waifu2x|realcugan|mnnsr)-ncnn.+")) {
                        if (tileSize > 0 && !cmd_head.contains(" -t "))
                            cmd.append(" -t ").append(tileSize);
                        if (!threadCount.isEmpty() && !cmd_head.contains(" -j "))
                            cmd.append(" -j ").append(threadCount);
                        if (useCPU && !cmd_head.startsWith("./srmd") && !cmd_head.startsWith("./mnnsr")
                                && !cmd_head.contains(" -g "))
                            cmd.append(" -g -1");
                        if (cmd_head.startsWith("./mnnsr") && !cmd_head.contains(" -b ")) {
                            cmd.append(" -b ").append(mnnBackend);
                        }
                    }
                }

                deleteFile(outputFile);
                if (inputIsGifAnimation) {
                    outputGif.delete();
                    outputFile.mkdir();
                }
                if (keepScreen) {
                    logTextView.setKeepScreenOn(true);
                }

                if (showFinalCommand) {
                    searchView.setQuery(cmd.toString(), false);
                    ToastUtil.showToast(this, cmd.toString(), Toast.LENGTH_SHORT);
                }

                run20(cmd.toString(), false, true);
            }
        });

        findViewById(R.id.btn_setting).setOnClickListener(view -> {
            Intent intent = new Intent(this, SettingActivity.class);
            this.startActivity(intent);
            overridePendingTransition(0, android.R.anim.slide_out_right);
        });

        requirePremision();

        if (menuProgress != null)
            menuProgress.setTitle("");
        else
            initProcess = true;

        readFileFromShare();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private void requirePremision() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    MY_PERMISSIONS_REQUEST);

        } else {
            File file = new File(savePath);
            if (file.isFile())
                file.delete();
            if (!file.exists())
                file.mkdirs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ToastUtil.showToast(RealSRMainActivity.this, "Permission Denied", Toast.LENGTH_SHORT);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void handleSelectedImages(List<Uri> uris) {
        if (uris == null || uris.isEmpty())
            return;
        deleteFile(inputFile);
        if (uris.size() == 1) {
            Uri url = uris.get(0);
            {

                inputFileName = getFileName(url, this).replaceFirst("\\.[^.]+$", "");
                Log.i(TAG, "input file name: " + inputFileName);
                InputStream in;

                try {
                    in = getContentResolver().openInputStream(url);
                    if (null != in)
                        saveInputImage(in, "");
                    else
                        ToastUtil.showToast(this, "input == null", Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
            return;
        }

        inputFile.mkdirs();
        outputFile.delete();

        SimpleDateFormat f = new SimpleDateFormat("MMdd_HHmmss");
        String time = f.format(new Date());
        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            inputFileName = getFileName(uri, this).replaceFirst("\\.[^.]+$", "");
            switch (name2) {
                case 0:
                    inputFileName = String.format("%s_%s", inputFileName, time);
                    break;
                case 1:
                    inputFileName = String.format("%s_%d", inputFileName, i);
                    break;
                case 2:
                    inputFileName = String.format("%s_%d", time, i);
                    break;
                case 3:
                    inputFileName = time + "_" + inputFileName;
                    break;
            }
            String inputFilePath = String.format("%s/input.png/%s.png", dir, inputFileName);
            int j = 0;
            while (new File(inputFilePath).exists()) {
                j++;
                inputFilePath = dir + "/input.png/" + inputFileName + "_" + j + ".png";
            }
            whiteFileFromUri(uri, inputFilePath);
        }
        int inputFileSize = inputFile.listFiles().length;
        logTextView.setText(String.format(getString(R.string.input_file_size), inputFileSize));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_OK && null != data) {
            Uri url = data.getData();

            if (requestCode == SELECT_IMAGE && null != url) {
                deleteFile(inputFile);
                inputFileName = getFileName(url, this).replaceFirst("\\.[^.]+$", "");
                Log.i(TAG, "input file name: " + inputFileName);
                InputStream in;

                try {
                    in = getContentResolver().openInputStream(url);
                    if (null != in)
                        saveInputImage(in, "");
                    else
                        ToastUtil.showToast(this, "input == null", Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            } else if (requestCode == SELECT_MULTI_IMAGE) {
                List<Uri> imageUris = new ArrayList<>();
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    imageUris.add(clipData.getItemAt(i).getUri());
                }
                handleSelectedImages(imageUris);
            }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public int get_gif_frame_delay(@NonNull String path) {

        StringBuilder con = new StringBuilder();
        String result;

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sh");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            OutputStream os = process.getOutputStream();
            String cmd = "cd " + dir + "; export LD_LIBRARY_PATH=" + dir
                    + "; ./magick identify -format \"%T \" " + ShellUtils.escapeShellArgument(path) + " ";
            os.write((cmd + "\n").getBytes());
            os.write("exit\n".getBytes());
            os.flush();
            os.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((result = br.readLine()) != null) {
                con.append(result);
                con.append('\n');
            }
            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();

            Log.d(TAG, "get_gif_frame_delay() crash; result=" + con);
            return -1;
        }

        String[] data = con.toString().strip().split("\\s+");
        if (data.length < 2)
            return 0;

        int avg = Integer.parseInt(data[1]);
        int dif = 0;
        for (String s : data) {
            dif += (Integer.parseInt(s) - avg);
        }
        avg = avg + dif / data.length;

        Log.d(TAG, "get_gif_frame_delay() finish; result=" + con);
        return avg;
    }

    public boolean run_command(@NonNull String command) {
        if (command.trim().length() < 1) {
            Log.d(TAG, "run_command command=" + command + "; break");
            return false;
        }

        StringBuilder con = new StringBuilder();
        String result;

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((result = br.readLine()) != null) {
                con.append(result);
                con.append('\n');
                Log.d(TAG, "run_command output: " + result);
            }

            int exitCode = process.waitFor();
            Log.d(TAG, "run_command exit code: " + exitCode);
            Log.d(TAG, "run_command output: " + con.toString());

            return exitCode == 0;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "run_command command=" + command + "; crash; result=" + con, e);
            return false;
        }
    }

    public synchronized boolean run20(@NonNull String cmd, boolean bench_mark_mode, boolean sr) {
        newTask = false;
        Log.i(TAG, "run20 cmd = " + cmd);
        final long timeStart = System.currentTimeMillis();
        boolean export_dir = false;

        // 直接检查 dir 下的可执行文件
        File exeFile = new File(dir, "realsr-ncnn");
        if (!exeFile.exists()) {
            Log.e(TAG, "realsr-ncnn not found in " + dir);
            runOnUiThread(() -> {
                if (menuProgress != null) menuProgress.setTitle("");
                ToastUtil.showToast(this, "可执行文件不存在，请检查 assets", Toast.LENGTH_LONG);
            });
            return false;
        }

        // 确保可执行权限
        exeFile.setExecutable(true, false);
        run_command("chmod +x " + dir + " -R");

        String finalCmd = cmd;
        if (cmd.startsWith("./realsr-ncnn")
                || cmd.startsWith("./mnnsr-ncnn")
                || cmd.startsWith("./srmd-ncnn")
                || cmd.startsWith("./realcugan-ncnn")
                || cmd.startsWith("./resize-ncnn")
                || cmd.startsWith("./waifu2x-ncnn")
                || cmd.startsWith("./magick input")
                || cmd.startsWith("./Anime4k")) {
            if (cmd.contains(" input.png ") && cmd.contains(" output.png")) {
                if (inputFile.isDirectory() && !inputIsGifAnimation) {
                    export_dir = true;
                    String safeSavePath = ShellUtils.escapeShellArgument(savePath);
                    finalCmd = cmd.replace(" output.png ", " " + safeSavePath + " ");
                    String[] dirFormats = getResources().getStringArray(R.array.dir_output_format);
                    if (dirOutputFormat > 0 && dirOutputFormat < dirFormats.length) {
                        if (!finalCmd.contains(" -f ") && finalCmd.matches("./(realsr|srmd|waifu2x|realcugan|mnnsr)-ncnn.*")) {
                            finalCmd += " -f " + dirFormats[dirOutputFormat];
                        } else if (!finalCmd.contains(" -E ") && finalCmd.startsWith("./Anime4k")) {
                            finalCmd += " -E ." + dirFormats[dirOutputFormat];
                        }
                    }
                }

                if (cmd.startsWith("./magick input.png") || cmd.startsWith("./resize-ncnn -i input.png")) {
                    Log.i(TAG, "run20 deleteFile " + outputFile);
                    deleteFile(outputFile);
                }
            }

            runOnUiThread(() -> {
                if (menuProgress != null) menuProgress.setTitle(BUSY);
                sendNotification(this, BUSY, false);
            });
            modelName = "Real-ESRGAN-anime";
            if (cmd.matches(".+\\s-m(\\s+)\\S*models-.+")) {
                modelName = cmd.replaceFirst(".+\\s-m(\\s+)\\S*models-(\\S+).*", "$2");
            }
            if (cmd.startsWith("./Anime4k")) {
                modelName = "Anime4k";
                if (cmd.contains("-w"))
                    modelName += "-ACNet";
                if (cmd.contains("-H"))
                    modelName += "-HDN";
            } else if (modelName.matches("(se|nose|pro)")) {
                modelName = "Real-CUGAN-" + modelName;
            } else if (cmd.startsWith("./realcugan-ncnn")) {
                modelName = "Real-CUGAN";
                if (cmd.contains(" -c "))
                    modelName += cmd.replaceFirst(".+\\s-c(\\s+)(\\S+)\\s.*", "-C$2");
                if (cmd.contains(" -n "))
                    modelName += cmd.replaceFirst(".+\\s-n(\\s+)(\\S+)\\s.*", "-Noise$2");
            } else if (cmd.matches(".+\\s-m(\\s+)(bicubic|bilinear|nearest|avir|de-nearest).*")) {
                modelName = cmd.replaceFirst(".+\\s-m(\\s+)(bicubic|bilinear|nearest|lancir|avir|de-nearest).*",
                        "Classical-$2");
            } else if (cmd.matches(".*waifu2x.+models-(cugan|cunet|upconv).*")) {
                modelName = cmd.replaceFirst(".*waifu2x.+models-(cugan|cunet|upconv_7_photo|upconv_7_anime).*",
                        "Waifu2x-$1");
            } else if (cmd.startsWith("./magick input")) {
                if (cmd.contains("-filter"))
                    modelName = cmd.replaceFirst(".*-filter\\s+(\\w+).+", "Magick-$1");
                else
                    modelName = "Magick";
            } else if (cmd.startsWith("./mnnsr")) {
                if (cmd.matches(".+\\s-d\\s+\\d+\\s.*")) {
                    modelName = "MNNSR-Decensor" + cmd.replaceFirst(".+\\s-d\\s+(\\d+)\\s.*", "$1");
                } else {
                    String[] v = CommandListManager.getNameFromModelPath(cmd.replaceFirst(".+\\s-m(\\s+)(\\S+)\\s.*", "$2"), "MNNSR");
                    modelName = v[0];
                }
            }
        } else
            modelName = "SR";

        final boolean run_ncnn = bench_mark_mode || !modelName.equals("SR");
        boolean export_one_file = run_ncnn && (autoSave || (inputFile.isDirectory() && inputIsGifAnimation))
                && cmd.contains("output.png");
        if (bench_mark_mode) {
            export_one_file = false;
            runOnUiThread(() -> {
                if (menuProgress != null) menuProgress.setTitle(BUSY);
                sendNotification(this, BUSY, false);
            });
        }
        final boolean save = export_one_file;

        CommandBuilder builder = new CommandBuilder();
        builder.append(finalCmd);

        if (save) {
            String export_cmd = saveOutputCmd();
            if (inputIsGifAnimation)
                builder.append(";./magick -delay " + inputGifDelay + " output.png/* -loop 0 " + ShellUtils.escapeShellArgument(outputSavePath));
            else
                builder.append(";" + export_cmd);
        } else {
            outputSavePath = "";
        }

        final String executionCmd = builder.build();

        // ★★★ 关键：定义 final_export_dir 供回调使用 ★★★
        final boolean final_export_dir = export_dir;

        progressLogHelper = new ProgressLogHelper();

        if (isBound && processingService != null) {
            progressLogHelper.reset();
            processingService.startTask(executionCmd, dir, notify, new ImageProcessor.ProcessCallback() {
                @Override
                public void onProgress(String line) {
                    progressLogHelper.appendLine(line);

                    runOnUiThread(() -> {
                        logTextView.setText(progressLogHelper.getDisplayText());
                        if (progressLogHelper.hasProgress()) {
                            if (menuProgress != null) menuProgress.setTitle(progressLogHelper.getProgressText());
                        }
                    });
                }

                @Override
                public void onCompleted(String result, boolean success) {
                    String logResult = progressLogHelper.getCompletionSummary(success, modelName, run_ncnn);

                    if (bench_mark_mode) {
                        logResult = logResult.replace("\n", String.format(", Benchmark run on %s\n%s",
                                DeviceInfo.getConfigStr(useCPU, tileSize), DeviceInfo.getInfo(RealSRMainActivity.this)));
                    }

                    progressLogHelper.appendLine(logResult);
                    String finalLog = progressLogHelper.getFullLog();
                    log = finalLog;

                    // ★★★ 新增：如果命令是 magick，打印输出图片尺寸 ★★★
                    if (cmd != null && cmd.startsWith("./magick")) {
                        try {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(outputFile.getAbsolutePath(), opts);
                            Log.d(TAG, "Magick output image size: " + opts.outWidth + "x" + opts.outHeight);
                            // 也打印输入图片尺寸对比
                            BitmapFactory.Options optsIn = new BitmapFactory.Options();
                            optsIn.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(inputFile.getAbsolutePath(), optsIn);
                            Log.d(TAG, "Magick input image size: " + optsIn.outWidth + "x" + optsIn.outHeight);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to decode image size", e);
                        }
                    }

                    runOnUiThread(() -> {
                        logTextView.setText(finalLog);
                        if (menuProgress != null) menuProgress.setTitle(success ? DONE : ERR);
                        boolean forceShow = !success && notify == 3;
                        sendNotification(RealSRMainActivity.this, success ? DONE : ERR, forceShow);

                        if (keepScreen) {
                            logTextView.setKeepScreenOn(false);
                        }

                        if (success) {
                            if (save) {
                                if (!outputFile.exists()) {
                                    Toast.makeText(getApplicationContext(), R.string.output_not_exits, Toast.LENGTH_SHORT)
                                            .show();
                                } else {
                                    checkSaveOutput();
                                }
                            } else if (final_export_dir) {
                                ToastUtil.showToast(getApplicationContext(), R.string.save_succeed, Toast.LENGTH_SHORT);
                            }

                            if (!save && inputFile.isDirectory()) {
                                if (inputIsGifAnimation)
                                    scanFiles(new String[] { outputSavePath });
                                else {
                                    File[] files = inputFile.listFiles();
                                    if (files != null) {
                                        List<String> outputPaths = new ArrayList<>();
                                        for (File file : files) {
                                            outputPaths.add(savePath + File.separator + file.getName());
                                        }
                                        scanFiles(outputPaths.toArray(new String[0]));
                                    }
                                }
                            }

                            boolean showImgView = (executionCmd.contains("output.png"));
                            if (showImgView) {
                                if (outputFile.exists() && outputFile.isFile()) {
                                    updateImage(dir + "/output.png", String.format("%s\n%s", getString(R.string.hr), log),
                                            false);
                                } else if (inputIsGifAnimation && outputFile.exists() && outputFile.isDirectory()
                                        && outputFile.listFiles().length > 1) {
                                    updateImage(outputFile.listFiles()[0].getPath(),
                                            String.format("%s\n%s", getString(R.string.hr), log), false);
                                } else {
                                    updateImage(dir + "/input.png", String.format("%s\n%s", getString(R.string.lr), log),
                                            false);
                                }
                            }
                            if (!executionCmd.contains("output.png"))
                                imageView.setVisibility(View.GONE);
                        } else {
                            if (!executionCmd.contains("output.png"))
                                imageView.setVisibility(View.GONE);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        logTextView.append("\nError: " + error);
                        sendNotification(RealSRMainActivity.this, ERR, true);
                        if (keepScreen) {
                            logTextView.setKeepScreenOn(false);
                        }
                        if (menuProgress != null) {
                            menuProgress.setTitle("");
                        }
                        ToastUtil.showToast(RealSRMainActivity.this, "错误: " + error, Toast.LENGTH_LONG);
                        Log.e(TAG, "onError: " + error);
                    });
                }
            });
        } else {
            Log.e(TAG, "ProcessingService not bound or null");
            ToastUtil.showToast(RealSRMainActivity.this, "服务未绑定，请重试", Toast.LENGTH_SHORT);
            return false;
        }

        return true;
    }

    private void performRestore() {
        if (!inputFile.exists() || inputFile.isDirectory()) {
            ToastUtil.showToast(this, R.string.image_not_exists, Toast.LENGTH_SHORT);
            return;
        }

        stopCommand();

        if (menuProgress != null) {
            menuProgress.setTitle("修复中...");
        }

        new Thread(() -> {
            String outputPath = dir + "/output.png";

            boolean success = ImageRestorer.restoreWithDefault(
                    RealSRMainActivity.this,
                    inputFile.getAbsolutePath(),
                    outputPath
            );

            runOnUiThread(() -> {
                if (menuProgress != null) {
                    menuProgress.setTitle("");
                }

                if (success) {
                    File outFile = new File(outputPath);
                    if (outFile.exists() && outFile.isFile()) {
                        updateImage(outputPath, "修复完成", false);
                        ToastUtil.showToast(RealSRMainActivity.this, "修复完成", Toast.LENGTH_SHORT);
                    } else {
                        ToastUtil.showToast(RealSRMainActivity.this, "修复失败，未生成结果文件", Toast.LENGTH_SHORT);
                    }
                } else {
                    ToastUtil.showToast(RealSRMainActivity.this, "修复失败，请检查日志", Toast.LENGTH_SHORT);
                }
            });
        }).start();
    }

    private void stopCommand() {
        if (isBound && processingService != null) {
            processingService.cancelTask();
            if (menuProgress != null)
                menuProgress.setTitle("");
        }
        newTask = true;
    }

    private boolean inputIsGifAnimation;
    private int inputGifDelay;

    private boolean saveInputImage(@NonNull InputStream in, String path) {

        Log.i(TAG, "saveInputImage start ");
        inputIsGifAnimation = false;
        boolean inputOneImage = false;
        if (path.isEmpty()) {
            inputOneImage = true;
            path = dir + "/input.png";
        }
        File file = new File(path);

        if (file.exists()) {
            file.delete();
        }
        try {

            byte[] buffer = new byte[4112];
            int read;

            int match = -1;

            if ((read = in.read(buffer)) != -1) {
                if (prePng) {
                    match = PreprocessToPng.match(buffer);
                    if (match >= 0) {
                        file = new File(dir + "/tmp");
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                }
            }

            file.createNewFile();
            OutputStream outStream = new FileOutputStream(file);
            outStream.write(buffer, 0, read);

            while ((read = in.read(buffer)) != -1) {
                outStream.write(buffer, 0, read);
            }

            outStream.flush();
            outStream.close();

            if (match >= 0) {
                if (PreprocessToPng.isHeif(match) || PreprocessToPng.isAVIF(match)) {
                    Bitmap bitmap = BitmapFactory.decodeFile(dir + "/tmp");

                    try {
                        FileOutputStream out = new FileOutputStream(path);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        out.flush();
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (preFrame && inputOneImage && PreprocessToPng.isGIF(match)) {
                    inputGifDelay = get_gif_frame_delay(dir + "/tmp");
                    inputIsGifAnimation = inputGifDelay > 0;
                    Log.i(TAG, "inputGifDelay=" + inputGifDelay + ", " + inputIsGifAnimation);
                    if (inputIsGifAnimation) {
                        deleteFile(inputFile);
                        inputFile.mkdirs();
                        run_command("./magick tmp -coalesce -delay 0 input.png/%04d.png");
                    } else
                        run_command("./magick tmp " + ShellUtils.escapeShellArgument(path));
                } else {
                    run_command("./magick tmp " + ShellUtils.escapeShellArgument(path));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        try {
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        updateImage(dir + "/input.png", getString(R.string.lr), false);
        return true;
    }

    private void updateImage(final String path, String text, boolean keepScreen) {
        Log.i(TAG, "updateImage runOnUiThread");
        File file = new File(path);
        runOnUiThread(() -> {
            if (file.exists()) {
                if (file.isDirectory()) {
                    if (file.listFiles().length > 0) {
                        imageView.setVisibility(View.VISIBLE);
                        imageView.setImage(ImageSource.uri(file.listFiles()[0].getPath()));
                        Log.i(TAG, "updateImage finish, directory");
                    } else {
                        imageView.setVisibility(View.GONE);
                        Log.i(TAG, "updateImage finish, empty directory");
                    }
                    logTextView.setText(text);
                } else {
                    imageView.setVisibility(View.VISIBLE);
                    imageView.setImage(ImageSource.uri(path));
                    logTextView.setText(getImageResolation(file, text));
                    Log.i(TAG, "updateImage finish, file");
                }

            } else
                Log.i(TAG, "updateImage skip");
            if (keepScreen) {
                logTextView.setKeepScreenOn(false);
            }

        });
    }

    private boolean run_fake_command(String q) {
        if (q == null)
            return true;
        if (q.isEmpty())
            return true;
        if (q.equals("help")) {
            showImage(titleFile, getString(R.string.default_log));
        } else if (q.equals("in")) {
            showImage(inputFile, getString(R.string.lr));
        } else if (q.equals("out")) {
            showImage(outputFile, getString(R.string.hr));
        } else if (q.startsWith("show ")) {
            String path = q.replaceFirst("\\s*show\\s+(\\S+)\\s*", "$1");
            File file = new File(path);
            if (!file.exists()) {
                path = dir + "/" + path;
                file = new File(path);
            }
            showImage(file, getString(R.string.show) + path);

        } else if (q.equals("none")) {
            showImage(null, getString(R.string.menu_reset_cache));
        } else if (q.equals(CMD_RESET_CACHE)) {
            showImage(null, getString(R.string.menu_reset_cache) + "...");
            return false;
        } else
            return false;
        return true;
    }

    private void showImage(File file, String info) {
        if (file == null) {
            imageView.setVisibility(View.GONE);
            logTextView.setText(info);
        } else if (file.exists() && (!file.isDirectory())) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImage(ImageSource.uri(file.getAbsolutePath()));
            logTextView.setText(getImageResolation(file, info));
        } else if (file.isDirectory()) {
            imageView.setVisibility(View.GONE);
            File[] files = file.listFiles();
            if (files.length < 1) {
                logTextView.setText(getString(R.string.image_not_exists));
            } else
                logTextView.setText(getString(R.string.image_is_directory));
        } else {
            imageView.setVisibility(View.GONE);
            logTextView.setText(getString(R.string.image_not_exists));
        }
    }

    private static String getImageResolation(File file, String info) {
        if (info.trim().contains("\n"))
            return info;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        int width = options.outWidth;
        int height = options.outHeight;
        if (width > 0 && height > 0)
            return info + " " + width + "x" + height;
        return info;
    }

    private void scanFiles(String[] filePath) {
        Log.i(TAG, "scanFiles() length=" + filePath.length);
        try {
            MediaScannerConnection.scanFile(getApplicationContext(), filePath, null,
                    (path, uri) -> {
                        Log.i(TAG, "Scanned " + path + ":");
                        Log.i(TAG, "-> uri=" + uri);
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkSaveOutput() {
        File file = new File(outputSavePath);
        if (file.exists()) {
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri uri = Uri.fromFile(file);
            intent.setData(uri);
            sendBroadcast(intent);
            ToastUtil.showToast(getApplicationContext(), R.string.save_succeed, Toast.LENGTH_SHORT);
        } else {
            ToastUtil.showToast(getApplicationContext(), R.string.save_fail, Toast.LENGTH_SHORT);
        }
    }

    private String saveOutputCmd() {

        SimpleDateFormat f = new SimpleDateFormat("MMdd_HHmmss");
        outputSavePath = savePath + File.separator;
        switch (name) {
            case 0:
                outputSavePath += modelName + "_" + f.format(new Date());
                break;
            case 1:
                outputSavePath += inputFileName + "_" + modelName + "_" + f.format(new Date());
                break;
            case 2:
                outputSavePath += inputFileName + "_" + modelName;
                break;
            case 3:
                outputSavePath += inputFileName + "_" + f.format(new Date());
                break;
            case 4:
                outputSavePath += inputFileName;
                break;
            default:
                outputSavePath += "output";
        }

        String cmd;
        if (inputIsGifAnimation) {
            outputSavePath += ".gif";
            cmd = ("cp " + dir + "/output.gif");
        } else if (format == 0) {
            outputSavePath += ".png";
            cmd = ("cp " + dir + "/output.png");
        } else {
            if (format == 1) {
                outputSavePath += ".webp";
                cmd = ("./magick output.png");
            } else if (format == 2) {
                outputSavePath += ".gif";
                cmd = ("./magick output.png");
            } else if (format == 3) {
                outputSavePath += ".heic";
                cmd = ("./magick output.png");
            } else {
                outputSavePath += ".jpg";
                String q = formats[format].replaceAll("[a-zA-Z%\\s]+", "");
                if (q.length() > 0) {
                    cmd = ("./magick output.png -quality " + q);
                } else
                    cmd = ("./magick output.png");
            }
        }

        return cmd + " " + ShellUtils.escapeShellArgument(outputSavePath);
    }
}
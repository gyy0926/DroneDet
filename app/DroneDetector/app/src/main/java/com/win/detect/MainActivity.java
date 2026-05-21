package com.win.detect;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.win.detect.filter.DroneOuiMatcher;
import com.win.detect.locate.LocatePipeline;
import com.win.detect.locate.OnnxLocatePredictor;
import com.win.detect.locate.TripletFiles;
import com.win.detect.model.RootShell;
import com.win.detect.predict.OnnxDemo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE = 100;

    private static final int REQ_READ_STORAGE = 1001;

    private static final String MODEL_ASSET_NAME = "model_locate.onnx";

    public static volatile OnnxLocatePredictor predictor;


    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile Exception modelLoadError;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        // 默认页面：无人机扫描
        loadFragment(new DroneScanFragment());

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
//        检查读写权限
        checkStoragePermission();
//        载入模型和OUI名单
        try {
            DroneOuiMatcher.init(this);
            OnnxDemo.init(this);
            loadModelAsync();
            ensurePermissionAndRun();
            RootShell.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
        nav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_scan) {
                loadFragment(new DroneScanFragment());
                return true;
            } else if (id == R.id.nav_csi) {
                loadFragment(new FragmentCsiDataCollect());
                return true;
            } else if (id == R.id.nav_history) {
                loadFragment(new HistoryFragment());
                return true;
            }
            return false;
        });

    }

    private void loadFragment(Fragment f) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, f)
                .commit();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            boolean readGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

            boolean writeGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

            if (!readGranted || !writeGranted) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_STORAGE);
            } else {
                onStorageReady();
            }
        } else {
            onStorageReady();
        }

    }
    private void onStorageReady() {
        File dir = new File("/sdcard/Download/detected_data/");
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            Log.d("DIR", "创建目录: " + ok);
        } else {
            Log.d("DIR", "目录已存在");
        }
    }

    private void loadModelAsync() {
        executor.execute(() -> {
            try {
                File modelFile = ensureAssetCopiedToFiles(MODEL_ASSET_NAME);
                predictor = OnnxLocatePredictor.create(modelFile);
                mainHandler.post(() -> {
                    setOutput("Model loaded. Ready.\n");
                });
            } catch (Exception e) {
                modelLoadError = e;
                mainHandler.post(() -> setOutput("Model load failed: " + e.getMessage()));
            }
        });
    }

    private void ensurePermissionAndRun() {
        // Android 11+ needs "All files access" for arbitrary /sdcard paths.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesAccess();
                return;
            }
            //runPipeline();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            //runPipeline();
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                REQ_READ_STORAGE);
    }

    private void requestAllFilesAccess() {
        setOutput("需要授权“所有文件访问权限”才能读取 /sdcard。\n请在系统设置中打开本应用的文件访问权限后重试。");
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_READ_STORAGE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //runPipeline();
        } else {
            setOutput("Permission denied: cannot read /sdcard.\n请允许读取存储权限。");
        }
    }



    private void setOutput(String s) {

    }



    private File ensureAssetCopiedToFiles(String assetName) throws Exception {
        File outFile = new File(getFilesDir(), assetName);
        if (outFile.isFile() && outFile.length() > 0) return outFile;

        File tmp = new File(getFilesDir(), assetName + ".tmp");
        if (tmp.isFile()) tmp.delete();

        try (InputStream in = getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[1024 * 64];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        }

        if (!tmp.renameTo(outFile)) {
            throw new IllegalStateException("Failed to move model to: " + outFile.getAbsolutePath());
        }
        return outFile;
    }
}

package com.win.detect.collect;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.win.detect.MainActivity;
import com.win.detect.locate.LocatePipeline;
import com.win.detect.locate.OnnxLocatePredictor;
import com.win.detect.locate.TripletFiles;
import com.win.detect.model.RootShell;
import com.win.detect.model.WifiCSI;
import com.win.detect.predict.OnnxDemo;
import com.win.detect.reader.CSIReader;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class CSICollector {

    private static final String TAG = "CSICollector";

    private static final String detectedFilePath = "/sdcard/Download/detected_data/";
    private static final String dirPath = "/sdcard/Download/detected_data/";
    private static final int TCPDUMP_PACKET_COUNT = 600;

    private static final long TCPDUMP_MAX_WAIT_MS = 3000L;
    private static final long TCPDUMP_GRACE_STOP_MS = 1000L;
    private static boolean locatingFinished = false;

    private static final int VOTE_COUNT = 10;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicReference<Process> locatingTcpdump = new AtomicReference<>(null);

    private static volatile Exception modelLoadError;
    private static volatile WifiCSI detectedWifi;
    private static volatile boolean DETECTED = false;

    public interface UiCallback {
        void onDroneDetected(String wifiName);
        void onNoDroneDetected();
        void onLocateError(String message);
        void onLocateResult(float distance);
    }

    public static void collectAsync(List<WifiCSI> list, UiCallback callback) {
        executor.execute(() -> {
            DETECTED = false;
            detectedWifi = null;

            for (WifiCSI wifi : list) {
                if (DETECTED) break;
                detectedWifi = wifi;
                new CollectTask(wifi, callback).runDetectOnce();
            }

            if (!DETECTED) {
                Log.d(TAG, "No drone detected after collecting all WiFi CSI data.");
                if (callback != null) {
                    mainHandler.post(callback::onNoDroneDetected);
                }
            }
        });
    }

    public static void startLocatingAsync(UiCallback callback) {
        final WifiCSI wifi = detectedWifi;
        locatingFinished = false;
        if (wifi == null) {
            postError(callback, "No detected WiFi to locate.");
            return;
        }
        executor.execute(() -> {
            try {
                new CollectTask(wifi, callback).runLocateOnce();
                locatingFinished = true;
            } catch (Throwable t) {
                postError(callback, "Locate collect failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        });
    }

    public static void stopLocating() {
        Process p = locatingTcpdump.getAndSet(null);
        if (p != null) {
            try {
                p.destroy();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void predictLocateAsync(UiCallback callback) {
        final WifiCSI wifi = detectedWifi;
        if (!locatingFinished) {
            postError(callback, "Locating is not finished yet.");
            return;
        }
        if (wifi == null) {
            postError(callback, "No detected WiFi to locate.");
            return;
        }
        executor.execute(() -> {
            try {
                float distance = runLocatePipeline(wifi.WifiName);
                postResult(callback, distance);
            } catch (Throwable t) {
                postError(callback, "Locate predict failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        });
    }

    private static float runLocatePipeline(String wifiName) throws Exception {
        OnnxLocatePredictor predictorLocal = MainActivity.predictor;
        if (predictorLocal == null) {
            if (modelLoadError != null) {
                throw new IllegalStateException("Model not ready: " + modelLoadError.getMessage(), modelLoadError);
            }
            throw new IllegalStateException("Model is still loading");
        }

        CSIReader.processPcap(wifiName);
        TripletFiles triplet = TripletFiles.findInDirectory(new File(dirPath), wifiName);

        try (InputStream time = new FileInputStream(triplet.timeFile);
             InputStream amp = new FileInputStream(triplet.ampFile);
             InputStream pha = new FileInputStream(triplet.phaFile)) {

            LocatePipeline pipeline = new LocatePipeline(predictorLocal);
            OnnxLocatePredictor.Result r = pipeline.predictFromTriplet(time, amp, pha, VOTE_COUNT);
            Log.d("Locate", String.format(Locale.US, "Locate OK mean=%.6f median=%.6f", r.mean, r.median));
            return r.mean;
        }
    }

    private static void postDetected(UiCallback cb, String wifiName) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onDroneDetected(wifiName));
    }

    private static void postError(UiCallback cb, String message) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onLocateError(message));
    }

    private static void postResult(UiCallback cb, float distance) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onLocateResult(distance));
    }

    private static final class CollectTask {
        private final WifiCSI wifi;
        private final UiCallback callback;

        CollectTask(WifiCSI wifi, UiCallback callback) {
            this.wifi = wifi;
            this.callback = callback;
        }

        void runDetectOnce() {
            Log.d(TAG, "Start collect/detect: " + wifi.WifiName);

            try {
                cleanupOutputs();
                RootShell.exec("ifconfig wlan0 down");
                RootShell.exec("ifconfig wlan0 up");
                Thread.sleep(2000);
                if (!isWlanUp()) throw new IOException("wlan0 is not up");

                runCollectWithNexutilAndTcpdump();

                CSIReader.processPcap(wifi.WifiName);

                String timePath = detectedFilePath + wifi.WifiName + "_time.txt";
                String ampPath = detectedFilePath + wifi.WifiName + "_amp.txt";
                String phaPath = detectedFilePath + wifi.WifiName + "_pha.txt";

                try (InputStream time = new FileInputStream(timePath);
                     InputStream amp = new FileInputStream(ampPath);
                     InputStream pha = new FileInputStream(phaPath)) {

                    int predicted = OnnxDemo.predict(time, amp, pha);
                    if (predicted == 0) {
                        Log.d(TAG, "Detected drone!");
                        DETECTED = true;
                        postDetected(callback, wifi.WifiName);
                    } else {
                        Log.d(TAG, "Not detected. pred=" + predicted);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Detect collect failed: " + wifi.WifiName, t);
            }
        }

        void runLocateOnce() throws Exception {
            Log.d(TAG, "Start locating collect: " + wifi.WifiName);
            cleanupOutputs();

            RootShell.exec("ifconfig wlan0 down");
            RootShell.exec("ifconfig wlan0 up");
            Thread.sleep(2000);
            if (!isWlanUp()) throw new IOException("wlan0 is not up");

            runCollectWithNexutilAndTcpdump();
        }

        private void cleanupOutputs() {
            deleteIfExists(new File(detectedFilePath + wifi.WifiName + ".pcap"));
            deleteIfExists(new File(detectedFilePath + wifi.WifiName + "_time.txt"));
            deleteIfExists(new File(detectedFilePath + wifi.WifiName + "_amp.txt"));
            deleteIfExists(new File(detectedFilePath + wifi.WifiName + "_pha.txt"));
        }

        private void deleteIfExists(File f) {
            try {
                if (f == null) return;
                if (!f.exists()) return;
                if (f.delete()) {
                    Log.d(TAG, "Deleted stale file: " + f.getAbsolutePath());
                } else {
                    Log.w(TAG, "Failed to delete stale file: " + f.getAbsolutePath());
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to delete stale file: " + (f == null ? "null" : f.getAbsolutePath()), t);
            }
        }

        private void runCollectWithNexutilAndTcpdump() throws Exception {
            String nexutilCmd = "nexutil -Iwlan0 -s500 -b -l34 -v" + wifi.CSIParam;
            Log.d(TAG, nexutilCmd);
            RootShell.exec(nexutilCmd);
            RootShell.exec("nexutil -m1");

            String tcpdumpCmd =
                    "tcpdump -i wlan0 -vv -c " + TCPDUMP_PACKET_COUNT
                            + " port 5500 -w "
                            + detectedFilePath + wifi.WifiName + ".pcap";

            Log.d(TAG, tcpdumpCmd);

            Process tcpdump = Runtime.getRuntime().exec(new String[]{"su", "-c", tcpdumpCmd});
            locatingTcpdump.set(tcpdump);

            Thread errReader = new Thread(() -> drainToLog(tcpdump.getErrorStream(), true));
            Thread outReader = new Thread(() -> drainToLog(tcpdump.getInputStream(), false));
            errReader.start();
            outReader.start();

            long start = System.currentTimeMillis();

            try {
                // === 核心：带时间兜底的等待逻辑 ===
                while (true) {
                    try {
                        int code = tcpdump.exitValue();
                        Log.d(TAG, "tcpdump finished normally, code=" + code + ", wifi=" + wifi.WifiName);

                        if (code != 0) {
                            throw new IOException("tcpdump failed, exit code=" + code);
                        }
                        break;
                    } catch (IllegalThreadStateException stillRunning) {
                        // still running
                    }

                    if (System.currentTimeMillis() - start >= TCPDUMP_MAX_WAIT_MS) {
                        Log.w(TAG, "tcpdump max wait reached, try graceful stop: " + wifi.WifiName);

                        // 1️⃣ 优雅停止（等价 Ctrl+C）
                        try {
                            RootShell.exec("pkill -2 tcpdump");
                        } catch (Throwable t) {
                            Log.w(TAG, "pkill SIGINT failed", t);
                        }

                        // 2️⃣ 等待优雅退出
                        long graceStart = System.currentTimeMillis();
                        while (true) {
                            try {
                                int code = tcpdump.exitValue();
                                Log.d(TAG, "tcpdump stopped gracefully, code=" + code);
                                break;
                            } catch (IllegalThreadStateException stillRunning) {
                                if (System.currentTimeMillis() - graceStart >= TCPDUMP_GRACE_STOP_MS) {
                                    Log.w(TAG, "grace stop timeout, force destroy");
                                    tcpdump.destroy();
                                    tcpdump.waitFor();
                                    break;
                                }
                                Thread.sleep(50);
                            }
                        }
                        break;
                    }

                    Thread.sleep(50);
                }

                // === 文件完整性保护 ===
                Thread.sleep(300);

                File pcapFile = new File(detectedFilePath + wifi.WifiName + ".pcap");
                if (!pcapFile.exists() || pcapFile.length() < 24) {
                    throw new IOException("pcap file is empty or invalid: " + pcapFile.getAbsolutePath());
                }

                Log.d(TAG, "pcap saved: " + pcapFile.getAbsolutePath() + ", size=" + pcapFile.length());

            } finally {
                try {
                    errReader.join(500);
                    outReader.join(500);
                } catch (InterruptedException ignored) {
                }

                locatingTcpdump.compareAndSet(tcpdump, null);
            }
        }

        private int exec(String cmd, long timeoutMs) throws Exception {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            String stdout = readStreamFully(p.getInputStream());
            String stderr = readStreamFully(p.getErrorStream());

            waitForProcessOrThrow(p, timeoutMs, cmd);

            int code = p.exitValue();
            if (!stdout.isEmpty()) Log.d(TAG, "su stdout: " + stdout.trim());
            if (!stderr.isEmpty()) Log.w(TAG, "su stderr: " + stderr.trim());
            if (code != 0) {
                throw new IOException("Command failed (" + code + "): " + cmd + (stderr.isEmpty() ? "" : "\nstderr: " + stderr.trim()));
            }
            return code;
        }

        private void waitForProcessOrThrow(Process p, long timeoutMs, String cmdForError) throws IOException, InterruptedException {
            long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 0L);
            while (true) {
                try {
                    p.exitValue();
                    return;
                } catch (IllegalThreadStateException notFinishedYet) {
                    // still running
                }

                if (timeoutMs >= 0 && System.currentTimeMillis() > deadline) {
                    p.destroy();
                    throw new IOException("Command timeout: " + cmdForError);
                }
                Thread.sleep(20);
            }
        }

        private String readStreamFully(InputStream in) throws IOException {
            if (in == null) return "";
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        }

        private void drainToLog(InputStream in, boolean isErr) {
            if (in == null) return;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    if (isErr) {
                        Log.w(TAG, "tcpdump stderr: " + line);
                    } else {
                        Log.d(TAG, "tcpdump stdout: " + line);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "drain stream failed", e);
            }
        }
    }

    public static boolean isWlanUp() {
        Process process = null;
        BufferedReader stdReader = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "ifconfig wlan0"});
            stdReader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));

            String line;
            while ((line = stdReader.readLine()) != null) {
                Log.d(TAG, "stdout: " + line);
                if (line.contains("UP")) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "isWlanUp failed: " + e.getMessage(), e);
            return false;
        } finally {
            try {
                if (stdReader != null) stdReader.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {
            }
        }
    }
}


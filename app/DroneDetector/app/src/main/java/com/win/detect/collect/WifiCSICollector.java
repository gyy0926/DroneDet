package com.win.detect.collect;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.win.detect.model.DataCollectModel;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class WifiCSICollector {
//    这个是专门收集csi数据的工具，不用再手动输入

    public interface CollectListener {

        void onStart();

        void onFinish();

        void onError(String msg);

    }

    private static final String TAG = "WifiCSICollector";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private enum StopReason { NONE, NORMAL, USER, ERROR }

    private static volatile boolean running = false;
    private static volatile StopReason stopReason = StopReason.NONE;
    private static volatile String errorMessage = null;

    public static void startCollect(Context context,
                                    DataCollectModel model,
                                    CollectListener listener) {

        if (running) {
            return;
        }

        running = true;
        stopReason = StopReason.NONE;
        errorMessage = null;

        new Thread(() -> {

            Process process = null;
            DataOutputStream os = null;
            Thread monitor = null;

            try {

                Log.i(TAG, "开始数据采集");

                process = Runtime.getRuntime().exec("su");
                os = new DataOutputStream(process.getOutputStream());

                execCmd(os, "ifconfig wlan0 down");
                execCmd(os, "ifconfig wlan0 up");

                Thread.sleep(1000);

                String nexutilCmd = "nexutil -Iwlan0 -s500 -b -l34 -v" + model.CSIParam;
                execCmd(os, nexutilCmd);
                execCmd(os, "nexutil -m1");

                execCmd(os, "mkdir -p /sdcard/Download/auto_collect");
                String safeName = model.WifiName == null ? "capture" : model.WifiName.replaceAll("[^A-Za-z0-9_-]", "_");
                if (safeName.isEmpty()) {
                    safeName = "capture";
                }
                String tcpdumpCmd =
                        "tcpdump -i wlan0 -vv dst port 5500 -w /sdcard/Download/auto_collect/"
                                + safeName + ".pcap &";

                execCmd(os, tcpdumpCmd);

                Log.i(TAG, "tcpdump开始采集");

                if (listener != null) {
                    MAIN.post(listener::onStart);
                }

                monitor = new Thread(() -> {
                    try {
                        while (running) {
                            Thread.sleep(5000);

                            if (!checkWlan() && running && stopReason == StopReason.NONE) {
                                Log.e(TAG, "wlan0 在采集中断开，停止采集");

                                stopReason = StopReason.ERROR;
                                errorMessage = "wlan0异常关闭，采集已停止";
                                running = false;

                                try {
                                    Process kill = Runtime.getRuntime().exec("su -c pkill tcpdump");
                                    kill.waitFor();
                                } catch (Exception e) {
                                    Log.e(TAG, "failed to pkill tcpdump: " + e.getMessage());
                                }

                                break;
                            }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                });

                monitor.start();

                int time = model.CollectTime;

                for (int i = 0; i < time && running; i++) {

                    Thread.sleep(1000);
                }

                if (stopReason == StopReason.NONE && running) {
                    running = false;
                    stopReason = StopReason.NORMAL;
                    try {

                        Process kill = Runtime.getRuntime().exec("su -c pkill tcpdump");
                        kill.waitFor();
                    } catch (Exception e) {
                        Log.e(TAG, "stop tcpdump failed: " + e.getMessage());
                        try {
                            execCmd(os, "pkill tcpdump");
                        } catch (Exception ignored) {}
                    }

                    Log.i(TAG, "采集结束");


                }


                if (listener != null) {
                    if (stopReason == StopReason.NORMAL) {
                        MAIN.post(listener::onFinish);
                    } else if (stopReason == StopReason.ERROR) {
                        String msg = errorMessage != null ? errorMessage : "Collect failed";
                        MAIN.post(() -> listener.onError(msg));
                    }
                }

                try {
                    if (monitor != null) {
                        monitor.interrupt();
                        monitor.join(1000);
                    }
                } catch (Exception ignored) {}

                execCmd(os, "exit");

                os.flush();
                os.close();

                process.waitFor();

            } catch (Exception e) {

                Log.e(TAG, "采集异常: " + e.getMessage());

                running = false;
                stopReason = StopReason.ERROR;
                errorMessage = "Collect failed: " + e.getMessage();

                if (listener != null) {
                    MAIN.post(() -> listener.onError(errorMessage));
                }
            } finally {
                try {
                    if (monitor != null && monitor.isAlive()) {
                        monitor.interrupt();
                        monitor.join(500);
                    }
                } catch (Exception ignored) {}
                try {
                    if (os != null) {
                        execCmd(os, "exit");
                        os.flush();
                        os.close();
                    }
                } catch (Exception ignored) {}
                try {
                    if (process != null) {
                        process.waitFor();
                        process.destroy();
                    }
                } catch (Exception ignored) {}
            }

        }).start();
    }

    private static void execCmd(DataOutputStream os, String cmd) throws Exception {

        Log.d(TAG, "执行命令: " + cmd);

        os.writeBytes(cmd + "\n");
        os.flush();
    }

    /**
     * 检查wlan0是否处于开启状态（UP）
     * @return true=开启，false=关闭/不存在
     */
    private static boolean checkWlan() {
        Process process = null;
        BufferedReader stdReader = null;
        BufferedReader errReader = null;
        try {
            // 执行su + ifconfig wlan0命令
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "ifconfig wlan0"});

            // 同时读取标准输出和错误输出（关键修复点）
            stdReader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"));

            // 先读标准输出
            String line;
            while ((line = stdReader.readLine()) != null) {
                Log.d(TAG, "标准输出: " + line);
                if (line.contains("UP")) {
                    return true; // 找到UP，说明wlan0开启
                }
            }

            // 标准输出没找到，读错误输出（部分安卓会把ifconfig输出放这里）
            while ((line = errReader.readLine()) != null) {
                Log.d(TAG, "错误输出: " + line);
                if (line.contains("UP")) {
                    return true;
                }
            }

            return false; // 未找到UP，说明关闭
        } catch (Exception e) {
            Log.e(TAG, "检查wlan0状态失败: " + e.getMessage());
            e.printStackTrace();
            return false; // 异常时默认判定为关闭
        } finally {
            // 关闭所有流和进程，避免内存泄漏
            try {
                if (stdReader != null) stdReader.close();
                if (errReader != null) errReader.close();
                if (process != null) {
                    process.destroy();
                    process.waitFor(); // 确保进程彻底退出
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void stop() {

        stopReason = StopReason.USER;
        running = false;

        try {

            Process kill = Runtime.getRuntime().exec("su -c pkill tcpdump");
            kill.waitFor();

        } catch (Exception e) {

            Log.e(TAG, "stop failed", e);
        }
    }
}
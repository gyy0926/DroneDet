package com.win.detect;

import android.util.Log;

public class DroneDetector {

    private boolean detecting = false;

    public boolean isDetecting() {
        return detecting;
    }

    public void startDetection() {
        detecting = true;
        Log.i("DETECT", "Started collecting drone-related data");
    }

    public void stopDetection() {
        detecting = false;
        Log.i("DETECT", "Stopped data collection");
    }

    public float calculateDistance() {
        Log.i("DETECT", "Calculating drone position...");
        return 11.24f; // 模拟返回值
    }
}

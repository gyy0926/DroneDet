package com.win.detect.model;

import java.util.Objects;

public class WifiInfo {

    public String name;            // SSID
    public int channel;            // 信道
    public int frequency;          // 频率 MHz
    public int channelWidth;       // 带宽 MHz (20/40/80/160)
    public String mac;             // BSS MAC
    public int signalIntensity;    // dBm

    public WifiInfo() {
    }

}

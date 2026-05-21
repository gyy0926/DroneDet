package com.win.detect.model;

public class WifiCSI {
    public String WifiName;   // 输出文件名
    public String CSIParam;   // 设备唯一标识符

    public WifiCSI(String rightWifi, String s) {
        this.WifiName = rightWifi;
        this.CSIParam = s;
    }

    public WifiCSI() {
    }
}
package com.win.detect.filter;

import static com.win.detect.filter.DroneOuiMatcher.isDroneVendorMac;

import com.win.detect.model.WifiInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WifiFilter {
    public static List<WifiInfo> filterWifi(List<WifiInfo> wifiList) {
        List<WifiInfo> list = new ArrayList<>();

        // 过滤掉信号强度较弱的WiFi（例如，信号强度小于-80 dBm）
        // 1. 过滤信号强度 < -90 dBm
        for (WifiInfo info : wifiList) {
            if (info.signalIntensity > -90 && DroneOuiMatcher.isDroneVendorMac(info.mac)) {
                list.add(info);
            }
        }
        return list;
    }
}

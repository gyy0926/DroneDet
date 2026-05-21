package com.win.detect.adapter;

import com.win.detect.model.WifiCSI;
import com.win.detect.model.WifiInfo;

import java.io.IOException;
import java.util.List;

public class CSIAdapter {
/**
 * 1. 结合上一步的WifiList进行遍历
 * 2. 调用以下命令：
 * ```
 * makecsiparams -c 36/20 -C 1 -N 1 -m 80:e4:55:e3:66:c1 -b 0x88
 * ```
 * 命令解释：-c后面是WifiInfo的信道channel/带宽channelWidth，-m后面是Wifi的mac地址
 * 3. 创建类WifiCSI，包含WIfiName和CSIParam都是String类型
 * 4. 调用命令后将生成的CSI保存至`List<WifiCSI>`中
 */
    public static List<WifiCSI> generateCSIParams(List<WifiInfo> wifiList) throws IOException {
        List<WifiCSI> csiList = new java.util.ArrayList<>();
        for (WifiInfo wifiInfo: wifiList) {
            WifiCSI wifiCSI = new WifiCSI();
            wifiCSI.WifiName = wifiInfo.name;

            String cmd = String.format("makecsiparams -c %d/%d -C 1 -N 1 -m %s",
                    wifiInfo.channel, wifiInfo.channelWidth, wifiInfo.mac);
            Process cmdProcess = Runtime.getRuntime().exec(cmd);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(cmdProcess.getInputStream()));
            StringBuilder csiParam = new StringBuilder();
            String line;
            if ((line  = reader.readLine()) == null) {
                continue; // 没有输出，跳过
            }
            csiParam.append(line);
            wifiCSI.CSIParam = csiParam.toString().trim();
            csiList.add(wifiCSI);
        }
        return csiList;
    }


}

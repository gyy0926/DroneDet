package com.win.detect.detect;

import com.win.detect.model.WifiInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class WifiOutputParser {

    public static List<WifiInfo> parse(String output) {
        List<WifiInfo> list = new ArrayList<>();

        WifiInfo current = null;

        boolean inHT = false;
        boolean inVHT = false;

        String htSecondary = null;
        Integer vhtChannelWidth = null;

        String[] lines = output.split("\n");

        for (String line : lines) {
            line = line.trim();

            // 新的 BSS
            if (line.startsWith("BSS ")
                    && line.matches("^BSS\\s+[0-9a-fA-F:]{17}.*")) {
                if (current != null) {
                    current.channelWidth = calcBandwidth(vhtChannelWidth, htSecondary);
                    list.add(current);
                }

                current = new WifiInfo();
                htSecondary = null;
                vhtChannelWidth = null;

                current.mac = line.split("\\s+")[1].split("\\(")[0];
                inHT = false;
                inVHT = false;


            }

            if (current == null) continue;

            if (line.startsWith("freq:")) {
                current.frequency = Integer.parseInt(line.split(":")[1].trim());
            }

            if (line.startsWith("signal:")) {
                current.signalIntensity = Integer.parseInt(
                        line.replace("signal:", "")
                                .replace(".00 dBm", "")
                                .trim()
                );
            }

            if (line.startsWith("SSID:")) {
                current.name = line.substring(5).trim(); // 允许为空
                if (current.name == null || current.name.isEmpty()) {
                    current.name = "unknown";
                }
            }

            if (line.startsWith("HT operation:")) {
                inHT = true;
                inVHT = false;
            }

            if (line.startsWith("VHT operation:")) {
                inVHT = true;
                inHT = false;
            }

            if (inHT && line.contains("primary channel:")) {
                current.channel = Integer.parseInt(
                        line.split(":")[1].trim()
                );
            }

            if (inHT && line.contains("secondary channel offset:")) {
                htSecondary = line.split(":")[1].trim();
            }

            if (inVHT && line.contains("channel width:")) {
                String val = line.split(":")[1].trim();
                vhtChannelWidth = Integer.parseInt(val.split("\\s+")[0]);
            }
        }

        if (current != null) {
            current.channelWidth = calcBandwidth(vhtChannelWidth, htSecondary);
            list.add(current);
        }
//        取消排序因为按照OUI过滤后基本没有元素了，排序太费时间了
        return list;
    }

    /**
     * 带宽算法（完全按你给的规则）
     */
    private static int calcBandwidth(Integer vhtWidth, String htSecondary) {

        // 1. VHT
        if (vhtWidth != null && vhtWidth >= 1) {
            if (vhtWidth == 1) return 80;
            if (vhtWidth == 2) return 160;
        }

        // 2. HT
        if (htSecondary != null) {
            if (htSecondary.equals("above") || htSecondary.equals("below")) {
                return 40;
            }
        }
        return 20;
    }
}

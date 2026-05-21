package com.win.detect.detect;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class WifiScanExecutor {

    public static String scanWifi() throws Exception {
//        修改代码为先执行"ifconfig wlan0 down"，
//        再执行"ifconfig wlan0 up"，确保wlan0接口重启，解决接口状态异常问题。
        Process downProcess = Runtime.getRuntime().exec("su");
        try (DataOutputStream downOs = new DataOutputStream(downProcess.getOutputStream())) {
         downOs.writeBytes("ifconfig wlan0 down\n");
         downOs.writeBytes("exit\n");
         downOs.flush();
        }
        downProcess.waitFor();

        Process upProcess = Runtime.getRuntime().exec("su");
        try (DataOutputStream upOs = new DataOutputStream(upProcess.getOutputStream())) {
         upOs.writeBytes("ifconfig wlan0 up\n");
         upOs.writeBytes("exit\n");
         upOs.flush();
        }
        upProcess.waitFor();
        Thread.sleep(1000);
//        再执行扫描命令，确保wlan0接口处于UP状态
        String[] cmds = new String[]{
                "iw dev wlan0 scan | grep -E \"BSS|SSID:|signal:|freq:|HT operation:|VHT operation:|primary channel:|secondary channel offset:|STA channel width:|channel width:|center freq segment [12]:\" | grep -vE \"^$|^\\s*$\""
        };

        StringBuilder result = new StringBuilder();

        Process process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
        BufferedReader is = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        for (String cmd : cmds) {
            os.writeBytes(cmd + "\n");
        }
        os.writeBytes("exit\n");
        os.flush();

        String line;
        while ((line = is.readLine()) != null) {
            result.append(line).append("\n");
        }

        process.waitFor();
        return result.toString();
    }
}


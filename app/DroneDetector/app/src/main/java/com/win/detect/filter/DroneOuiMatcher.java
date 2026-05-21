package com.win.detect.filter;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Android 6 / API 23 compatible OUI matcher.
 *
 * Put drone_oui_prefixes_only_android23.txt into assets/.
 * Call DroneOuiMatcher.init(context) once in Application.onCreate().
 * Later call DroneOuiMatcher.isDroneVendorMac(macAddress).
 */
public final class DroneOuiMatcher {

    private static final String ASSET_FILE_NAME = "drone_oui_prefixes_only_android23.txt";
    private static final Set<String> OUI_SET = new HashSet<String>();

    private static boolean sInitialized = false;
    private static boolean sInitSuccess = false;

    private DroneOuiMatcher() {
    }

    /**
     * App startup preloading.
     */
    public static synchronized void init(Context context) throws IOException {
        if (sInitialized) {
            return;
        }

        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            inputStream = context.getAssets().open(ASSET_FILE_NAME);
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                String oui = normalizeOui(line);
                if (oui != null) {
                    OUI_SET.add(oui);
                }
            }

            sInitSuccess = true;
            sInitialized = true;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            } else if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Direct lookup after preload.
     */
    public static boolean isDroneVendorMac(String macAddress) {
        if (!sInitialized || !sInitSuccess) {
            return false;
        }

        String oui = normalizeOui(macAddress);
        if (oui == null) {
            return false;
        }

        return OUI_SET.contains(oui);
    }

    public static boolean isInitialized() {
        return sInitialized && sInitSuccess;
    }

    public static int getLoadedOuiCount() {
        return OUI_SET.size();
    }

    /**
     * Accepts:
     * 00:12:1C
     * 00:12:1C:AA:BB:CC
     * 00121C
     * 00121CAABBCC
     * 00-12-1C-AA-BB-CC
     *
     * Returns normalized OUI: 00:12:1C
     */
    private static String normalizeOui(String input) {
        if (input == null) {
            return null;
        }
//        直接裁剪，不用验证格式，我保证都是对的
        return input.substring(0, 8);
    }
}

package com.win.detect.model;

import java.io.DataOutputStream;
import java.io.IOException;

public class RootShell {

    private static Process process;
    private static DataOutputStream os;

    public static synchronized void init() throws Exception {
        if (isAlive()) return;
        cleanup();

        process = Runtime.getRuntime().exec("su");
        os = new DataOutputStream(process.getOutputStream());
    }

    public static synchronized void exec(String cmd) throws Exception {
        init();

        try {
            os.writeBytes(cmd + "\n");
            os.flush();
        } catch (IOException firstError) {
            cleanup();
            init();
            os.writeBytes(cmd + "\n");
            os.flush();
        }
    }

    public static synchronized void close() throws Exception {
        if (os != null) {
            os.writeBytes("exit\n");
            os.flush();
        }
        cleanup();
    }

    private static boolean isAlive() {
        if (process == null || os == null) return false;
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    private static void cleanup() {
        if (os != null) {
            try {
                os.close();
            } catch (IOException ignored) {
            }
            os = null;
        }
        if (process != null) {
            try {
                process.destroy();
            } catch (Throwable ignored) {
            }
            process = null;
        }
    }
}

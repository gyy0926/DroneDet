package com.win.detect.predict;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.util.Log;

import java.io.*;

public final class OnnxDemo {

    private static OnnxPredictor predictor; // 全局唯一
    private static boolean initialized = false;

    private OnnxDemo() {}

    /**
     * 应用启动时调用（只调用一次）
     */
    public static synchronized void init(Context context) throws Exception {
        if (initialized) return;

        // 1. 从 assets 拷贝模型到内部存储
        String modelPath = copyAsset(context, "model_detection.onnx");

        // 2. 初始化 ONNX
        predictor = OnnxPredictor.create(new File(modelPath));

        initialized = true;
    }

    /**
     * 预测（静态调用）
     */
    public static int predict(
            InputStream timeStream,
            InputStream ampStream,
            InputStream phaStream
    ) throws Exception {

        if (!initialized || predictor == null) {
            throw new IllegalStateException("OnnxDemo not initialized. Call init() first.");
        }

        Preprocess.SampleBatch batch = Preprocess.processTripletToSamples(
                timeStream, ampStream, phaStream,
                200, 80, 5, 3.0f
        );

        return predictor.predictMajority(batch.data, 10);
    }

    /**
     * assets → filesDir
     */
    private static String copyAsset(Context context, String name) throws IOException {
        File file = new File(context.getFilesDir(), name);
        Log.d(TAG, "copyAsset: onnx目录在：" + file.getAbsolutePath());

        if (!file.exists()) {
            try (InputStream is = context.getAssets().open(name);
                 FileOutputStream fos = new FileOutputStream(file)) {

                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            }
        }

        return file.getAbsolutePath();
    }
}
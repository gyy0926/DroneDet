package com.win.detect.locate;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.util.Log;

import com.win.detect.predict.OnnxPredictor;
import com.win.detect.predict.Preprocess;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * End-to-end locate(distance) pipeline:
 * 1) Preprocess time/amp/pha txt streams into [N][200*104] samples
 * 2) Run ONNX locate model to get distances
 *
 * Designed so the app can load the ONNX model once at init and reuse it.
 */
public final class LocatePipeline {
    private static OnnxLocatePredictor predictor;

    private static boolean initialized = false;

    public LocatePipeline(OnnxLocatePredictor predictor) {
        this.predictor = predictor;
    }

    public static synchronized void init(Context context) throws Exception {
        if (initialized) return;

        // 1. 从 assets 拷贝模型到内部存储
        String modelPath = copyAsset(context, "model_locate.onnx");

        // 2. 初始化 ONNX
        predictor = OnnxLocatePredictor.create(new File(modelPath));

        initialized = true;
    }

    /**
     * Preprocess then run model.
     * @return aggregated (mean/median) over first voteCount samples, plus per-sample preds for that vote slice.
     */
    public static OnnxLocatePredictor.Result predictFromTriplet(
            InputStream timeStream,
            InputStream ampStream,
            InputStream phaStream,
            int voteCount
    ) throws Exception {
        Preprocess.SampleBatch batch = Preprocess.processTripletToSamples(
                timeStream, ampStream, phaStream,
                200, 80, 5, 3.0f
        );
        return predictor.predictAggregated(batch.data, voteCount);
    }

    /**
     * Convenience factory if you already have a model file on disk (e.g., cached from assets).
     * App should call this once during init and keep the instance.
     */
    public static LocatePipeline create(File onnxModelFile) throws Exception {
        OnnxLocatePredictor p = OnnxLocatePredictor.create(onnxModelFile);
        return new LocatePipeline(p);
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


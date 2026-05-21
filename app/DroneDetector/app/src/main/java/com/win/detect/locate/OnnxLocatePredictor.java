package com.win.detect.locate;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

/**
 * Locate(distance) model ONNX predictor.
 *
 * Model input: [N, 200, 104] float
 * Model output0 ("distance"): [N, 1] float
 *
 * Note: Model file can be packaged as assets then copied to a readable file,
 * or downloaded and cached, then passed into {@link #create(File)}.
 */
public final class OnnxLocatePredictor implements AutoCloseable {
    private final OrtEnvironment ortEnv;
    private final OrtSession session;

    private OnnxLocatePredictor(OrtEnvironment ortEnv, OrtSession session) {
        this.ortEnv = ortEnv;
        this.session = session;
    }

    /**
     * Run a batch of flattened samples.
     * @param batch float[N][200*104]
     * @return distances float[N]
     */
    public float[] runBatchDistances(float[][] batch) throws Exception {
        int n = batch.length;
        if (n == 0) return new float[0];

        long[] shape = new long[]{n, 200, 104};
        float[] flat = new float[n * 200 * 104];
        int k = 0;
        for (int i = 0; i < n; i++) {
            float[] row = batch[i];
            if (row.length != 200 * 104) {
                throw new IllegalArgumentException("Each sample must be 200*104 floats.");
            }
            System.arraycopy(row, 0, flat, k, row.length);
            k += row.length;
        }

        FloatBuffer fb = FloatBuffer.wrap(flat);
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, fb, shape)) {
            String inputName = session.getInputNames().iterator().next();
            Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, inputTensor);
            try (OrtSession.Result outputs = session.run(inputs)) {
                Object v = outputs.get(0).getValue(); // [N][1]
                float[][] out2d = (float[][]) v;
                float[] out = new float[out2d.length];
                for (int i = 0; i < out2d.length; i++) out[i] = out2d[i][0];
                return out;
            }
        }
    }

    /**
     * Aggregate first voteCount predictions with mean and median.
     */
    public Result predictAggregated(float[][] samples, int voteCount) throws Exception {
        if (samples.length == 0) throw new IllegalArgumentException("No samples.");
        int useCount = Math.min(Math.max(voteCount, 1), samples.length);
        float[][] batch = new float[useCount][];
        System.arraycopy(samples, 0, batch, 0, useCount);
        float[] preds = runBatchDistances(batch);
        float mean = mean(preds);
        float median = median(preds);
        return new Result(preds, mean, median);
    }

    public static OnnxLocatePredictor create(File modelFile) throws Exception {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions());
        return new OnnxLocatePredictor(env, session);
    }

    @Override
    public void close() throws Exception {
        session.close();
        // OrtEnvironment is a process-wide singleton; don't close it here.
    }

    public static final class Result {
        public final float[] preds;
        public final float mean;
        public final float median;
        public Result(float[] preds, float mean, float median) {
            this.preds = preds;
            this.mean = mean;
            this.median = median;
        }
    }

    private static float mean(float[] x) {
        double s = 0.0;
        for (float v : x) s += v;
        return (float) (s / x.length);
    }

    private static float median(float[] x) {
        float[] copy = x.clone();
        java.util.Arrays.sort(copy);
        int mid = copy.length / 2;
        if (copy.length % 2 == 0) return (copy[mid - 1] + copy[mid]) * 0.5f;
        return copy[mid];
    }
}


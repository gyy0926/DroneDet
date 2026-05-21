package com.win.detect.predict;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

public final class OnnxPredictor {
    private final OrtEnvironment ortEnv;
    private final OrtSession session;

    private OnnxPredictor(OrtEnvironment ortEnv, OrtSession session) {
        this.ortEnv = ortEnv;
        this.session = session;
    }

    public int predictMajority(float[][] samples, int voteCount) throws Exception {
        if (samples.length == 0) throw new IllegalArgumentException("No samples.");
        int useCount = Math.min(voteCount, samples.length);
        float[][] batch = new float[useCount][];
        System.arraycopy(samples, 0, batch, 0, useCount);
        float[][] logits = runBatch(batch); // [N][3]

        int[] counts = new int[3];
        for (float[] logit : logits) {
            int pred = argmax(logit);
            counts[pred]++;
        }
        return argmax(counts);
    }

    public float[][] runBatch(float[][] batch) throws Exception {
        int n = batch.length;
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
                Object v = outputs.get(0).getValue();
                return (float[][]) v;
            }
        }
    }

    public static OnnxPredictor create(File modelFile) throws Exception {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions());
        return new OnnxPredictor(env, session);
    }

    private static int argmax(float[] x) {
        int best = 0;
        float bestVal = x[0];
        for (int i = 1; i < x.length; i++) {
            if (x[i] > bestVal) {
                bestVal = x[i];
                best = i;
            }
        }
        return best;
    }

    private static int argmax(int[] x) {
        int best = 0;
        int bestVal = x[0];
        for (int i = 1; i < x.length; i++) {
            if (x[i] > bestVal) {
                bestVal = x[i];
                best = i;
            }
        }
        return best;
    }

}

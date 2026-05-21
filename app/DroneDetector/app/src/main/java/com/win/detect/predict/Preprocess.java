package com.win.detect.predict;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Preprocess {
    public static final class SampleBatch {
        public final float[][] data; // shape: [N][200*104]
        public SampleBatch(float[][] data) {
            this.data = data;
        }
    }

    private Preprocess() {}

    public static SampleBatch processTripletToSamples(
            InputStream timeStream,
            InputStream ampStream,
            InputStream phaStream,
            int targetLength,
            int minLen,
            int windowSize,
            float nSigma
    ) throws IOException {
        float[] time = readCsv1D(timeStream);
        float[][] amp = readCsv2D(ampStream);
        float[][] pha = readCsv2D(phaStream);

        if (amp.length == 0 || pha.length == 0) {
            throw new IllegalArgumentException("amp/pha empty.");
        }
        if (amp[0].length != 52 || pha[0].length != 52) {
            throw new IllegalArgumentException("Expected 52 columns for amp/pha.");
        }
        if (time.length != amp.length || time.length != pha.length) {
            throw new IllegalArgumentException("time/amp/pha length mismatch.");
        }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < time.length; i++) {
            int sec = (int) Math.floor(time[i]);

            List<Integer> list = groups.get(sec);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(sec, list);
            }
            list.add(i);
        }

        List<float[]> samples = new ArrayList<>();
        for (List<Integer> idxs : groups.values()) {
            if (idxs.size() < minLen) continue;

            float[][] ampRaw = new float[idxs.size()][52];
            float[][] phaRaw = new float[idxs.size()][52];
            for (int r = 0; r < idxs.size(); r++) {
                int src = idxs.get(r);
                System.arraycopy(amp[src], 0, ampRaw[r], 0, 52);
                System.arraycopy(pha[src], 0, phaRaw[r], 0, 52);
            }

            float[][] ampProcessed = new float[ampRaw.length][52];
            for (int c = 0; c < 52; c++) {
                float[] col = new float[ampRaw.length];
                for (int r = 0; r < ampRaw.length; r++) col[r] = ampRaw[r][c];
                float[] filtered = hampelFilter1D(col, windowSize, nSigma);
                float[] z = zscore1D(filtered, 1e-8f);
                for (int r = 0; r < z.length; r++) ampProcessed[r][c] = z[r];
            }

            float[][] phaProcessed = new float[phaRaw.length][52];
            for (int c = 0; c < 52; c++) {
                float[] col = new float[phaRaw.length];
                for (int r = 0; r < phaRaw.length; r++) col[r] = phaRaw[r][c];
                float[] z = zscore1D(col, 1e-8f);
                for (int r = 0; r < z.length; r++) phaProcessed[r][c] = z[r];
            }

            float[][] merged = new float[ampProcessed.length][104];
            for (int r = 0; r < merged.length; r++) {
                System.arraycopy(ampProcessed[r], 0, merged[r], 0, 52);
                System.arraycopy(phaProcessed[r], 0, merged[r], 52, 52);
            }

            float[][] resampled = resample2D(merged, targetLength);
            float[] flat = new float[targetLength * 104];
            int k = 0;
            for (int r = 0; r < targetLength; r++) {
                for (int c = 0; c < 104; c++) {
                    flat[k++] = resampled[r][c];
                }
            }
            samples.add(flat);
        }

        if (samples.isEmpty()) {
            throw new IllegalArgumentException("No valid samples found. Check input or lower minLen.");
        }
        float[][] out = new float[samples.size()][];
        for (int i = 0; i < samples.size(); i++) out[i] = samples.get(i);
        return new SampleBatch(out);
    }

    private static float[] readCsv1D(InputStream stream) throws IOException {
        List<Float> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(",");
            for (String p : parts) {
                String t = p.trim();
                if (!t.isEmpty()) list.add(Float.parseFloat(t));
            }
        }
        float[] out = new float[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }

    private static float[][] readCsv2D(InputStream stream) throws IOException {
        List<float[]> rows = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(",");
            float[] row = new float[parts.length];
            for (int i = 0; i < parts.length; i++) row[i] = Float.parseFloat(parts[i].trim());
            rows.add(row);
        }
        return rows.toArray(new float[0][]);
    }

    private static float[] zscore1D(float[] x, float eps) {
        double sum = 0.0;
        for (float v : x) sum += v;
        float mean = (float) (sum / x.length);
        double varSum = 0.0;
        for (float v : x) {
            float d = v - mean;
            varSum += d * d;
        }
        float std = (float) Math.sqrt(varSum / x.length);
        float denom = std + eps;
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) out[i] = (x[i] - mean) / denom;
        return out;
    }

    private static float[] hampelFilter1D(float[] x, int windowSize, float nSigma) {
        int n = x.length;
        int k = Math.max(1, windowSize);
        float[] out = x.clone();
        for (int i = 0; i < n; i++) {
            int start = Math.max(0, i - k);
            int end = Math.min(n, i + k + 1);
            float[] window = new float[end - start];
            for (int j = start; j < end; j++) window[j - start] = x[j];
            float med = median(window);
            float mad = medianAbsDev(window, med);
            if (mad < 1e-12f) continue;
            float threshold = nSigma * 1.4826f * mad;
            if (Math.abs(x[i] - med) > threshold) out[i] = med;
        }
        return out;
    }

    private static float median(float[] x) {
        float[] copy = x.clone();
        java.util.Arrays.sort(copy);
        int mid = copy.length / 2;
        if (copy.length % 2 == 0) return (copy[mid - 1] + copy[mid]) * 0.5f;
        return copy[mid];
    }

    private static float medianAbsDev(float[] x, float med) {
        float[] dev = new float[x.length];
        for (int i = 0; i < x.length; i++) dev[i] = Math.abs(x[i] - med);
        return median(dev);
    }

    private static float[][] resample2D(float[][] data, int targetLength) {
        int srcLen = data.length;
        if (srcLen == targetLength) return data;
        int cols = data[0].length;
        float[][] out = new float[targetLength][cols];
        float[] xOld = new float[srcLen];
        float[] xNew = new float[targetLength];
        for (int i = 0; i < srcLen; i++) xOld[i] = (float) i / (float) (srcLen - 1);
        for (int i = 0; i < targetLength; i++) xNew[i] = (float) i / (float) (targetLength - 1);
        for (int c = 0; c < cols; c++) {
            for (int i = 0; i < targetLength; i++) {
                float x = xNew[i];
                int idx = findInterval(xOld, x);
                int idx1 = Math.min(idx + 1, srcLen - 1);
                float x0 = xOld[idx];
                float x1 = xOld[idx1];
                float y0 = data[idx][c];
                float y1 = data[idx1][c];
                float t = (x1 == x0) ? 0f : (x - x0) / (x1 - x0);
                out[i][c] = y0 + t * (y1 - y0);
            }
        }
        return out;
    }

    private static int findInterval(float[] xOld, float x) {
        int lo = 0;
        int hi = xOld.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) / 2;
            if (xOld[mid] <= x) lo = mid;
            else hi = mid;
        }
        return lo;
    }
}

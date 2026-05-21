package com.win.detect.reader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Java rewrite of csireader.m.
 * Reads one Nexmon CSI pcap file, keeps the 52 effective subcarriers for 20 MHz,
 * computes amplitude, timestamp, and sanitized phase, and can write them to text files.
 */
public class CSIReader {
    private static final int HOFFSET = 16;
    private static final int NUM_SUBCARRIERS_20MHZ = 52;
    private static final int[] SUBCARRIER_INDEX_20MHZ = buildSubcarrierIndex20MHz();

    private static final String DEFAULT_CHIP = "4358";
    private static final int DEFAULT_BW = 20;
    private static final int DEFAULT_NPKTS_MAX = 200000;
    private static final String DEFAULT_PATH = "/sdcard/Download/detected_data/";
    private static String DEFAULT_FILENAME;

    public static final class CsiResult {
        public final Complex[][] csiBuff;
        public final double[][] amplitude;
        public final double[][] phase;
        public final double[] timestamp;

        public CsiResult(Complex[][] csiBuff, double[][] amplitude, double[][] phase, double[] timestamp) {
            this.csiBuff = csiBuff;
            this.amplitude = amplitude;
            this.phase = phase;
            this.timestamp = timestamp;
        }
    }

    public static void processPcap(String filename) throws IOException {
        DEFAULT_FILENAME = filename;
        String filePath = buildDefaultPcapPath();
        String chip = DEFAULT_CHIP;
        int bw = DEFAULT_BW;
        int npktsMax = DEFAULT_NPKTS_MAX;

//        todo 添加逻辑，如果已经有txt文件了，就先删除

        CsiResult result = readCsi(filePath, chip, bw, npktsMax);
        String outputPrefix = stripExtension(filePath);
        writeMatrix(result.amplitude, outputPrefix + "_amp.txt");
        writeMatrix(result.phase, outputPrefix + "_pha.txt");
        writeVector(result.timestamp, outputPrefix + "_time.txt");

    }

    public static CsiResult readCsi(String filePath, String chip, int bw, int npktsMax) throws IOException {
        int nfft = (int) Math.round(bw * 3.2);
        ReadPcap reader = new ReadPcap();
        List<Complex[]> csiRows = new ArrayList<>();
        List<Double> timestamps = new ArrayList<>();

        reader.open(filePath);
        try {
            reader.fromStart();
            ReadPcap.Frame frame;
            long baseSec = 0L;
            long baseUsec = 0L;
            boolean firstPacket = true;

            while (csiRows.size() < npktsMax && (frame = reader.next()) != null) {
                if (!isExpectedFrameLength(frame.header.origLen, nfft)) {
                    continue;
                }

                int[] words = bytesToLittleEndianWords(frame.payload);
                if (words.length < HOFFSET + nfft - 1) {
                    continue;
                }

                int start = HOFFSET - 1;
                int[] h = new int[nfft];
                System.arraycopy(words, start, h, 0, nfft);

                int[] decoded = decodeCsi(chip, nfft, h);
                if (decoded.length < nfft * 2) {
                    continue;
                }

                Complex[] row = new Complex[nfft];
                for (int i = 0; i < nfft; i++) {
                    row[i] = new Complex(decoded[i * 2], decoded[i * 2 + 1]);
                }
                csiRows.add(row);

                if (firstPacket) {
                    baseSec = frame.header.tsSec;
                    baseUsec = frame.header.tsUsec;
                    timestamps.add(0.0);
                    firstPacket = false;
                } else {
                    timestamps.add(toRelativeSeconds(baseSec, baseUsec, frame.header.tsSec, frame.header.tsUsec));
                }
            }
        } finally {
            reader.close();
        }

        Complex[][] csiBuff = csiRows.toArray(new Complex[0][]);
        double[] timestamp = toPrimitiveArray(timestamps);
        return postProcess(csiBuff, timestamp, bw);
    }

    public static void writeResult(CsiResult result, String outputPrefix) throws IOException {
        writeMatrix(result.amplitude, outputPrefix + "_amp.txt");
        writeMatrix(result.phase, outputPrefix + "_pha.txt");
        writeVector(result.timestamp, outputPrefix + "_time.txt");
    }

    private static CsiResult postProcess(Complex[][] csiBuff, double[] timestamp, int bw) {
        if (bw != 20) {
            throw new IllegalArgumentException("This rewrite currently follows the 20 MHz MATLAB path only.");
        }

        Complex[][] shifted = CSIProcessor.fftshift2D(csiBuff);
        double[][] amplitude = new double[shifted.length][NUM_SUBCARRIERS_20MHZ];
        double[][] rawPhase = new double[shifted.length][NUM_SUBCARRIERS_20MHZ];

        for (int row = 0; row < shifted.length; row++) {
            int outCol = 0;
            for (int col = 6; col <= 31; col++) {
                amplitude[row][outCol] = shifted[row][col].abs();
                rawPhase[row][outCol] = Math.atan2(shifted[row][col].im, shifted[row][col].re);
                outCol++;
            }
            for (int col = 33; col <= 58; col++) {
                amplitude[row][outCol] = shifted[row][col].abs();
                rawPhase[row][outCol] = Math.atan2(shifted[row][col].im, shifted[row][col].re);
                outCol++;
            }
        }

        double[][] sanitizedPhase = PhaseSanitizer.batchPhaseSanitization(rawPhase, SUBCARRIER_INDEX_20MHZ);
        return new CsiResult(csiBuff, amplitude, sanitizedPhase, timestamp);
    }

    private static boolean isExpectedFrameLength(long origLen, int nfft) {
        return origLen - (long) (HOFFSET - 1) * 4L == (long) nfft * 4L;
    }

    private static int[] decodeCsi(String chip, int nfft, int[] h) {
        if ("4339".equals(chip) || "43455c0".equals(chip)) {
            int[] out = new int[nfft * 2];
            for (int i = 0; i < nfft; i++) {
                int word = h[i];
                out[i * 2] = (short) (word & 0xFFFF);
                out[i * 2 + 1] = (short) ((word >>> 16) & 0xFFFF);
            }
            return out;
        }
        if ("4358".equals(chip)) {
            return UnpackFloat.unpack(0, nfft, h);
        }
        if ("4366c0".equals(chip)) {
            return UnpackFloat.unpack(1, nfft, h);
        }
        throw new IllegalArgumentException("Invalid CHIP: " + chip);
    }

    private static int[] bytesToLittleEndianWords(byte[] payload) {
        int wordCount = payload.length / 4;
        int[] words = new int[wordCount];
        for (int i = 0; i < wordCount; i++) {
            int offset = i * 4;
            words[i] = (payload[offset] & 0xFF)
                    | ((payload[offset + 1] & 0xFF) << 8)
                    | ((payload[offset + 2] & 0xFF) << 16)
                    | ((payload[offset + 3] & 0xFF) << 24);
        }
        return words;
    }

    private static double toRelativeSeconds(long baseSec, long baseUsec, long currentSec, long currentUsec) {
        if (baseUsec > currentUsec) {
            return (double) (currentSec - baseSec) - (double) (baseUsec - currentUsec) / 1_000_000.0;
        }
        return (double) (currentSec - baseSec) + (double) (currentUsec - baseUsec) / 1_000_000.0;
    }

    private static double[][] batchPhaseSanitization(double[][] phase, int[] subcarrierIndex) {
        double[][] sanitized = new double[phase.length][];
        for (int i = 0; i < phase.length; i++) {
            sanitized[i] = sanitizePhaseRow(phase[i], subcarrierIndex);
        }
        return sanitized;
    }

    private static double[] sanitizePhaseRow(double[] row, int[] subcarrierIndex) {
        double[] unwrapped = unwrap(row);
        double slope = linearRegressionSlope(subcarrierIndex, unwrapped);
        double intercept = linearRegressionIntercept(subcarrierIndex, unwrapped, slope);

        double[] sanitized = new double[unwrapped.length];
        for (int i = 0; i < unwrapped.length; i++) {
            sanitized[i] = unwrapped[i] - (slope * subcarrierIndex[i] + intercept);
        }
        return sanitized;
    }

    private static double[] unwrap(double[] row) {
        double[] out = new double[row.length];
        if (row.length == 0) {
            return out;
        }

        out[0] = row[0];
        double offset = 0.0;
        for (int i = 1; i < row.length; i++) {
            double diff = row[i] - row[i - 1];
            if (diff > Math.PI) {
                offset -= 2.0 * Math.PI;
            } else if (diff < -Math.PI) {
                offset += 2.0 * Math.PI;
            }
            out[i] = row[i] + offset;
        }
        return out;
    }

    private static double linearRegressionSlope(int[] x, double[] y) {
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < x.length; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= x.length;
        meanY /= y.length;

        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - meanX;
            numerator += dx * (y[i] - meanY);
            denominator += dx * dx;
        }
        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }

    private static double linearRegressionIntercept(int[] x, double[] y, double slope) {
        double sum = 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += y[i] - slope * x[i];
        }
        return sum / x.length;
    }

    public static void writeMatrix(double[][] matrix, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (double[] row : matrix) {
                writer.write(joinRow(row));
                writer.newLine();
            }
        }
    }

    public static void writeVector(double[] vector, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(joinRow(vector));
            writer.newLine();
        }
    }

    private static String joinRow(double[] row) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.US, "%.10f", row[i]));
        }
        return builder.toString();
    }

    private static double[] toPrimitiveArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static int[] buildSubcarrierIndex20MHz() {
        int[] indices = new int[NUM_SUBCARRIERS_20MHZ];
        int pos = 0;
        for (int i = -26; i <= -1; i++) {
            indices[pos++] = i;
        }
        for (int i = 1; i <= 26; i++) {
            indices[pos++] = i;
        }
        return indices;
    }

    private static String stripExtension(String path) {
        int index = path.lastIndexOf('.');
        return index >= 0 ? path.substring(0, index) : path;
    }

    private static String buildDefaultPcapPath() {
        return new File(DEFAULT_PATH, DEFAULT_FILENAME + ".pcap").getPath();
    }
}

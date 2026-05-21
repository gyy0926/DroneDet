package com.win.detect.reader;

import java.io.*;
import java.util.Locale;

/**
 * CSIProcessor: compute fftshift along subcarrier axis, magnitudes and phases.
 * Instead of plotting, it writes magnitude and phase CSV files (one row per packet).
 */
public class CSIProcessor {

    /**
     * Performs fftshift along columns for a 2D complex array (packets x subcarriers).
     */
    public static Complex[][] fftshift2D(Complex[][] data) {
        int rows = data.length;
        if (rows == 0) return data;
        int cols = data[0].length;
        int half = cols / 2;
        Complex[][] out = new Complex[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (c + half) % cols;
                out[r][c] = data[r][idx];
            }
        }
        return out;
    }

    /**
     * Compute magnitude matrix and phase matrix (degrees), and write them to CSV files.
     * magnitudeFile and phaseFile will be overwritten if exist.
     */
    public static void writeMagnitudeAndPhaseCSV(Complex[][] csi, String magnitudeFile, String phaseFile) throws IOException {
        Complex[][] shifted = fftshift2D(csi);
        int pkts = shifted.length;
        int nfft = pkts == 0 ? 0 : shifted[0].length;

        // compute magnitudes and phases and write to CSV
        try (BufferedWriter magW = new BufferedWriter(new FileWriter(magnitudeFile));
             BufferedWriter phW = new BufferedWriter(new FileWriter(phaseFile))) {

            // header: subcarrier indices
            StringBuilder hdr = new StringBuilder();
            for (int i = 0; i < nfft; i++) {
                if (i > 0) hdr.append(',');
                hdr.append("sc").append(i);
            }
            magW.write(hdr.toString()); magW.newLine();
            phW.write(hdr.toString()); phW.newLine();

            for (int p = 0; p < pkts; p++) {
                StringBuilder magLine = new StringBuilder();
                StringBuilder phLine = new StringBuilder();
                for (int s = 0; s < nfft; s++) {
                    double mag = shifted[p][s].abs();
                    double ph = shifted[p][s].phaseDegrees();
                    if (s > 0) {
                        magLine.append(',');
                        phLine.append(',');
                    }
                    magLine.append(String.format(Locale.US, "%.6f", mag));
                    phLine.append(String.format(Locale.US, "%.6f", ph));
                }
                magW.write(magLine.toString()); magW.newLine();
                phW.write(phLine.toString()); phW.newLine();
            }
        }
    }

    /**
     * Also provide simple console summary printing to inspect data quickly.
     */
    public static void printSummary(Complex[][] csi) {
        int pkts = csi.length;
        int nfft = pkts == 0 ? 0 : csi[0].length;
        System.out.println("Packets: " + pkts + ", Subcarriers (NFFT): " + nfft);
        if (pkts > 0) {
            double maxMag = 0;
            for (int p = 0; p < pkts; p++) {
                for (int s = 0; s < nfft; s++) {
                    double m = csi[p][s].abs();
                    if (m > maxMag) maxMag = m;
                }
            }
            System.out.println("Max magnitude across all packets/subcarriers: " + maxMag);
        }
    }
}


package com.win.detect.reader;
/**
 * Phase sanitization utility. Matches batch_phase_sanitization.m behavior.
 */
public final class PhaseSanitizer {
    private PhaseSanitizer() {
    }

    public static double[][] batchPhaseSanitization(double[][] rawPhases, int[] subcarrierIndex) {
        if (rawPhases.length == 0) {
            return rawPhases;
        }

        int numSubcarriers = rawPhases[0].length;
        if (numSubcarriers != subcarrierIndex.length) {
            throw new IllegalArgumentException("Subcarrier index count does not match phase columns.");
        }

        double kMean = 0.0;
        for (int k : subcarrierIndex) {
            kMean += k;
        }
        kMean /= subcarrierIndex.length;

        double[] kDiff = new double[subcarrierIndex.length];
        for (int i = 0; i < subcarrierIndex.length; i++) {
            kDiff[i] = subcarrierIndex[i] - kMean;
        }

        double deltaK = subcarrierIndex[subcarrierIndex.length - 1] - subcarrierIndex[0];
        double[][] cleaned = new double[rawPhases.length][numSubcarriers];

        for (int row = 0; row < rawPhases.length; row++) {
            double phiMean = 0.0;
            for (int col = 0; col < numSubcarriers; col++) {
                phiMean += rawPhases[row][col];
            }
            phiMean /= numSubcarriers;

            double deltaPhi = rawPhases[row][numSubcarriers - 1] - rawPhases[row][0];
            double slope = deltaPhi / deltaK;

            for (int col = 0; col < numSubcarriers; col++) {
                cleaned[row][col] = rawPhases[row][col] - phiMean - slope * kDiff[col];
            }
        }

        return cleaned;
    }
}

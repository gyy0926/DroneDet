package com.win.detect.reader;

import java.io.File;
import java.io.IOException;

/**
 * Java rewrite of saveMulticsi.m.
 * Iterates through the configured pcap sets and exports amplitude, phase, and timestamp files.
 */
public class SaveMultiCSI {
    private static final String CHIP = "4358";
    private static final int BW = 20;
    private static final int NPKTS_MAX = 200000;
    private static final int SAMPLE_COUNT = 40;

    private static final DatasetConfig[] DATASETS = new DatasetConfig[] {
            new DatasetConfig("dronedata/adata", "a"),
            new DatasetConfig("dronedata/b16mdata", "b16"),
            new DatasetConfig("dronedata/b17mdata", "b17"),
            new DatasetConfig("dronedata/b18mdata", "b18"),
            new DatasetConfig("dronedata/b19mdata", "b19"),
            new DatasetConfig("dronedata/b20mdata", "b20")
    };

    private static final String[] DEVICE_SUFFIXES = new String[] { "_" };

    public static void main(String[] args) throws IOException {
        for (DatasetConfig dataset : DATASETS) {
            for (String deviceSuffix : DEVICE_SUFFIXES) {
                for (int sample = 1; sample <= SAMPLE_COUNT; sample++) {
                    processOne(dataset, deviceSuffix, sample);
                }
            }
        }
    }

    private static void processOne(DatasetConfig dataset, String deviceSuffix, int sample) throws IOException {
        String baseName = dataset.name + deviceSuffix + sample;
        File pcapFile = new File(dataset.path, baseName + ".pcap");

        if (!pcapFile.isFile()) {
            System.out.println("Skip missing file: " + pcapFile.getPath());
            return;
        }

        System.out.println("Processing " + pcapFile.getPath());
        CSIReader.CsiResult result = CSIReader.readCsi(pcapFile.getPath(), CHIP, BW, NPKTS_MAX);
        String outputPrefix = new File(dataset.path, baseName).getPath();
        CSIReader.writeResult(result, outputPrefix);
    }

    private static final class DatasetConfig {
        private final String path;
        private final String name;

        private DatasetConfig(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }
}

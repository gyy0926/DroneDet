package com.win.detect.locate;

import java.io.File;

/**
 * Finds the time/amp/pha txt triplet inside a directory.
 *
 * Expected fixed filenames:
 * - time.txt
 * - amp.txt
 * - pha.txt
 */
public final class TripletFiles {
    public final File timeFile;
    public final File ampFile;
    public final File phaFile;

    private static final String TIME_NAME = "_time.txt";
    private static final String AMP_NAME = "_amp.txt";
    private static final String PHA_NAME = "_pha.txt";

    private TripletFiles(File timeFile, File ampFile, File phaFile) {
        this.timeFile = timeFile;
        this.ampFile = ampFile;
        this.phaFile = phaFile;
    }

    public static TripletFiles findInDirectory(File dir,String fileName) {
        if (dir == null || !dir.isDirectory()) {
            throw new IllegalArgumentException("txt dir not found: " + (dir == null ? "null" : dir.getAbsolutePath()));
        }

        File time = new File(dir, fileName + TIME_NAME);
        File amp = new File(dir, fileName + AMP_NAME);
        File pha = new File(dir, fileName + PHA_NAME);

        if (!time.isFile() || !amp.isFile() || !pha.isFile()) {
            throw new IllegalArgumentException(
                    "Missing triplet .txt in " + dir.getAbsolutePath()
                            + "\nNeed fixed files: " + TIME_NAME + ", " + AMP_NAME + ", " + PHA_NAME);
        }
        return new TripletFiles(time, amp, pha);
    }
}

package com.win.detect.reader;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal pcap reader similar in behavior to the MATLAB readpcap.m
 * Note: only supports little-endian pcap (common on modern systems)
 */
public class ReadPcap {
    private RandomAccessFile raf;
    private long dataStartOffset = 24; // after global header
    private GlobalHeader globalHeader;

    public static class GlobalHeader {
        public long magicNumber;
        public int versionMajor;
        public int versionMinor;
        public int thiszone;
        public long sigfigs;
        public long snaplen;
        public long network;
    }

    public static class Frame {
        public Header header;
        public byte[] payload;
    }

    public static class Header {
        public long tsSec;
        public long tsUsec;
        public long inclLen;
        public long origLen;
    }

    public void open(String filename) throws IOException {
        raf = new RandomAccessFile(filename, "r");
        raf.seek(0);

        byte[] buf4 = new byte[4];
        raf.readFully(buf4);
        long magic = getUInt32LE(buf4, 0);

        GlobalHeader gh = new GlobalHeader();
        gh.magicNumber = magic;
        gh.versionMajor = readUInt16LE();
        gh.versionMinor = readUInt16LE();
        gh.thiszone = readInt32LE();
        gh.sigfigs = readUInt32LE();
        gh.snaplen = readUInt32LE();
        gh.network = readUInt32LE();

        this.globalHeader = gh;
    }

    public void fromStart() throws IOException {
        raf.seek(dataStartOffset);
    }

    public Frame next() throws IOException {
        Frame frame = new Frame();
        Header h = new Header();

        try {
            h.tsSec = readUInt32LE();
        } catch (EOFException e) {
            return null;
        }

        h.tsUsec = readUInt32LE();
        h.inclLen = readUInt32LE();
        h.origLen = readUInt32LE();

        if (h.inclLen == 0) {
            return null;
        }

        frame.header = h;

        // read payload as bytes
        long incl = h.inclLen;
        if (incl > Integer.MAX_VALUE) {
            throw new IOException("Frame too large");
        }
        byte[] payload = new byte[(int) incl];
        raf.readFully(payload);
        frame.payload = payload;
        return frame;
    }

    public List<Frame> all() throws IOException {
        List<Frame> frames = new ArrayList<>();
        fromStart();
        while (true) {
            Frame f = next();
            if (f == null) break;
            frames.add(f);
        }
        return frames;
    }

    public void close() throws IOException {
        if (raf != null) raf.close();
    }

    // helper little-endian readers
    private int readUInt16LE() throws IOException {
        int lo = raf.readUnsignedByte();
        int hi = raf.readUnsignedByte();
        return (hi << 8) | lo;
    }

    private long readUInt32LE() throws IOException {
        byte[] b = new byte[4];
        raf.readFully(b);
        return getUInt32LE(b, 0);
    }

    private int readInt32LE() throws IOException {
        byte[] b = new byte[4];
        raf.readFully(b);
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        return bb.getInt();
    }

    private static long getUInt32LE(byte[] b, int off) {
        return ((b[off] & 0xFFL)) |
                ((b[off + 1] & 0xFFL) << 8) |
                ((b[off + 2] & 0xFFL) << 16) |
                ((b[off + 3] & 0xFFL) << 24);
    }
}


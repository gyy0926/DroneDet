package com.win.detect.reader;

/**
 * Java translation of the unpack_float.c MEX logic (unpack_float_acphy).
 *
 * Public API:
 *   int[] unpack(int format, int nfft, int[] Hwords)
 * Returns an int array of length nfft*2 (VI and VQ interleaved) analogous to Hout in MATLAB.
 *
 * Note: Hwords is the uint32 words extracted from payload (each word provided as int but treated unsigned where needed).
 */
public class UnpackFloat {

    // convenience wrapper
    public static int[] unpack(int format, int nfft, int[] Hwords) {
        if (format != 0 && format != 1) {
            throw new IllegalArgumentException("format must be 0 or 1");
        }
        if (Hwords.length < nfft) {
            throw new IllegalArgumentException("Hwords length must be at least nfft");
        }
        // call underlying method with parameters matching C code
        if (format == 0) {
            return unpackFloatAcphy(10, 0, 0, 1, 9, 5, nfft, Hwords);
        } else {
            return unpackFloatAcphy(10, 0, 0, 1, 12, 6, nfft, Hwords);
        }
    }

    // core port of unpack_float_acphy
    private static int[] unpackFloatAcphy(int nbits, int autoscale, int shft,
                                          int fmt, int nman, int nexp, int nfft,
                                          int[] H) {
        int e_p, maxbit, e, pwr_shft = 0, e_zero, sgn;
        int n_out, e_shift;
        byte[] He = new byte[256];
        int vi, vq;
        int[] pOut;
        long x;
        long iq_mask, e_mask, sgnr_mask, sgni_mask;

        iq_mask = (1L << (nman - 1)) - 1L;
        e_mask = (1L << nexp) - 1L;
        e_p = (1 << (nexp - 1));
        sgnr_mask = (1L << (nexp + 2 * nman - 1));
        sgni_mask = (sgnr_mask >> nman);
        e_zero = -nman;
        // pOut reference omitted, we'll assemble Hout array later
        n_out = (nfft << 1);
        e_shift = 1;
        maxbit = -e_p;
        int[] Hout = new int[n_out];
        // iterate per-sample to compute vi, vq and He
        for (int i = 0; i < nfft; i++) {
            int word = H[i];
            // treat word as unsigned 32-bit
            long wordU = word & 0xFFFFFFFFL;

            // vi: top bits >> (nexp + nman)
            vi = (int) ((wordU >> (nexp + nman)) & iq_mask);
            vq = (int) ((wordU >> nexp) & iq_mask);
            e = (int) (wordU & e_mask);
            if (e >= e_p) e -= (e_p << 1);
            He[i] = (byte) e;
            x = ( (long)vi & 0xFFFFFFFFL ) | ( (long)vq & 0xFFFFFFFFL );
            if (autoscale != 0 && x != 0L) {
                long m = 0xffff0000L;
                long b = 0xffffL;
                int s = 16;
                while (s > 0) {
                    if ((x & m) != 0L) {
                        e += s;
                        x >>= s;
                    }
                    s >>= 1;
                    m = (m >> s) & b;
                    b >>= s;
                }
                if (e > maxbit) maxbit = e;
            }
            // sign extension detection
            if ((wordU & sgnr_mask) != 0L) {
                vi |= (1 << 31); // set sign bit
            }
            if ((wordU & sgni_mask) != 0L) {
                vq |= (1 << 31);
            }
            Hout[i << 1] = vi;
            Hout[(i << 1) + 1] = vq;
        }

        shft = nbits - maxbit;
        int pIndex = 0;
        for (int i = 0; i < n_out; i++) {
            e = He[(i >> e_shift)] + shft;
            int val = Hout[pIndex];
            pIndex++;
            int sgnFlag = 1;
            if ((val & (1 << 31)) != 0) {
                sgnFlag = -1;
                val &= ~(1 << 31);
            }
            int outVal;
            if (e < e_zero) {
                outVal = 0;
            } else if (e < 0) {
                int ee = -e;
                outVal = (val >> ee);
            } else {
                outVal = (val << e);
            }
            Hout[i] = sgnFlag * outVal;
        }

        return Hout;
    }
}


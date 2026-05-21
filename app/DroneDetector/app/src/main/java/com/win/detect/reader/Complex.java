package com.win.detect.reader;

public class Complex {
    public final double re;
    public final double im;

    public Complex(double re, double im) {
        this.re = re;
        this.im = im;
    }

    public double abs() {
        return Math.hypot(re, im);
    }

    public double phaseDegrees() {
        return Math.toDegrees(Math.atan2(im, re));
    }

    public Complex add(Complex o) {
        return new Complex(this.re + o.re, this.im + o.im);
    }

    public Complex sub(Complex o) {
        return new Complex(this.re - o.re, this.im - o.im);
    }

    public Complex mul(Complex o) {
        return new Complex(this.re * o.re - this.im * o.im, this.re * o.im + this.im * o.re);
    }

    public String toString() {
        return String.format("%.6f%+.6fi", re, im);
    }
    public static void main(String[] args) {

    }
}

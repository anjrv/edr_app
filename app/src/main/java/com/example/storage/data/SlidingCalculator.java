package com.example.storage.data;

import java.util.ArrayList;
import java.util.Random;

/**
 * Helper class for sliding window statistics using Welford's method
 */
@SuppressWarnings("unused")
public class SlidingCalculator {
    private final Window w;
    private double mean;
    private double square;
    private double dSq;

    public SlidingCalculator(int size) {
        this.mean = 0;
        this.square = 0; // NOTE: this can overflow for large window sizes
        this.dSq = 0;
        this.w = new Window(size);
    }

    public void update(double value) {
        Double oldest = this.w.append(value);

        if (oldest == null) {
            if (this.w.getCount() == 1) {
                this.mean = value;
                this.square = Math.pow(value, 2);
                this.dSq = 0;
            } else {
                double meanIncrement = (value - this.mean) / (double) this.w.getCount();
                double nextMean = this.mean + meanIncrement;

                double dSqIncrement = (value - nextMean) * (value - this.mean);
                double nextDsq = this.dSq + dSqIncrement;

                this.mean = nextMean;
                this.square += Math.pow(value, 2);
                this.dSq = nextDsq;
            }
        } else {
            double meanIncrement = (value - oldest) / (double) this.w.getCount();
            double nextMean = this.mean + meanIncrement;

            double dSqIncrement = (value - oldest) * (value - nextMean + oldest - this.mean);
            double nextDsq = this.dSq + dSqIncrement;

            this.mean = nextMean;
            this.square = this.square + (Math.pow(value, 2) - Math.pow(oldest, 2));
            this.dSq = nextDsq;
        }
    }

    public int getCount() {
        return this.w.getCount();
    }

    public double getMean() {
        return this.mean;
    }

    public double getRms() {
        return Math.sqrt(this.square / (double) this.w.getCount());
    }

    public double getVariance() {
        return this.dSq / (double) this.w.getCount();
    }

    public double getStd() {
        return Math.sqrt(this.getVariance());
    }

    private static class Window {

        private final double[] buffer;
        private int index;
        private int count;

        public Window(int size) {
            this.buffer = new double[size];
            this.index = 0;
            this.count = 0;
        }

        public int getCount() {
            return this.count;
        }

        public Double append(double value) {
            Double oldest = null;

            if (this.count == this.buffer.length) {
                oldest = this.buffer[this.index];
            } else {
                this.count++;
            }

            this.buffer[this.index] = value;
            this.index = (this.index + 1) % this.buffer.length;

            return oldest;
        }
    }
}
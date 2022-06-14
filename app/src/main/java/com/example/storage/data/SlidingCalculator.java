package com.example.storage.data;

/**
 * Helper class for sliding window statistics using Welford's method
 */
@SuppressWarnings("unused")
public class SlidingCalculator {
    private final Window w;
    private double mean;
    private double dSq;

    public SlidingCalculator() {
        this.mean = 0;
        this.dSq = 0;
        this.w = new Window();
    }

    public void update(double value) {
        Double oldest = this.w.append(value);

        if (oldest == null) {
            if (this.w.getCount() == 1) {
                this.mean = value;
                this.dSq = 0;
            } else {
                double meanIncrement = (value - this.mean) / this.w.getCount();
                double nextMean = this.mean + meanIncrement;

                double dSqIncrement = (value - nextMean) * (value - this.mean);
                double nextDsq = this.dSq + dSqIncrement;

                this.mean = nextMean;
                this.dSq = nextDsq;
            }
        } else {
            double meanIncrement = (value - oldest) / this.w.getCount();
            double nextMean = this.mean + meanIncrement;

            double dSqIncrement = (value - oldest) * (value - nextMean + oldest - this.mean);
            double nextDsq = this.dSq + dSqIncrement;

            this.mean = nextMean;
            this.dSq = nextDsq;
        }
    }

    public int getCount() {
        return this.w.getCount();
    }

    public double getMean() {
        return this.mean;
    }

    public double getPopulationVariance() {
        return this.dSq / this.w.getCount();
    }

    public double getPopulationStd() {
        return Math.sqrt(this.getPopulationVariance());
    }

    static class Window {
        private final double[] buffer;
        private int index;
        private int count;

        public Window() {
            this.buffer = new double[500];
            this.index = 0;
            this.count = 0;
        }

        public int getCount() {
            return this.count;
        }

        public Double append(double value) {
            Double oldest = null;

            if (this.count >= this.buffer.length) {
                oldest = this.buffer[this.index];
            }

            this.buffer[this.index] = value;
            this.index = (this.index + 1) % this.buffer.length;
            this.count++;

            return oldest;
        }
    }
}

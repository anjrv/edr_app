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
                double meanIncrement = (value - this.mean) / (double) this.w.getCount();
                double nextMean = this.mean + meanIncrement;

                double dSqIncrement = (value - nextMean) * (value - this.mean);
                double nextDsq = this.dSq + dSqIncrement;

                this.mean = nextMean;
                this.dSq = nextDsq;
            }
        } else {
            double meanIncrement = (value - oldest) / (double) this.w.getCount();
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

    private static double findMean(ArrayList<Double> nums) {
        double sum = 0.0;

        for (int i = 0; i < nums.size(); i++) {
            sum += nums.get(i);
        }

        return sum / (double) nums.size();
    }

    private static double findStd(ArrayList<Double> nums) {
        double mean = findMean(nums);

        double squareSum = 0.0;

        for (int i = 0; i < nums.size(); i++) {
            squareSum += Math.pow(nums.get(i) - mean, 2);
        }

        return Math.sqrt(squareSum / (double) nums.size());
    }

    // Double check validity...
    public static void main(String[] args) {
        Random rand = new Random();
        SlidingCalculator sc = new SlidingCalculator();
        ArrayList<Double> test = new ArrayList<>(500);

        for (int i = 0; i < 500; i++) {
            double d = rand.nextDouble();
            sc.update(d);
            test.add(d);
        }

        System.out.println("Means: " + findMean(test) + ", " + sc.getMean());
        System.out.println("Std: " + findStd(test) + ", " + sc.getStd());

        for (int i = 0; i < 200; i++) {
            double d = rand.nextDouble();
            sc.update(d);
            test.set(i, d);
        }

        System.out.println("Means: " + findMean(test) + ", " + sc.getMean());
        System.out.println("Std: " + findStd(test) + ", " + sc.getStd());
    }
}
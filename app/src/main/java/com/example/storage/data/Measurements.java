package com.example.storage.data;

import com.example.storage.data.Measurement;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

/**
 * Storage wrapper for measurements
 *
 * This is necessary to ensure that measurement memory is static with app life time
 * and is not lost with a forced application pause
 */
public class Measurements {
    public static final Semaphore sMeasSemaphore = new Semaphore(1, true);

    public static int consecutiveMeasurements = 0;

    public static Double[] zVal = new Double[3];
    public static Double[] z = new Double[3];
    public static Double[] x = new Double[3];
    public static Double[] y = new Double[3];
    public static Double[] w = new Double[3];

    public static ArrayList<Measurement> sData = new ArrayList<>();
}

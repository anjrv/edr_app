package com.example.storage.data;

import android.location.Location;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

/**
 * Storage wrapper for measurements
 * <p>
 * This is necessary to ensure that measurement memory is static with app life time
 * and is not lost with a forced application pause
 */
public class Measurements {
    public static volatile boolean sensorHasConnection = false;
    public static volatile boolean backlogHasConnection = false;

    public static final Semaphore sLocSemaphore = new Semaphore(1, true);
    public static Location sCurrLoc = null;

    public static final Semaphore sMeasSemaphore = new Semaphore(1, true);
    public static int consecutiveMeasurements = 0;
    public static final Float[] zVal = new Float[3];
    public static final Double[] z = new Double[3];
    public static final Double[] x = new Double[3];
    public static final Double[] y = new Double[3];
    public static final Double[] w = new Double[3];

    public static final ArrayList<Measurement> sData = new ArrayList<>();
}

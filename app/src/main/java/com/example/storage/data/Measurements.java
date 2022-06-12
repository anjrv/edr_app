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

    public static volatile double sLongitude = Double.NEGATIVE_INFINITY; // Canary to check write
    public static volatile double sLatitude = Double.MIN_VALUE;
    public static volatile double sAltitude = Double.MIN_VALUE;
    public static volatile float sSpeed = Float.MIN_VALUE;
    public static volatile float sAccuracy = Float.MIN_VALUE;

    public static final Float[] zVal = new Float[3];
    public static final Double[] z = new Double[3];
    public static final Double[] x = new Double[3];
    public static final Double[] y = new Double[3];
    public static final Double[] w = new Double[3];

    public static volatile int consecutiveMeasurements = 0;
    public static volatile boolean firstArray = true;
    public static volatile int currIdx = 0;

    public static final ArrayList<Measurement> sData1 = new ArrayList<>(10000);
    public static final ArrayList<Measurement> sData2 = new ArrayList<>(10000);

}

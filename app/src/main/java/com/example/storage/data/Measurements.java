package com.example.storage.data;

import java.util.ArrayList;

/**
 * Storage wrapper for measurements
 * <p>
 * This is necessary to ensure that measurement memory is static with app life time
 * and is not lost with a forced application pause
 */
public class Measurements {
    public static final Float[] Z_VAL = new Float[3];
    public static final Double[] Z = new Double[3];
    public static final Double[] X = new Double[3];
    public static final Double[] Y = new Double[3];
    public static final Double[] W = new Double[3];
    public static final Double[] Z_GAIN = new Double[3];
    public static final Double[] X_GAIN = new Double[3];
    public static final Double[] Y_GAIN = new Double[3];
    public static final Double[] W_GAIN = new Double[3];
    public static final ArrayList<Measurement> DATA_1 = new ArrayList<>(10000);
    public static final ArrayList<Measurement> DATA_2 = new ArrayList<>(10000);
    public static volatile boolean sBacklogHasConnection = false;
    public static volatile double sLongitude = Double.NEGATIVE_INFINITY; // Canary to check write
    public static volatile double sLatitude = Double.MIN_VALUE;
    public static volatile double sAltitude = Double.MIN_VALUE;
    public static volatile float sSpeed = Float.MIN_VALUE;
    public static volatile float sAccuracy = Float.MIN_VALUE;
    public static volatile boolean sFirstArray = true;
    public static volatile int sCurrIdx = 0;

}

package com.example.storage;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

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

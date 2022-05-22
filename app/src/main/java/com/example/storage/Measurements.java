package com.example.storage;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class Measurements {
    public static final Semaphore sMeasSemaphore = new Semaphore(1, true);
    public static ArrayList<Measurement> sData = new ArrayList<>();
}

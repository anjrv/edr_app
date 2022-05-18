package com.example.storage;

import java.util.ArrayList;

public class Measurements {
    // Not explicitly thread safe but we are currently only adding
    // UI reads can be relatively delayed without too much issue
    public static ArrayList<Measurement> sData = new ArrayList<>();
}

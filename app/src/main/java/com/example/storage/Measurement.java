package com.example.storage;

import androidx.annotation.NonNull;

public class Measurement implements Cloneable {
    private final double zValue;
    private double filteredZValue;
    private final long time;
    private final double longitude;
    private final double latitude;
    private final double altitude;
    private final float speed;
    private final float accuracy;

    public Measurement(Double zValue, Double filteredZValue, Long time, Double longitude, Double latitude, Double altitude, Float speed, Float accuracy) {
        this.zValue = zValue;
        this.filteredZValue = filteredZValue;
        this.time = time;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        this.speed = speed;
        this.accuracy = accuracy;
    }

    public double getzValue() {
        return zValue;
    }

    public void setFilteredZValue(Double filteredZValue) { this.filteredZValue = filteredZValue; }

    public double getFilteredZValue() { return filteredZValue; }

    public long getTime() {
        return time;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public float getSpeed() {
        return speed;
    }

    public float getAccuracy() {
        return accuracy;
    }

    @NonNull
    public Object clone() throws CloneNotSupportedException {
        Measurement clone = null;
        clone = (Measurement) super.clone();

        return clone;
    }
}

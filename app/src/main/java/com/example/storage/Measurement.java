package com.example.storage;

import androidx.annotation.NonNull;

/**
 * Container Object for individual moment to moment measurements
 */
public class Measurement implements Cloneable {
    private final double zValue;
    private double filteredZValue;
    private final long time;
    private final double longitude;
    private final double latitude;
    private final double altitude;
    private final float speed;
    private final float accuracy;

    /**
     * Container class for storing moment to moment measurements
     *
     * @param zValue The z Acceleration value read from the sensor
     * @param filteredZValue The four section filter z value result
     * @param time Time of measurement in UTC milliseconds
     * @param longitude The longitude obtained by location measurement
     * @param latitude The latitude obtained by location measurement
     * @param altitude The altitude obtained by location measurement
     * @param speed The estimated speed obtained by location measurement
     * @param accuracy The percentage accuracy estimation of the location sensor during the measurement
     */
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
    public Measurement clone() throws CloneNotSupportedException {
        return (Measurement) super.clone();
    }
}

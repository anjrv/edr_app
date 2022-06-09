package com.example.storage.data;

import androidx.annotation.NonNull;

/**
 * Container Object for individual moment to moment measurements
 */
public class Measurement implements Cloneable {
    private final String time;
    private final float lon;
    private final float lat;
    private final float alt;
    private final float ms;
    private final float acc;
    private final float z;
    private double fz;

    /**
     * Container class for storing moment to moment measurements
     *
     * @param z     The z Acceleration value read from the sensor
     * @param fz    The four section filter z value result
     * @param time  Time of measurement in UTC milliseconds
     * @param lon   The longitude obtained by location measurement
     * @param lat   The latitude obtained by location measurement
     * @param alt   The altitude obtained by location measurement
     * @param speed The estimated speed obtained by location measurement given in m/s
     * @param acc   The percentage accuracy estimation of the location sensor during the measurement
     */
    public Measurement(Float z, Double fz, String time, Double lon, Double lat, Double alt, Float speed, Float acc) {
        this.z = z;
        this.fz = fz;
        this.time = time;
        this.lon = lon.floatValue();
        this.lat = lat.floatValue();
        this.alt = alt.floatValue();
        this.ms = speed;
        this.acc = acc;
    }

    public float getZ() {
        return z;
    }

    public double getFz() {
        return fz;
    }

    public void setFz(Double fz) {
        this.fz = fz;
    }

    public String getTime() {
        return time;
    }

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }

    public double getAlt() {
        return alt;
    }

    public float getMs() {
        return ms;
    }

    public float getAcc() {
        return acc;
    }

    @NonNull
    public Measurement clone() throws CloneNotSupportedException {
        return (Measurement) super.clone();
    }
}

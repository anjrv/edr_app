package com.example.storage.data;

import androidx.annotation.NonNull;

/**
 * Container Object for individual moment to moment measurements
 */
@SuppressWarnings({"unused", "CommentedOutCode"})
public class Measurement implements Cloneable {
    private String time;
    private float lon;
    private float lat;
    private float alt;
    private float ms;
    private float ms0;
    private float acc;
    private float z;
    private double fz;
    // private double std;
    // private double edr;
    private double rms;
    private double edr_rms;

    public Measurement() {
    }

    /**
     * @param z      The z Acceleration value read from the sensor
     * @param fz     The four section filter z value result
     * @param time   Time of measurement in UTC milliseconds
     * @param lon    The longitude obtained by location measurement
     * @param lat    The latitude obtained by location measurement
     * @param alt    The altitude obtained by location measurement
     * @param speed  The estimated speed obtained by location measurement given in m/s
     * @param speed1 The estimated speed obtained by calculating distance traversed
     * @param acc    The percentage accuracy estimation of the location sensor during the measurement
     * @param edrRms The Eddy Dissipation Rate obtained from fz and speed1
     */
    public Measurement(Float z, Double fz, String time, Double lon, Double lat, Double alt, Float speed, Float speed1, Float acc, /* Double std, Double edr, */ Double edrRms) {
        this.z = z;
        this.fz = fz;
        this.time = time;
        this.lon = lon.floatValue();
        this.lat = lat.floatValue();
        this.alt = alt.floatValue();
        this.ms = speed;
        this.ms0 = speed1;
        this.acc = acc;
        // this.std = std;
        // this.edr = edr;
        this.edr_rms = edrRms;
    }

    public float getZ() {
        return z;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public double getFz() {
        return fz;
    }

    public void setFz(double fz) {
        this.fz = fz;
    }

    // public double getStd() {
    //     return std;
    // }

    // public void setStd(double std) {
    //     this.std = std;
    // }

    // public double getEdr() {
    //     return edr;
    // }

    // public void setEdr(double edr) {
    //     this.edr = edr;
    // }

    public double getRms() {
        return rms;
    }

    public void setRms(double rms) {
        this.rms = rms;
    }

    public double getEdrRms() {
        return edr_rms;
    }

    public void setEdrRms(double edr_rms) {
        this.edr_rms = edr_rms;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon.floatValue();
    }

    public double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat.floatValue();
    }

    public double getAlt() {
        return alt;
    }

    public void setAlt(Double alt) {
        this.alt = alt.floatValue();
    }

    public float getMs() {
        return ms;
    }

    public void setMs(float ms) {
        this.ms = ms;
    }

    public float getMs0() {
        return ms0;
    }

    public void setMs0(float ms) {
        this.ms0 = ms;
    }

    public float getAcc() {
        return acc;
    }

    public void setAcc(float acc) {
        this.acc = acc;
    }

    @NonNull
    public Measurement clone() throws CloneNotSupportedException {
        return (Measurement) super.clone();
    }
}

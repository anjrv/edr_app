package com.example.storage;

public class Measurement {
    private final Float zValue;
    private final Long time;
    private final Double longitude;
    private final Double latitude;
    private final Double altitude;
    private final Float speed;
    private final Float accuracy;

    public Measurement(Float zValue, Long time, Double longitude, Double latitude, Double altitude, Float speed, Float accuracy) {
        this.zValue = zValue;
        this.time = time;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        this.speed = speed;
        this.accuracy = accuracy;
    }

    public Float getzValue() {
        return zValue;
    }

    public Long getTime() {
        return time;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getAltitude() {
        return altitude;
    }

    public Float getSpeed() {
        return speed;
    }

    public Float getAccuracy() {
        return accuracy;
    }
}

package com.example.storage;

public class Measurement {
    private Float zValue;
    private String time;
    private String date;
    private Double longitude;
    private Double latitude;
    private Double altitude;
    private Float speed;
    private Float accuracy;

    public Measurement() {}

    public Measurement(Float zValue, String time, String date, Double longitude, Double latitude, Double altitude, Float speed, Float accuracy) {
        this.zValue = zValue;
        this.time = time;
        this.date = date;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        this.speed = speed;
        this.accuracy = accuracy;
    }

    public Float getzValue() {
        return zValue;
    }

    public void setzValue(Float zValue) {
        this.zValue = zValue;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    public Float getSpeed() {
        return speed;
    }

    public void setSpeed(Float speed) {
        this.speed = speed;
    }

    public Float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Float accuracy) {
        this.accuracy = accuracy;
    }
}

package com.example.storage;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Dataframe Object for the purpose of constructing messages
 *
 * Getters and Setters are required for serialization
 */
public class Dataframe implements Serializable {
    private String brand;
    private String manufacturer;
    private String model;
    private String id;
    private String version;
    private ArrayList<Measurement> data;

    public Dataframe(String brand, String manufacturer, String model, String id, String version, ArrayList<Measurement> data) {
        this.brand = brand;
        this.manufacturer = manufacturer;
        this.model = model;
        this.id = id;
        this.version = version;
        this.data = data;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public ArrayList<Measurement> getData() {
        return data;
    }

    public void setData(ArrayList<Measurement> data) {
        this.data = data;
    }
}

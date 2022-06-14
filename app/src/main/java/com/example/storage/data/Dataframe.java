package com.example.storage.data;

import java.io.Serializable;
import java.util.List;

/**
 * Dataframe Object for the purpose of constructing messages
 * <p>
 * Getters and Setters are required for serialization
 */
@SuppressWarnings("unused") // The getters are used for JSON conversion
public class Dataframe implements Serializable {
    private String brand;
    private String manufacturer;
    private String model;
    private String id;
    private String version;
    private String session;
    private List<Measurement> data;

    public Dataframe() {

    }

    public Dataframe(String brand, String manufacturer, String model, String id, String version, String session, List<Measurement> data) {
        this.brand = brand;
        this.manufacturer = manufacturer;
        this.model = model;
        this.id = id;
        this.version = version;
        this.session = session;
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

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public List<Measurement> getData() {
        return data;
    }

    public void setData(List<Measurement> data) {
        this.data = data;
    }
}

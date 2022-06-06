package com.example.storage.data;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Dataframe Object for the purpose of constructing messages
 * <p>
 * Getters and Setters are required for serialization
 */
@SuppressWarnings("unused") // The getters are used for JSON conversion
public class Dataframe implements Serializable {
    private final String brand;
    private final String manufacturer;
    private final String model;
    private final String id;
    private final String version;
    private final String session;
    private final ArrayList<Measurement> data;

    public Dataframe(String brand, String manufacturer, String model, String id, String version, String session, ArrayList<Measurement> data) {
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

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getSession() {
        return session;
    }

    public ArrayList<Measurement> getData() {
        return data;
    }
}

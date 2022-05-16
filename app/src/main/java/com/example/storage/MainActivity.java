package com.example.storage;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.LogPrinter;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import java.lang.Math;
import java.util.Vector;

public class MainActivity extends AppCompatActivity implements SensorEventListener{

    //GPS
    public static final int DEFAULT_UPDATE_INTERVAL = 1;
    public static final int FAST_UPDATE_INTERVAL = 1;
    private static final int PERMISSION_FINE_LOCATION = 99;

    //Variable definition
    public float zAcc; public double lat; public double lon; public double alt; public float accuracy; public float speed;
    public float b0; public float b1; public float b2; public float a0; public double a1; public double a2;
    public float b01; public float b11; public float b21; public float a01; public double a11; public double a21;
    public float b02; public float b12; public float b22; public float a02; public double a12; public double a22;
    public float b03; public float b13; public float b23; public float a03; public double a13; public double a23;
    public double data1; public double data2; public double data3; public double data4; public double datagain;

    //References to the UI elements
    TextView tv_lat, tv_lon, tv_altitude, tv_accuracy, tv_speed, tv_address, zvalue, time, aserial, amodel, aid, amanuf, abrand, aversioncode;
    LocationRequest locationRequest;
    LocationCallback locationCallBack;
    FusedLocationProviderClient fusedLocationProviderClient;
    //GPS ENDS

    //Accelerometer definitoin
    private static final String TAG = "MainActivity";
    private SensorManager sensorManager;
    Sensor accelerometer;

    //Switch
    Switch switchBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //GPS
        //Give each UI variable a value
        tv_lat = findViewById(R.id.tv_lat);
        tv_lon = findViewById(R.id.tv_lon);
        tv_altitude = findViewById(R.id.tv_altitude);
        tv_accuracy = findViewById(R.id.tv_accuracy);
        tv_speed = findViewById(R.id.tv_speed);
        tv_address = findViewById(R.id.tv_address);
        aserial =  findViewById(R.id.aserial);
        amodel =  findViewById(R.id.amodel);
        amanuf =  findViewById(R.id.amanuf);
        aid =   findViewById(R.id.aid);
        aversioncode =   findViewById(R.id.aversioncode);
        abrand =  findViewById(R.id.abrand);

        //switch
        switchBtn = findViewById(R.id.switchBtn);
        switchBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            //If swhich is checked, start recording data (not working yet)
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked){
                            Toast.makeText(getBaseContext(),"ON", Toast.LENGTH_SHORT).show();
                            //Displayed on phone
                            //Phone Info -
                            aserial.setText(Build.SERIAL);
                            aid.setText(Build.ID);
                            aversioncode.setText(Build.VERSION.RELEASE);
                            abrand.setText(Build.BRAND);
                            amanuf.setText(Build.MANUFACTURER);
                            amodel.setText(Build.MODEL);

                            time.setText(String.valueOf(getTime()));
                            //Z Acceleration
                            zvalue.setText(String.valueOf(getZvalue()));

                            //GPS
                            tv_lat.setText(String.valueOf(getTv_lat()));
                            tv_lon.setText(String.valueOf(getTv_lon()));
                            tv_accuracy.setText(String.valueOf(getTv_accuracy()));
                            tv_altitude.setText(String.valueOf(getTv_altitude()));
                            tv_speed.setText(String.valueOf(getTv_speed()));
                            tv_address.setText(String.valueOf(getTv_address()));

                        }else {
                            Toast.makeText(getBaseContext(), "OFF", Toast.LENGTH_SHORT).show();
                            aserial.setText("N/A");
                            aid.setText("N/A");
                            aversioncode.setText("N/A");
                            abrand.setText("N/A");
                            amanuf.setText("N/A");
                            amodel.setText("N/A");
                        }
                    }
            });

        //set all properties of locationReq
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1 * DEFAULT_UPDATE_INTERVAL);
        //How often does the location check occur when set to the most frequent update?
        locationRequest.setFastestInterval(1 * FAST_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        locationCallBack = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                //save the location
                updateUIValues(locationResult.getLastLocation());
            }
        };

        //most accurate - use GPS
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        updateGPS();
        startLocationUpdates();
        //GPS ENDS

        //Accelerometer
        Log.d(TAG, "onCreate: Initializing Sensor Services");
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        Log.d(TAG, "onCreate: Registered Accelerometer listener");
        //Date and time
        time = findViewById(R.id.time);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
    //Define an array list
    ArrayList<Float> arrayZvalues = new ArrayList<Float>();
    ArrayList<String> arrayTime = new ArrayList<String>();
    ArrayList<String> arrayDate = new ArrayList<String>();
    ArrayList<Double> arrayLon = new ArrayList<Double>();
    ArrayList<Double> arrayLat = new ArrayList<Double>();
    ArrayList<Double> arrayAlt = new ArrayList<Double>();
    ArrayList<Float> arraySpeed = new ArrayList<Float>();
    ArrayList<Float> arrayAccuracy = new ArrayList<Float>();
    ArrayList<Float> filterZAcc = new ArrayList<Float>();
    ArrayList<Double> z = new ArrayList<Double>();
    ArrayList<Double> x = new ArrayList<Double>();
    ArrayList<Double> y = new ArrayList<Double>();
    ArrayList<Double> w = new ArrayList<Double>();

    //Z Axis Acceleration - Date and time
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //Date and time
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss a");
        SimpleDateFormat simpleDatesFormat = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat simpleTimeFormat = new SimpleDateFormat("hh:mm:ss ");
        String dateTime = simpleDateFormat.format(calendar.getTime());
        String date123 = simpleDatesFormat.format(calendar.getTime());
        String time123 = simpleTimeFormat.format(calendar.getTime());
        zvalue =  findViewById(R.id.zValue);
        zvalue.setText(String.valueOf(sensorEvent.values[2]));
        time.setText((dateTime));
        //Get Z acceleration
        zAcc = sensorEvent.values[2];
        //Store Z acceleration in an array
        arrayZvalues.add(zAcc);
        arrayTime.add(time123);
        arrayDate.add(date123);
        arrayLon.add(lon);
        arrayLat.add(lat);
        arrayAlt.add(alt);
        arraySpeed.add(speed);
        arrayAccuracy.add(accuracy);

    }
    //Vector<Double> z = new Vector<Double>();

    public class filtering {
        public void main(String args[]) {
            //Low pass filter first section
            //constants
            b0 = 1;
            b1 = 2;
            b2 = 1;
            a0 = 1;
            a1 = -1.94921595802584;
            a2 = 0.953069895327891;

            //Build low pass filter first section
            // y(n) = b0x(n) + b1x(n-1) + b2(x(n-2) - a1y(n-1) - a2y(n-2)
            z.add(0, (double) (1 * arrayZvalues.get(0)) * 0.000963484325512291);
            z.add(1, (b0 * arrayZvalues.get(1) + b1 * arrayZvalues.get(0) - a1 * arrayZvalues.get(0)) * 0.000963484325512291);
            int i = 0;
            for (i = 2; i < arrayZvalues.size(); i++) {
                data1 = (b0 * arrayZvalues.get(i) + b1 * arrayZvalues.get(i - 1) + b2 * arrayZvalues.get(i - 2) - (a1) * z.get(i - 1) - (a2) * z.get(i - 2));
                datagain = data1 * 0.000963484325512291;
                z.add(i, datagain);
            }

            //low pass filter second section
            //constants
            b01 = 1;
            b11 = 2;
            b21 = 1;
            a01 = 1;
            a11 = -1.88660958262151;
            a21 = 0.890339736284024;

            //Build low pass filter second section
            x.add(0, b01 * z.get(0) * 0.000932538415629474);
            x.add(1, b01 * z.get(1) + b11 * z.get(0) - a11 * z.get(0) * 0.000932538415629474);
            for (i = 2; i < arrayZvalues.size(); i++) {
                data2 = ((b01 * z.get(i) + b11 * z.get(i - 1) + b21 * z.get(i - 2) - a11 * x.get(i - 1) - a21 * x.get(i - 2)));
                x.add(i, 0.000932538415629474 * data2);
            }

            //high pass filter first section
            //constants
            b02 = 1;
            b12 = -2;
            b22 = 1;
            a02 = 1;
            a12 = -1.999037095803727126;
            a22 = 0.9990386741811910775;

            //Build high pass filter first section
            y.add(0, b02 * x.get(0) * 0.999518942496229523);
            y.add(1, b02 * x.get(1) + b12 * x.get(0) - a12 * z.get(0) * 0.999518942496229523);
            for (i = 2; i < arrayZvalues.size(); i++) {
                data3 = (b02 * x.get(i) + b12 * x.get(i - 1) + b22 * x.get(i - 2) - a12 * y.get(i - 1) - a22 * y.get(i - 2));
                y.add(i, 0.999518942496229523 * data3);
            }

            //high pass filter second section
            //constants
            b03 = 1;
            b13 = -2;
            b23 = 1;
            a03 = 1;
            a13 = -1.99767915341159740;
            a23 = 0.997680730716872465;

            //Build high pass filter second section
            w.add(0, b03 * y.get(0) * 0.998839971032117524);
            w.add(1, b03 * y.get(1) + b13 * y.get(0) - a13 * y.get(0) * 0.998839971032117524);
            for (i = 2; i < arrayZvalues.size(); i++) {
                data4 = (b03 * y.get(i) + b13 * y.get(i - 1) + b23 * y.get(i - 2) - a13 * w.get(i - 1) - a23 * w.get(i - 2));
                w.add(i, 0.998839971032117524 * data4);
            }
        }

    }

    //Define z acceleration and time outside of loop
    public TextView getZvalue() { return zvalue; }
    public TextView getTime() {return time; }

    //GPS Tracking loops
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallBack, null);
        updateGPS();
    }

    //app requires premission from user to use gps
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_FINE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //If premission is granted use the GPS
                    updateGPS();
                } else {
                    //if premission is not granted display the following message
                    Toast.makeText(this, "This app requires permission to be granted in order to work properly", Toast.LENGTH_SHORT).show();
                    finish();
                }break;
        }
    }

    private void updateGPS() {
        //get permission from the user to track GPS
        //get the current location from the fused client
        //update the UI - i.e. set all properties in their associated text view items
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            //User provided the permission
            //we got permission. Put the value of location. XXX into UI components
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, this::updateUIValues);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_FINE_LOCATION); }
        }
    }

    //Updating UI values
    private void updateUIValues(Location location) {
        //update all of the text view object with a new location
        tv_lat.setText(String.valueOf(location.getLatitude()));
        tv_lon.setText(String.valueOf(location.getLongitude()));
        tv_accuracy.setText(String.valueOf(location.getAccuracy()));
        lat = location.getLatitude();
        lon = location.getLongitude();
        accuracy = location.getAccuracy();
        alt = location.getAltitude();
        speed = location.getSpeed();

        if (location.hasAltitude()) {
            tv_altitude.setText(String.valueOf(location.getAltitude()));
        } else {
            tv_altitude.setText("Not available");
        }
        if (location.hasSpeed()) {
            tv_speed.setText(String.valueOf(location.getSpeed()));
        } else {
            tv_speed.setText("Not available");
        }
        Geocoder geocoder = new Geocoder(MainActivity.this);
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            tv_address.setText(addresses.get(0).getAddressLine(0));

        } catch (Exception e) {
            tv_address.setText("Unable to get street address");
        }
        //Display values in Logcat
       // Log.d(TAG, "updateUIValues: LAT: " + location.getLatitude() + " LON:" + location.getLongitude() + " ALT: " + location.getAltitude() + " SPEED: " + location.getSpeed());
    }

    //Define values outside of loop
    public TextView getTv_lat() { return tv_lat; }
    public TextView getTv_lon() { return tv_lon; }
    public TextView getTv_accuracy() { return tv_accuracy; }
    public TextView getTv_altitude() { return tv_altitude; }
    public TextView getTv_speed() { return tv_speed; }
    public TextView getTv_address() { return tv_address; }
    //End of GPS

    //Store in Excel CSV file
    public void export(View view) {


        //generate data
        StringBuilder data = new StringBuilder();
        data.append("Date,Time,Z Acc, Z Acc filter,Latitude,Longitude,Altitude,Speed,Accuracy,Address,Serial,Model,ID,Manufacturer,Brand,Version code");
        int i = 0;
        String arrayDateFinal = arrayDate.get(i);
        //for each index, take the value from the arrays
        //For loop is currently not displaying the arrays correctly into the csv file
        for(i= 0 ; i < arrayZvalues.size(); i++){
            data.append("\n"+ arrayDateFinal +","+arrayTime.get(i)+","+arrayZvalues.get(i)+','+ z.get(i) +","+arrayLat.get(i)+","+arrayLon.get(i)+","+arrayAlt.get(i)+","+arraySpeed.get(i)+","+arrayAccuracy.get(i)+","+"address"+","+Build.SERIAL+","+Build.MODEL+","+Build.ID+","+Build.MANUFACTURER+","+Build.BRAND+","+Build.VERSION.RELEASE);
        }

        try{
            //saving the file into device
            FileOutputStream out = openFileOutput("data.csv", Context.MODE_PRIVATE);
            out.write((data.toString()).getBytes());
            out.close();

            //exporting
            Context context = getApplicationContext();
            File filelocation = new File(getFilesDir(), "data.csv");
            Uri path = FileProvider.getUriForFile(context, "com.example.Storage.fileprovider", filelocation);
            Intent fileIntent = new Intent(Intent.ACTION_SEND);
            fileIntent.setType("text/csv");
            fileIntent.putExtra(Intent.EXTRA_SUBJECT, "Data");
            fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fileIntent.putExtra(Intent.EXTRA_STREAM, path);
            startActivity(Intent.createChooser(fileIntent, "Send mail"));
        }
        catch(Exception e){ e.printStackTrace(); }
    }
}
package com.example.storage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Process;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.storage.databinding.ActivityMainBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding mBinding;

    private volatile Location mCurrLoc;
    private AtomicInteger mCurrMeasurement = new AtomicInteger();

    private FusedLocationProviderClient mFusedLocationProviderClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private Looper mLocationLooper;

    private Geocoder mGeocoder;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);

        HandlerThread locationThread = new HandlerThread("loc");
        locationThread.start();
        mLocationLooper = locationThread.getLooper();

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        new Thread(() -> {
            Looper.prepare();
            Handler sensorHandler = new Handler();
            sensorManager.registerListener(mSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST, sensorHandler);
            Looper.loop();
        }).start();

        mGeocoder = new Geocoder(this);

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mBinding.switchBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(getBaseContext(), "ON", Toast.LENGTH_SHORT).show();
                startLocationUpdates();
                mBinding.aid.setText(Build.ID);
                mBinding.aversioncode.setText(Build.VERSION.RELEASE);
                mBinding.abrand.setText(Build.BRAND);
                mBinding.amanuf.setText(Build.MANUFACTURER);
                mBinding.amodel.setText(Build.MODEL);
            } else {
                if (mFusedLocationProviderClient != null)
                    mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
            }
        });

        mLocationRequest = LocationRequest.create()
                .setInterval(2)
                .setFastestInterval(2)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (mBinding.switchBtn.isChecked()) mCurrLoc = locationResult.getLastLocation();
            }
        };

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                runOnUiThread(() -> {
                    int idx = mCurrMeasurement.get();
                    if (idx > 0) {
                        updateUI(Measurements.sData.get(idx - 1));
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSensorManager != null )
            mSensorManager.unregisterListener(mSensorEventListener);

        if (mFusedLocationProviderClient != null)
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSensorManager != null)
            mSensorManager.registerListener(mSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        startLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        int PERMISSION_FINE_LOCATION = 99;
        if (requestCode == PERMISSION_FINE_LOCATION) {
            if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "This app requires permission to be granted in order to work properly", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }

        if (mFusedLocationProviderClient == null)
            mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        else
            mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest, mLocationCallback, mLocationLooper);
    }

    @SuppressLint("SetTextI18n")
    private void updateUI(Measurement m) {
        mBinding.zValue.setText((String.valueOf(m.getzValue())));
        mBinding.tvLat.setText(String.valueOf(m.getLatitude()));
        mBinding.tvLon.setText(String.valueOf(m.getLongitude()));
        mBinding.tvAccuracy.setText(String.valueOf(m.getAccuracy()));
        mBinding.tvAltitude.setText(String.valueOf(m.getAltitude()));
        mBinding.tvSpeed.setText(String.valueOf(m.getSpeed()));
        mBinding.time.setText(m.getDate() + m.getTime());

        try {
            List<Address> addresses = mGeocoder.getFromLocation(m.getLatitude(), m.getLongitude(), 1);
            mBinding.tvAddress.setText(addresses.get(0).getAddressLine(0));
        } catch (Exception e) {
            mBinding.tvAddress.setText("Unable to get street address");
        }
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        @SuppressLint("SimpleDateFormat")
        public void onSensorChanged(SensorEvent event) {
            if (mBinding.switchBtn.isChecked() && mCurrLoc != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat simpleDatesFormat = new SimpleDateFormat("dd-MM-yyyy");
                SimpleDateFormat simpleTimeFormat = new SimpleDateFormat("hh:mm:ss ");
                String date = simpleDatesFormat.format(calendar.getTime());
                String time = simpleTimeFormat.format(calendar.getTime());
                Float zAcc = event.values[2];

                Measurement m = new Measurement(
                    zAcc, time, date, mCurrLoc.getLongitude(), mCurrLoc.getLatitude(), mCurrLoc.getAltitude(), mCurrLoc.getSpeed(), mCurrLoc.getAccuracy()
                );

                Measurements.sData.add(m);
                mCurrMeasurement.set(Measurements.sData.size());
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) { }
    };

    public void export(View view) {
        System.out.println(Measurements.sData.size());
        // Gonna use for MQTT testing instead
    }

    /*
    Store in Excel CSV file
     public void export(View view) {
         //generate data
         StringBuilder data = new StringBuilder();
         data.append("Date,Time,Z Acc, Z Acc filter,Latitude,Longitude,Altitude,Speed,Accuracy,Address,Serial,Model,ID,Manufacturer,Brand,Version code");
         int i = 0;
         String arrayDateFinal = arrayDate.get(i);
         //for each index, take the value from the arrays
         //For loop is currently not displaying the arrays correctly into the csv file
         for (i = 0; i < arrayZvalues.size(); i++) {
             data.append("\n").append(arrayDateFinal).append(",").append(arrayTime.get(i)).append(",").append(arrayZvalues.get(i)).append(',').append(z.get(i)).append(",").append(arrayLat.get(i)).append(",").append(arrayLon.get(i)).append(",").append(arrayAlt.get(i)).append(",").append(arraySpeed.get(i)).append(",").append(arrayAccuracy.get(i)).append(",").append("address").append(",").append(Build.SERIAL).append(",").append(Build.MODEL).append(",").append(Build.ID).append(",").append(Build.MANUFACTURER).append(",").append(Build.BRAND).append(",").append(Build.VERSION.RELEASE);
         }
         try {
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
         } catch (Exception e) {
             e.printStackTrace();
         }
     }
     public class filtering {
         public void main(String[] args) {
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
    */

}
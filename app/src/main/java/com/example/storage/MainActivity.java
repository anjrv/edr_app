package com.example.storage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity {
    private final Semaphore mLocSemaphore = new Semaphore(1, true);

    private ActivityMainBinding mBinding;
    private Timer mViewTimer;
    private int mApproxRefresh;

    private volatile boolean switchToggled;

    private Location mCurrLoc; // Sensor and Location threads both need to use this
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private Looper mLocationLooper;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private final int PERMISSION_FINE_LOCATION = 99;

    private final double B0 = 1;
    private final double B1 = 2;
    private final double B2 = 1;
    private final double A1 = -1.94921595802584;
    private final double A2 = 0.953069895327891;
    private final double B01 = 1;
    private final double B11 = 2;
    private final double B21 = 1;
    private final double A11 = -1.88660958262151;
    private final double A21 = 0.890339736284024;
    private final double B02 = 1;
    private final double B12 = -2;
    private final double B22 = 1;
    private final double A12 = -1.999037095803727126;
    private final double A22 = 0.9990386741811910775;
    private final double B03 = 1;
    private final double B13 = -2;
    private final double B23 = 1;
    private final double A13 = -1.99767915341159740;
    private final double A23 = 0.997680730716872465;

    /**
     * Listener for accelerometer events
     * <p>
     * Obtains most recent location and creates a new measurement entry
     * Updates most recent measurement number for UI refresh function to use
     */
    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        @SuppressLint("SimpleDateFormat")
        public void onSensorChanged(SensorEvent event) {
            if (!switchToggled) Measurements.consecutiveMeasurements = 0;

            if (switchToggled && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float zAcc = event.values[2];
                long time = System.currentTimeMillis();

                try {
                    Measurement m = null;
                    mLocSemaphore.acquire();

                    if (mCurrLoc != null) {
                        m = new Measurement(
                                (double) zAcc, 0.0, time, mCurrLoc.getLongitude(), mCurrLoc.getLatitude(),
                                mCurrLoc.getAltitude(), mCurrLoc.getSpeed(), mCurrLoc.getAccuracy()
                        );
                    }

                    mLocSemaphore.release();

                    if (m != null) {
                        Measurements.sMeasSemaphore.acquire();
                        if (Measurements.consecutiveMeasurements == 0) {
                            Measurements.zVal[0] = m.getzValue();
                            Measurements.z[0] = (B0 * Measurements.zVal[0]) * 0.000963484325512291;
                            Measurements.x[0] = (B01 * Measurements.z[0]) * 0.000932538415629474;
                            Measurements.y[0] = (B02 * Measurements.x[0]) * 0.999518942496229523;
                            Measurements.w[0] = (B03 * Measurements.y[0]) * 0.998839971032117524;

                            m.setFilteredZValue(Measurements.w[0]);
                        } else if (Measurements.consecutiveMeasurements == 1) {
                            Measurements.zVal[1] = m.getzValue();

                            Measurements.z[1] = (B0 * Measurements.zVal[1] + B1 * Measurements.zVal[0]
                                    - A1 * Measurements.z[0]) * 0.000963484325512291;

                            Measurements.x[1] = (B01 * Measurements.z[1] + B11 * Measurements.z[0]
                                    - A11 * Measurements.x[0]) * 0.000932538415629474;

                            Measurements.y[1] = (B02 * Measurements.x[1] + B12 * Measurements.x[0]
                                    - A12 * Measurements.y[0]) * 0.999518942496229523;

                            Measurements.w[1] = (B03 * Measurements.y[1] + B13 * Measurements.y[0]
                                    - A13 * Measurements.w[0]) * 0.998839971032117524;

                            m.setFilteredZValue(Measurements.w[1]);
                        } else {
                            if (Measurements.consecutiveMeasurements > 2) {
                                // Shift running stats to the left
                                Measurements.zVal[0] = Measurements.zVal[1];
                                Measurements.zVal[1] = Measurements.zVal[2];

                                Measurements.z[0] = Measurements.z[1];
                                Measurements.z[1] = Measurements.z[2];

                                Measurements.x[0] = Measurements.x[1];
                                Measurements.x[1] = Measurements.x[2];

                                Measurements.y[0] = Measurements.y[1];
                                Measurements.y[1] = Measurements.y[2];

                                Measurements.w[0] = Measurements.w[1];
                                Measurements.w[1] = Measurements.w[2];
                            }

                            Measurements.zVal[2] = m.getzValue();

                            double zData = (B0 * Measurements.zVal[2]
                                    + B1 * Measurements.zVal[1]
                                    + B2 * Measurements.zVal[0]
                                    - (A1) * Measurements.z[1] - (A2) * Measurements.z[0]);

                            Measurements.z[2] = zData * 0.000963484325512291;

                            double xData = (B01 * Measurements.z[2] + B11 * Measurements.z[1]
                                    + B21 * Measurements.z[0] - A11 * Measurements.x[1] - A21 * Measurements.x[0]);

                            Measurements.x[2] = xData * 0.000932538415629474;

                            double yData = (B02 * Measurements.x[2] + B12 * Measurements.x[1]
                                    + B22 * Measurements.x[0] - A12 * Measurements.y[1] - A22 * Measurements.y[0]);

                            Measurements.y[2] = yData * 0.999518942496229523;

                            double wData = (B03 * Measurements.y[2] + B13 * Measurements.y[1]
                                    + B23 * Measurements.y[0] - A13 * Measurements.w[1] - A23 * Measurements.w[0]);

                            Measurements.w[2] = wData * 0.998839971032117524;

                            m.setFilteredZValue(Measurements.w[2]);
                        }

                        Measurements.consecutiveMeasurements++;
                        Measurements.sData.add(m);
                        Measurements.sMeasSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) { /* Unused */ }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = mBinding.getRoot();
        setContentView(view);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Thread looper to be used for accelerometer callbacks
        new Thread(() -> {
            Looper.prepare();
            Handler sensorHandler = new Handler();
            mSensorManager
                    .registerListener(mSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST, sensorHandler);
            Looper.loop();
        }).start();

        // Thread looper to be used for location callbacks
        HandlerThread locationThread = new HandlerThread("loc");
        locationThread.start();
        mLocationLooper = locationThread.getLooper();
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationRequest = LocationRequest.create()
                .setInterval(1000)
                .setFastestInterval(500)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (switchToggled) {
                    try {
                        mLocSemaphore.acquire();
                        mCurrLoc = locationResult.getLastLocation();
                        mLocSemaphore.release();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        switchToggled = false;
        mBinding.switchBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchToggled = true;
                Toast.makeText(getBaseContext(), "ON", Toast.LENGTH_SHORT).show();
                startLocationUpdates();
            } else {
                switchToggled = false;
                if (mFusedLocationProviderClient != null)
                    mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
            }
        });

        mBinding.aid.setText(Build.ID);
        mBinding.aversioncode.setText(Build.VERSION.RELEASE);
        mBinding.abrand.setText(Build.BRAND);
        mBinding.amanuf.setText(Build.MANUFACTURER);
        mBinding.amodel.setText(Build.MODEL);

        mApproxRefresh = (int) (1000 / ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay()
                .getRefreshRate()) + 1;

        mViewTimer = new Timer();
        scheduleUITimer();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mViewTimer != null)
            mViewTimer.cancel();

        if (mSensorManager != null)
            mSensorManager.unregisterListener(mSensorEventListener);

        if (mFusedLocationProviderClient != null)
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mViewTimer = new Timer();
        scheduleUITimer();

        if (mSensorManager != null)
            mSensorManager
                    .registerListener(mSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        startLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_FINE_LOCATION) {
            if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "This app requires permission to be granted in order to work properly", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_FINE_LOCATION);
        }

        if (mFusedLocationProviderClient == null)
            mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        else
            mFusedLocationProviderClient
                    .requestLocationUpdates(mLocationRequest, mLocationCallback, mLocationLooper);
    }

    /**
     * Helper function to kick off screen updates to slightly slower than screen refresh
     *
     * Approximate timing is impossible and assigning faster than refresh updates crashes eventually
     * due to vsync inconsistencies.
     */
    private void scheduleUITimer() {
        mViewTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (switchToggled) {
                    runOnUiThread(() -> {
                        try {
                            Measurements.sMeasSemaphore.acquire();

                            int idx = Measurements.sData.size() - 1;
                            if (idx > 0) {
                                updateUI(Measurements.sData.get(idx));
                            }

                            Measurements.sMeasSemaphore.release();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        }, 0, mApproxRefresh);
    }

    /**
     * Helper function to update on screen information with recent...ish values
     */
    @SuppressLint("SimpleDateFormat")
    private void updateUI(Measurement m) {
        mBinding.zValue.setText((String.valueOf(m.getzValue())));
        mBinding.tvLat.setText(String.valueOf(m.getLatitude()));
        mBinding.tvLon.setText(String.valueOf(m.getLongitude()));
        mBinding.tvAccuracy.setText(String.valueOf(m.getAccuracy()));
        mBinding.tvAltitude.setText(String.valueOf(m.getAltitude()));
        mBinding.tvSpeed.setText(String.valueOf(m.getSpeed()));
        mBinding.time
                .setText(new SimpleDateFormat("dd MMM yyyy HH:mm")
                        .format(new Date(m.getTime())));
    }

    /**
     * Currently used as a debug test function
     *
     * @param view the calling view
     */
    public void export(View view) {
        try {
            Measurements.sMeasSemaphore.acquire();
            Toast.makeText(this, String.valueOf(Measurements.sData.size()), Toast.LENGTH_SHORT).show();

            if (Measurements.sData.size() == 0) {
                Measurements.sMeasSemaphore.release();
                return;
            }

            ArrayList<Double> z = new ArrayList<>();
            ArrayList<Double> x = new ArrayList<>();
            ArrayList<Double> y = new ArrayList<>();
            ArrayList<Double> w = new ArrayList<>();

            double data1;
            double datagain;

            //Build low pass filter first section
            // y(n) = b0x(n) + b1x(n-1) + b2(x(n-2) - a1y(n-1) - a2y(n-2)
            z.add(0, (B0 * Measurements.sData.get(0).getzValue()) * 0.000963484325512291);
            z.add(1, (B0 * Measurements.sData.get(1).getzValue() + B1 * Measurements.sData.get(0).getzValue() - A1 * z.get(0)) * 0.000963484325512291);

            int i = 0;
            for (i = 2; i < Measurements.sData.size(); i++) {
                data1 = (B0 * Measurements.sData.get(i).getzValue() + B1 * Measurements.sData.get(i - 1).getzValue() + B2 * Measurements.sData.get(i - 2).getzValue() - (A1) * z.get(i - 1) - (A2) * z.get(i - 2));
                datagain = data1 * 0.000963484325512291;
                z.add(i, datagain);
            }

            //low pass filter second section
            //constants

            double data2;
            double datagain2;

            //Build low pass filter second section
            x.add(0, B01 * z.get(0) * 0.000932538415629474);
            x.add(1, (B01 * z.get(1) + B11 * z.get(0) - A11 * x.get(0)) * 0.000932538415629474);
            for (i = 2; i < Measurements.sData.size(); i++) {
                data2 = ((B01 * z.get(i) + B11 * z.get(i - 1) + B21 * z.get(i - 2) - A11 * x.get(i - 1) - A21 * x.get(i - 2)));
                datagain2 = data2 * 0.000932538415629474;
                x.add(i, datagain2);
            }

            //high pass filter first section
            //constants

            double data3;
            double datagain3;

            //Build high pass filter first section
            y.add(0, B02 * x.get(0) * 0.999518942496229523);
            y.add(1, (B02 * x.get(1) + B12 * x.get(0) - A12 * y.get(0)) * 0.999518942496229523);
            for (i = 2; i < Measurements.sData.size(); i++) {
                data3 = (B02 * x.get(i) + B12 * x.get(i - 1) + B22 * x.get(i - 2) - A12 * y.get(i - 1) - A22 * y.get(i - 2));
                datagain3 = data3 * 0.999518942496229523;
                y.add(i, datagain3);
            }

            //high pass filter second section
            //constants

            double data4;
            double datagain4;

            //Build high pass filter second section
            w.add(0, B03 * y.get(0) * 0.998839971032117524);
            w.add(1, (B03 * y.get(1) + B13 * y.get(0) - A13 * w.get(0)) * 0.998839971032117524);
            for (i = 2; i < Measurements.sData.size(); i++) {
                data4 = (B03 * y.get(i) + B13 * y.get(i - 1) + B23 * y.get(i - 2) - A13 * w.get(i - 1) - A23 * w.get(i - 2));
                datagain4 = data4 * 0.998839971032117524;
                w.add(i, datagain4);
            }

            System.out.println("Export: " + w.get(Measurements.sData.size() - 1) + " Loop: " + Measurements.sData.get(Measurements.sData.size() - 1).getFilteredZValue());

            Measurements.sMeasSemaphore.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

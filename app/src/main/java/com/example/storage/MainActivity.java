package com.example.storage;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.text.Editable;
import android.text.TextWatcher;
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import info.mqtt.android.service.MqttAndroidClient;

public class MainActivity extends AppCompatActivity {
    private final Semaphore mLocSemaphore = new Semaphore(1, true);
    private final int PERMISSION_FINE_LOCATION = 99;

    private ActivityMainBinding mBinding;
    private Timer mViewTimer;
    private int mApproxRefresh;
    private volatile boolean switchToggled;
    private Location mCurrLoc; // Sensor and Location threads both need to use this

    private MqttAndroidClient mPublisher;

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

                DateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                isoDate.setTimeZone(TimeZone.getTimeZone("UTC"));

                String time = isoDate.format(new Date());

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
                        double b0 = 1;
                        double b1 = 2;
                        double a1 = -1.94921595802584;
                        double b01 = 1;
                        double b11 = 2;
                        double a11 = -1.88660958262151;
                        double b02 = 1;
                        double b12 = -2;
                        double a12 = -1.999037095803727126;
                        double b03 = 1;
                        double b13 = -2;
                        double a13 = -1.99767915341159740;

                        double zWeight = 0.000963484325512291;
                        double xWeight = 0.000932538415629474;
                        double yWeight = 0.999518942496229523;
                        double wWeight = 0.998839971032117524;

                        Measurements.sMeasSemaphore.acquire();
                        if (Measurements.consecutiveMeasurements == 0) {
                            Measurements.zVal[0] = m.getzValue();
                            Measurements.z[0] = (b0 * Measurements.zVal[0]) * zWeight;
                            Measurements.x[0] = (b01 * Measurements.z[0]) * xWeight;
                            Measurements.y[0] = (b02 * Measurements.x[0]) * yWeight;
                            Measurements.w[0] = (b03 * Measurements.y[0]) * wWeight;

                            m.setFilteredZValue(Measurements.w[0]);
                        } else if (Measurements.consecutiveMeasurements == 1) {
                            Measurements.zVal[1] = m.getzValue();

                            Measurements.z[1] = (b0 * Measurements.zVal[1] + b1 * Measurements.zVal[0]
                                    - a1 * Measurements.z[0]) * zWeight;

                            Measurements.x[1] = (b01 * Measurements.z[1] + b11 * Measurements.z[0]
                                    - a11 * Measurements.x[0]) * xWeight;

                            Measurements.y[1] = (b02 * Measurements.x[1] + b12 * Measurements.x[0]
                                    - a12 * Measurements.y[0]) * yWeight;

                            Measurements.w[1] = (b03 * Measurements.y[1] + b13 * Measurements.y[0]
                                    - a13 * Measurements.w[0]) * wWeight;

                            m.setFilteredZValue(Measurements.w[1]);
                        } else {
                            double b2 = 1;
                            double a2 = 0.953069895327891;
                            double b21 = 1;
                            double a21 = 0.890339736284024;
                            double b22 = 1;
                            double a22 = 0.9990386741811910775;
                            double b23 = 1;
                            double a23 = 0.997680730716872465;

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

                            double zData = (b0 * Measurements.zVal[2]
                                    + b1 * Measurements.zVal[1]
                                    + b2 * Measurements.zVal[0]
                                    - (a1) * Measurements.z[1] - (a2) * Measurements.z[0]);

                            Measurements.z[2] = zData * zWeight;

                            double xData = (b01 * Measurements.z[2] + b11 * Measurements.z[1]
                                    + b21 * Measurements.z[0] - a11 * Measurements.x[1] - a21 * Measurements.x[0]);

                            Measurements.x[2] = xData * xWeight;

                            double yData = (b02 * Measurements.x[2] + b12 * Measurements.x[1]
                                    + b22 * Measurements.x[0] - a12 * Measurements.y[1] - a22 * Measurements.y[0]);

                            Measurements.y[2] = yData * yWeight;

                            double wData = (b03 * Measurements.y[2] + b13 * Measurements.y[1]
                                    + b23 * Measurements.y[0] - a13 * Measurements.w[1] - a23 * Measurements.w[0]);

                            Measurements.w[2] = wData * wWeight;

                            m.setFilteredZValue(Measurements.w[2]);
                        }

                        Measurements.consecutiveMeasurements++;

                        if (Measurements.sData.size() >= 10000) {
                            ArrayList<Measurement> copy = new ArrayList<>();

                            for (Measurement ms:Measurements.sData) {
                                copy.add(ms.clone());
                            }

                            Dataframe d = new Dataframe(Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.ID, Build.VERSION.RELEASE, String.valueOf(mBinding.session.getText()), copy);
                            mMessageThread.handleMessage(d, mPublisher);

                            Measurements.sData.clear();
                        }

                        Measurements.sData.add(m);
                        Measurements.sMeasSemaphore.release();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) { /* Unused */ }
    };

    private FusedLocationProviderClient mFusedLocationProviderClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private Looper mLocationLooper;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private MessageThread mMessageThread;

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
            Handler sensorHandler = new Handler(Looper.myLooper());
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

        mMessageThread = new MessageThread();
        mMessageThread.start();

        switchToggled = false;
        mBinding.switchBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchToggled = true;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Toast.makeText(getBaseContext(), "ON", Toast.LENGTH_SHORT).show();
                startLocationUpdates();
            } else {
                switchToggled = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (mFusedLocationProviderClient != null)
                    mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mApproxRefresh = (int) (1000 / this.getDisplay().getRefreshRate()) + 1;
        } else {
            // Assume 60fps ish, rounded up to not kick off vsync errors
            mApproxRefresh = 17;
        }

        mViewTimer = new Timer();
        scheduleUITimer();

        String defaultTxt = String.valueOf(mBinding.server.getText());
        if (defaultTxt.length() >= 7) {
            mPublisher = Mqtt.generateClient(this, defaultTxt);
            Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));
        }

        mBinding.session.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Unused */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mBinding.switchBtn.setEnabled(s.length() > 0 && mBinding.server.getText().length() >= 7);
            }

            @Override
            public void afterTextChanged(Editable s) { /* Unused */ }
        });

        mBinding.server.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Unused */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 7) {
                    if (mPublisher != null)
                        Mqtt.disconnect(mPublisher);

                    String server = String.valueOf(mBinding.server.getText());
                    mPublisher = Mqtt.generateClient(getBaseContext(), server);
                    Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));

                    mBinding.switchBtn.setEnabled(mBinding.session.getText().length() > 0);
                }
            }

            @Override
            public void afterTextChanged(Editable s) { /* Unused */ }
        });
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

        try {
            Measurements.sMeasSemaphore.acquire();
            Measurements.consecutiveMeasurements = 0;
            Measurements.sMeasSemaphore.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
     * <p>
     * Approximate timing is impossible and assigning faster than refresh updates crashes eventually
     * due to vsync inconsistencies.
     */
    private void scheduleUITimer() {
        mViewTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Reflect connection changes even if paused
                runOnUiThread(() -> {
                    mBinding.connectionLabel.setText(mPublisher.isConnected() ? getString(R.string.connection_succeeded) : getString(R.string.connection_failed));
                });

                if (switchToggled) {
                    runOnUiThread(() -> {
                        try {
                            Measurements.sMeasSemaphore.acquire();

                            int idx = Measurements.sData.size() - 1;
                            if (idx >= 0) {
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
    private void updateUI(Measurement m) {
        mBinding.zValue.setText((String.valueOf(m.getzValue())));
        mBinding.tvLat.setText(String.valueOf(m.getLatitude()));
        mBinding.tvLon.setText(String.valueOf(m.getLongitude()));
        mBinding.tvAccuracy.setText(String.valueOf(m.getAccuracy()));
        mBinding.tvAltitude.setText(String.valueOf(m.getAltitude()));
        mBinding.tvSpeed.setText(String.valueOf(m.getSpeed()));
        mBinding.time.setText(m.getTime().replace('T', ' ').replace('Z', ' '));
        mBinding.zFiltered.setText(String.valueOf(Math.max(m.getFilteredZValue(), 0.0)));
    }
}

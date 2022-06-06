package com.example.storage;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

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
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.storage.data.Dataframe;
import com.example.storage.data.Measurement;
import com.example.storage.data.Measurements;
import com.example.storage.databinding.ActivityMainBinding;
import com.example.storage.network.MessageThread;
import com.example.storage.network.Mqtt;
import com.example.storage.utils.FileUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.apache.commons.validator.routines.InetAddressValidator;

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
    private final InetAddressValidator mInetAddressValidator = InetAddressValidator.getInstance();
    private final Semaphore mLocSemaphore = new Semaphore(1, true);
    private final int PERMISSION_FINE_LOCATION = 99;
    private ActivityMainBinding mBinding;
    private Timer mViewTimer;
    private int mApproxRefresh;
    private volatile boolean switchToggled;
    private Location mCurrLoc; // Sensor and Location threads both need to use this

    private MqttAndroidClient mPublisher;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private Looper mLocationLooper;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private MessageThread mMessageThread;
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
            if (switchToggled && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float zAcc = event.values[2];

                DateFormat isoDate = new SimpleDateFormat(FileUtils.ISO_DATE);
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
                        final double b0 = 1;
                        final double b1 = 2;
                        final double a1 = -1.94921595802584;
                        final double b01 = 1;
                        final double b11 = 2;
                        final double a11 = -1.88660958262151;
                        final double b02 = 1;
                        final double b12 = -2;
                        final double a12 = -1.999037095803727126;
                        final double b03 = 1;
                        final double b13 = -2;
                        final double a13 = -1.99767915341159740;

                        final double zWeight = 0.000963484325512291;
                        final double xWeight = 0.000932538415629474;
                        final double yWeight = 0.999518942496229523;
                        final double wWeight = 0.998839971032117524;

                        Measurements.sMeasSemaphore.acquire();
                        if (Measurements.consecutiveMeasurements == 0) {
                            Measurements.zVal[0] = m.getZ();
                            Measurements.z[0] = (b0 * Measurements.zVal[0]) * zWeight;
                            Measurements.x[0] = (b01 * Measurements.z[0]) * xWeight;
                            Measurements.y[0] = (b02 * Measurements.x[0]) * yWeight;
                            Measurements.w[0] = (b03 * Measurements.y[0]) * wWeight;

                            m.setFz(Measurements.w[0]);
                        } else if (Measurements.consecutiveMeasurements == 1) {
                            Measurements.zVal[1] = m.getZ();

                            Measurements.z[1] = (b0 * Measurements.zVal[1] + b1 * Measurements.zVal[0]
                                    - a1 * Measurements.z[0]) * zWeight;

                            Measurements.x[1] = (b01 * Measurements.z[1] + b11 * Measurements.z[0]
                                    - a11 * Measurements.x[0]) * xWeight;

                            Measurements.y[1] = (b02 * Measurements.x[1] + b12 * Measurements.x[0]
                                    - a12 * Measurements.y[0]) * yWeight;

                            Measurements.w[1] = (b03 * Measurements.y[1] + b13 * Measurements.y[0]
                                    - a13 * Measurements.w[0]) * wWeight;

                            m.setFz(Measurements.w[1]);
                        } else {
                            final double b2 = 1;
                            final double a2 = 0.953069895327891;
                            final double b21 = 1;
                            final double a21 = 0.890339736284024;
                            final double b22 = 1;
                            final double a22 = 0.9990386741811910775;
                            final double b23 = 1;
                            final double a23 = 0.997680730716872465;

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

                            Measurements.zVal[2] = m.getZ();

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

                            m.setFz(Measurements.w[2]);
                        }

                        Measurements.consecutiveMeasurements++;

                        if (Measurements.sData.size() >= 10000) {
                            flushMessages(Measurements.sData.size());
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

    private PowerManager.WakeLock mWakeLock;

    /**
     * Send the currently stored messages to the message thread handler
     * <p>
     * NOTE: You must obtain the Measurements semaphore when calling this method!
     */
    private void flushMessages(int size) {
        final ArrayList<Measurement> copy = new ArrayList<>(size);

        for (Measurement ms : Measurements.sData) {
            try {
                copy.add(ms.clone());
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }

        final Dataframe d = new Dataframe(Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.ID, Build.VERSION.RELEASE, String.valueOf(mBinding.session.getText()), copy);
        mMessageThread.handleMessage(d, mPublisher, getBaseContext());

        Measurements.sData.clear();
    }

    @SuppressLint("WakelockTimeout")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_FINE_LOCATION);
        }

        mWakeLock = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mWakeLock = ((PowerManager) this.getSystemService(POWER_SERVICE))
                    .newWakeLock(PARTIAL_WAKE_LOCK, "edr:processingWakeLock");
        }

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Thread looper to be used for accelerometer callbacks
        new Thread(() -> {
            Thread.currentThread().setPriority(8); // Relatively high priority

            Looper.prepare();
            final Handler sensorHandler = new Handler(Looper.myLooper());
            mSensorManager
                    .registerListener(mSensorEventListener, mAccelerometer, 2000, sensorHandler);
            Looper.loop();
        }).start();

        // Thread looper to be used for location callbacks
        final HandlerThread locationThread = new HandlerThread("loc");
        locationThread.start();
        mLocationLooper = locationThread.getLooper();
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationRequest = LocationRequest.create()
                .setInterval(1000)
                .setFastestInterval(2)
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
                if (mWakeLock != null)
                    mWakeLock.acquire();

                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                startLocationUpdates();

                if (mSensorManager != null)
                    mSensorManager
                            .registerListener(mSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

                switchToggled = true;
            } else {
                if (mWakeLock != null)
                    mWakeLock.release();

                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                if (mFusedLocationProviderClient != null)
                    mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);

                if (mSensorManager != null)
                    mSensorManager.unregisterListener(mSensorEventListener);

                try {
                    Measurements.sMeasSemaphore.acquire();

                    Measurements.consecutiveMeasurements = 0;
                    if (Measurements.sData.size() > 0) {
                        flushMessages(Measurements.sData.size());
                    }

                    Measurements.sMeasSemaphore.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                switchToggled = false;
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mApproxRefresh = (int) (1000 / this.getDisplay().getRefreshRate()) + 1;
        } else {
            // Assume 60fps ish, rounded up
            mApproxRefresh = 17;
        }

        mViewTimer = new Timer();
        scheduleUITimer();

        String defaultTxt = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
        if (defaultTxt.length() >= 7 && mInetAddressValidator.isValid(defaultTxt)) {
            mPublisher = Mqtt.generateClient(this, defaultTxt);
            Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));
        }

        scheduleBacklogs();

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
                    String server = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
                    if (mInetAddressValidator.isValid(defaultTxt)) {
                        mPublisher = Mqtt.generateClient(getBaseContext(), server);
                        // Mqtt will kick off old connection by itself if this is a duplicate
                        Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));

                        mBinding.switchBtn.setEnabled(mBinding.session.getText().length() > 0);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) { /* Unused */ }
        });
    }

    @Override
    protected void onStop() {
        /*
         if (mFusedLocationProviderClient != null)
             mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
         if (mSensorManager != null)
             mSensorManager.unregisterListener(mSensorEventListener);
         if (mMessageThread != null && mMessageThread.looper != null) {
             mMessageThread.looper.quitSafely();
             mMessageThread = null;
         }
         if (mPublisher != null) {
             Mqtt.disconnect(mPublisher);
             mPublisher = null;
         }
         try {
             Measurements.sMeasSemaphore.acquire();
             Measurements.consecutiveMeasurements = 0;
             if (Measurements.sData.size() > 0) {
                 ArrayList<Measurement> copy = new ArrayList<>();
                 for (Measurement ms : Measurements.sData) {
                     copy.add(ms.clone());
                 }
                 Dataframe d = new Dataframe(Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.ID, Build.VERSION.RELEASE, String.valueOf(mBinding.session.getText()), copy);
                 String json = JsonConverter.convert(d);
                 byte[] msg = ZipUtils.compress(json);
                 FileUtils.write(copy.get(copy.size() - 1).getTime(), msg, this);
                 Measurements.sData.clear();
             }
             Measurements.sMeasSemaphore.release();
         } catch (Exception e) {
             e.printStackTrace();
         }
        */

        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (mPublisher == null) {
            String defaultTxt = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
            if (defaultTxt.length() >= 7 && mInetAddressValidator.isValid(defaultTxt)) {
                mPublisher = Mqtt.generateClient(this, defaultTxt);
                Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));
            }
        }

        if (mMessageThread == null) {
            mMessageThread = new MessageThread();
            mMessageThread.start();
        }

        if (switchToggled) {
            if (mSensorManager != null)
                mSensorManager
                        .registerListener(mSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

            startLocationUpdates();
        }


        mViewTimer = new Timer();
        scheduleUITimer();
    }

    @Override
    protected void onPause() {
        if (mViewTimer != null)
            mViewTimer.cancel();

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mViewTimer = new Timer();
        scheduleUITimer();
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

    /**
     * Checks for location permission
     * <p>
     * If permissions are granted then it ensures there is a location provider client
     * <p>
     * Once a client exists requests location updates using:
     * mLocationRequest,
     * mLocationCallback,
     * mLocationLooper
     */
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
     * Creates a looper handler to add backlogged message data to the MessageThread
     * <p>
     * Currently backlog files are enqueued once a minute if measurements are ongoing
     * or once every 20 seconds if measurements are paused
     */
    @SuppressLint("SimpleDateFormat")
    private void scheduleBacklogs() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mPublisher != null && mPublisher.isConnected()) {
                    ArrayList<String> files = FileUtils.list(getBaseContext());

                    if (mMessageThread != null && files.size() > 0) {
                        mMessageThread.handleFile(files.get(0), mPublisher, getBaseContext());
                    }
                }

                // Queue more messages if MessageThread isn't being used
                handler.postDelayed(this, switchToggled ? 60000 : 20000);
            }
        });
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
                // Reflect connection changes even if not measuring
                runOnUiThread(() -> mBinding.connectionLabel.
                        setText(mPublisher != null && mPublisher.isConnected() ?
                                getString(R.string.connection_succeeded) :
                                getString(R.string.connection_failed)));

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
     * Updates on screen information with recent values
     */
    private void updateUI(Measurement m) {
        mBinding.zValue.setText((String.valueOf(m.getZ())));
        mBinding.tvLat.setText(String.valueOf(m.getLat()));
        mBinding.tvLon.setText(String.valueOf(m.getLon()));
        mBinding.tvAccuracy.setText(String.valueOf(m.getAcc()));
        mBinding.tvAltitude.setText(String.valueOf(m.getAlt()));
        mBinding.tvSpeed.setText(String.valueOf(m.getMs()));
        mBinding.time.setText(m.getTime().replace('T', ' ').replace('Z', ' '));
        mBinding.zFiltered.setText(String.valueOf(Math.max(m.getFz(), 0.0)));
    }
}

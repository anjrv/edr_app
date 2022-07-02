package com.example.storage;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.storage.data.Dataframe;
import com.example.storage.data.Measurement;
import com.example.storage.data.Measurements;
import com.example.storage.data.SlidingCalculator;
import com.example.storage.utils.FileUtils;
import com.example.storage.utils.JsonConverter;
import com.example.storage.utils.ZipUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class SensorService extends Service implements SensorEventListener {
    private static final Dataframe d = new Dataframe();
    private PowerManager.WakeLock mWakeLock;
    private SlidingCalculator mCalculator;
    private SensorManager mSensorManager;
    private Thread mCallbackThread;
    private Handler mCallbackHandler;
    private Looper mCallbackLooper;
    private HandlerThread mLocationThread;
    private Looper mLocationLooper;
    private HandlerThread mFileThread;
    private Looper mFileLooper;
    private Handler mFileHandler;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private MathThread mMathThread;

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        mFileThread = new HandlerThread("files");
        mFileThread.start();
        mFileLooper = mFileThread.getLooper();
        mFileHandler = new Handler(mFileLooper);

        mMathThread = new MathThread();
        mMathThread.start();

        mCallbackThread = new Thread(() -> {
            Thread.currentThread().setPriority(8); // Relatively high priority

            Looper.prepare();

            mCallbackLooper = Looper.myLooper();
            mCallbackHandler = new Handler(mCallbackLooper);

            Looper.loop();
        });

        mCallbackThread.start();

        mLocationThread = new HandlerThread("location");
        mLocationThread.start();

        mLocationLooper = mLocationThread.getLooper();
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        mLocationRequest = LocationRequest.create()
                .setInterval(10000)
                .setFastestInterval(1000)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Measurements.sAccuracy = locationResult.getLastLocation().getAccuracy();
                Measurements.sLongitude = locationResult.getLastLocation().getLongitude();
                Measurements.sLatitude = locationResult.getLastLocation().getLatitude();
                Measurements.sAltitude = locationResult.getLastLocation().getAltitude();
                Measurements.sSensorSpeed = locationResult.getLastLocation().getSpeed();
                if (Measurements.sSensorSpeed > 0.0)
                    Measurements.sSpeed = Measurements.sSensorSpeed;
            }
        };

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::SensorWakeLock");
    }

    @Override
    @SuppressLint({"MissingPermission", "WakelockTimeout"})
    // This permission should be obtained on activity creation
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent,
                            PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "SENSOR_CHANNEL")
                    .setSmallIcon(R.drawable.ic_launcher_new_foreground)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(false)
                    .setContentTitle("Eddy")
                    .setContentText("Measurements ongoing...");

            Notification notification = builder.build();

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("SENSOR_CHANNEL", "SENSOR_CHANNEL", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);

            notificationManager.notify(1, notification);

            startForeground(1, notification);
        }

        // Preallocate all memory
        for (int i = 0; i < Measurements.MEASUREMENT_COUNT; i++) {
            Measurements.DATA_1.add(new Measurement());
            Measurements.DATA_2.add(new Measurement());
        }

        mCalculator = new SlidingCalculator();
        Measurements.sCurrIdx = 0;
        Measurements.sFirstArray = true;

        d.setBrand(Build.BRAND);
        d.setManufacturer(Build.MANUFACTURER);
        d.setModel(Build.MODEL);
        d.setId(Build.ID);
        d.setVersion(Build.VERSION.RELEASE);
        d.setSession((String) intent.getExtras().get("SESSION"));
        d.setStart((String) intent.getExtras().get("START"));

        if (mFusedLocationProviderClient == null)
            mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        else
            mFusedLocationProviderClient
                    .requestLocationUpdates(mLocationRequest, mLocationCallback, mLocationLooper);

        mSensorManager
                .registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 2000, mCallbackHandler);

        mWakeLock.acquire();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mSensorManager.unregisterListener(this);

        if (mFusedLocationProviderClient != null)
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);

        mLocationLooper.quitSafely();
        mCallbackLooper.quitSafely();
        mMathThread.looper.quitSafely();
        mFileLooper.quitSafely();

        mLocationThread.interrupt();
        mFileThread.interrupt();
        mMathThread.interrupt();
        mCallbackThread.interrupt();

        mWakeLock.release();

        super.onDestroy();
    }

    /**
     * Send the currently stored messages to the message thread handler
     */
    private void flushMessages(boolean first, int idx) {
        // All other dataframe values should be set in onStartCommand, they never change
        d.setData(first ? Measurements.DATA_1.subList(0, idx) :
                Measurements.DATA_2.subList(0, idx));

        mFileHandler.post(() -> {
            try {
                final byte[] msg = ZipUtils.compress(JsonConverter.convert(d));
                FileUtils.write(d.getData().get(d.getData().size() - 1).getTime(), msg, getApplicationContext());
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    @SuppressLint("SimpleDateFormat")
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            DateFormat isoDate = new SimpleDateFormat(FileUtils.ISO_DATE);
            isoDate.setTimeZone(TimeZone.getTimeZone("UTC"));

            String time = isoDate.format(new Date());

            mMathThread.handleMeasurement(event.values[2], time);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Internal thread to offload z filtering from the listener
     */
    class MathThread extends Thread {
        public Looper looper;
        public Handler handler;

        @Override
        public void run() {
            Looper.prepare();

            looper = Looper.myLooper();
            handler = new Handler(looper);

            Looper.loop();
        }

        /**
         * Calculates filtered Z from current acceleration and previous measurements
         *
         * @param zAcc the latest z acceleration from the accelerometer
         * @param time the timestamp for that measurement
         */
        public void handleMeasurement(float zAcc, String time) {
            handler.post(() -> {
                if (Measurements.sLongitude == Double.NEGATIVE_INFINITY || Measurements.DATA_1.size() == 0 || Measurements.DATA_2.size() == 0) {
                    return;
                }

                double fz;

                double b0 = 1;
                double b1 = 2;
                double b2 = 1;
                double a1 = -1.94921595802584;
                double a2 = 0.953069895327891;
                double gain1 = 0.000963484325512291;

                double b01 = 1;
                double b11 = 2;
                double b21 = 1;
                double a11 = -1.88660958262151;
                double a21 = 0.890339736284024;
                double gain2 = 0.000932538415629474;

                double b02 = 1;
                double b12 = -2;
                double b22 = 1;
                double a12 = -1.999037095803727126;
                double a22 = 0.9990386741811910775;
                double gain3 = 0.999518942496229523;

                double b03 = 1;
                double b13 = -2;
                double b23 = 1;
                double a13 = -1.99767915341159740;
                double a23 = 0.997680730716872465;
                double gain4 = 0.998839971032117524;

                if (mCalculator.getCount() == 0) {
                    Measurements.Z_VAL[0] = zAcc;

                    Measurements.Z[0] = b0 * Measurements.Z_VAL[0];
                    Measurements.Z_GAIN[0] = Measurements.Z[0] * gain1;

                    Measurements.X[0] = b01 * Measurements.Z_GAIN[0];
                    Measurements.X_GAIN[0] = Measurements.X[0] * gain2;

                    Measurements.Y[0] = b02 * Measurements.X_GAIN[0];
                    Measurements.Y_GAIN[0] = Measurements.Y[0] * gain3;

                    Measurements.W[0] = b03 * Measurements.Y_GAIN[0];
                    Measurements.W_GAIN[0] = Measurements.W[0] * gain4;

                    fz = Measurements.W_GAIN[0];
                } else if (mCalculator.getCount() == 1) {
                    Measurements.Z_VAL[1] = zAcc;

                    Measurements.Z[1] = (b0 * Measurements.Z_VAL[1] + b1 * Measurements.Z_VAL[0]
                            - a1 * Measurements.Z[0]);

                    Measurements.Z_GAIN[1] = Measurements.Z[1] * gain1;

                    Measurements.X[1] = (b01 * Measurements.Z_GAIN[1] + b11 * Measurements.Z_GAIN[0]
                            - a11 * Measurements.X_GAIN[0]);

                    Measurements.X_GAIN[1] = Measurements.X[1] * gain2;

                    Measurements.Y[1] = (b02 * Measurements.X_GAIN[1] + b12 * Measurements.X_GAIN[0]
                            - a12 * Measurements.Y_GAIN[0]);

                    Measurements.Y_GAIN[1] = Measurements.Y[1] * gain3;

                    Measurements.W[1] = (b03 * Measurements.Y_GAIN[1] + b13 * Measurements.Y_GAIN[0]
                            - a13 * Measurements.W_GAIN[0]);

                    Measurements.W_GAIN[1] = Measurements.W[1] * gain4;

                    fz = Measurements.W_GAIN[1];
                } else {


                    if (mCalculator.getCount() > 2) {
                        Measurements.Z_VAL[0] = Measurements.Z_VAL[1];
                        Measurements.Z_VAL[1] = Measurements.Z_VAL[2];

                        Measurements.Z[0] = Measurements.Z[1];
                        Measurements.Z[1] = Measurements.Z[2];

                        Measurements.Z_GAIN[0] = Measurements.Z_GAIN[1];
                        Measurements.Z_GAIN[1] = Measurements.Z_GAIN[2];

                        Measurements.X[0] = Measurements.X[1];
                        Measurements.X[1] = Measurements.X[2];

                        Measurements.X_GAIN[0] = Measurements.X_GAIN[1];
                        Measurements.X_GAIN[1] = Measurements.X_GAIN[2];

                        Measurements.Y[0] = Measurements.Y[1];
                        Measurements.Y[1] = Measurements.Y[2];

                        Measurements.Y_GAIN[0] = Measurements.Y_GAIN[1];
                        Measurements.Y_GAIN[1] = Measurements.Y_GAIN[2];

                        Measurements.W[0] = Measurements.W[1];
                        Measurements.W[1] = Measurements.W[2];

                        Measurements.W_GAIN[0] = Measurements.W_GAIN[1];
                        Measurements.W_GAIN[1] = Measurements.W_GAIN[2];
                    }

                    Measurements.Z_VAL[2] = zAcc;

                    Measurements.Z[2] = (b0 * Measurements.Z_VAL[2]
                            + b1 * Measurements.Z_VAL[1]
                            + b2 * Measurements.Z_VAL[0]
                            - (a1) * Measurements.Z[1] - (a2) * Measurements.Z[0]);

                    Measurements.Z_GAIN[2] = Measurements.Z[2] * gain1;

                    Measurements.X[2] = (b01 * Measurements.Z_GAIN[2] + b11 * Measurements.Z_GAIN[1]
                            + b21 * Measurements.Z_GAIN[0] - a11 * Measurements.X[1] - a21 * Measurements.X[0]);

                    Measurements.X_GAIN[2] = Measurements.X[2] * gain2;

                    Measurements.Y[2] = (b02 * Measurements.X_GAIN[2] + b12 * Measurements.X_GAIN[1]
                            + b22 * Measurements.X_GAIN[0] - a12 * Measurements.Y[1] - a22 * Measurements.Y[0]);

                    Measurements.Y_GAIN[2] = Measurements.Y[2] * gain3;

                    Measurements.W[2] = (b03 * Measurements.Y_GAIN[2] + b13 * Measurements.Y_GAIN[1]
                            + b23 * Measurements.Y_GAIN[0] - a13 * Measurements.W[1] - a23 * Measurements.W[0]);

                    Measurements.W_GAIN[2] = Measurements.W[2] * gain4;

                    fz = Measurements.W_GAIN[2];
                }

                float s = Measurements.sSpeed;
                double edr = 0.0;
                mCalculator.update(fz);
                double std = mCalculator.getStd();

                if (s > 0.0f) { // Motion required to calculate edr using speed divisor
                    double speed = Math.pow(s, 2.0 / 3);
                    double I = 5.4;
                    double denominator = 0.7 * speed * I;
                    edr = std / (Math.pow(denominator, 0.5));
                }

                if (Measurements.sFirstArray) {
                    if (Measurements.sCurrIdx >= Measurements.MEASUREMENT_COUNT) {
                        flushMessages(true, Measurements.sCurrIdx);
                        Measurements.sFirstArray = false;
                        Measurements.sCurrIdx = 0;
                    }
                } else {
                    if (Measurements.sCurrIdx >= Measurements.MEASUREMENT_COUNT) {
                        flushMessages(false, Measurements.sCurrIdx);
                        Measurements.sFirstArray = true;
                        Measurements.sCurrIdx = 0;
                    }
                }

                // Pull a pointer instead of creating a new allocation
                Measurement m = Measurements.sFirstArray ?
                        Measurements.DATA_1.get(Measurements.sCurrIdx) :
                        Measurements.DATA_2.get(Measurements.sCurrIdx);

                if (m != null) { // Immediate exit may clear the array
                    m.setZ(zAcc);
                    m.setFz(fz);
                    m.setStd(std);
                    m.setEdr(edr);
                    m.setTime(time);
                    m.setLon(Measurements.sLongitude);
                    m.setLat(Measurements.sLatitude);
                    m.setAlt(Measurements.sAltitude);
                    m.setMs(s);
                    m.setMs0(Measurements.sSensorSpeed);
                    m.setAcc(Measurements.sAccuracy);

                    int idx = Measurements.sCurrIdx;
                    Measurements.sCurrIdx = idx + 1;
                }
            });
        }
    }
}

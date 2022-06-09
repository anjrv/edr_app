package com.example.storage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.storage.data.Dataframe;
import com.example.storage.data.Measurement;
import com.example.storage.data.Measurements;
import com.example.storage.network.MessageThread;
import com.example.storage.network.Mqtt;
import com.example.storage.utils.FileUtils;
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

import info.mqtt.android.service.MqttAndroidClient;

public class SensorService extends Service implements SensorEventListener {
    private static final String clientId = Build.BRAND + "_" + Build.ID + "_SENSORS";
    private MqttAndroidClient mPublisher;
    private SensorManager mSensorManager;
    private MessageThread mMessageThread;
    private Thread mCallbackThread;
    private Handler mCallbackHandler;
    private Looper mCallbackLooper;
    private HandlerThread mLocationThread;
    private Looper mLocationLooper;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private MathThread mMathThread;
    private String mSession;

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        mMessageThread = new MessageThread();
        mMessageThread.start();

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
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationRequest = LocationRequest.create()
                .setInterval(1000)
                .setFastestInterval(1000)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);

                try {
                    Measurements.sLocSemaphore.acquire();
                    Measurements.sCurrLoc = locationResult.getLastLocation();
                    Measurements.sLocSemaphore.release();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) getBaseContext(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, 0, notificationIntent,
                            PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getBaseContext(), "SENSOR_CHANNEL")
                    .setSmallIcon(R.drawable.ic_launcher_new_foreground)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setContentTitle("Eddy")
                    .setContentText("Measurements ongoing...");

            Notification notification = builder.build();

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("SENSOR_CHANNEL", "SENSOR_CHANNEL", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);

            notificationManager.notify(1, notification);

            startForeground(1, notification);
        }

        Context ctx = this;

        mSession = (String) intent.getExtras().get("SESSION");

        mPublisher = Mqtt.generateClient(this, clientId, (String) intent.getExtras().get("SERVER"));
        Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));

        final Handler handler = new Handler(mLocationLooper); // Reuse location looper, it's not very busy
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mPublisher != null && mPublisher.isConnected()) {
                    ArrayList<String> files = FileUtils.list(getBaseContext());

                    if (mMessageThread != null && files.size() > 0) {
                        mMessageThread.handleFile(files.get(0), mPublisher, ctx);
                    }
                }

                // Queue more messages if MessageThread isn't being used
                handler.postDelayed(this, 60000);
            }
        });

        if (mFusedLocationProviderClient == null)
            mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx);
        else
            mFusedLocationProviderClient
                    .requestLocationUpdates(mLocationRequest, mLocationCallback, mLocationLooper);

        mSensorManager
                .registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST, mCallbackHandler);

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
        mMessageThread.looper.quitSafely();

        mLocationThread.interrupt();
        mMessageThread.interrupt();
        mMathThread.interrupt();
        mCallbackThread.interrupt();

        mPublisher.unregisterResources();
        mPublisher.close();
        mPublisher.disconnect();

        super.onDestroy();
    }

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

        final Dataframe d = new Dataframe(Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.ID, Build.VERSION.RELEASE, mSession, copy);

        mMessageThread.handleMessage(d, mPublisher, this);

        Measurements.sData.clear();
    }

    @Override
    @SuppressLint("SimpleDateFormat")
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            Measurements.sensorHasConnection = mPublisher.isConnected();
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
                try {
                    Measurement m = null;
                    Measurements.sLocSemaphore.acquire();

                    if (Measurements.sCurrLoc != null) {
                        m = new Measurement(
                                zAcc, 0.0, time, Measurements.sCurrLoc.getLongitude(), Measurements.sCurrLoc.getLatitude(),
                                Measurements.sCurrLoc.getAltitude(), Measurements.sCurrLoc.getSpeed(), Measurements.sCurrLoc.getAccuracy()
                        );
                    }

                    Measurements.sLocSemaphore.release();

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
            });
        }
    }
}

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
import com.example.storage.utils.NetworkUtils;
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
    private static final Dataframe d = new Dataframe();
    private MqttAndroidClient mPublisher;
    private SensorManager mSensorManager;
    private MessageThread mMessageThread;
    private Context ctx;
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
                Measurements.sLongitude = locationResult.getLastLocation().getLongitude();
                Measurements.sLatitude = locationResult.getLastLocation().getLatitude();
                Measurements.sAltitude = locationResult.getLastLocation().getAltitude();
                Measurements.sSpeed = locationResult.getLastLocation().getSpeed();
                Measurements.sAccuracy = locationResult.getLastLocation().getAccuracy();
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) getBaseContext(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
        }

        ctx = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, 0, notificationIntent,
                            PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "SENSOR_CHANNEL")
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

        // Preallocate all memory
        for (int i = 0; i < 10000; i++) {
            Measurements.sData1.add(new Measurement());
            Measurements.sData2.add(new Measurement());
        }

        Measurements.consecutiveMeasurements = 0;
        Measurements.currIdx = 0;
        Measurements.firstArray = true;

        mSession = (String) intent.getExtras().get("SESSION");

        mPublisher = Mqtt.generateClient(this, clientId, (String) intent.getExtras().get("SERVER"));
        Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));

        final Handler handler = new Handler(mLocationLooper); // Reuse location looper, it's not very busy
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mPublisher != null && mPublisher.isConnected()) {
                    ArrayList<String> files = FileUtils.list(ctx);

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
                .registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 2000, mCallbackHandler);

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

        Measurements.sensorHasConnection = false;

        super.onDestroy();
    }

    /**
     * Send the currently stored messages to the message thread handler
     */
    private void flushMessages(boolean first, int idx) {
        d.setBrand(Build.BRAND);
        d.setManufacturer(Build.MANUFACTURER);
        d.setModel(Build.MODEL);
        d.setId(Build.ID);
        d.setVersion(Build.VERSION.RELEASE);
        d.setSession(mSession);
        d.setData(first ? Measurements.sData1.subList(0, idx) :
                Measurements.sData2.subList(0, idx));

        mMessageThread.handleMessage(d, mPublisher, this);

        // Give the connection a kick
        if (mPublisher != null && !mPublisher.isConnected() && NetworkUtils.isNetworkConnected(ctx))
            Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));
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
                if (Measurements.sLongitude == Double.NEGATIVE_INFINITY || Measurements.sData1.size() == 0 || Measurements.sData2.size() == 0) {
                    return;
                }

                double fz = 0.0;

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

                if (Measurements.consecutiveMeasurements == 0) {
                    Measurements.zVal[0] = zAcc;
                    Measurements.z[0] = (b0 * Measurements.zVal[0]) * zWeight;
                    Measurements.x[0] = (b01 * Measurements.z[0]) * xWeight;
                    Measurements.y[0] = (b02 * Measurements.x[0]) * yWeight;
                    Measurements.w[0] = (b03 * Measurements.y[0]) * wWeight;

                    fz = Measurements.w[0];
                } else if (Measurements.consecutiveMeasurements == 1) {
                    Measurements.zVal[1] = zAcc;

                    Measurements.z[1] = (b0 * Measurements.zVal[1] + b1 * Measurements.zVal[0]
                            - a1 * Measurements.z[0]) * zWeight;

                    Measurements.x[1] = (b01 * Measurements.z[1] + b11 * Measurements.z[0]
                            - a11 * Measurements.x[0]) * xWeight;

                    Measurements.y[1] = (b02 * Measurements.x[1] + b12 * Measurements.x[0]
                            - a12 * Measurements.y[0]) * yWeight;

                    Measurements.w[1] = (b03 * Measurements.y[1] + b13 * Measurements.y[0]
                            - a13 * Measurements.w[0]) * wWeight;

                    fz = Measurements.w[1];
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

                    Measurements.zVal[2] = zAcc;

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

                    fz = Measurements.w[2];
                }

                int tmp = Measurements.consecutiveMeasurements;
                Measurements.consecutiveMeasurements = tmp + 1;

                if (Measurements.firstArray) {
                    if (Measurements.currIdx >= 10000) {
                        flushMessages(true, Measurements.currIdx);
                        Measurements.firstArray = false;
                        Measurements.currIdx = 0;
                    }
                } else {
                    if (Measurements.currIdx >= 10000) {
                        flushMessages(false, Measurements.currIdx);
                        Measurements.firstArray = true;
                        Measurements.currIdx = 0;
                    }
                }

                // Pull a pointer instead of creating a new allocation
                Measurement m = Measurements.firstArray ?
                        Measurements.sData1.get(Measurements.currIdx) :
                        Measurements.sData2.get(Measurements.currIdx);

                m.setZ(zAcc);
                m.setTime(time);
                m.setLon(Measurements.sLongitude);
                m.setLat(Measurements.sLatitude);
                m.setAlt(Measurements.sAltitude);
                m.setMs(Measurements.sSpeed);
                m.setAcc(Measurements.sAccuracy);
                m.setFz(fz);

                int idx = Measurements.currIdx;
                Measurements.currIdx = idx + 1;
            });
        }
    }
}

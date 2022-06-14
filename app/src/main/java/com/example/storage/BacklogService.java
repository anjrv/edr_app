package com.example.storage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.storage.data.Measurements;
import com.example.storage.network.MessageThread;
import com.example.storage.network.Mqtt;
import com.example.storage.utils.FileUtils;
import com.example.storage.utils.NetworkUtils;

import java.util.ArrayList;

import info.mqtt.android.service.MqttAndroidClient;

public class BacklogService extends Service {
    private static final String clientId = Build.BRAND + "_" + Build.ID + "_BACKLOG";
    private MqttAndroidClient mPublisher;
    private MessageThread mMessageThread;
    private HandlerThread mBacklogThread;
    private Context ctx;
    private Looper mBacklogLooper;
    private Handler mBacklogHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        mMessageThread = new MessageThread();
        mMessageThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ctx = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, 0, notificationIntent,
                            PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "BACKLOG_CHANNEL")
                    .setSmallIcon(R.drawable.ic_launcher_new_foreground)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setContentTitle("Eddy")
                    .setContentText("Trying to send backlog...");

            Notification notification = builder.build();

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("BACKLOG_CHANNEL", "BACKLOG_CHANNEL", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);

            notificationManager.notify(2, notification);

            startForeground(2, notification);
        }

        mPublisher = Mqtt.generateClient(ctx, clientId, (String) intent.getExtras().get("SERVER"));
        Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));

        scheduleBacklogs();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mPublisher.unregisterResources();
        mPublisher.close();
        mPublisher.disconnect();

        Measurements.backlogHasConnection = false;

        mMessageThread.looper.quitSafely();
        mBacklogLooper.quitSafely();

        mMessageThread.interrupt();
        mBacklogThread.interrupt();

        super.onDestroy();
    }

    /**
     * "Recursive" handler to handle measurements that have been put in internal storage
     */
    private void scheduleBacklogs() {
        mBacklogThread = new HandlerThread("backlog");
        mBacklogThread.start();

        mBacklogLooper = mBacklogThread.getLooper();

        mBacklogHandler = new Handler(mBacklogLooper);
        mBacklogHandler.post(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> files = FileUtils.list(ctx);

                if (files.size() == 0) {
                    mBacklogHandler.postDelayed(() -> stopSelf(), Mqtt.TIMEOUT * 2);
                } else {
                    if (mPublisher != null && mPublisher.isConnected()) {
                        Measurements.backlogHasConnection = mPublisher.isConnected();

                        if (mMessageThread != null) {
                            mMessageThread.handleFile(files.get(0), mPublisher, ctx);
                        }
                    } else if (mPublisher != null && !mPublisher.isConnected() && NetworkUtils.isNetworkConnected(ctx))
                        Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));

                    mBacklogHandler.postDelayed(this, mPublisher != null && mPublisher.isConnected() ? (Mqtt.TIMEOUT * 2) : 60000);
                }


            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

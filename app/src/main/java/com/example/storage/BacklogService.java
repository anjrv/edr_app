package com.example.storage;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

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

        mPublisher = Mqtt.generateClient(ctx, clientId, (String) intent.getExtras().get("SERVER"));
        Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));

        scheduleBacklogs();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mMessageThread.looper.quitSafely();
        mBacklogLooper.quitSafely();

        mPublisher.unregisterResources();
        mPublisher.close();
        mPublisher.disconnect();

        Measurements.backlogHasConnection = false;

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

                if (mPublisher != null && mPublisher.isConnected()) {
                    Measurements.backlogHasConnection = mPublisher.isConnected();

                    if (mMessageThread != null && files.size() > 0) {
                        mMessageThread.handleFile(files.get(0), mPublisher, ctx);
                    }
                } else if (mPublisher != null && !mPublisher.isConnected() && NetworkUtils.isNetworkConnected(ctx))
                    Mqtt.connect(mPublisher, getString(R.string.mqtt_username), getString(R.string.mqtt_password));

                if (files.size() > 1)
                    mBacklogHandler.postDelayed(this, mPublisher != null && mPublisher.isConnected() ? (Mqtt.TIMEOUT + 1000) : 60000);
                else // Disconnect from within handler thread not ideal, ensure adequate time for previous message to complete
                    mBacklogHandler.postDelayed(() -> stopSelf(), Mqtt.TIMEOUT * 2);
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

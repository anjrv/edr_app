package com.example.storage;

import android.annotation.SuppressLint;
import android.app.Service;
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

import java.util.ArrayList;

import info.mqtt.android.service.MqttAndroidClient;

public class BacklogService extends Service {
    private static final String clientId = Build.BRAND + "_" + Build.ID + "_BACKLOG";
    private MqttAndroidClient mPublisher;
    private MessageThread mMessageThread;
    private HandlerThread mBacklogThread;
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
        mPublisher = Mqtt.generateClient(this, clientId, (String) intent.getExtras().get("SERVER"));
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
                if (mPublisher != null && mPublisher.isConnected()) {
                    Measurements.backlogHasConnection = mPublisher.isConnected();

                    ArrayList<String> files = FileUtils.list(getBaseContext());

                    if (mMessageThread != null && files.size() > 0) {
                        mMessageThread.handleFile(files.get(0), mPublisher, getBaseContext());
                    }
                }

                // Queue more often if MessageThread isn't being used
                mBacklogHandler.postDelayed(this, 20000);
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

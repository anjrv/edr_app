package com.example.storage;

import android.os.Handler;
import android.os.Looper;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;

import info.mqtt.android.service.MqttAndroidClient;

public class MessageThread extends Thread {
    public Looper looper;
    public Handler handler;

    @Override
    public void run() {
        Looper.prepare();

        looper = Looper.myLooper();
        handler = new Handler(looper);

        Looper.loop();
    }

    public void handleMessage(Dataframe d, MqttAndroidClient mqtt) {
        handler.post(() -> {
            boolean hasConnection = mqtt.isConnected();
            try {
                String msg = JsonConverter.convert(d);

                if (hasConnection) {
                    IMqttDeliveryToken token = Mqtt.publish(mqtt, "EDR", ZipUtils.compress(msg));
                    token.waitForCompletion();
                    // Loop through existing stored files
                } else {
                    // Write a file
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}

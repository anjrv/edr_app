package com.example.storage.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.storage.data.Dataframe;
import com.example.storage.utils.FileUtils;
import com.example.storage.utils.JsonConverter;
import com.example.storage.utils.ZipUtils;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;

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

    public void handleFile(String name, MqttAndroidClient mqtt, Context c) {
        handler.post(() -> {
            if (!mqtt.isConnected()) return; // Misfired request

            try {
                byte[] msg = FileUtils.retrieve(name, c);
                IMqttDeliveryToken token = Mqtt.publish(mqtt, "EDR", msg);

                token.waitForCompletion();
                if (token.getException() == null)
                    FileUtils.delete(name, c);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void handleMessage(Dataframe d, MqttAndroidClient mqtt, Context c) {
        handler.post(() -> {
            boolean hasConnection = mqtt.isConnected();

            try {
                String json = JsonConverter.convert(d);
                byte[] msg = ZipUtils.compress(json);

                if (hasConnection) {
                    IMqttDeliveryToken token = Mqtt.publish(mqtt, "EDR", msg);
                    token.waitForCompletion();

                    if (token.getException() != null)
                        FileUtils.write(d.getData().get(d.getData().size() - 1).getTime(), msg, c);
                } else {
                    FileUtils.write(d.getData().get(d.getData().size() - 1).getTime(), msg, c);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}

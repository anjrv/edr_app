package com.example.storage.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.storage.data.Dataframe;
import com.example.storage.utils.FileUtils;
import com.example.storage.utils.JsonConverter;
import com.example.storage.utils.ZipUtils;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;

import info.mqtt.android.service.MqttAndroidClient;

/**
 * Thread instance containing additional methods to handle measurement data
 */
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

    private void writeMsg(String name, byte[] msg, Context c) {
        FileUtils.write(name, msg, c);
        Toast.makeText(c, "Measurements written to backlog", Toast.LENGTH_SHORT).show();
    }

    /**
     * Posts a new runnable to the queue.
     * Used to queue stored measurement files
     * <p>
     * If sending through MQTT is successful then the file
     * will be deleted from internal storage
     *
     * @param name The name of the file to send through MQTT
     * @param mqtt The current MQTT client object
     * @param c    The context of the file to be sent
     */
    public void handleFile(String name, MqttAndroidClient mqtt, Context c) {
        handler.post(() -> {
            if (!mqtt.isConnected()) return; // Misfired request, do nothing

            try {
                IMqttDeliveryToken token = Mqtt.publish(mqtt, "EDR", FileUtils.retrieve(name, c));
                token.waitForCompletion(Mqtt.TIMEOUT);

                if (token.getException() == null) {
                    FileUtils.delete(name, c);
                    Toast.makeText(c, "Backlog file sent over MQTT", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Posts a new runnable to the queue.
     * Used to queue new measurement dataframes
     * <p>
     * If sending through MQTT is not successful
     * the data will be written to internal storage instead
     *
     * @param d    The Dataframe object containing the new measurements
     * @param mqtt The current MQTT client object
     * @param c    The context to save the file to if the data cannot be sent
     */
    public void handleMessage(Dataframe d, MqttAndroidClient mqtt, Context c) {
        handler.post(() -> {
            try {
                final byte[] msg = ZipUtils.compress(JsonConverter.convert(d));

                if (mqtt.isConnected()) {
                    IMqttDeliveryToken token = Mqtt.publish(mqtt, "EDR", msg);

                    try {
                        token.waitForCompletion(Mqtt.TIMEOUT);

                        if (token.getException() != null) {
                            writeMsg(d.getData().get(d.getData().size() - 1).getTime(), msg, c);
                        } else {
                            Toast.makeText(c, "Measurements sent over MQTT", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        writeMsg(d.getData().get(d.getData().size() - 1).getTime(), msg, c);
                    }
                } else {
                    writeMsg(d.getData().get(d.getData().size() - 1).getTime(), msg, c);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });
    }
}

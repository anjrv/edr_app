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
            if (!mqtt.isConnected()) return; // Misfired request

            try {
                byte[] msg = FileUtils.retrieve(name, c);
                IMqttDeliveryToken token = Mqtt.publish(mqtt, "EDR", msg);

                token.waitForCompletion();
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
            boolean hasConnection = mqtt.isConnected();

            try {
                String json = JsonConverter.convert(d);
                byte[] msg = ZipUtils.compress(json);

                if (hasConnection) {
                    IMqttDeliveryToken token = Mqtt.publish(mqtt, "EDR", msg);
                    token.waitForCompletion(5);

                    if (token.getException() != null) {
                        FileUtils.write(d.getData().get(d.getData().size() - 1).getTime(), msg, c);
                        Toast.makeText(c, "Measurements written to backlog", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(c, "Measurements sent over MQTT", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    FileUtils.write(d.getData().get(d.getData().size() - 1).getTime(), msg, c);
                    Toast.makeText(c, "Measurements written to backlog", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}

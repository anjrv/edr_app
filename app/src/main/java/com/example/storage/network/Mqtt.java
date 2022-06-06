package com.example.storage.network;

import android.content.Context;
import android.os.Build;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

/**
 * Wrapper class to provide configuration for the paho MQTT methods
 */
public class Mqtt {

    /**
     * Generate a new MQTT client
     *
     * @param c      The context to provide for the MQTT client
     * @param server The server IP to attempt to connect to
     * @return The constructed client object
     */
    public static MqttAndroidClient generateClient(Context c, String server) {
        String clientId = Build.BRAND + "_" + Build.ID;
        return new MqttAndroidClient(c, "tcp://" + server + ":1883", clientId, Ack.AUTO_ACK);
    }

    /**
     * Attempt to establish and retain a connection for the given client
     *
     * @param client   The client to use for the connection
     * @param username The MOSQUITTO username to use
     * @param password The MOSQUITTO password to use
     */
    public static void connect(MqttAndroidClient client, String username, String password) {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setUserName(username);
        opts.setPassword(password.toCharArray());
        opts.setKeepAliveInterval(30);
        opts.setConnectionTimeout(5);
        opts.setAutomaticReconnect(true);

        client.connect(opts);
    }

    /**
     * Disconnect the given client
     *
     * @param client The client to disconnect
     */
    public static void disconnect(MqttAndroidClient client) {
        client.disconnect();
    }

    /**
     * Publish data to the connected MQTT broker
     *
     * @param client The client to use to publish with
     * @param topic  The topic to use for the payload
     * @param msg    The data to use for the payload
     * @return the MQTT delivery token for the transaction
     */
    public static IMqttDeliveryToken publish(MqttAndroidClient client, String topic, byte[] msg) {
        MqttMessage message = new MqttMessage();
        message.setPayload(msg);
        message.setQos(0);

        return client.publish(topic, message);
    }
}

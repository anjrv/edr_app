package com.example.storage.network;

import android.content.Context;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

public class Mqtt {
    public static MqttAndroidClient generateClient(Context c, String server) {
        String clientId = MqttClient.generateClientId();
        return new MqttAndroidClient(c, server, clientId, Ack.AUTO_ACK);
    }

    public static void connect(MqttAndroidClient client, String username, String password) {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setUserName(username);
        opts.setPassword(password.toCharArray());
        opts.setKeepAliveInterval(60);
        opts.setAutomaticReconnect(true);

        client.connect(opts);
    }

    public static void disconnect(MqttAndroidClient client){
        client.disconnect();
    }

    public static IMqttDeliveryToken publish(MqttAndroidClient client, String topic, byte[] msg) {
        MqttMessage message = new MqttMessage();
        message.setPayload(msg);
        message.setQos(0);

        return client.publish(topic, message);
    }
}

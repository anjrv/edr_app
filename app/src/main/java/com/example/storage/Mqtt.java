package com.example.storage;

import android.content.Context;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

public class Mqtt {
    public static MqttAndroidClient generateClient(Context c, String server) {
        String clientId = MqttClient.generateClientId();
        return new MqttAndroidClient(c, server, clientId, Ack.AUTO_ACK);
    }

    public static void connect(MqttAndroidClient client) {
        IMqttToken token = client.connect();
        token.setActionCallback(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                System.out.println("Connect success");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                System.out.println(exception);
            }
        });
    }

    public static void disconnect(MqttAndroidClient client){
        IMqttToken token = client.disconnect();
        token.setActionCallback(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                System.out.println("Disconnect success");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                System.out.println("Disconnect failure");
            }
        });
    }

    public static void publish(Context c, MqttAndroidClient client, String topic, String msg) {
        // if (!client.isConnected())
        //     client.connect();

        MqttMessage message = new MqttMessage();
        message.setPayload(msg.getBytes());
        message.setQos(0);

        client.publish(topic, message);

        // client.publish(topic, message, c, new IMqttActionListener() {
        //     @Override
        //     public void onSuccess(IMqttToken asyncActionToken) {
        //         System.out.println("sent");
        //     }

        //     @Override
        //     public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        //         System.out.println("failed");
        //     }
        // });
    }
}

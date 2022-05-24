package com.example.storage;

import android.content.Context;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Mqtt {
    public static MqttAndroidClient generateClient(Context c, String server) {
        String clientId = MqttClient.generateClientId();
        return new MqttAndroidClient(c, server, clientId);
    }

    public static void connect(MqttAndroidClient client) {
        try {
            IMqttToken token = client.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("Connect success");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("Connect failure");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static void disconnect(MqttAndroidClient client){
        try {
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
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish(MqttAndroidClient c, String topic, String msg) {
        try {
            if (!c.isConnected())
                c.connect();

            MqttMessage message = new MqttMessage();
            message.setPayload(msg.getBytes());
            message.setQos(0);

            c.publish(topic, message,null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("sent");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("failed");
                }
            });
        } catch ( MqttException e) {
            e.printStackTrace();
        }
    }
}

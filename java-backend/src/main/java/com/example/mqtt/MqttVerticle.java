package com.example.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

public class MqttVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MqttVerticle.class);

    @Override
    public void start() {

        String mqttBrokerHost = System.getenv("MQTT_BROKER_URL") != null
                ? System.getenv("MQTT_BROKER_URL") : "mosquitto";
        int mqttBrokerPort = System.getenv("MQTT_BROKER_PORT") != null ? Integer.parseInt(System.getenv("MQTT_BROKER_PORT")) : 1883;
        String mqttUsername = System.getenv("MQTT_USERNAME") != null ? System.getenv("MQTT_USERNAME") : "";
        String mqttPassword = System.getenv("MQTT_PASSWORD") != null ? System.getenv("MQTT_PASSWORD") : "";

        MqttClientOptions options = new MqttClientOptions()
                .setAutoKeepAlive(true);
                if (!mqttUsername.isBlank()) {
                    options.setUsername(mqttUsername);
                }
                if (!mqttPassword.isBlank()) {
                    options.setPassword(mqttPassword);
                }

        MqttClient mqttClient = MqttClient.create(vertx, options);

        mqttClient.connect(mqttBrokerPort, mqttBrokerHost, ar -> {
            if (ar.succeeded()) {
                logger.info("Connected to MQTT broker successfully: {}:{}", mqttBrokerHost, mqttBrokerPort);

                MqttController mqttController = new MqttController(mqttClient, vertx);
                mqttController.registerEventBusConsumers();
                mqttController.registerMqttConsumers();

                vertx.setPeriodic(10000, id -> {
                    vertx.eventBus().publish("mqtt.controller.ping.all", new JsonObject());
                });

            } else {
                logger.error("Failed to connect to MQTT: {}", ar.cause().getMessage());
            }
        }
        );

    }
}

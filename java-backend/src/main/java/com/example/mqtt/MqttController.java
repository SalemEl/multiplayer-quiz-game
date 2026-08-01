package com.example.mqtt;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttClient;

public class MqttController {

    private static final Logger logger = LoggerFactory.getLogger(MqttController.class);
    private final java.util.List<String> registeredControllers = new java.util.ArrayList<>();
    private final MqttService mqttService;
    private final MqttClient mqttClient;
    private final EventBus eventBus;

    final String mqttMessagePrefix = System.getenv("MQTT_MESSAGE_PREFIX") != null
            ? System.getenv("MQTT_MESSAGE_PREFIX") : "group-07/";

    public MqttController(MqttClient mqttClient, Vertx vertx) {
        this.mqttClient = mqttClient;
        this.mqttService = new MqttService(mqttClient);
        this.eventBus = vertx.eventBus();
    }

    public void registerEventBusConsumers() {

        // Game State an alle Controller senden
        this.eventBus.consumer("mqtt.game.state", msg -> {
            JsonObject body = (JsonObject) msg.body();
            mqttService.publishGameState(body.getString("state"));
        });

        // Ping an alle registrierten Controller senden
        this.eventBus.consumer("mqtt.controller.ping.all", pingMsg -> {
            for (String controllerId : registeredControllers) {
                JsonObject ping = new JsonObject()
                        .put("requestId", java.util.UUID.randomUUID().toString())
                        .put("ts", System.currentTimeMillis());
                mqttClient.publish(
                        mqttMessagePrefix + "controller/" + controllerId + "/ping",
                        ping.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false
                );
                logger.info("Ping sent to controller: {}", controllerId);
            }
        });

        // Spielername nach Controller-Bindung senden
        this.eventBus.consumer("mqtt.controller.assign", assignMsg -> {
            JsonObject body = (JsonObject) assignMsg.body();
            String controllerId = body.getString("controllerId");
            String playerName = body.getString("playerName");
            mqttService.publishAssign(controllerId, playerName);
        });

        // Frage an alle Controller senden
        this.eventBus.consumer("mqtt.game.question", questionMsg -> {
            JsonObject body = (JsonObject) questionMsg.body();
            mqttService.publishQuestion(body);
        });

        // Ergebnis an einen bestimmten Controller senden
        this.eventBus.consumer("mqtt.game.result", resultMsg -> {
            JsonObject body = (JsonObject) resultMsg.body();
            String controllerId = body.getString("controllerId");
            mqttService.publishResult(controllerId, body);
        });

        // Ergebnis an alle registrierten Controller senden
        this.eventBus.consumer("player.send.result", resultMsg -> {
            JsonObject body = (JsonObject) resultMsg.body();
            for (String controllerId : registeredControllers) {
                mqttService.publishResult(controllerId, body);
            }
        });

        // Einzelnen Ping senden
        this.eventBus.consumer("mqtt.controller.ping", pingMsg -> {
            JsonObject body = (JsonObject) pingMsg.body();
            mqttService.publishPing(body);
        });

        // Ready-Status vom Controller verarbeiten
        this.eventBus.consumer("mqtt.player.ready", readyMsg -> {
            JsonObject body = (JsonObject) readyMsg.body();
            String controllerId = body.getString("controllerId");
            Boolean ready = body.getBoolean("ready");
            logger.info("Ready received from controller: {} ready={}", controllerId, ready);
            eventBus.publish("player.set.ready", new JsonObject()
                    .put("controllerId", controllerId)
                    .put("ready", ready != null && ready)
            );
        });

        // Fehlermeldung bei unbekannter RFID-Karte an Controller senden
        this.eventBus.consumer("rfid.unknown", rfidMsg -> {
            JsonObject body = (JsonObject) rfidMsg.body();
            String controllerId = body.getString("controllerId");
            if (controllerId != null) {
                JsonObject errorMsg = new JsonObject()
                        .put("message", "RFID Karte nicht bekannt");
                mqttClient.publish(
                        mqttMessagePrefix + "controller/" + controllerId + "/error",
                        errorMsg.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false
                );
                logger.info("Unbekannte RFID-Karte - Fehlermeldung an {} gesendet", controllerId);
            }
        });
    }

    public void registerMqttConsumers() {

        mqttClient.publishHandler(message -> {
            Buffer payload = message.payload();
            String topic = message.topicName();

            logger.info("Message received via MQTT. Topic: {}, Payload: {}", topic, payload);

            JsonObject json;
            try {
                json = payload.toJsonObject();
            } catch (Exception e) {
                logger.error("Invalid MQTT JSON payload on topic {}: {}", topic, payload);
                return;
            }

            if (topic.contains("/controller/") && topic.endsWith("/ready")) {
                eventBus.publish("mqtt.player.ready", json);

            } else if (topic.equals(mqttMessagePrefix + "player/ready")) {
                eventBus.publish("mqtt.player.ready", json);

            } else if (topic.equals(mqttMessagePrefix + "player/answer")) {
                eventBus.publish("mqtt.player.answer", json);

            } else if (topic.contains("/controller/") && topic.endsWith("/answer")) {
                eventBus.publish("mqtt.player.answer", json);

            } else if (topic.contains("/controller/") && topic.endsWith("/register")) {
                String controllerId = json.getString("controllerId");
                String controllerType = json.getString("type", "hardware");

                if (controllerId != null && !registeredControllers.contains(controllerId)) {
                    registeredControllers.add(controllerId);
                    logger.info("Controller registered: {}", controllerId);
                    eventBus.publish("controller.registered", new JsonObject()
                            .put("controllerId", controllerId)
                            .put("type", controllerType)
                    );
                }
                eventBus.publish("mqtt.controller.register", json);

            } else if (topic.contains("/controller/") && topic.endsWith("/pong")) {
                eventBus.publish("mqtt.controller.pong", json);

            } else if (topic.contains("/controller/") && topic.endsWith("/rfid")) {
                // RFID-Scan vom Hardware-Controller empfangen
                String controllerId = json.getString("controllerId");
                String rfidUid = json.getString("rfidUid");
                logger.info("RFID received from controller {}: {}", controllerId, rfidUid);
                eventBus.publish("rfid.scanned", new JsonObject()
                        .put("controllerId", controllerId)
                        .put("rfidUid", rfidUid)
                );

            } else {
                logger.info("Handler for topic {} not implemented", topic);
            }
        });

        mqttClient.subscribe(Map.of(
                mqttMessagePrefix + "player/ready", 0,
                mqttMessagePrefix + "player/answer", 0,
                mqttMessagePrefix + "controller/+/register", 0,
                mqttMessagePrefix + "controller/+/pong", 0,
                mqttMessagePrefix + "controller/+/ready", 0,
                mqttMessagePrefix + "controller/+/answer", 0,
                mqttMessagePrefix + "controller/+/rfid", 0
        ));
    }
}
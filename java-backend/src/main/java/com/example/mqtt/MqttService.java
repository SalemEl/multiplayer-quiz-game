package com.example.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttClient;

public class MqttService {

    private final MqttClient mqttClient;

    private static final Logger logger = LoggerFactory.getLogger(MqttService.class);

    final String mqttMessagePrefix = System.getenv("MQTT_MESSAGE_PREFIX") != null ? System.getenv("MQTT_MESSAGE_PREFIX") : "group-07/";

    public MqttService(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    public void publishGameState(String state) {
        JsonObject data = new JsonObject().put("state", state);
        mqttClient.publish(mqttMessagePrefix + "game/state", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("MQTT published game state: {}", data.encode());
    }
    
    public void publishQuestion(JsonObject questionPayload) {
        mqttClient.publish(mqttMessagePrefix + "game/question", questionPayload.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("MQTT published question: {}", questionPayload.encode());
    }
    
    public void publishResult(String controllerId, JsonObject resultPayload) {
        mqttClient.publish(
            mqttMessagePrefix + "controller/" + controllerId + "/result",
            resultPayload.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false
        );
        logger.info("MQTT published result to {}: {}", controllerId, resultPayload.encode());
    }
    
    public void publishPing(JsonObject pingPayload) {
        mqttClient.publish(mqttMessagePrefix + "controller/ping", pingPayload.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("MQTT published ping: {}", pingPayload.encode());
    } 
    public void publishAssign(String controllerId, String playerName) {
        JsonObject payload = new JsonObject().put("playerName", playerName);
        mqttClient.publish(
            mqttMessagePrefix + "controller/" + controllerId + "/assign",
            payload.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false
        );
        logger.info("MQTT published assign to {}: {}", controllerId, payload.encode());
    }
}

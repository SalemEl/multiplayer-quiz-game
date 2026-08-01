#pragma once

#include <Arduino.h>
#include <PubSubClient.h>
#include <WiFiS3.h>

namespace net::wifi_mqtt {

// Verbindet mit WiFi und MQTT
bool ensureConnected();

// Verbindet nur mit WiFi
bool connectWiFi();

// Verbindet nur mit MQTT
bool connectMqtt();

// Gibt zurueck ob wir gerade verbunden sind
bool isConnected();

// NEU: muss in loop() aufgerufen werden - haelt Verbindung am Leben
void service();

// NEU: Sendet eine Nachricht an ein MQTT Topic
bool publish(String topic, String payload);

// NEU: Meldet sich fuer ein MQTT Topic an
bool subscribe(String topic);

// NEU: Registriert eine Funktion die aufgerufen wird wenn eine Nachricht ankommt
void setCallback(void (*callback)(char*, uint8_t*, unsigned int));

// Gibt die MAC-Adresse als String zurueck (z.B. "AA:BB:CC:DD:EE:FF")
String macAddressString();

} // namespace net::wifi_mqtt
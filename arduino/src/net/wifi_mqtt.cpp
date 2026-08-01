#include "net/wifi_mqtt.h"
#include "secrets.h"

namespace net::wifi_mqtt {

static WiFiClient   g_netClient;
static PubSubClient g_client(g_netClient);

// Tracks when we last tried to reconnect
static unsigned long lastReconnectAttempt = 0;

// -------------------------------------------------------

String macAddressString() {
    uint8_t mac[6];
    WiFi.macAddress(mac);
    char buf[18];
    sprintf(buf, "%02X:%02X:%02X:%02X:%02X:%02X",
            mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    return String(buf);
}

// -------------------------------------------------------

bool connectWiFi() {
    if (WiFi.status() == WL_CONNECTED) return true;

    Serial.print("Connecting to WiFi: ");
    Serial.println(WIFI_SSID);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    int retries = 40; // ~20 Sekunden warten
    while (WiFi.status() != WL_CONNECTED && retries-- > 0) {
        delay(500);
        Serial.print('.');
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {
        Serial.print("WiFi verbunden. IP: ");
        Serial.println(WiFi.localIP());
        return true;
    }

    Serial.println("WiFi Verbindung fehlgeschlagen.");
    return false;
}

// -------------------------------------------------------

bool connectMqtt() {
    if (!connectWiFi()) return false;

    g_client.setServer(MQTT_HOST, MQTT_PORT);

    if (g_client.connected()) return true;

    Serial.print("Verbinde mit MQTT Broker: ");
    Serial.println(MQTT_HOST);

    // Eindeutige Client-ID aus MAC-Adresse
    String clientId = "hw-" + macAddressString();

    for (int i = 0; i < 3; i++) {
        if (g_client.connect(clientId.c_str(), MQTT_USER, MQTT_PASS)) {
            Serial.println("MQTT verbunden!");
            return true;
        }
        Serial.print("MQTT fehlgeschlagen, rc=");
        Serial.println(g_client.state());
        delay(2000);
    }

    Serial.println("MQTT konnte nicht verbunden werden.");
    return false;
}

// -------------------------------------------------------

bool ensureConnected() {
    return connectMqtt();
}

// -------------------------------------------------------
// NEU: muss in loop() aufgerufen werden.
// Hält die MQTT-Verbindung am Leben und verarbeitet eingehende Nachrichten.
// Versucht alle 5 Sekunden neu zu verbinden wenn die Verbindung weg ist.

void service() {
    if (g_client.connected()) {
        g_client.loop();
        return;
    }

    // Nur alle 5 Sekunden neu versuchen, nicht in jedem loop()-Aufruf
    unsigned long now = millis();
    if (now - lastReconnectAttempt < 5000) return;

    lastReconnectAttempt = now;
    Serial.println("MQTT Verbindung verloren - versuche Reconnect...");
    connectMqtt();
}

// -------------------------------------------------------
// NEU: Sendet eine Nachricht an ein MQTT Topic

bool publish(String topic, String payload) {
    if (!g_client.connected()) {
        Serial.println("publish() uebersprungen - nicht verbunden.");
        return false;
    }
    return g_client.publish(topic.c_str(), payload.c_str());
}

// -------------------------------------------------------
// NEU: Meldet sich fuer ein MQTT Topic an

bool subscribe(String topic) {
    if (!g_client.connected()) return false;
    return g_client.subscribe(topic.c_str());
}

// -------------------------------------------------------
// NEU: Registriert eine Funktion die aufgerufen wird wenn eine Nachricht ankommt

void setCallback(void (*callback)(char*, uint8_t*, unsigned int)) {
    g_client.setCallback(callback);
}

// -------------------------------------------------------
// NEU: Gibt zurueck ob wir gerade verbunden sind

bool isConnected() {
    return g_client.connected();
}

} // namespace net::wifi_mqtt
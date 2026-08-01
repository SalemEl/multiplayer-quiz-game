#include <Arduino.h>

#include "config.h"
#include "secrets.h"
#include "hardware/buttons.h"
#include "hardware/neopixel.h"
#include "hardware/oled.h"
#include "hardware/rfid.h"
#include "net/wifi_mqtt.h"

// =======================
// Globale Spielvariablen
// =======================

String controllerId = "";
String playerName = "";
bool isReady = false;
String currentQuestionId = "";
String currentQuestionText = "";  // NEU: Fragetext speichern
String errorMessage = "";           // FIX 2: Fehlermeldung (z.B. RFID nicht bekannt)
unsigned long errorShowUntil = 0;   // Fehlermeldung nur X Sekunden zeigen
bool hasAnswered = false;
float totalPoints = 0.0;
bool mqttConnected = false;
String gameState = "LOBBY";

unsigned long lastButtonPressMs = 0;

// Speichert ob die letzte Antwort richtig war (für LED-Feedback)
bool lastAnswerWasCorrect = false;

// =======================
// MQTT Callback
// =======================

void onMqttMessage(char* topic, byte* payload, unsigned int length) {
    String message = "";
    for (int i = 0; i < length; i++) {
        message += (char)payload[i];
    }

    String topicStr = String(topic);

    Serial.print("Nachricht empfangen [");
    Serial.print(topicStr);
    Serial.print("]: ");
    Serial.println(message);

    // --- game/state ---
    if (topicStr == String(MQTT_PREFIX) + "game/state") {

        if (message.indexOf("\"state\":\"LOBBY\"") != -1) {
            gameState = "LOBBY";
            totalPoints = 0.0;
            hasAnswered = false;
            isReady = false;
            Serial.println("Spielzustand: LOBBY");
        }
        else if (message.indexOf("\"state\":\"COUNTDOWN\"") != -1) {
            gameState = "COUNTDOWN";
            Serial.println("Spielzustand: COUNTDOWN");
        }
        else if (message.indexOf("\"state\":\"QUESTION\"") != -1) {
            gameState = "QUESTION";
            Serial.println("Spielzustand: QUESTION");
        }
        else if (message.indexOf("\"state\":\"EVALUATION\"") != -1) {
            gameState = "EVALUATION";
            Serial.println("Spielzustand: EVALUATION");
        }
        else if (message.indexOf("\"state\":\"END\"") != -1) {
            gameState = "END";
            Serial.println("Spielzustand: END");
        }
    }

    // --- game/question ---
    else if (topicStr == String(MQTT_PREFIX) + "game/question") {
        // questionId parsen
        int start = message.indexOf("\"questionId\":\"") + 13;
        int end = message.indexOf("\"", start);
        currentQuestionId = message.substring(start, end);

        // NEU: questionText parsen und anzeigen
        int textStart = message.indexOf("\"questionText\":\"") + 15;
        int textEnd = message.indexOf("\"", textStart);
        if (textStart > 15 && textEnd > textStart) {
            currentQuestionText = message.substring(textStart, textEnd);
        } else {
            currentQuestionText = "Frage aktiv";
        }

        hasAnswered = false;
        gameState = "QUESTION";

        Serial.print("Neue Frage: ");
        Serial.println(currentQuestionText);
    }

    // --- controller/{id}/assign ---
    else if (topicStr == String(MQTT_PREFIX) + "controller/" + controllerId + "/assign") {
        int start = message.indexOf("\"playerName\":\"") + 14;
        int end = message.indexOf("\"", start);
        playerName = message.substring(start, end);

        Serial.print("Zugewiesen an Spieler: ");
        Serial.println(playerName);
    }

    // --- controller/{id}/result ---
    else if (topicStr == String(MQTT_PREFIX) + "controller/" + controllerId + "/result") {
        if (message.indexOf("\"correct\":true") != -1) {
            lastAnswerWasCorrect = true;  //  für LED-Feedback merken

            int start = message.indexOf("\"points\":") + 9;
            int end = message.indexOf("}", start);
            String pointsStr = message.substring(start, end);
            totalPoints += pointsStr.toFloat();

            Serial.print("Richtig! Gesamtpunkte: ");
            Serial.println(totalPoints);
        } else {
            lastAnswerWasCorrect = false;  // für LED-Feedback merken
            Serial.println("Falsch.");
        }
    }

    // FIX 2: Fehlermeldung empfangen (z.B. RFID nicht bekannt)
    else if (topicStr == String(MQTT_PREFIX) + "controller/" + controllerId + "/error") {
        int start = message.indexOf("\"message\":\"") + 11;
        int end = message.indexOf("\"", start);
        if (start > 11 && end > start) {
            errorMessage = message.substring(start, end);
        } else {
            errorMessage = "Fehler!";
        }
        errorShowUntil = millis() + 3000; // 3 Sekunden anzeigen
        Serial.print("Fehlermeldung: ");
        Serial.println(errorMessage);
    }

    // --- controller/{id}/ping ---
    else if (topicStr == String(MQTT_PREFIX) + "controller/" + controllerId + "/ping") {
        String pongTopic = String(MQTT_PREFIX) + "controller/" + controllerId + "/pong";
        String pongPayload = "{\"controllerId\":\"" + controllerId + "\"}";
        net::wifi_mqtt::publish(pongTopic, pongPayload);
        Serial.println("Pong gesendet.");
    }
}

// =======================
// Topics abonnieren und Controller registrieren
// =======================

void subscribeAndRegister() {
    net::wifi_mqtt::subscribe(String(MQTT_PREFIX) + "game/state");
    net::wifi_mqtt::subscribe(String(MQTT_PREFIX) + "game/question");
    net::wifi_mqtt::subscribe(String(MQTT_PREFIX) + "controller/" + controllerId + "/assign");
    net::wifi_mqtt::subscribe(String(MQTT_PREFIX) + "controller/" + controllerId + "/result");
    net::wifi_mqtt::subscribe(String(MQTT_PREFIX) + "controller/" + controllerId + "/ping");
    // FIX 2: Error Topic abonnieren für RFID Fehlermeldungen
    net::wifi_mqtt::subscribe(String(MQTT_PREFIX) + "controller/" + controllerId + "/error");

    String topic = String(MQTT_PREFIX) + "controller/" + controllerId + "/register";
    String payload = "{\"controllerId\":\"" + controllerId + "\",\"type\":\"hardware\"}";
    net::wifi_mqtt::publish(topic, payload);

    Serial.println("Topics abonniert und Controller registriert.");
}

// =======================
// Antwort senden
// =======================

void sendAnswer(String answer) {
    String topic = String(MQTT_PREFIX) + "controller/" + controllerId + "/answer";
    // FIX: playerName mitschicken damit der Score dem richtigen Spieler zugeordnet wird
    String payload = "{\"controllerId\":\"" + controllerId + "\""
                   + ",\"playerName\":\"" + playerName + "\""
                   + ",\"questionId\":\"" + currentQuestionId + "\""
                   + ",\"answer\":\"" + answer + "\"}";
    net::wifi_mqtt::publish(topic, payload);

    Serial.print("Antwort gesendet: ");
    Serial.println(answer);
}

// =======================
// Buttons verarbeiten
// =======================

void handleButtons() {
    if (millis() - lastButtonPressMs < 200) return;

    if (hw::buttons::isPressed(PIN_BTN_BLUE) && gameState == "LOBBY") {
        lastButtonPressMs = millis();
        isReady = !isReady;

        String topic = String(MQTT_PREFIX) + "controller/" + controllerId + "/ready";
        String payload = "{\"controllerId\":\"" + controllerId + "\""
                       + ",\"ready\":" + (isReady ? "true" : "false") + "}";
        net::wifi_mqtt::publish(topic, payload);

        Serial.print("Ready: ");
        Serial.println(isReady ? "true" : "false");
    }

    if (gameState == "QUESTION" && !hasAnswered) {
        // B1=blau=A, B2=gruen=B, B3=rot=C, B4=gelb=D
        if (hw::buttons::isPressed(PIN_BTN_BLUE)) {
            lastButtonPressMs = millis();
            hasAnswered = true;
            sendAnswer("A");
        }
        else if (hw::buttons::isPressed(PIN_BTN_GREEN)) {
            lastButtonPressMs = millis();
            hasAnswered = true;
            sendAnswer("B");
        }
        else if (hw::buttons::isPressed(PIN_BTN_YELLOW)) {
            lastButtonPressMs = millis();
            hasAnswered = true;
            sendAnswer("C");
        }
        else if (hw::buttons::isPressed(PIN_BTN_RED)) {
            lastButtonPressMs = millis();
            hasAnswered = true;
            sendAnswer("D");
        }
    }
}

// =======================
// Display aktualisieren
// =======================

void updateDisplay() {
    auto& d = hw::oled::display();
    d.clearDisplay();
    d.setTextColor(WHITE);
    d.setTextSize(1);
    d.setFont(nullptr);

    if (!mqttConnected) {
        d.setCursor(0, 0);
        d.println("Kein MQTT!");
        d.println("Verbinde...");
    }
    // FIX 2: Fehlermeldung anzeigen wenn aktiv
    else if (errorMessage.length() > 0 && millis() < errorShowUntil) {
        d.setCursor(0, 0);
        d.println("FEHLER:");
        d.println(errorMessage);
    }
    else if (gameState == "LOBBY") {
        d.setCursor(0, 0);
        d.println("LOBBY");
        d.println(playerName.length() > 0 ? playerName : "Nicht angemeldet");
        d.println(isReady ? "Status: Bereit" : "Status: Nicht bereit");
        d.println("Grün = Bereit togglen");
    }
    else if (gameState == "COUNTDOWN") {
        d.setCursor(0, 0);
        d.println("Spiel startet...");
        d.println(playerName);
    }
    else if (gameState == "QUESTION") {
        d.setCursor(0, 0);
        // NEU: Fragetext anzeigen (aufgeteilt in Zeilen wegen kleinem Display)
        if (hasAnswered) {
            d.println("Geantwortet!");
            d.print("Punkte: ");
            d.println(totalPoints);
        } else {
            d.println("FRAGE:");
            // Fragetext in Zeilen aufteilen (max ~18 Zeichen pro Zeile)
            String q = currentQuestionText;
            int lineLen = 18;
            for (int i = 0; i < q.length() && i < lineLen * 3; i += lineLen) {
                d.println(q.substring(i, min((int)q.length(), i + lineLen)));
            }
            d.println("A B C D druecken");
        }
    }
    else if (gameState == "EVALUATION") {
        d.setCursor(0, 0);
        d.println("AUSWERTUNG");
        d.println(lastAnswerWasCorrect ? "Richtig!" : "Falsch!");
        d.print("Punkte: ");
        d.println(totalPoints);
    }
    else if (gameState == "END") {
        d.setCursor(0, 0);
        d.println("SPIEL BEENDET");
        d.println(playerName);
        d.print("Gesamt: ");
        d.println(totalPoints);
    }

    d.display();
}

// =======================
// LEDs aktualisieren
// =======================

void updateLeds() {
    auto& strip = hw::neopixel::strip();

    if (!mqttConnected) {
        // Kein MQTT: alle LEDs rot
        strip.fill(strip.Color(80, 0, 0));
    }
    else if (gameState == "LOBBY") {
        // Bereit: blau — Nicht bereit: gelb
        if (isReady) {
            strip.fill(strip.Color(0, 0, 80));
        } else {
            strip.fill(strip.Color(60, 60, 0));
        }
    }
    else if (gameState == "COUNTDOWN") {
        // Countdown: alle weiss
        strip.fill(strip.Color(60, 60, 60));
    }
    else if (gameState == "QUESTION") {
        if (hasAnswered) {
            // Schon geantwortet: orange - warte auf Ergebnis
            strip.fill(strip.Color(80, 30, 0));
        } else {
            // Noch nicht geantwortet: weiss - Buttons sind aktiv
            strip.fill(strip.Color(60, 60, 60));
        }
    }
    else if (gameState == "EVALUATION") {
        // Richtig: grün — Falsch: rot
        if (lastAnswerWasCorrect) {
            strip.fill(strip.Color(0, 80, 0));
        } else {
            strip.fill(strip.Color(80, 0, 0));
        }
    }
    else if (gameState == "END") {
        // Spiel vorbei: blau
        strip.fill(strip.Color(0, 0, 80));
    }

    strip.show();
}

// =======================
// Setup
// =======================

void setup() {
    Serial.begin(9600);
    while (!Serial) { delay(10); }

    Serial.println("=== Hardware Controller startet ===");

    hw::buttons::begin();
    hw::neopixel::begin();
    hw::oled::begin();
    hw::oled::showThmLogo();
    hw::rfid::begin();

    controllerId = "hw-" + net::wifi_mqtt::macAddressString();
    controllerId.replace(":", "");
    Serial.print("Controller-ID: ");
    Serial.println(controllerId);

    net::wifi_mqtt::setCallback(onMqttMessage);

    if (net::wifi_mqtt::ensureConnected()) {
        mqttConnected = true;
        subscribeAndRegister();
        Serial.println("Verbindung OK.");
    } else {
        Serial.println("Verbindung fehlgeschlagen.");
    }
}

// =======================
// Loop
// =======================

void loop() {
    bool wasConnected = mqttConnected;
    net::wifi_mqtt::service();
    mqttConnected = net::wifi_mqtt::isConnected();

    if (!wasConnected && mqttConnected) {
        Serial.println("Reconnect erfolgreich - neu abonnieren.");
        subscribeAndRegister();
    }

    hw::rfid::service();

    if (hw::rfid::lastUid() != "") {
        String uid = hw::rfid::lastUid();
        Serial.print("RFID gescannt: ");
        Serial.println(uid);

        String rfidTopic = String(MQTT_PREFIX) + "controller/" + controllerId + "/rfid";
        String rfidPayload = "{\"controllerId\":\"" + controllerId + "\",\"rfidUid\":\"" + uid + "\"}";
        net::wifi_mqtt::publish(rfidTopic, rfidPayload);
    }

    handleButtons();
    updateDisplay();
    updateLeds();

    delay(10);
}
# Multiplayer Trivia Quiz

Wintersemester 2025/2026 | GEN1002 Informatik-Projekt | THM

---

## Team

Hochschulprojekt (GEN1002, THM Gießen, WiSe 2025/26) – entwickelt im 2er-Team.

| Name | Verantwortung |
|------|---------------|
| **Salem Elabid** (dieses Repository) | Web-Frontend, Web-Controller, ESP32-Hardware-Controller (Firmware), MQTT-Kommunikationsschicht, Integration |
| Salah-Eddine Safouate | Java-Backend, Datenbank, Spiellogik |


---

## Konfiguration & Secrets

Zugangsdaten liegen **nicht** im Repository. Vor dem Start:

- `.env` aus `.env.example` erstellen: `cp .env.example .env`
- Fuer den Hardware-Controller: `arduino/include/secrets.h` aus `secrets.h.example` erstellen

Beide Dateien sind per `.gitignore` ausgeschlossen und muessen mit eigenen Werten befuellt werden.

---

## Projektbeschreibung

Ein vernetztes Multiplayer-Quizspiel mit Web-Frontend, Java-Backend und MQTT-basierter Kommunikation. Spieler können sich über den Browser oder einen Hardware-Controller (ESP32) am Spiel beteiligen und Multiple-Choice-Fragen beantworten.

---

## Projektdokumentation

- [Projektbeschreibung (DE)](./doc/ProjektBeschreibung.md)
- [Projektbeschreibung (EN)](./doc/ProjectDescription.md)
- [Designvorschlag (DE)](./doc/DesignVorschlag.md)
- [Designvorschlag (EN)](./doc/DesignProposal.md)

---

## Verwendete Technologien

| Technologie | Zweck |
|------------|-------|
| **Java / Vert.x** | Backend – REST API und Event-Bus |
| **MariaDB** | Datenbank – Nutzer, Fragen, Highscores |
| **MQTT / Mosquitto** | Echtzeit-Kommunikation zwischen Controllern und Backend |
| **Vanilla JavaScript** | Frontend – kein Framework, reines HTML/CSS/JS |
| **ESP32 / Arduino** | Hardware-Controller mit Buttons, LEDs, OLED und RFID |
| **Docker** | Alle Dienste laufen als Container |
| **NGINX** | Statisches Frontend und Web-Controller bereitstellen |

---

## Systemarchitektur

<img src="doc/comp_arch.png" width="1000px">

Das System besteht aus vier Hauptkomponenten:

- **Frontend** – zeigt Lobby, Spielstatus und Highscores. Kommuniziert per REST mit dem Backend (Polling alle 1 Sekunde).
- **Web-Controller** – Browser-basierter Buzzer. Kommuniziert per MQTT über WebSocket.
- **Backend** – verwaltet Spieler, Spielablauf, Fragen und Highscores.
- **Hardware-Controller** – ESP32 mit Buttons, LEDs, OLED-Display und RFID-Reader. Kommuniziert per MQTT.

---

## Voraussetzungen

### Betriebssystem

Ubuntu wird empfohlen. Windows-Nutzer können WSL verwenden, allerdings ohne offizielle Unterstützung.

### Docker installieren

Anleitung: https://docs.docker.com/desktop/

## Projekt starten

### 1. Repository klonen

```bash
git clone https://github.com/SalemEl/multiplayer-quiz-game.git
cd multiplayer-quiz-game
```

### 2. Umgebungsvariablen prüfen

Die Datei `.env` wird aus `.env.example` erstellt (`cp .env.example .env`) und mit eigenen Werten befuellt. Sie enthaelt alle Konfigurationswerte:

```
MQTT_BROKER_URL=mosquitto
MQTT_BROKER_PORT_WS=9001
MQTT_BROKER_PORT_TCP=1883
MQTT_USERNAME=DEIN_MQTT_USER
MQTT_PASSWORD=DEIN_MQTT_PASSWORT
MQTT_MESSAGE_PREFIX=${MQTT_USERNAME}/

DB_USER=DEIN_DB_USER
DB_PASSWORD=DEIN_DB_PASSWORT
DB_ROOT_PASSWORD=DEIN_DB_ROOT_PASSWORT
DB_NAME=game
DB_HOST=mariadb
DB_PORT=3306
```

Für Hardware-Tests über das THM-Netzwerk:
```
MQTT_BROKER_URL=iti-mqtt.mni.thm.de
```

### 3. Projekt bauen und starten

```bash
docker compose up --build
```

Wenn alles funktioniert, erscheint:
```
✔ Container mosquitto       Started
✔ Container mariadb         Started
✔ Container frontend        Started
✔ Container web-controller  Started
✔ Container phpmyadmin      Started
✔ Container java-backend    Started
```

### 4. Projekt stoppen

```bash
docker compose down
```

Alles zurücksetzen (Vorsicht – löscht alle Docker-Daten):
```bash
docker system prune -a --volumes --force
```

---

## Dienste und Adressen

| Dienst | Port | URL |
|--------|------|-----|
| Frontend | 80 | http://localhost |
| Web-Controller | 81 | http://localhost:81 |
| Backend API | 8080 | http://localhost:8080 |
| phpMyAdmin | 8081 | http://localhost:8081 |
| MariaDB | 3306 | nur intern |
| MQTT (TCP) | 1883 | nur intern |
| MQTT (WebSocket) | 9001 | nur intern |

---

## Spielablauf

1. Nutzer registriert sich mit Benutzername und Passwort
2. Nutzer loggt sich ein und wählt einen verfügbaren Controller
3. Controller verbindet sich per MQTT und zeigt Lobby-Status an
4. Nutzer setzt sich auf „Ready"
5. Wenn alle aktiven Spieler bereit sind, startet ein 3-Sekunden-Countdown
6. Fragen werden nacheinander angezeigt (30 Sekunden pro Frage)
7. Spieler antworten mit A/B/C/D über ihren Controller
8. Nach jeder Frage gibt es 3 Sekunden Auswertung
9. Am Ende werden die Highscores angezeigt

---

## Punktevergabe

| Schwierigkeit | Basispunkte |
|--------------|-------------|
| Leicht | 1 |
| Mittel | 2 |
| Schwer | 3 |

Zeitfaktor (nur bei richtiger Antwort):

| Antwortzeit | Faktor |
|------------|--------|
| 0–5 Sekunden | 1.0 |
| 5–10 Sekunden | 0.9 |
| 10–15 Sekunden | 0.8 |
| 15–20 Sekunden | 0.7 |
| 20–25 Sekunden | 0.6 |
| 25–30 Sekunden | 0.5 |
| Ab 30 Sekunden | 0.0 |

Punkte = Basispunkte × Zeitfaktor (keine Rundung)

---

## Designentscheidungen

### Mehrfach-Login

Wenn ein Nutzer sich einloggt, obwohl er bereits eingeloggt ist, wird die alte Sitzung automatisch ersetzt. Der neue Login verdrängt den alten. Das erlaubt es Spielern, sich nach einem Browser-Refresh wieder einzuloggen, ohne dass ein expliziter Logout nötig ist.

### Disconnect-Verhalten

Wenn ein Controller nicht mehr auf Heartbeat-Pings antwortet (2 verpasste Pings = ca. 20 Sekunden), wird er als „Disconnected" markiert. Der Spieler bleibt in der Lobby sichtbar, zählt aber nicht mehr als aktiver Spieler. Der Spielfluss wird nicht unterbrochen. Ein Rejoin während einer laufenden Partie ist nicht vorgesehen.

### RFID-Login

Spieler können sich am Hardware-Controller per RFID-Karte anmelden, wenn ihre RFID-ID in der Datenbank hinterlegt ist. Unbekannte Karten zeigen die Meldung „RFID Karte nicht bekannt" auf dem OLED-Display.

---

## Hardware-Controller (ESP32)

Der Hardware-Controller verbindet sich automatisch per WLAN und MQTT. Er wird über das MICRO Remote-Labor der THM bereitgestellt.

**Steuerung:**
- B1 (Blau) = Antwort A / Lobby: Ready togglen
- B2 (Grün) = Antwort B
- B3 (Gelb) = Antwort C
- B4 (Rot) = Antwort D

**LED-Farben:**
| Farbe | Bedeutung |
|-------|-----------|
| Blau | Spieler ist bereit |
| Gelb | Spieler ist nicht bereit |
| Weiß | Frage aktiv / Countdown |
| Orange | Antwort abgegeben, warte auf Ergebnis |
| Grün | Richtige Antwort |
| Rot | Falsche Antwort / kein MQTT |

**OLED-Anzeige:**
- Lobby: Spielername + Ready-Status
- Frage: „FRAGE AKTIV" + Punktestand
- Auswertung: Richtig/Falsch + Punkte
- Ende: Gesamtpunkte

**Für Hardware-Tests (THM VPN erforderlich):**
```
MQTT_BROKER_URL=iti-mqtt.mni.thm.de
```

---

## Fehlerbehebung

**Backend startet nicht:**
- Prüfen ob MariaDB läuft: `docker ps`
- Logs ansehen: `docker logs java-backend`

**MQTT-Verbindungsfehler:**
- Prüfen ob Mosquitto läuft: `docker ps`
- `.env` Datei prüfen: `MQTT_BROKER_URL=mosquitto`

**Web-Controller verbindet nicht:**
- Browser-Konsole (F12) öffnen und Fehlermeldung prüfen
- Sicherstellen dass Port 9001 nicht blockiert ist

**Datenbank-Fehler:**
- `docker compose down` dann `docker compose up --build`
- Bei hartnäckigen Problemen: `docker system prune -a --volumes --force` (löscht alle Daten!)

## Hardware-Controller testen (MICRO Remote-Labor)

Um den Hardware-Controller über das MICRO Remote-Labor zu testen,
muss der MQTT-Broker auf den THM-Server umgestellt werden.

### Schritt 1 — THM VPN verbinden
Zuerst mit dem THM VPN verbinden. Ohne VPN ist der THM-Broker nicht erreichbar.

### Schritt 2 — .env auf THM-Broker umstellen
```
MQTT_BROKER_URL=iti-mqtt.mni.thm.de
MQTT_USERNAME=group-07
MQTT_PASSWORD=DEIN_MQTT_PASSWORT
MQTT_MESSAGE_PREFIX=group-07/
```

### Schritt 3 — Projekt neu starten
```bash
docker compose down
docker compose up --build
```

### Schritt 4 — Hardware-Controller hochladen
Im MICRO Lab die `arduino.zip` hochladen. Der Controller verbindet sich
automatisch mit dem THM-Broker und erscheint dann im Frontend-Dropdown.

### Schritt 5 — Nach dem Test zurücksetzen
Für den normalen Betrieb (ohne Hardware) .env zurücksetzen:
```
MQTT_BROKER_URL=mosquitto
MQTT_USERNAME=DEIN_MQTT_USER
MQTT_PASSWORD=DEIN_MQTT_PASSWORT
MQTT_MESSAGE_PREFIX=${MQTT_USERNAME}/
```
Dann wieder `docker compose down && docker compose up --build`.

> **Hinweis:** Die Verzögerung zwischen Hardware- und Web-Controller
> ist normal und entsteht durch die Netzwerk-Latenz zum THM-Broker.

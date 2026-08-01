package com.example.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository repository;
    private final Vertx vertx;

    private final Set<String> activeUsers = new HashSet<>();

    // username -> controllerId
    private final Map<String, String> boundControllers = new HashMap<>();

    // controllerId -> controllerType
    private final Map<String, String> controllerTypes = new HashMap<>();

    // username -> ready
    private final Map<String, Boolean> readyStates = new HashMap<>();
    private final Map<String, String> tokens = new HashMap<>();

    // FIX 1: Disconnect tracking - controllerId -> missed ping count
    private final Map<String, Integer> missedPings = new HashMap<>();

    // FIX 1: Set of disconnected usernames
    private final Set<String> disconnectedUsers = new HashSet<>();

    public PlayerService(Vertx vertx) {
        this.vertx = vertx;
        this.repository = new PlayerRepository(vertx);

        // Beispiel Web-Controller fuer lokalen Test
        controllerTypes.put("web-1", "web");
        controllerTypes.put("web-2", "web");

        // Wenn ein Controller sich per MQTT registriert
        vertx.eventBus().consumer("controller.registered", msg -> {
            JsonObject body = (JsonObject) msg.body();
            String controllerId = body.getString("controllerId");
            String type = body.getString("type", "hardware");

            if (!controllerTypes.containsKey(controllerId)) {
                controllerTypes.put(controllerId, type);
                logger.info("Controller automatisch registriert: {} ({})", controllerId, type);
            }
            // Reset missed pings when controller registers
            missedPings.put(controllerId, 0);
        });

        // Ready-Status von MQTT verarbeiten
        vertx.eventBus().consumer("player.set.ready", msg -> {
            JsonObject body = (JsonObject) msg.body();
            String controllerId = body.getString("controllerId");
            boolean ready = body.getBoolean("ready", false);

            for (Map.Entry<String, String> entry : boundControllers.entrySet()) {
                if (entry.getValue().equals(controllerId)) {
                    String username = entry.getKey();
                    setReady(username, ready);
                    logger.info("Ready via MQTT aktualisiert: {} = {}", username, ready);
                    break;
                }
            }
        });

        // FIX 1: Pong empfangen → missed pings zurücksetzen
        vertx.eventBus().consumer("mqtt.controller.pong", msg -> {
            JsonObject body = (JsonObject) msg.body();
            String controllerId = body.getString("controllerId");
            if (controllerId != null) {
                missedPings.put(controllerId, 0);

                // Wenn Controller wieder antwortet → aus disconnected entfernen
                for (Map.Entry<String, String> entry : boundControllers.entrySet()) {
                    if (entry.getValue().equals(controllerId)) {
                        String username = entry.getKey();
                        if (disconnectedUsers.remove(username)) {
                            logger.info("Controller wieder verbunden: {} ({})", username, controllerId);
                        }
                        break;
                    }
                }
            }
        });

        // FIX 1: Ping gesendet → missed pings erhöhen für alle gebundenen Controller
        vertx.eventBus().consumer("mqtt.controller.ping.all", msg -> {
            for (Map.Entry<String, String> entry : boundControllers.entrySet()) {
                String username = entry.getKey();
                String controllerId = entry.getValue();
                if (controllerId == null) continue;

                int missed = missedPings.getOrDefault(controllerId, 0) + 1;
                missedPings.put(controllerId, missed);

                // Nach 2 verpassten Pings → disconnected
                if (missed >= 2 && !disconnectedUsers.contains(username)) {
                    disconnectedUsers.add(username);
                    logger.info("Controller disconnected (missed {} pings): {} ({})",
                            missed, username, controllerId);
                }
            }
        });

        // FIX 2: RFID Login Handler
        vertx.eventBus().consumer("rfid.scanned", msg -> {
            JsonObject body = (JsonObject) msg.body();
            String controllerId = body.getString("controllerId");
            String rfidUid = body.getString("rfidUid");

            logger.info("RFID scanned: {} on controller {}", rfidUid, controllerId);

            repository.findUserByRfid(rfidUid).onSuccess(username -> {
                if (username == null) {
                    // Unbekannte RFID-Karte
                    logger.info("Unbekannte RFID-Karte: {}", rfidUid);
                    vertx.eventBus().publish("rfid.unknown", new JsonObject()
                            .put("controllerId", controllerId)
                    );
                    return;
                }

                // Nutzer gefunden → einloggen
                if (!activeUsers.contains(username)) {
                    activeUsers.add(username);
                    readyStates.put(username, false);
                    String token = UUID.randomUUID().toString();
                    tokens.put(token, username);
                }

                // Controller binden wenn noch frei
                if (!boundControllers.containsValue(controllerId)) {
                    boundControllers.put(username, controllerId);
                    missedPings.put(controllerId, 0);
                }

                // Assign via MQTT senden
                vertx.eventBus().publish("mqtt.controller.assign", new JsonObject()
                        .put("controllerId", controllerId)
                        .put("playerName", username)
                );

                logger.info("RFID Login erfolgreich: {} auf {}", username, controllerId);
            }).onFailure(err -> {
                logger.error("RFID lookup Fehler: {}", err.getMessage());
                vertx.eventBus().publish("rfid.unknown", new JsonObject()
                        .put("controllerId", controllerId)
                );
            });
        });
    }

    public boolean register(String username, String password) {
        if (repository.existsByUsername(username)) {
            return false;
        }
        repository.saveUser(username, password);
        return true;
    }

    public String login(String username, String password) {
        if (!repository.validateUser(username, password)) {
            return null;
        }

        if (activeUsers.contains(username)) {
            String oldToken = null;
            for (java.util.Map.Entry<String, String> entry : tokens.entrySet()) {
                if (entry.getValue().equals(username)) {
                    oldToken = entry.getKey();
                    break;
                }
            }
            if (oldToken != null) tokens.remove(oldToken);
            activeUsers.remove(username);
            readyStates.remove(username);
            boundControllers.remove(username);
        }

        activeUsers.add(username);
        readyStates.put(username, false);
        disconnectedUsers.remove(username);

        String token = UUID.randomUUID().toString();
        tokens.put(token, username);
        return token;
    }

    public void logout(String token) {
        String username = tokens.get(token);
        if (username != null) {
            activeUsers.remove(username);
            tokens.remove(token);
            readyStates.remove(username);
            boundControllers.remove(username);
            disconnectedUsers.remove(username);
            logger.info("User logged out: {}", username);
        }
    }

    public Set<String> getActiveUsers() { return activeUsers; }

    public String getUsernameByToken(String token) { return tokens.get(token); }

    public JsonObject getAvailableControllersJson() {
        JsonArray controllers = new JsonArray();
        for (Map.Entry<String, String> entry : controllerTypes.entrySet()) {
            String controllerId = entry.getKey();
            String controllerType = entry.getValue();
            if (!boundControllers.containsValue(controllerId)) {
                controllers.add(new JsonObject()
                        .put("controllerId", controllerId)
                        .put("type", controllerType));
            }
        }
        return new JsonObject().put("controllers", controllers);
    }

    public JsonObject getLobbyStatusJson() {
        JsonArray players = new JsonArray();

        for (String username : activeUsers) {
            String controllerId = boundControllers.get(username);
            boolean ready = readyStates.getOrDefault(username, false);

            // FIX 1: disconnected status
            String status;
            if (disconnectedUsers.contains(username)) {
                status = "disconnected";
            } else {
                status = ready ? "ready" : "not-ready";
            }

            players.add(new JsonObject()
                    .put("name", username)
                    .put("status", status)
                    .put("controllerId", controllerId));
        }

        return new JsonObject().put("players", players);
    }

    public Map<String, String> getAvailableControllers() {
        Map<String, String> available = new HashMap<>();
        for (Map.Entry<String, String> entry : controllerTypes.entrySet()) {
            if (!boundControllers.containsValue(entry.getKey())) {
                available.put(entry.getKey(), entry.getValue());
            }
        }
        return available;
    }

    public boolean bindController(String username, String controllerId) {
        if (!activeUsers.contains(username)) return false;
        if (!controllerTypes.containsKey(controllerId)) return false;
        if (boundControllers.containsValue(controllerId)) return false;

        boundControllers.put(username, controllerId);
        missedPings.put(controllerId, 0);
        return true;
    }

    public boolean setReady(String username, boolean ready) {
        if (!activeUsers.contains(username)) return false;
        if (disconnectedUsers.contains(username)) return false;

        readyStates.put(username, ready);

        // Only non-disconnected players count for all-ready check
        long nonDisconnected = activeUsers.stream()
                .filter(u -> !disconnectedUsers.contains(u))
                .count();

        boolean allReady = activeUsers.stream()
                .filter(u -> !disconnectedUsers.contains(u))
                .allMatch(u -> readyStates.getOrDefault(u, false));

        if (allReady && nonDisconnected > 0) {
            vertx.eventBus().publish("game.all.ready", new JsonObject());
            logger.info("Alle Spieler ready - Countdown startet!");
        }

        return true;
    }

    public String getLobbyStatusDetailed() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (String username : activeUsers) {
            if (!first) sb.append(", ");
            String controllerId = boundControllers.getOrDefault(username, "none");
            boolean ready = readyStates.getOrDefault(username, false);
            sb.append("{username=").append(username)
                    .append(", ready=").append(ready).append("}");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
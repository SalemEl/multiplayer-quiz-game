package com.example.player;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.HashMap;
import java.util.Map;

public class PlayerRepository {

    private final Map<String, String> users = new HashMap<>();

    //   RFID support - rfidUid -> username
    private final Map<String, String> rfidToUser = new HashMap<>();

    public PlayerRepository(Vertx vertx) {
        // In-memory storage
    }

    public boolean existsByUsername(String username) {
        return users.containsKey(username);
    }

    public void saveUser(String username, String password) {
        users.put(username, password);
    }

    public boolean validateUser(String username, String password) {
        return users.containsKey(username) && users.get(username).equals(password);
    }

    // RFID methods
    public Future<String> findUserByRfid(String rfidUid) {
        return Future.succeededFuture(rfidToUser.get(rfidUid));
    }

    public void saveRfid(String username, String rfidUid) {
        // Remove old mapping for this user if exists
        rfidToUser.entrySet().removeIf(e -> e.getValue().equals(username));
        // Add new mapping
        rfidToUser.put(rfidUid, username);
    }

    public void removeRfid(String username) {
        rfidToUser.entrySet().removeIf(e -> e.getValue().equals(username));
    }

    public String getRfidByUsername(String username) {
        return rfidToUser.entrySet().stream()
                .filter(e -> e.getValue().equals(username))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
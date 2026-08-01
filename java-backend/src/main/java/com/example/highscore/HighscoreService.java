package com.example.highscore;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

public class HighscoreService {

    private static HighscoreService instance;

    private final Map<Integer, List<JsonObject>> highscores = new HashMap<>();

    private HighscoreService(Vertx vertx) {
        highscores.put(5, new ArrayList<>());
        highscores.put(10, new ArrayList<>());
        highscores.put(20, new ArrayList<>());
    }

    public static synchronized HighscoreService getInstance(Vertx vertx) {
        if (instance == null) {
            instance = new HighscoreService(vertx);
        }
        return instance;
    }

    public void addScore(int mode, String username, double score) {
        if (!highscores.containsKey(mode)) {
            return;
        }

        // Punkte auf 2 Dezimalstellen runden
        double roundedScore = Math.round(score * 100.0) / 100.0;

        // Aktuelles Datum und Uhrzeit speichern
        String createdAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        JsonObject entry = new JsonObject()
                .put("username", username)
                .put("player", username)
                .put("score", roundedScore)
                .put("points", roundedScore)
                .put("created_at", createdAt);

        highscores.get(mode).add(entry);

        // sort descending
        highscores.get(mode).sort((a, b) ->
                Double.compare(b.getDouble("score"), a.getDouble("score"))
        );

        // keep top 20
        if (highscores.get(mode).size() > 20) {
            highscores.get(mode).remove(highscores.get(mode).size() - 1);
        }
    }

    public JsonObject getHighscores(int mode) {
        JsonArray array = new JsonArray();

        if (highscores.containsKey(mode)) {
            for (JsonObject obj : highscores.get(mode)) {
                array.add(obj);
            }
        }

        return new JsonObject().put("entries", array);
    }
}
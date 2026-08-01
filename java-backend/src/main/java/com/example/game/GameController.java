package com.example.game;

import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

public class GameController implements HttpController {

    private final GameService gameService;

    public GameController(Vertx vertx) {
        this.gameService = new GameService(vertx);
    }

    @Override
    public void registerRoutes(Router router) {
        router.post("/api/game/start").handler(this::startGame);
        router.get("/api/game/state").handler(this::getGameState);
        router.get("/api/game/question").handler(this::getQuestion);
        router.post("/api/game/answer").handler(this::submitAnswer);
        router.get("/api/game/result").handler(this::getResult);
        router.post("/api/game/config").handler(this::configureGame);
        router.get("/api/game/scores").handler(this::getScores);
    }

    private String extractToken(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring("Bearer ".length());
    }

    private void startGame(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) { ctx.response().setStatusCode(401).end(); return; }

        JsonObject body = ctx.body().asJsonObject();
        Integer mode = body.getInteger("mode");

        if (mode == null) { ctx.response().setStatusCode(400).end("Mode is required"); return; }

        boolean success = gameService.startGame(mode);

        if (success) {
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("state", gameService.getCurrentState()).encode());
        } else {
            ctx.response().setStatusCode(400).end("Game start failed");
        }
    }

    private void getGameState(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) { ctx.response().setStatusCode(401).end(); return; }

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("state", gameService.getCurrentState()).encode());
    }

    private void getQuestion(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) { ctx.response().setStatusCode(401).end(); return; }

        if (!gameService.getCurrentState().equals("QUESTION")) {
            ctx.response().setStatusCode(400).end("Not in question state");
            return;
        }

        JsonObject question = gameService.getQuestion();
        if (question == null) { ctx.response().setStatusCode(404).end(); return; }

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(question.encode());
    }

    private void submitAnswer(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) { ctx.response().setStatusCode(401).end(); return; }

        JsonObject body = ctx.body().asJsonObject();
        String answer = body.getString("answer");

        if (answer == null) { ctx.response().setStatusCode(400).end("Answer is required"); return; }

        boolean success = gameService.submitAnswer(answer);

        if (success) {
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("state", gameService.getCurrentState()).encode());
        } else {
            ctx.response().setStatusCode(400).end("Answer rejected");
        }
    }

    private void getResult(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) { ctx.response().setStatusCode(401).end(); return; }

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(gameService.getResult().encode());
    }

    private void getScores(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) { ctx.response().setStatusCode(401).end(); return; }

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(gameService.getScores().encode());
    }

    private void configureGame(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) { ctx.response().setStatusCode(401).end(); return; }

        JsonObject body = ctx.body().asJsonObject();

        // roundLength oder mode - beide akzeptieren
        Integer mode = body.getInteger("mode") != null
                ? body.getInteger("mode")
                : body.getInteger("roundLength");

        if (mode == null) { ctx.response().setStatusCode(400).end("Mode is required"); return; }

        // Kategorien lesen
        List<String> categories = new ArrayList<>();
        JsonArray categoriesJson = body.getJsonArray("categories");
        if (categoriesJson != null) {
            for (int i = 0; i < categoriesJson.size(); i++) {
                categories.add(categoriesJson.getString(i));
            }
        }
        if (categories.isEmpty()) {
            categories.add("Programmierung");
            categories.add("Datenbanken");
            categories.add("Web-Technologien");
            categories.add("Netzwerke");
            categories.add("Betriebssysteme");
        }

        // Schwierigkeiten lesen
        List<String> difficulties = new ArrayList<>();
        JsonArray difficultiesJson = body.getJsonArray("difficulties");
        if (difficultiesJson != null) {
            for (int i = 0; i < difficultiesJson.size(); i++) {
                difficulties.add(difficultiesJson.getString(i));
            }
        }
        if (difficulties.isEmpty()) {
            difficulties.add("Easy");
            difficulties.add("Medium");
            difficulties.add("Hard");
        }

        // Async configureGame aufrufen - laedt Fragen aus DB
        gameService.configureGame(mode, categories, difficulties)
                .onSuccess(success -> {
                    if (success) {
                        ctx.response()
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject()
                                        .put("mode", mode)
                                        .put("configured", true)
                                        .encode());
                    } else {
                        ctx.response().setStatusCode(400).end("Game config failed or too few questions");
                    }
                })
                .onFailure(err -> {
                    ctx.response().setStatusCode(500).end("Server error: " + err.getMessage());
                });
    }
}
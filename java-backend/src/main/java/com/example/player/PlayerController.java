package com.example.player;

import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class PlayerController implements HttpController {

    private final Vertx vertx;
    private final PlayerService service;

    public PlayerController(Vertx vertx) {
        this.vertx = vertx;
        this.service = new PlayerService(vertx);
    }

    @Override
    public void registerRoutes(Router router) {
        router.post("/api/auth/register").handler(this::register);
        router.post("/api/auth/login").handler(this::login);
        router.post("/api/auth/logout").handler(this::logout);
        router.get("/api/lobby/status").handler(this::lobbyStatus);
        router.get("/api/controllers/available").handler(this::availableControllers);
        router.post("/api/players/bind").handler(this::bindController);
        router.post("/api/players/ready").handler(this::setReady);
    }

    private void register(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String username = body.getString("username");
        String password = body.getString("password");

        boolean success = service.register(username, password);

        if (success) {
            ctx.response().end("Registered");
        } else {
            ctx.response().setStatusCode(400).end("Username already exists");
        }
    }

    private void login(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String username = body.getString("username");
        String password = body.getString("password");

        String token = service.login(username, password);

        if (token != null) {
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("token", token).encode());
        } else {
            ctx.response().setStatusCode(401).end();
        }
    }

    private void logout(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) {
            ctx.response().setStatusCode(401).end("No token");
            return;
        }
        service.logout(token);
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"message\":\"Logged out\"}");
    }

    private String extractToken(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring("Bearer ".length());
    }

    private void lobbyStatus(RoutingContext ctx) {
        String token = extractToken(ctx);
        String username = service.getUsernameByToken(token);

        if (username == null) {
            ctx.response().setStatusCode(401).end();
            return;
        }
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(service.getLobbyStatusJson().encode());
    }

    private void availableControllers(RoutingContext ctx) {
        String token = extractToken(ctx);
        String username = service.getUsernameByToken(token);

        if (username == null) {
            ctx.response().setStatusCode(401).end();
            return;
        }
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(service.getAvailableControllersJson().encode());
    }

    private void bindController(RoutingContext ctx) {
        String token = extractToken(ctx);
        String username = service.getUsernameByToken(token);

        if (username == null) {
            ctx.response().setStatusCode(401).end();
            return;
        }

        JsonObject body = ctx.body().asJsonObject();
        String controllerId = body.getString("controllerId");

        boolean success = service.bindController(username, controllerId);

        if (success) {
            vertx.eventBus().publish("mqtt.controller.assign",
                    new JsonObject()
                            .put("controllerId", controllerId)
                            .put("playerName", username)
            );
            ctx.response().end("Controller bound");
        } else {
            ctx.response().setStatusCode(400).end("Binding failed");
        }
    }

    private void setReady(RoutingContext ctx) {
        String token = extractToken(ctx);
        String username = service.getUsernameByToken(token);

        if (username == null) {
            ctx.response().setStatusCode(401).end();
            return;
        }

        JsonObject body = ctx.body().asJsonObject();
        Boolean ready = body.getBoolean("ready");

        boolean success = service.setReady(username, ready != null && ready);

        if (success) {
            ctx.response().end("Ready state updated");
        } else {
            ctx.response().setStatusCode(400).end("Ready update failed");
        }
    }
}
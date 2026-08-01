package com.example.highscore;

import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class HighscoreController implements HttpController {

    private final HighscoreService highscoreService;

    public HighscoreController(Vertx vertx) {
        this.highscoreService = HighscoreService.getInstance(vertx);
    }

    @Override
    public void registerRoutes(Router router) {
        router.get("/api/highscores").handler(this::getHighscores);
    }

    private void getHighscores(RoutingContext ctx) {
        String modeParam = ctx.request().getParam("mode");

        if (modeParam == null) {
            ctx.response().setStatusCode(400).end("Mode is required");
            return;
        }

        int mode;
        try {
            mode = Integer.parseInt(modeParam);
        } catch (Exception e) {
            ctx.response().setStatusCode(400).end("Invalid mode");
            return;
        }

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(highscoreService.getHighscores(mode).encode());
    }
}
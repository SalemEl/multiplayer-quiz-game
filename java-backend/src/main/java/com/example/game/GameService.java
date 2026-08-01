package com.example.game;

import com.example.database.DatabaseClient;
import com.example.highscore.HighscoreService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private final GameStateManager gameStateManager;
    private final Vertx vertx;
    private final JDBCPool jdbcPool;
    private final HighscoreService highscoreService;

    // Aktuelle Frageliste fuer das laufende Spiel
    private final List<JsonObject> questions = new ArrayList<>();

    // Punkte pro Spieler im aktuellen Spiel
    private final java.util.Map<String, Double> totalScoresByPlayer = new java.util.HashMap<>();
    private boolean finalScoresSaved = false;

    // Speichert welche Controller bei der aktuellen Frage schon geantwortet haben
    private final Set<String> answeredControllers = new HashSet<>();

    public GameService(Vertx vertx) {
        this.vertx = vertx;
        this.gameStateManager = GameStateManager.getInstance(vertx);
        this.highscoreService = HighscoreService.getInstance(vertx);
        this.jdbcPool = DatabaseClient.getInstance();

        // Wenn alle Spieler ready sind, Spiel automatisch starten
        vertx.eventBus().consumer("game.all.ready", msg -> {
            if (gameStateManager.getCurrentState().equals("LOBBY")
                    && gameStateManager.isConfigured()) {
                startGame(gameStateManager.getGameMode());
            }
        });

        // Antworten vom Controller verarbeiten
        vertx.eventBus().consumer("mqtt.player.answer", msg -> {
            JsonObject body = (JsonObject) msg.body();
            handleControllerAnswer(body);
        });
    }

    // Laedt Fragen aus der Datenbank gefiltert nach Kategorien und Schwierigkeiten
    private Future<List<JsonObject>> loadQuestionsFromDb(List<String> categories, List<String> difficulties, int limit) {

        String categoryPlaceholders = String.join(",", Collections.nCopies(categories.size(), "?"));
        String difficultyPlaceholders = String.join(",", Collections.nCopies(difficulties.size(), "?"));

        // Separator ## wird benutzt weil | Escaping-Probleme in Java Strings hat
        String sql = "SELECT q.id, q.question_text, q.correct_option, q.difficulty," +
                " GROUP_CONCAT(CONCAT(qo.option_letter, qo.option_text) ORDER BY qo.option_letter SEPARATOR '##') as options" +
                " FROM questions q" +
                " JOIN categories c ON q.category_id = c.id" +
                " JOIN question_options qo ON qo.question_id = q.id" +
                " WHERE c.name IN (" + categoryPlaceholders + ")" +
                " AND q.difficulty IN (" + difficultyPlaceholders + ")" +
                " AND q.is_active = 1" +
                " GROUP BY q.id, q.question_text, q.correct_option, q.difficulty" +
                " ORDER BY RAND()" +
                " LIMIT ?";

        Tuple tuple = Tuple.tuple();
        for (String cat : categories) {
            tuple.addString(cat);
        }
        for (String diff : difficulties) {
            tuple.addString(diff.toUpperCase());
        }
        tuple.addInteger(limit);

        return jdbcPool.preparedQuery(sql).execute(tuple).map(rows -> {
            List<JsonObject> result = new ArrayList<>();

            for (Row row : rows) {
                // Format: "AText1##BText2##CText3##DText4"
                String optionsRaw = row.getString("options");
                String[] parts = optionsRaw != null ? optionsRaw.split("##") : new String[0];

                String answerA = "", answerB = "", answerC = "", answerD = "";
                for (String part : parts) {
                    if (part.length() < 2) continue;
                    String letter = part.substring(0, 1);
                    String text = part.substring(1);
                    switch (letter) {
                        case "A" -> answerA = text;
                        case "B" -> answerB = text;
                        case "C" -> answerC = text;
                        case "D" -> answerD = text;
                    }
                }

                JsonArray answersArray = new JsonArray();
                answersArray.add(answerA);
                answersArray.add(answerB);
                answersArray.add(answerC);
                answersArray.add(answerD);

                String correctOption = row.getString("correct_option");
                String correctAnswer = switch (correctOption) {
                    case "A" -> answerA;
                    case "B" -> answerB;
                    case "C" -> answerC;
                    case "D" -> answerD;
                    default -> answerA;
                };

                String difficulty = row.getString("difficulty");
                String difficultyNorm = switch (difficulty.toUpperCase()) {
                    case "EASY" -> "leicht";
                    case "MEDIUM" -> "mittel";
                    case "HARD" -> "schwer";
                    default -> "leicht";
                };

                result.add(new JsonObject()
                        .put("question", row.getString("question_text"))
                        .put("answers", answersArray)
                        .put("correctAnswer", correctAnswer)
                        .put("difficulty", difficultyNorm));
            }

            logger.info("Loaded {} questions from DB", result.size());
            return result;
        });
    }

    // Spiel konfigurieren - laedt Fragen aus DB
    public Future<Boolean> configureGame(int mode, List<String> categories, List<String> difficulties) {
        if (!gameStateManager.getCurrentState().equals("LOBBY")) {
            return Future.succeededFuture(false);
        }

        if (mode != 5 && mode != 10 && mode != 20) {
            return Future.succeededFuture(false);
        }

        return loadQuestionsFromDb(categories, difficulties, mode).map(loadedQuestions -> {
            if (loadedQuestions.size() < mode) {
                logger.warn("Not enough questions: needed {}, got {}", mode, loadedQuestions.size());
                return false;
            }

            questions.clear();
            questions.addAll(loadedQuestions);

            gameStateManager.setGameMode(mode);
            gameStateManager.setTotalQuestions(mode);
            gameStateManager.setCurrentQuestionIndex(0);
            gameStateManager.setConfigured(true);

            logger.info("Game configured: mode={}, questions={}", mode, questions.size());
            return true;
        });
    }

    public boolean startGame(int mode) {
        if (!gameStateManager.getCurrentState().equals("LOBBY")) return false;
        if (!gameStateManager.isConfigured()) return false;

        totalScoresByPlayer.clear();
        finalScoresSaved = false;
        gameStateManager.setGameMode(mode);
        gameStateManager.setCurrentQuestionIndex(0);
        gameStateManager.setCurrentState("COUNTDOWN");

        vertx.eventBus().publish("mqtt.game.state", new JsonObject().put("state", "COUNTDOWN"));
        vertx.setTimer(3000, id -> startQuestion());
        return true;
    }

    private void startQuestion() {
        if (gameStateManager.getCurrentQuestionIndex() >= gameStateManager.getTotalQuestions()) {
            endGame();
            return;
        }

        JsonObject question = getCurrentQuestionObject();
        if (question == null) {
            endGame();
            return;
        }

        gameStateManager.setCurrentState("QUESTION");
        gameStateManager.setQuestionStartTime(System.currentTimeMillis());
        gameStateManager.setAnswered(false);
        gameStateManager.setGivenAnswer(null);
        gameStateManager.setCorrect(false);
        gameStateManager.setScore(0.0);
        gameStateManager.setCorrectAnswer(question.getString("correctAnswer"));

        // Bei jeder neuen Frage die antwortenden Controller zuruecksetzen
        answeredControllers.clear();

        String questionId = "q-" + gameStateManager.getCurrentQuestionIndex();

        vertx.eventBus().publish("mqtt.game.question", new JsonObject()
                .put("questionId", questionId)
                .put("questionText", question.getString("question"))
                .put("answers", question.getJsonArray("answers"))
                .put("timeLimit", 30));

        vertx.eventBus().publish("mqtt.game.state", new JsonObject().put("state", "QUESTION"));

        vertx.setTimer(30000, timeoutId -> {
            if (gameStateManager.getCurrentState().equals("QUESTION")) {
                gameStateManager.setCurrentState("EVALUATION");
                vertx.eventBus().publish("mqtt.game.state", new JsonObject().put("state", "EVALUATION"));

                vertx.setTimer(3000, nextId -> {
                    gameStateManager.setCurrentQuestionIndex(
                            gameStateManager.getCurrentQuestionIndex() + 1);
                    startQuestion();
                });
            }
        });
    }

    private void endGame() {
        saveFinalScores();
        gameStateManager.setCurrentState("END");
        vertx.eventBus().publish("mqtt.game.state", new JsonObject().put("state", "END"));

        // Nach 5 Sekunden automatisch zurueck zur Lobby
        vertx.setTimer(5000, resetId -> {
            gameStateManager.setCurrentState("LOBBY");
            gameStateManager.setConfigured(false);
            gameStateManager.setCurrentQuestionIndex(0);
            vertx.eventBus().publish("mqtt.game.state", new JsonObject().put("state", "LOBBY"));
            logger.info("Game reset to LOBBY.");
        });
    }

    private int getBasePoints(String difficulty) {
        return switch (difficulty) {
            case "leicht" -> 1;
            case "mittel" -> 2;
            case "schwer" -> 3;
            default -> 1;
        };
    }

    private double getTimeFactor(long answerTimeMillis) {
        double seconds = answerTimeMillis / 1000.0;
        if (seconds <= 5) return 1.0;
        if (seconds <= 10) return 0.9;
        if (seconds <= 15) return 0.8;
        if (seconds <= 20) return 0.7;
        if (seconds <= 25) return 0.6;
        if (seconds < 30) return 0.5;
        return 0.0;
    }

    private JsonObject getCurrentQuestionObject() {
        int index = gameStateManager.getCurrentQuestionIndex();
        if (index < 0 || index >= questions.size()) return null;
        return questions.get(index);
    }

    public JsonObject getQuestion() {
        JsonObject question = getCurrentQuestionObject();
        if (question == null) return null;

        return new JsonObject()
                .put("questionId", "q-" + gameStateManager.getCurrentQuestionIndex())
                .put("question", question.getString("question"))
                .put("questionText", question.getString("question"))
                .put("answers", question.getJsonArray("answers"))
                .put("difficulty", question.getString("difficulty"));
    }

    public boolean submitAnswer(String answer) {
        if (!gameStateManager.getCurrentState().equals("QUESTION")) return false;

        JsonObject question = getCurrentQuestionObject();
        if (question == null) return false;

        JsonArray answers = question.getJsonArray("answers");
        String correctAnswer = question.getString("correctAnswer");

        String realAnswer = switch (answer.trim().toUpperCase()) {
            case "A" -> answers.getString(0);
            case "B" -> answers.getString(1);
            case "C" -> answers.getString(2);
            case "D" -> answers.getString(3);
            default -> answer;
        };

        boolean correct = correctAnswer.equals(realAnswer);
        long answerTimeMillis = System.currentTimeMillis() - gameStateManager.getQuestionStartTime();
        double score = correct ? getBasePoints(question.getString("difficulty")) * getTimeFactor(answerTimeMillis) : 0.0;

        gameStateManager.setGivenAnswer(realAnswer);
        gameStateManager.setCorrectAnswer(correctAnswer);
        gameStateManager.setCorrect(correct);
        gameStateManager.setScore(score);
        gameStateManager.setAnswered(true);
        return true;
    }

    private void handleControllerAnswer(JsonObject body) {
        if (body == null) return;

        String controllerId = body.getString("controllerId");
        String playerName = body.getString("playerName");
        String answer = body.getString("answer");

        if (controllerId == null || answer == null) return;

        // Pruefen ob DIESER Controller schon geantwortet hat
        if (answeredControllers.contains(controllerId)) return;

        if (!gameStateManager.getCurrentState().equals("QUESTION")) return;

        JsonObject question = getCurrentQuestionObject();
        if (question == null) return;

        // Diesen Controller als geantwortet markieren
        answeredControllers.add(controllerId);

        JsonArray answers = question.getJsonArray("answers");
        String correctAnswer = question.getString("correctAnswer");
        String difficulty = question.getString("difficulty");

        String realAnswer = switch (answer.trim().toUpperCase()) {
            case "A" -> answers.getString(0);
            case "B" -> answers.getString(1);
            case "C" -> answers.getString(2);
            case "D" -> answers.getString(3);
            default -> answer;
        };

        boolean correct = correctAnswer.equals(realAnswer);
        long answerTimeMillis = System.currentTimeMillis() - gameStateManager.getQuestionStartTime();
        double points = correct ? getBasePoints(difficulty) * getTimeFactor(answerTimeMillis) : 0.0;
        double roundedPoints = Math.round(points * 100.0) / 100.0;

        if (playerName != null && !playerName.isBlank()) {
            totalScoresByPlayer.merge(playerName, roundedPoints, Double::sum);
        }

        vertx.eventBus().publish("mqtt.game.result", new JsonObject()
                .put("controllerId", controllerId)
                .put("playerName", playerName)
                .put("correct", correct)
                .put("givenAnswer", realAnswer)
                .put("correctAnswer", correctAnswer)
                .put("points", roundedPoints)
                .put("message", correct ? "Richtig!" : "Falsch!"));
    }

    // Gibt aktuelle Punkte aller Spieler zurueck (fuer Evaluation-Anzeige)
    public JsonObject getScores() {
        JsonArray array = new JsonArray();
        List<java.util.Map.Entry<String, Double>> entries = new ArrayList<>(totalScoresByPlayer.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        for (java.util.Map.Entry<String, Double> entry : entries) {
            double rounded = Math.round(entry.getValue() * 100.0) / 100.0;
            array.add(new JsonObject()
                    .put("player", entry.getKey())
                    .put("points", rounded));
        }
        return new JsonObject().put("scores", array);
    }

    private void saveFinalScores() {
        if (finalScoresSaved) return;
        finalScoresSaved = true;
        int mode = gameStateManager.getGameMode();
        for (java.util.Map.Entry<String, Double> entry : totalScoresByPlayer.entrySet()) {
            highscoreService.addScore(mode, entry.getKey(), entry.getValue());
        }
    }

    public JsonObject getResult() {
        return new JsonObject()
                .put("correct", gameStateManager.isCorrect())
                .put("givenAnswer", gameStateManager.getGivenAnswer())
                .put("correctAnswer", gameStateManager.getCorrectAnswer())
                .put("score", gameStateManager.getScore())
                .put("points", gameStateManager.getScore())
                .put("questionIndex", gameStateManager.getCurrentQuestionIndex() + 1)
                .put("totalQuestions", gameStateManager.getTotalQuestions());
    }

    public String getCurrentState() {
        return gameStateManager.getCurrentState();
    }

    public int getCurrentMode() {
        return gameStateManager.getGameMode();
    }
}
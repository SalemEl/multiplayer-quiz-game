package com.example.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

public class GameStateManager {

    private static final Logger logger = LoggerFactory.getLogger(GameStateManager.class);

    private final List<String> activePlayers = Collections.synchronizedList(new ArrayList<>());
    private final List<String> activeControllers = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> currentSequence = Collections.synchronizedList(new ArrayList<>());

    private static GameStateManager instance;
    private final EventBus eventBus;
    private final GameModel gameModel;

    private GameStateManager(Vertx vertx) {
        this.eventBus = vertx.eventBus();
        this.gameModel = new GameModel();
    }

    public static synchronized GameStateManager getInstance(Vertx vertx) {
        if (instance == null) {
            instance = new GameStateManager(vertx);
        }
        return instance;
    }

    public String getCurrentState() {
        return gameModel.getState();
    }

    public void setCurrentState(String state) {
        logger.info("Game state changed from {} to {}", gameModel.getState(), state);
        gameModel.setState(state);
    }

    public int getGameMode() {
        return gameModel.getMode();
    }

    public void setGameMode(int mode) {
        gameModel.setMode(mode);
    }

    public String getGivenAnswer() {
        return gameModel.getGivenAnswer();
    }

    public void setGivenAnswer(String answer) {
        gameModel.setGivenAnswer(answer);
    }

    public String getCorrectAnswer() {
        return gameModel.getCorrectAnswer();
    }

    public void setCorrectAnswer(String answer) {
        gameModel.setCorrectAnswer(answer);
    }

    public boolean isCorrect() {
        return gameModel.isCorrect();
    }

    public void setCorrect(boolean correct) {
        gameModel.setCorrect(correct);
    }

    public boolean isConfigured() {
        return gameModel.isConfigured();
    }

    public void setConfigured(boolean configured) {
        gameModel.setConfigured(configured);
    }

    public int getCurrentQuestionIndex() {
        return gameModel.getCurrentQuestionIndex();
    }

    public void setCurrentQuestionIndex(int currentQuestionIndex) {
        gameModel.setCurrentQuestionIndex(currentQuestionIndex);
    }

    public int getTotalQuestions() {
        return gameModel.getTotalQuestions();
    }

    public void setTotalQuestions(int totalQuestions) {
        gameModel.setTotalQuestions(totalQuestions);
    }

    public boolean isAnswered() {
        return gameModel.isAnswered();
    }

    public void setAnswered(boolean answered) {
        gameModel.setAnswered(answered);
    }

    public long getQuestionStartTime() {
        return gameModel.getQuestionStartTime();
    }

    public void setQuestionStartTime(long questionStartTime) {
        gameModel.setQuestionStartTime(questionStartTime);
    }

    public double getScore() {
        return gameModel.getScore();
    }

    public void setScore(double score) {
        gameModel.setScore(score);
    }
}
package com.example.game;

public class GameModel {

    private String state;
    private int mode;

    private String givenAnswer;
    private String correctAnswer;
    private boolean correct;

    private boolean configured;
    private int currentQuestionIndex;
    private int totalQuestions;

    private boolean answered;
    private long questionStartTime;
    private double score;

    public GameModel() {
        this.state = "LOBBY";
        this.mode = 0;

        this.givenAnswer = null;
        this.correctAnswer = null;
        this.correct = false;

        this.configured = false;
        this.currentQuestionIndex = 0;
        this.totalQuestions = 0;

        this.answered = false;
        this.questionStartTime = 0;
        this.score = 0.0;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public String getGivenAnswer() {
        return givenAnswer;
    }

    public void setGivenAnswer(String givenAnswer) {
        this.givenAnswer = givenAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void setCurrentQuestionIndex(int currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public boolean isAnswered() {
        return answered;
    }

    public void setAnswered(boolean answered) {
        this.answered = answered;
    }

    public long getQuestionStartTime() {
        return questionStartTime;
    }

    public void setQuestionStartTime(long questionStartTime) {
        this.questionStartTime = questionStartTime;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
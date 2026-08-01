const controllerIdElement = document.getElementById("controller-id");
const playerNameElement = document.getElementById("player-name");
const connectionStatusElement = document.getElementById("connection-status");
const messageElement = document.getElementById("controller-message");
const questionTextElement = document.getElementById("question-text");
const timerValueElement = document.getElementById("timer-value");
const readyButton = document.getElementById("ready-btn");
const answerButtons = document.querySelectorAll(".answer-btn");
const playerPointsElement = document.getElementById("player-points");
const countdownBox = document.getElementById("countdown-box");
const countdownValue = document.getElementById("countdown-value");

const params = new URLSearchParams(window.location.search);
let controllerId = params.get("id");
let playerName = "Not assigned";
let isReady = false;

let questionTimer = null;
let timeLeft = 30;

// Aktuelle Fragen-ID für die Antwort-Zuordnung
let currentQuestionId = null;

// Gesamtpunkte des Spielers
let totalPoints = 0;

const mqttHost = window.__ENV__.MQTT_BROKER_URL;
const mqttPort = window.__ENV__.MQTT_BROKER_PORT_WS || window.__ENV__.MQTT_BROKER_PORT;
const mqttUsername = window.__ENV__.MQTT_USERNAME;
const mqttPassword = window.__ENV__.MQTT_PASSWORD;
const mqttPrefix = window.__ENV__.MQTT_MESSAGE_PREFIX || "test/";

if (!controllerId) {
    controllerId = "web-" + Math.random().toString(36).substring(2, 8);
    params.set("id", controllerId);
    window.history.replaceState({}, "", `${window.location.pathname}?${params.toString()}`);
}

controllerIdElement.textContent = controllerId;
playerNameElement.textContent = playerName;

function setConnectionStatus(isConnected) {
    if (isConnected) {
        connectionStatusElement.textContent = "Connected";
        connectionStatusElement.classList.remove("status-disconnected");
        connectionStatusElement.classList.add("status-connected");
    } else {
        connectionStatusElement.textContent = "Disconnected";
        connectionStatusElement.classList.remove("status-connected");
        connectionStatusElement.classList.add("status-disconnected");
    }
}

function setReadyButtonState() {
    if (isReady) {
        readyButton.textContent = "Not Ready";
        readyButton.classList.remove("ready-btn");
        readyButton.classList.add("not-ready-btn");
    } else {
        readyButton.textContent = "Ready";
        readyButton.classList.remove("not-ready-btn");
        readyButton.classList.add("ready-btn");
    }
}

function setAssignedPlayer(newPlayerName) {
    playerName = newPlayerName || "Unknown";
    playerNameElement.textContent = playerName;
    messageElement.textContent = "Assigned to player " + playerName;
}

function disableAnswerButtons() {
    for (let i = 0; i < answerButtons.length; i++) {
        answerButtons[i].disabled = true;
    }
}

function enableAnswerButtons() {
    for (let i = 0; i < answerButtons.length; i++) {
        answerButtons[i].disabled = false;
    }
}

function resetAnswerButtons() {
    answerButtons[0].textContent = "A";
    answerButtons[1].textContent = "B";
    answerButtons[2].textContent = "C";
    answerButtons[3].textContent = "D";
}

function setAnswerTexts(answers) {
    if (!Array.isArray(answers) || answers.length < 4) {
        resetAnswerButtons();
        return;
    }

    answerButtons[0].textContent = "A) " + answers[0];
    answerButtons[1].textContent = "B) " + answers[1];
    answerButtons[2].textContent = "C) " + answers[2];
    answerButtons[3].textContent = "D) " + answers[3];
}

function publishJson(topic, data) {
    client.publish(topic, JSON.stringify(data));
}

function stopQuestionTimer() {
    if (questionTimer !== null) {
        clearInterval(questionTimer);
        questionTimer = null;
    }
}

function startQuestionTimer(seconds) {
    stopQuestionTimer();

    timeLeft = seconds;
    timerValueElement.textContent = timeLeft;

    questionTimer = setInterval(function () {
        timeLeft = timeLeft - 1;
        timerValueElement.textContent = timeLeft;

        if (timeLeft <= 0) {
            stopQuestionTimer();
            disableAnswerButtons();
            messageElement.textContent = "Time is over.";
        }
    }, 1000);
}

function sendReadyState() {
    publishJson(mqttPrefix + "controller/" + controllerId + "/ready", {
        controllerId: controllerId,
        playerName: playerName,
        ready: isReady
    });
}

const client = mqtt.connect(`ws://${mqttHost}:${mqttPort}`, {
    username: mqttUsername,
    password: mqttPassword
});

client.on("connect", function () {
    setConnectionStatus(true);
    messageElement.textContent = "Connected to broker.";

    client.subscribe(mqttPrefix + "game/#");
    client.subscribe(mqttPrefix + "controller/" + controllerId + "/#");

    publishJson(mqttPrefix + "controller/" + controllerId + "/register", {
        controllerId: controllerId,
        type: "web-controller"
    });
});

client.on("error", function () {
    setConnectionStatus(false);
    messageElement.textContent = "MQTT connection error.";
});

client.on("close", function () {
    setConnectionStatus(false);
    messageElement.textContent = "Connection closed.";
});

client.on("message", function (topic, message) {
    const text = message.toString();
    console.log("Message received:", topic, text);

    let data = null;

    try {
        data = JSON.parse(text);
    } catch (error) {
        console.log("Message is not JSON");
        return;
    }

    if (topic === mqttPrefix + "controller/" + controllerId + "/assign") {
        setAssignedPlayer(data.playerName);
    }

    if (topic === mqttPrefix + "game/question") {
        // Fragen-ID speichern für die Antwort
        currentQuestionId = data.questionId || null;

        questionTextElement.textContent = data.questionText || "New question";
        setAnswerTexts(data.answers);
        enableAnswerButtons();
        messageElement.textContent = "Question received. Choose an answer.";

        isReady = false;
        setReadyButtonState();

        const seconds = data.timeLimit || 30;
        startQuestionTimer(seconds);
    }

    if (topic === mqttPrefix + "controller/" + controllerId + "/result") {
        messageElement.textContent = data.message || "Result received.";
        disableAnswerButtons();
        stopQuestionTimer();

        // Punkte aktualisieren und anzeigen
        if (data.points !== undefined) {
            totalPoints = totalPoints + data.points;
            playerPointsElement.textContent = totalPoints;
        }
    }

    if (topic === mqttPrefix + "game/state") {
        messageElement.textContent = "Game state: " + (data.state || "unknown");

        if (data.state === "LOBBY") {
            isReady = false;
            setReadyButtonState();
            disableAnswerButtons();
            stopQuestionTimer();
            questionTextElement.textContent = "Waiting for question...";
            resetAnswerButtons();
            timerValueElement.textContent = "30";
            countdownBox.style.display = "none";
            totalPoints = 0;
            playerPointsElement.textContent = "0";
        }

        if (data.state === "COUNTDOWN") {
            countdownBox.style.display = "block";
            countdownValue.textContent = data.countdown || "3";
        }

        if (data.state === "QUESTION") {
            countdownBox.style.display = "none";
        }

        if (data.state === "EVALUATION" || data.state === "END") {
            disableAnswerButtons();
            stopQuestionTimer();
            countdownBox.style.display = "none";
        }
    }

    if (topic === mqttPrefix + "controller/" + controllerId + "/ping") {
        publishJson(mqttPrefix + "controller/" + controllerId + "/pong", {
            controllerId: controllerId
        });
    }
});

readyButton.addEventListener("click", function () {
    if (playerName === "Not assigned") {
        messageElement.textContent = "Controller is not assigned to a player yet.";
        return;
    }

    isReady = !isReady;
    setReadyButtonState();
    sendReadyState();

    if (isReady) {
        messageElement.textContent = playerName + " is ready.";
    } else {
        messageElement.textContent = playerName + " is not ready.";
    }
});

for (let i = 0; i < answerButtons.length; i++) {
    answerButtons[i].addEventListener("click", function () {
        const answer = this.dataset.answer;

        publishJson(mqttPrefix + "controller/" + controllerId + "/answer", {
            controllerId: controllerId,
            playerName: playerName,
            answer: answer,
            questionId: currentQuestionId
        });

        messageElement.textContent = "Answer sent: " + answer;
        disableAnswerButtons();
        stopQuestionTimer();
    });
}

setReadyButtonState();
disableAnswerButtons();
setConnectionStatus(false);
resetAnswerButtons();
timerValueElement.textContent = "30";
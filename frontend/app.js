const mockPlayers = [
    { name: "Alice", status: "ready" },
    { name: "Bob", status: "not-ready" },
    { name: "Charlie", status: "disconnected" }
];

const mockHighscores = {
    5: [
        { rank: 1, player: "Alice", points: 150, created_at: "2024-01-10 14:32" },
        { rank: 2, player: "Bob", points: 140, created_at: "2024-01-09 10:15" },
        { rank: 3, player: "Charlie", points: 130, created_at: "2024-01-08 18:00" }
    ],
    10: [
        { rank: 1, player: "Alice", points: 280, created_at: "2024-01-10 14:32" },
        { rank: 2, player: "David", points: 250, created_at: "2024-01-09 10:15" },
        { rank: 3, player: "Eve", points: 220, created_at: "2024-01-08 18:00" }
    ],
    20: [
        { rank: 1, player: "Bob", points: 500, created_at: "2024-01-10 14:32" },
        { rank: 2, player: "Charlie", points: 470, created_at: "2024-01-09 10:15" },
        { rank: 3, player: "Alice", points: 430, created_at: "2024-01-08 18:00" }
    ]
};

let players = [...mockPlayers];
let highscores = [...mockHighscores[5]];

let selectedMode = 5;

let selectedCategories = [
    "Programmierung",
    "Datenbanken"
];

let selectedDifficulties = [
    "Easy"
];

let currentMainView = "lobby";
let currentSidePanel = "login";
let selectedHighscoreTab = 5;
let loggedInUser = null;

let authToken = null;

const categories = [
    "Programmierung",
    "Datenbanken",
    "Web-Technologien",
    "Netzwerke",
    "Betriebssysteme"
];

const difficulties = [
    "Easy",
    "Medium",
    "Hard"
];

function getStatusText(status) {
    if (status === "ready") return "Ready";
    if (status === "not-ready") return "Not Ready";
    if (status === "disconnected") return "Disconnected";
    return "Unknown";
}

function getStatusClass(status) {
    if (status === "ready") return "badge badge-ready";
    if (status === "not-ready") return "badge badge-not-ready";
    if (status === "disconnected") return "badge badge-disconnected";
    return "badge";
}

function renderSidePanel() {
    const header = document.querySelector(".side-panel .panel-header h2");
    const body = document.querySelector(".side-panel .panel-body");

    if (currentSidePanel === "login") {
        header.textContent = "🎮 Quiz Game Login";

        body.innerHTML = `
      <div class="form-group">
        <label for="login-username">Username</label>
        <input type="text" id="login-username" placeholder="Enter username">
      </div>

      <div class="form-group">
        <label for="login-password">Password</label>
        <input type="password" id="login-password" placeholder="Enter password">
      </div>

      <div class="button-row">
        <button class="btn btn-green" id="login-btn">Login</button>
        <button class="btn btn-gray" id="show-register-btn">Register</button>
      </div>

      <p id="login-message"></p>
      <button class="link-btn" id="go-register-link">No account yet? Register here</button>
    `;

        attachLoginEvents();
    }

    if (currentSidePanel === "register") {
        header.textContent = "📝 Register";

        body.innerHTML = `
      <div class="form-group">
        <label for="register-username">Username</label>
        <input type="text" id="register-username" placeholder="Choose username">
      </div>

      <div class="form-group">
        <label for="register-password">Password</label>
        <input type="password" id="register-password" placeholder="Enter password">
      </div>

      <div class="form-group">
        <label for="register-password-repeat">Repeat Password</label>
        <input type="password" id="register-password-repeat" placeholder="Repeat password">
      </div>

      <div class="button-row">
        <button class="btn btn-green" id="register-btn">Create Account</button>
        <button class="btn btn-gray" id="back-login-btn">Back</button>
      </div>

      <p id="register-message"></p>
      <button class="link-btn" id="go-login-link">Back to login</button>
    `;

        attachRegisterEvents();
    }

    if (currentSidePanel === "user") {
        header.textContent = "👤 User";

        body.innerHTML = `
      <p><strong>Eingeloggt als:</strong></p>
      <p>${loggedInUser || "Unbekannt"}</p>

      <div class="form-group">
        <label for="controller-select">Controller auswählen</label>
        <select id="controller-select" class="controller-select">
          <option value="">-- Wird geladen... --</option>
        </select>
      </div>

      <div class="button-row">
        <button class="btn btn-green" id="bind-btn">Controller binden</button>
        <button class="btn btn-gray" id="logout-btn">Logout</button>
      </div>

      <p id="user-message"></p>
    `;

        attachUserEvents();
        loadAvailableControllers();
    }
}

function attachLoginEvents() {
    const loginButton = document.getElementById("login-btn");
    const showRegisterButton = document.getElementById("show-register-btn");
    const registerLink = document.getElementById("go-register-link");
    const loginMessage = document.getElementById("login-message");

    showRegisterButton.addEventListener("click", function () {
        currentSidePanel = "register";
        renderSidePanel();
    });

    registerLink.addEventListener("click", function () {
        currentSidePanel = "register";
        renderSidePanel();
    });

    loginButton.addEventListener("click", async function () {
        const username = document.getElementById("login-username").value.trim();
        const password = document.getElementById("login-password").value.trim();

        if (username === "" || password === "") {
            loginMessage.textContent = "Please enter username and password.";
            return;
        }

        try {
            const response = await fetch("http://localhost:8080/api/auth/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    username: username,
                    password: password
                })
            });

            if (!response.ok) {
                throw new Error("Login failed");
            }

            const loginData = await response.json();
            authToken = loginData.token || null;
            console.log("Login erfolgreich. Token gespeichert:", authToken);

            loggedInUser = username;
            currentSidePanel = "user";
            renderSidePanel();
        } catch (error) {
            loginMessage.textContent = "Login fehlgeschlagen.";
            console.log("Login request:", { username, password });
        }
    });
}

function attachRegisterEvents() {
    const registerButton = document.getElementById("register-btn");
    const backLoginButton = document.getElementById("back-login-btn");
    const loginLink = document.getElementById("go-login-link");
    const registerMessage = document.getElementById("register-message");

    backLoginButton.addEventListener("click", function () {
        currentSidePanel = "login";
        renderSidePanel();
    });

    loginLink.addEventListener("click", function () {
        currentSidePanel = "login";
        renderSidePanel();
    });

    registerButton.addEventListener("click", async function () {
        const username = document.getElementById("register-username").value.trim();
        const password = document.getElementById("register-password").value.trim();
        const repeatPassword = document.getElementById("register-password-repeat").value.trim();

        if (username === "" || password === "" || repeatPassword === "") {
            registerMessage.textContent = "Please fill all fields.";
            return;
        }

        if (password !== repeatPassword) {
            registerMessage.textContent = "Passwords do not match.";
            return;
        }

        try {
            const response = await fetch("http://localhost:8080/api/auth/register", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    username: username,
                    password: password
                })
            });

            if (!response.ok) {
                throw new Error("Register failed");
            }

            registerMessage.textContent = "Account created successfully.";

            setTimeout(function () {
                currentSidePanel = "login";
                renderSidePanel();
            }, 1000);
        } catch (error) {
            registerMessage.textContent = "Register failed. Backend may not be ready yet.";
            console.log("Register request:", { username, password });
        }
    });
}

function attachUserEvents() {
    const logoutButton = document.getElementById("logout-btn");
    const bindButton = document.getElementById("bind-btn");

    logoutButton.addEventListener("click", async function () {
        try {
            await fetch("http://localhost:8080/api/auth/logout", {
                method: "POST",
                headers: {
                    "Authorization": "Bearer " + authToken
                }
            });
        } catch (error) {
            console.log("Logout request failed.");
        }

        loggedInUser = null;
        authToken = null;
        console.log("Logout: Benutzer wurde lokal und im Backend ausgeloggt.");
        currentSidePanel = "login";
        renderSidePanel();
    });

    bindButton.addEventListener("click", async function () {
        const select = document.getElementById("controller-select");
        const userMessage = document.getElementById("user-message");
        const selectedId = select.value;

        if (!selectedId) {
            userMessage.textContent = "Bitte zuerst einen Controller auswählen.";
            return;
        }

        try {
            const response = await fetch("http://localhost:8080/api/players/bind", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": "Bearer " + authToken
                },
                body: JSON.stringify({
                    controllerId: selectedId
                })
            });

            if (!response.ok) {
                throw new Error("Binden fehlgeschlagen");
            }

            userMessage.textContent = "✅ Controller erfolgreich gebunden!";
            console.log("Controller gebunden:", selectedId);
        } catch (error) {
            userMessage.textContent = "Backend noch nicht bereit.";
            console.log("Bind request:", { controllerId: selectedId });
        }
    });
}

async function loadAvailableControllers() {
    const select = document.getElementById("controller-select");
    const userMessage = document.getElementById("user-message");

    if (!select) return;

    try {
        const response = await fetch("http://localhost:8080/api/controllers/available", {
            headers: {
                "Authorization": "Bearer " + authToken
            }
        });

        if (!response.ok) {
            throw new Error("Laden fehlgeschlagen");
        }

        const data = await response.json();
        const controllers = data.controllers || [];

        select.innerHTML = "";

        if (controllers.length === 0) {
            select.innerHTML = "<option value=''>Keine Controller verfügbar</option>";
            return;
        }

        const placeholder = document.createElement("option");
        placeholder.value = "";
        placeholder.textContent = "-- Controller auswählen --";
        select.appendChild(placeholder);

        controllers.forEach(function (controller) {
            const option = document.createElement("option");
            option.value = controller.controllerId;
            option.textContent = controller.controllerId + " (" + controller.type + ")";
            select.appendChild(option);
        });

        console.log("Controller geladen:", controllers.length);

    } catch (error) {
        select.innerHTML = "<option value=''>Backend nicht bereit</option>";
        console.log("Controller konnten nicht geladen werden.");
    }
}

function renderPlayers() {
    const playerList = document.getElementById("player-list");
    if (!playerList) return;

    playerList.innerHTML = "";

    players.forEach(player => {
        const li = document.createElement("li");
        li.className = "player-item";

        const name = document.createElement("span");
        name.className = "player-name";
        name.textContent = player.name;

        const status = document.createElement("span");
        status.className = getStatusClass(player.status);
        status.textContent = getStatusText(player.status);

        const controller = document.createElement("span");
        controller.className = "player-controller";
        controller.textContent = player.controllerId ? "🎮 " + player.controllerId : "kein Controller";

        li.appendChild(name);
        li.appendChild(controller);
        li.appendChild(status);
        playerList.appendChild(li);
    });
}

function renderGameConfig() {
    const gameConfigCard = document.getElementById("game-config-card");

    let categoryHtml = "";
    let difficultyHtml = "";

    for (let i = 0; i < categories.length; i++) {
        const category = categories[i];
        const isChecked = selectedCategories.includes(category);

        categoryHtml += `
      <label>
        <input type="checkbox" class="category-checkbox" value="${category}" ${isChecked ? "checked" : ""}>
        ${category}
      </label>
    `;
    }

    for (let i = 0; i < difficulties.length; i++) {
        const difficulty = difficulties[i];
        const isChecked = selectedDifficulties.includes(difficulty);

        difficultyHtml += `
      <label>
        <input type="checkbox" class="difficulty-checkbox" value="${difficulty}" ${isChecked ? "checked" : ""}>
        ${difficulty}
      </label>
    `;
    }

    gameConfigCard.innerHTML = `
    <h3>Game Configuration</h3>

    <h4>Game Mode</h4>
    <div class="mode-buttons">
      <button class="btn mode-btn ${selectedMode === 5 ? "btn-green" : "btn-light"}" data-mode="5">5 Fragen</button>
      <button class="btn mode-btn ${selectedMode === 10 ? "btn-green" : "btn-light"}" data-mode="10">10 Fragen</button>
      <button class="btn mode-btn ${selectedMode === 20 ? "btn-green" : "btn-light"}" data-mode="20">20 Fragen</button>
    </div>

    <div class="checkbox-group">
      <h4>Categories</h4>
      ${categoryHtml}
    </div>

    <div class="checkbox-group">
      <h4>Difficulties</h4>
      ${difficultyHtml}
    </div>

    <div class="checkbox-group">
      <button class="btn btn-green" id="start-game-btn">Start Game</button>
      <p id="start-game-message"></p>
    </div>
  `;

    attachModeEvents();
    attachCategoryEvents();
    attachDifficultyEvents();
    attachStartGameEvent();
}

function attachModeEvents() {
    document.querySelectorAll(".mode-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            selectedMode = Number(btn.dataset.mode);
            renderGameConfig();
        });
    });
}

function attachCategoryEvents() {
    document.querySelectorAll(".category-checkbox").forEach(box => {
        box.addEventListener("change", () => {
            const value = box.value;

            if (box.checked) {
                if (!selectedCategories.includes(value)) {
                    selectedCategories.push(value);
                }
            } else {
                selectedCategories = selectedCategories.filter(c => c !== value);
            }
        });
    });
}

function attachDifficultyEvents() {
    document.querySelectorAll(".difficulty-checkbox").forEach(box => {
        box.addEventListener("change", () => {
            const value = box.value;

            if (box.checked) {
                if (!selectedDifficulties.includes(value)) {
                    selectedDifficulties.push(value);
                }
            } else {
                selectedDifficulties = selectedDifficulties.filter(d => d !== value);
            }
        });
    });
}

function attachStartGameEvent() {
    const startGameButton = document.getElementById("start-game-btn");
    const startGameMessage = document.getElementById("start-game-message");

    if (!startGameButton) return;

    startGameButton.addEventListener("click", async function () {
        const gameConfig = {
            roundLength: selectedMode,
            mode: selectedMode,
            categories: selectedCategories,
            difficulties: selectedDifficulties
        };

        startGameButton.disabled = true;
        startGameMessage.textContent = "Fragenpool wird geprüft...";

        try {
            const configResponse = await fetch("http://localhost:8080/api/game/config", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": "Bearer " + authToken
                },
                body: JSON.stringify(gameConfig)
            });

            if (!configResponse.ok) {
                const errorText = await configResponse.text();
                startGameMessage.textContent = errorText || "Nicht genug Fragen für diese Auswahl.";
                startGameButton.disabled = false;
                return;
            }

            const startResponse = await fetch("http://localhost:8080/api/game/start", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": "Bearer " + authToken
                },
                body: JSON.stringify({ mode: selectedMode })
            });

            if (!startResponse.ok) {
                throw new Error("Start request failed");
            }

            startGameMessage.textContent = "Spiel wurde gestartet!";
            console.log("Spiel gestartet mit Config:", gameConfig);

        } catch (error) {
            startGameMessage.textContent = "Backend noch nicht bereit.";
            console.log("Game config:", gameConfig);
        } finally {
            startGameButton.disabled = false;
        }
    });
}

function renderLobbyView() {
    const header = document.querySelector(".main-panel .panel-header h2");
    const body = document.querySelector(".main-panel .panel-body");

    header.textContent = "🏛 Lobby";

    body.innerHTML = `
    <div class="content-grid">
      <div class="card" id="game-config-card"></div>

      <div class="card">
        <div class="player-list-header">
          <h3>Active Players</h3>
          <button class="btn btn-light" id="refresh-btn">🔄 Aktualisieren</button>
        </div>
        <ul id="player-list" class="player-list"></ul>
      </div>
    </div>
  `;

    const refreshBtn = document.getElementById("refresh-btn");
    refreshBtn.addEventListener("click", async function () {
        refreshBtn.textContent = "⏳ Lädt...";
        refreshBtn.disabled = true;
        await fetchLobbyStatus();
        refreshBtn.textContent = "🔄 Aktualisieren";
        refreshBtn.disabled = false;
    });

    renderPlayers();
    renderGameConfig();
}

// Aktuelle Frage vom Backend
let currentQuestion = null;

async function fetchCurrentQuestion() {
    try {
        const res = await fetch("http://localhost:8080/api/game/question", {
            headers: { "Authorization": "Bearer " + authToken }
        });
        if (!res.ok) return;
        currentQuestion = await res.json();
        renderQuestionView();
    } catch {
        // Frage konnte nicht geladen werden
    }
}

function renderQuestionView() {
    const header = document.querySelector(".main-panel .panel-header h2");
    const body = document.querySelector(".main-panel .panel-body");

    header.textContent = "🎯 Game";

    const questionText = currentQuestion ? currentQuestion.question : "Frage wird geladen...";
    const answers = currentQuestion ? currentQuestion.answers : ["A", "B", "C", "D"];

    body.innerHTML = `
    <div class="question-box">
      <h3>${questionText}</h3>
    </div>
    <div class="answer-grid">
      <button class="answer-btn">${answers[0] || "A"}</button>
      <button class="answer-btn">${answers[1] || "B"}</button>
      <button class="answer-btn">${answers[2] || "C"}</button>
      <button class="answer-btn">${answers[3] || "D"}</button>
    </div>
  `;
}

// Aktuelle Punkte waehrend des Spiels
let currentScores = [];

async function fetchCurrentScores() {
    try {
        const res = await fetch("http://localhost:8080/api/game/scores", {
            headers: { "Authorization": "Bearer " + authToken }
        });
        if (!res.ok) return;
        const data = await res.json();
        if (Array.isArray(data.scores)) {
            currentScores = data.scores;
        }
    } catch {
        // Scores konnten nicht geladen werden
    }
}

function renderEvaluationView() {
    const header = document.querySelector(".main-panel .panel-header h2");
    const body = document.querySelector(".main-panel .panel-body");

    header.textContent = "📊 Evaluation";

    let playerRows = "";
    if (currentScores.length > 0) {
        currentScores.forEach(function(entry, index) {
            playerRows += "<li>" + (index + 1) + ". " + entry.player + " — " + entry.points + " Punkte</li>";
        });
    } else {
        playerRows = "<li>Noch keine Punkte...</li>";
    }

    body.innerHTML = `
    <div class="result-card">
      <h3>Zwischenergebnis</h3>
      <ul>${playerRows}</ul>
    </div>
  `;
}

function renderHighscoresView() {
    const header = document.querySelector(".main-panel .panel-header h2");
    const body = document.querySelector(".main-panel .panel-body");

    header.textContent = "🏆 Highscores";

    body.innerHTML = `
    <div class="card">
      <h3>Best Scores</h3>

      <div class="tab-buttons">
        <button class="btn highscore-tab ${selectedHighscoreTab === 5 ? "btn-green" : "btn-light"}" data-tab="5">5 Fragen</button>
        <button class="btn highscore-tab ${selectedHighscoreTab === 10 ? "btn-green" : "btn-light"}" data-tab="10">10 Fragen</button>
        <button class="btn highscore-tab ${selectedHighscoreTab === 20 ? "btn-green" : "btn-light"}" data-tab="20">20 Fragen</button>
      </div>

      <table class="table">
        <thead>
          <tr>
            <th>Rank</th>
            <th>Player</th>
            <th>Points</th>
            <th>Datum</th>
          </tr>
        </thead>
        <tbody id="highscore-table-body"></tbody>
      </table>
    </div>
  `;

    renderHighscoreRows();
    attachHighscoreTabEvents();
}

function renderHighscoreRows() {
    const tableBody = document.getElementById("highscore-table-body");
    if (!tableBody) return;

    tableBody.innerHTML = "";

    highscores.forEach(entry => {
        const row = document.createElement("tr");

        let datum = "-";
        if (entry.created_at) {
            const date = new Date(entry.created_at);
            if (!isNaN(date.getTime())) {
                datum = date.toLocaleDateString("de-DE") + " " + date.toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" });
            } else {
                datum = entry.created_at;
            }
        }

        row.innerHTML = `
      <td>${entry.rank}</td>
      <td>${entry.player}</td>
      <td>${entry.points}</td>
      <td>${datum}</td>
    `;

        tableBody.appendChild(row);
    });
}

function attachHighscoreTabEvents() {
    document.querySelectorAll(".highscore-tab").forEach(btn => {
        btn.addEventListener("click", async () => {
            selectedHighscoreTab = Number(btn.dataset.tab);
            await fetchHighscores(selectedHighscoreTab);

            if (currentMainView === "highscores") {
                renderHighscoresView();
            }
        });
    });
}

function attachNavEvents() {
    document.querySelectorAll(".nav-btn").forEach(btn => {
        btn.addEventListener("click", async () => {
            const text = btn.textContent.trim();

            if (text === "Lobby") currentMainView = "lobby";
            if (text === "Game") currentMainView = "game";
            if (text === "Evaluation") currentMainView = "evaluation";

            if (text === "Highscores") {
                currentMainView = "highscores";
                await fetchHighscores(selectedHighscoreTab);
            }

            if (text === "Web Controller") {
                window.open("http://localhost:81/controller.html", "_blank");
                return;
            }

            renderMainView();
        });
    });
}

function renderMainView() {
    if (currentMainView === "lobby") renderLobbyView();
    if (currentMainView === "game") renderQuestionView();
    if (currentMainView === "evaluation") renderEvaluationView();
    if (currentMainView === "highscores") renderHighscoresView();

    attachNavEvents();
}

async function fetchLobbyStatus() {
    try {
        const res = await fetch("http://localhost:8080/api/lobby/status", {
            headers: {
                "Authorization": "Bearer " + authToken
            }
        });

        if (!res.ok) throw new Error();

        const data = await res.json();

        if (Array.isArray(data.players)) {
            players = data.players;

            if (currentMainView === "lobby") {
                renderPlayers();
            }
        }
    } catch {
        players = [...mockPlayers];

        if (currentMainView === "lobby") {
            renderPlayers();
        }
    }
}

async function fetchHighscores(roundLength) {
    try {
        const res = await fetch(`http://localhost:8080/api/highscores?mode=${roundLength}`, {
            headers: {
                "Authorization": "Bearer " + authToken
            }
        });

        if (!res.ok) throw new Error();

        const data = await res.json();

        if (Array.isArray(data.entries)) {
            highscores = data.entries.map(function(entry, index) {
                return {
                    rank: index + 1,
                    player: entry.player || entry.username || "Unknown",
                    points: Math.round((entry.points || entry.score || 0) * 100) / 100,
                    created_at: entry.created_at || null
                };
            });
        } else if (Array.isArray(data.highscores)) {
            highscores = data.highscores;
        } else {
            highscores = [...mockHighscores[roundLength]];
        }
    } catch {
        highscores = [...mockHighscores[roundLength]];
    }
}

let lastGameState = null;

async function fetchGameState() {
    try {
        const res = await fetch("http://localhost:8080/api/game/state", {
            headers: {
                "Authorization": "Bearer " + authToken
            }
        });

        if (!res.ok) throw new Error();

        const data = await res.json();
        const state = data.state;

        if (state === lastGameState) return;
        lastGameState = state;

        console.log("Game-State geändert:", state);

        if (state === "LOBBY") {
            currentMainView = "lobby";
            renderMainView();
        }

        if (state === "COUNTDOWN") {
            currentMainView = "game";
            currentQuestion = null;
            renderMainView();
        }

        if (state === "QUESTION") {
            currentMainView = "game";
            await fetchCurrentQuestion();
        }

        if (state === "EVALUATION") {
            currentMainView = "evaluation";
            await fetchCurrentScores();
            renderMainView();
        }

        if (state === "END") {
            currentMainView = "highscores";
            await fetchHighscores(selectedHighscoreTab);
            renderMainView();
        }

    } catch {
    }
}

renderSidePanel();
renderMainView();
fetchLobbyStatus();
fetchGameState();
setInterval(fetchLobbyStatus, 1000);
setInterval(fetchGameState, 1000);
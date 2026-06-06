const homeView = document.querySelector("#homeView");
const gameView = document.querySelector("#gameView");
const nameInput = document.querySelector("#nameInput");
const roomCodeInput = document.querySelector("#roomCodeInput");
const createRoomButton = document.querySelector("#createRoomButton");
const joinRoomButton = document.querySelector("#joinRoomButton");
const homeError = document.querySelector("#homeError");
const connectionStatus = document.querySelector("#connectionStatus");
const roomBadge = document.querySelector("#roomBadge");
const restartButton = document.querySelector("#restartButton");
const leaveButton = document.querySelector("#leaveButton");
const gameStatus = document.querySelector("#gameStatus");
const canvas = document.querySelector("#gameCanvas");
const ctx = canvas.getContext("2d");

let roomCode = sessionStorage.getItem("roomCode") || "";
let playerId = sessionStorage.getItem("playerId") || "";
let pollTimer = null;
let latestState = null;
let swipeStart = null;

const scoreEls = [
  document.querySelector("#playerOneScore"),
  document.querySelector("#playerTwoScore"),
];

async function postJson(url, payload) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || "Request failed");
  }
  return data;
}

function saveSession(nextRoomCode, nextPlayerId) {
  roomCode = nextRoomCode;
  playerId = nextPlayerId;
  sessionStorage.setItem("roomCode", roomCode);
  sessionStorage.setItem("playerId", playerId);
}

function clearSession() {
  roomCode = "";
  playerId = "";
  latestState = null;
  sessionStorage.removeItem("roomCode");
  sessionStorage.removeItem("playerId");
}

function showHome(errorMessage = "") {
  homeView.classList.remove("hidden");
  gameView.classList.add("hidden");
  connectionStatus.textContent = "Offline";
  homeError.textContent = errorMessage;
  stopPolling();
}

function showGame() {
  homeView.classList.add("hidden");
  gameView.classList.remove("hidden");
  connectionStatus.textContent = "Online";
}

function cleanRoomCode(value) {
  return value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 4);
}

function playerName() {
  return nameInput.value.trim();
}

async function createRoom() {
  homeError.textContent = "";
  const data = await postJson("/api/create", { name: playerName() });
  saveSession(data.roomCode, data.playerId);
  showGame();
  applyState(data.state);
  startPolling();
}

async function joinRoom() {
  homeError.textContent = "";
  const code = cleanRoomCode(roomCodeInput.value);
  roomCodeInput.value = code;
  const data = await postJson("/api/join", {
    roomCode: code,
    name: playerName(),
  });
  saveSession(data.roomCode, data.playerId);
  showGame();
  applyState(data.state);
  startPolling();
}

async function fetchState() {
  if (!roomCode || !playerId) {
    return;
  }

  try {
    const response = await fetch(`/api/state?room=${encodeURIComponent(roomCode)}&player=${encodeURIComponent(playerId)}`, {
      cache: "no-store",
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "Room unavailable");
    }
    applyState(data.state);
  } catch (error) {
    clearSession();
    showHome(error.message);
  }
}

function startPolling() {
  stopPolling();
  fetchState();
  pollTimer = window.setInterval(fetchState, 80);
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

function sendDirection(direction) {
  if (!roomCode || !playerId) {
    return;
  }

  if (navigator.vibrate) {
    navigator.vibrate(8);
  }

  postJson("/api/input", { roomCode, playerId, direction }).catch(() => {});
}

async function restartGame() {
  if (!roomCode || !playerId) {
    return;
  }

  const data = await postJson("/api/restart", { roomCode, playerId });
  applyState(data.state);
}

function leaveGame() {
  clearSession();
  showHome();
}

function applyState(state) {
  latestState = state;
  roomBadge.textContent = state.roomCode;
  renderScores(state);
  renderStatus(state);
  drawBoard(state);
}

function renderScores(state) {
  for (let index = 0; index < scoreEls.length; index += 1) {
    const player = state.players[index];
    const scoreEl = scoreEls[index];
    const dot = scoreEl.querySelector(".score-dot");
    const name = scoreEl.querySelector(".score-name");
    const score = scoreEl.querySelector("strong");

    if (!player) {
      dot.style.background = "#aeb7c2";
      name.textContent = index === 0 ? "Player 1" : "Player 2";
      score.textContent = "0";
      scoreEl.classList.remove("you");
      continue;
    }

    dot.style.background = player.color;
    name.textContent = player.isYou ? `${player.name} • You` : player.name;
    score.textContent = player.score;
    scoreEl.classList.toggle("you", player.isYou);
  }
}

function renderStatus(state) {
  if (state.status === "waiting") {
    gameStatus.textContent = `Room ${state.roomCode}`;
    return;
  }

  if (state.status === "gameover") {
    gameStatus.textContent = state.winner === "Draw" ? "Draw" : `${state.winner} wins`;
    return;
  }

  gameStatus.textContent = `Room ${state.roomCode}`;
}

function resizeCanvasToDisplaySize() {
  const rect = canvas.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  const width = Math.max(1, Math.round(rect.width * dpr));
  const height = Math.max(1, Math.round(rect.height * dpr));

  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }

  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  return { width: rect.width, height: rect.height };
}

function drawBoard(state) {
  const size = resizeCanvasToDisplaySize();
  const cols = state.board.cols;
  const rows = state.board.rows;
  const cell = Math.min(size.width / cols, size.height / rows);
  const boardWidth = cols * cell;
  const boardHeight = rows * cell;
  const offsetX = (size.width - boardWidth) / 2;
  const offsetY = (size.height - boardHeight) / 2;

  ctx.clearRect(0, 0, size.width, size.height);
  ctx.fillStyle = "#11151b";
  ctx.fillRect(0, 0, size.width, size.height);

  ctx.strokeStyle = "#242b35";
  ctx.lineWidth = 1;
  for (let x = 0; x <= cols; x += 1) {
    const px = offsetX + x * cell;
    ctx.beginPath();
    ctx.moveTo(px, offsetY);
    ctx.lineTo(px, offsetY + boardHeight);
    ctx.stroke();
  }
  for (let y = 0; y <= rows; y += 1) {
    const py = offsetY + y * cell;
    ctx.beginPath();
    ctx.moveTo(offsetX, py);
    ctx.lineTo(offsetX + boardWidth, py);
    ctx.stroke();
  }

  drawCell(state.food.x, state.food.y, cell, offsetX, offsetY, "#ef4444", true);

  for (const player of state.players) {
    for (let index = player.body.length - 1; index >= 0; index -= 1) {
      const block = player.body[index];
      if (index === 0) {
        drawCell(block.x, block.y, cell, offsetX, offsetY, "#f4f6f8", true);
        drawCell(block.x, block.y, cell, offsetX, offsetY, player.color, true, 0.28);
      } else {
        drawCell(block.x, block.y, cell, offsetX, offsetY, player.color, true);
      }
    }
  }

  if (state.status === "waiting" || state.status === "gameover") {
    drawOverlay(state, size);
  }
}

function drawCell(x, y, cell, offsetX, offsetY, color, rounded, padRatio = 0.09) {
  const pad = Math.max(2, cell * padRatio);
  const px = offsetX + x * cell + pad;
  const py = offsetY + y * cell + pad;
  const side = Math.max(1, cell - pad * 2);
  ctx.fillStyle = color;

  if (rounded && ctx.roundRect) {
    ctx.beginPath();
    ctx.roundRect(px, py, side, side, Math.min(6, side * 0.28));
    ctx.fill();
    return;
  }

  ctx.fillRect(px, py, side, side);
}

function drawOverlay(state, size) {
  ctx.fillStyle = "rgba(0, 0, 0, 0.58)";
  ctx.fillRect(0, 0, size.width, size.height);

  const title = state.status === "waiting"
    ? "Waiting"
    : state.winner === "Draw"
      ? "Draw"
      : `${state.winner} wins`;

  ctx.fillStyle = "#f4f6f8";
  ctx.font = "700 34px Arial";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(title, size.width / 2, size.height / 2 - 20);

  ctx.fillStyle = "#f5b83b";
  ctx.font = "700 24px Arial";
  ctx.fillText(state.roomCode, size.width / 2, size.height / 2 + 28);
}

function directionFromDelta(dx, dy) {
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 22) {
    return "";
  }

  if (Math.abs(dx) > Math.abs(dy)) {
    return dx > 0 ? "RIGHT" : "LEFT";
  }

  return dy > 0 ? "DOWN" : "UP";
}

createRoomButton.addEventListener("click", () => {
  createRoom().catch((error) => {
    homeError.textContent = error.message;
  });
});

joinRoomButton.addEventListener("click", () => {
  joinRoom().catch((error) => {
    homeError.textContent = error.message;
  });
});

roomCodeInput.addEventListener("input", () => {
  roomCodeInput.value = cleanRoomCode(roomCodeInput.value);
});

for (const button of document.querySelectorAll("[data-direction]")) {
  button.addEventListener("pointerdown", (event) => {
    event.preventDefault();
    sendDirection(button.dataset.direction);
  });
}

restartButton.addEventListener("click", () => {
  restartGame().catch(() => {});
});

leaveButton.addEventListener("click", leaveGame);

roomBadge.addEventListener("click", async () => {
  if (navigator.clipboard && roomCode) {
    await navigator.clipboard.writeText(roomCode).catch(() => {});
  }
});

canvas.addEventListener("pointerdown", (event) => {
  swipeStart = { x: event.clientX, y: event.clientY };
});

canvas.addEventListener("pointerup", (event) => {
  if (!swipeStart) {
    return;
  }
  const direction = directionFromDelta(event.clientX - swipeStart.x, event.clientY - swipeStart.y);
  swipeStart = null;
  if (direction) {
    sendDirection(direction);
  }
});

window.addEventListener("keydown", (event) => {
  const keyMap = {
    ArrowUp: "UP",
    ArrowDown: "DOWN",
    ArrowLeft: "LEFT",
    ArrowRight: "RIGHT",
    w: "UP",
    s: "DOWN",
    a: "LEFT",
    d: "RIGHT",
  };

  const direction = keyMap[event.key];
  if (direction) {
    event.preventDefault();
    sendDirection(direction);
  }
});

window.addEventListener("resize", () => {
  if (latestState) {
    drawBoard(latestState);
  }
});

if (roomCode && playerId) {
  showGame();
  startPolling();
} else {
  showHome();
}

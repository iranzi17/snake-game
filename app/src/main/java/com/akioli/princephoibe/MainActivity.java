package com.akioli.princephoibe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends Activity {
    private static final String DEFAULT_SERVER_URL = "";
    private static final String OLD_DEFAULT_SERVER_URL = "http://192.168.1.102:8000";
    private static final int BG = Color.rgb(16, 19, 25);
    private static final int PANEL = Color.rgb(24, 29, 36);
    private static final int PANEL_STRONG = Color.rgb(32, 39, 50);
    private static final int LINE = Color.rgb(48, 56, 68);
    private static final int TEXT = Color.rgb(244, 246, 248);
    private static final int MUTED = Color.rgb(174, 183, 194);
    private static final int GREEN = Color.rgb(36, 200, 98);
    private static final int BLUE = Color.rgb(79, 140, 255);
    private static final int RED = Color.rgb(239, 68, 68);
    private static final int AMBER = Color.rgb(245, 184, 59);
    private static final int SOLO_COLS = 24;
    private static final int SOLO_ROWS = 24;
    private static final int SOLO_TICK_MS = 130;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    private SharedPreferences preferences;
    private EditText serverInput;
    private EditText nameInput;
    private EditText roomInput;
    private TextView homeStatus;
    private TextView connectionStatus;
    private TextView gameStatus;
    private TextView playerOneName;
    private TextView playerOneScore;
    private TextView playerTwoName;
    private TextView playerTwoScore;
    private View playerOneDot;
    private View playerTwoDot;
    private Button roomBadge;
    private BoardView boardView;

    private String serverUrl = "";
    private String roomCode = "";
    private String playerId = "";
    private boolean inGame = false;
    private boolean polling = false;
    private boolean stateRequestRunning = false;
    private boolean soloMode = false;
    private boolean soloGameOver = false;
    private int soloScore = 0;
    private String soloDirection = "RIGHT";
    private String soloNextDirection = "RIGHT";
    private int soloFoodX = 12;
    private int soloFoodY = 12;
    private ArrayList<int[]> soloSnake = new ArrayList<>();

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling || !inGame) {
                return;
            }
            fetchState(false);
            mainHandler.postDelayed(this, 110);
        }
    };

    private final Runnable soloRunnable = new Runnable() {
        @Override
        public void run() {
            if (!soloMode || !inGame) {
                return;
            }
            stepSoloGame();
            if (!soloGameOver) {
                mainHandler.postDelayed(this, SOLO_TICK_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("couples_snake", MODE_PRIVATE);
        serverUrl = preferences.getString("serverUrl", DEFAULT_SERVER_URL);
        if (OLD_DEFAULT_SERVER_URL.equals(serverUrl)) {
            serverUrl = DEFAULT_SERVER_URL;
            preferences.edit().remove("serverUrl").apply();
        }
        roomCode = preferences.getString("roomCode", "");
        playerId = preferences.getString("playerId", "");

        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        if (!roomCode.isEmpty() && !playerId.isEmpty()) {
            showGame();
            startPolling();
        } else {
            showHome("");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inGame && soloMode) {
            startSoloTick();
        } else if (inGame) {
            startPolling();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
        stopSoloTick();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow();
    }

    private void showHome(String message) {
        inGame = false;
        soloMode = false;
        stopPolling();
        stopSoloTick();

        LinearLayout root = baseRoot();
        addTopBar(root, "Offline");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(panelBackground(PANEL, LINE));

        PreviewView previewView = new PreviewView(this);
        panel.addView(previewView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(132)
        ));

        addSpace(panel, 14);

        serverInput = field("Server URL, for example http://192.168.1.107:8000", serverUrl);
        panel.addView(serverInput);

        addSpace(panel, 12);

        nameInput = field("Name", preferences.getString("playerName", ""));
        panel.addView(nameInput);

        Button soloButton = button("Play Alone", AMBER, Color.rgb(18, 13, 3), 0);
        LinearLayout.LayoutParams soloParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        );
        soloParams.topMargin = dp(14);
        panel.addView(soloButton, soloParams);

        Button createButton = button("Create Room", GREEN, Color.rgb(7, 18, 11), 0);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        );
        createParams.topMargin = dp(14);
        panel.addView(createButton, createParams);

        LinearLayout joinRow = new LinearLayout(this);
        joinRow.setOrientation(LinearLayout.HORIZONTAL);
        joinRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams joinParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        joinParams.topMargin = dp(12);

        roomInput = field("Code", "");
        roomInput.setAllCaps(true);
        roomInput.setSingleLine(true);
        roomInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});

        LinearLayout.LayoutParams roomParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        joinRow.addView(roomInput, roomParams);

        Button joinButton = button("Join", PANEL_STRONG, TEXT, LINE);
        LinearLayout.LayoutParams joinButtonParams = new LinearLayout.LayoutParams(dp(104), dp(50));
        joinButtonParams.leftMargin = dp(10);
        joinRow.addView(joinButton, joinButtonParams);
        panel.addView(joinRow, joinParams);

        homeStatus = label(message, message.isEmpty() ? MUTED : Color.rgb(255, 141, 141), 14, Typeface.NORMAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(36)
        );
        statusParams.topMargin = dp(10);
        panel.addView(homeStatus, statusParams);

        soloButton.setOnClickListener(view -> startSoloGame());
        createButton.setOnClickListener(view -> createRoom());
        joinButton.setOnClickListener(view -> joinRoom());

        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.CENTER_VERTICAL;
        panelParams.topMargin = dp(18);
        panelParams.bottomMargin = dp(18);
        scrollView.addView(panel, panelParams);

        root.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        setContentView(root);
    }

    private void showGame() {
        soloMode = false;
        showGameScreen(false);
    }

    private void showGameScreen(boolean solo) {
        inGame = true;

        LinearLayout root = baseRoot();
        addTopBar(root, solo ? "Solo" : "Online");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout scoreBar = new LinearLayout(this);
        scoreBar.setOrientation(LinearLayout.HORIZONTAL);
        scoreBar.setGravity(Gravity.CENTER);

        LinearLayout playerOnePill = scorePill(true);
        LinearLayout playerTwoPill = scorePill(false);
        roomBadge = button(solo ? "SOLO" : roomCode.isEmpty() ? "----" : roomCode, Color.rgb(21, 25, 31), AMBER, LINE);

        scoreBar.addView(playerOnePill, new LinearLayout.LayoutParams(0, dp(46), 1f));

        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(78), dp(46));
        badgeParams.leftMargin = dp(8);
        badgeParams.rightMargin = dp(8);
        scoreBar.addView(roomBadge, badgeParams);

        scoreBar.addView(playerTwoPill, new LinearLayout.LayoutParams(0, dp(46), 1f));

        content.addView(scoreBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(46)
        ));

        addSpace(content, 12);

        boardView = new BoardView(this);
        boardView.setDirectionListener(this::handleDirection);
        content.addView(boardView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        gameStatus = label("", MUTED, 15, Typeface.BOLD);
        gameStatus.setGravity(Gravity.CENTER);
        content.addView(gameStatus, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(34)
        ));

        GridLayout controls = new GridLayout(this);
        controls.setColumnCount(3);
        controls.setRowCount(2);
        controls.setUseDefaultMargins(false);
        controls.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        addControlCell(controls, null);
        addControlCell(controls, controlButton("UP", "^"));
        addControlCell(controls, null);
        addControlCell(controls, controlButton("LEFT", "<"));
        addControlCell(controls, controlButton("DOWN", "v"));
        addControlCell(controls, controlButton("RIGHT", ">"));

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        controlsParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(controls, controlsParams);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);

        Button restartButton = button("Restart", PANEL_STRONG, TEXT, LINE);
        Button leaveButton = button("Leave", BG, MUTED, LINE);

        LinearLayout.LayoutParams actionButtonParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        actionButtonParams.topMargin = dp(12);
        actionRow.addView(restartButton, actionButtonParams);

        LinearLayout.LayoutParams leaveParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        leaveParams.leftMargin = dp(10);
        leaveParams.topMargin = dp(12);
        actionRow.addView(leaveButton, leaveParams);

        content.addView(actionRow, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        restartButton.setOnClickListener(view -> {
            if (soloMode) {
                restartSoloGame();
            } else {
                restartGame();
            }
        });
        leaveButton.setOnClickListener(view -> leaveGame());

        root.addView(content, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        setContentView(root);
    }

    private void createRoom() {
        saveHomeFields();
        setHomeStatus("", MUTED);
        if (!hasServerUrl()) {
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("name", nameInput.getText().toString().trim());
        } catch (JSONException ignored) {
        }

        postJson("/api/create", body, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                handleSessionResponse(json);
            }

            @Override
            public void onError(String message) {
                setHomeStatus(message, Color.rgb(255, 141, 141));
            }
        });
    }

    private void joinRoom() {
        saveHomeFields();
        setHomeStatus("", MUTED);
        if (!hasServerUrl()) {
            return;
        }

        String code = roomInput.getText().toString().trim().toUpperCase(Locale.US);
        roomInput.setText(code);

        JSONObject body = new JSONObject();
        try {
            body.put("name", nameInput.getText().toString().trim());
            body.put("roomCode", code);
        } catch (JSONException ignored) {
        }

        postJson("/api/join", body, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                handleSessionResponse(json);
            }

            @Override
            public void onError(String message) {
                setHomeStatus(message, Color.rgb(255, 141, 141));
            }
        });
    }

    private void handleSessionResponse(JSONObject json) {
        roomCode = json.optString("roomCode", "");
        playerId = json.optString("playerId", "");
        preferences.edit()
            .putString("roomCode", roomCode)
            .putString("playerId", playerId)
            .apply();

        showGame();
        applyState(json.optJSONObject("state"));
        startPolling();
    }

    private void startSoloGame() {
        saveHomeFields();
        soloMode = true;
        stopPolling();
        resetSoloState();
        showGameScreen(true);
        applySoloState();
        startSoloTick();
    }

    private void restartSoloGame() {
        resetSoloState();
        applySoloState();
        startSoloTick();
    }

    private void resetSoloState() {
        soloScore = 0;
        soloDirection = "RIGHT";
        soloNextDirection = "RIGHT";
        soloGameOver = false;
        soloSnake = new ArrayList<>();
        soloSnake.add(new int[]{5, 12});
        soloSnake.add(new int[]{4, 12});
        soloSnake.add(new int[]{3, 12});
        placeSoloFood();
    }

    private void startSoloTick() {
        stopSoloTick();
        if (soloMode && inGame && !soloGameOver) {
            mainHandler.postDelayed(soloRunnable, SOLO_TICK_MS);
        }
    }

    private void stopSoloTick() {
        mainHandler.removeCallbacks(soloRunnable);
    }

    private void stepSoloGame() {
        if (soloGameOver || soloSnake.isEmpty()) {
            return;
        }

        soloDirection = soloNextDirection;
        int[] head = soloSnake.get(0);
        int nextX = head[0] + dxFor(soloDirection);
        int nextY = head[1] + dyFor(soloDirection);
        boolean grows = nextX == soloFoodX && nextY == soloFoodY;

        if (nextX < 0 || nextX >= SOLO_COLS || nextY < 0 || nextY >= SOLO_ROWS || hitsSoloBody(nextX, nextY, grows)) {
            soloGameOver = true;
            saveSoloBestScore();
            applySoloState();
            return;
        }

        soloSnake.add(0, new int[]{nextX, nextY});
        if (grows) {
            soloScore += 1;
            saveSoloBestScore();
            placeSoloFood();
        } else {
            soloSnake.remove(soloSnake.size() - 1);
        }

        applySoloState();
    }

    private void queueSoloDirection(String direction) {
        if (direction == null || direction.isEmpty()) {
            return;
        }
        if (!isOpposite(direction, soloDirection)) {
            soloNextDirection = direction;
        }
    }

    private void placeSoloFood() {
        do {
            soloFoodX = random.nextInt(SOLO_COLS);
            soloFoodY = random.nextInt(SOLO_ROWS);
        } while (containsSoloCell(soloFoodX, soloFoodY));
    }

    private boolean hitsSoloBody(int x, int y, boolean grows) {
        int limit = grows ? soloSnake.size() : soloSnake.size() - 1;
        for (int index = 0; index < limit; index++) {
            int[] block = soloSnake.get(index);
            if (block[0] == x && block[1] == y) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSoloCell(int x, int y) {
        for (int[] block : soloSnake) {
            if (block[0] == x && block[1] == y) {
                return true;
            }
        }
        return false;
    }

    private void saveSoloBestScore() {
        int best = preferences.getInt("soloBestScore", 0);
        if (soloScore > best) {
            preferences.edit().putInt("soloBestScore", soloScore).apply();
        }
    }

    private int dxFor(String direction) {
        if ("LEFT".equals(direction)) {
            return -1;
        }
        if ("RIGHT".equals(direction)) {
            return 1;
        }
        return 0;
    }

    private int dyFor(String direction) {
        if ("UP".equals(direction)) {
            return -1;
        }
        if ("DOWN".equals(direction)) {
            return 1;
        }
        return 0;
    }

    private boolean isOpposite(String direction, String currentDirection) {
        return ("UP".equals(direction) && "DOWN".equals(currentDirection))
            || ("DOWN".equals(direction) && "UP".equals(currentDirection))
            || ("LEFT".equals(direction) && "RIGHT".equals(currentDirection))
            || ("RIGHT".equals(direction) && "LEFT".equals(currentDirection));
    }

    private void applySoloState() {
        applyState(buildSoloState());
    }

    private JSONObject buildSoloState() {
        JSONObject state = new JSONObject();
        try {
            JSONObject board = new JSONObject();
            board.put("cols", SOLO_COLS);
            board.put("rows", SOLO_ROWS);

            JSONObject food = new JSONObject();
            food.put("x", soloFoodX);
            food.put("y", soloFoodY);

            JSONArray body = new JSONArray();
            for (int[] block : soloSnake) {
                JSONObject cell = new JSONObject();
                cell.put("x", block[0]);
                cell.put("y", block[1]);
                body.put(cell);
            }

            JSONObject player = new JSONObject();
            String name = preferences.getString("playerName", "").trim();
            player.put("name", name.isEmpty() ? "You" : name);
            player.put("slot", 0);
            player.put("color", "#24C862");
            player.put("score", soloScore);
            player.put("body", body);
            player.put("isYou", true);

            JSONObject best = new JSONObject();
            best.put("name", "Best");
            best.put("slot", 1);
            best.put("color", "#F5B83B");
            best.put("score", Math.max(soloScore, preferences.getInt("soloBestScore", 0)));
            best.put("body", new JSONArray());
            best.put("isYou", false);

            JSONArray players = new JSONArray();
            players.put(player);
            players.put(best);

            state.put("roomCode", "SOLO");
            state.put("status", soloGameOver ? "gameover" : "playing");
            state.put("winner", "");
            state.put("message", soloGameOver ? "Game Over" : "");
            state.put("detail", "Score: " + soloScore);
            state.put("board", board);
            state.put("food", food);
            state.put("players", players);
        } catch (JSONException ignored) {
        }
        return state;
    }

    private void fetchState(boolean showErrors) {
        if (stateRequestRunning || roomCode.isEmpty() || playerId.isEmpty()) {
            return;
        }
        stateRequestRunning = true;

        String path = "/api/state?room=" + Uri.encode(roomCode) + "&player=" + Uri.encode(playerId);
        getJson(path, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                stateRequestRunning = false;
                applyState(json.optJSONObject("state"));
            }

            @Override
            public void onError(String message) {
                stateRequestRunning = false;
                if (showErrors) {
                    showHome(message);
                } else if (gameStatus != null) {
                    gameStatus.setText(message);
                }
            }
        });
    }

    private void sendDirection(String direction) {
        if (soloMode) {
            queueSoloDirection(direction);
            return;
        }
        if (roomCode.isEmpty() || playerId.isEmpty()) {
            return;
        }

        if (boardView != null) {
            boardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        JSONObject body = new JSONObject();
        try {
            body.put("roomCode", roomCode);
            body.put("playerId", playerId);
            body.put("direction", direction);
        } catch (JSONException ignored) {
        }

        postJson("/api/input", body, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void restartGame() {
        if (soloMode) {
            restartSoloGame();
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("roomCode", roomCode);
            body.put("playerId", playerId);
        } catch (JSONException ignored) {
        }

        postJson("/api/restart", body, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                applyState(json.optJSONObject("state"));
            }

            @Override
            public void onError(String message) {
                if (gameStatus != null) {
                    gameStatus.setText(message);
                }
            }
        });
    }

    private void leaveGame() {
        stopSoloTick();
        stopPolling();
        soloMode = false;
        preferences.edit()
            .remove("roomCode")
            .remove("playerId")
            .apply();
        roomCode = "";
        playerId = "";
        showHome("");
    }

    private void applyState(JSONObject state) {
        if (state == null) {
            return;
        }

        if (boardView != null) {
            boardView.setState(state);
        }

        String displayRoomCode = state.optString("roomCode", roomCode);
        if (!"SOLO".equals(displayRoomCode)) {
            roomCode = displayRoomCode;
        }
        if (roomBadge != null) {
            roomBadge.setText(displayRoomCode);
        }

        JSONArray players = state.optJSONArray("players");
        applyScore(players, 0, playerOneName, playerOneScore, playerOneDot);
        applyScore(players, 1, playerTwoName, playerTwoScore, playerTwoDot);

        String status = state.optString("status", "waiting");
        String winner = state.optString("winner", "");
        String message = state.optString("message", "");

        if (gameStatus != null) {
            if ("gameover".equals(status)) {
                if (!message.isEmpty()) {
                    gameStatus.setText(message + " - Score " + getOwnScore(players));
                } else {
                    gameStatus.setText("Draw".equals(winner) ? "Draw" : winner + " wins");
                }
            } else if ("SOLO".equals(displayRoomCode)) {
                gameStatus.setText("Solo - Score " + getOwnScore(players));
            } else {
                gameStatus.setText("Room " + roomCode);
            }
        }
    }

    private int getOwnScore(JSONArray players) {
        if (players == null) {
            return 0;
        }

        for (int index = 0; index < players.length(); index++) {
            JSONObject player = players.optJSONObject(index);
            if (player != null && player.optBoolean("isYou", false)) {
                return player.optInt("score", 0);
            }
        }

        return 0;
    }

    private void applyScore(JSONArray players, int index, TextView nameView, TextView scoreView, View dotView) {
        if (nameView == null || scoreView == null || dotView == null) {
            return;
        }

        JSONObject player = players != null ? players.optJSONObject(index) : null;
        if (player == null) {
            nameView.setText(index == 0 ? "Player 1" : "Player 2");
            scoreView.setText("0");
            dotView.setBackground(cellBackground(MUTED));
            return;
        }

        String name = player.optString("name", index == 0 ? "Player 1" : "Player 2");
        if (player.optBoolean("isYou", false)) {
            name += " - You";
        }
        nameView.setText(name);
        scoreView.setText(String.valueOf(player.optInt("score", 0)));
        dotView.setBackground(cellBackground(Color.parseColor(player.optString("color", "#AEB7C2"))));
    }

    private void startPolling() {
        if (polling) {
            return;
        }
        polling = true;
        fetchState(true);
        mainHandler.post(pollRunnable);
    }

    private void stopPolling() {
        polling = false;
        mainHandler.removeCallbacks(pollRunnable);
    }

    private void saveHomeFields() {
        serverUrl = normalizeServerUrl(serverInput.getText().toString());
        String playerName = nameInput.getText().toString().trim();
        preferences.edit()
            .putString("serverUrl", serverUrl)
            .putString("playerName", playerName)
            .apply();
    }

    private String normalizeServerUrl(String rawValue) {
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return DEFAULT_SERVER_URL;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean hasServerUrl() {
        if (!serverUrl.isEmpty()) {
            return true;
        }

        setHomeStatus("Enter the server URL printed by mobile_snake_server.py", Color.rgb(255, 141, 141));
        return false;
    }

    private void setHomeStatus(String message, int color) {
        if (homeStatus != null) {
            homeStatus.setText(message);
            homeStatus.setTextColor(color);
        }
    }

    private LinearLayout baseRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(10));
        root.setBackgroundColor(BG);
        return root;
    }

    private void addTopBar(LinearLayout root, String status) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = label("Prince&Phoibe", TEXT, 18, Typeface.BOLD);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(54), 1f));

        connectionStatus = label(status, MUTED, 13, Typeface.BOLD);
        connectionStatus.setGravity(Gravity.CENTER);
        connectionStatus.setBackground(panelBackground(BG, LINE));
        bar.addView(connectionStatus, new LinearLayout.LayoutParams(dp(86), dp(36)));

        root.addView(bar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ));
    }

    private LinearLayout scorePill(boolean first) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(9), 0, dp(9), 0);
        pill.setBackground(panelBackground(PANEL, LINE));

        View dot = new View(this);
        dot.setBackground(cellBackground(first ? GREEN : BLUE));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        pill.addView(dot, dotParams);

        TextView name = label(first ? "Player 1" : "Player 2", MUTED, 13, Typeface.NORMAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(8);
        pill.addView(name, nameParams);

        TextView score = label("0", TEXT, 18, Typeface.BOLD);
        score.setGravity(Gravity.RIGHT);
        pill.addView(score, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (first) {
            playerOneDot = dot;
            playerOneName = name;
            playerOneScore = score;
        } else {
            playerTwoDot = dot;
            playerTwoName = name;
            playerTwoScore = score;
        }

        return pill;
    }

    private EditText field(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
        editText.setSingleLine(true);
        editText.setTextColor(TEXT);
        editText.setHintTextColor(MUTED);
        editText.setTextSize(15);
        editText.setPadding(dp(13), 0, dp(13), 0);
        editText.setBackground(panelBackground(Color.rgb(15, 19, 25), LINE));
        return editText;
    }

    private Button button(String text, int bg, int fg, int stroke) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(fg);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(panelBackground(bg, stroke));
        return button;
    }

    private Button controlButton(String direction, String label) {
        Button button = button(label, Color.rgb(37, 44, 54), TEXT, Color.rgb(59, 69, 84));
        button.setTextSize(24);
        button.setOnClickListener(view -> handleDirection(direction));
        return button;
    }

    private void handleDirection(String direction) {
        if (soloMode) {
            queueSoloDirection(direction);
        } else {
            sendDirection(direction);
        }
    }

    private void addControlCell(GridLayout grid, Button button) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(76);
        params.height = dp(58);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));

        if (button == null) {
            Space space = new Space(this);
            grid.addView(space, params);
        } else {
            grid.addView(button, params);
        }
    }

    private TextView label(String text, int color, int sp, int style) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(color);
        label.setTextSize(sp);
        label.setTypeface(Typeface.DEFAULT, style);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setSingleLine(true);
        return label;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        Space space = new Space(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private GradientDrawable panelBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(8));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private GradientDrawable cellBackground(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(4));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void getJson(String path, JsonCallback callback) {
        networkExecutor.execute(() -> {
            try {
                JSONObject json = request("GET", path, null);
                mainHandler.post(() -> callback.onSuccess(json));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(friendlyNetworkError(error)));
            }
        });
    }

    private void postJson(String path, JSONObject body, JsonCallback callback) {
        networkExecutor.execute(() -> {
            try {
                JSONObject json = request("POST", path, body);
                mainHandler.post(() -> callback.onSuccess(json));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(friendlyNetworkError(error)));
            }
        });
    }

    private String friendlyNetworkError(Exception error) {
        if (error instanceof MalformedURLException) {
            return "Check the server URL format.";
        }
        if (error instanceof SocketTimeoutException) {
            return "Server not reachable. Check Wi-Fi, server URL, and firewall.";
        }

        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Server not reachable. Check the server URL.";
        }
        if (message.toLowerCase(Locale.US).contains("failed to connect")) {
            return "Server not reachable. Start the Python server and use its phone URL.";
        }
        return message;
    }

    private JSONObject request(String method, String path, JSONObject body) throws IOException, JSONException {
        URL url = new URL(serverUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3500);
        connection.setReadTimeout(3500);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        if (body != null) {
            connection.setDoOutput(true);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
            ? connection.getInputStream()
            : connection.getErrorStream();
        String response = readStream(stream);
        JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);

        if (status < 200 || status >= 300) {
            throw new IOException(json.optString("error", "Server error"));
        }

        return json;
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private interface JsonCallback {
        void onSuccess(JSONObject json);
        void onError(String message);
    }

    private interface DirectionListener {
        void onDirection(String direction);
    }

    public static class PreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public PreviewView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cell = getWidth() / 12f;
            paint.setColor(Color.rgb(18, 22, 29));
            canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), 8, 8, paint);

            paint.setColor(Color.rgb(48, 56, 68));
            paint.setStrokeWidth(1);
            for (float x = 0; x <= getWidth(); x += cell) {
                canvas.drawLine(x, 0, x, getHeight(), paint);
            }
            for (float y = 0; y <= getHeight(); y += cell) {
                canvas.drawLine(0, y, getWidth(), y, paint);
            }

            drawPreviewBlock(canvas, 2, 2, cell, Color.rgb(36, 200, 98));
            drawPreviewBlock(canvas, 3, 2, cell, Color.rgb(36, 200, 98));
            drawPreviewBlock(canvas, 4, 2, cell, Color.rgb(36, 200, 98));
            drawPreviewBlock(canvas, 8, 5, cell, Color.rgb(79, 140, 255));
            drawPreviewBlock(canvas, 9, 5, cell, Color.rgb(79, 140, 255));
            drawPreviewBlock(canvas, 10, 5, cell, Color.rgb(79, 140, 255));
            drawPreviewBlock(canvas, 6, 3.5f, cell, Color.rgb(239, 68, 68));
        }

        private void drawPreviewBlock(Canvas canvas, float col, float row, float cell, int color) {
            float pad = cell * 0.12f;
            paint.setColor(color);
            RectF rect = new RectF(col * cell + pad, row * cell + pad, (col + 1) * cell - pad, (row + 1) * cell - pad);
            canvas.drawRoundRect(rect, 6, 6, paint);
        }
    }

    public static class BoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private JSONObject state;
        private DirectionListener directionListener;
        private float downX;
        private float downY;

        public BoardView(Context context) {
            super(context);
            setBackgroundColor(Color.rgb(17, 21, 27));
        }

        public void setDirectionListener(DirectionListener listener) {
            directionListener = listener;
        }

        public void setState(JSONObject state) {
            this.state = state;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int size = Math.min(width, height);
            setMeasuredDimension(size, size);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                String direction = directionFromDelta(event.getX() - downX, event.getY() - downY);
                if (!direction.isEmpty() && directionListener != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    directionListener.onDirection(direction);
                }
                return true;
            }

            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(Color.rgb(17, 21, 27));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

            if (state == null) {
                return;
            }

            try {
                JSONObject board = state.getJSONObject("board");
                int cols = board.getInt("cols");
                int rows = board.getInt("rows");
                float cell = Math.min(getWidth() / (float) cols, getHeight() / (float) rows);
                float boardWidth = cols * cell;
                float boardHeight = rows * cell;
                float offsetX = (getWidth() - boardWidth) / 2f;
                float offsetY = (getHeight() - boardHeight) / 2f;

                drawGrid(canvas, cols, rows, cell, offsetX, offsetY, boardWidth, boardHeight);

                JSONObject food = state.getJSONObject("food");
                drawCell(canvas, food.getInt("x"), food.getInt("y"), cell, offsetX, offsetY, Color.rgb(239, 68, 68), 0.09f);

                JSONArray players = state.getJSONArray("players");
                for (int playerIndex = 0; playerIndex < players.length(); playerIndex++) {
                    JSONObject player = players.getJSONObject(playerIndex);
                    int color = Color.parseColor(player.optString("color", "#AEB7C2"));
                    JSONArray body = player.getJSONArray("body");

                    for (int blockIndex = body.length() - 1; blockIndex >= 0; blockIndex--) {
                        JSONObject block = body.getJSONObject(blockIndex);
                        int x = block.getInt("x");
                        int y = block.getInt("y");
                        if (blockIndex == 0) {
                            drawCell(canvas, x, y, cell, offsetX, offsetY, Color.rgb(244, 246, 248), 0.09f);
                            drawCell(canvas, x, y, cell, offsetX, offsetY, color, 0.28f);
                        } else {
                            drawCell(canvas, x, y, cell, offsetX, offsetY, color, 0.09f);
                        }
                    }
                }

                String status = state.optString("status", "waiting");
                if ("waiting".equals(status) || "gameover".equals(status)) {
                    drawOverlay(canvas, state);
                }
            } catch (JSONException ignored) {
            }
        }

        private void drawGrid(Canvas canvas, int cols, int rows, float cell, float offsetX, float offsetY, float boardWidth, float boardHeight) {
            paint.setColor(Color.rgb(36, 43, 53));
            paint.setStrokeWidth(1);
            for (int x = 0; x <= cols; x++) {
                float px = offsetX + x * cell;
                canvas.drawLine(px, offsetY, px, offsetY + boardHeight, paint);
            }
            for (int y = 0; y <= rows; y++) {
                float py = offsetY + y * cell;
                canvas.drawLine(offsetX, py, offsetX + boardWidth, py, paint);
            }
        }

        private void drawCell(Canvas canvas, int x, int y, float cell, float offsetX, float offsetY, int color, float padRatio) {
            float pad = Math.max(2f, cell * padRatio);
            float left = offsetX + x * cell + pad;
            float top = offsetY + y * cell + pad;
            float right = offsetX + (x + 1) * cell - pad;
            float bottom = offsetY + (y + 1) * cell - pad;
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), Math.min(8f, cell * 0.24f), Math.min(8f, cell * 0.24f), paint);
        }

        private void drawOverlay(Canvas canvas, JSONObject state) {
            paint.setColor(Color.argb(160, 0, 0, 0));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

            String winner = state.optString("winner", "");
            String message = state.optString("message", "");
            String title = !message.isEmpty()
                ? message
                : "waiting".equals(state.optString("status", "waiting"))
                    ? "Waiting"
                    : "Draw".equals(winner) ? "Draw" : winner + " wins";

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(Color.rgb(244, 246, 248));
            paint.setTextSize(34f * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText(title, getWidth() / 2f, getHeight() / 2f - 18f, paint);

            paint.setColor(Color.rgb(245, 184, 59));
            paint.setTextSize(22f * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText(state.optString("detail", state.optString("roomCode", "")), getWidth() / 2f, getHeight() / 2f + 34f, paint);
        }

        private String directionFromDelta(float dx, float dy) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) < 24f) {
                return "";
            }
            if (Math.abs(dx) > Math.abs(dy)) {
                return dx > 0 ? "RIGHT" : "LEFT";
            }
            return dy > 0 ? "DOWN" : "UP";
        }
    }
}

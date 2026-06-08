from __future__ import annotations

import argparse
import json
import mimetypes
import os
import secrets
import socket
import sys
import threading
import time
from dataclasses import dataclass, field
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


BOARD_COLS = 24
BOARD_ROWS = 24
TICK_SECONDS = 0.13
ROOM_TTL_SECONDS = 60 * 60
ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
WEB_DIR = Path(__file__).resolve().parent / "web"

DIRECTIONS = {
    "UP": (0, -1),
    "DOWN": (0, 1),
    "LEFT": (-1, 0),
    "RIGHT": (1, 0),
}

OPPOSITES = {
    "UP": "DOWN",
    "DOWN": "UP",
    "LEFT": "RIGHT",
    "RIGHT": "LEFT",
}

STARTS = [
    {
        "color": "#24c862",
        "body": [[5, 12], [4, 12], [3, 12]],
        "direction": "RIGHT",
    },
    {
        "color": "#4f8cff",
        "body": [[18, 12], [19, 12], [20, 12]],
        "direction": "LEFT",
    },
]

rooms_lock = threading.RLock()
rooms: dict[str, "Room"] = {}


class ApiError(Exception):
    def __init__(self, status: HTTPStatus, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


@dataclass
class PlayerState:
    player_id: str
    name: str
    slot: int
    color: str
    body: list[list[int]]
    direction: str
    next_direction: str
    score: int = 0
    last_seen: float = field(default_factory=time.time)

    @property
    def head(self) -> list[int]:
        return self.body[0]

    def queue_direction(self, direction: str) -> None:
        direction = direction.upper()
        if direction in DIRECTIONS and direction != OPPOSITES[self.direction]:
            self.next_direction = direction


@dataclass
class Room:
    code: str
    players: list[PlayerState] = field(default_factory=list)
    food: list[int] = field(default_factory=lambda: [0, 0])
    status: str = "waiting"
    winner: str | None = None
    message: str = "Waiting for another player"
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)


def sanitize_name(name: object, default: str) -> str:
    if not isinstance(name, str):
        return default
    name = " ".join(name.strip().split())
    return name[:18] or default


def make_player(slot: int, name: str, player_id: str | None = None) -> PlayerState:
    start = STARTS[slot]
    return PlayerState(
        player_id=player_id or secrets.token_urlsafe(16),
        name=name,
        slot=slot,
        color=start["color"],
        body=[block.copy() for block in start["body"]],
        direction=start["direction"],
        next_direction=start["direction"],
    )


def generate_room_code() -> str:
    while True:
        code = "".join(secrets.choice(ROOM_ALPHABET) for _ in range(4))
        if code not in rooms:
            return code


def occupied_cells(players: list[PlayerState]) -> set[tuple[int, int]]:
    return {
        (block[0], block[1])
        for player in players
        for block in player.body
    }


def place_food(players: list[PlayerState]) -> list[int]:
    occupied = occupied_cells(players)
    while True:
        food = [secrets.randbelow(BOARD_COLS), secrets.randbelow(BOARD_ROWS)]
        if tuple(food) not in occupied:
            return food


def is_inside_board(head: list[int]) -> bool:
    return 0 <= head[0] < BOARD_COLS and 0 <= head[1] < BOARD_ROWS


def next_body_for(player: PlayerState, new_head: list[int], grows: bool) -> list[list[int]]:
    if grows:
        return [new_head] + [block.copy() for block in player.body]
    return [new_head] + [block.copy() for block in player.body[:-1]]


def reset_room(room: Room) -> None:
    players = []
    for old_player in room.players:
        player = make_player(old_player.slot, old_player.name, old_player.player_id)
        player.last_seen = old_player.last_seen
        players.append(player)

    room.players = players
    room.food = place_food(room.players)
    room.winner = None
    room.status = "playing" if len(room.players) == 2 else "waiting"
    room.message = "" if room.status == "playing" else "Waiting for another player"
    room.updated_at = time.time()


def get_winner(players: list[PlayerState], losers: set[int]) -> str:
    if len(losers) == len(players):
        return "Draw"

    for index, player in enumerate(players):
        if index not in losers:
            return player.name

    return "Draw"


def step_room(room: Room) -> None:
    if room.status != "playing" or len(room.players) != 2:
        return

    players = room.players
    old_heads = [player.head.copy() for player in players]
    new_heads: list[list[int]] = []
    grows: list[bool] = []

    for player in players:
        player.direction = player.next_direction
        dx, dy = DIRECTIONS[player.direction]
        new_heads.append([player.head[0] + dx, player.head[1] + dy])

    for head in new_heads:
        grows.append(head == room.food)

    next_bodies = [
        next_body_for(player, new_heads[index], grows[index])
        for index, player in enumerate(players)
    ]

    losers: set[int] = set()

    for index, head in enumerate(new_heads):
        if not is_inside_board(head):
            losers.add(index)
        if head in next_bodies[index][1:]:
            losers.add(index)

    if new_heads[0] == new_heads[1]:
        losers.update({0, 1})

    if new_heads[0] == old_heads[1] and new_heads[1] == old_heads[0]:
        losers.update({0, 1})

    for index, head in enumerate(new_heads):
        other_index = 1 - index
        if head in next_bodies[other_index][1:]:
            losers.add(index)

    for index, player in enumerate(players):
        player.body = next_bodies[index]
        if grows[index] and index not in losers:
            player.score += 1

    room.updated_at = time.time()

    if losers:
        room.status = "gameover"
        room.winner = get_winner(players, losers)
        room.message = "Draw" if room.winner == "Draw" else f"{room.winner} wins"
        return

    if any(grows):
        room.food = place_food(players)


def create_room(name: str) -> tuple[Room, PlayerState]:
    code = generate_room_code()
    player = make_player(0, sanitize_name(name, "Player 1"))
    room = Room(code=code, players=[player])
    room.food = place_food(room.players)
    rooms[code] = room
    return room, player


def join_room(code: str, name: str) -> tuple[Room, PlayerState]:
    code = code.strip().upper()
    room = rooms.get(code)
    if not room:
        raise ApiError(HTTPStatus.NOT_FOUND, "Room not found")
    if len(room.players) >= 2:
        raise ApiError(HTTPStatus.CONFLICT, "Room is already full")

    player = make_player(1, sanitize_name(name, "Player 2"))
    room.players.append(player)
    reset_room(room)
    return room, player


def find_player(room: Room, player_id: str) -> PlayerState:
    for player in room.players:
        if player.player_id == player_id:
            player.last_seen = time.time()
            return player
    raise ApiError(HTTPStatus.FORBIDDEN, "Player not in this room")


def get_room(code: str) -> Room:
    room = rooms.get(code.strip().upper())
    if not room:
        raise ApiError(HTTPStatus.NOT_FOUND, "Room not found")
    return room


def room_to_dict(room: Room, player_id: str | None = None) -> dict:
    return {
        "roomCode": room.code,
        "status": room.status,
        "winner": room.winner,
        "message": room.message,
        "board": {"cols": BOARD_COLS, "rows": BOARD_ROWS},
        "food": {"x": room.food[0], "y": room.food[1]},
        "players": [
            {
                "name": player.name,
                "slot": player.slot,
                "color": player.color,
                "score": player.score,
                "body": [{"x": block[0], "y": block[1]} for block in player.body],
                "isYou": player.player_id == player_id,
            }
            for player in room.players
        ],
    }


def tick_rooms_forever() -> None:
    while True:
        time.sleep(TICK_SECONDS)
        now = time.time()
        with rooms_lock:
            for code, room in list(rooms.items()):
                if room.status == "playing":
                    step_room(room)
                if room.players and all(now - player.last_seen > ROOM_TTL_SECONDS for player in room.players):
                    del rooms[code]


class GameRequestHandler(BaseHTTPRequestHandler):
    def do_OPTIONS(self) -> None:
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self) -> None:
        try:
            parsed = urlparse(self.path)
            if parsed.path == "/api/state":
                self.handle_state(parsed.query)
                return
            self.serve_static(parsed.path)
        except ApiError as error:
            self.send_json({"error": error.message}, error.status)
        except Exception as error:
            self.send_json({"error": str(error)}, HTTPStatus.INTERNAL_SERVER_ERROR)

    def do_POST(self) -> None:
        try:
            parsed = urlparse(self.path)
            data = self.read_json()

            if parsed.path == "/api/create":
                with rooms_lock:
                    room, player = create_room(data.get("name", ""))
                    self.send_json({
                        "roomCode": room.code,
                        "playerId": player.player_id,
                        "state": room_to_dict(room, player.player_id),
                    })
                return

            if parsed.path == "/api/join":
                with rooms_lock:
                    room, player = join_room(str(data.get("roomCode", "")), data.get("name", ""))
                    self.send_json({
                        "roomCode": room.code,
                        "playerId": player.player_id,
                        "state": room_to_dict(room, player.player_id),
                    })
                return

            if parsed.path == "/api/input":
                self.handle_input(data)
                return

            if parsed.path == "/api/restart":
                self.handle_restart(data)
                return

            raise ApiError(HTTPStatus.NOT_FOUND, "Unknown endpoint")
        except ApiError as error:
            self.send_json({"error": error.message}, error.status)
        except json.JSONDecodeError:
            self.send_json({"error": "Invalid JSON"}, HTTPStatus.BAD_REQUEST)
        except Exception as error:
            self.send_json({"error": str(error)}, HTTPStatus.INTERNAL_SERVER_ERROR)

    def handle_state(self, query: str) -> None:
        params = parse_qs(query)
        room_code = params.get("room", [""])[0]
        player_id = params.get("player", [""])[0]

        with rooms_lock:
            room = get_room(room_code)
            find_player(room, player_id)
            self.send_json({"state": room_to_dict(room, player_id)})

    def handle_input(self, data: dict) -> None:
        with rooms_lock:
            room = get_room(str(data.get("roomCode", "")))
            player = find_player(room, str(data.get("playerId", "")))
            if room.status == "playing":
                player.queue_direction(str(data.get("direction", "")))
            self.send_json({"ok": True})

    def handle_restart(self, data: dict) -> None:
        with rooms_lock:
            room = get_room(str(data.get("roomCode", "")))
            find_player(room, str(data.get("playerId", "")))
            reset_room(room)
            self.send_json({"state": room_to_dict(room, str(data.get("playerId", "")))})

    def read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(length) if length else b"{}"
        if not raw_body:
            return {}
        return json.loads(raw_body.decode("utf-8"))

    def send_json(self, payload: dict, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def serve_static(self, request_path: str) -> None:
        if request_path == "/":
            request_path = "/index.html"

        relative_path = request_path.lstrip("/")
        target = (WEB_DIR / relative_path).resolve()
        try:
            target.relative_to(WEB_DIR.resolve())
        except ValueError:
            raise ApiError(HTTPStatus.NOT_FOUND, "File not found") from None

        if not target.is_file():
            raise ApiError(HTTPStatus.NOT_FOUND, "File not found")

        body = target.read_bytes()
        content_type = mimetypes.guess_type(target.name)[0] or "application/octet-stream"

        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: object) -> None:
        return


def get_lan_ip() -> str:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        try:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
        except OSError:
            return socket.gethostbyname(socket.gethostname())


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Prince&Phoibe phone server.")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", default=int(os.environ.get("PORT", "8000")), type=int)
    args = parser.parse_args()

    if not WEB_DIR.exists():
        print(f"Missing web folder: {WEB_DIR}", file=sys.stderr)
        return 1

    threading.Thread(target=tick_rooms_forever, daemon=True).start()

    server = ThreadingHTTPServer((args.host, args.port), GameRequestHandler)
    server.daemon_threads = True

    render_url = os.environ.get("RENDER_EXTERNAL_URL")
    lan_url = render_url or f"http://{get_lan_ip()}:{args.port}"
    local_url = f"http://127.0.0.1:{args.port}"
    print("Prince&Phoibe server is running")
    print(f"Local: {local_url}")
    print(f"Phones/public URL: {lan_url}")
    print("Press Ctrl+C to stop.")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server.")
    finally:
        server.server_close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

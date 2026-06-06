import random
import sys
from dataclasses import dataclass

import pygame


pygame.init()

WIDTH = 640
HEIGHT = 480
HUD_HEIGHT = 40
BLOCK_SIZE = 20
SPEED = 10

screen = pygame.display.set_mode((WIDTH, HEIGHT))
pygame.display.set_caption("Prince&Phoibe")
clock = pygame.time.Clock()

FONT = pygame.font.SysFont("Arial", 24)
BIG_FONT = pygame.font.SysFont("Arial", 42, bold=True)

WHITE = (245, 245, 245)
MUTED = (165, 172, 181)
BLACK = (12, 14, 18)
PANEL = (24, 28, 36)
GRID = (31, 35, 44)
GREEN = (36, 190, 98)
BLUE = (72, 158, 255)
FOOD = (239, 68, 68)

DIRECTIONS = {
    "UP": (0, -BLOCK_SIZE),
    "DOWN": (0, BLOCK_SIZE),
    "LEFT": (-BLOCK_SIZE, 0),
    "RIGHT": (BLOCK_SIZE, 0),
}

OPPOSITES = {
    "UP": "DOWN",
    "DOWN": "UP",
    "LEFT": "RIGHT",
    "RIGHT": "LEFT",
}


@dataclass
class Player:
    name: str
    color: tuple[int, int, int]
    controls: dict[int, str]
    body: list[list[int]]
    direction: str
    next_direction: str
    score: int = 0

    @property
    def head(self):
        return self.body[0]

    def queue_direction(self, key):
        wanted_direction = self.controls.get(key)
        if wanted_direction and wanted_direction != OPPOSITES[self.direction]:
            self.next_direction = wanted_direction


def draw_text(text, font, color, x, y, center=False):
    message = font.render(text, True, color)
    rect = message.get_rect()
    if center:
        rect.center = (x, y)
    else:
        rect.topleft = (x, y)
    screen.blit(message, rect)


def make_players():
    return [
        Player(
            name="You",
            color=GREEN,
            controls={
                pygame.K_w: "UP",
                pygame.K_s: "DOWN",
                pygame.K_a: "LEFT",
                pygame.K_d: "RIGHT",
            },
            body=[[140, 240], [120, 240], [100, 240]],
            direction="RIGHT",
            next_direction="RIGHT",
        ),
        Player(
            name="Girlfriend",
            color=BLUE,
            controls={
                pygame.K_UP: "UP",
                pygame.K_DOWN: "DOWN",
                pygame.K_LEFT: "LEFT",
                pygame.K_RIGHT: "RIGHT",
            },
            body=[[500, 240], [520, 240], [540, 240]],
            direction="LEFT",
            next_direction="LEFT",
        ),
    ]


def occupied_cells(players):
    cells = set()
    for player in players:
        for block in player.body:
            cells.add(tuple(block))
    return cells


def random_food_position(players):
    occupied = occupied_cells(players)

    while True:
        x = random.randrange(0, WIDTH, BLOCK_SIZE)
        y = random.randrange(HUD_HEIGHT, HEIGHT, BLOCK_SIZE)
        if (x, y) not in occupied:
            return [x, y]


def is_inside_play_area(head):
    return 0 <= head[0] < WIDTH and HUD_HEIGHT <= head[1] < HEIGHT


def next_body_for(player, new_head, grows):
    if grows:
        return [new_head] + [block.copy() for block in player.body]
    return [new_head] + [block.copy() for block in player.body[:-1]]


def move_players(players, food):
    new_heads = []
    grows = []

    for player in players:
        player.direction = player.next_direction
        dx, dy = DIRECTIONS[player.direction]
        new_heads.append([player.head[0] + dx, player.head[1] + dy])

    for head in new_heads:
        grows.append(head == food)

    next_bodies = [
        next_body_for(player, new_head, grows[index])
        for index, (player, new_head) in enumerate(zip(players, new_heads))
    ]

    losers = set()

    for index, player in enumerate(players):
        head = new_heads[index]
        if not is_inside_play_area(head):
            losers.add(player.name)

        if head in next_bodies[index][1:]:
            losers.add(player.name)

    if new_heads[0] == new_heads[1]:
        losers.add(players[0].name)
        losers.add(players[1].name)

    for index, player in enumerate(players):
        other_index = 1 - index
        if new_heads[index] in next_bodies[other_index][1:]:
            losers.add(player.name)

    for index, player in enumerate(players):
        player.body = next_bodies[index]
        if grows[index] and player.name not in losers:
            player.score += 1

    if losers:
        winner = get_winner(players, losers)
        return food, winner

    if any(grows):
        food = random_food_position(players)

    return food, None


def get_winner(players, losers):
    if len(losers) == len(players):
        return "Draw"

    for player in players:
        if player.name not in losers:
            return player.name

    return "Draw"


def draw_board(players, food, winner=None):
    screen.fill(BLACK)
    pygame.draw.rect(screen, PANEL, pygame.Rect(0, 0, WIDTH, HUD_HEIGHT))

    draw_text(f"{players[0].name}: {players[0].score}", FONT, players[0].color, 16, 9)
    draw_text(
        f"{players[1].name}: {players[1].score}",
        FONT,
        players[1].color,
        WIDTH - 170,
        9,
    )

    for x in range(0, WIDTH, BLOCK_SIZE):
        pygame.draw.line(screen, GRID, (x, HUD_HEIGHT), (x, HEIGHT), 1)
    for y in range(HUD_HEIGHT, HEIGHT, BLOCK_SIZE):
        pygame.draw.line(screen, GRID, (0, y), (WIDTH, y), 1)

    pygame.draw.rect(
        screen,
        FOOD,
        pygame.Rect(food[0], food[1], BLOCK_SIZE, BLOCK_SIZE),
        border_radius=4,
    )

    for player in players:
        for index, block in enumerate(player.body):
            rect = pygame.Rect(block[0], block[1], BLOCK_SIZE, BLOCK_SIZE)
            if index == 0:
                pygame.draw.rect(screen, WHITE, rect, border_radius=4)
                inset = rect.inflate(-6, -6)
                pygame.draw.rect(screen, player.color, inset, border_radius=3)
            else:
                pygame.draw.rect(screen, player.color, rect, border_radius=4)

    if winner:
        overlay = pygame.Surface((WIDTH, HEIGHT), pygame.SRCALPHA)
        overlay.fill((0, 0, 0, 175))
        screen.blit(overlay, (0, 0))

        result = "Draw!" if winner == "Draw" else f"{winner} wins!"
        draw_text(result, BIG_FONT, WHITE, WIDTH // 2, HEIGHT // 2 - 60, center=True)
        draw_text(
            f"{players[0].name} {players[0].score}  -  {players[1].score} {players[1].name}",
            FONT,
            MUTED,
            WIDTH // 2,
            HEIGHT // 2,
            center=True,
        )
        draw_text("R Restart    Q Quit", FONT, WHITE, WIDTH // 2, HEIGHT // 2 + 50, center=True)

    pygame.display.update()


def reset_game():
    players = make_players()
    food = random_food_position(players)
    return players, food, None


def game_loop():
    players, food, winner = reset_game()
    running = True

    while running:
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                pygame.quit()
                sys.exit()

            if event.type == pygame.KEYDOWN:
                if winner:
                    if event.key == pygame.K_r:
                        players, food, winner = reset_game()
                    elif event.key == pygame.K_q:
                        pygame.quit()
                        sys.exit()
                else:
                    for player in players:
                        player.queue_direction(event.key)

        if not winner:
            food, winner = move_players(players, food)

        draw_board(players, food, winner)
        clock.tick(SPEED)


if __name__ == "__main__":
    game_loop()

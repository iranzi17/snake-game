import unittest

import mobile_snake_server as game


class RoomLogicTests(unittest.TestCase):
    def make_room(self):
        room = game.Room(code="TEST")
        room.players = [
            game.make_player(0, "You", "p1"),
            game.make_player(1, "Partner", "p2"),
        ]
        room.status = "playing"
        room.food = game.place_food(room.players)
        return room

    def test_player_scores_when_eating_food(self):
        room = self.make_room()
        room.food = [6, 12]

        game.step_room(room)

        self.assertEqual(room.players[0].score, 1)
        self.assertEqual(room.status, "playing")

    def test_wall_collision_gives_other_player_the_win(self):
        room = self.make_room()
        room.players[0].body = [[0, 5], [1, 5], [2, 5]]
        room.players[0].direction = "LEFT"
        room.players[0].next_direction = "LEFT"
        room.food = [12, 12]

        game.step_room(room)

        self.assertEqual(room.status, "gameover")
        self.assertEqual(room.winner, "Partner")

    def test_collision_with_other_snake_body_gives_other_player_the_win(self):
        room = self.make_room()
        room.players[0].body = [[5, 5], [4, 5], [3, 5]]
        room.players[0].direction = "RIGHT"
        room.players[0].next_direction = "RIGHT"
        room.players[1].body = [[14, 14], [6, 5], [6, 6]]
        room.players[1].direction = "LEFT"
        room.players[1].next_direction = "LEFT"
        room.food = [12, 12]

        game.step_room(room)

        self.assertEqual(room.status, "gameover")
        self.assertEqual(room.winner, "Partner")

    def test_head_on_swap_is_a_draw(self):
        room = self.make_room()
        room.players[0].body = [[5, 5], [4, 5], [3, 5]]
        room.players[0].direction = "RIGHT"
        room.players[0].next_direction = "RIGHT"
        room.players[1].body = [[6, 5], [7, 5], [8, 5]]
        room.players[1].direction = "LEFT"
        room.players[1].next_direction = "LEFT"
        room.food = [12, 12]

        game.step_room(room)

        self.assertEqual(room.status, "gameover")
        self.assertEqual(room.winner, "Draw")


if __name__ == "__main__":
    unittest.main()

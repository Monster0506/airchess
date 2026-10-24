# AirChess

**Your table is the board. Your friends are anywhere.**

AirChess drops a full-size, real-feeling chess set onto any table, desk, or floor through your phone's camera, then lets you play it: alone, with a coach at your shoulder, or against a friend across town.

## Play on any surface

Point your phone at a table. A glowing outline finds the surface. Tap, and a polished wooden board with all 32 pieces settles into place, anchored to the real world. Walk around it. Lean in. Look at the position from your opponent's side. It stays exactly where you put it.

## Touch the pieces

Tap a piece and its legal moves light up right on the board. Tap a square and the piece glides there. Captures, castling, en passant, promotion: every rule of chess, enforced perfectly, so you only ever think about the game.

## A grandmaster coach on the table

Stuck? Tap **Hint**. A world-class engine reads the position and draws its best move as a glowing arrow across the board itself, with an evaluation of who's winning. Turn on threat view to see which of your pieces are under attack before it's too late.

## Challenge a friend, anywhere

Create a game and share a six-character code. Your friend joins from their own phone, places their own board on their own table, and you play live, each from your own side of the board. Pick a time control, watch the clocks tick down, offer a draw, resign with dignity, or demand a rematch.

## Built for the moment

- Instant setup: open the app, find a table, play. No accounts, no menus to wade through.
- Mistakes forgiven: undo and reset in casual play.
- Full move history at a glance.
- No AR on your phone? Play the same board in 3D.

**AirChess. Chess, wherever you are.**

## Pointing the app at a server

The default server URL is `ws://localhost:8080/ws`, which reaches a server on your computer after `adb reverse tcp:8080 tcp:8080`. To bake in another one, set `airchess.serverUrl` in `local.properties` or pass `-Pairchess.serverUrl=ws://192.168.1.20:8080/ws`. The URL can also be edited in the Play online sheet.

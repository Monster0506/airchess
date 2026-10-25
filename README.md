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

The default server URL is `ws://localhost:8080/ws`, which reaches a server on your computer after `adb reverse tcp:8080 tcp:8080`. To bake in a hosted server, set `airchess.serverUrl=wss://your.host/ws` in `local.properties` (gitignored) or pass `-Pairchess.serverUrl=...`. The URL can also be edited in the Play online sheet.

---

## Features

- Tabletop AR chess: ARCore plane detection places a board on a real table and anchors it there.
- Complete chess rules: castling, en passant, promotion, check, checkmate, stalemate, fifty-move rule, threefold repetition, insufficient material.
- Tap a piece to select it. Legal moves show as dots, captures as rings. Tap a destination to move.
- Animated moves.
- Undo and reset (local games).
- Move history in SAN.
- Stockfish 19 hints drawn as an arrow on the board, with an evaluation. Disabled during live online games.
- Threatened-squares view.
- Online multiplayer:
  - Private games joined by 6-character codes.
  - Server-authoritative clocks: 1+0, 3+2, 5+0, 10+0, 15+10.
  - Resign, draw offers, and rematch with color swap.
  - Reconnect within 60 seconds.
- 3D fallback for devices without ARCore.
- Debug overlay: tracking state, plane count, FPS, anchor, FEN.

## Tech stack

- Kotlin 2.4, Jetpack Compose, Material 3
- ARCore, SceneView 4.62 (ARSceneView), Filament (via SceneView), glTF/GLB models
- Kotlin coroutines and StateFlow
- Stockfish 19 over UCI
- Ktor 3 WebSockets (client and server)
- kotlinx.serialization

## Architecture

```text
 AR / 3D rendering (app/ar)        Compose overlay (app/ui)
            \                         /
             v                       v
                 ChessViewModel (StateFlow<GameUiState>)
                /        |                \
               v         v                 v
     ChessGame (:chess)  Stockfish (UCI)   OnlineGameClient (Ktor WS)
                                                    |
                                                    v
                                           AirChess server (:server)
                                           validates with :chess
```

Game state lives in `ChessGame`. The AR layer renders an immutable `BoardViewState` and reports tapped squares; it never decides legality. A `PieceReconciler` diffs successive positions to animate moves. The server re-validates every move with the same `:chess` module.

| Module | Contents |
|---|---|
| `:chess` | Pure Kotlin rules, FEN, SAN |
| `:protocol` | Wire messages |
| `:server` | Ktor server |
| `:app` | Android app: `ar/`, `ui/`, `viewmodel/`, `engine/`, `net/`, `model/`, `data/` |

## Requirements

- Android Studio Otter or newer (AGP 9.4).
- JDK 17+ for Gradle. Android Studio's bundled JBR works.
- Android SDK 37 platform installed.
- ARCore-supported phone running Android 8.0 (API 26)+ with Google Play Services for AR. arm64 is required for Stockfish hints.
- Network access on first build: Gradle downloads the pinned Stockfish 19 release (~80 MB) into `.cache/stockfish/` and verifies its SHA-256.

## Build and install

```sh
sh ./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
sh ./gradlew test                     # all unit tests (chess, protocol, server, app)
```

On Windows use `gradlew.bat`. Release: `sh ./gradlew assembleRelease`. Local releases use the demo debug key unless `AIRCHESS_KEYSTORE_FILE` and the matching signing environment variables are set.

## Android downloads

[Download the latest APK](https://github.com/Monster0506/airchess/releases/latest/download/airchess.apk), or use **Download Android app** on the website. No GitHub login is required. Android may ask you to allow installation from your browser.

`.github/workflows/android-release.yml` tests and builds every push to `main`, including merged pull requests. It publishes a signed APK and `SHA256SUMS` as a public GitHub Release only after all checks pass. Failed builds leave the previous download available. Older builds remain in [Releases](https://github.com/Monster0506/airchess/releases).

The workflow uses standard Linux runners on this public repository, with no Actions artifact storage or paid services. It skips private repositories. The workflow can also be started manually on `main`.

Repository secrets:

| Secret | Value |
|---|---|
| `AIRCHESS_SERVER_URL` | Hosted `wss://` chess server URL |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded signing keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

Keep the signing key unchanged so installed apps can update. CI uses the existing demo signing certificate. Keep a backup of that keystore outside Git; losing it prevents compatible updates. CI increments the version code with each workflow run and never commits the server URL or signing key.

## Multiplayer server

```sh
sh ./gradlew :server:run              # listens on 0.0.0.0:8080, prints ws:// LAN URLs
```

Point the app at it: Online -> Server URL (e.g. `ws://192.168.1.20:8080/ws`), or bake a default via `airchess.serverUrl=ws://...` in `local.properties`. For a USB-connected phone, run `adb reverse tcp:8080 tcp:8080` and use `ws://localhost:8080/ws` (the default). Deployment details: see [server/README.md](server/README.md).

## Controls

- **Placement:** move the phone slowly over a table until the outline appears, then tap to place. Reposition returns to placement.
- **Play:** tap a piece to select it (only the side to move, or your color online). Dots mark legal moves and rings mark captures. Tap a destination to move. Promotion opens a picker.
- **Buttons:**
  - Undo
  - Reset (local only)
  - Resign / Draw (online)
  - Reposition
  - Hint
  - History
  - Settings: engine depth 8-20, legal-move dots, threatened squares, haptics, debug overlay
  - Online: create or join with a code. Share sends the code.

## Testing

- `:chess`: rules, including perft.
- `:protocol`: codec.
- `:server`: rooms, clocks, WebSocket integration.
- `:app`: view model, UCI parsing, board geometry, piece reconciliation.

## Assets and licenses

See [ATTRIBUTIONS.md](ATTRIBUTIONS.md) for full details.

- Piece and board GLBs are generated by `tools/models/generate_models.py` (CC0).
- Stockfish 19 is GPLv3. It ships unmodified as a separate executable and is spoken to over UCI.
- SceneView is Apache 2.0.
- The ARCore SDK is provided under Google's terms.

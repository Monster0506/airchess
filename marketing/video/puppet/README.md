# AirChess video puppet

`puppet.mjs` drives one side of an online game in the AirChess web client through a visible browser window, so the other side (the phone) can be filmed next to it. It plays its colour's moves from a UCI move list, offers or declines draws, offers or accepts rematches, and turns on view sharing. It logs every ply.

Files:
- `puppet.mjs`: the CLI. Run `node puppet.mjs --help` for every option.
- `moves/legal-mate.txt`: the 13-ply Légal's Mate line from `../moves.md`. Ply 13 is `Nd5#`.
- `package.json`: depends on `puppeteer-core` only. It drives the Microsoft Edge already on the machine; no browser download.

## Setup

```
cd marketing/video/puppet
npm install
```

Edge is used by default (`C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe`). For another browser, pass `--browser <exe>`.

## Take A (kitchen): phone is Alex (White), browser is Sam (Black)

1. Phone (Alex, White) creates the room and reads out the code.
2. Browser:
   ```
   node puppet.mjs --server <your server URL> --name Sam --join <CODE> --moves moves/legal-mate.txt --offer-draw-after 8 --accept-rematch --share --open-stage --record ../assets/phone/take-a-alex-view.webm
   ```
3. Phone plays the White column of `../moves.md`. Once the game starts, tap **Share view** on the phone too.

The browser offers a draw right after 4...g6 (ply 8), and the phone declines it. After checkmate the phone offers a rematch, and the browser accepts it. With `--record`, the browser saves the phone's shared view (Alex's clean AR view: camera plus board, no app buttons) to the file as it arrives, for the Share view composite. No OBS needed.

## Take B (desk): browser is Alex (White), phone is Sam (Black)

1. Browser:
   ```
   node puppet.mjs --server <your server URL> --name Alex --create --color WHITE --moves moves/legal-mate.txt --decline-draws --offer-rematch --share --open-stage --record ../assets/phone/take-b-sam-view.webm
   ```
   The room code prints in a banner. Read it to the phone.
2. Phone (Sam, Black) joins with that code, places the board on the desk, taps **Share view**, and plays the Black column of `../moves.md`, tapping **Draw** right after 4...g6.

The browser declines the draw and mates the phone's Black. After checkmate it offers a rematch, which the phone accepts. The recording is Sam's clean AR view, used full screen in the Share view composite.

Both takes use the 5+0 time control (`300,0`, the default) and a 3-second delay before each of the puppet's moves.

## Options

| Option | Meaning |
|---|---|
| `--server <url>` | Your server's base URL. The web client is at `<url>/play/`. |
| `--create` / `--join <CODE>` | Create a room, or join one. |
| `--name <name>` | Display name. Defaults to `Alex` for `--create` and `Sam` for `--join`. |
| `--color WHITE\|BLACK\|RANDOM` | Colour requested on create (default `RANDOM`). |
| `--time <init,inc>` | `60,0`, `180,2`, `300,0` (default), `600,0`, `900,10`. |
| `--moves <file>` | UCI move list. The puppet plays its colour's plies from it. |
| `--delay <ms>` | Pause before each of our moves (default 3000). |
| `--offer-draw-after <n>` | Offer a draw right after our move that makes ply `n`. |
| `--decline-draws` | Decline draw offers. |
| `--offer-rematch` | Offer a rematch once the game is over. |
| `--accept-rematch` | Accept a rematch the opponent offers. |
| `--share` | Turn on "Share view" once the game starts. |
| `--open-stage` | Open the opponent's shared view full screen when it arrives. |
| `--record <file.webm>` | Save the opponent's shared view stream to a file (VP9/VP8 WebM, about 8 Mbit/s). Convert with `ffmpeg -i in.webm -vf fps=30 -c:v libx264 -crf 18 out.mp4`. |
| `--headless` | No visible window (for tests). |
| `--browser <exe>` | Browser executable (default Edge). |
| `--width <px>` / `--height <px>` | Window size (default 1600 x 1000). |

Stop with Ctrl+C. The puppet closes its browser and deletes its temporary profile (`%TEMP%/airchess-puppet-*`). A run killed harder than Ctrl+C can leave that folder behind; delete it by hand.

## Logs

Each line has a timestamp and a tag: `INFO`, `PLAY` (our ply), `SEEN` (opponent's ply), `DRAW`, `REMATCH`, `SHARE`, `STAGE`, `GAME`, `WARN`, `ERROR`. A room code prints in a banner on create.

If the board and the move list disagree, the puppet logs `MISMATCH` and pauses playback until the board matches again. A move that doesn't register is retried once.

## Caveats

- The web client in this repo has no `window.airchess` hook. The puppet clicks `[data-square]` cells directly and reads the move history from the `#moves` panel (SAN), converting it to UCI with the web client's own `chess.js` at `server/src/main/resources/web/vendor/chess.js/chess.js`. If a `window.airchess` hook is added later, the puppet uses `selectSquare`/`getHistory` automatically. The chess.js import is a relative path into `server/`, so keep this `puppet/` folder inside the repo.
- Promotion is handled by clicking `[data-piece]` in the picker.
- Game-over and rematch logs come from the web client's `#gameOver` modal and `#rematch` button, so the wording follows the web client.
- If the browser window is closed by hand, the puppet exits.
- `node_modules` is gitignored; run `npm install` after cloning.

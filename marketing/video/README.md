# AirChess launch video: production kit

- Script and playbook: [`../video-script.md`](../video-script.md)
- Move sheet (what to play): [`moves.md`](moves.md)
- Browser opponent automation: [`puppet/README.md`](puppet/README.md)
- Built media: `assets/` (gitignored; each folder has its own README)

## What is already built

| Folder | Contents | Script use |
|---|---|---|
| `assets/browser/` | Web client recordings, 1920x1080 30 fps: lobby, 2D to 3D, vs computer (Hard, 3D), hint (Stockfish suggests **Nxe5, +2.53**, with threat view), full Légal's Mate in 3D, 3D board orbit; stills of the 3D board | 0:35 (web alt), 2:10, end card |
| `assets/emulator/` | Android UI cutaways (no AR): onboarding, home, computer dialog, online create and share sheet, settings | Menu cutaways at 0:50, 1:05, 1:58 |
| `assets/graphics/` | End cards (16:9, 9:16), 8 title cards in both aspects, split-screen frame, Share view overlay and preview mask, phone frame, dramatization note, invite text bubble, 3D board render | Throughout |
| `assets/captions/` | `captions-16x9.srt` (full), `captions-9x16.srt` (45 s cutdown) | Burn in |
| `assets/vo/` | Scratch voiceover, Windows TTS: one WAV per line plus `vo-scratch-full.wav` laid out on the 2:40 timeline | Edit to it; replace with a human read |
| `assets/music/` | Three candidate tracks with build/peak notes | Bed |
| `assets/stock/` | 8 Pexels clips: golden-hour neighborhood and city, kitchen table, home desk, café table, hand with phone, park bench | 1:05 "across town" transition, 2:22 montage |
| `puppet/` | Plays the offscreen opponent in the browser for takes A and B, offers/declines the draw, rematch, opens the Share view stage | Takes A and B |

## What you still record (Pixel + laptop)

Use the Pixel's built-in screen recorder unless noted. Do Not Disturb on.

| # | Shot | Script | Where | Notes |
|---|---|---|---|---|
| 1 | Cold open: sweep, outline locks, tap, board settles (several takes) | 0:00-0:12 | Kitchen | Hero shot. Slow, steady. |
| 2 | Walk around the placed board | 0:12-0:22 | Kitchen | Laptop webcam wide (OBS) **and** Pixel recording at the same time |
| 3 | Lean in close to the pieces | 0:12-0:22 | Kitchen | |
| 4 | Touch the pieces, castling, capture | 0:22-0:35 | Kitchen | Play on the table; moves in `moves.md` |
| 5 | Promotion with the picker | 0:30 | Kitchen | After Reset; `moves.md` |
| 6 | Hint arrow, then threat view | 0:35-0:50 | Kitchen | After Reset; `moves.md`. Expect Nxe5 |
| 7 | Computer game, Black, Hard, blunder, Undo | 0:50-1:05 | Kitchen | Improvise; see `moves.md` |
| 8 | Take A: create 5+0 White, Share invite, play White, decline draw, mate, rematch | 1:05-2:10 | Kitchen | Puppet joins as Sam. OBS records the laptop's full-screen stage (Alex's clean view). Screenshot the phone's Share view screen |
| 9 | Take B: join by code as Black, place board, play Black, offer draw, get mated | 1:05-2:10 | Desk, other room | Puppet creates as Alex. OBS records the stage (Sam's clean view) |
| 10 | Close the app mid-game, reopen, Resume game | 1:58-2:10 | Either | |
| 11 | Montage: 5-10 s of placement on 5+ surfaces | 2:22-2:32 | Desk, coffee table, floor, windowsill, outdoors | |

Optional: a real phone "message received" shot instead of `invite-bubble.png`.

## Building the composites in the edit

- **Split screen (1:20):** `split-screen-frame-16x9.png` over take A (left) and take B (right). Cut both takes on the same ply.
- **Share view (1:30):** take B's OBS stage recording full screen inside `share-view-overlay.png`, take A's OBS stage recording in the corner through `share-view-preview-mask.png`. Check placement against your Share view screenshot from take A.
- Add `dramatization-note.png` during both.

## Credits and licenses for the published video

- **Music:** the two Alexander Nakarada tracks are CC BY 4.0. If you use one, put its attribution line from `assets/music/LICENSES.md` in the video description. "Isolation Waltz" is CC0 and needs nothing.
- **Stock:** Pexels License; no attribution required (creators listed in `assets/stock/LICENSES.md`).
- **Stockfish** is named on screen; no extra notice needed in the video.

## Known caveats

- **Server URL on screen:** the Play online sheet and Settings show the hosted server address. Blur that field in any shot that shows it (emulator `online-create`, `online-join`, `settings`, and the lobby in takes A and B).
- **Emulator clips are UI only:** no AR and no Stockfish there (no x86 engine), so the board behind sheets is 3D. In `online-join.mp4` the name field reads "Alex"; use it only for the code-typing close-up, or re-shoot the join on the Pixel in take B.
- **`stage-template.png`** is an earlier real Share view screenshot, not a fresh capture. Replace it with the one you take in take A.
- **`invite-bubble.png`** shows code K7Q2MD, which won't match the real code in take B. Keep it on screen briefly, before the cut to the phone.
- **`home-scroll.mp4`** is a still hold, not a real scroll.

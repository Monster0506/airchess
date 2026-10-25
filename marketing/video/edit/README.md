# AirChess launch video: edit

Build: `marketing/video/edit/build.py`. Output: `marketing/video/assets/edit/airchess-launch-16x9.mp4` (1920x1080, 30 fps, H.264 + AAC 48 kHz). Picture is 136.70 s. Final mix length and loudness are recorded after the VO mix runs (see the VO section).

## Rebuild

Run from `marketing/video/edit/`:

1. `python build.py picture`: renders every scene to `assets/edit/work/seg/`, writes `airchess-launch-16x9-picture.mp4` (music-free picture), `airchess-launch-16x9-clean.mp4`, and `captions-16x9.srt`.
2. `python build.py cues`: writes `vo-cues.json` (13 VO cues: id, scene, start, slot, measured need).
3. `python build.py mix --vo-dir <absolute path>/marketing/video/assets/vo/kokoro`: places the 13 VO lines, ducks music under VO, loudnorm two-pass to -16 LUFS, writes the final mp4. The mix does not re-render picture. The `--vo-dir` path must be absolute.

Timings live in the data structures at the top of `build.py`. Segment cache keys include the source clip list. If a render fails mid-write, delete partial new segments in `assets/edit/work/seg/` before retrying.

Tools: ffmpeg/ffprobe on PATH, Python 3 with PIL. Fonts: `C:/Windows/Fonts/segoeui.ttf` and `segoeuib.ttf`, passed explicitly.

## Scene table (picture, 136.70 s)

| # | Scene | Start | Dur | Source | On-screen text / notes |
|---|---|---|---|---|---|
| 1 | cold open | 0.0 | 10.6 | selects/shot01 | "Your table is the board." |
| 2 | walk | 10.6 | 5.0 | shot02 | "Walk around it. Lean in." |
| 3 | lean | 15.6 | 4.5 | shot03 | "Walk around it. Lean in." (continues) |
| 4 | castle | 20.1 | 4.0 | shot04-touch-castle-take1 | "Tap a piece. Its moves light up." |
| 5 | capture | 24.1 | 3.5 | capture | "Tap a square. It glides there." |
| 6 | promo | 27.6 | 4.5 | shot05-promotion-take2 | "Every rule is handled for you." |
| 7 | hint | 32.1 | 11.0 | shot06-hint-take3 | "A coach on your table." / "Stuck? Tap Hint. Stockfish draws its best move." |
| 8 | computer | 43.1 | 12.0 | shot07-computer-undo-take1 (clean) | "Play the computer." / "Pick a side and a level." / "Made a mistake? Undo it." |
| 9 | across town | 55.1 | 3.5 | stock golden-hour 01 | "Across town." |
| 10 | invite | 58.6 | 7.0 | take A raw 0–7 s (name patched) | "Chess is better with a friend." / "Create a game. Share the code." |
| 11 | join | 65.6 | 8.0 | take B raw join (name patched) | "Join with the code." / "Sam plays Black." |
| 12 | split | 73.6 | 7.2 | take A raw 20.5 s + take B raw 11 s | "Two tables, one game." / "Same game, same move." Dramatization note. |
| 13 | share view | 80.8 | 10.0 | take-b-sam-view + take-a-alex-view corner | "See their board, not just their moves." Dramatization note. |
| 14 | privacy | 90.8 | 6.5 | share-24 composite | "Their room. Their table. Their move." / "Off until you turn it on." / "Nothing is recorded." |
| 15 | decline | 97.3 | 3.0 | take A raw 57.8 s (name patched) | "Draw offered. Declined." |
| 16 | resume | 100.3 | 3.6 | raw 015822 (home screen, Resume card) | "Pick up where you left off." |
| 17 | mate | 103.9 | 5.0 | take A raw 81.0 s (name patched) | "Checkmate." |
| 18 | rematch | 108.9 | 5.0 | take B raw 105.5 s (name patched) | "Rematch. Colors swap." |
| 19 | browser | 113.9 | 9.0 | browser-2d-to-3d 4.0 s + browser-vs-computer 1.0 s, in a window frame | "Or in your browser." / "2D or 3D." / "Stockfish replies in 3D." |
| 20 | orbit | 122.9 | 4.0 | board-3d-orbit 0–4 s | "Every piece, in 3D." |
| 21 | montage | 126.9 | 4.2 | shot02 1.0 + 9.0 s, take-a-alex-view 5 s, take-b-sam-view 25 s; pillarboxed over blurred copy | "Any table. Any friend. Any time." |
| 22 | end card | 131.1 | 5.6 | graphics/end-card-16x9.png | AirChess, chess wherever you are, server URL |

## VO (Kokoro, male voice regeneration in progress)

Files: `assets/vo/kokoro/vo-01..13.wav` and `durations.json`. The voice is being regenerated as male (am_*) by a sibling; line lengths will change. Cue slots in `build.py` are set for the female af_heart durations: 01 4.6, 02 9.4, 03 11.6, 04 10.6, 05 11.6, 06 6.6, 07 7.1, 08 9.6, 09 6.1, 10 12.5, 11 8.8, 12 3.8 (montage), 13 5.0 (end). Cue 12 moved from orbit to montage; cue 13 slot shortened to fit the 5.6 s end card. Before the final mix, re-run `build.py cues` against the new `durations.json`, check for "TOO LONG" flags, and then run `mix`.

## Music and attribution

Music bed: `assets/music/brothers-unite_nakarada_CC-BY-4.0.mp3`. Fade in 0–3 s, lift at 0:50, fade out over the last 3 s, ending on the end card.

Attribution for the video description:

> Music: "Brothers Unite" by Alexander Nakarada (https://www.serpentsoundstudios.com). Licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Source: https://alexandernakarada.bandcamp.com/track/brothers-unite-2

## Pass notes

Pass 1: split layout; music bed; two-pass loudnorm.

Pass 2: scene cache key includes a hash of the clip list; removed black flashes; caption timing 1.2 s per 10 characters, minimum 1.5 s.

Pass 3: name patch on raw takes A/B only; lobby and vs scenes removed; orbit lead-in 4 s; computer undo caption; single-phone and split layouts as described in the scene table.

Pass 4 (Main's final-pass fixes):
1. Browser crop: the window now shows the full board, controls row, right panel ("Black to move", Moves list, Home). Crop `1160:860:380:100` scaled to 1000x742 inside a 1000x782 window at (880,149). Verified at 118.0 s.
2. Resume cutaway: `screen-20261025-015822` home screen with the "Resume game" card, inserted after decline (3.6 s, caption "Pick up where you left off.").
3. Montage: new pillarboxed montage of real board clips (4.2 s) before the end card, with the VO 12 line. Verified at 127.5 and 129.5 s.
4. End card shortened from 10.8 s to 5.6 s. Music fade-out shortened from 8 s to 3 s.

## Checks on the picture (136.70 s)

- H.264 1920x1080, 30 fps.
- blackdetect (0.2 s, 98%): no hits.
- freezedetect (1.5 s): 43.1–45.7 (computer menu), 65.9–68.1 (join), 100.5–103.7 (resume card, static home screen with caption on), 114.4–118.4 (browser board), 118.9–122.7 (browser board, static). End card from 131.1.
- Frames verified: `assets/edit/review/final3_101.5.png`, `final3_118.0.png`, `final3_127.5.png`, `final3_129.5.png`, `final3_134.0.png`.

## Caveats

- Montage cuts are on a fixed 1 s grid, not matched to music beats.
- Browser beat has a static 3.8 s hold (118.9–122.7) on the board while the caption reads; consider trimming it if the VO timing allows.
- Resume card holds static for about 3 s under its caption.
- Split sync is approximate after ply 1. Both takes are aligned on visual anchors, not wall-clock logs.
- Name patch is a static overlay; it does not track motion.
- Single-phone scenes have a flat dark-green backdrop (the montage uses a blurred backdrop).
- Caption cues are close to the 1.5 s minimum in places; timings are in `captions-16x9.srt`.

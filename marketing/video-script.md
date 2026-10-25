# AirChess launch video: script and playbook

Draft 1. Target runtime 2:40 (hard cap 3:00). Master in 16:9 at 1920x1080; a 9:16 cutdown follows from the same footage.

## Concept

**"Chess, wherever you are."** One continuous story: a player sets a board on their kitchen table, learns, beats the computer, then invites a friend across town and plays them live, each on their own table, each able to see the other's board. The hero shot is always the board sitting on a real surface.

- Tone: warm, confident, a little playful. Not techy. No jargon (no "ARCore", "WebRTC", "TURN").
- Music: one royalty-free track that builds. Quiet open, lift at the friend invite (1:05), peak at Share view (1:30), resolve on the end card.
- Voiceover (VO): one calm voice, about 220 words, paced well under the runtime so music and board moments breathe. Every line is also an on-screen caption, so the video works muted.

## Script

| Time | Picture | VO | On-screen text |
|---|---|---|---|
| 0:00-0:06 | Cold open. Phone (screen POV) sweeps slowly over a wooden kitchen table. The placement outline appears and locks on. A finger taps. | None. Music in. | |
| 0:06-0:12 | The board and all 32 pieces settle onto the table. Hold. | "Your table is the board." | **Your table is the board.** |
| 0:12-0:22 | Live action: the player walks around the table, phone up; the board stays anchored. Cut to screen POV leaning in close to the pieces. | "AirChess puts a full-size chess set on any table, desk, or floor. Walk around it. Lean in. It stays right where you put it." | |
| 0:22-0:35 | Screen POV: tap a knight, legal-move dots light up, the piece glides. A capture. Castling. A pawn promotes; the picker opens; choose a queen. | "Tap a piece and its moves light up. Tap a square and it glides there. Every rule is handled for you, so all you think about is the game." | |
| 0:35-0:50 | Screen POV: tap **Hint**; a glowing arrow appears across the board with an evaluation. Toggle threat view; attacked pieces glow. | "Stuck? Tap Hint. Stockfish, one of the strongest chess engines ever built, draws its best move right across your board, and shows you what's under attack." | **A coach on your table** |
| 0:50-1:05 | Home screen, **Play the computer**: pick Black, Hard. Stockfish replies on the board. A blunder; tap **Undo**; the round rewinds. | "Want a challenge? Pick a side, pick a level, and take on the computer. Made a mistake? Undo it and try again." | Easy · Medium · Hard · Max |
| 1:05-1:20 | Music lifts. **Play a friend online**: choose 5+0, White. Tap **Share invite**; the text message sends. Cut to a second person across town; they tap the link; AirChess opens straight into the game. | "But chess is better with a friend. Create a game, send an invite, and one tap brings them in, from anywhere." | **Play a friend, anywhere** |
| 1:20-1:30 | Split screen: two tables in two homes, two boards placing. Clocks start ticking. | "You each play on your own table, from your own side of the board, live, with real clocks." | 1+0 to 15+10 |
| 1:30-1:50 | Music peaks. Tap **Share view**. The friend's room, table, and board appear in the corner. Tap it: full screen, with your own view tucked in the corner, like a video call. Friend makes a move; it lands on both boards. | "And with Share view, you see their board exactly as they do. Their room. Their table. Their move." | **See their board, not just their moves** |
| 1:50-1:58 | Over the full-screen share view. | "Sharing is off until you turn it on, nothing is recorded, and there's no microphone." | Off until you turn it on · Never recorded · No mic |
| 1:58-2:10 | Draw offer pops up; declined. Checkmate. **Rematch**; colors swap. Quick cut: the app closed mid-game, reopened, **Resume game**. | "Offer a draw. Demand a rematch. Close the app mid-game, and your game is waiting when you come back." | |
| 2:10-2:22 | A laptop browser: the same game in 2D, then flip to 3D, playing against the phone. | "No app? No problem. Your friend can join right from a web browser." | **Play in any browser** |
| 2:22-2:32 | Fast montage, about 1 second each: a desk, a park bench, a coffee table, a floor, a café, each with a board placed on it. | "Any table. Any friend. Any time." | |
| 2:32-2:40 | End card: app icon, board render, download link. Music resolves. | "AirChess. Chess, wherever you are." | **AirChess** · Chess, wherever you are. · Free for Android · No account · No ads · [download link] |

## 9:16 cutdown (about 45 seconds)

Cold open (0:00-0:12), the hint arrow (0:35-0:42), the invite tap (1:10-1:20), Share view going full screen (1:30-1:45), montage (2:22-2:32), end card. Captions do the work; the VO is optional.

## Playbook

### Footage sources

| Type | What | How |
|---|---|---|
| Screen POV | Every in-app shot, including AR (camera plus board) | Android's built-in screen recorder (or `adb shell screenrecord`) at 1080p while someone moves the phone |
| Live action | Walking around the table, the friend receiving the invite, split-screen homes, montage locations | A second phone or camera, steady, landscape |
| Browser | The web client in 2D and 3D | Any browser screen recorder |
| Graphics | Captions, end card, split-screen layout | Brand colors and a board render |

### Before shooting

1. Clean demo state: fresh install, player named "Alex" (friend: "Sam"), haptics on, no debug overlay, light theme or dark theme chosen and used consistently.
2. Rehearsed move sequences so hero moves happen on cue: a castle, a promotion, a clean checkmate, and a position where the hint arrow is dramatic. The app has no position editor, so write each sequence down from the starting position.
3. Two locations for the friend scenes; each needs a table with visible wood grain or texture so AR locks on fast.
4. Good, even light. AR tracking and the look both suffer in dim rooms.
5. Notifications silenced on every device (Do Not Disturb), status bar clean, battery and signal looking full.

### Shot list order (efficient capture)

1. All single-player screen POV shots (0:00-1:05) at the kitchen table, in one session.
2. Online session with two phones: invite, both placements, clocks, Share view, draw, checkmate, rematch, resume.
3. Browser session against a phone.
4. Live-action coverage of the same moments.
5. Montage locations, 5-10 seconds of placement at each.

### Post-production

- Edit with ffmpeg or any NLE. Cut on piece landings and music beats.
- Captions burned in, readable on a phone: sans-serif, at least 48 px at 1080p, safe margins.
- Blur or crop anything personal in live-action and AR shots (faces, addresses, screens, mail on the table).
- Exports: 16:9 1080p master (YouTube, website) and 9:16 1080x1920 cutdown (Reels, TikTok, Shorts), H.264, AAC, under 3:00.

### Claims check

Every line above matches shipping features. Do not add: ratings, user counts, iOS, "AI coach", voice or video chat. Stockfish is credited by name, so its GPL attribution stays in the app and repo, not in the video.

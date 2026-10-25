# AirChess launch video: script and playbook

Draft 3. Target runtime 2:40 (hard cap 3:00). Master in 16:9 at 1920x1080; a 9:16 cutdown follows from the same footage.

**Production constraint:** one person, one phone (Pixel), one laptop, two locations filmed one after the other. The video stages two players on two phones by shooting the same rehearsed game twice, once at each table, then cutting the two takes together. Every frame is a real app recording; only the side-by-side and Share view layouts are assembled in the edit.

## Concept

**"Chess, wherever you are."** Alex sets a board on the kitchen table, learns, beats the computer, then invites Sam across town. Sam places a board on a desk in another room, and they play live, each on their own table. With Share view, each sees the other's real room and board. The hero shot is always the board sitting on a real surface.

- Tone: warm, confident, a little playful. Not techy. No jargon (no "ARCore", "WebRTC", "TURN").
- Music: one royalty-free track that builds. Quiet open, lift at the invite (1:05), peak at Share view (1:30), resolve on the end card.
- Voiceover (VO): one calm voice, about 220 words. Every line is also an on-screen caption, so the video works muted.
- Cast: you play both players. Alex is the kitchen table, Sam is the desk across town. Only hands and phone views appear, so no faces are needed and one person can play both.

## Script

| Time | Picture | Source | VO | On-screen text |
|---|---|---|---|---|
| 0:00-0:06 | Cold open. Screen POV sweeps slowly over a wooden kitchen table. The placement outline locks on. A tap. | Pixel recording | None. Music in. | |
| 0:06-0:12 | The board and all 32 pieces settle onto the table. Hold. | Pixel recording | "Your table is the board." | **Your table is the board.** |
| 0:12-0:22 | Screen POV: walk a slow circle around the table; the board stays put and shows itself from every side. Then lean in close to the pieces. | Pixel recording | "AirChess puts a full-size chess set on any table, desk, or floor. Walk around it. Lean in. It stays right where you put it." | |
| 0:22-0:35 | Tap a knight; legal-move dots light up; the piece glides. A capture. Castling. A pawn promotes; the picker opens; choose a queen. | Pixel recording | "Tap a piece and its moves light up. Tap a square and it glides there. Every rule is handled for you, so all you think about is the game." | |
| 0:35-0:50 | Tap **Hint**: a glowing arrow appears across the board with an evaluation. Toggle threat view; attacked pieces glow. | Pixel recording | "Stuck? Tap Hint. Stockfish, one of the strongest chess engines ever built, draws its best move right across your board, and shows you what's under attack." | **A coach on your table** |
| 0:50-1:05 | Home screen, **Play the computer**: pick Black, Hard. Stockfish replies on the board. A blunder; tap **Undo**; the round rewinds. | Pixel recording | "Want a challenge? Pick a side, pick a level, and take on the computer. Made a mistake? Undo it and try again." | Easy · Medium · Hard · Max |
| 1:05-1:20 | Music lifts. Alex: **Play a friend online**, 5+0, White; the six-character code appears and Alex taps **Share invite**. Cut to Sam's desk: Sam opens AirChess, taps **Join**, types the code, and places a board on the desk. | Pixel, take A, then take B | "Chess is better with a friend. Create a game, share the code, and they're in, from anywhere." | **Play a friend, anywhere** |
| 1:20-1:30 | Split screen: Alex's kitchen table, left; Sam's desk, right, seen from Black's side. The same moves land on both boards. Clocks tick. | Takes A and B, cut on matching moves | "You each play on your own table, from your own side of the board, live, with real clocks." | 1+0 to 15+10 |
| 1:30-1:50 | Music peaks. Alex taps **Share view**. Sam's desk, room, and board fill Alex's screen, with Alex's own kitchen view tucked in the corner. Sam moves; Alex watches it land on Sam's desk. Alex taps the corner to return to the board. | Composite: take B's clean AR stream full screen, take A's in the corner, real app buttons | "And with Share view, you see their board exactly as they do. Their room. Their table. Their move." | **See their board, not just their moves** |
| 1:50-1:58 | Hold on the full-screen shared view. | Composite (as above) | "Sharing is off until you turn it on, nothing is recorded, and there's no microphone." | Off until you turn it on · Never recorded · No mic |
| 1:58-2:10 | "Opponent offers a draw" pops up on the Pixel; declined. Checkmate. **Rematch**; colors swap. Quick cut: the app is closed mid-game, reopened, **Resume game**. | Pixel, takes A and B | "Offer a draw. Demand a rematch. Close the app mid-game, and your game is waiting when you come back." | |
| 2:10-2:22 | The laptop browser on its own: a 2D board flips to 3D; Stockfish answers a move. | Laptop screen | "No phone handy? Play right in your browser, in 2D or 3D, against a friend or the computer." | **Play in any browser** |
| 2:22-2:32 | Fast montage, about 1 second each: a desk, a coffee table, the floor, a windowsill, a park bench, each with a board placed on it. | Pixel recordings | "Any table. Any friend. Any time." | |
| 2:32-2:40 | End card: app icon, board render, download link. Music resolves. | Built graphics | "AirChess. Chess, wherever you are." | **AirChess** · Chess, wherever you are. · Free for Android · No account · No ads · [download link] |

## 9:16 cutdown (about 45 seconds)

Cold open (0:00-0:12), the hint arrow (0:35-0:42), Sam joining (1:10-1:20), Share view filling the screen (1:30-1:45), montage (2:22-2:32), end card. Captions do the work; the VO is optional.

## Playbook

### Your kit and what each piece does

| Device | Role | Records with |
|---|---|---|
| Pixel | The on-camera player: Alex in take A, Sam in take B | Android's built-in screen recorder (no time limit) |
| Laptop browser | The offscreen opponent in each take, playing the other player's moves from the move list; also the browser beat at 2:10 | OBS Studio (free) |

### The two-take method

Each take is a real online game between the Pixel and the laptop browser. The browser is a stand-in: you play the other player's moves on it from a written move list, and it never appears on screen.

| | Take A: kitchen table | Take B: desk, another room |
|---|---|---|
| Pixel plays | Alex, White | Sam, Black |
| Browser plays (offscreen) | Sam's moves, Black | Alex's moves, White |
| Who creates the game | Pixel (Alex taps **Share invite**; open the link in the laptop browser) | Browser; on the Pixel, **Play a friend online**, **Join**, and type the code |
| Pixel recording gives | Alex's screens: invite, play, draw offer, checkmate, rematch | Sam's screens: joining by code, placement, play from Black's side |
| OBS recording gives | Alex's **clean AR stream**: turn on Share view on both sides; the browser's video stage shows exactly what Sam would see, with no app buttons | Sam's **clean AR stream**, the same way |

Play the identical move list in both takes, at the same steady tempo, so every cut between tables lands on the same position. The clocks won't match exactly between takes, so keep any shot with both clocks visible short.

### What is real and what is built

**Real captures:** every in-app screen, the invite opening the app, both AR boards, and both clean AR streams. Share view genuinely streams the AR view between devices; the edit only places two separately recorded streams together.

**Built from scratch in editing:**
- The **Share view screen (1:30)**: take B's clean stream full screen, take A's clean stream in the top-right corner, and the real **Your board** and **Stop sharing** buttons. Screenshot a real Share view screen on the Pixel during take A and use it as the layout template, so the size and position of the corner preview, the buttons, and the fonts match the app.
- The **split screen (1:20)**: take A left, take B right, cut on matching moves, with a divider and the two player names.
- Captions, the music bed, the end card, and the montage cuts.
- Device frames around screen recordings, so viewers read them as two phones.
- Optional: a text-message bubble showing the code arriving, before Sam types it.

Recommended: a small "Dramatization. Screens recorded separately." note during the split screen and Share view. Both features work exactly as shown, but the two sides were not live at the same moment.

### Before shooting

1. Clean demo state: fresh install, haptics on, no debug overlay. Pick light or dark theme and keep it. Set the Pixel's name to "Alex" for take A and "Sam" for take B (Settings), and the browser's name to the opposite.
2. Rehearsed move sequences so hero moves happen on cue: a castle, a promotion, a clean checkmate, a draw offer, and a position where the hint arrow is dramatic. The app has no position editor, so write each sequence down from the starting position. The online game uses one written move list for both takes.
3. Two locations that look like two different homes: the kitchen table for Alex, and a desk in another room for Sam, with different light and surroundings.
4. Good, even light. AR tracking and the look both suffer in dim rooms.
5. Do Not Disturb on the Pixel and laptop. Close other browser tabs and hide the bookmarks bar. Status bar clean, battery charged.

### Recording sessions

1. **Solo AR (kitchen table):** every shot from 0:00 to 1:05, on the Pixel's built-in recorder. Do several takes of the cold open.
2. **Walk around:** with the board placed, walk a slow circle around the table, phone up, for about 30 seconds, then lean in close to the pieces. Pixel recorder only.
3. **Take A (kitchen):** Pixel recorder running, OBS recording the browser. Create the game on the Pixel, share the invite, open it in the browser. Turn on Share view on both sides. Play the move list; offer a draw from the browser and decline it on the Pixel; deliver checkmate; rematch. Screenshot the Pixel's Share view screen for the layout template.
4. **Take B (desk):** create a game in the browser as White. On the Pixel, open **Play a friend online**, tap **Join**, type the code, and place the board on the desk. Turn on Share view on both sides. Play the same move list as Black.
5. **Resume:** close the app mid-game on the Pixel, reopen, tap **Resume game**.
6. **Browser solo:** a 2D to 3D flip and a Stockfish reply.
7. **Montage:** 5 to 10 seconds of placement at each of five or more surfaces.

### Post-production

- Edit in any video editor (DaVinci Resolve is free). Cut on piece landings and music beats.
- Captions burned in and readable on a phone: sans-serif, at least 48 px at 1080p, inside safe margins.
- Blur or crop anything personal in AR shots: mail, screens, photos, house numbers.
- Exports: 16:9 1080p master (YouTube, website) and 9:16 1080x1920 cutdown (Reels, TikTok, Shorts), H.264 and AAC, under 3:00.

### Claims check

Every line above matches shipping features. Do not add: ratings, user counts, iOS, "AI coach", voice or video chat. Label any composite that shows two views as live when they were not.

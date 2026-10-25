# Move sheet

Every line below was checked for legality with chess.js. Squares are given as "from → to" so you can tap them directly.

## Online game (takes A and B): Légal's Mate

Alex (White) sacrifices the queen and mates in 7. Time control 5+0.

| # | White (Alex) | Black (Sam) | Notes |
|---|---|---|---|
| 1 | e4 (e2 → e4) | e5 (e7 → e5) | |
| 2 | Nf3 (g1 → f3) | d6 (d7 → d6) | |
| 3 | Bc4 (f1 → c4) | Bg4 (c8 → g4) | The bishop pins the knight to the queen. |
| 4 | Nc3 (b1 → c3) | g6 (g7 → g6) | **Sam offers a draw** right after this move. Alex declines. |
| 5 | Nxe5! (f3 → e5) | Bxd1 (g4 → d1) | Alex ignores the pin; Sam grabs the queen. |
| 6 | Bxf7+ (c4 → f7) | Ke7 (e8 → e7) | Check. |
| 7 | Nd5# (c3 → d5) | | **Checkmate.** Then Alex taps **Rematch**; Sam accepts. |

- **Take A (kitchen, Pixel = Alex, White):** you play the White column on the Pixel. The browser script plays Black, offers the draw after 4...g6, and accepts the rematch.
- **Take B (desk, Pixel = Sam, Black):** you play the Black column on the Pixel and tap **Draw** right after 4...g6. The browser script plays White, declines your draw offer, and mates you.
- Same tempo in both takes: about 3 seconds per move, so the two takes cut together cleanly.

## Solo shots (Pixel, Play on the table)

Pass and play: you play both colors.

**Touch the pieces, castling, capture (0:22):**
1. e4 (e2 → e4), e5 (e7 → e5)
2. Nf3 (g1 → f3), Nc6 (b8 → c6)
3. Bc4 (f1 → c4), Bc5 (f8 → c5)
4. **O-O**: tap the king, then g1 (e1 → g1). Nf6 (g8 → f6)
5. d3 (d2 → d3), **O-O** (e8 → g8)
6. Bg5 (c1 → g5), h6 (h7 → h6)
7. **Bxf6** (g5 → f6), Qxf6 (d8 → f6): capture and recapture.

**Promotion (0:30)**, after **Reset**:
1. a4 (a2 → a4), b5 (b7 → b5)
2. axb5 (a4 → b5), a6 (a7 → a6)
3. bxa6 (b5 → a6), Bb7 (c8 → b7)
4. axb7 (a6 → b7), Nc6 (b8 → c6)
5. **bxa8=Q**: b7 → a8 takes the rook; the picker opens; choose the queen.

**Hint and threat view (0:35)**, after **Reset**:
1. e4 (e2 → e4), e5 (e7 → e5)
2. Nf3 (g1 → f3), d6 (d7 → d6)
3. Bc4 (f1 → c4), Bg4 (c8 → g4)
4. Nc3 (b1 → c3), g6 (g7 → g6)

White to move. Tap **Hint**; see the hint check below for the expected arrow. Then turn on **Show threatened squares** in Settings. This is the same position as the online game before 5.Nxe5, so the hint sets up the payoff.

## Computer game (0:50)

Stockfish's replies can't be scripted, so improvise:

1. **Play the computer**, side **Black**, difficulty **Hard**. Stockfish moves first.
2. Answer naturally for two or three moves.
3. Blunder on purpose: move your queen to a square where a pawn or piece can take it.
4. When Stockfish takes it, tap **Undo**. Your move and Stockfish's reply both disappear. That's the shot.

"""Build the launch-video SRT caption files (stdlib only; ffprobe on PATH).

Outputs (assets/captions/):
  captions-16x9.srt  every VO line at its script timecode, timed from the measured WAVs
  captions-9x16.srt  45 s cutdown, from the hand-set CUTDOWN timeline below

Run: python build-captions.py
"""
import json
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
VO_LINES = HERE.parent / "vo" / "vo-lines.json"
VO_DIR = (HERE / ".." / ".." / "assets" / "vo").resolve()
CAP_DIR = (HERE / ".." / ".." / "assets" / "captions").resolve()

LINE_WIDTH = 42     # characters per on-screen line
MAX_LINES = 2       # lines per cue
TAIL_PAD = 0.2      # seconds a final cue may outlast the spoken audio
GAP_BEFORE_NEXT = 0.1  # cue must end this long before the next VO line starts

# Hand-set 9:16 cutdown timeline (cutdown seconds). Proposal; the editor may change it.
# Cold open 0:00-0:09, hint 0:35-0:42 -> 0:09-0:16, Sam joins 1:10-1:18 -> 0:16-0:24,
# Share view 1:30-1:42 -> 0:24-0:36, montage 2:22-2:28 -> 0:36-0:42, end card 0:42-0:45.
CUTDOWN = [
    (6.0, 9.0, "Your table is the board."),
    (9.0, 12.0, "Stuck? Tap Hint."),
    (12.0, 16.0, "A coach on your table"),
    (16.0, 20.0, "Play a friend, anywhere"),
    (20.0, 24.0, "Create a game. Share the code."),
    (24.0, 30.0, "See their board, not just their moves"),
    (30.0, 36.0, "Their room. Their table. Their move."),
    (36.0, 42.0, "Any table. Any friend. Any time."),
    (42.0, 45.0, "AirChess. Chess, wherever you are."),
]
CUTDOWN_TOTAL = 45.0


def fmt_srt(seconds: float) -> str:
    ms = round(seconds * 1000)
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def wrap(words: list[str]) -> list[str]:
    """Greedy word wrap at LINE_WIDTH."""
    lines, cur = [], ""
    for w in words:
        cand = w if not cur else f"{cur} {w}"
        if len(cand) <= LINE_WIDTH or not cur:
            cur = cand
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def fits(words: list[str]) -> bool:
    return len(wrap(words)) <= MAX_LINES


def greedy_split(words: list[str]) -> list[list[str]]:
    """Fewest consecutive cues (max-fill) that each fit MAX_LINES x LINE_WIDTH."""
    cues, cur = [], []
    for w in words:
        if cur and not fits(cur + [w]):
            cues.append(cur)
            cur = [w]
        else:
            cur.append(w)
    if cur:
        cues.append(cur)
    return cues


def split_cues(text: str) -> list[list[str]]:
    """Split into the minimal cue count, with cue boundaries balanced by characters."""
    words = text.split()
    n = len(greedy_split(words))
    if n == 1:
        return [words]
    total = len(" ".join(words))
    cues, start = [], 0
    for k in range(1, n):
        target = total * k / n
        best, best_score = None, None
        for j in range(start + 1, len(words)):
            if not fits(words[start:j]):
                break
            if len(greedy_split(words[j:])) > n - k:
                continue
            chars = len(" ".join(words[:j]))
            end_mark = words[j - 1][-1]
            penalty = 0 if end_mark in ".?!" else 12 if end_mark in ",;:" else 25
            score = abs(chars - target) + penalty
            if best is None or score < best_score:
                best, best_score = j, score
        if best is None:
            return greedy_split(words)
        cues.append(words[start:best])
        start = best
    cues.append(words[start:])
    return cues


def probe_duration(path: Path) -> float:
    if not path.exists():
        raise SystemExit(f"missing {path}; run make-vo.ps1 and mix-vo.py first")
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "csv=p=0", str(path)],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
    return float(out)


def write_srt(path: Path, cues: list[tuple[float, float, str]]) -> None:
    blocks = []
    for n, (start, end, text) in enumerate(cues, start=1):
        if not start < end:
            raise SystemExit(f"{path.name}: cue {n} has start >= end")
        if n > 1 and start < cues[n - 2][1]:
            raise SystemExit(f"{path.name}: cue {n} overlaps cue {n - 1}")
        blocks.append(f"{n}\n{fmt_srt(start)} --> {fmt_srt(end)}\n"
                      + "\n".join(wrap(text.split())) + "\n")
    path.write_text("\n".join(blocks) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {path} ({len(cues)} cues)")


def build_16x9() -> None:
    lines = json.loads(VO_LINES.read_text(encoding="utf-8"))
    cues: list[tuple[float, float, str]] = []
    for idx, line in enumerate(lines):
        start = float(line["start"])
        row_end = start + float(line["slot"])
        next_start = float(lines[idx + 1]["start"]) if idx + 1 < len(lines) else row_end
        clamp = min(row_end, next_start - GAP_BEFORE_NEXT)
        spoken = probe_duration(VO_DIR / f"vo-{line['id']}.wav")

        pieces = split_cues(line["text"])
        total_chars = sum(len(" ".join(p)) for p in pieces)
        done = 0
        for k, words in enumerate(pieces):
            text = " ".join(words)
            seg_start = start + spoken * done / total_chars
            done += len(text)
            if k == len(pieces) - 1:
                seg_end = start + spoken + TAIL_PAD
            else:
                seg_end = start + spoken * done / total_chars
            seg_end = min(seg_end, clamp)
            cues.append((seg_start, seg_end, text))
    write_srt(CAP_DIR / "captions-16x9.srt", cues)


def build_9x16() -> None:
    write_srt(CAP_DIR / "captions-9x16.srt", CUTDOWN)


def main() -> None:
    CAP_DIR.mkdir(parents=True, exist_ok=True)
    build_16x9()
    build_9x16()


if __name__ == "__main__":
    main()

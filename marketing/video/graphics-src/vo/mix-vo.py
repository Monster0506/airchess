"""Place each VO WAV at its script start time on a silent 160 s base.

Reads vo-lines.json (ids, start seconds) and assets/vo/vo-NN.wav; writes
assets/vo/vo-scratch-full.wav (160.000 s, 44100 Hz, mono, 16-bit).
Requires ffmpeg on PATH. Run: python mix-vo.py
"""
import json
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
VO_DIR = (HERE / ".." / ".." / "assets" / "vo").resolve()
TOTAL_SECONDS = 160
RATE = 44100
OUT = VO_DIR / "vo-scratch-full.wav"


def main() -> None:
    VO_DIR.mkdir(parents=True, exist_ok=True)
    lines = json.loads((HERE / "vo-lines.json").read_text(encoding="utf-8"))

    cmd = ["ffmpeg", "-v", "error", "-y",
           "-f", "lavfi", "-t", str(TOTAL_SECONDS),
           "-i", f"anullsrc=r={RATE}:cl=mono"]
    chains = []
    labels = []
    for i, line in enumerate(lines, start=1):
        wav = VO_DIR / f"vo-{line['id']}.wav"
        if not wav.exists():
            raise SystemExit(f"missing {wav}; run make-vo.ps1 first")
        cmd += ["-i", str(wav)]
        delay_ms = round(float(line["start"]) * 1000)
        label = f"v{i}"
        chains.append(f"[{i}:a]adelay={delay_ms}:all=1[{label}]")
        labels.append(f"[{label}]")

    inputs = "[0:a]" + "".join(labels)
    mix = (f"{inputs}amix=inputs={len(lines) + 1}:duration=first:"
           f"dropout_transition=0:normalize=0[out]")
    filter_complex = ";".join(chains + [mix])

    cmd += ["-filter_complex", filter_complex, "-map", "[out]",
            "-t", str(TOTAL_SECONDS), "-ar", str(RATE), "-ac", "1",
            "-c:a", "pcm_s16le", str(OUT)]
    subprocess.run(cmd, check=True)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()

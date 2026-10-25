"""Render AirChess video voiceover lines with Kokoro-82M (Apache-2.0).

Usage:
    python marketing/video/edit/make_vo_kokoro.py <cues.json> <outdir> [--voice af_heart] [--speed 0.97]

Reads cue entries {id, start, slot, text}; writes <outdir>/vo-<id>.wav
(24 kHz mono, silence-trimmed, loudness-normalised to -18 LUFS) and
<outdir>/durations.json. Lines whose audio exceeds their slot are re-rendered
once with speed raised (max 1.1) and reported.
"""
import argparse
import json
import re
import subprocess
import tempfile
from pathlib import Path

import numpy as np
import soundfile as sf
import spacy
from misaki import en
from kokoro import KPipeline

SR = 24000
TARGET_LUFS = -18
MAX_SPEED = 1.1
PAD_S = 0.05
SILENCE_DB = -50

# Spoken-text respellings (display text in cues is untouched).
RESPELL = [
    (r"\b2D\b", "two-D"),
    (r"\b3D\b", "three-D"),
    # misaki without POS tags reads the verb "Close" as /kloʊs/; force /kloʊz/
    (r"\bClose the app\b", "[Close](/klˈOz/) the app"),
]


def patch_misaki_without_spacy_model():
    """en_core_web_sm fails to load on this Windows setup (OverflowError / E018),
    so misaki gets a blank English pipeline: tokenisation works, POS-based
    homograph disambiguation is lost (acceptable for these lines)."""
    def init(self, version=None, trf=False, british=False, fallback=None, unk="❓"):
        self.version = version
        self.british = british
        self.nlp = spacy.blank("en")
        self.lexicon = en.Lexicon(british)
        self.fallback = fallback
        self.unk = unk
    en.G2P.__init__ = init


def spoken(text):
    for pat, rep in RESPELL:
        text = re.sub(pat, rep, text)
    return text


def render(pipe, text, voice, speed):
    chunks = [a for _, _, a in pipe(spoken(text), voice=voice, speed=speed)]
    return np.concatenate(chunks).astype(np.float32)


def trim(audio):
    thr = 10 ** (SILENCE_DB / 20)
    idx = np.where(np.abs(audio) > thr)[0]
    if idx.size == 0:
        return audio
    pad = int(PAD_S * SR)
    return audio[max(idx[0] - pad, 0): min(idx[-1] + pad, len(audio))]


def ffmpeg(*args):
    return subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", *args],
                          check=True, capture_output=True, text=True)


def measure_lufs(path):
    r = subprocess.run(["ffmpeg", "-hide_banner", "-i", path, "-af",
                        f"loudnorm=I={TARGET_LUFS}:TP=-1.5:LRA=11:print_format=json",
                        "-f", "null", "-"], capture_output=True, text=True)
    blob = r.stderr[r.stderr.rfind("{"): r.stderr.rfind("}") + 1]
    return json.loads(blob)


def normalise(src, dst):
    m = measure_lufs(src)
    af = (f"loudnorm=I={TARGET_LUFS}:TP=-1.5:LRA=11:linear=true:"
          f"measured_I={m['input_i']}:measured_TP={m['input_tp']}:"
          f"measured_LRA={m['input_lra']}:measured_thresh={m['input_thresh']}:"
          f"offset={m['target_offset']}")
    ffmpeg("-y", "-i", src, "-af", af, "-ar", str(SR), "-ac", "1", dst)


def duration(path):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=nw=1:nk=1", path], capture_output=True, text=True, check=True)
    return float(r.stdout.strip())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cues")
    ap.add_argument("outdir")
    ap.add_argument("--voice", default="af_heart")
    ap.add_argument("--speed", type=float, default=0.97)
    ap.add_argument("--only", nargs="+", help="re-render only these cue ids; merge into existing durations.json")
    args = ap.parse_args()

    patch_misaki_without_spacy_model()
    pipe = KPipeline(lang_code="a")
    cues = json.loads(Path(args.cues).read_text(encoding="utf-8"))
    if args.only:
        cues = [c for c in cues if c["id"] in args.only]
    out = Path(args.outdir)
    out.mkdir(parents=True, exist_ok=True)
    report = []
    with tempfile.TemporaryDirectory() as tmp:
        for cue in cues:
            speed = args.speed
            raw = trim(render(pipe, cue["text"], args.voice, speed))
            tmp_raw = Path(tmp) / f"{cue['id']}.wav"
            sf.write(tmp_raw, raw, SR, subtype="PCM_16")
            dur = duration(str(tmp_raw))
            retried = False
            if dur > cue["slot"]:
                speed = min(round(args.speed * 1.1, 3), MAX_SPEED)
                if speed > args.speed:
                    raw = trim(render(pipe, cue["text"], args.voice, speed))
                    sf.write(tmp_raw, raw, SR, subtype="PCM_16")
                    dur = duration(str(tmp_raw))
                    retried = True
            dst = out / f"vo-{cue['id']}.wav"
            normalise(str(tmp_raw), str(dst))
            final = duration(str(dst))
            report.append({
                "id": cue["id"],
                "duration": round(final, 2),
                "slot": cue["slot"],
                "fits": final <= cue["slot"],
                "speed": speed,
                "retried_for_fit": retried,
            })
            print(f"{cue['id']}: {final:.2f}s / slot {cue['slot']}s speed {speed}"
                  f"{' (retried)' if retried else ''}")
    dpath = out / "durations.json"
    if args.only and dpath.exists():
        old = {l["id"]: l for l in json.loads(dpath.read_text(encoding="utf-8"))["lines"]}
        old.update({r["id"]: r for r in report})
        report = [old[k] for k in sorted(old)]
    dpath.write_text(json.dumps(
        {"voice": args.voice, "base_speed": args.speed, "target_lufs": TARGET_LUFS,
         "lines": report}, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

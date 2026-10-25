#!/usr/bin/env python3
"""AirChess launch video build (1920x1080, 30 fps, H.264 + AAC).

Stages (decoupled so audio can change without re-rendering picture):
  python build.py picture            picture (captions burned in) + clean (no captions) + SRT
  python build.py cues               write vo-cues.json next to this script
  python build.py mix [--vo-dir DIR] music bed + VO placed at cue starts, ducked, ~-16 LUFS,
                                     muxed onto the picture -> airchess-launch-16x9.mp4
  python build.py all [--vo-dir DIR]

Outputs live under marketing/video/assets/edit/ (gitignored); intermediates in edit/work/.
"""
import argparse
import glob
import hashlib
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# ============================================================================
# TIMINGS AND CONTENT: edit here. Scene durations drive every timecode.
# ============================================================================
W, H, FPS = 1920, 1080, 30

# Music: track time 0 lands at this video second. The track starts with the
# picture so the cold open is not silent; it builds toward a peak near the end card.
MUSIC_FILE = "music/parhelion_nakarada_CC-BY-4.0.mp3"
MUSIC_START = 0.0           # video time the track starts (track t=0)
MUSIC_GAIN = 0.55          # bed level before loudnorm
MUSIC_FADE_IN = 3.0
MUSIC_FADE_OUT = 3.0
VO_OVER_MUSIC_DB = 12.0    # target voice-over-music separation under each line
DUCK_ATTACK = 0.25         # seconds to ramp the bed down before a line
DUCK_RELEASE = 0.5         # seconds to ramp it back up after
LOUDNESS_I = -16.0

WPS = 2.6                  # VO budget: words per second

# Source clips (relative to marketing/video/assets/). Globs resolve the first match.
SRC = {
    "shot01": "phone/selects/shot01-cold-open-take1.mp4",
    "shot02": "phone/selects/shot02-walk-around-take1.mp4",
    "shot03": "phone/selects/shot03-lean-in-take1.mp4",
    "castle": "phone/selects/shot04-touch-castle-take1.mp4",
    "capture": "phone/selects/shot04-capture-take1.mp4",
    "promo": "phone/selects/shot05-promotion-take2.mp4",
    "hint": "phone/selects/shot06-hint-take3.mp4",
    "computer": "phone/selects/shot07-computer-undo-take1.mp4",
    "rawA": "phone/raw/screen-20261025-024201-*.mp4",   # take A: Alex (White), kitchen
    "rawB": "phone/raw/screen-20261025-024618-*.mp4",   # take B: Sam (Black), other room
    "cleanA": "phone/take-a-alex-view.mp4",             # clean Share view, 640x1280
    "cleanB": "phone/take-b-sam-view.mp4",              # clean Share view, 640x1280
    "b2d": "browser/browser-2d-to-3d.mp4",
    "bvs": "browser/browser-vs-computer.mp4",
    "orbit": "browser/board-3d-orbit.mp4",
    "stock": "stock/01-golden-hour-neighborhood*.mp4",
    "resume": "phone/raw/screen-20261025-015822-*.mp4",   # home screen with Resume game card
}

# Raw takes carry a name strip ("TJ") at the bottom left; it is covered with the take's
# character name. Select clips show no strip.
NAME_PATCH = {"rawA": ("Alex", (18, 16, 17)), "rawB": ("Sam", (25, 24, 27))}

# Scene kinds:
#   phone : clips=[(src, ss, dur), ...] shown in the phone frame (right side)
#   share : b=(ss, dur) clean Sam view full screen; a=ss clean Alex view in corner
#   split : left/right=(src, ss, dur) two phone frames side by side, labels
#   full  : clip=(src, ss, dur) 16:9 footage full frame
#   card  : img=graphics png held for dur
# caps: (kind, start, end, text) relative to scene start.
#   kind 'cap' = caption (burned in picture, removed in clean)
#   kind 'title' = design title (kept in both)
SCENES = [
    dict(id="cold", dur=10.6, kind="phone", clips=[("shot01", 0, 10.6)],
         caps=[("title", 6.0, 10.6, "Your table is the board.")]),
    dict(id="walk", dur=5.0, kind="phone", clips=[("shot02", 0, 5.0)],
         caps=[("cap", 0.3, 4.8, "Any table, desk, or floor.")]),
    dict(id="lean", dur=4.5, kind="phone", clips=[("shot03", 0, 4.5)],
         caps=[("cap", 0.2, 4.3, "Walk around it. Lean in.")]),
    dict(id="castle", dur=4.0, kind="phone", clips=[("castle", 0, 4.0)],
         caps=[("cap", 0.2, 3.9, "Tap a piece. Its moves light up.")]),
    dict(id="capture", dur=3.5, kind="phone", clips=[("capture", 0.5, 3.5)],
         caps=[("cap", 0.2, 3.2, "Tap a square. It glides there.")]),
    dict(id="promo", dur=4.5, kind="phone", clips=[("promo", 4.5, 4.5)],
         caps=[("cap", 0.2, 4.2, "Every rule is handled for you.")]),
    dict(id="hint", dur=11.0, kind="phone", clips=[("hint", 0, 11.0)],
         caps=[("title", 0.0, 3.2, "A coach on your table"),
               ("cap", 3.4, 10.8, "Stuck? Tap Hint. Stockfish draws its best move.")]),
    dict(id="computer", dur=12.0, kind="phone",
         clips=[("computer", 1, 4.8), ("computer", 28.0, 7.2)],
         caps=[("title", 0.0, 2.6, "Play the computer"),
               ("cap", 2.8, 5.0, "Pick a side and a level."),
               ("cap", 8.4, 11.8, "Made a mistake? Undo it.")]),
    dict(id="across", dur=3.5, kind="full", clip=("stock", 0, 3.5),
         caps=[("cap", 0.4, 2.6, "Across town.")]),
    dict(id="invite", dur=7.0, kind="phone", clips=[("rawA", 0, 7.0)],
         caps=[("cap", 0.3, 3.6, "Chess is better with a friend."),
               ("cap", 3.9, 6.8, "Create a game. Share the code.")]),
    dict(id="join", dur=8.0, kind="phone", clips=[("rawB", 0.5, 2.77), ("rawB", 3.92, 5.23)],
         caps=[("cap", 0.3, 2.6, "Join with the code."),
               ("cap", 3.0, 5.6, "Sam plays Black.")]),
    dict(id="split", dur=7.2, kind="split", left=("rawA", 20.5, 7.2),
         right=("rawB", 11.0, 7.2), labels=["Alex  \u00b7  White", "Sam  \u00b7  Black"],
         note=True,
         caps=[("title", 0.0, 2.3, "Two tables, one game."),
               ("cap", 2.5, 5.6, "Same game, same move.")]),
    dict(id="share", dur=10.0, kind="share", b=(14.0, 10.0), a=12.0, note=True,
         caps=[("cap", 0.3, 4.9, "See their board, not just their moves."),
               ("cap", 5.2, 9.4, "Their room. Their table. Their move.")]),
    dict(id="privacy", dur=6.5, kind="share", b=(24.0, 6.5), a=22.0, note=True,
         caps=[("cap", 0.3, 3.0, "Off until you turn it on."),
               ("cap", 3.2, 5.6, "Nothing is recorded.")]),
    dict(id="decline", dur=3.0, kind="phone", clips=[("rawA", 57.8, 3.0)],
         caps=[("cap", 0.2, 2.8, "Draw offered. Declined.")]),
    dict(id="resume", dur=3.6, kind="phone", clips=[("resume", 0.0, 3.6)],
         caps=[("cap", 0.1, 3.4, "Pick up where you left off.")]),
    dict(id="mate", dur=5.0, kind="phone", clips=[("rawA", 81.0, 5.0)],
         caps=[("cap", 0.3, 2.0, "Checkmate.")]),
    dict(id="rematch", dur=5.0, kind="phone", clips=[("rawB", 105.5, 5.0)],
         caps=[("cap", 0.3, 2.6, "Rematch. Colors swap.")]),
    dict(id="browser", dur=9.0, kind="browser", clips=[("b2d", 4.0, 4.6), ("bvs", 1.0, 4.4)],
         caps=[("title", 0.0, 2.4, "Or in your browser."),
               ("cap", 2.8, 4.5, "2D or 3D."),
               ("cap", 5.0, 8.8, "Stockfish replies in 3D.")]),
    dict(id="orbit", dur=4.0, kind="full", clip=("orbit", 0, 4.0),
         caps=[("cap", 0.3, 2.6, "Every piece, in 3D.")]),
    dict(id="montage", dur=4.2, kind="phone",
         clips=[("shot02", 1.0, 1.0), ("cleanA", 5.0, 1.0), ("cleanB", 25.0, 1.1), ("shot02", 9.0, 1.1)],
         caps=[("cap", 0.0, 4.0, "Any table. Anywhere.\nAny time.")]),
    dict(id="end", dur=5.6, kind="card", img="end-card-16x9.png", caps=[]),
]

# VO cues: one per script VO line. start = scene start + offset; slot = max seconds.
VO = [
    dict(id="01", scene="cold", offset=6.0, slot=3.6,
         text="This is AirChess."),
    dict(id="02", scene="walk", offset=-0.8, slot=10.9,
         text="AirChess puts a full-size chess set on any table, desk, or floor. "
              "Walk around it. Lean in. It stays right where you put it."),
    dict(id="03", scene="castle", offset=0.6, slot=11.2,
         text="Tap a piece and its moves light up. Tap a square and it glides there."),
    dict(id="04", scene="hint", offset=0.2, slot=10.6,
         text="If you're stuck, tap Hint. Stockfish, one of the strongest chess engines ever built, "
              "draws its best move right across the board, and shows you what's under attack."),
    dict(id="05", scene="computer", offset=0.2, slot=11.6,
         text="Want a challenge? Pick a side, pick a level, and take on the computer. "
              "If you've made a mistake, you can undo it and try again."),
    dict(id="06", scene="invite", offset=0.2, slot=6.6,
         text="Chess is better with a friend. Create a game, share the code, "
              "and they're in, from anywhere."),
    dict(id="07", scene="split", offset=0.1, slot=7.1,
         text="You each play on your own table, from your own side of the board, "
              "live, with real clocks."),
    dict(id="08", scene="share", offset=0.2, slot=9.6,
         text="And with Share view, you see their board exactly as they do. "
              "Their room, their table, their move."),
    dict(id="09", scene="privacy", offset=0.2, slot=6.1,
         text="Sharing is off until you turn it on, so nothing is recorded, and there's no microphone."),
    dict(id="10", scene="decline", offset=0.1, slot=12.5,
         text="You can offer a draw or demand a rematch, and if you close the app mid-game, "
              "your game is waiting when you come back."),
    dict(id="11", scene="browser", offset=0.2, slot=8.8,
         text="No phone handy? Play right in your browser, in 2D or 3D, "
              "against a friend or the computer."),
    dict(id="12", scene="montage", offset=0.2, slot=3.8,
         text="Any table. Anywhere. Any time."),
    dict(id="13", scene="end", offset=0.3, slot=5.0,
         text="AirChess. Chess, wherever you are."),
]

# ============================================================================
ROOT = Path(__file__).resolve().parent                 # marketing/video/edit
VIDEO = ROOT.parent                                    # marketing/video
ASSETS = VIDEO / "assets"
EDIT = ASSETS / "edit"
WORK = EDIT / "work"
SEG = WORK / "seg"
PNG = WORK / "png"
GRAPHICS = ASSETS / "graphics"
FRAME = GRAPHICS / "phone-frame-9x19.png"
NOTE = GRAPHICS / "dramatization-note.png"
CHROME = GRAPHICS / "share-view-overlay.png"
FONT_B = "C:/Windows/Fonts/segoeuib.ttf"
FONT_R = "C:/Windows/Fonts/segoeui.ttf"
BG = "0x101713"
PICTURE = EDIT / "airchess-launch-16x9-picture.mp4"
CLEAN = EDIT / "airchess-launch-16x9-clean.mp4"
SRT = EDIT / "captions-16x9.srt"
FINAL = EDIT / "airchess-launch-16x9.mp4"
CUES_JSON = ROOT / "vo-cues.json"
DEFAULT_VO = ASSETS / "vo" / "kokoro"


def sh(cmd):
    print("+", " ".join(str(c) for c in cmd)[:240], flush=True)
    subprocess.run([str(c) for c in cmd], check=True)


def ff(*args):
    sh(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args])


def probe_dur(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                          "-of", "csv=p=0", str(path)], capture_output=True, text=True, check=True)
    return float(out.stdout.strip())


def src(key):
    hits = sorted(glob.glob(str(ASSETS / SRC[key])))
    if not hits:
        sys.exit(f"missing source for {key}: {SRC[key]}")
    return hits[0]


def scene_starts():
    t, out = 0.0, {}
    for s in SCENES:
        out[s["id"]] = t
        t += s["dur"]
    return out, t


# ---------------------------------------------------------------- geometry
def frame_geom():
    im = Image.open(FRAME).convert("RGBA")
    w, h = im.size
    a = im.getchannel("A")
    inner = a.crop((40, 40, w - 40, h - 40)).point(lambda v: 255 if v < 128 else 0)
    bb = inner.getbbox()
    hole = (bb[0] + 40, bb[1] + 40, bb[2] + 40, bb[3] + 40)
    return w, h, hole


FW0, FH0, HOLE0 = frame_geom()


def phone_layout(ph, cx, py):
    """Return frame box and screen box for a phone drawn ph px tall, centred at cx."""
    s = ph / FH0
    fw = round(FW0 * s)
    hx, hy = round(HOLE0[0] * s), round(HOLE0[1] * s)
    hw, hh = round((HOLE0[2] - HOLE0[0]) * s), round((HOLE0[3] - HOLE0[1]) * s)
    px = round(cx - fw / 2)
    return dict(cx=round(cx), px=px, py=py, fw=fw, fh=ph, hx=hx, hy=hy, hw=hw, hh=hh)


# ---------------------------------------------------------------- text art
def wrap(text, font, maxw):
    lines = []
    for para in text.split("\n"):
        cur = ""
        for word in para.split():
            t = (cur + " " + word).strip()
            if not cur or font.getlength(t) <= maxw:
                cur = t
            else:
                lines.append(cur)
                cur = word
        if cur:
            lines.append(cur)
    return lines


def text_png(out, kind, text, x, y, maxw=760):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if kind == "title":
        font, lh = ImageFont.truetype(FONT_B, 96), 118
    elif kind == "label":
        font, lh = ImageFont.truetype(FONT_R, 40), 50
    else:
        font, lh = ImageFont.truetype(FONT_B, 56), 74
    lines = wrap(text, font, maxw)
    tw = max(font.getlength(line) for line in lines)
    th = lh * len(lines)
    if kind == "cap":
        pad = 26
        d.rounded_rectangle((x - pad, y - pad, x + tw + pad, y + th + pad - 10),
                            radius=16, fill=(16, 23, 19, 222))
    fill = (160, 176, 168, 255) if kind == "label" else (242, 245, 243, 255)
    for i, line in enumerate(lines):
        if kind == "label":
            d.text((x, y + i * lh), line, font=font, fill=fill, anchor="ma")
        else:
            d.text((x, y + i * lh), line, font=font, fill=fill)
    im.save(out)
    return out


# ---------------------------------------------------------------- segments
def seg_phone(key, ss, dur):
    path = src(key)
    patch = NAME_PATCH.get(key)
    tag = "" if patch is None else "-n" + hashlib.sha1(repr(patch).encode()).hexdigest()[:6]
    out = SEG / f"{key}-{ss:.2f}-{dur:.2f}{tag}-p.mp4"
    if not out.exists():
        # Clean views are 1:2, the phone screen is 1:2.24; cover-crop instead of stretching.
        fit = ("scale=1080:2424:force_original_aspect_ratio=increase:flags=lanczos,crop=1080:2424"
               if key.startswith("clean") else "scale=1080:2424:flags=lanczos")
        vf = f"fps=30,{fit},setsar=1,format=yuv420p"
        if patch is None:
            ff("-ss", ss, "-t", dur, "-i", path, "-vf", vf,
               "-an", "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-r", "30", out)
        else:
            name, bg = patch
            ff("-ss", ss, "-t", dur, "-i", path, "-loop", "1", "-i", name_patch_png(name, bg),
               "-filter_complex", f"[0:v]{vf}[b];[b][1:v]overlay=40:1922,format=yuv420p[o]",
               "-map", "[o]", "-t", dur,
               "-an", "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-r", "30", out)
    return out


def seg_full(key, ss, dur):
    path = src(key)
    out = SEG / f"{key}-{ss:.2f}-{dur:.2f}-f.mp4"
    if not out.exists():
        ff("-ss", ss, "-t", dur, "-i", path,
           "-vf", "fps=30,scale=1920:1080:force_original_aspect_ratio=increase,"
                  "crop=1920:1080,setsar=1,format=yuv420p",
           "-an", "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-r", "30", out)
    return out


def seg_share(ss_b, ss_a, dur):
    """Share view composite at 1080x2424: Sam's clean view full screen, Alex's clean
    view in the preview window (share-view-preview-mask bbox 749,110 294x405), chrome on top."""
    out = SEG / f"share-{ss_b:.2f}-{ss_a:.2f}-{dur:.2f}.mp4"
    if not out.exists():
        fc = ("[0:v]fps=30,scale=1212:2424:flags=lanczos,crop=1080:2424:66:0,setsar=1[b];"
              "[1:v]fps=30,scale=294:405:force_original_aspect_ratio=increase,crop=294:405,setsar=1[a];"
              "[b][a]overlay=749:110[ba];"
              "[2:v]format=rgba[ch];[ba][ch]overlay=0:0,format=yuv420p[o]")
        ff("-ss", ss_b, "-t", dur, "-i", src("cleanB"),
           "-ss", ss_a, "-t", dur, "-i", src("cleanA"),
           "-loop", "1", "-t", dur, "-i", CHROME,
           "-filter_complex", fc, "-map", "[o]", "-an",
           "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-r", "30", out)
    return out


def name_patch_png(name, bg):
    """Solid pill-coloured patch that covers the name strip on raw takes."""
    out = PNG / f"name-{name}.png"
    if not out.exists():
        PNG.mkdir(parents=True, exist_ok=True)
        im = Image.new("RGBA", (120, 60), (*bg, 255))
        ImageDraw.Draw(im).text((14, 44), name, font=ImageFont.truetype(FONT_B, 42),
                                fill=(240, 240, 240, 255), anchor="ls")
        im.save(out)
    return out


def window_png():
    """Browser window chrome, 1000x782: 40 px title bar with three dots, transparent body (742 px)."""
    out = PNG / "window-frame-1000x782.png"
    if not out.exists():
        PNG.mkdir(parents=True, exist_ok=True)
        im = Image.new("RGBA", (1000, 782), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.rectangle((0, 0, 999, 39), fill=(22, 32, 28, 255))
        for i, c in enumerate([(232, 96, 84), (232, 186, 80), (96, 196, 110)]):
            x0 = 16 + i * 24
            d.ellipse((x0, 13, x0 + 14, 27), fill=(*c, 255))
        d.rectangle((0, 0, 999, 781), outline=(46, 64, 56, 255), width=2)
        im.save(out)
    return out


def seg_win(key, ss, dur):
    """Crop the 16:9 browser capture to the board and right panel (1160x860 region), fit to 1000x742."""
    path = src(key)
    out = SEG / f"win2-{key}-{ss:.2f}-{dur:.2f}.mp4"
    if not out.exists():
        ff("-ss", ss, "-t", dur, "-i", path,
           "-vf", "fps=30,crop=1160:860:380:100,scale=1000:742:flags=lanczos,setsar=1,format=yuv420p",
           "-an", "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-r", "30", out)
    return out


def concat_segs(paths, out):
    if len(paths) == 1:
        return paths[0]
    if not out.exists():
        lst = out.with_suffix(".txt")
        lst.write_text("".join(f"file '{p.as_posix()}'\n" for p in paths), encoding="utf-8")
        ff("-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", out)
    return out


# ---------------------------------------------------------------- scene render
def timed_caps(scene):
    """Extend each caption to its reading time (1.2 s per 10 characters, min 1.5 s),
    never past the scene end or the start of the next caption in the same scene."""
    caps = scene["caps"]
    out = []
    for i, (kind, a, b, text) in enumerate(caps):
        if kind == "cap":
            need = max(1.5, len(text) / 10 * 1.2)
            limit = scene["dur"] - 0.2
            later = [s for k, s, _, _ in caps[i + 1:] if k == "cap" and s > a]
            if later:
                limit = min(limit, min(later) - 0.1)
            b = max(b, min(a + need, limit))
        out.append((kind, a, b, text))
    return out


def caps_for(scene, variant):
    items = []
    for kind, a, b, text in timed_caps(scene):
        if variant == "clean" and kind == "cap":
            continue
        items.append((kind, a, b, text))
    return items



def render_scene(scene, variant, out):
    dur = scene["dur"]
    kind = scene["kind"]
    chain = []
    n = [0]

    # Build with explicit index tracking.
    args = []
    idx = 0

    def inp(*a):
        nonlocal idx
        args.extend(str(x) for x in a)
        i = idx
        idx += 1
        return i

    def lbl():
        n[0] += 1
        return f"v{n[0]}"

    cur = None
    slots = []
    if kind == "card":
        i = inp("-loop", "1", "-t", dur, "-i", GRAPHICS / scene["img"])
        out_l = lbl()
        chain.append(f"[{i}:v]scale=1920:1080,setsar=1,format=yuv420p[{out_l}]")
        cur = f"[{out_l}]"
    elif kind == "full":
        key, ss, d = scene["clip"]
        clip = seg_full(key, ss, d)
        i = inp("-i", clip)
        out_l = lbl()
        chain.append(f"[{i}:v]setsar=1[{out_l}]")
        cur = f"[{out_l}]"
    else:
        i = inp("-f", "lavfi", "-i", f"color=c=0x101713:s={W}x{H}:r={FPS}:d={dur}")
        out_l = lbl()
        chain.append(f"[{i}:v]null[{out_l}]")
        cur = f"[{out_l}]"
        if kind == "phone":
            screen = concat_segs(
                [seg_phone(k, ss, d) for k, ss, d in scene["clips"]],
                SEG / f"{scene['id']}-{variant}-{hashlib.sha1(json.dumps(scene['clips']).encode()).hexdigest()[:10]}-screen.mp4")
            lay = phone_layout(994, 1300, 43)
            slots.append((screen, lay))
        elif kind == "share":
            ssb, db = scene["b"]
            screen = seg_share(ssb, scene["a"], db)
            lay = phone_layout(994, 1300, 43)
            slots.append((screen, lay))
        elif kind == "split":
            lay_l = phone_layout(800, 740, 180)
            lay_r = phone_layout(800, 1180, 180)
            for (k, ss, d), lay in ((scene["left"], lay_l), (scene["right"], lay_r)):
                slots.append((seg_phone(k, ss, d), lay))
        elif kind == "browser":
            win_key = hashlib.sha1(json.dumps(scene["clips"]).encode()).hexdigest()[:10]
            win = concat_segs([seg_win(k, ss, d) for k, ss, d in scene["clips"]],
                              SEG / f"{scene['id']}-{variant}-{win_key}-win2.mp4")
            wi = inp("-i", win)
            ol = lbl()
            chain.append(f"{cur}[{wi}:v]overlay=880:189[{ol}]")
            cur = f"[{ol}]"
            fi = inp("-loop", "1", "-t", dur, "-i", window_png())
            ol = lbl()
            chain.append(f"{cur}[{fi}:v]overlay=880:149[{ol}]")
            cur = f"[{ol}]"

    for path, lay in slots:
        si = inp("-i", path)
        sl = lbl()
        chain.append(f"[{si}:v]scale={lay['hw']}:{lay['hh']},setsar=1[s{si}]")
        ol = lbl()
        chain.append(f"{cur}[s{si}]overlay={lay['px'] + lay['hx']}:{lay['py'] + lay['hy']}[{ol}]")
        cur = f"[{ol}]"
        fi = inp("-loop", "1", "-t", dur, "-i", FRAME)
        fl = lbl()
        chain.append(f"[{fi}:v]scale={lay['fw']}:{lay['fh']}[f{fi}]")
        ol = lbl()
        chain.append(f"{cur}[f{fi}]overlay={lay['px']}:{lay['py']}[{ol}]")
        cur = f"[{ol}]"

    # Text layer: captions/titles/labels as full-frame PNGs with enable windows.
    name_base = PNG / f"{scene['id']}-{variant}"
    PNG.mkdir(parents=True, exist_ok=True)
    overlays = []
    for j, (kind_t, a, b, text) in enumerate(caps_for(scene, variant)):
        if kind == "split":
            tx, ty, mw = 140, 60, 1100
        else:
            tx, ty, mw = 140, (430 if kind_t == "title" else 580), 760
        p = text_png(f"{name_base}-{j}.png", kind_t, text, tx, ty, maxw=mw)
        overlays.append((p, a, b))
    if kind == "split":
        lab_font = ImageFont.truetype(FONT_R, 40)
        for j, (cx, text) in enumerate(zip([lay_l["cx"], lay_r["cx"]], scene["labels"])):
            p = text_png(f"{name_base}-label{j}.png", "label", text, round(cx - lab_font.getlength(text) / 2), 1000)
            overlays.append((p, 0, dur))
    for p, a, b in overlays:
        oi = inp("-loop", "1", "-t", dur, "-i", p)
        ol = lbl()
        chain.append(f"{cur}[{oi}:v]overlay=0:0:enable='between(t,{a},{b})'[{ol}]")
        cur = f"[{ol}]"
    if scene.get("note"):
        nx, ny, nw = (1320, 60, 560) if kind == "split" else (140, 960, 700)
        ni = inp("-loop", "1", "-t", dur, "-i", NOTE)
        nl = lbl()
        chain.append(f"[{ni}:v]scale={nw}:-1[{nl}]")
        ol = lbl()
        chain.append(f"{cur}[{nl}]overlay={nx}:{ny}[{ol}]")
        cur = f"[{ol}]"

    chain.append(f"{cur}format=yuv420p[vout]")
    out.parent.mkdir(parents=True, exist_ok=True)
    ff(*args, "-filter_complex", ";".join(chain), "-map", "[vout]", "-t", dur,
       "-an", "-c:v", "libx264", "-crf", "18", "-preset", "medium", "-r", "30",
       "-pix_fmt", "yuv420p", "-movflags", "+faststart", out)
    return out


def build_variant(variant, out_path):
    build_hash = hashlib.sha1(Path(__file__).read_bytes()).hexdigest()[:8]
    parts = []
    for i, scene in enumerate(SCENES):
        spec = json.dumps({"s": scene, "v": variant, "b": build_hash}, sort_keys=True, default=str)
        key = hashlib.sha1(spec.encode()).hexdigest()[:10]
        part = WORK / variant / f"{i:02d}-{scene['id']}-{key}.mp4"
        if not part.exists():
            render_scene(scene, variant, part)
        parts.append(part)
    lst = WORK / variant / "concat.txt"
    lst.write_text("".join(f"file '{p.as_posix()}'\n" for p in parts), encoding="utf-8")
    ff("-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", "-movflags", "+faststart", out_path)
    print(f"{variant}: {probe_dur(out_path):.2f} s -> {out_path}")


def stage_picture():
    for d in ("picture", "clean", "seg"):
        (WORK / d).mkdir(parents=True, exist_ok=True)
    EDIT.mkdir(parents=True, exist_ok=True)
    build_variant("picture", PICTURE)
    build_variant("clean", CLEAN)
    write_srt()


def fmt_srt(t):
    ms = round(t * 1000)
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def write_srt():
    starts, _ = scene_starts()
    rows = []
    for scene in SCENES:
        for kind, a, b, text in timed_caps(scene):
            if kind == "cap":
                rows.append((starts[scene["id"]] + a, starts[scene["id"]] + b, text))
    rows.sort()
    lines = []
    for i, (a, b, text) in enumerate(rows, 1):
        lines += [str(i), f"{fmt_srt(a)} --> {fmt_srt(b)}", text, ""]
    SRT.write_text("\n".join(lines), encoding="utf-8")
    print(f"srt: {len(rows)} captions -> {SRT}")


# ---------------------------------------------------------------- cues
def stage_cues():
    starts, total = scene_starts()
    cues = []
    for v in VO:
        start = starts[v["scene"]] + v["offset"]
        words = len(v["text"].split())
        need = words / WPS
        flag = "" if v["slot"] >= need else "  <-- TOO LONG FOR SLOT"
        print(f"VO{v['id']} start {start:6.2f}  slot {v['slot']:5.2f}  need {need:5.2f}{flag}")
        cues.append({"id": v["id"], "start": round(start, 2), "slot": v["slot"], "text": v["text"]})
    for a, b in zip(cues, cues[1:]):
        if a["start"] + a["slot"] > b["start"] + 1e-6:
            print(f"WARNING: VO{a['id']} slot overlaps VO{b['id']}")
    CUES_JSON.write_text(json.dumps(cues, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"cues -> {CUES_JSON} (total {total:.2f} s)")


# ---------------------------------------------------------------- mix
def loudnorm_two_pass(src, dst):
    """Measure integrated loudness, then apply linear gain to hit LOUDNESS_I."""
    spec = f"I={LOUDNESS_I}:TP=-1.5:LRA=11"
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(src),
                        "-af", f"loudnorm={spec}:print_format=json", "-f", "null", "-"],
                       capture_output=True, text=True, check=True)
    j = json.loads(r.stderr[r.stderr.rindex("{"):r.stderr.rindex("}") + 1])
    lin = (f"{spec}:measured_I={j['input_i']}:measured_TP={j['input_tp']}:"
           f"measured_LRA={j['input_lra']}:measured_thresh={j['input_thresh']}:"
           f"offset={j['target_offset']}:linear=true")
    ff("-i", src, "-af", f"loudnorm={lin}", "-ar", "48000", "-ac", "2", dst)


def loudness(path, start=None, dur=None):
    seek = [] if start is None else ["-ss", f"{start:.3f}", "-t", f"{dur:.3f}"]
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", *seek, "-i", str(path),
                        "-af", "ebur128", "-f", "null", "-"], capture_output=True, text=True, check=True)
    return float([l for l in r.stderr.splitlines() if l.strip().startswith("I:")][-1].split()[1])


def duck_expr(music_wav, vo_dir, present):
    """Volume expression lowering the bed under each line just enough for the target
    separation; the track builds, so a fixed compressor under-ducks the loud end."""
    terms = []
    for c in present:
        dur = probe_dur(vo_dir / f"vo-{c['id']}.wav")
        need = loudness(vo_dir / f"vo-{c['id']}.wav") - VO_OVER_MUSIC_DB
        cut = max(0.0, loudness(music_wav, c["start"], dur) - need)
        if cut <= 0:
            continue
        a, b = c["start"] - DUCK_ATTACK, c["start"] + dur
        ramp = (f"clip(min((t-{a:.3f})/{DUCK_ATTACK},({b + DUCK_RELEASE:.3f}-t)/{DUCK_RELEASE}),0,1)")
        terms.append(f"{1 - 10 ** (-cut / 20):.4f}*{ramp}")
        print(f"duck VO{c['id']}: -{cut:.1f} dB")
    if not terms:
        return "1"
    depth = terms[0]
    for t in terms[1:]:
        depth = f"max({depth},{t})"
    return f"1-{depth}"


def stage_mix(vo_dir):
    audio = WORK / "audio"
    audio.mkdir(parents=True, exist_ok=True)
    total = probe_dur(PICTURE)
    music_wav = audio / "music.wav"
    fade_out_st = max(0.0, total - MUSIC_FADE_OUT)
    ms = round(MUSIC_START * 1000)
    ff("-i", ASSETS / MUSIC_FILE,
       "-af", f"adelay={ms}|{ms},volume={MUSIC_GAIN},"
              f"afade=t=in:st={MUSIC_START:.3f}:d={MUSIC_FADE_IN},"
              f"afade=t=out:st={fade_out_st:.3f}:d={MUSIC_FADE_OUT},apad",
       "-t", f"{total:.3f}", "-ar", "48000", "-ac", "2", music_wav)

    cues = json.loads(CUES_JSON.read_text(encoding="utf-8"))
    present = [c for c in cues if vo_dir and (vo_dir / f"vo-{c['id']}.wav").exists()]
    vo_wav = None
    if present:
        vo_wav = audio / "vo.wav"
        args = []
        for c in present:
            args += ["-i", vo_dir / f"vo-{c['id']}.wav"]
        fc = []
        fc.append(f"anullsrc=r=48000:cl=stereo,atrim=0:{total:.3f}[sil]")
        labels = ["[sil]"]
        for k, c in enumerate(present):
            d = round(c["start"] * 1000)
            fc.append(f"[{k}:a]aformat=channel_layouts=stereo:sample_rates=48000,"
                      f"adelay={d}|{d},apad=whole_dur={total:.3f},atrim=0:{total:.3f}[a{k}]")
            labels.append(f"[a{k}]")
        fc.append("".join(labels) + f"amix=inputs={len(labels)}:duration=first:normalize=0[vo]")
        ff(*args, "-filter_complex", ";".join(fc), "-map", "[vo]", "-ar", "48000", "-ac", "2", vo_wav)
        print(f"vo: {len(present)} of {len(cues)} lines placed")
    else:
        print("vo: none found, music-only mix")

    mix = audio / "mix.wav"
    if vo_wav:
        ducked = audio / "music-ducked.wav"
        ff("-i", music_wav, "-af", f"volume=eval=frame:volume='{duck_expr(music_wav, vo_dir, present)}'",
           "-ar", "48000", "-ac", "2", ducked)
        pre = audio / "pre-norm.wav"
        ff("-i", ducked, "-i", vo_wav, "-filter_complex",
           "[0:a][1:a]amix=inputs=2:duration=first:normalize=0[aout]", "-map", "[aout]",
           "-ar", "48000", "-ac", "2", pre)
    else:
        pre = music_wav
    loudnorm_two_pass(pre, mix)

    ff("-i", PICTURE, "-i", mix, "-map", "0:v", "-map", "1:a",
       "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest",
       "-movflags", "+faststart", FINAL)
    print(f"final -> {FINAL} ({probe_dur(FINAL):.2f} s)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("stage", choices=["picture", "cues", "mix", "all"])
    ap.add_argument("--vo-dir", type=Path, default=None,
                    help=f"folder of vo-NN.wav (default for 'mix' is none unless given; try {DEFAULT_VO})")
    a = ap.parse_args()
    if a.stage in ("picture", "all"):
        stage_picture()
    if a.stage in ("cues", "all"):
        stage_cues()
    if a.stage in ("mix", "all"):
        stage_mix(a.vo_dir)


if __name__ == "__main__":
    main()

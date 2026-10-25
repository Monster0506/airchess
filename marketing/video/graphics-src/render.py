"""Render AirChess video graphics from the HTML sources in this folder.

Usage (from the repo root):
    python marketing/video/graphics-src/render.py              # render everything
    python marketing/video/graphics-src/render.py end-card-16x9.png title-01-your-table-16x9.png

Needs: pip install playwright, and Microsoft Edge installed (channel "msedge").
Output: marketing/video/assets/graphics/ (gitignored).
The board background is not rendered here. Capture it with capture-board-3d.py first.
Pages that contain an element with id "fit" are cropped to that element; others use a fixed canvas.
"""
import sys
import urllib.parse
from pathlib import Path

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "assets" / "graphics"

TITLES = [
    ("01", "your-table", "Your table is the board."),
    ("02", "a-coach", "A coach on your table"),
    ("03", "levels", "Easy · Medium · Hard · Max"),
    ("04", "play-friend", "Play a friend, anywhere"),
    ("05", "time-controls", "1+0 to 15+10"),
    ("06", "share-view", "See their board, not just their moves"),
    ("07", "privacy", "Off until you turn it on · Never recorded · No mic"),
    ("08", "browser", "Play in any browser"),
]

SIZES = {"16x9": (1920, 1080), "9x16": (1080, 1920)}

# (output name, page, query, viewport, transparent)
JOBS = [
    ("end-card-16x9.png", "end-card.html", {}, SIZES["16x9"], False),
    ("end-card-9x16.png", "end-card.html", {}, SIZES["9x16"], False),
    ("split-screen-frame-16x9.png", "split-screen.html", {}, SIZES["16x9"], True),
    ("split-screen-frame-9x16.png", "split-screen.html", {}, SIZES["9x16"], True),
    ("share-view-overlay.png", "share-view-overlay.html", {}, (1080, 2424), True),
    ("share-view-preview-mask.png", "share-view-overlay.html", {"mask": "1"}, (1080, 2424), True),
    ("phone-frame-9x19.png", "phone-frame.html", {}, (1152, 2496), True),
    ("dramatization-note.png", "dramatization-note.html", {}, (1000, 120), True),
    ("invite-bubble.png", "invite-bubble.html", {"w": "1080"}, (1080, 800), True),
    ("invite-bubble-small.png", "invite-bubble.html", {"w": "720"}, (1080, 800), True),
]
for num, slug, text in TITLES:
    for label, size in SIZES.items():
        JOBS.append((f"title-{num}-{slug}-{label}.png", "title-card.html", {"text": text}, size, True))


def main(only):
    OUT.mkdir(parents=True, exist_ok=True)
    jobs = [j for j in JOBS if not only or j[0] in only]
    if not jobs:
        sys.exit("no matching jobs")
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        try:
            for name, page_name, query, (w, h), transparent in jobs:
                ctx = browser.new_context(viewport={"width": w, "height": h}, device_scale_factor=1)
                page = ctx.new_page()
                url = (HERE / page_name).as_uri()
                if query:
                    url += "?" + urllib.parse.urlencode(query)
                page.goto(url)
                page.evaluate("document.fonts.ready.then(() => true)")
                page.wait_for_timeout(150)
                fit = page.locator("#fit")
                if fit.count():
                    box = fit.bounding_box()
                    cw, ch = int(box["width"]), round(box["height"])
                    page.screenshot(path=str(OUT / name), omit_background=transparent,
                                    clip={"x": box["x"], "y": box["y"], "width": cw, "height": ch})
                    size = f"{cw}x{ch}"
                else:
                    page.screenshot(path=str(OUT / name), omit_background=transparent,
                                    clip={"x": 0, "y": 0, "width": w, "height": h})
                    size = f"{w}x{h}"
                ctx.close()
                print(f"{name}  {size}  transparent={transparent}")
        finally:
            browser.close()


if __name__ == "__main__":
    main(set(sys.argv[1:]))

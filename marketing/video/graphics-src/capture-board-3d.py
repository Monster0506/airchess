"""Capture the AirChess web client's 3D board as a PNG (used as the end-card background).

Usage:
    python capture-board-3d.py <client-url> <out.png> [--width 1920 --height 1080] [--headed]

<client-url> is the web client root (e.g. https://your-server/play/). It is passed on the
command line on purpose so no server address is committed to the repo.
Starts a local game ("Play locally"), switches to 3D view, lets the piece animation settle,
then screenshots the #threeBoard canvas.
"""
import argparse
import sys
import time

from playwright.sync_api import sync_playwright


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("url")
    parser.add_argument("out")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1080)
    parser.add_argument("--headed", action="store_true")
    parser.add_argument("--settle", type=float, default=4.0, help="seconds to wait after switching to 3D")
    parser.add_argument("--scale", type=float, default=3.0, help="device scale factor (sharper canvas)")
    args = parser.parse_args()

    with sync_playwright() as p:
        browser = p.chromium.launch(
            channel="msedge",
            headless=not args.headed,
            args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"],
        )
        page = browser.new_page(
            viewport={"width": args.width, "height": args.height},
            device_scale_factor=args.scale,
        )
        errors: list[str] = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.goto(args.url, wait_until="domcontentloaded")
        page.click("#localStart")
        page.wait_for_selector("#viewToggle:not([disabled])", timeout=30000)
        page.click("#viewToggle")
        page.wait_for_function("() => !document.querySelector('#threeBoard').classList.contains('hidden')", timeout=30000)
        time.sleep(args.settle)
        page.locator("#threeBoard").screenshot(path=args.out)
        if errors:
            print("page errors:", *errors, sep="\n  ", file=sys.stderr)
        browser.close()
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

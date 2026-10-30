#!/usr/bin/env python3
"""Composes the Google Play screenshot set from the raw captures.

Reads the raw screenshots that `fastlane screengrab` writes to fastlane/metadata/android
(the plain set F-Droid shows), plus the watch captures in watch/<locale>/ (capture-watch.sh makes
watch.png; phone.png is the phone's workout screen at the start of the same walk), lays each slide out
as HTML with the copy from captions.json, and renders it with headless Chrome into
fastlane/metadata/android-play/<locale>/images/phoneScreenshots at 1080x1920.

Usage: python3 tools/store-screenshots/build.py [locale ...]
Set CHROME to the Chrome binary if it isn't in the default macOS location.
"""
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
RAW = ROOT / "fastlane/metadata/android"
OUT = ROOT / "fastlane/metadata/android-play"
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

WIDTH, HEIGHT = 1080, 1920
BACKGROUND = "#14324F"
INK = "#FFFFFF"
ACCENT = "#FF8A5C"
SUBLINE = "#D3DDEA"
# Where the devices start on every slide, below the tallest text block, so they line up
# across the set however many lines a headline takes.
DEVICE_TOP = 850

PAGE = """<!doctype html>
<html lang="{lang}"><head><meta charset="utf-8">
<style>
@font-face {{ font-family: "Jakarta"; src: url("{fonts}/PlusJakartaSans.ttf"); font-weight: 200 800; }}
@font-face {{ font-family: "Manrope"; src: url("{fonts}/Manrope.ttf"); font-weight: 200 800; }}
body {{ margin: 0; }}
.shot {{ position: relative; width: {w}px; height: {h}px; overflow: hidden; background: {bg};
  font-family: "Jakarta", "Manrope", sans-serif; }}
.text {{ position: absolute; left: 96px; top: 110px; width: 888px; display: flex; flex-direction: column; gap: 34px; }}
h1 {{ margin: 0; font-size: 150px; font-weight: 800; line-height: 1.02; letter-spacing: -4px; color: {ink}; }}
h1 span.line {{ display: block; white-space: nowrap; width: max-content; }}
h1 em {{ font-style: normal; color: {accent}; }}
p {{ margin: 0; max-width: 820px; font-size: 46px; font-weight: 500; line-height: 1.3; color: {sub}; }}
.device {{ position: absolute; }}
.phone {{ position: relative; background: #0E1013; box-shadow: 0 0 0 3px #3A3E46, 0 80px 160px rgba(0,0,0,.55); }}
.phone img {{ display: block; }}
.camera {{ position: absolute; left: 50%; border-radius: 50%; background: #050607; }}
.button {{ position: absolute; right: -9px; width: 7px; border-radius: 4px; background: #3A3E46; }}
</style></head>
<body><div class="shot">{body}</div>
<script>
// Shrink the headline until its widest line fits, and the subline until it fits three lines,
// so long translations keep the same layout as English.
document.fonts.ready.then(() => {{
  const h1 = document.querySelector("h1"), p = document.querySelector("p");
  let size = 150;
  while (size > 70 && Math.max(...[...h1.querySelectorAll(".line")].map(l => l.offsetWidth)) > 888) {{
    size -= 4; h1.style.fontSize = size + "px"; h1.style.letterSpacing = (-4 * size / 150) + "px";
  }}
  let sub = 46;
  while (sub > 30 && p.offsetHeight > sub * 1.3 * 3 + 2) {{ sub -= 2; p.style.fontSize = sub + "px"; }}
  document.body.dataset.ready = "1";
}});
</script></body></html>"""


def headline(text):
    lines = []
    for line in text.split("|"):
        escaped = html.escape(line)
        escaped = re.sub(r"\{(.*?)\}", r"<em>\1</em>", escaped)
        lines.append(f'<span class="line">{escaped}</span>')
    return "".join(lines)


def phone(src, width, alt):
    pad = round(width * 0.037)
    screen_w = width - 2 * pad
    screen_h = round(screen_w * 2400 / 1080)
    radius = round(width * 0.143)
    cam = round(width * 0.043)
    return (f'<div class="phone" style="padding:{pad}px;border-radius:{radius}px">'
            f'<div class="button" style="top:{round(width * .5)}px;height:{round(width * .22)}px"></div>'
            f'<img src="{src}" alt="{html.escape(alt)}" style="width:{screen_w}px;height:{screen_h}px;border-radius:{radius - pad}px">'
            f'<div class="camera" style="top:{round(pad * 2.1)}px;width:{cam}px;height:{cam}px;margin-left:-{cam // 2}px"></div>'
            f'</div>')


def watch(src, size, alt):
    strap_w, strap_h, bezel = round(size * 0.56), round(size * 0.42), round(size * 0.07)
    left = (size - strap_w) // 2
    return (f'<div style="position:relative;width:{size}px;height:{size}px">'
            f'<div style="position:absolute;left:{left}px;top:-{strap_h - 30}px;width:{strap_w}px;height:{strap_h}px;border-radius:40px 40px 18px 18px;background:#24272D"></div>'
            f'<div style="position:absolute;left:{left}px;bottom:-{strap_h - 30}px;width:{strap_w}px;height:{strap_h}px;border-radius:18px 18px 40px 40px;background:#24272D"></div>'
            f'<div style="position:absolute;right:-{round(size * .035)}px;top:{round(size * .42)}px;width:{round(size * .06)}px;height:{round(size * .16)}px;border-radius:10px;background:#3A3E46"></div>'
            f'<div style="position:absolute;inset:0;border-radius:50%;background:#0E1013;box-shadow:0 0 0 4px #3A3E46,0 50px 100px rgba(0,0,0,.55)"></div>'
            f'<img src="{src}" alt="{html.escape(alt)}" style="position:absolute;left:{bezel}px;top:{bezel}px;width:{size - 2 * bezel}px;height:{size - 2 * bezel}px;border-radius:50%">'
            f'</div>')


def placed(content, left, top, rotate):
    return (f'<div class="device" style="left:{left}px;top:{top}px;transform:rotate({rotate}deg)">'
            f'{content}</div>')


def source(locale, ref):
    if ref.startswith("watch:"):
        path = HERE / "watch" / locale / f"{ref.split(':')[1]}.png"
    else:
        path = RAW / locale / "images/phoneScreenshots" / f"{ref}.png"
    if not path.exists():
        sys.exit(f"Missing capture: {path}")
    return path.as_uri()


def slide_body(locale, slide, head, sub):
    text = f'<div class="text"><h1>{headline(head)}</h1><p>{html.escape(sub)}</p></div>'
    main = source(locale, slide["main"])
    if "watch" in slide:
        devices = (placed(phone(main, 540, "C2K workout screen during a walk interval"), 40, DEVICE_TOP, -11)
                   + placed(watch(source(locale, slide["watch"]), 460, "Smartwatch showing a C2K walk alert"),
                            520, DEVICE_TOP + 300, 7))
    else:
        devices = (placed(phone(source(locale, slide["back"]), 540, "C2K screen"), 40, DEVICE_TOP + 60, -11)
                   + placed(phone(main, 600, "C2K screen"), 440, DEVICE_TOP, 7))
    return text + devices


def render(html_path, png_path):
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                    "--allow-file-access-from-files", f"--window-size={WIDTH},{HEIGHT}",
                    "--virtual-time-budget=8000", f"--screenshot={png_path}", html_path.as_uri()],
                   check=True, capture_output=True)


def main():
    captions = json.loads((HERE / "captions.json").read_text(encoding="utf-8"))
    locales = sys.argv[1:] or list(captions["copy"])
    with tempfile.TemporaryDirectory() as tmp:
        for locale in locales:
            copy = captions["copy"][locale]
            out_dir = OUT / locale / "images/phoneScreenshots"
            if out_dir.exists():
                shutil.rmtree(out_dir)
            out_dir.mkdir(parents=True)
            for slide, (head, sub) in zip(captions["slides"], copy):
                page = PAGE.format(lang=locale.split("-")[0], fonts=(HERE / "fonts").as_uri(), w=WIDTH, h=HEIGHT,
                                   bg=BACKGROUND, ink=INK, accent=ACCENT, sub=SUBLINE,
                                   body=slide_body(locale, slide, head, sub))
                html_path = Path(tmp) / f"{locale}_{slide['name']}.html"
                html_path.write_text(page, encoding="utf-8")
                render(html_path, out_dir / f"{slide['name']}.png")
            print(f"{locale}: {len(copy)} slides")


if __name__ == "__main__":
    main()
